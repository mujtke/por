package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DeepCopier;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.porf;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;

public class OGTransfer {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final DeepCopier copier = new DeepCopier();

    private static final NLTComparator nltcmp = new NLTComparator();
    public OGTransfer(Map<Integer, List<ObsGraph>> pOGMap,
                      Map<Integer, OGNode> pNodeMap) {
        this.OGMap = pOGMap;
        this.nodeMap = pNodeMap;
    }

    private static class NLTComparator implements Comparator<ARGState> {
        // NLT => <next
        @Override
        public int compare(ARGState s1, ARGState s2) {
            Map<Integer, Integer> nlt = GlobalInfo.getInstance().getOgInfo().getNlt();
            ARGState par = s1.getParents().iterator().next();
            assert par == s2.getParents().iterator().next() : "s1 and s2 should " +
                    "have only the same parent.";
            CFAEdge e1 = par.getEdgeToChild(s1), e2 = par.getEdgeToChild(s2);
            assert e1 != null && e2 != null;
            int cmp1 = nlt.get(hash(e1.hashCode(), e2.hashCode())),
                    cmp2 = nlt.get(hash(e2.hashCode(), e1.hashCode()));
            if (cmp1 == 0 || cmp2 == 0) return 0; // equal.
            if (cmp1 == 1 && cmp2 == -1) return -1; // <
            return 1; // >, cmp1 == 1 && cmp2 == -1.
        }
    }

    public static void setRelation(String type, SharedEvent e1, SharedEvent e2) {
        // set relation: <e1, e2> \in <_{type}
        OGNode e1n = e1.getInNode(), e2n = e2.getInNode();
        switch (type) {
            case "rf":
                // e1 <_rf e2, e2 reads from e1.
                Preconditions.checkArgument(e1.getReadFrom() != e2);
                e2.setReadFrom(e1);
                e1.getReadBy().add(e2);
                if (!e2n.getReadFrom().contains(e1n)) e2n.getReadFrom().add(e1n);
                if (!e1n.getReadBy().contains(e2n)) e1n.getReadBy().add(e2n);
                break;
            case "fr":
                // from read.
                Preconditions.checkArgument(!e1.getFromRead().contains(e2));
                e1.getFromRead().add(e2);
                e2.getFromReadBy().add(e1);
                if (!e1n.getFromRead().contains(e2n)) e1n.getFromRead().add(e2n);
                if (!e2n.getFromReadBy().contains(e1n)) e2n.getFromReadBy().add(e1n);
                break;
            case "mo":
                Preconditions.checkArgument(e1.getMoBefore() != e2);
                e1.setMoBefore(e2);
                e2.setMoAfter(e1);
                if (!e1n.getMoBefore().contains(e2n)) e1n.getMoBefore().add(e2n);
                if (!e2n.getMoAfter().contains(e1n)) e2n.getMoAfter().add(e1n);
                break;
            default:
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
     * @param graph
     * @param edge
     * @param parState Initial {@ARGState} where the transferring begin.
     * @param chState Final {@ARGState} where the transferring stop.
     * @return Transferred graph if no conflict found, else null.
     */
    // FIXME
    public ObsGraph singleStepTransfer(ObsGraph graph,
                                       CFAEdge edge,
                                       ARGState parState, /* lead state */
                                       ARGState chState) {
        OGNode node = nodeMap.get(edge.hashCode());
        // If the edge is in a block but not the first one of it, then we just transfer
        // the graph simply. Also, for the edge that doesn't access global variables.
        // For a block, the first edge decides whether we could transfer the graph.
        if (node == null /* No OGNode for the edge. */) {
            // Transfer graph from parState to chSate simply. I.e., just return the graph.
            graph.setNeedToRevisit(false);
            // Debug.
            addGraphToFull(graph, chState.getStateId());
            return graph;
        }

        // Whether the graph contains the node.
        int idx = graph.contain(node);
        // Whether there is any node in the graph still unmet.
        boolean hasNodeUnmet = hasUnmetNode(graph);
        // In the case that the graph still has some nodes unmet and doesn't contain the
        // current 'node', just skip the 'node' simply.
        if (hasNodeUnmet && (idx < 0)) return null;

        // no unmet nodes or idx > 0.
        if (idx > 0) {
            // The node has been in the graph.
            if (!node.isSimpleNode() && !edge.equals(node.getBlockStartEdge())) {
                // Debug.
                addGraphToFull(graph, chState.getStateId());
                // Inside the non-simple node, just return.
                updatePreSucState(edge, node, parState, chState);
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
                return graph;
            }
            // Add the node to the graph.
            // If we add a new node to the graph, we should use its deep copy.
            OGNode newNode = copier.deepCopy(node);
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
        return graph;
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

    public void multiStepTransfer(Vector<AbstractState> waitlist,
                                  ARGState leadState,
                                  ObsGraph graph) {
        // Debug.
        addGraphToFull(graph, leadState.getStateId());
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
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            OGNode node = nodeMap.get(etp.hashCode());
            // assert node != null: "Could not find OGNode for edge " + etp;
            ObsGraph chGraph = singleStepTransfer(graph, etp, leadState, chState);
            if (chGraph != null) {
                // Transfer stop, we have found the target state.
                List<ObsGraph> chGraphs = OGMap.computeIfAbsent(chState.getStateId(),
                        k -> new ArrayList<>());
                chGraphs.add(chGraph);
                // Adjust waitlist to ensure chState will be explored before its
                // siblings that has no graphs.
                adjustWaitlist(OGMap, waitlist, chState);
                return;
            }
        }
        // Handel states not in the waitlist.
        for (ARGState chState : notInWait) {
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            OGNode node = nodeMap.get(etp.hashCode());
            // assert node != null: "Could not find OGNode for edge " + etp;
            ObsGraph chGraph = singleStepTransfer(graph, etp, leadState, chState);
            if (chGraph != null) {
                if (waitlist.contains(chState)) {
                    // Transfer stop, we have found the target state.
                    List<ObsGraph> chGraphs = OGMap.computeIfAbsent(chState.getStateId(),
                            k -> new ArrayList<>());
                    chGraphs.add(chGraph);
                    // Adjust waitlist to ensure chState will be explored before its
                    // siblings that has no graphs.
                    adjustWaitlist(OGMap, waitlist, chState);
                    return;
                }
                // Else, find target state recursively.
                multiStepTransfer(waitlist, chState, chGraph);
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
            if (nodej.getFromReadBy().contains(nodej)
                || nodej.getWAfter().contains(nodej)
                || porf(n, nodej)) {
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
        oLast.setTrBefore(nLast);
        graph.setTraceLen(graph.getTraceLen() + 1);

        // Update mo for the new last node (nLast) by backtracking along the trace.
        Set<SharedEvent> wSet = new HashSet<>(nLast.getWs()), toRemove = new HashSet<>();
        OGNode tracePre = nLast.getTrAfter();
        while (tracePre != null) {
            for (SharedEvent wp : tracePre.getWs()) {
                for (SharedEvent w : wSet) {
                    if (w.accessSameVarWith(wp)) {
                        // wp <_mo w.
                        setRelation("mo", wp, w);
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
            addRfMoForNewNode(n, node, rFlag, wFlag);
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
    void addRfMoForNewNode(OGNode n, OGNode newNode,
                   Set<SharedEvent> rFlag,
                   Set<SharedEvent> wFlag) {
        for (SharedEvent w : n.getWs()) {
            Set<SharedEvent> toRemove = new HashSet<>();
            // Rf.
            for (SharedEvent r : rFlag) {
                if (r.accessSameVarWith(w)) {
                    // set w <_rf r.
                    setRelation("rf", w, r);
                    toRemove.add(r);
                }
            }
            rFlag.removeAll(toRemove);
            toRemove.clear();

            // Mo.
            for (SharedEvent j : wFlag) {
                if (j.accessSameVarWith(w)) {
                    setRelation("mo", w, j);
                    toRemove.add(j);
                }
            }
            wFlag.removeAll(toRemove);
            toRemove.clear();

            // Fr: when should we deduce this?
            // It seems when a node is added at the first time, we needn't deduce fr,
            // because all read events in it read from the mo-max writes, which have no
            // mo successor.
            /*
            for (SharedEvent k : ) {
                if (k.accessSameVarWith(w)) {
                    if (newNode.getInThread().equals(n.getInThread())
                            || !n.getThreadLoc().containsKey(newNode.getInThread())) {
                        // Add wb.
                        k.getWAfter().add(w);
                        w.getWBefore().add(k);
                        if (!newNode.getWAfter().contains(n)) {
                            newNode.getWAfter().add(n);
                        }
                        if (!n.getWBefore().contains(newNode)) {
                            n.getWBefore().add(newNode);
                        }
                        toRemove.add(k);

                        // Add fr.
                        Set<SharedEvent> wps = new HashSet<>();
                        wps.add(w);
                        while (!wps.isEmpty()) {
                            Set<SharedEvent> nwps = new HashSet<>();
                            for (SharedEvent wp : wps) {
                                if (!wp.getReadBy().isEmpty()) {
                                    for (SharedEvent rb : wp.getReadBy()) {
                                        if (rb.getInNode() != newNode) {
                                            // newNode may also read from n, we should
                                            // skip this case.
                                            rb.getFromRead().add(k);
                                            k.getFromReadBy().add(rb);
                                            if (!newNode.getFromReadBy().contains(n)) {
                                                newNode.getFromReadBy().add(n);
                                            }
                                            OGNode rbn = rb.getInNode();
                                            if (!rbn.getFromRead().contains(newNode)) {
                                                rbn.getFromRead().add(newNode);
                                            }
                                        }
                                        nwps.addAll(wp.getWAfter());
                                    }
                                }
                            }
                            wps = nwps;
                        }
                        break;
                    }
                }
            }
            wbFlag.removeAll(toRemove);
            toRemove.clear();
            */
        }
    }

    private void deduceWb(OGNode n0) {
        List<OGNode> rfns = n0.getReadFrom();
        for (OGNode rfn : rfns) {
            deduceWb0(n0, rfn);
        }
    }

    /**
     * Deduce wb relations caused by nA's rf and fr relations that come from nA reads
     * from nB.
     * @implNode 1. fr: if n0 -fr-> nx, then n2 that happens before and writes the same
     * var with n0 will write before nx.
     * 2. rf: nx - rf -> n0, then n2 that happens before and writes the same var with nx
     * will write before n0.
     * @param nA
     * @param nB nA reads from nB.
     */
    private void deduceWb0(OGNode nA, OGNode nB) {
        Set<SharedEvent> rfs = new HashSet<>();
        nA.getRs().forEach(r -> {
            if (nB.getWs().contains(r)) {
                rfs.add(r);
            }
        });
        Set<OGNode> frns = new HashSet<>(nA.getFromRead()),
                frrfns = new HashSet<>();
        frns.forEach(frn -> frrfns.addAll(frn.getReadFrom()));
        Set<OGNode> hbA = new HashSet<>(),
                visited = new HashSet<>();
        getHb(nA, hbA);
        while (!hbA.isEmpty()) {
            for (OGNode n : hbA) {
                for (SharedEvent wn : n.getWs()) {
                    // rf
                    for (SharedEvent rf : rfs) {
                        if (rf == wn) continue;
                        if (rf.accessSameVarWith(wn)) {
                            if (!rf.getWAfter().contains(wn))
                                rf.getWAfter().add(wn);
                            if (!wn.getWBefore().contains(rf))
                                wn.getWBefore().add(rf);
                            if (!n.getWBefore().contains(rf.getInNode()))
                                n.getWBefore().add(rf.getInNode());
                            if (!rf.getInNode().getWAfter().contains(n))
                                rf.getInNode().getWAfter().add(n);
                        }
                    }

                    // frn.
                    for (OGNode frn : frns) {
                        for (SharedEvent fr : frn.getWs()) {
                            if (fr == wn) continue;
                            if (fr.accessSameVarWith(wn)) {
                                if (!fr.getWAfter().contains(wn))
                                    fr.getWAfter().add(wn);
                                if (!wn.getWBefore().contains(fr))
                                    wn.getWBefore().add(fr);
                                if (!fr.getInNode().getWAfter().contains(n))
                                    fr.getInNode().getWAfter().add(n);
                                if (!n.getWBefore().contains(fr.getInNode()))
                                    n.getWBefore().add(n);
                            }
                        }
                    }

                    // frrfn.
                    for (OGNode frrfn: frrfns) {
                        for (SharedEvent frrf : frrfn.getWs()) {
                            if (frrf == wn) continue;
                            if (frrf.accessSameVarWith(wn)) {
                                if (!frrf.getWAfter().contains(wn))
                                    frrf.getWAfter().add(wn);
                                if (!wn.getWBefore().contains(frrf))
                                    wn.getWBefore().add(frrf);
                                if (!frrf.getInNode().getWAfter().contains(n))
                                    frrf.getInNode().getWAfter().add(n);
                                if (!n.getWBefore().contains(frrf.getInNode()))
                                    n.getWBefore().add(n);
                            }
                        }
                    }
                }
            }

            visited.addAll(hbA);
            Set<OGNode> nhbA = new HashSet<>();
            hbA.forEach(h -> {
                Set<OGNode> hbhs = new HashSet<>();
                getHb(h, hbhs);
                hbhs.forEach(hbn -> {
                    if (!visited.contains(hbn))
                        nhbA.add(hbn);
                });
            });
            hbA = nhbA;
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
    private void addGraphToFull(ObsGraph graph, Integer stateId) {
        String gStr = getDotStr(graph);
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        assert ogInfo != null;
        List<String> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
                k -> new ArrayList<>());
        ogs.add(gStr);
    }
}