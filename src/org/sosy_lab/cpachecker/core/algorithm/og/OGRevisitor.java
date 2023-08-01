package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.obsgraph.DeepCopier;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer.getHb;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getAllDot;

public class OGRevisitor {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    DeepCopier copier = new DeepCopier();
    public OGRevisitor(Map<Integer, List<ObsGraph>> pOGMap,
                       Map<Integer, OGNode> nodeMap) {
        this.OGMap = pOGMap;
        this.nodeMap = nodeMap;
    }

    /**
     *
     * @param graphs The list of graphs on which revisit will be performed if needed.
     * @param result All results produced by revisit process will be put into it.
     */
    public void apply(List<ObsGraph> graphs, List<Pair<AbstractState, ObsGraph>> result) {
        if (graphs.isEmpty()) {
            return;
        }

        for (ObsGraph graph : graphs) {
            if (!needToRevisit(graph)) {
                continue;
            }
            result.addAll(revisit(graph));
        }
    }

    private boolean needToRevisit(ObsGraph graph) {
        return graph.isNeedToRevisit();
    }

    private List<Pair<AbstractState, ObsGraph>> revisit(ObsGraph g) {
        List<Pair<AbstractState, ObsGraph>> result = new ArrayList<>();
        List<OGNode> nodes = g.getNodes();
        OGNode node0 = g.getLastNode();
        int nodeNum = nodes.size();
        assert node0 != null && node0.equals(nodes.get(nodeNum - 1));

        List<ObsGraph> gs = new ArrayList<>();
        gs.add(g);
        // Backtracking along the order ndoes added?
        // TODO: maybe it's better to use trace order.
        OGNode nodei = node0.getTrAfter();
//        for (int i = nodeNum - 2; i >= 0; i--) {
        for (; nodei != null; nodei = nodei.getTrAfter()) {
//            OGNode nodei = nodes.get(i);
            int i = nodes.indexOf(nodei);
            int affectedNodeIndex = -1;
            if (mayReadFrom(nodei, node0)) {
                // If nodei may reads from node0, then we update the affectedNode as
                // nodei. Here, we record the index of affectedNode instead itself,
                // because there may be more than one graph produced when revisiting
                // and these graphs have the same affectedNodes.
                affectedNodeIndex = nodes.indexOf(nodei);
            }
            if (node0.getReadFrom().contains(nodei) /* node0 reads from nodei. */
                    /* nodei may read from node0. */
                    || affectedNodeIndex >= 0) {
                // Collecting the graphs produced in one revisiting, these graphs may be
                // used for next revisiting.
                List<ObsGraph> tmpGraphs = new ArrayList<>();
                for (ObsGraph g0 : gs) {
                    Pair<AbstractState, ObsGraph> tmpResult = null;
                    // During the revisit, the number of nodes may change.
                    int node0Index = g0.getNodes().size() - 1,
                            /* The index of nodei keep unchanged. */
                            nodeiIndex = i;
                    tmpResult = revisit0(g0, affectedNodeIndex, node0Index, nodeiIndex);
                    if (tmpResult != null) {
                        tmpGraphs.add(tmpResult.getSecondNotNull());
                        result.add(tmpResult);
                    }
                }
                if (!tmpGraphs.isEmpty()) {
                    // Add all newly got graphs during the last revisiting for next
                    // revisiting.
                    gs.addAll(tmpGraphs);
                }
            }
        }

        // Before returning, update the trace orders for all graphs in result because we
        // update the last node for these graphs.
        result.forEach(pair -> updateTraceOrder(pair.getSecondNotNull()));

        return result;
    }

    /**
     * Clear trace orders for nodes after graph.getLastNode().
     * @param graph
     */
    private void updateTraceOrder(ObsGraph graph) {
        getAllDot(graph);
        System.out.printf("");
        OGNode tan = graph.getLastNode().getTrBefore();
        graph.getLastNode().setTrBefore(null);
        int trL = graph.getTraceLen();
        while (tan != null) {
            trL -= 1;
            tan.setInGraph(false);
            tan.setTrAfter(null);
            OGNode tmptb = tan.getTrBefore();
            tan.setTrBefore(null);
            tan = tmptb;
        }
        graph.setTraceLen(trL);
        getAllDot(graph);
        System.out.printf("");
    }

    private Pair<AbstractState, ObsGraph> revisit0(
            ObsGraph g0,
            int affectedNodeIndex,
            int node0Index,
            int nodeiIndex) {
        OGNode node0 = g0.getNodes().get(node0Index),
                nodei = g0.getNodes().get(nodeiIndex),
                affectedNode = affectedNodeIndex >= 0 ?
                        g0.getNodes().get(affectedNodeIndex) : null;
        if (affectedNode != null) {
            // AffectedNode shouldn't happen before node0, except that node0 reads from
            // AffectedNode directly or AffectedNode is the direct predecessor of node0.
            if (!node0.getReadFrom().contains(affectedNode)
                    // Here, use '!=' to check whether affectedNode is node0's predecessor
                    && node0.getPredecessor() != affectedNode) {
                if (happenBefore(affectedNode, node0)) {
                    return null;
                }
            }
        } else {
            // affectedNode == null.
            boolean hasNewValueToRead = false;
            for (SharedEvent r : node0.getRs()) {
                for (SharedEvent w : nodei.getWs()) {
                    if (r.getReadFrom() == w) {
                        if (w.getMoAfter() != null) {
                            hasNewValueToRead = true;
                            break;
                        }
                    }
                }
                if (hasNewValueToRead) break;
            }
            if (!hasNewValueToRead) return null;
        }

        ObsGraph g = copier.deepCopy(g0);
        getAllDot(g);
        System.out.printf("");
        node0 = g.getNodes().get(node0Index);
        affectedNode = affectedNodeIndex >= 0 ? g.getNodes().get(affectedNodeIndex) : null;
        nodei = g.getNodes().get(nodeiIndex);
        assert node0 != null && nodei != null;

        List<OGNode> delete = new ArrayList<>();
        if (affectedNode != null) {
            // If affectedNode exists, then some nodes may get deleted.
            getDelete(g, affectedNode, node0, delete);
            // Only all nodes in delete added maximally we can continue to revisit.
            // Else, just drop the revisit process and return null;
            if (!maximallyAdded(g, delete, node0, affectedNode)) {
                return null;
            }
        }
        // Delete the nodes in 'delete' if necessary.
        removeDelete(g, delete, affectedNodeIndex);
        // Update relations after removing the delete part.
        getAllDot(g);
        System.out.printf("");
        updateRelation(node0, nodei, affectedNode, g);
        getAllDot(g);
        System.out.printf("");
        // Check the consistency of the new graph, if not satisfied, return null;
        if (!consistent(g)) {
            getAllDot(g);
            System.out.printf("");
            consistent(g);
            return null;
        }

        Pair<ARGState, OGNode> nLStateNode = updateLStateNode(node0, nodei, affectedNode);
        assert nLStateNode != null;
        ARGState leadState = nLStateNode.getFirstNotNull();
        OGNode nLastNode = nLStateNode.getSecondNotNull();
        g.setLastNode(nLastNode);
        g.setNeedToRevisit(false);

        return Pair.of(leadState, g);
    }

    /**
     * TODO
     * It seems that when node0 doesn't contains w, i.e., affectedNode is null, lead
     * state should be the suc state of the node which has the smallest trace order and
     * is read by node0.
     * @implNote
     */
    private Pair<ARGState, OGNode> updateLStateNode(OGNode node0,
                                                    OGNode nodei,
                                                    OGNode affectedNode) {
        if (affectedNode != null) {
            return Pair.of(nodei.getPreState(), nodei.getTrAfter());
        }
        else {
            ARGState leadState = null;
            OGNode leadNode = null;
            // Backtracking to find the proper lead state.
            int sucNum = Integer.MAX_VALUE;
            for (OGNode n : node0.getReadFrom()) {
                if (n.getSucState().getStateId() < sucNum) {
                    leadState = n.getSucState();
                    leadNode = n;
                }
                sucNum = n.getSucState().getStateId();
            }
            assert leadState != null && leadNode != null;
            return Pair.of(leadState, leadNode);
        }
    }

    /**
     * Ref: <a herf="https://www.geeksforgeeks.org/detect-cycle-in-a-graph/"></a>
     * @return true if there is no any cycle in g.
     */
    private boolean consistent(ObsGraph g) {
        int nodeNum = g.getNodes().size();
        if (nodeNum <= 0) return true;
        boolean[] visited = new boolean[nodeNum];
        boolean[] inTrace = new boolean[nodeNum];
        for (int i = 0; i < nodeNum; i++) {
            if (isCyclic(g, i, visited, inTrace))
                return false;
        }
        return true;
    }

    private boolean isCyclic(ObsGraph g, int i, boolean[] visited, boolean[] inTrace) {
        // mark g.getNodes().get(i) as visited and in trace.
        Preconditions.checkState(i >= 0 && i < visited.length && i < inTrace.length);
        visited[i] = true;
        inTrace[i] = true;

        OGNode nodei = g.getNodes().get(i);
        Set<Integer> neighbours = new HashSet<>();
        List<OGNode> nodes = g.getNodes();
        for (OGNode suc : nodei.getSuccessors()) {
            if (nodes.contains(suc))
                neighbours.add(nodes.indexOf(suc));
        }
        for (OGNode rbn : nodei.getReadBy()) {
            if (nodes.contains(rbn))
                neighbours.add(nodes.indexOf(rbn));
        }
        for (OGNode frn : nodei.getFromRead()) {
            if (nodes.contains(frn))
                neighbours.add(nodes.indexOf(frn));
        }
        for (Integer n : neighbours) {
            if (inTrace[n]) {
                return true;
            }
            else if (!visited[n] && isCyclic(g, n, visited, inTrace)) {
                return true;
            }
        }
        inTrace[i] = false;

        return false;
    }

    private void updateRelation(OGNode node0, OGNode nodei, OGNode affectedNode,
                                /* debug. */
                                ObsGraph graph) {
        // 1. affectedNode.
        if (affectedNode != null) {
            getAllDot(graph);
            System.out.println("");
            // Get chrfrs.
            List<SharedEvent> chrfrs = getJoin(affectedNode.getRs(), node0.getWs());
            // Remove old wb.
            for (SharedEvent r : chrfrs) {
                removeOldWb(affectedNode, node0, r);
            }
            // Read from.
            getAllDot(graph);
            System.out.printf("");
            chReadFrom(chrfrs, node0.getWs(), affectedNode, node0);
            getAllDot(graph);
            System.out.printf("");
            // From read.
            updateFromRead(affectedNode);
            getAllDot(graph);
            System.out.printf("");
            // add new wb.
            updateWriteBefore(affectedNode, node0);
        }

        // 2. node0.
        // Read from.
        // Only performed for node0.getRs() \wedge nodei.getWs().
        getAllDot(graph);
        System.out.printf("");
        List<SharedEvent> chrfrs = getJoin(node0.getRs(), nodei.getWs());
        for (SharedEvent r : chrfrs) {
            for (SharedEvent w: nodei.getWs()) {
                if (r.accessSameVarWith(w)) {
                    if (w.getMoAfter() != null) {
                        OGNode wman = w.getMoAfter().getInNode();
                        removeOldWb(node0, wman, r);
                        chReadFrom(chrfrs, wman.getWs(), node0, wman);
                    }
                }
            }
        }
        // From read.
        getAllDot(graph);
        System.out.printf("");
        updateFromRead(node0);
        getAllDot(graph);
        System.out.printf("");
        // Add new wb.
        for (SharedEvent r : chrfrs) {
            if (r.getReadFrom() == null) continue;
            updateWriteBefore(node0, r.getReadFrom().getInNode());
        }
        getAllDot(graph);
        System.out.printf("");
    }

    private void removeDelete(ObsGraph g, List<OGNode> delete, int affectedNodeIndex) {
        if (affectedNodeIndex < 0 || delete.isEmpty()) return;
        for (int i = affectedNodeIndex + 1; i < g.getNodes().size(); i++) {
            OGNode n = g.getNodes().get(i);
            if (!delete.contains(n)) continue;
            // For nodes in delete, we need to remove all relations.
            // TODO: when remove rf and rb, should we also remove wb deduced?
            removeDeducedWb(n);
            getAllDot(g);
            System.out.println("");
            // Deduce new relations before remove the node 'n'.
            deduceRelations(n, g);
            getAllDot(g);
            System.out.println("");
            // 1. predecessor and successors.
            if (n.getPredecessor() != null){
                n.getPredecessor().getSuccessors().remove(n);
                n.setPredecessor(null);
            }
            n.getSuccessors().forEach(suc -> suc.setPredecessor(null));
            n.getSuccessors().clear();
            getAllDot(g);
            System.out.println("");
            // 2. read from and read by.
            n.getReadFrom().forEach(rf -> rf.getReadBy().remove(n));
            n.getReadFrom().clear();
            n.getReadBy().forEach(rb -> rb.getReadFrom().remove(n));
            n.getReadBy().clear();
            n.getRs().forEach(r -> {
                if (r.getReadFrom() != null) {
                    r.getReadFrom().getReadBy().remove(r);
                    r.setReadFrom(null);
                }
            });
            n.getWs().forEach(w -> {
                if (!w.getReadBy().isEmpty()) {
                    w.getReadBy().forEach(rb -> {
                        // rb may have changed its read from when deducing relations.
                        if (w == rb.getReadFrom())
                            rb.setReadFrom(null);
                    });
                    w.getReadBy().clear();
                }
            });
            getAllDot(g);
            System.out.println("");
            // 3. from read and from read by.
            n.getFromRead().forEach(fr -> fr.getFromReadBy().remove(n));
            n.getFromRead().clear();
            n.getFromReadBy().forEach(frb -> frb.getFromRead().remove(n));
            n.getFromReadBy().clear();
            n.getRs().forEach(r -> {
                if (!r.getFromRead().isEmpty()) {
                    r.getFromRead().forEach(fr -> fr.getFromReadBy().remove(r));
                    r.getFromRead().clear();
                }
            });
            n.getWs().forEach(w -> {
                if (!w.getFromReadBy().isEmpty()) {
                    w.getFromReadBy().forEach(frb -> frb.getFromRead().remove(w));
                    w.getFromReadBy().clear();
                }
            });
            getAllDot(g);
            System.out.println("");
            // 4. mo before and mo after.
            n.getMoBefore().forEach(mb -> mb.getMoAfter().remove(n));
            n.getMoBefore().clear();
            n.getMoAfter().forEach(ma -> ma.getMoBefore().remove(n));
            n.getMoAfter().clear();
            n.getWs().forEach(w -> {
                w.setMoAfter(null);
                w.setMoBefore(null);
            });
            // 5. wb and wa.
            n.getWBefore().forEach(wb -> wb.getWAfter().remove(n));
            n.getWBefore().clear();
            n.getWAfter().forEach(wa -> wa.getWBefore().remove(n));
            n.getWAfter().clear();
            n.getWs().forEach(w -> {
                w.getWBefore().forEach(wb -> wb.getWAfter().remove(w));
                w.getWBefore().clear();
                w.getWAfter().forEach(wa -> wa.getWBefore().remove(w));
                w.getWAfter().clear();
            });
            // 6. trace before and trace after.
            // TODO: A -> B -> C ==> A -> C ?
            OGNode tb = n.getTrBefore();
            OGNode ta = n.getTrAfter();
            if (tb != null) tb.setTrAfter(ta);
            if (ta != null) ta.setTrBefore(tb);
            n.setTrAfter(null);
            n.setTrBefore(null);
            g.getNodes().remove(n);
            g.setTraceLen(g.getTraceLen() - 1);
        }
    }

    private void deduceRelations(OGNode n,
                                 ObsGraph g /* debug. */) {
        // Deduce new rf when remove an old one.
        for (SharedEvent w : n.getWs()) {
            if (w.getMoAfter() != null) {
                SharedEvent wa = w.getMoAfter();
                for (SharedEvent rb : w.getReadBy()) {
                    rb.setReadFrom(wa);
                    wa.getReadBy().add(rb);
                    OGNode rbn = rb.getInNode(), wan = wa.getInNode();
                    if (!wan.getReadBy().contains(rbn)) wan.getReadBy().add(rbn);
                    if (!rbn.getReadFrom().contains(wan)) rbn.getReadFrom().add(wan);
                }
            }
        }

        // Deduce new mo when remove old ones.
        for (SharedEvent w : n.getWs()) {
            SharedEvent ma = w.getMoAfter(), mb = w.getMoBefore();
            if (ma != null && mb != null) {
                ma.setMoBefore(mb);
                mb.setMoAfter(ma);
                OGNode man = ma.getInNode(), mbn = mb.getInNode();
                if (!man.getMoBefore().contains(mbn)) man.getMoBefore().add(mbn);
                if (!mbn.getMoAfter().contains(man)) mbn.getMoAfter().add(man);
            }
        }

        // Deduce new wb when remove old ones.
        for (SharedEvent w : n.getWs()) {
            List<SharedEvent> was = w.getWAfter(), wbs = w.getWBefore();
            if (!was.isEmpty() && !wbs.isEmpty()) {
                for (SharedEvent wa : was) {
                    for (SharedEvent wb : wbs) {
                        wa.getWBefore().add(wb);
                        wb.getWAfter().add(wa);
                        OGNode wan = wa.getInNode(), wbn = wb.getInNode();
                        if (!wan.getWBefore().contains(wbn))
                            wan.getWBefore().add(wbn);
                        if (!wbn.getWAfter().contains(wan))
                            wbn.getWAfter().add(wan);
                        for (SharedEvent rb : wa.getReadBy()) {
                            OGNode rbn = rb.getInNode();
                            if (rbn != wbn) {
                                // rb and wb may be in the same node.
                                rb.getFromRead().add(wb);
                                wb.getFromReadBy().add(rb);
                                if (!rbn.getFromRead().contains(wbn))
                                    rbn.getFromRead().add(wbn);
                                if (!wbn.getFromReadBy().contains(rbn))
                                    wbn.getFromReadBy().add(rbn);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Similar with 'removeOldWb'.
     * @param n
     */
    private void removeDeducedWb(OGNode n) {
        // Handle rf-deduced wb.
        for (SharedEvent r : n.getRs()) {
            SharedEvent rf = r.getReadFrom();
            List<SharedEvent> causalRs = rf.getReadBy()
                    .stream()
                    .filter(cr -> (cr != r)
                    && r.accessSameVarWith(cr)
                    && (happenBefore(cr.getInNode(), n) || happenBefore(n,
                            cr.getInNode())))
                    .collect(Collectors.toList());
            if (!causalRs.isEmpty()) {
                // R is not the only reason that causes the wb. So we don't remove the
                // wb.
                continue;
            }

            for (SharedEvent wa : rf.getWAfter()) {
                OGNode wan = wa.getInNode(), rfn = rf.getInNode();
                // Wa relations in the same thread are stable.
                if (wan.getInThread().equals(rfn.getInThread())) continue;
                if (wa.accessSameVarWith(r)
                        && happenBefore(wan, n)
                        && !porf(wan, rfn)) {
                    // R reads from rf is the only reason that wa write before rf.
                    rf.getWAfter().remove(wa);
                    wa.getWBefore().remove(rf);
                    removeWbIfNecessary(wan, rfn);
                }

                for (SharedEvent w :
                        getJoin(r.getFromRead(), Collections.singleton(rf))) {
                    if (wan.getInThread().equals(w.getInNode().getInThread())) continue;
                    if (wa.accessSameVarWith(r) && happenBefore(wan, n)) {
                        if (w.getWAfter().contains(wa)) {
                            w.getWAfter().remove(wa);
                            wa.getWBefore().remove(w);
                            removeWbIfNecessary(wan, w.getInNode());
                        }
                    }
                }
            }
        }
        /*
        // Handle rb-deduced wb.
        for (SharedEvent w : n.getWs()) {
            for (SharedEvent rb : w.getReadBy()) {
                List<SharedEvent> causalRs = w.getReadBy()
                        .stream()
                        .filter(cr -> (cr != rb)
                                && rb.accessSameVarWith(cr)
                                && (happenBefore(rb.getInNode(), cr.getInNode())
                                || happenBefore(cr.getInNode(), rb.getInNode())))
                        .collect(Collectors.toList());
                if (!causalRs.isEmpty()) continue;
                // Handle rf.
            }
        }
        */
    }

    /**
     * Remove n1 from n2.getWAfter() if allowed.
     * @param n1 writes before n2.
     * @param n2 writes after n1.
     */
    private void removeWbIfNecessary(OGNode n1, OGNode n2) {
        Set<OGNode> wans = new HashSet<>();
        n2.getWs().forEach(w -> {
            for (SharedEvent wa : w.getWAfter()) {
                wans.add(wa.getInNode());
            }
        });
        if (!wans.contains(n1)) {
            // If n2.WAfter doesn't contain n1 any longer,
            // remove n1 from wAfter of n2.
            n2.getWAfter().remove(n1);
            n1.getWBefore().remove(n2);
        }
    }

    /**
     *
     * @param g
     * @param delete
     * @param node0
     * @param affectedNode
     * @return True, if affectedNode and all nodes in delete added maximally.
     * @implNode
     */
    private boolean maximallyAdded(ObsGraph g, List<OGNode> delete, OGNode node0,
                                   OGNode affectedNode) {
        // Maximality check should include affectedNode.
        List<OGNode> checkList = new ArrayList<>(delete);
        checkList.add(affectedNode);
        for (OGNode dn : checkList) {
            List<OGNode> previous = new ArrayList<>();
            for (OGNode n : g.getNodes()) {
                boolean cond1 =
                        g.getNodes().indexOf(n) <= g.getNodes().indexOf(affectedNode)
                        && !happenBefore(affectedNode, n),
                        cond2 = happenBefore(n, node0);
                if (cond1 || cond2) {
                    previous.add(n);
                }
            }
            // Check Rs, TODO: when dn has Ws and Rs, only check its Rs?
            for (OGNode rfn : dn.getReadFrom()) {
                if (!previous.contains(rfn)) continue;
                for (SharedEvent r : dn.getRs()) {
                    for (SharedEvent w : rfn.getWs()) {
                        if (r.accessSameVarWith(w)) {
                            if (w.getMoBefore() != null) {
                                OGNode wmbn = w.getMoBefore().getInNode();
                                if (previous.contains(wmbn))
                                    return false;
                            }
                        }
                    }
                }
            }

            // Check Ws.
            if (!previous.contains(dn)) continue;
            for (OGNode pn : previous) {
                if ((g.getNodes().indexOf(pn) < g.getNodes().indexOf(dn))
                && pn.getReadFrom().contains(dn))
                    return false;
            }
            for (SharedEvent w : dn.getWs()) {
                if (w.getMoBefore() != null) {
                    OGNode wmbn = w.getMoBefore().getInNode();
                    if (previous.contains(wmbn))
                        return false;
                }
            }
        }

        return true;
    }

    /**
     * @implNote The delete part includes all nodes that are added to graph g after
     * affectedNode and independent with node0.
     * @param delete The delete part.
     */
    private void getDelete(ObsGraph g, OGNode affectedNode,
                           OGNode node0, List<OGNode> delete) {
        int h = g.getNodes().size() - 1,
                l = g.getNodes().indexOf(affectedNode);
        for (; h > l; h--) {
            OGNode nodek = g.getNodes().get(h);
            if (nodek == node0) continue;
            if (happenBefore(nodek, node0)) continue;
            delete.add(nodek);
        }
    }

    /**
     *
     * @return true if node1 happens before node2.
     */
    public static boolean happenBefore(OGNode node1, OGNode node2) {
        if (node1 == null || node2 == null) return false;
        for (OGNode n : node1.getSuccessors()) {
            if (n == node2) return true;
            if (happenBefore(n, node2)) return true;
        }
        for (OGNode n : node1.getReadBy()) {
            if (n == node2) return true;
            if (happenBefore(n, node2)) return true;
        }
        return false;
    }

    /**
     *
     * @return true if nodei has r accesses the same var as w in node0.
     */
    private boolean mayReadFrom(OGNode nodei, OGNode node0) {
        return !getJoin(nodei.getRs(), node0.getWs()).isEmpty();
    }

    private List<SharedEvent> getJoin(Collection<SharedEvent> es1,
                                      Collection<SharedEvent> es2) {
        List<SharedEvent> res = new ArrayList<>();
        es1.forEach(r -> {
            es2.forEach(w -> {
                if (r.accessSameVarWith(w)) {
                    res.add(r);
                }
            });
        });

        return res;
    }

    /**
     * When node A turns to read from node B, remove the wb caused by the old rf and fr
     * of event r.
     * E.g., w --> r ==> w' --> r, then wb caused by 'w --> r' should be removed if
     * there is no any r's causal R that is in w.readBy() and happens after w0 which
     * writes before w.
     * w0 writes before w comes from: 1) w0 happens before r, 2) w --> r. So if there
     * is any r's causal R that reads from w and happens after w0, then w0 will still
     * write before w.
     * @param A
     * @param B
     * @param r use this to get the old rf and fr relations. And r should be in node A.
     */
    private void removeOldWb(OGNode A, OGNode B, SharedEvent r) {
        SharedEvent rf = r.getReadFrom();
        assert rf != null;
        OGNode rfn = rf.getInNode();
        // If there are some Rs in rf.readBy() happen before/after r and happen
        // after rf, then the corresponding wa in rf.wa shouldn't be removed.
        List<SharedEvent> causalRs = rf.getReadBy()
                .stream()
                .filter(cr -> (cr != r)
                && r.accessSameVarWith(cr)
                && (happenBefore(cr.getInNode(), A) || happenBefore(A, cr.getInNode()))
                && happenBefore(B, cr.getInNode()))
                .collect(Collectors.toList());
        if (!causalRs.isEmpty()) return;

        List<SharedEvent> rmwas = new ArrayList<>();
        for (SharedEvent wa : rf.getWAfter()) {
            OGNode wan = wa.getInNode();
            // Wa relations in the same thread are stable.
            if (wan.getInThread().equals(rfn.getInThread()))
                continue;
            // If wa doesn't happen before r, then wa couldn't be removed from rf.wb.
            if (wa.accessSameVarWith(r)
                    && happenBefore(wan, A)
                    && !porf(wan, rfn)) {
                // r reads from rf is the only reason that wa writes before rf.
//                rf.getWAfter().remove(wa);
//                wa.getWBefore().remove(rf);
                rmwas.add(wa);
                Set<SharedEvent> rfwas = new HashSet<>();
                rfn.getWs().forEach(w -> rfwas.addAll(w.getWAfter()));
                if (getJoin(rfwas, wan.getWs()).isEmpty()) {
                    // No w in rf's inNode writes after w' in wa's inNode, this means
                    // we could remove wa's inNode from wAfter of rf's inNode.
                    rfn.getWAfter().remove(wan);
                    wan.getWBefore().remove(rfn);
                }
            }

            for (SharedEvent w :
                    getJoin(r.getFromRead(), Collections.singleton(rf))) {
                if (wan.getInThread().equals(w.getInNode().getInThread()))
                    continue;
                if (wa.accessSameVarWith(r) && happenBefore(wan, A)) {
                    if (w.getWAfter().contains(wa)) {
                        w.getWAfter().remove(wa);
                        wa.getWBefore().remove(w);
                        Set<SharedEvent> was = new HashSet<>();
                        w.getInNode().getWs().forEach(ww -> {
                            was.addAll(ww.getWAfter());
                        });
                        if (!getJoin(was, wan.getWs()).isEmpty()) {
                            w.getInNode().getWAfter().remove(wan);
                            wan.getWBefore().remove(w.getInNode());
                        }
                    }
                }
            }
        }
        rf.getWAfter().removeAll(rmwas);
        rmwas.forEach(wa -> wa.getWBefore().remove(rf));
    }

    /**
     * @return true if node A is porf-before B.
     * @implNote porf only contains po and rf relations.
     */
    private boolean porf(OGNode A, OGNode B) {
        if (A == null || B == null) return false;
        for (OGNode n : A.getSuccessors()) {
            if (n == B) return true;
            if (porf(n, B)) return true;
        }
        for (OGNode n : A.getReadBy()) {
            if (n == B) return true;
            if (porf(n, B)) return true;
        }

        return false;
    }

    /**
     * Let r in chrfrs (belong to B) read from nws.
     * @param B the node that r events of chrfrs locate in.
     * @param C the node that w events of nws locate in.
     */
    private void chReadFrom(Collection<SharedEvent> chrfrs,
                            Collection<SharedEvent> nws,
                            OGNode B, OGNode C) {
        if (!B.getReadFrom().contains(C)) B.getReadFrom().add(C);
        if (!C.getReadBy().contains(B)) C.getReadBy().add(B);
        for (SharedEvent r : chrfrs) {
            for (SharedEvent w : nws) {
                if (r.accessSameVarWith(w)) {
                    SharedEvent rf = r.getReadFrom();
                    assert rf != w;
                    if (rf != null) {
                        assert rf.getReadBy().contains(r);
                        rf.getReadBy().remove(r);
                        r.setReadFrom(w);
                        w.getReadBy().add(r);
                        OGNode A = rf.getInNode();
                        if (A.getReadBy().contains(B)) {
                        // When no R in B reads from A, we remove A from B.read_from.
                        // For all Rs except r, get their inNode.
                            Collection<OGNode> rfns = B.getRs().stream()
                                    .filter(r0 -> (r0 != r) && (r.getReadFrom() != null))
                                    .map(r0 -> r.getReadFrom().getInNode())
                                    .collect(Collectors.toList());
                            if (!rfns.contains(A)) {
                                // If A is not in rfns, it means we can remove A
                                // from B.getReadFrom().
                                B.getReadFrom().remove(A);
                                A.getReadBy().remove(B);
                            }
                        }
                    }

                }
            }
        }
    }

    private void updateFromRead(OGNode A) {
        // Remove all old from read relations and build again.
        A.getFromRead().forEach(fr -> fr.getFromReadBy().remove(A));
        A.getFromRead().clear();
        for (SharedEvent r : A.getRs()) {
            r.getFromRead().forEach(fr -> fr.getFromReadBy().remove(r));
            r.getFromRead().clear();
            Set<SharedEvent> ws = r.getReadFrom() != null
                    ? new HashSet<>(Collections.singleton(r.getReadFrom()))
                    : new HashSet<>();
            while (!ws.isEmpty()) {
                Set<SharedEvent> nws = new HashSet<>();
                for (SharedEvent w : ws) {
                    for (SharedEvent wb: w.getWBefore()) {
                        if (wb.getInNode() != A) {
                            r.getFromRead().add(wb);
                            wb.getFromReadBy().add(r);
                            if (!wb.getInNode().getFromReadBy().contains(A))
                                wb.getInNode().getFromReadBy().add(A);
                            if (!A.getFromRead().contains(wb.getInNode()))
                                A.getFromRead().add(wb.getInNode());
                        }
                    }
                    // If r -fr-> w0, and w0 -wb-> w1, then r -rf-> w1 should hold.
                    nws.addAll(w.getWBefore());
                }
                ws = nws;
            }
        }
    }

    private void updateWriteBefore(OGNode A, OGNode B) {
        Set<SharedEvent> rfs = new HashSet<>();
        A.getRs().forEach(r -> {
            if (B.getWs().contains(r.getReadFrom()))
                rfs.add(r.getReadFrom());
        });

        Set<OGNode> frns = new HashSet<>(A.getFromRead()),
                frrfns = new HashSet<>(),
                hbA = new HashSet<>(),
                visited = new HashSet<>();

        frns.forEach(frn -> frrfns.addAll(frn.getReadFrom()));
        getHb(A, hbA);

        while (!hbA.isEmpty()) {
            for (OGNode n : hbA) {
                for (SharedEvent wn : n.getWs()) {
                    OGNode n1, n2;
                    for (SharedEvent rf : rfs) {
                        // wn and rf may be equal because hbA includes all rf
                        // predecessors of A.
                        if (rf == wn) continue;
                        if (rf.accessSameVarWith(wn)) {
                            setWbForEvent(rf, wn);
                        }
                    }

                    for (OGNode frn : frns) {
                        for (SharedEvent fr : frn.getWs()) {
                            if (fr == wn) continue;
                            if (fr.accessSameVarWith(wn)) {
                                setWbForEvent(fr, wn);
                            }
                        }
                    }

                    for (OGNode frrfn : frrfns) {
                        for (SharedEvent frrf : frrfn.getWs()) {
                            if (frrf == wn) continue;
                            if (frrf.accessSameVarWith(wn)) {
                                setWbForEvent(frrf, wn);
                            }
                        }
                    }
                }
            }
            visited.addAll(hbA);
            Set<OGNode> newhbA = new HashSet<>();
            hbA.forEach(n -> {
                Set<OGNode> hbns = new HashSet<>();
                getHb(n, hbns);
                hbns.forEach(hbn -> {
                    if (!visited.contains(hbn))
                        newhbA.add(hbn);
                });
            });
            hbA = newhbA;
        }
    }

    /**
     * Set e2 writes before e1.
     */
    private void setWbForEvent(SharedEvent e1, SharedEvent e2) {
        OGNode n1 = e1.getInNode(),
                n2 = e2.getInNode();
        if (!e2.getWBefore().contains(e1))
            e2.getWBefore().add(e1);
        if (!e1.getWAfter().contains(e2))
            e1.getWAfter().add(e2);
        if (!n1.getWAfter().contains(n2))
            n1.getWAfter().add(n2);
        if (!n2.getWBefore().contains(n1))
            n2.getWBefore().add(n1);
    }
}