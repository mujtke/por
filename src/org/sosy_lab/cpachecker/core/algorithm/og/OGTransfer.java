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
            OGPORState chOgState,
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
                        OGNode currentNode =
                                copiedGraph.getCurrentNode(chOgState.getInThread());
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
     * This method transfers a given graph from a parent state {@parState} to its child
     * State {@chState}. If the node conflicts with the graph, then transfer stops
     * and returns null. Else, return the transferred graph.
     * When no conflict exists, there are still two possible cases need to be considered:
     * 1) The graph has contained the node. In this case, we update the new last node of
     * the graph (also update the necessary relations like mo, etc.).
     * 2) The graph meets the node first time. In this case, we add the node to the graph
     * and add all necessary relations, like rf, fr, wb and so on.
     * @implNode When add the node to the graph, we add its deep copy.
     * @param graphWrapper
     * @param edge
     * @param parState Initial {@ARGState} where the transferring begin.
     * @param chState Final {@ARGState} where the transferring stop.
     * @return Transferred graph if no conflict found, else null.
     */
    // FIXME
    public Pair<ObsGraph, ObsGraph> singleStepTransfer(
            List<ObsGraph> graphWrapper,
            CFAEdge edge,
            ARGState parState, /* lead state */
            ARGState chState,
            boolean isSimpleTransfer) {
        // Debug.
        boolean debug = true;
        int parId = parState.getStateId(), chId = chState.getStateId();

        Preconditions.checkArgument(graphWrapper.size() == 1);
        ObsGraph graph = graphWrapper.iterator().next(), copiedGraph = null;
        OGPORState chOgState = AbstractStates.extractStateByType(chState, OGPORState.class),
                parOgState = AbstractStates.extractStateByType(parState, OGPORState.class);
        assert chOgState != null && parOgState != null;
        String curThread = chOgState.getInThread();

        // Get OGNode for the current thread.
        OGNode node = graph.getCurrentNode(curThread);
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        CriticalAreaAction criticalAreaAction = chOgState.getInCaa();
        boolean isNormalEdge = chOgState.enteringEdgeIsNormal(),
                hasSharedVars = !(sharedEvents == null || sharedEvents.isEmpty()),
                isAssumeEdge = edge instanceof AssumeEdge,
                hasNonDet = isAssumeEdge && hasNonDet(parState, edge);

        if (node == null) {
            // No node for the current thread.
            if (isNormalEdge) {
                // If the edge is normal, then we transfer the graph directly.
                // Though there still exist some nodes that come from other threads and
                // should be visited by us first, we still can transfer the graph.
                // If the edge is just normal, i.e., it neither accesses any shared
                // vars nor begins any atomic block.

                // Handle possible assume edge.
                // FIXME: how to get rid of redundancy?
                if (isAssumeEdge && hasNonDet) {
                    if (isSimpleTransfer) {
                        copiedGraph = handleNonDet(graph, parState, chOgState, edge,
                                true);
                        graph.addVisitedAssumeEdge(curThread, edge, chOgState);
                    } else {
                        // Multiple-step transfer.
                        if (!graph.matchCachedEdge(curThread, edge, chOgState)) {
                            graph = null;
                        }
                    }
                }

                if (graph != null) {
                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();
                    if (debug) debugActions(graph, parState, chState, edge);
                }

                return Pair.of(graph, copiedGraph);
            } else {
                // Else, abnormal edge (Accesses shared vars or begins a atomic block).
                // Transferring requires no unmet nodes.
                if (hasUnmetNode(graph)) {
                    return Pair.of(null, null);
                } else {
                    // No unmet nodes.
                    if (!hasSharedVars) {
                        // The edge has no shared events. In this case, the edge should
                        // be a fun call since it is not normal.
                        assert criticalAreaAction == START : "Invalid critical area: " +
                                criticalAreaAction + " when current node is null, for " +
                                "edge: " + edge;
                        OGNode newNode = new OGNode(edge,
                                new ArrayList<>(Collections.singleton(edge)),
                                false,
                                false);
                        newNode.setThreadInfo(chState);
                        updatePreSucState(edge, newNode, parState, chState);
                        graph.setNeedToRevisit(false);
                        graph.updateCurrentNode(curThread, newNode);
                        graphWrapper.clear();

                        if (debug) debugActions(graph, parState, chState, edge);
                        return Pair.of(graph, null);
                    } else {
                        // The edge has some shared events. In this case, the edge is
                        // not normal, which means the edge may be a fun call or just a
                        // simple edge.
                        OGNode newNode;
                        switch (criticalAreaAction) {
                            case START:
                                newNode = new OGNode(edge,
                                        new ArrayList<>(Collections.singleton(edge)),
                                        false,
                                        false);
                                newNode.setThreadInfo(chState);
                                updatePreSucState(edge, newNode, parState, chState);
                                graph.updateCurrentNode(curThread, newNode);
                                graph.setNeedToRevisit(false);
                                graphWrapper.clear();

                                if (debug) debugActions(graph, parState, chState, edge);
                                return Pair.of(graph, null);
                            case CONTINUE:
                                throw new UnsupportedOperationException("Nesting locks " +
                                        "is not allowed when current node is null: " + edge);
                            case END:
                                throw new UnsupportedOperationException("Unlocking is " +
                                        "not allowed when current node is null: " + edge);
                            case NOT_IN:
                                // The edge contains some non-lock shared vars.
                                newNode = new OGNode(edge,
                                        new ArrayList<>(Collections.singleton(edge)),
                                        true,
                                        false);
                                newNode.addEvents(sharedEvents);
                                newNode.setThreadInfo(chState);
                                updatePreSucState(edge, newNode, parState, chState);
                                visitNode(graph, newNode, chOgState, false);

                                graph.updateCurrentNode(curThread, null);
                                graph.setNeedToRevisit(true);
                                graphWrapper.clear();

                                if (debug) debugActions(graph, parState, chState, edge);
                                if (isAssumeEdge && hasNonDet) {
                                    if (isSimpleTransfer) {
                                        copiedGraph = handleNonDet(graph, parState,
                                                chOgState, edge, true);
                                    }
                                    // Else?
                                }
                                return Pair.of(graph, copiedGraph);
                            default:
                                throw new UnsupportedOperationException(
                                        "Missing action: " + edge);
                        }
                    }
                }
            }
        }

        else {
            // Node != null.
            // Indicate whether we will enter, have been inside or still haven't reached
            // the start of the node.
            Triple<Integer, CFAEdge, Boolean> checkPosition = isInsideNode(parState,
                    node, edge, hasNonDet, sharedEvents, criticalAreaAction);
            assert checkPosition.getFirst() != null && checkPosition.getThird() != null;
            int position = checkPosition.getFirst();
            edge = checkPosition.getSecond();
            boolean edgeHasBeenVisited = checkPosition.getThird();

            if (position == 0) {
                // We will enter the node. In this case, we need to consider all possible
                // conflicts before entering.
                if (isConflict(graph, curThread, node)) {
                    // Conflict means we should visit nodes of other threads first.
                    return Pair.of(null, null);
                } else {
                    if (node.isSimpleNode()) {
                        // For the simple node, we have also reached its end.
                        assert criticalAreaAction == NOT_IN : "No critical area for a " +
                                "simple node is required.";
                        updatePreSucState(edge, node, parState, chState);
                        visitNode(graph,node, chOgState, true);
                        graph.updateCurrentNodeTable(curThread, node);
                        // FIXME: should we revisit for the substituted assumption edge?
                    } else {
                        assert criticalAreaAction == START : "Require START critical " +
                                "area action when entering a complex node.";
                    }

                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (debug) debugActions(graph, parState, chState, edge);
                    return Pair.of(graph, null);
                }
            } else if (position > 0) {
                // We have entered a complex node.
                assert criticalAreaAction == CONTINUE || criticalAreaAction == END :
                        "Only CONTINUE or END is allowed inside the current node: " + edge;
                if (criticalAreaAction == END) {
                    if (!edgeHasBeenVisited) {
                        visitNode(graph, node, chOgState, false);
                        if (node.shouldRevisit())
                            graph.setNeedToRevisit(true);
                    } else {
                        visitNode(graph, node, chOgState, true);
                    }

                    node.setLastVisitedEdge(null);
                    // Update the current nodes for threads.
                    graph.updateCurrentNodeTable(curThread, node);
                    updatePreSucState(edge, node, parState, chState);
                } else {
                    // CONTINUE
                    // We are inside a complex node, and the node contains the edge.
                    if (edgeHasBeenVisited) node.setLastVisitedEdge(edge);
                    graph.setNeedToRevisit(false);
                    if (isAssumeEdge) {
                        if (isSimpleTransfer) {
                            copiedGraph = handleNonDet(graph, parState, chOgState, edge,
                                    hasNonDet);
                            if (!hasSharedVars) {
                                graph.addVisitedAssumeEdge(curThread, edge, chOgState);
                            }
                        } else {
                            // Shared-vars edge?
                            if (!hasSharedVars &&
                                    !graph.matchCachedEdge(curThread, edge, chOgState)) {
                                graph = null;
                            }
                        }
                    }
                }

                graphWrapper.clear();

                if (debug) debugActions(graph, parState, chState, edge);
                return Pair.of(graph, copiedGraph);
            } else {
                // >>>>>
                if (criticalAreaAction == CONTINUE) {
                    // FIXME
                    // We are inside some node, but the graph cannot transfer along the
                    // edge because the latter is not inside the node.
                    return Pair.of(null, null);
                }
                // <<<<<
                // Else, we haven't entered the node yet, i.e., we still haven't met the
                // start edge of the node.
                assert criticalAreaAction == NOT_IN : "Requiring NOT_IN, but "
                        + criticalAreaAction + " provided when handling the edge: " + edge;

                if (!isNormalEdge) {
                    // TODO
                    assert node.isSimpleNode() : "Single abnormal edge must be " +
                            "corresponding to  a simple node: " + edge;
                    // This means the node doesn't contain the edge.
                    return Pair.of(null, null);
                } else if (isAssumeEdge && hasNonDet){
                    if (!graph.matchCachedEdge(curThread, edge, chOgState)) {
                        graph = null;
                    }
                }

                // Else, The edge should be normal.
                // Just transfer the graph without changing the nodeTable.
                if (graph != null) {
                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();
                    if (debug) debugActions(graph, parState, chState, edge);
                }

                return Pair.of(graph, null);
            }
        }
    }

    private @NonNull Triple<Integer, CFAEdge, Boolean> isInsideNode(
            ARGState parState,
            OGNode node,
            CFAEdge edge,
            boolean hasNonDet,
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
