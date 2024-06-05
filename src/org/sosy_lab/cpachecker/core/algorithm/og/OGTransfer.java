package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.porf;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.CriticalAreaAction;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;

public class OGTransfer {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, List<SharedEvent>> edgeVarMap;
    private final NLTComparator nltcmp = new NLTComparator();
    private final boolean enableDebug;

    public OGTransfer(
            Map<Integer, List<ObsGraph>> pOGMap,
            HashMap<Integer, List<SharedEvent>> pEdgeVarMap,
            boolean pEnableDebug) {
        this.OGMap = pOGMap;
        this.edgeVarMap = pEdgeVarMap;
        this.enableDebug = pEnableDebug;
    }

    public NLTComparator getNltcmp() { return nltcmp; }

    // FIXME

    /**
     * @return deep copy of the {@param graph} when needed.
     * @implNote Only copy graph when needed.For co-edges, we copy the graph only when
     * we meet the first one of them, which implies an implicit assumption that the order
     * of parState's outgoing edges keep unchanged across the whole algorithm. For
     * example, if parState has two outgoing edges d and !d, then when we traverse all
     * outgoing edges of parState, we will always meet d before !d. By assuming so, we
     * will only copy the graph when we meet edge d.
     */
    public ObsGraph handleNonDet(ObsGraph graph,
            ARGState parState,
            CFAEdge edge) {
        assert edge instanceof AssumeEdge;
        for (ARGState ch : parState.getChildren()) {
            CFAEdge tmpEdge = parState.getEdgeToChild(ch);
            assert tmpEdge != null;
            if (Objects.equals(edge.getPredecessor(), tmpEdge.getPredecessor())) {
                if (Objects.equals(edge, tmpEdge)) {
                    return graph.deepCopy(new HashMap<>());
                }

                break; // Only for the first edge, for the second one, we don't copy the graph.
            }
        }

        return null;
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
     *                          SingleStepTransfer Framework.
     * This method transfers a given graph from {@param parState} to {@param chState}.
     * For the given {@param edge}, there must be some node corresponds it. If the node
     * conflicts with the graph, then transfer stops and returns <null, null> as result.
     * If no conflict exists, then there are two possible cases need to be considered:
     * 1) The graph has contained the node.
     * 2) The graph meets the node first time.
     * For both cases, we need to handle the edge and events according to the types of the
     * edge and critical area.
     * @param graphWrapper Indicates whether the given graph has been transferred.
     * @param edge Current CFA edge.
     * @param parState Initial ARGState where the transferring begin.
     * @param chState Final ARGState where the transferring stop.
     * @param isSimpleTransfer Indicate whether the current call of this method is direct
     *                         or indirect, for the latter, the method is called by
     *                         {@link #multiStepTransfer(Vector, ARGState, List)}.
     * @return <graph, copiedGraph> CopiedGraph is used for the case where the indeterminacy
     * exists.
     * @implNote When adding events to the node, we use deep copies of the events in
     * {{@link #edgeVarMap}. Similarly, copiedGraph also comes from the deep copying of
     * the graph in {@param graphWrapper}.
     */
	public Pair<ObsGraph, ObsGraph> singleStepTransfer(
			List<ObsGraph> graphWrapper, 
			CFAEdge edge,
			ARGState parState,
			ARGState chState,
			boolean isSimpleTransfer) {

        // For debugging.
        int parId = parState.getStateId(), chId = chState.getStateId();

        OGPORState chOgState =
                AbstractStates.extractStateByType(chState, OGPORState.class);
        assert chOgState != null;
        CriticalAreaAction caa = chOgState.getInCaa();
        Pair<ObsGraph, ObsGraph> result = null;

        // CriticalAreaAction.
        switch (caa) {
            case START:
                result = handleBlockStart(graphWrapper, edge, parState, chState);
                break;
            case CONTINUE:
                result = handleBlockContinue(graphWrapper, edge, parState, chState,
                        isSimpleTransfer);
                break;
            case END:
                result = handleBlockTerminated(graphWrapper, edge, parState, chState,
                        isSimpleTransfer);
                break;
            case NOT_IN:
                result = handleNotInBlock(graphWrapper, edge, parState, chState,
                        isSimpleTransfer);
        }

        assert result != null;
        return result;
	}

    private int getEdgeType(List<SharedEvent> sharedEvents, CFAEdge edge) {
        boolean hasSharedVars = !(sharedEvents == null || sharedEvents.isEmpty()),
                isAssumeEdge = edge instanceof AssumeEdge;
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

    private Pair<ObsGraph, ObsGraph> handleNotInBlock(
            List<ObsGraph> graphWrapper,
            CFAEdge edge,
            ARGState parState,
            ARGState chState,
            boolean isSimpleTransfer) {
        // Caa = NOT_IN.
        assert graphWrapper.size() == 1;
        ObsGraph graph = graphWrapper.iterator().next(),
                copiedGraph = null;
        OGPORState chOgState =
                AbstractStates.extractStateByType(chState, OGPORState.class);
        assert chOgState != null;
        String curThd = chOgState.getInThread();
        OGNode node = graph.getCurrentNode(curThd);
        if (node != null)
            assert node.isSimpleNode();
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        int edgeType = getEdgeType(sharedEvents, edge);
        boolean newNodeCreated = false;
        Pair<ObsGraph, ObsGraph> result = null;

        if (edgeType == 0) { // Local non-assumption edge.
            //
        } else if (edgeType == 1) { // Local assumption edge.
            CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
            if (coARGEdge != null && isSimpleTransfer) {
                // Indeterminacy exists, and it's the first time that graph meet the edge.
                copiedGraph = handleNonDet(graph, parState, edge);
                graph.addVisitedAssumeEdge(curThd, edge, chOgState);
            } else if (coARGEdge != null) {
                // Not the first time that graph meets the edge.
                if (!graph.cachedEdgeMatch(curThd, edge, chOgState))
                    // graph = null;
                    return Pair.of(null, null);
            }
        } else if (edgeType == 2) { // Shared non-assumption edge
            if (node != null) {
                // In this case, we must check the conflict. But we cannot check the
                // conflict until we visit the node and update its relations.
                assert node.contains(edge) :
                        "A simple node must contains its only edge!";
            } else if (hasUnmetNode(graph)) { // Node == null and there are unmet nodes.
                // Transfer requires no unmet nodes.
                return Pair.of(null, null);
            } else { // Node == null and no unmet nodes.
                // In this case, there must be no conflict.
                node = new OGNode(
                        new ArrayList<>(Collections.singleton(edge)),
                        true,
                        parState,
                        chState);
                node.addEvents(sharedEvents);
                // Indicate there is no need to check the conflict.
                newNodeCreated = true;
            }
            // edgeType = 2
        } else { // Shared assumption edge.
            if (node != null) {
                boolean edgeInNode = node.contains(edge);
                if (!edgeInNode) { // The node doesn't contain the edge.
                    // In this case, we should transfer the graph along the coEdge,
                    // which requires coEdge should exist.
                    CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
                    // FIXME: we may need to replace the edge.
                    if (coARGEdge == null)
                        throw new UnsupportedOperationException(
                                "Graph gets blocked at s" + parState.getStateId());
                    // graph = null;
                    return Pair.of(null, null);
                }
                // Else, the node contains the edge.
            } else if (hasUnmetNode(graph)) { // Node == null and there are unmet nodes.
                // Transfer requires no unmet nodes.
                return Pair.of(null, null);
            } else { // Node == null and no unmet nodes exist.
                CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
                if (coARGEdge != null && isSimpleTransfer) {
                    // Indeterminacy exists, and it's the first time that graph meets
                    // the edge.
                    copiedGraph = handleNonDet(graph, parState, edge);
                    // graph.addVisitedAssumeEdge(curThd, edge, chOgState);
                }

                node = new OGNode(new ArrayList<>(Collections.singleton(edge)),
                        true,
                        parState,
                        chState);
                node.addEvents(sharedEvents);
                newNodeCreated = true;
            }
        }

        // assert graph != null;
        if (node != null) {
            visitNode(graph, node);
            node.updatePreAndSucState(parState, chState);
            if (!newNodeCreated && isConflict(graph, curThd, node))
                graph = null;
        }
        if (graph != null) {
            graph.setNeedToRevisit(node != null && node.shouldRevisit());
            if (node != null)
                graph.updateCurrentNodeTable(curThd, node);
            graphWrapper.clear();
        }
        if (enableDebug)
            debugActions(graph, parState, chState, edge);
        result = Pair.of(graph, copiedGraph);

        return result;
    }

    private Pair<ObsGraph, ObsGraph> handleBlockTerminated(
            List<ObsGraph> graphWrapper,
            CFAEdge edge,
            ARGState parState,
            ARGState chState,
            boolean isSimpleTransfer) {
        // Caa = END. This means edge should be a funCall and node terminates.
        assert graphWrapper.size() == 1;
        ObsGraph graph = graphWrapper.iterator().next(),
                copiedGraph = null;
        OGPORState chOgState =
                AbstractStates.extractStateByType(chState, OGPORState.class);
        assert chOgState != null;
        String curThd = chOgState.getInThread();
        OGNode node = graph.getCurrentNode(curThd);
        assert node != null; // node must be not null.
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        int edgeType = getEdgeType(sharedEvents, edge);
        boolean edgeInNode = node.contains(edge);
        Pair<ObsGraph, ObsGraph> result = null;

        if (edgeType == 0) { // Local non-assumption edge.
            if (!edgeInNode) {
                node.addEdgeWithEvents(edge, null);
            } else {
                //
            }
            // TODO: check the conflict.
        }

        else if (edgeType == 2) { // Shared non-assumption edge.
            if (!edgeInNode) {
                node.addEdgeWithEvents(edge, sharedEvents);
            } else {
                //
            }
        }

        else { // Special cases.
            // In normal cases, an assumption edge cannot terminate a node.
            // However, it will happen that an assumption edge appears as the last
            // edge of the node when an abnormal termination happens because of the
            // call of functions like 'exit()' and 'abort()'.
            if (edgeType == 1) { // local-assumption edge.
                if (node.hasBeenAddedToGraph() && edgeInNode) {
                    //
                }
                else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                    Pair<ObsGraph, ObsGraph> handleResult =
                            handleAssumeEdgeNotInNode(graph, node, edge, parState,
                                    chOgState, curThd, isSimpleTransfer, false);
                    graph = handleResult.getFirst();
                    copiedGraph = handleResult.getSecond();
                } else { // Totally new node.
                    Pair<ObsGraph, ObsGraph> handleResult =
                            handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                                    chOgState, curThd, isSimpleTransfer, true);
                    graph = handleResult.getFirst();
                    copiedGraph = handleResult.getSecond();
                }
                // edgeType == 1
            } else { // edgeType == 3, shared-assumption edge.
                if (node.hasBeenAddedToGraph() && edgeInNode) {
                    //
                } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                    Pair<ObsGraph, ObsGraph> handleResult =
                            handleAssumeEdgeNotInNode(graph, node, edge, parState,
                                    chOgState, curThd, isSimpleTransfer, true);
                    graph = handleResult.getFirst();
                    copiedGraph = handleResult.getSecond();
                } else { // Totally new node.
                    Pair<ObsGraph, ObsGraph> handleResult =
                            handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                                    chOgState, curThd, isSimpleTransfer, true);
                    graph = handleResult.getFirst();
                    copiedGraph = handleResult.getSecond();
                }
            }
        }

        if (graph != null) {
            // Even if the node has been added to the graph, we may still need to set
            // relations for the events after lhe.
            visitNode(graph, node);
            // After set relations, we need to check the conflict.
            if (isConflict(graph, curThd, node)) // Conflict exists.
                graph = null;
        }
        if (graph != null) {
            node.updatePreAndSucState(null, chState);
            node.setLoopDepth(chOgState.getLoopDepth());
            graph.setNeedToRevisit(node.shouldRevisit());
            graph.updateCurrentNodeTable(curThd, node);
            graphWrapper.clear();
            if (enableDebug)
                debugActions(graph, parState, chState, edge);
        }
        result = Pair.of(graph, copiedGraph);

        return result;
    }

    private Pair<ObsGraph, ObsGraph> handleBlockContinue(
            List<ObsGraph> graphWrapper,
            CFAEdge edge,
            ARGState parState,
            ARGState chState,
            boolean isSimpleTransfer) {
        // Caa = CONTINUE means we are inside a *complex* node.
        assert graphWrapper.size() == 1;
        ObsGraph graph = graphWrapper.iterator().next(),
                copiedGraph = null; // For handling an indeterminate assignment.
        OGPORState chOgState =
                AbstractStates.extractStateByType(chState, OGPORState.class);
        assert chOgState != null;
        String curThd = chOgState.getInThread();
        OGNode node = graph.getCurrentNode(curThd);
        assert node != null;
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        int edgeType = getEdgeType(sharedEvents, edge);
        boolean edgeInNode = node.contains(edge);
        Pair<ObsGraph, ObsGraph> handleAssumeResult = null,
                result = null;

        if (edgeType == 0) { // Local non-assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                // The node has been added to the graph.
            }
            else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                // The node has been added to the graph, but some edges get deleted
                // during the revisiting. In this case, we add the edge to the node.
                node.addEdgeWithEvents(edge, null);
            }
            else { // The node is totally new.
                // Strictly, edge shouldn't be inside the node here, because we are constructing
                // the node. But some special edges, like 'functionStartDummyEdge' may cause the
                // assertion error, because we cannot distinguish them.
                // assert !edgeInNode;
                node.addEdgeWithEvents(edge, null);
            }
            // edgeType == 0
        }

        else if (edgeType == 1) { // Local assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                //
            } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                handleAssumeResult =
                        handleAssumeEdgeNotInNode(graph, node, edge, parState,
                                chOgState, curThd, isSimpleTransfer, false);
            } // node.hasBeenAddedToGraph() && !edgeInNode
            else { // Totally new node.
                handleAssumeResult =
                        handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                                chOgState, curThd, isSimpleTransfer, false);
            }
            // edgeType == 1
        }

        else if (edgeType == 2) { // Shared non-assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                //
            }
            else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                node.addEdgeWithEvents(edge, sharedEvents);
            }
            else { // The node is totally new.
                node.addEdgeWithEvents(edge, sharedEvents);
            }
            // edgeType == 2
        }

        else { // Shared assumption edge.
            if (node.hasBeenAddedToGraph() && edgeInNode) {
                //
            }
            else if (node.hasBeenAddedToGraph() && !edgeInNode) {
                handleAssumeResult =
                        handleAssumeEdgeNotInNode(graph, node, edge, parState, chOgState,
                                curThd, isSimpleTransfer, true);
            } else { // Totally new node.
                handleAssumeResult =
                        handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                                chOgState, curThd, isSimpleTransfer, true);
            }
        }

        if (handleAssumeResult != null) {
            graph = handleAssumeResult.getFirst();
            copiedGraph = handleAssumeResult.getSecond();
        }
        if (graph != null) {
            node.updatePreAndSucState(null, chState);
            graph.setNeedToRevisit(false);
            graphWrapper.clear();
            if (enableDebug)
                debugActions(graph, parState, chState, edge);
        }
        // For copied graph, we don't update info because it doesn't transfer along the edge.
        result = Pair.of(graph, copiedGraph);

        return result;
    }

    // Check whether we need to check conflict caused by mo.
    // If we need to add some shared events to the node, then we put them into
    // toAddEvents. If we need to check conflict, we put some events into toCheckEvents.
    private void getToAddToCheckEvents(OGNode node,
            CFAEdge edge,
            List<SharedEvent> sharedEvents,
            List<SharedEvent> toAddEvents,
            List<SharedEvent> toCheckEvents) {
        toAddEvents.addAll(sharedEvents);
        int edgeStartIndex = -1, i;
        for (i = 0; i < node.getEvents().size(); i++) {
            SharedEvent e = node.getEvents().get(i);
            if (edgeStartIndex < 0) {
                if (Objects.equals(e.getInEdge(), edge)) {
                    edgeStartIndex = i;
                    i--;
                    continue;
                }
                for (Iterator<SharedEvent> it = toAddEvents.iterator(); it.hasNext();) {
                    SharedEvent ei = it.next();
                    // When ei is a read and accesses the save var with e, e will cover ei.
                    if (ei.isRead() && ei.accessSameVarWith(e))
                        it.remove();
                    // FIXME: when both ei and e are write, and they access the same
                    //  var, then ei will cover e.
                }
            } else if (Objects.equals(edge, e.getInEdge())){
                // edgeStartIndex >= 0 && edge == e.getInEdge()
                for (Iterator<SharedEvent> it = toAddEvents.iterator(); it.hasNext();) {
                    SharedEvent ei = it.next();
                    // When e and ei access the same var and have the same access type,
                    // ei will have no need to be added.
                    if (ei.accessSameVarWith(e) && (e.getAType() == ei.getAType()))
                        it.remove();
                }
            } else {
                // edgeStartIndex >= 0 && edge != e.getInEdge()
                for (Iterator<SharedEvent> it = toAddEvents.iterator(); it.hasNext();) {
                    SharedEvent ei = it.next();
                    // When both e and ei are write and access the same var. ei will be
                    // covered by e. NOTE: such e may don't exist. In that case, we will
                    //  add ei into toAddEvents (Strictly, this is not correct, but the
                    //  ei added here will be covered by other write events added later).
                    if (ei.accessSameVarWith(e)
                            && e.getAType() == SharedEvent.AccessType.WRITE
                            && ei.getAType() == SharedEvent.AccessType.WRITE)
                        it.remove();
                }
            }
        }

        if (toCheckEvents != null) {
            if (edgeStartIndex >= 0) { // Node has added some events in sharedEvents.
                for (int j = edgeStartIndex; j < node.getEvents().size(); j++) {
                    SharedEvent ej = node.getEvents().get(j);
                    if (!Objects.equals(ej.getInEdge(), edge))
                        break;
                    if (ej.isWrite()) // Only check write events.
                        toCheckEvents.add(ej);
                }
            }
            // Writes newly added should also be checked.
            toAddEvents.forEach(e -> {
                if (e.isWrite())
                    toCheckEvents.add(e);
            });
        }
    }

    private Pair<ObsGraph, ObsGraph> handleBlockStart(
            List<ObsGraph> graphWrapper,
            CFAEdge edge,
            ARGState parState,
            ARGState chState) {
        // Caa = START. This means edge should be a funCall and we will enter a node.
        assert graphWrapper.size() == 1;
        ObsGraph graph = graphWrapper.iterator().next(), copiedGraph = null;
        OGPORState chOgState =
                AbstractStates.extractStateByType(chState, OGPORState.class);
        assert chOgState != null;
        String curThd = chOgState.getInThread();
        OGNode node = graph.getCurrentNode(curThd);
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        int edgeType = getEdgeType(sharedEvents, edge);
        Pair<ObsGraph, ObsGraph> result = null;

        if (edgeType == 0) { // Local non-assumption edge.
            if (node != null) {
                // Only complex node can contain a block start edge. Besides, the node
                // should contain the edge when node != null, this means we have created
                // the node before.
                assert !node.isSimpleNode() && node.contains(edge);
                // NOTE: here is an implicit strong assumption: block start edge contains
                //  no writes.
                // We don't check the conflict until the node become complete, because
                // we can't always know which write events the node will have at this
                // time.
                // TODO: For a complete node, we could know its all write events,
                //  and check the conflict.
            }
            else { // node == null.
                // We start a new node and enter it if no conflicts exist.
                if (hasUnmetNode(graph)) {
                    // Conflicted, we need to meet some other nodes first.
                    return Pair.of(null, null);
                }
                // No unmet nodes.
                node = new OGNode(
                        new ArrayList<>(Collections.singleton(edge)),
                        false,
                        parState,
                        chState);
            }
            // edgeType == 0
        } else if (edgeType == 2) { // Shared non-assumption edge.
            if (node != null) {
                assert !node.isSimpleNode() && node.contains(edge);
                // TODO: we check the conflict only when node is complete.
            } else { // Node == null.
                if (hasUnmetNode(graph)) {
                    return Pair.of(null, null);
                }
                node = new OGNode(
                        new ArrayList<>(Collections.singleton(edge)),
                        false,
                        parState,
                        chState);
            }
        } else {
            throw new UnsupportedOperationException(
                    "Incorrect edge type: " + edgeType + ", 0 or 2 required.");
        }

        // When we get here, it means the graph is transferred successfully.
        assert graph != null && node != null;
        // update the pre/suc state.
        node.updatePreAndSucState(parState, chState);
        graph.updateCurrentNode(curThd, node);
        graph.setNeedToRevisit(node.shouldRevisit());
        result = Pair.of(graph, null);
        graphWrapper.clear();
        if (enableDebug)
            debugActions(graph, parState, chState, edge);

        return result;
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
                break;
            }
        }

        assert tmp != null : "Cannot find the coEdge of: " + edge;
        return tmp;
    }

    /**
     * Handle the case where the node has been added to the graph but the edge
     * not in the node.
     * Assume: edge = d, co-edge = !d, then d is not inside the node.
     * There are four cases, and each of them has two according to whether the edge
     * contains shared vars:
     * (1) !d is not in the ARG but the node:
     *      (A).if edge don't access shared vars, then * we need to replace
     *          !d(co-edge) with d(edge) and transfer the graph along the edge(d).
     *      (B).Else, transfer gets blocked at {@param parState} because we cannot
     *          replace an edge contains shared vars.
     * (2) !d is neither in the ARG nor the node, then add the edge(d) to the node
     *      and transfer the graph along the edge(d) no matter whether the edge
     *      contains shared vars.
     * (3) !d is in the ARG but the node, then we need to handle the indeterminacy. No
     *      whether the edge contains shared vars, we need to copy the graph.
     * (4) !d is in the ARG and the node, then transfer should stop here no matter
     *      whether the edge contains shared vars. Because we should transfer the graph
     *      along !d.
     * @param graph current ObsGraph.
     * @param node current OGNode.
     * @param edge an assumption edge.
     * @param parState antecedent ARGState of the edge.
     * @param chOgState successive OGPORState of the edge.
     * @param curThd the thread that edge in.
     * @param isSimpleTransfer whether the first time that the graph meet the edge.
     * @param isShared whether the edge contains shared vars.
     * @return Pair of the graph and its deep copy (if needed).
     */
    private Pair<ObsGraph, ObsGraph> handleAssumeEdgeNotInNode(
            ObsGraph graph,
            OGNode node,
            CFAEdge edge,
            ARGState parState,
            OGPORState chOgState,
            String curThd,
            boolean isSimpleTransfer,
            boolean isShared) {
        ObsGraph copiedGraph = null;
        CFAEdge coCFAEdge = getCoEdgeFromCFA(edge),
                coARGEdge = getCoEdgeFromARG(parState, edge);
        boolean coCFAEdgeInNode = node.contains(coCFAEdge);
        if (coCFAEdgeInNode && coARGEdge == null) { // case (1)
            if (!isShared) {
                // Replacing coCFAEdge(!d) with edge(d).
                node.replaceCoEdge(coCFAEdge, edge);
                assert node.getBlockEdges().contains(edge) :
                        "Replacing edge " + edge + "Failed.";
            } else {
                // Replacement won't happen for shared assumption edge because the graph
                // remembers which edge it has met. Therefore, transfer gets blocked here.
                throw new UnsupportedOperationException(
                        "Transfer gets blocked at " + parState);
            }
        }

        else if (!coCFAEdgeInNode && coARGEdge == null) { // case (2)
            // Replacement shouldn't happen. Add the edge(d) to the node.
            node.addEdgeWithEvents(edge, null);
        }

        else if (!coCFAEdgeInNode) { // case (3), coARGEdge != null
            // In this case indeterminacy exists.
            // When indeterminacy exists, we need to traverse both two edges.
            // This is done only when a graph meets the edge at the first
            // time, otherwise, the graph has known which edge to choose. This
            // is because the graph has stored the corresponding edge at the
            // first time.
            // Specifically, if graph G choose edge(d), then its deep copy nG will
            // choose coEdge(!d). And either of them will remember their choices.
            if (isSimpleTransfer) { // graph meets the edge first time.
                copiedGraph = handleNonDet(graph, parState, edge);
                node.addEdgeWithEvents(edge, null);
                if (!isShared) {
                    graph.addVisitedAssumeEdge(curThd, edge, chOgState);
                } else {
                    // FIXME: should we store the edge if it contains shared vars?
                }
                // Don't add coEdge(!d) for the copied graph, because the latter
                // still doesn't meet the former now.
            } else { // not the first time the graph meets the edge.
                if (!isShared) {
                    if (!graph.cachedEdgeMatch(curThd, edge, chOgState)) {
                        graph = null;
                    }
                } else {
                    // FIXME: the case where the edge contains shared vars.
                    throw new UnsupportedOperationException("Unhandled case: " +
                            "indeterminacy exists inside node during a multi-step " +
                            "transfer");
                }
            }
            // case (3)
        }
        else { // case (4), coCFAEgeInNode && coARGEdge != null
            graph = null;
        }

        return Pair.of(graph, copiedGraph);
    }

    private Pair<ObsGraph, ObsGraph> handleAssumeEdgeWithNewNode(
            ObsGraph graph,
            OGNode node,
            CFAEdge edge,
            ARGState parState,
            OGPORState chOgState,
            String curThd,
            boolean isSimpleTransfer,
            boolean isShared) {
        // It must be the first time we meet the edge.
        assert isSimpleTransfer;
        ObsGraph copiedGraph = null;
        // Copy the graph when indeterminacy exists.
        CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
        if (coARGEdge != null) { // Indeterminacy exists.
            copiedGraph = handleNonDet(graph, parState, edge);
            node.addEdgeWithEvents(edge, null);
            if (!isShared) {
                graph.addVisitedAssumeEdge(curThd, edge, chOgState);
            } else {
                // FIXME: cache the edge when it has share vars?
            }
            // Note, we don't add coARGEdge to copiedGraph here, because the
            // latter hasn't met the former yet.
        }
        return Pair.of(graph, copiedGraph);
    }

    private void debugActions(ObsGraph graph,
            ARGState parState, ARGState chState, CFAEdge edge) {

        if (graph == null) return;
        addGraphToFull(graph, chState.getStateId());
        System.out.println("Transferring from s" + parState.getStateId()
                + " -> s" + chState.getStateId() + ": " + edge);
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
     * @return pair of the target state and the graph.
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
                // Adjust the waitlist to ensure chState will be explored before its
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
     * nodes in the graph. So, one graph may have more than one trace.
     * It's regarded as a conflict if there are nodes from other threads happen before the
     * node of the current thread.
     * @return true, if conflicted.
     * @implNote we check conflict only if the node is complete.
     * FIXME: or just check whether the graph is consistent?
     */
    private boolean isConflict(ObsGraph graph, String curThd, OGNode curNode) {
        //
        Set<OGNode> otherThdNodes = new HashSet<>();
        graph.getNodeTable().forEach((k, v) -> {
            if (!curThd.equals(k) && v != null && !v.isInGraph())
                otherThdNodes.add(v);
        });

        for (OGNode otn : otherThdNodes) {
            if (otn.getFromRead().contains(curNode)
                    || porf(otn, curNode)) {
                return true;
            }
        }

        // Check mo-deduced conflicts.
        List<SharedEvent> toCheckEvents = curNode.getToCheckEvents();
        // Get mo predecessors of the events to check.
        List<Pair<SharedEvent, SharedEvent>> moPredecessors = new ArrayList<>();
        getMoPredecessors(graph, toCheckEvents, moPredecessors);

        // Find possible conflict.
        for (Pair<SharedEvent, SharedEvent> pair : moPredecessors) {
            SharedEvent ce = pair.getFirstNotNull(),    // Checked event.
                    mpe = pair.getSecondNotNull(),      // Direct mo predecessor of ce.
                    msuc = mpe.getMoBefore();           // Direct mo successor of mpe.
            // 1. There exists r (mperb) reads from mpe, but r.inNode is not the graph.
            // In this case, conflict exists, because it requires that r.inNode happen
            // before the curNode, but the former hasn't been in the graph yet.
            while (mpe != null) {
                // Check conflict.
                // FIXME: it's enough to use 'readBy' only?
                for (SharedEvent mperb : mpe.getReadBy()) {
                    OGNode mperbn = mperb.getInNode();
                    assert mperbn != null;
                    if (mperbn != curNode
                            && !mperbn.isInGraph()) { //
                        // Conflict found.
                        return true;
                    }
                }
                mpe = mpe.getMoAfter();
            }

            // 2. There exists wsuc is a mo-descendant of the mpe, s.t., for some rs
            // that reads from ce: 1) msuc porf r
            //                     2) msuc.inNode is not in the graph.
            //                     3) r.inNode is not in the graph.
            // In this case, conflict exists, because it requires that msuc.inNode happen
            // before the curNode, but the former hasn't been in the graph yet.
            for (SharedEvent rb : ce.getReadBy()) {
                while (msuc != null) {
                    // In this case, wsuc, ce and r locate different nodes respectively.
                    OGNode msucn = msuc.getInNode(), rbn = rb.getInNode();
                    if (msucn != curNode
                            &&!msucn.isInGraph()
                            && !rbn.isInGraph()
                            && porf(msucn, rbn)) {
                        // Conflict found.
                        // msuc should happen before the curNode.
                        return true;
                    }
                    msuc = msuc.getMoBefore();
                }
            }
        }

        return false;
    }

    // Get all direct mo-predecessors of events in toCheckEvents.
    private void getMoPredecessors(ObsGraph graph,
            List<SharedEvent> toCheckEvents,
            List<Pair<SharedEvent, SharedEvent>> moPredecessors) {
        if (toCheckEvents == null) return;

        // Build mo relations for builtMoEvents.
        OGNode n = graph.getLastNode();
        List<SharedEvent> toRemove = new ArrayList<>();
        while (n != null && !toCheckEvents.isEmpty()) {
            for (SharedEvent w : n.getWs()) {
                for (SharedEvent w0 : toCheckEvents) {
                    if(w.accessSameVarWith(w0)) {
                        moPredecessors.add(Pair.of(w0, w)); // Find the moPredecessor of
                        // w0.
                        toRemove.add(w0);
                    }
                }
            }

            toCheckEvents.removeAll(toRemove);
            toRemove.clear();
            n = n.getTrAfter();
        }
    }

    /**
     * When visiting a node, we add rf relations for the events that behind the
     * last-handled event of the node, update mo relations for all write events, and
     * update po for the node.
     * @param node the specific node that just terminates.
     */
    public void visitNode(ObsGraph graph, OGNode node) {
        Set<SharedEvent> rFlag = new HashSet<>(), wFlag = new HashSet<>();
        node.getRsNeedToVisit(rFlag);
        node.getWsNeedToVisit(wFlag);
        // Indicate whether we have found the predecessor(po) of the node.
        boolean preFlag = node.getPredecessor() != null;
        // Till now, the node hasn't been added to the graph, so we choose the last
        // node in the trace as the start of backtracking.
        OGNode n = graph.getLastNode();
        // Backtracking along with the trace.
        while (n != null) {
            // FIXME: is there the case where the node isn't in the graph?
            assert n.isInGraph() :
                    "Trying to visit a node not in graph when backtracking!";
            if (!preFlag && n.isPredecessorOf(node)) {
                n.setSuccessor(node);
                node.setPredecessor(n);
                preFlag = true;
            }
            if (rFlag.isEmpty() && wFlag.isEmpty()) {
                // All events in rFlag and wFlag have been handled.
                if (preFlag) {
                    // If we have found the predecessor of the node, then stop backtracking.
                    break;
                } else {
                    // Else, continue to find predecessor.
                    n = n.getTrAfter();
                    continue;
                }
            }

            graph.setRelations(n, rFlag, wFlag);
            n = n.getTrAfter();
        }

        if (!graph.contains(node)) {
            // Add the node to the graph if we visit it the first time.
            graph.addNode(node);
        }

        // Update info for node and graph.
        node.setInGraph(true);
        if (graph.getLastNode() != null) {
            graph.getLastNode().setTrBefore(node);
            node.setTrAfter(graph.getLastNode());
        }
        graph.setLastNode(node);
        graph.setTraceLen(graph.getTraceLen() + 1);
    }

    private void adjustWaitlist(Map<Integer, List<ObsGraph>> OGMap,
                                Vector<AbstractState> waitlist,
                                ARGState state) {
        int i = waitlist.indexOf(state), j = i + 1;
        for (; j < waitlist.size(); j++) {
            assert waitlist.get(j) instanceof ARGState;
            ARGState other = (ARGState) waitlist.get(j);
            // Just searching for the state's siblings that are closer to the end of the
            // waitlist.
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

    // Debug.
    public void addGraphToFull(ObsGraph graph, Integer stateId) {
        String gStr = getDotStr(graph);
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        assert ogInfo != null;
//        List<String> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
//                k -> new ArrayList<>());
//        Map<Integer, String> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
//                k -> new HashMap<>());
        List<Pair<Integer, String>> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
                k -> new ArrayList<>());
//        ogs.add(gStr);
        ogs.add(Pair.of(graph.getIdentityHash(), gStr));
    }

    // Debug.
    public void removeGraphFromFull(ObsGraph graph, Integer stateId) {
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        assert ogInfo != null;
//        Map<Integer, String> ogs = ogInfo.getFullOGMap().get(stateId);
        List<Pair<Integer, String>> ogs = ogInfo.getFullOGMap().get(stateId);
//        assert ogs != null && ogs.containsKey(graph.getIdentityHash()) :
//                "Missing graph at s" + stateId;
        assert ogs != null : "Missing graph a s" + stateId;
        int index = -1, i = 0;
        for (; i < ogs.size(); i++) {
            if (Objects.equals(ogs.get(i).getFirst(), graph.getIdentityHash())) {
                index = i;
                break;
            }
        }
        assert index >= 0 : "Missing graph a s" + stateId;
//        ogs.remove(graph.getIdentityHash());
        ogs.remove(index);
    }
}
