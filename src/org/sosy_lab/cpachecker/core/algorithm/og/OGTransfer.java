package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DeepCopier;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.porf;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.setRelation;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;

public class OGTransfer {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final DeepCopier copier = new DeepCopier();

    private final NLTComparator nltcmp = new NLTComparator();
    public OGTransfer(Map<Integer, List<ObsGraph>> pOGMap,
                      Map<Integer, OGNode> pNodeMap) {
        this.OGMap = pOGMap;
        this.nodeMap = pNodeMap;
    }

    public NLTComparator getNltcmp() {
        return nltcmp;
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
        Preconditions.checkArgument(graphWrapper.size() == 1);
        ObsGraph graph = graphWrapper.iterator().next();
        OGNode node = nodeMap.get(edge.hashCode());
        // If the edge is in a block but not the first one of it, then we just transfer
        // the graph simply. Also, for the edge that doesn't access global variables.
        // For a block, the first edge decides whether we could transfer the graph.
        if (node == null /* No OGNode for the edge. */) {
            // Transfer graph from parState to chSate simply. I.e., just return the graph.
            graph.setNeedToRevisit(false);
            // Debug.
            addGraphToFull(graph, chState.getStateId());
            graphWrapper.clear();
            return graph;
        }

        // Whether the graph contains the node.
        // FIXME: only use depth is enough?
        int loopDepth = getLoopDepth(chState), idx = graph.contain(node, loopDepth);
        // Whether there is any node in the graph still unmet.
        boolean hasNodeUnmet = hasUnmetNode(graph);
        // In the case that the graph still has some nodes unmet and doesn't contain the
        // current 'node', just skip the 'node' simply.
        if (hasNodeUnmet && (idx < 0)) return null;

        // no unmet nodes or idx >= 0.
        if (idx >= 0) {
            // The node has been in the graph.
            OGNode innerNode = graph.getNodes().get(idx);
            if (!node.isSimpleNode() && !edge.equals(node.getBlockStartEdge())) {
                // Debug.
                addGraphToFull(graph, chState.getStateId());
                // Inside the non-simple node, just return.
                updatePreSucState(edge, innerNode, parState, chState);
                graphWrapper.clear();
                return graph;
            }
            // Not inside the non-simple node, check the conflict.
            if (isConflict(graph, node, idx)) {
                // If some other nodes should be 'added' to the graph before the node but
                // still not be, then the transfer is not allowed.
                return null;
            }
            // Update the last node for the graph.
            // This will also update the mo relations for the last node.
            updateLastNode(graph, idx,
                    node.isSimpleNode() ? parState : node.getPreState(),
                    chState);
            // Update the loop depth and thread info.
            innerNode.setLoopDepth(loopDepth);
//            innerNode.setThreadInfo(chState);
            // In this case, needn't revisit the graph.
            graph.setNeedToRevisit(false);
        } else {
            if (node.isSimpleNode() || edge.equals(node.getBlockStartEdge())) {
                // The node is not in the graph.
                if (isConflict(graph, node, -1)) {
                    // Some other nodes should happen before the node.
                    return null;
                }
            }
            if (!node.isSimpleNode() && !edge.equals(node.getLastBlockEdge())) {
                graph.setNeedToRevisit(false);
                // Debug.
                addGraphToFull(graph, chState.getStateId());
                updatePreSucState(edge, node, parState, chState);
                graphWrapper.clear();
                return graph;
            }
            // Add the node to the graph.
            // If we add a new node to the graph, we should use its deep copy.
            OGNode newNode = copier.deepCopy(node);
            // Update the loopDepth.
            newNode.setLoopDepth(loopDepth);
//            newNode.setThreadInfo(chState);
            // Debug.
//            System.out.println("\u001b[32m" + newNode + "\u001b[0m");
            updatePreSucState(edge, newNode, parState, chState);
//            getDot(graph);
//            System.out.println("");
            addNewNode(graph, newNode,
                    node.isSimpleNode() ? parState : node.getPreState(),
                    chState);
//            getDot(graph);
//            System.out.println("");
        }

        // Debug.
        addGraphToFull(graph, chState.getStateId());
        graphWrapper.clear();
        return graph;
    }

    private int getLoopDepth(ARGState chState) {
        OGPORState ogState = AbstractStates.extractStateByType(chState, OGPORState.class);
        Preconditions.checkState(ogState != null);
        return ogState.getLoopDepth();
    }

    private void updatePreSucState(CFAEdge edge, OGNode node, ARGState parState,
                                ARGState chState) {
        if (node.isSimpleNode() /* Simple node. */) {
            // Update the preState and SucStat for the node, if it's not null;
            node.setPreState(parState);
            node.setSucState(chState);
        } else { // Not simple node.
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
     *
     * @param graphWrapper A container used to justify whether we should stop the
     *                     enumeration for the states in the inWait or notInWait. If
     *                     the container has no graph anymore, which means the graph has
     *                     been transferred, then there is no need to handle the left
     *                     states.
     */
    public void multiStepTransfer(Vector<AbstractState> waitlist,
                                  ARGState leadState,
                                  List<ObsGraph> graphWrapper) {
        Preconditions.checkArgument(graphWrapper.size() == 1, "one and only one graph " +
                "in graphWrapper is allowed.");
        // Divide children of leadState into two parts: in the waitlist and not.
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
            if (graphWrapper.isEmpty()) return;
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            // assert node != null: "Could not find OGNode for edge " + etp;
            ObsGraph chGraph = singleStepTransfer(graphWrapper, etp, leadState, chState);
            if (chGraph != null) {
                // Transfer stop, we have found the target state.
                OGMap.putIfAbsent(chState.getStateId(), new ArrayList<>());
                List<ObsGraph> chGraphs = OGMap.get(chState.getStateId());
                chGraphs.add(chGraph);
                // Adjust waitlist to ensure chState will be explored before its
                // siblings that has no graphs.
                adjustWaitlist(OGMap, waitlist, chState);
                return;
            }
        }
        // Handel states not in the waitlist.
        for (ARGState chState : notInWait) {
            if (graphWrapper.isEmpty()) return;
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            // assert node != null: "Could not find OGNode for edge " + etp;
            ObsGraph chGraph = singleStepTransfer(graphWrapper, etp, leadState, chState);
            if (chGraph != null) {
                if (chState.getChildren().isEmpty()) {
                    // FIXME: chState may be neither in the waitlist nor have any child.
                    // In this case, should we add the chState to the waitlist again?
                    // At the same time, when we can add states to waitlist, do we
                    // still need to adjust the waitlist?
                    waitlist.add(chState);
                    OGMap.putIfAbsent(chState.getStateId(), new ArrayList<>());
                    List<ObsGraph> chGraphs = OGMap.get(chState.getStateId());
                    chGraphs.add(chGraph);
                    return;
                }
                // Else, find target state recursively.
                List<ObsGraph> newGraphWrapper = new ArrayList<>();
                newGraphWrapper.add(chGraph);
                multiStepTransfer(waitlist, chState, newGraphWrapper);
            }
        }
    }


    /**
     * Detect whether a node conflicts with a graph. No conflict means we could add the
     * node to the trace. A trace corresponds to an actual execution sequence of the
     * nodes in the graph, so, one graph may have more than one trace.
     * @return true, if conflict.
     */
    // FIXME: throw away the write-before order.
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
            if (nodej.getFromReadBy().contains(n) || porf(n, nodej)) {
                // || nodej.getWAfter().contains(n)
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

    // FIXME
    private void addNewNode(ObsGraph graph,
                            OGNode node,
                            ARGState newPreState,
                            ARGState newSucState) {
        // 1. Add rf, mo, fr relations.
        Set<SharedEvent> rFlag = new HashSet<>(node.getRs()),
                wFlag = new HashSet<>(node.getWs());
        // Whether we have found the predecessor of the node.
        boolean preFlag = false;
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
//            getAllDot(graph);
//            System.out.println("");
            addRfMoForNewNode(graph, n, node, rFlag, wFlag);
//            getAllDot(graph);
//            System.out.println("");
            n = n.getTrAfter();
        }
        // 2. Add the new node to the graph.
        node.setPreState(newPreState);
        node.setSucState(newSucState);
        node.setInGraph(true);
        graph.getNodes().add(node);
        graph.setNeedToRevisit(true);
        if (graph.getLastNode() != null) {
            graph.getLastNode().setTrBefore(node);
            node.setTrAfter(graph.getLastNode());
        }
        graph.setLastNode(node);
        graph.setTraceLen(graph.getTraceLen() + 1);
        // TODO: Set RE when we add a new node to the graph?
        graph.setRE();
    }

    // Add rf and mo for the newly added node.
    void addRfMoForNewNode(ObsGraph graph, OGNode n, OGNode newNode,
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