package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DeepCopier;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;
import scala.concurrent.impl.FutureConvertersImpl;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.porf;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.setRelation;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer.CriticalAreaAction.*;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer.LockStatus.LOCK;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer.LockStatus.UNLOCK;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;

public class OGTransfer {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final Map<Integer, List<SharedEvent>> edgeVarMap;
    private final DeepCopier copier = new DeepCopier();
    private final Set<String> atomicBegins = ImmutableSet.of("__VERIFIER_atomic_begin");
    private final Set<String> atomicEnds = ImmutableSet.of("__VERIFIER_atomic_end");
    private final Set<String> lockBegins = ImmutableSet.of("pthread_lock", "lock");
    private final Set<String> lockEnds = ImmutableSet.of("pthread_unlock", "unlock");

    protected enum CriticalAreaAction {
        START,
        CONTINUE,
        END
    }

    protected enum LockStatus {
        LOCK,
        UNLOCK
    }

    private final NLTComparator nltcmp = new NLTComparator();

    public OGTransfer(Map<Integer, List<ObsGraph>> pOGMap,
                      Map<Integer, OGNode> pNodeMap,
            Map<Integer, List<SharedEvent>> pEdgeVarMap) {
        this.OGMap = pOGMap;
        this.nodeMap = pNodeMap;
        this.edgeVarMap = pEdgeVarMap;
    }

    public NLTComparator getNltcmp() {
        return nltcmp;
    }


    /**
     * Compute whether there exists nondeterminism among the edges from parState to
     * successors.
     * @return List of the  assume-edge successors that belong to current thread and have
     * the same predecessor.
     */
    public List<AbstractState> hasNonDet(ARGState parState,
                                     Collection<? extends AbstractState> successors) {
        if (successors.size() < 2)  return new ArrayList<>();
        OGPORState parOgState = AbstractStates.extractStateByType(parState, OGPORState.class);
        Preconditions.checkArgument(parOgState != null, "OGPORCPA required.");
        String curThread = parOgState.getInThread();
        Preconditions.checkArgument(curThread != null,
                "Try to obtain current thread failed.");
        List<AbstractState> nonDetSucs = new ArrayList<>(), tmp;
        tmp = successors.stream()
                .filter(s -> {
                    OGPORState ogState = AbstractStates.extractStateByType(s,
                            OGPORState.class);
                    String thread = ogState.getInThread();
                    return curThread.equals(thread);
                })
                .filter(s -> {
                    CFAEdge edge = parState.getEdgeToChild((ARGState) s);
                    return edge instanceof AssumeEdge;
                })
                .collect(Collectors.toList());

        if (tmp.size() == 2) {
            nonDetSucs.addAll(tmp);
        }
        return nonDetSucs;
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
     * This method transfer a given graph from a parent state {@parState} to its child
     * State {@chState}. If the node conflict with the graph, then transfer stops
     * and return null. Else, return the transferred graph.
     * When no conflict exists, there are still two possible cases need to considered:
     * 1) The graph has contained the node. In this case, we update the new last node of
     * the graph (also update necessary relations like mo, etc).
     * 2) The graph meet the node first time. In this case, we add the node to the graph
     * and add all necessary relations, like rf, fr, wb and so on.
     * @implNode When add the node to the graph, we add its deep copy.
     * @param graphWrapper
     * @param edge
     * @param parState Initial {@ARGState} where the transferring begin.
     * @param chState Final {@ARGState} where the transferring stop.
     * @return Transferred graph if no conflict found, else null.
     */
    // FIXME
    public ObsGraph singleStepTransfer(List<ObsGraph> graphWrapper,
                                       CFAEdge edge,
                                       ARGState parState, /* lead state */
                                       ARGState chState) {
        // Debug.
        boolean debug = true;

        Preconditions.checkArgument(graphWrapper.size() == 1);
        ObsGraph graph = graphWrapper.iterator().next();
        OGPORState chOgState = AbstractStates.extractStateByType(chState, OGPORState.class),
                parOgState = AbstractStates.extractStateByType(parState, OGPORState.class);
        assert chOgState != null && parOgState != null;
        String curThread = chOgState.getInThread();

        // Get OGNode for the current thread.
        OGNode node = graph.getCurrentNode(curThread);
        List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
        Stack<String> locks = chOgState.getLocks().get(curThread);

        if (node == null) {
            // No node for the current thread.
            if (hasUnmetNode(graph)) {
                // If there still are some nodes from other threads not visited, we
                // should visit them first, i.e., we shouldn't transfer the graph.
                return null;
            } else {
                // No unmet nodes.
                if (sharedEvents == null || sharedEvents.isEmpty()) {
                    // Edge has no shared events.
                    Pair<LockStatus, String> l = getLock(edge);
                    if (l != null) {
                        // The edge contains some lock.
                        CriticalAreaAction action = handleLock(locks, l);
                        switch (action) {
                            case START:
                                OGNode newNode = new OGNode(edge,
                                        new ArrayList<>(Collections.singleton(edge)),
                                        true,
                                        false);
                                newNode.setThreadInfo(chState);
//                                chOgState.setNodeTable(parOgState.getNodeTable());
//                                chOgState.updateNode(curThread, newNode);
                                updatePreSucState(edge, newNode, parState, chState);
                                graph.setNeedToRevisit(false);
                                graph.updateCurrentNode(curThread, newNode);
                                graphWrapper.clear();

                                if (debug) debugActions(graph, parState, chState, edge);
                                return graph;
                            case CONTINUE:
                                throw new UnsupportedOperationException("Nesting locks " +
                                        "is not allowed when node == null: " + edge);
                            case END:
                                throw new UnsupportedOperationException("Unlocking " +
                                        "is not allowed when node == null: " + edge);
                            default:
                                throw new UnsupportedOperationException("Missing " +
                                        "action: " + edge);
                        }
                    } else {
                        // Else, the edge just accesses the non-lock local vars.
                        // We just need to transfer graph from parState to chSate.
                        graph.setNeedToRevisit(false);
                        graphWrapper.clear();

                        // Debug.
                        if (debug) debugActions(graph, parState, chState, edge);
                        return graph;
                    }
                } else {
                    // The edge has some shared events.
                    // TODO: Shared lock begin or end.
                    Pair<LockStatus, String> l = getLock(edge);
                    if (l != null) {
                        CriticalAreaAction action = handleLock(locks, l);
                        switch (action) {
                            case START:
                                OGNode newNode = new OGNode(edge,
                                        new ArrayList<>(Collections.singleton(edge)),
                                        true,
                                        false);
                                newNode.setThreadInfo(chState);
//                                chOgState.setNodeTable(parOgState.getNodeTable());
//                                chOgState.updateNode(curThread, newNode);
                                updatePreSucState(edge, newNode, parState, chState);
                                graph.updateCurrentNode(curThread, newNode);
                                graph.setNeedToRevisit(false);
                                graphWrapper.clear();
                                if (debug) debugActions(graph, parState, chState, edge);
                                return graph;
                            case CONTINUE:
                                throw new UnsupportedOperationException("Nesting locks " +
                                        "is not allowed when node == null: " + edge);
                            case END:
                                throw new UnsupportedOperationException("Unlocking " +
                                        "is not allowed when node == null: " + edge);
                            default:
                                throw new UnsupportedOperationException("Missing " +
                                        "action: " + edge);
                        }
                    } else {
                        // The edge contains non-lock shared vars.
                        OGNode newNode = new OGNode(edge,
                                new ArrayList<>(Collections.singleton(edge)),
                                true,
                                false);
                        newNode.addEvents(sharedEvents);
                        newNode.setThreadInfo(chState);
//                        chOgState.setNodeTable(parOgState.getNodeTable());
//                        chOgState.updateNode(curThread, null);
                        updatePreSucState(edge, newNode, parState, chState);
                        visitNode(graph, newNode, false);

                        graph.updateCurrentNode(curThread, null);
                        graph.setNeedToRevisit(true);
                        graphWrapper.clear();

                        if (debug) debugActions(graph, parState, chState, edge);
                        return graph;
                    }
                }
            }
        } else {
            // node != null.
            int idx = node.contains(edge);
            boolean isInsideNode = isInsideNode(node, edge);

            if (isConflict(graph, curThread, node)) {
                // Conflict means we should visit nodes of other threads first. Despite
                // this, we could still transfer the graph since the current thread is
                // equal to the thread of the node. The difference is that the node
                // table doesn't change at all after the transfer.
                if (idx >= 0 || isInsideNode) {
                    return null;
                } else {
                    // The edge just accesses local vars, we transfer graph simply
                    // without any change to nodeTable.
                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (debug) debugActions(graph, parState, chState, edge);
                    return graph;
                }
            }

            if (edge instanceof AssumeEdge) {
                // Handle conditional statements.
                if (isInsideNode) {
                    if (idx < 0) {
                        // The node doesn't contain the edge. Because the edge is an
                        // assumption and inside the node, we need to replace it with its
                        // coEdge.The process of replacing the coEdge will also add the
                        // coEdge and all possible shared events to blockEdges and events
                        // of the node, separately.
                        edge = node.replaceCoEdge(edgeVarMap, edge);
                    } else if (node.isSimpleNode()){
                        updatePreSucState(edge, node, parState, chState);
                        visitNode(graph, node, true);
                        graph.updateCurrentNodeTable(curThread, node);
                    }
                }

//                chOgState.setNodeTable(parOgState.getNodeTable());
                node.setLastVisitedEdge(edge);
                graph.setNeedToRevisit(false);
                graphWrapper.clear();

                if (debug) debugActions(graph, parState, chState, edge);
                return graph;
            } else {
                // Handle non-conditional statements.
                Pair<LockStatus, String> l = getLock(edge);
                if (l != null) {
                    CriticalAreaAction action = handleLock(locks, l);
                    switch (action) {
                        case START:
                            throw new UnsupportedOperationException("Cannot " +
                                    "start a new node when there has been one: " + edge);
                        case CONTINUE:
                            if (sharedEvents != null && !sharedEvents.isEmpty()) {
                                node.addEvents(sharedEvents);
                            }
                            if (idx < 0) {
                                // The node does not contain the edge.
                                node.getBlockEdges().add(edge);
                            }
                            node.setLastVisitedEdge(edge);
//                            chOgState.setNodeTable(parOgState.getNodeTable());
                            graph.setNeedToRevisit(false);
                            graphWrapper.clear();

                            if (debug) debugActions(graph, parState, chState, edge);
                            return graph;
                        case END:
//                            chOgState.setNodeTable(parOgState.getNodeTable());
                            if (idx < 0) {
                                if (sharedEvents != null && !sharedEvents.isEmpty()) {
                                    node.addEvents(sharedEvents);
                                }
                                node.getBlockEdges().add(edge);
                                visitNode(graph, node, false);
                                if (node.shouldRevisit()) {
                                    graph.setNeedToRevisit(true);
                                }
                            } else {
                                // Update the current nodes for threads.
//                                chOgState.updateNodeTable(curThread, node);
                                graph.updateCurrentNodeTable(curThread, node);
                                visitNode(graph, node, true);
                            }

                            node.setLastVisitedEdge(edge);
                            updatePreSucState(edge, node, parState, chState);
                            graphWrapper.clear();

                            if (debug) debugActions(graph, parState, chState, edge);
                            return graph;
                        default:
                            throw new UnsupportedOperationException("Missing " +
                                    "action: " + edge);
                    }
                } else {
                    // Non-lock edge.
//                    chOgState.setNodeTable(parOgState.getNodeTable());
                    if (isInsideNode) {
                        // If the node is simple, update the current node.
                        if (node.isSimpleNode()) {
//                        chOgState.updateNodeTable(curThread, node);
                            updatePreSucState(edge, node, parState, chState);
                            visitNode(graph,node, true);
                            graph.updateCurrentNodeTable(curThread, node);
                        } else if (idx < 0) {
                            // We continue to add the edge to the current node, at the
                            // same time we add the shared events (if existed), too.
                            if (sharedEvents != null && !sharedEvents.isEmpty()) {
                                node.addEvents(sharedEvents);
                            }
                            node.getBlockEdges().add(edge);
                        }
                        node.setLastVisitedEdge(edge);
                    }

                    graph.setNeedToRevisit(false);
                    graphWrapper.clear();

                    if (debug) debugActions(graph, parState, chState, edge);
                    return graph;
                }
            }
        }
    }

    private boolean isInsideNode(OGNode node, CFAEdge edge) {
        if (node.isSimpleNode()) {
            return node.contains(edge) == 0;
        } else {
            return node.getLastVisitedEdge() != null
                    && node.contains(node.getLastVisitedEdge()) > 0;
        }
    }

    // TODO
    private CriticalAreaAction handleLock(Stack<String> locks,
            Pair<LockStatus, String> pL) {
        LockStatus lockStatus = pL.getFirstNotNull();
        String l = pL.getSecondNotNull();

        if (locks.isEmpty()) {
            if (lockStatus == UNLOCK) {
                throw new UnsupportedOperationException("Unlocking " +
                        "is not allowed when no lock held.");
            }
            locks.push(l);
            return START;
        } else {
            String l0 = locks.peek();
            if (Objects.equals(l, l0)) {
                if (lockStatus == LOCK) {
                    // TODO: Try to acquire a holding lock.
                    return CONTINUE;
                } else {
                    // Unlock.
                    locks.pop();
                    return locks.isEmpty() ? END : CONTINUE;
                }
            } else {
                if (lockStatus == LOCK) {
                    locks.push(l);
                    return CONTINUE;
                } else {
                    // Unlock.
                    if (locks.contains(l)) {
                        // Critical area preserved.
                        locks.remove(l);
                        Preconditions.checkArgument(!locks.isEmpty(),
                                "Unlocking a lock that doesn't at the top of " +
                                        "lock stack makes locks not empty");
                        return CONTINUE;
                    } else {
                        throw new UnsupportedOperationException("Unlocking " +
                                "is not allowed when no lock held.");
                    }
                }
            }
        }
    }

    // Get the name of the lock var form the given edge.
    private Pair<LockStatus, String> getLock(CFAEdge edge) {

        if (!(edge instanceof CFunctionCallEdge)) {
            return null;
        }

        CFunctionCallEdge cFuncEdge = (CFunctionCallEdge) edge;

        if (hasAtomicBegin(edge)) {
            return Pair.of(LOCK, "__VERIFIER_atomic_begin");
        }
        if (hasAtomicEnd(edge)) {
            return Pair.of(UNLOCK,"__VERIFIER_atomic_end");
        }

        if (hasLockBegin(edge)) {
            return Pair.of(LOCK, getLockVarName(cFuncEdge));
        }
        if (hasLockEnd(edge)) {
            return Pair.of(UNLOCK, getLockVarName(cFuncEdge));
        }

        return null;
    }

    private String getLockVarName(CFunctionCallEdge edge) {
        List<? extends AExpression> arguments = edge.getArguments();
        Preconditions.checkArgument(arguments.size() == 1,
                "More than one vars in lock function is not supported." + edge);
        AExpression arg = arguments.iterator().next();
        Preconditions.checkArgument(arguments instanceof CUnaryExpression);
        CUnaryExpression unaryExpression = (CUnaryExpression) arg;
        CIdExpression lockIdExpression = (CIdExpression) unaryExpression.getOperand();
        return lockIdExpression.getName();
    }

    private void debugActions(ObsGraph graph,
            ARGState parState, ARGState chState, CFAEdge edge) {

        addGraphToFull(graph, chState.getStateId());
        System.out.println("Transferring from s" + parState.getStateId()
                + " -> s" + chState.getStateId() + ": " + edge);
    }

    private boolean hasLockEnd(CFAEdge cfaEdge) {
        for (String le : lockEnds)
            if (cfaEdge.getRawStatement().contains(le)) return true;
        return false;
    }

    private boolean hasAtomicEnd(CFAEdge cfaEdge) {
        for (String ae : atomicEnds)
            if (cfaEdge.getRawStatement().contains(ae)) return true;
        return false;
    }

    private boolean hasLockBegin(CFAEdge cfaEdge) {
        for (String lb : lockBegins)
            if (cfaEdge.getRawStatement().contains(lb)) return true;
        return false;
    }

    private boolean hasAtomicBegin(CFAEdge cfaEdge) {
        for (String ab : atomicBegins)
            if (cfaEdge.getRawStatement().contains(ab)) return true;
        return false;
    }

    private int getLoopDepth(ARGState chState) {
        OGPORState ogState = AbstractStates.extractStateByType(chState, OGPORState.class);
        Preconditions.checkState(ogState != null);
        return ogState.getLoopDepth();
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
            ObsGraph chGraph = singleStepTransfer(graphWrapper, etp, leadState, chState);
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
            ObsGraph chGraph = singleStepTransfer(graphWrapper, etp, leadState, chState);
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
    /**
     * Update the last node and calculate trace order and modify order for it.
     * This function is called only when the new last node has already been in the graph.
     * @param idx gives the index of the new last node in the graph.
     */
    // FIXME
    private void updateLastNode(ObsGraph graph, int idx, ARGState newPreState,
                                ARGState newSucState) {
        OGNode nLast = graph.getNodes().get(idx),
                oLast = graph.getLastNode();
        nLast.setPreState(newPreState);
        nLast.setSucState(newSucState);
        nLast.setInGraph(true);
        graph.setLastNode(nLast);
        // oLast -- trBefore ->  nLast
        nLast.setTrAfter(oLast);
        // oLast may be null.
        if (oLast != null) oLast.setTrBefore(nLast);
        graph.setTraceLen(graph.getTraceLen() + 1);

        // Update mo for the new last node (nLast) by backtracking along the trace.
        Set<SharedEvent> wSet = new HashSet<>(nLast.getWs()), toRemove = new HashSet<>();
        OGNode tracePre = nLast.getTrAfter();
        while (tracePre != null) {
            for (SharedEvent wp : tracePre.getWs()) {
                for (SharedEvent w : wSet) {
                    if (w.accessSameVarWith(wp)) {
                        // wp <_mo w.
                        setRelation("mo", graph, wp, w);
                        toRemove.add(w);
                        break;
                    }
                }
            }
            wSet.removeAll(toRemove);
            tracePre = tracePre.getPredecessor();
        }
    }

    private void visitNode(ObsGraph graph, OGNode node,
//                            ARGState newPreState,
//                            ARGState newSucState,
            boolean hasVisited) {
        // 1.1 Add rf, mo, fr relations if the node is visited the first time.
        // For the visited nodes, just updating mo.
        Set<SharedEvent> rFlag = new HashSet<>(), wFlag = new HashSet<>(node.getWs());
        if (!hasVisited) {
            rFlag.addAll(node.getRs());
        }
        // Whether we have found the predecessor of the node.
        boolean preFlag = node.getPredecessor() != null;
        OGNode n = graph.getLastNode();
        // Backtracking along with the trace.
        while (n != null) {
            if (!preFlag && (n.getInThread().equals(node.getInThread())
                    || !n.getThreadLoc().containsKey(node.getInThread()))) {
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

        // 1.2 Add the node to the graph if we visit it the first time.
        if (!hasVisited) {
            assert !graph.getNodes().contains(node)
                    : "Try to add a node that has been added before";
            graph.getNodes().add(node);
        }

        // 2. Update the info for the node and graph.
        node.setInGraph(true);
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