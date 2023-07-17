package org.sosy_lab.cpachecker.core.algorithm.og;

import de.uni_freiburg.informatik.ultimate.util.ScopeUtils;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.obsgraph.DeepCopier;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;

import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.happenBefore;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getAllDot;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDot;

public class OGTransfer {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final DeepCopier copier = new DeepCopier();
    public OGTransfer(Map<Integer, List<ObsGraph>> pOGMap,
                      Map<Integer, OGNode> pNodeMap) {
        this.OGMap = pOGMap;
        this.nodeMap = pNodeMap;
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
    public ObsGraph singleStepTransfer(ObsGraph graph,
                                       CFAEdge edge,
                                       ARGState parState, /* lead state */
                                       ARGState chState) {
        OGNode node = nodeMap.get(edge.hashCode());
        // If the edge is in a bock but not the last one of it, then we just transfer
        // simply. Also, for the edge that doesn't access global variables.
        if (node == null /* No OGNode for the edge. */
                || (!node.isSimpleNode() && !node.getLastBlockEdge().equals(edge))) {
            // Transfer graph from parState to chSate simply. I.e., just return the graph.
            // Update the preState and SucStat for the node, if it's not null;
            if (node != null) {
                // Here, the node must not be simple.
                // For block, only set preState for the start edge.
                if (!edge.equals(node.getBlockStartEdge())) {
                    node.setSucState(chState);
                } else {
                    node.setPreState(parState);
                    node.setSucState(chState);
                }
            }

            return graph;
        }

        int idx = contain(graph, node);
        if (idx > 0) {
            // The graph has contained the node.
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
        } else {
            // The node is not in the graph.
            if (isConflict(graph, node, -1)) {
                // Some other nodes should happen before the node.
                return null;
            }
            // Add the node to the graph.
            // If we add a new node to the graph, we should use its deep copy.
            OGNode newNode = copier.deepCopy(node);
//            getDot(graph);
//            System.out.println("");
            addNewNode(graph, newNode,
                    node.isSimpleNode() ? parState : node.getPreState(),
                    chState);
//            getDot(graph);
//            System.out.println("");
        }

        return graph;
    }

    /**
     *
     * @param leadState
     * @param graph
     */
    public void multiStepTransfer(Vector<AbstractState> waitlist,
                                  ARGState leadState,
                                  ObsGraph graph) {
        for (ARGState chState : leadState.getChildren()) {
            CFAEdge etp = leadState.getEdgeToChild(chState);
            assert etp != null;
            OGNode node = nodeMap.get(etp.hashCode());
            assert node != null: "Could find OGNode for edge " + etp;
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
     * Given a graph and a OGNode, judge whether the graph contains the node.
     * If true, return the index of the node in that graph. Else, return -1;
     * @param graph
     * @param node
     * @return A positive integer if the graph contains node, -1 if not.
     */
    private int contain(ObsGraph graph, OGNode node) {
        for (int i = 0; i < graph.getNodes().size(); i++) {
            assert graph.getNodes().get(i) != null;
            if (graph.getNodes().get(i).equals(node)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Detect whether a node conflict with a graph. No conflict means we could add the
     * node to the trace corresponding to the graph.
     * @param graph
     * @param node
     * @param nodeInGraph
     * @return true, if conflict.
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
            if (nodej.getFromReadBy().contains(nodej)
                || nodej.getWAfter().contains(nodej)
                || happenBefore(n, nodej)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Update the last node to {@node} for {@graph}, and update relations for the new
     * last node.
     * @param graph
     * @param idx the index of the new last node.
     * @param newPreState used to update the preState of the new last node.
     * @param newSucState used to update the sucState of the new last node.
     * @implNote Because of the block, we should handle the preState carefully.
     */
    private void updateLastNode(ObsGraph graph, int idx, ARGState newPreState,
                                ARGState newSucState) {
        OGNode nLast = graph.getNodes().get(idx),
                oLast = graph.getLastNode();
        nLast.setPreState(newPreState);
        nLast.setSucState(newSucState);
        nLast.setInGraph(true);
        graph.setLastNode(nLast);
        nLast.setTrAfter(oLast);
        oLast.setTrBefore(nLast);
        graph.setTraceLen(graph.getTraceLen() + 1);

        // Update mo for the new last node (nLast).
        /* Firstly, clear all old mo of nLast. */
        nLast.getWs().forEach(w -> {
            if (w.getMoAfter() != null) {
                w.getMoAfter().setMoBefore(null);
                w.setMoAfter(null);
            }
            if (w.getMoBefore() != null) {
                w.getMoBefore().setMoAfter(null);
                w.setMoBefore(null);
            }
        });
        nLast.getMoAfter().forEach(n -> {
            n.getMoBefore().remove(nLast);
        });
        nLast.getMoAfter().clear();
        nLast.getMoBefore().forEach(n -> {
            n.getMoAfter().remove(nLast);
        });
        nLast.getMoBefore().clear();

        /* Secondly, update mo by backtracking along the trace. */
        Set<SharedEvent> wSet = new HashSet<>(nLast.getWs()),
                toRemove = new HashSet<>();
        OGNode tracePre = nLast.getTrAfter();
        while (tracePre != null) {
            for (SharedEvent wp : tracePre.getWs()) {
                for (SharedEvent w : wSet) {
                    if (w.getVar().getName().equals(wp.getVar().getName())) {
                        wp.setMoBefore(w);
                        w.setMoAfter(wp);
                        toRemove.add(w);
                        if (!tracePre.getMoBefore().contains(nLast)) {
                            tracePre.getMoBefore().add(nLast);
                        }
                        break;
                    }
                }
            }
            wSet.removeAll(toRemove);
            tracePre = tracePre.getPredecessor();
        }

    }

    private void addNewNode(ObsGraph graph,
                            OGNode node,
                            ARGState newPreState,
                            ARGState newSucState) {
        // 1. Add rf, mo, fr and wb (not deduced from rf and fr) relations.
        Set<SharedEvent> rFlag = new HashSet<>(node.getRs()),
                wFlag = new HashSet<>(node.getWs()),
                wbFlag = new HashSet<>(node.getWs());
        // Whether we have found the predecessor of the node.
        boolean preFlag = false;
        OGNode n = graph.getLastNode();
        // Backtracking along with the trace.
        while (n != null) {
           if (!preFlag && (n.getInThread().equals(node.getInThread())
                   || !n.getThreadLoc().containsKey(n.getInThread()))) {
                n.getSuccessors().add(node);
                node.setPredecessor(n);
                preFlag = true;
           }
           if (rFlag.isEmpty() && wFlag.isEmpty() && wbFlag.isEmpty()) {
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
            addRfFrWb(n, node, rFlag, wFlag, wbFlag);
//            getAllDot(graph);
//            System.out.println("");
            n = n.getTrAfter();
        }
        // 2. Deduce wb from rf and fr.
//        getAllDot(graph);
//        System.out.println("");
        deduceWb(node);
//        getAllDot(graph);
//        System.out.println("");
        // 3. Add the new node to the graph.
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
    }

    void addRfFrWb(OGNode n,
                   OGNode newNode,
                   Set<SharedEvent> rFlag,
                   Set<SharedEvent> wFlag,
                   Set<SharedEvent> wbFlag) {
        for (SharedEvent w : n.getWs()) {
            Set<SharedEvent> toRemove = new HashSet<>();
            // Rf.
            for (SharedEvent r : rFlag) {
                if (r.accessSameVarWith(w)) {
                    r.setReadFrom(w);
                    w.getReadBy().add(r);
                    if (!newNode.getReadFrom().contains(n)) {
                        newNode.getReadFrom().add(n);
                    }
                    if (!n.getReadBy().contains(newNode)) {
                        n.getReadBy().add(newNode);
                    }
                    toRemove.add(r);
                }
            }
            rFlag.removeAll(toRemove);
            toRemove.clear();

            // Mo.
            for (SharedEvent j : wFlag) {
                if (j.accessSameVarWith(w)) {
                    j.setMoAfter(w);
                    w.setMoBefore(j);
                    if (!newNode.getMoAfter().contains(n)) {
                        newNode.getMoAfter().add(n);
                    }
                    if (!n.getMoBefore().contains(newNode)) {
                        n.getMoBefore().add(newNode);
                    }
                    toRemove.add(j);
                }
            }
            wFlag.removeAll(toRemove);
            toRemove.clear();

            // Wb and Fr.
            for (SharedEvent k : wbFlag) {
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
}
