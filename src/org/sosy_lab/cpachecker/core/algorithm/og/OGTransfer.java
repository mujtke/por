package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.porf;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.setRelation;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.CriticalAreaAction;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.CriticalAreaAction.*;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;

public class OGTransfer {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final Map<Integer, List<SharedEvent>> edgeVarMap;
    private final NLTComparator nltcmp = new NLTComparator();

    public OGTransfer(Map<Integer, List<ObsGraph>> pOGMap,
                      Map<Integer, OGNode> pNodeMap,
            Map<Integer, List<SharedEvent>> pEdgeVarMap) {
        this.OGMap = pOGMap;
        this.nodeMap = pNodeMap;
        this.edgeVarMap = pEdgeVarMap;
    }

    public NLTComparator getNltcmp() { return nltcmp; }


    public ObsGraph handleNonDet(ObsGraph graph,
            ARGState parState,
            // OGPORState chOgState,
            String chThd,
            CFAEdge edge,
            boolean hasNonDet) {
        // Ensure no redundant copy of the graph.
        // For co-edges, we copy the graph only when meet the first one of them.
        if (hasNonDet) {
            for (ARGState ch : parState.getChildren()) {
                CFAEdge tmpEdge = parState.getEdgeToChild(ch);
                assert tmpEdge != null;
                if (Objects.equals(edge.getPredecessor(), tmpEdge.getPredecessor())) {
                    if (Objects.equals(edge, tmpEdge)) {
                        // >>>>>
                        ObsGraph copiedGraph = graph.deepCopy(new HashMap<>());
//                        OGNode currentNode = copiedGraph.getCurrentNode(chOgState.getInThread());
                        OGNode currentNode = copiedGraph.getCurrentNode(chThd);
                        // Copied graph is used for another conditional branch.
                        if (currentNode != null) {
                            currentNode.removeEvent(edge);
                            currentNode.getBlockEdges().remove(edge);
                        }
                        // <<<<<<
                        return copiedGraph;
                    }
                    break;
                }
            }
        }

        return null;
    }

    public boolean hasNonDet(ARGState parState, CFAEdge edge) {
        // Check whether parState has indeterminate successors.
        List<ARGState> coSuccessors = new ArrayList<>();
        parState.getChildren().forEach(s -> {
            CFAEdge tmpEdge = parState.getEdgeToChild(s);
            assert tmpEdge != null;
            if (Objects.equals(tmpEdge.getPredecessor(), edge.getPredecessor())) {
                coSuccessors.add(s);
            }
        });

        return coSuccessors.size() == 2;
    }

    private static class NLTComparator implements Comparator<AbstractState> {
        // NLT => <next
        @Override
        public int compare(AbstractState ps1, AbstractState ps2) {
//            Preconditions.checkArgument(ps1 instanceof ARGState
//                    && ps2 instanceof ARGState);
            ARGState s1 = (ARGState) ps1, s2 = (ARGState) ps2;
            Map<Integer, Integer> nlt = GlobalInfo.getInstance().getOgInfo().getNlt();
            ARGState par = s1.getParents().iterator().next();
            assert par == s2.getParents().iterator().next() : "s1 and s2 must " +
                    "have the same parent.";
            CFAEdge e1 = par.getEdgeToChild(s1), e2 = par.getEdgeToChild(s2);
            assert e1 != null && e2 != null;
            int cmp1 = nlt.get(hash(e1.hashCode(), e2.hashCode())),
                    cmp2 = nlt.get(hash(e2.hashCode(), e1.hashCode()));
            if (cmp1 == 0 || cmp2 == 0) return 0; // equal.
            if (cmp1 == 1 && cmp2 == -1) return -1; // <
            return 1; // >, cmp1 == 1 && cmp2 == -1.
        }
    }


    /**
     * FIXME: modify the description.
     * This method transfers a given graph from a parent state {@parState} to its child
     * State {@chState}.
     * If the node conflicts with the graph, then transfer stops and returns null.
     * Else, return the transferred graph.
     * When no conflict exists, there are still two possible cases need to be considered:
     * 1) The graph has contained the node. In this case, we update the new last node of
     * the graph (also update the necessary relations like mo, etc.).
     * 2) The graph meets the node first time. In this case, we add the node to the graph
     * and add all necessary relations, like rf, fr, wb and so on.
     * @param graphWrapper indicates whether the given graph has been transferred.
     * @param edge current CFA edge.
     * @param parState Initial ARGState where the transferring begin.
     * @param chState Final ARGState where the transferring stop.
     * @return <graph, copiedGraph> copiedGraph is used for the case in which
     * indeterminacy exists.
     * @implNode When add the node to the graph, we add its deep copy.
     */
	///// singleStepTransfer framework.
	public Pair<ObsGraph, ObsGraph> singleStepTransfer(
			List<ObsGraph> graphWrapper, 
			CFAEdge edge,
			ARGState parState,
			ARGState chState,
			boolean isSimpleTransfer) {

        // Debug.
        boolean __DEBUG__ = true;
        int parId = parState.getStateId(), chId = chState.getStateId();

        Preconditions.checkArgument(graphWrapper.size() == 1);
        ObsGraph graph = graphWrapper.iterator().next(), copiedGraph = null;
        OGPORState chOgState = AbstractStates.extractStateByType(chState, OGPORState.class),
                parOgState = AbstractStates.extractStateByType(parState, OGPORState.class);
        assert chOgState != null && parOgState != null;
        String curThread = chOgState.getInThread();

		OGNode node = graph.getCurrentNode(curThread);
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        CriticalAreaAction criticalAreaAction = chOgState.getInCaa();
        boolean isNormalEdge = chOgState.enteringEdgeIsNormal(),
                hasSharedVars = !(sharedEvents == null || sharedEvents.isEmpty()),
                isAssumeEdge = edge instanceof AssumeEdge,
                hasNonDet = isAssumeEdge && hasNonDet(parState, edge);

        int edgeType = getEdgeType(hasSharedVars, isAssumeEdge);

        Pair<ObsGraph, ObsGraph> result = null;
        // CriticalAreaAction.
        switch (criticalAreaAction) {
            case START:
                result = handleBlockStart(graph, edge, edgeType, node, curThread,
                        parState, chState, graphWrapper, __DEBUG__);
                break;
            case CONTINUE:
                result = handleBlockContinue(graph, edge, edgeType, node, curThread,
                        parState, chState, graphWrapper, sharedEvents, isSimpleTransfer,
                        __DEBUG__);
                break;
            case END:
                result = handleBlockTerminated(graph, edge, edgeType, node, parState,
                        chState, curThread, graphWrapper, __DEBUG__);
                break;
            case NOT_IN:
                result = handleNotInBlock(graph, edge, edgeType, node, parState,
                        chState, curThread, graphWrapper, isSimpleTransfer, __DEBUG__);
        }

        assert result != null;
        return result;
	}

    private int getEdgeType(boolean hasSharedVars, boolean isAssumeEdge) {
        if (!hasSharedVars && !isAssumeEdge) {
            return 0;
        } else if (!hasSharedVars) { // !hasSharedVars && isAssumeEdge.
            return 1;
        } else if (!isAssumeEdge) { // hasSharedVars && !isAssumeEdge.
            return 2;
        } else { // hasSharedVars && isAssumeEdge.
            return 3;
        }

    }

    OGPORState getCoOGSibling (ARGState parState, CFAEdge coARGEdge) {
        List<ARGState> chSiblings = parState.getChildren().stream()
                .filter(ch -> Objects.equals(parState.getEdgeToChild(ch), coARGEdge))
                .collect(Collectors.toList());
        assert chSiblings.size() == 1;
        OGPORState coChOgState = AbstractStates.extractStateByType(
                chSiblings.get(0), OGPORState.class);
        assert coChOgState != null;

        return coChOgState;
    }

    private Pair<ObsGraph, ObsGraph> handleNotInBlock(ObsGraph graph, CFAEdge edge,
            int edgeType,
            OGNode node,
            ARGState parState,
            ARGState chState,
            String curThd,
            List<ObsGraph> graphWrapper,
            boolean isSimpleTransfer,
            boolean __DEBUG__) {
        // Caa = NOT_IN.
        Pair<ObsGraph, ObsGraph> result = null;
        ObsGraph copiedGraph = null;
        if (edgeType == 0) { // Local non-assumption edge.
            graph.setNeedToRevisit(false);
            graphWrapper.clear();

            if (__DEBUG__) debugActions(graph, parState, chState, edge);
            result = Pair.of(graph, null);
        } else if (edgeType == 1) { // Local assumption edge.
            CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
            OGPORState chOgState = AbstractStates.extractStateByType(chState, OGPORState.class);
            if (isSimpleTransfer && coARGEdge != null) { // Has indeterminacy.
                copiedGraph = handleNonDet(graph, parState, curThd, edge, true);
                graph.addVisitedAssumeEdge(curThd, edge, chOgState);
                // FIXME: copiedGraph.addVisitedAssumeEdge()?
                copiedGraph.addVisitedAssumeEdge(curThd, coARGEdge,
                        getCoOGSibling(parState, coARGEdge));
            } else if (coARGEdge != null) {
                // Multi-step transfer.
                if (!graph.matchCachedEdge(curThd, edge, chOgState))
                    graph = null;
            }
            if (graph != null) {
                graph.setNeedToRevisit(false);
                graphWrapper.clear();
                if (__DEBUG__) debugActions(graph, parState, chState, edge);
            }

            result = Pair.of(graph, copiedGraph);
        } else if (edgeType == 2) { // Shared non-assumption edge
            if (node != null && isConflict(graph, curThd, node)) {
                return Pair.of(null, null);
            } else if(node != null) { // Node != null and no conflict exists.
                // Node should be simple.
                assert node.getBlockEdges().contains(edge);
                OGPORState chOgState = AbstractStates.extractStateByType(chState,
                        OGPORState.class);
                assert chOgState != null;
                updatePreSucState(edge, node, parState, chState);
                visitNode(graph, node, chOgState, true);
                graph.updateCurrentNodeTable(curThd, node);
                // FIXME: should we revisit for the substituted shared-assumption edge?
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else if (hasUnmetNode(graph)) { // Node == null and conflict exists.
                // The edge corresponds to some node, so transfer requires no unmet nodes.
                return Pair.of(null, null);
            } else { // Node == null and no conflict exists.
                OGNode newNode = new OGNode(edge,
                                new ArrayList<>(Collections.singleton(edge)),
                                true,
                                false);
                newNode.addEvents(edgeVarMap.get(edge.hashCode()));
                newNode.setThreadInfo(chState);
                updatePreSucState(edge, newNode, parState, chState);
                OGPORState chOgState = AbstractStates.extractStateByType(chState,
                        OGPORState.class);
                assert chOgState != null;
                visitNode(graph, newNode, chOgState, false);
                graph.updateCurrentNode(curThd, null); // NewNode is simple.
                graph.setNeedToRevisit(true);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            }
            // edgeType = 2
        } else { // Shared assumption edge.
            if (node != null && isConflict(graph, curThd, node)) {
                return Pair.of(null, null);
            } else if(node != null) { // Node != null and no conflict exists.
                // Node should be simple.
                assert node.isSimpleNode();
                boolean edgeInNode = node.getBlockEdges().contains(edge);
                if (!edgeInNode) { // The node doesn't contain the edge.
                    // In this case, we should transfer the graph along the coEdge,
                    // which requires coEdge should exist.
                    CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
                    if (coARGEdge == null)
                        throw new UnsupportedOperationException("Cannot find the " +
                                "coARGEdge of the edge " + edge);
                    return Pair.of(null, null);
                }

                // Else, the node contains the edge.
                OGPORState chOgState = AbstractStates.extractStateByType(chState,
                        OGPORState.class);
                assert chOgState != null;
                updatePreSucState(edge, node, parState, chState);
                visitNode(graph, node, chOgState, true);
                graph.updateCurrentNodeTable(curThd, node);
                // FIXME: should we revisit for the substituted shared-assumption edge?
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else if (hasUnmetNode(graph)) { // Node == null and conflict exists.
                // The edge corresponds to some node, so transfer requires no unmet nodes.
                return Pair.of(null, null);
            } else { // Node == null and no conflict exists.
                // If there exists indeterminacy, and it's a simple transfer.
                CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
                if (coARGEdge != null && isSimpleTransfer) {
                    copiedGraph = handleNonDet(graph, parState, curThd, edge, true);
                }

                OGNode newNode = new OGNode(edge,
                        new ArrayList<>(Collections.singleton(edge)),
                        true,
                        false);
                newNode.addEvents(edgeVarMap.get(edge.hashCode()));
                newNode.setThreadInfo(chState);
                updatePreSucState(edge, newNode, parState, chState);
                OGPORState chOgState = AbstractStates.extractStateByType(chState,
                        OGPORState.class);
                assert chOgState != null;
                visitNode(graph, newNode, chOgState, false);
                graph.updateCurrentNode(curThd, null); // NewNode is simple.
                graph.setNeedToRevisit(true);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, copiedGraph);
            }
        }

        assert result != null;
        return result;
    }

    private Pair<ObsGraph, ObsGraph> handleBlockTerminated(ObsGraph graph,
            CFAEdge edge,
            int edgeType,
            OGNode node,
            ARGState parState,
            ARGState chState,
            String curThd,
            List<ObsGraph> graphWrapper,
            boolean __DEBUG__) {
        // Caa = END. This means edge should be a funCall and node terminates.
        Pair<ObsGraph, ObsGraph> result = null;
        // Node must be not null.
        assert node != null;
        boolean edgeInNode = node.getBlockEdges().contains(edge);
        OGPORState chOgState = AbstractStates.extractStateByType(chState, OGPORState.class);
        assert chOgState != null;
        if (edgeType == 0) { // Local non-assumption edge.
            if (!edgeInNode) {
                node.addEdge(edge, null);
            } else {
                node.setLastVisitedEdge(edge);
            }
            visitNode(graph, node, chOgState, node.hasBeenAddedToGraph());
            if (node.shouldRevisit())
                graph.setNeedToRevisit(true);
            graph.updateCurrentNodeTable(curThd, node);
            updatePreSucState(edge, node, parState, chState);
            graphWrapper.clear();

            if (__DEBUG__) debugActions(graph, parState, chState, edge);
            result = Pair.of(graph, null);
        } else if (edgeType == 2) { // Shared non-assumption edge.
            if (!edgeInNode) {
                node.addEdge(edge, edgeVarMap.get(edge.hashCode()));
            } else {
                node.setLastVisitedEdge(edge);
            }
            visitNode(graph, node, chOgState, !node.hasBeenAddedToGraph());
            if (node.shouldRevisit())
                graph.setNeedToRevisit(true);
            graph.updateCurrentNodeTable(curThd, node);
            updatePreSucState(edge, node, parState, chState);
            graphWrapper.clear();

            if (__DEBUG__) debugActions(graph, parState, chState, edge);
            result = Pair.of(graph, null);
        } else {
            throw new UnsupportedOperationException("Incorrect edge type: "
                    + edgeType + ", 0 or 2 allowed.");
        }

        assert result != null;
        return result;
    }

    private Pair<ObsGraph, ObsGraph> handleBlockContinue(ObsGraph graph,
            CFAEdge edge,
            int edgeType,
            OGNode node,
            String curThd,
            ARGState parState,
            ARGState chState,
            List<ObsGraph> graphWrapper,
            List<SharedEvent> sharedEvents,
            boolean isSimpleTransfer,
            boolean __DEBUG__) {
        // Caa = CONTINUE means we are inside a *complex* node.
        assert node != null;
        Pair<ObsGraph, ObsGraph> result = null;
        ObsGraph copiedGraph = null;
        // Edge has been visited?
        boolean edgeInNode = node.getBlockEdges().contains(edge);
        if (edgeType == 0) { // Local non-assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                // The node has been added to the graph.
                // Set the lastVisitedEdge in this case.
                node.setLastVisitedEdge(edge);
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                // The node has been added to the graph, but some edges get deleted
                // during the revisiting.
                node.addEdge(edge, null);
                // FIXME: set last visited edge?
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else { // The node is totally new.
                assert !edgeInNode;
                node.addEdge(edge, null); // Also add the events.
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            }
            // edgeType == 0
        } else if (edgeType == 1) { // Local assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                node.setLastVisitedEdge(edge);
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                // Transfer gets blocked or a replacement should take place.
                // Assume: edge = d, co-edge = !d. Now d is not inside the node. If:
                // (1) !d is not in the ARG but the node, then we need to replace
                // !d(co-edge) with d(edge).
                // (2) !d is neither in the ARG nor the node, then add the edge to the
                // node.
                // (3) !d is in the ARG but the node, then we need to handle the
                // indeterminacy.
                // (4) !d is in the ARG and the node, then transfer gets blocked here.
                CFAEdge coCFAEdge = getCoEdgeFromCFA(edge),
                        coARGEdge = getCoEdgeFromARG(parState, edge);
                boolean coCFAEdgeInNode = node.getBlockEdges().contains(coCFAEdge);
                if (coCFAEdgeInNode && coARGEdge == null) { // case (1)
                    // Replacing.
                    node.replaceCoEdge(edgeVarMap, edge, coCFAEdge);
                    assert node.getBlockEdges().contains(edge) :
                            "The edge not in the node " + node + " after replacing.";
                    // FIXME: update last visited edge?
                    // node.setLastVisitedEge(edge);
                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (__DEBUG__) debugActions(graph, parState, chState, edge);
                    result = Pair.of(graph, null);
                } else if (!coCFAEdgeInNode && coARGEdge == null) { // case (2)
                    // Replacement shouldn't happen. Add the edge to the node.
                    // FIXME: add the visited assume edge?
                    node.addEdge(edge, null);
                    // Update last visited edge?
                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (__DEBUG__) debugActions(graph, parState, chState, edge);
                    result = Pair.of(graph, null);
                } else if (!coCFAEdgeInNode) { // case (3), coARGEdge != null
                    // In this case indeterminacy exists.
                    OGPORState chOgState = AbstractStates.extractStateByType(chState,
                            OGPORState.class);
                    // FIXME: do we need to distinguish simple or multi-step transfer?
                    if (isSimpleTransfer) { // A simple transfer only.
                        // The node doesn't contain the edge, so add first.
                        node.addEdge(edge, null);
                        copiedGraph = handleNonDet(graph, parState, curThd, edge, true);
                        graph.addVisitedAssumeEdge(curThd, edge, chOgState);
                        // FIXME: copiedGraph.addVisitedAssumeEdge()?
                        copiedGraph.addVisitedAssumeEdge(curThd, coARGEdge,
                                getCoOGSibling(parState, coARGEdge));
                    } else { // A multi-step transfer.
                        // TODO: will this case happen?
                        throw new UnsupportedOperationException("Unhandled case: " +
                                "indeterminacy exists inside node during a multi-step " +
                                "transfer");
//                        if (!graph.matchCachedEdge(curThd, edge, chOgState)) {
//                            graph = null;
//                        }
                    }

                    if (graph != null) {
                        graph.setNeedToRevisit(false);
                        graphWrapper.clear();
                        if (__DEBUG__) debugActions(graph, parState, chState, edge);
                    }
                    if (copiedGraph != null) copiedGraph.setNeedToRevisit(false);

                    result = Pair.of(graph, copiedGraph);
                    // case (3)
                } else { // case (4), coCFAEgeInNode && coARGEdge != null
                    result = Pair.of(null, null);
                }
            } else { // Totally new node.
                // Must be a simple transfer.
                // FIXME: add visited assume edge.
                node.addEdge(edge, null);

                // Copy the graph when indeterminacy exists.
                CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
                OGPORState chOgState = AbstractStates.extractStateByType(parState,
                        OGPORState.class);
                if (coARGEdge != null) { // Indeterminacy exists.
                    copiedGraph = handleNonDet(graph, parState, curThd, edge, true);
                    graph.addVisitedAssumeEdge(curThd, edge, chOgState);
                    // FIXME: copiedGraph.addVisitedAssumeEdge()?
                    copiedGraph.addVisitedAssumeEdge(curThd, coARGEdge,
                            getCoOGSibling(parState, coARGEdge));
                }

                graph.setNeedToRevisit(false);
                if (copiedGraph != null) copiedGraph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, copiedGraph);
            }
            // edgeType == 1
        } else if (edgeType == 2) { // Shared non-assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                node.setLastVisitedEdge(edge);
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                // The node should have removed some events after revisiting.
                assert node.getLastHandledEvent() != null;
                graph.setNeedToRevisit(false);
                node.addEdge(edge, sharedEvents); // Also add the events.
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else { // The node is totally new.
                graph.setNeedToRevisit(false);
                node.addEdge(edge, sharedEvents);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            }
            // edgeType == 2
        } else { // Shared assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                // Replacement won't happen for shared-var edge.
                CFAEdge coCFAEdge = getCoEdgeFromCFA(edge),
                        coARGEdge = getCoEdgeFromARG(parState, edge);
                boolean coCFAEdgeInNode = node.getBlockEdges().contains(coCFAEdge);
                if (coCFAEdgeInNode && coARGEdge == null) { // case (1)
                    // We cannot replace the coCFAEdge, transfer gets blocked here.
                    throw new UnsupportedOperationException("Transfer gets blocked at " + parState);
                } else if (!coCFAEdgeInNode && coARGEdge == null) { // case (2)
                    node.addEdge(edge, sharedEvents);
                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (__DEBUG__) debugActions(graph, parState, chState, edge);
                    result = Pair.of(graph, null);
                } else if (!coCFAEdgeInNode) { // case (3), coARGEdge != null.
                    node.addEdge(edge, sharedEvents);
                    // Indeterminacy exists.
                    copiedGraph = handleNonDet(graph, parState, curThd, edge, true);
                    graph.setNeedToRevisit(false);
                    if (copiedGraph != null) copiedGraph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (__DEBUG__) debugActions(graph, parState, chState, edge);
                    result = Pair.of(graph, copiedGraph);
                } else { // case (4), coCFAEdgeInNode && coARGEdge != null.
                    result = Pair.of(null, null);
                }
            } else { // Totally new node.
                node.addEdge(edge, sharedEvents);
                // Handle indeterminacy if there exists.
                CFAEdge coARGEge = getCoEdgeFromARG(parState, edge);
                if (coARGEge != null) { // Has indeterminacy.
                    copiedGraph = handleNonDet(graph, parState, curThd, edge, true);
                }
                graph.setNeedToRevisit(false);
                if (copiedGraph != null) copiedGraph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, copiedGraph);
            }
        }

        assert result != null;
        return result;
    }

    private Pair<ObsGraph, ObsGraph> handleBlockStart(
            ObsGraph graph,
            CFAEdge edge,
            int edgeType,
            OGNode node,
            String curThd,
            ARGState parState,
            ARGState chState,
            List<ObsGraph> graphWrapper,
            boolean __DEBUG__) {
        // Caa = START. This means edge should be a funCall and we will enter a node.
        Pair<ObsGraph, ObsGraph> result = null;
        if (edgeType == 0) { // Local non-assumption edge.
            if (node != null) {
                // Simple node contains only one edge. And, node should contain the
                // edge at this time.
                assert !node.isSimpleNode() && node.getBlockEdges().contains(edge);
                // We will enter the node if no conflicts exist.
                if (isConflict(graph, curThd, node)) {
                    return Pair.of(null, null);
                }
                graph.setNeedToRevisit(false);
                graphWrapper.clear();
                if (__DEBUG__) debugActions(graph, parState, chState, edge);
                result = Pair.of(graph, null);
            } else { // node == null.
                // We start a new node and enter it if no conflicts exist.
                if (hasUnmetNode(graph)) {
                    // Conflicted, we need to meet some other nodes first.
                    return Pair.of(null, null);
                }
                // No Conflict.
                OGNode newNode = new OGNode(edge,
                        new ArrayList<>(Collections.singleton(edge)),
                        false,
                        false);
                newNode.setThreadInfo(chState);
                updatePreSucState(edge, newNode, parState, chState);
                graph.setNeedToRevisit(false);
                graph.updateCurrentNode(curThd, newNode);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
//                return Pair.of(graph, null);
                result = Pair.of(graph, null);
            }
            // edgeType == 0
        } else if (edgeType == 2) { // Shared non-assumption edge.
            if (node != null) { // Simple node.
                assert !node.isSimpleNode();
                // We will enter the node if no conflicts exist.
                if (isConflict(graph, curThd, node)) {
                    return Pair.of(null, null);
                }

                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
//                return Pair.of(graph, null);
                result = Pair.of(graph, null);
            } else { // Node == null.
                if (hasUnmetNode(graph)) {
                    return Pair.of(null, null);
                }
                // No Conflict.
                OGNode newNode = new OGNode(edge,
                        new ArrayList<>(Collections.singleton(edge)),
                        false,
                        false);
                newNode.setThreadInfo(chState);
                updatePreSucState(edge, newNode, parState, chState);
                graph.updateCurrentNode(curThd, newNode);
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (__DEBUG__) debugActions(graph, parState, chState, edge);
//                return Pair.of(graph, null);
                result = Pair.of(graph, null);
            }
        } else {
            throw new UnsupportedOperationException("Incorrect edge type: "
                    + edgeType + ", 0 or 2 allowed.");
        }

        return result;
    }

    private @NonNull Triple<Integer, CFAEdge, Boolean> isInsideNode(
            ARGState parState,
            OGNode node,
            CFAEdge edge,
            List<SharedEvent> sharedEvents,
            CriticalAreaAction caa) {
        if (node.isSimpleNode()) {
            return node.contains(edge) == 0 ? Triple.of(0, edge, true)
                    : Triple.of(-1, edge, false);
        } else {
            // Complex node.
            int idx = node.contains(edge);
            if (idx >= 0) {
                return Triple.of(idx, edge, true);
            } else {
                // The node (complex) doesn't contain the edge.
                if (node.getLastVisitedEdge() != null) {
                    // We are inside a node that has been added to the graph.
                    // FIXME: Use "if(node.getLastHandledEvent() != null)"?
                    int lastVisitEdgeIdx = -1;
                    boolean edgeHasBeenVisited = false;
                    if (edge instanceof AssumeEdge
                            && (sharedEvents == null || sharedEvents.isEmpty())) {
                        // The Replacing and adding of the edge won't happen when edge
                        // access shared vars.
                        // When edge's coEdge is inside the node but not in the ARG, we
                        // may need to replace it with the edge.
                        CFAEdge coCFAEdge = getCoEdgeFromCFA(edge),
                                coARGEdge = getCoEdgeFromARG(parState, edge);

                        if (node.getBlockEdges().contains(coCFAEdge)) {
                            // coEdge is inside the node, replacement may happen.
                            if (coARGEdge == null) {
                                // Replacing.
                                node.replaceCoEdge(edgeVarMap, edge, coCFAEdge);
                                assert node.getBlockEdges().contains(edge) :
                                        "The edge is not in the node after " +
                                                "replacing:" + edge;
                                lastVisitEdgeIdx = node.getBlockEdges().indexOf(edge);
                            }
                            // Else, there is no need to replace coEdge.
                            // Also, don't add the edge.
                            // TODO
                        } else {
                            // Replacement shouldn't happen.
                            // Add the edge to the node.
                            node.getBlockEdges().add(edge);
                            node.addEvents(sharedEvents);
                            lastVisitEdgeIdx = node.getBlockEdges().indexOf(edge);
                        }
                    } else {
                        // Not an assumption edge, or
                        // Assumption edge with shared vars.
                        node.getBlockEdges().add(edge);
                        node.addEvents(sharedEvents);
                        lastVisitEdgeIdx = node.getBlockEdges().indexOf(edge);
                    }

                    return Triple.of(lastVisitEdgeIdx, edge, edgeHasBeenVisited);
                } else {
                    if (caa == NOT_IN ||
                            /* FIXME: When we are inside a node but the node doesn't
                                contain the edge? */
                            (caa == CONTINUE && node.getLastHandledEvent() != null)) {
                        // We haven't entered a critical area yet.
                        return Triple.of(-1, edge, false);
                    } else {
                        // We are inside a node that hasn't been added to the graph yet.
                        // Add the edge to the node.
                        node.getBlockEdges().add(edge);
                        node.addEvents(sharedEvents);
                        return Triple.of(node.getBlockEdges().size(), edge, false);
                    }
                }
            }
        }
    }

    // Get edge's coEdge that comes from parState.
    private CFAEdge getCoEdgeFromARG(ARGState parState, CFAEdge edge) {
        for (ARGState chState : parState.getChildren()) {
            CFAEdge tmp = parState.getEdgeToChild(chState);
            if (tmp instanceof AssumeEdge
                    && tmp != edge
                    && Objects.equals(tmp.getPredecessor(), edge.getPredecessor())) {
                return tmp;
            }
        }

        return null;
    }

    // Get edge's coEdge that locates in the same CFA and has the same CFA predecessor
    // with the edge.
    private CFAEdge getCoEdgeFromCFA(CFAEdge edge) {
        Preconditions.checkArgument(
                edge.getPredecessor().getNumLeavingEdges() > 1,
                "Predecessor of assume edge has less then two outgoing " +
                        "edges is not allowed: " + edge);
        CFAEdge tmp = null;
        for (int i = 0; i < edge.getPredecessor().getNumLeavingEdges(); i++) {
            tmp = edge.getPredecessor().getLeavingEdge(i);
            if (tmp instanceof AssumeEdge && tmp != edge) {
                tmp = edge;
                break;
            }
        }

        assert tmp != null : "Cannot find the coEdge of: " + edge;
        return tmp;
    }

    private void debugActions(ObsGraph graph,
            ARGState parState, ARGState chState, CFAEdge edge) {

        if (graph == null) return;
        addGraphToFull(graph, chState.getStateId());
        System.out.println("Transferring from s" + parState.getStateId()
                + " -> s" + chState.getStateId() + ": " + edge);
    }

    private void updatePreSucState(CFAEdge edge, OGNode node, ARGState parState,
                                ARGState chState) {
        if (node.isSimpleNode() /* Simple node. */) {
            // Update the preState and SucStat for the node if it's not null;
            node.setPreState(parState);
            node.setSucState(chState);
        } else { // Not a simple node.
            if (edge.equals(node.getBlockStartEdge())) {
                node.setPreState(parState);
            }
            else if (edge.equals(node.getLastBlockEdge())) {
                node.setSucState(chState);
            }
        }
    }

    private boolean hasUnmetNode(ObsGraph graph) {
        // Judge whether there is any node in the graph yet to meet.
        // traceLen != nodes.size()
        return graph.getTraceLen() != graph.getNodes().size();
    }

    /**
     * @param graphWrapper A container used to justify whether we should stop the
     *                     enumeration for the states in the inWait or notInWait. If
     *                     the container has no graph anymore, which means the graph has
     *                     been transferred, then there is no need to handle the left
     *                     states.
     * @return
     */
    public Pair<AbstractState, ObsGraph> multiStepTransfer(Vector<AbstractState> waitlist,
                                  ARGState leadState,
                                  List<ObsGraph> graphWrapper) {
        Preconditions.checkArgument(graphWrapper.size() == 1,
                "Only one graph in graphWrapper is allowed.");
        // Divide children of leadState into two parts: in the waitlist or not.
        List<ARGState> inWait = new ArrayList<>(), notInWait = new ArrayList<>();
        leadState.getChildren().forEach(s -> {
            if (waitlist.contains(s)) inWait.add(s);
            else notInWait.add(s);
        });
        // Reorder by using <next.
        inWait.sort(nltcmp);
        notInWait.sort(nltcmp);
        // Handle states in the waitlist first.
        for (ARGState chState : inWait) {
            if (graphWrapper.isEmpty()) return null;
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            // assert node != null: "Could not find OGNode for edge " + etp;//
//            ObsGraph chGraph = singleStepTransfer(graphWrapper, etp, leadState, chState);
            Pair<ObsGraph, ObsGraph> transferResult = singleStepTransfer(graphWrapper,
                    etp, leadState, chState, false);
            ObsGraph chGraph = transferResult.getFirst();
            if (chGraph != null) {
                // Transfer stop, we have found the target state.
                OGMap.putIfAbsent(chState.getStateId(), new ArrayList<>());
                List<ObsGraph> chGraphs = OGMap.get(chState.getStateId());
                chGraphs.add(chGraph);
                // Adjust waitlist to ensure chState will be explored before its
                // siblings that has no graphs.
                adjustWaitlist(OGMap, waitlist, chState);
                return Pair.of(chState, chGraph);
            }
        }
        // Handel states not in the waitlist.
        for (ARGState chState : notInWait) {
            if (graphWrapper.isEmpty()) return null;
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            // assert node != null: "Could not find OGNode for edge " + etp;
//            ObsGraph chGraph = singleStepTransfer(graphWrapper, etp, leadState, chState);
            Pair<ObsGraph, ObsGraph> transferResult = singleStepTransfer(graphWrapper,
                    etp, leadState, chState, false);
            ObsGraph chGraph = transferResult.getFirst();
            if (chGraph != null) {
                if (chState.getChildren().isEmpty()) {
                    // FIXME: chState may be neither in the waitlist nor have any child.
                    // In this case, should we add the chState to the waitlist again?
                    // At the same time, when we can add states to the waitlist, do we
                    // still need to adjust it?
                    waitlist.add(chState);
                    OGMap.putIfAbsent(chState.getStateId(), new ArrayList<>());
                    List<ObsGraph> chGraphs = OGMap.get(chState.getStateId());
                    chGraphs.add(chGraph);
                    return Pair.of(chState, chGraph);
                }
                // Else, find target state recursively.
                List<ObsGraph> newGraphWrapper = new ArrayList<>();
                newGraphWrapper.add(chGraph);
                return multiStepTransfer(waitlist, chState, newGraphWrapper);
            }
        }
        return null;
    }

    /**
     * Detect whether a node conflicts with a graph. No conflict means we could add the
     * node to the trace. A trace corresponds to an actual execution sequence of the
     * nodes in the graph, so, one graph may have more than one trace.
     * @return true, if conflicted.
     */
    private boolean isConflict(ObsGraph graph, OGNode node, int nodeInGraph) {
        int i = graph.getTraceLen(), j = nodeInGraph;
        if (j < 0) {
            // The node is not in the graph.
            // We explore the nodes in graph first. In this case it's regarded as a
            // conflict if there are still some nodes in the graph we haven't met, we
            // should explore them before we meet some new nodes.
            if (i < graph.getNodes().size()) {
                // There are still some nodes we haven't met.
                return true;
            }
            return false;
        }
        // The node is in the graph.
        // In this case, it is regarded as a conflict if some nodes happen before the
        // 'node' but are still unexplored.
        OGNode nodej = graph.getNodes().get(j);
        assert nodej != null;
        for (OGNode n : graph.getNodes()) {
            if (nodej == n || n.isInGraph()) continue;
            // n != nodej && n not in graph.
            if (n.getFromRead().contains(nodej) || porf(n, nodej)) {
                // || nodej.getWAfter().contains(n)
                return true;
            }
        }

        return false;
    }

    /**
     * it's regarded as a conflict if the nodes from other threads happen before the
     * node of the current thread.
     */
    private boolean isConflict(ObsGraph graph, String curThread, OGNode curNode) {
        Set<OGNode> otherNodes = new HashSet<>();
        graph.getNodeTable().forEach((k, v) -> {
             if (!curThread.equals(k) && v != null && !v.isInGraph())
                 otherNodes.add(v);
        });

        for (OGNode on : otherNodes) {
            if (on.getFromRead().contains(curNode) || porf(on, curNode)) {
                return true;
            }
        }

        return false;
    }

    private void visitNode(ObsGraph graph, OGNode node,
            OGPORState chOgState,
            boolean hasBeenVisited) {
        // 1.1 Add rf, mo, fr relations if the node is visited the first time.
        // For the visited nodes, just updating mo.
        Set<SharedEvent> rFlag = new HashSet<>(), wFlag = new HashSet<>(node.getWs());
        if (!hasBeenVisited) {
            // FIXME: Not all but only Rs after the lastHandledEvent should be added?
            // rFlag.addAll(node.getRs());
            node.getNewRs(rFlag);
        }
        // Whether we have found the predecessor of the node.
        boolean preFlag = node.getPredecessor() != null;
        OGNode n = graph.getLastNode();
        // Backtracking along with the trace.
        while (n != null) {
            if (!preFlag && n.isPredecessorOf(node)) {
                n.getSuccessors().add(node);
                node.setPredecessor(n);
                preFlag = true;
            }
            if (rFlag.isEmpty() && wFlag.isEmpty()) {
                // All Rs and Ws in node have been handled.
                if (preFlag) {
                    // We have found the predecessor of node.
                    break;
                } else {
                    // Else, continue to find the predecessor of node.
                    n = n.getTrAfter();
                    continue;
                }
            }

            addRfMoForNewNode(graph, n, rFlag, wFlag);
            n = n.getTrAfter();
        }

        if (!hasBeenVisited) {
            if (!graph.getNodes().contains(node)) {
                // 1.2 Add the node to the graph if we visit it the first time.
                graph.getNodes().add(node);
            }
        }

        // 2. Update the info for the node and graph.
        node.setInGraph(true);
        // FIXME: loopDepth?
        node.setLoopDepth(chOgState.getLoopDepth());
        if (graph.getLastNode() != null) {
            graph.getLastNode().setTrBefore(node);
            node.setTrAfter(graph.getLastNode());
        }
        graph.setLastNode(node);
        graph.setTraceLen(graph.getTraceLen() + 1);
    }

    // Add rf and mo for the newly added node.
    void addRfMoForNewNode(ObsGraph graph, OGNode n,
                           Set<SharedEvent> rFlag,
                           Set<SharedEvent> wFlag) {
        for (SharedEvent w : n.getWs()) {
            Set<SharedEvent> toRemove = new HashSet<>();
            // Rf.
            for (SharedEvent r : rFlag) {
                if (r.accessSameVarWith(w)) {
                    // set w <_rf r.
                    setRelation("rf", graph, w, r);
                    toRemove.add(r);
                }
            }
            rFlag.removeAll(toRemove);
            toRemove.clear();

            // Mo.
            for (SharedEvent j : wFlag) {
                if (j.accessSameVarWith(w)) {
                    setRelation("mo", graph, w, j);
                    toRemove.add(j);
                }
            }
            wFlag.removeAll(toRemove);
            toRemove.clear();
        }
    }


    private void adjustWaitlist(Map<Integer, List<ObsGraph>> OGMap,
                                Vector<AbstractState> waitlist,
                                ARGState state) {
        int i = waitlist.indexOf(state), j = i + 1;
        for (; j < waitlist.size(); j++) {
            assert waitlist.get(j) instanceof ARGState;
            ARGState other = (ARGState) waitlist.get(j);
            // Just searching for state's siblings that are closer to the end of waitlist.
            if (Collections.disjoint(state.getParents(), other.getParents())) {
                // If we find some states that belong to different parents with state,
                // We can stop.
                break;
            }
            // Find a sibling of state. If the sibling has graphs, then just skip.
            // Else swap it with state. TODO: nonnull but empty?
            if (OGMap.get(other.getStateId()) == null
                    || OGMap.get(other.getStateId()).isEmpty()) {
                continue;
            }
            // Swap.
            ARGState tmp = other;
            waitlist.set(j, state);
            waitlist.set(i, tmp);
            i = j; // Update i, it points to the original state.
        }
    }

    public static void getHb(OGNode n, Set<OGNode> hbn) {
        assert hbn != null;
        if (n.getPredecessor() != null) hbn.add(n.getPredecessor());
        hbn.addAll(n.getReadFrom());
        hbn.addAll(n.getFromReadBy());
        hbn.addAll(n.getWAfter());
    }

    // Debug.
    public void addGraphToFull(ObsGraph graph, Integer stateId) {
        String gStr = getDotStr(graph);
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        assert ogInfo != null;
        List<String> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
                k -> new ArrayList<>());
        ogs.add(gStr);
    }
}
