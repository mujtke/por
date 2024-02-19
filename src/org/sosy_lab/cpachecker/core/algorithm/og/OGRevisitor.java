package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bdd.ConditionalStatementHandler;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getAllDot;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.READ;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.WRITE;

@Options(prefix = "algorithm.og")
public class OGRevisitor {

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;

    // Handle conditional statements.
    private static ConditionalStatementHandler CSHandler;

    public OGRevisitor(Map<Integer, List<ObsGraph>> pOGMap,
                       Map<Integer, OGNode> nodeMap,
                       Configuration config,
                       CFA cfa,
                       LogManager logger) throws InvalidConfigurationException {
        this.OGMap = pOGMap;
        this.nodeMap = nodeMap;
        CSHandler = new ConditionalStatementHandler(config, cfa, logger);
    }

    /**
     *
     * @param graphs The list of graphs on which revisit will be performed if needed.
     * @param result All results produced by revisit process will be put into it.
     */
    public void apply(List<ObsGraph> graphs, List<Pair<AbstractState, ObsGraph>> result) {
        if (graphs.isEmpty()) return;

        for (ObsGraph graph : graphs) {
            if (!needToRevisit(graph)) continue;
            try {
                result.addAll(revisit(graph));
            } catch (Exception e) {
                e.printStackTrace();
            }
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

        List<ObsGraph> RG = new ArrayList<>();
        RG.add(g);

        // Debug.
        boolean debug = false;
        int depth = 0;
        while (!RG.isEmpty()) {
            if (debug) System.out.println("Size of RG: " + RG.size() + ", loop depth: "
                    + (++depth));
            ObsGraph G0 = RG.remove(0);
            List<SharedEvent> RE = new ArrayList<>(G0.getRE());
            for (SharedEvent a; !RE.isEmpty();) {
                // If we are handling event e, then in the resulting graphs, it will not be
                // handled again. Otherwise, we may get redundant results.
                a = RE.remove(0);
                // Update event 'a' to be the new lastHandledEvent.
                a.getInNode().setLastHandledEvent(a);
                if (debug) System.out.println("\tSize of G0.RE: " + G0.getRE().size());
                if (debug) System.out.println("\tEntering for loop.");
                switch (a.getAType()) {
                    case READ:
                        if (debug) System.out.println("\t'a' is R(" + a.getVar().getName() + ").");
                        if (debug) System.out.println("\tGet the events that have the same location with 'a'");
                        List<SharedEvent> locA = G0.getSameLocationAs(a);
                        for (SharedEvent w : locA) {
                            ObsGraph Gr = G0.deepCopy(new HashMap<>());
                            if (debug) System.out.println("\tCopying G0 is finished. Try to get the copy of 'a'.");
                            // After deep copy, a not in Gr.
                            SharedEvent ap = getCopyEvent(Gr, G0, a),
                                    wp = getCopyEvent(Gr, G0, w);
                            if (debug) System.out.println("\tSetting the new read-from relations.");
                            Gr.setReadFromAndFromRead(ap, wp);
                            if (debug) System.out.println("\tSetting the new read-from is finished. RG adds the new graph Gr.");
                            // Gr.RE = G0.RE \ {a}.
//                            Gr.RESubtract(ap);
//                            RG.add(Gr);
                            if (debug) System.out.println("\tChecking the consistency of Gr.");
                            if (consistent(Gr)) {
                                if (debug) System.out.println("\tGr is consistent. Try to get the pivot state.");
                                AbstractState pivotState = getPivotState(Gr);
                                result.add(Pair.of(pivotState, Gr));
                                if (debug) System.out.println("\tHaving gotten the pivot state s" + ((ARGState) pivotState).getStateId() + ", add the Gr to the revisit result.");
                            } else {
                                RG.add(Gr);
                            }
                        }

                        break;

                    case WRITE:
                        if (debug) System.out.println("\t'a' is W(" + a.getVar().getName() + "). Try to get the events that have the same location" + " with 'a'.");
                        locA = G0.getSameLocationAs(a);
                        if (debug) System.out.println("\tEntering for loop.");
                        for (SharedEvent r : locA) {
                            if (debug) System.out.println("\tStarting to copy G0.");
                            ObsGraph Gw = G0.deepCopy(new HashMap<>());
                            if (debug) System.out.println("\tCopying G0 is complete. Get the copy event of 'a' and 'r'.");
                            SharedEvent rp = getCopyEvent(Gw, G0, r),
                                    ap = getCopyEvent(Gw, G0, a);
                            if (debug) System.out.println("\tGet the delete.");
                            List<SharedEvent> delete = getDelete(Gw, rp, ap);
                            if (debug) System.out.println("\tGet the deletePlusR.");
                            List<SharedEvent> deletePlusR = getDeletePlusR(delete, rp);
                            if (debug) System.out.println("\tChecking maximality.");
                            if (allMaximallyAdded(Gw, deletePlusR, ap)) {
                                if (debug) System.out.println("\tChecking the maximality is complete. Removing the delete.");
                                Gw.removeDelete(delete);
                                if (debug) System.out.println("\tRemoving the delete is complete. Set the new read-from relation.");
                                Gw.setReadFromAndFromRead(rp, ap);
                                // Remove the corresponding cached assume edges.
                                Gw.removeAssumeEdges(rp, delete);
                                if (debug) System.out.println("\tSetting the new read-from is finished. RG adds the new graph Gw. Check the consistency of Gw.");
                                // Gw.RE = G0.RE \ {ap}.
//                                Gw.RESubtract(ap);
                                RG.add(Gw);
                                if (consistent(Gw)) {
                                    if (debug) System.out.println("\tGw is consistent. Try to get the pivot State.");
                                    AbstractState pivotState = getPivotState(Gw);
                                    result.add(Pair.of(pivotState, Gw));
//                                    result.add(Pair.of(getPivotState(Gw), Gw));
                                    if (debug) System.out.println("\tHaving gotten the pivot state s" + ((ARGState) pivotState).getStateId() + ", add the Gr to the revisit result.");
                                }
                            }
                        }

                    case UNKNOWN:
                }
            }
        }

        return result;
    }

    private AbstractState getPivotState(ObsGraph G) {
        // FIXME: try not going back to the first state.
        OGNode targetNode;
        // Use the preState of the first node, for the simplicity.
        targetNode = G.getNodes().get(0);
        G.setLastNode(null);
        // Before return, clear the trace order and modify the order for nodes that
        // trace after the target node. At the same time, set those nodes invisible in
        // the graph.
        for (OGNode next = targetNode; next != null;) {
            OGNode tmp = next.getTrBefore();
            // Trace order.
            next.setTrAfter(null);
            next.setTrBefore(null);
            // Modify order.
            // Events.
            next.getWs().forEach(w -> {
                if (w.getMoAfter() != null) {
                    w.getMoAfter().setMoBefore(null);
                    w.setMoAfter(null);
                }
                if (w.getMoBefore() != null) {
                    w.getMoBefore().setMoAfter(null);
                    w.setMoBefore(null);
                }
            });
            OGNode finalNext = next;
            next.getMoAfter().forEach(n -> n.getMoBefore().remove(finalNext));
            next.getMoAfter().clear();
            next.getMoBefore().forEach(n -> n.getMoAfter().remove(finalNext));
            next.getMoBefore().clear();
            // Set node invisible.
            next.setInGraph(false);
            // Set lastVisitedEdge null.
            next.setLastVisitedEdge(null);
            G.setTraceLen(G.getTraceLen() - 1);
            next = tmp;
        }

        Preconditions.checkState(targetNode != null);
        Preconditions.checkState(targetNode.getPreState() != null);

        G.setInitialCurrentNodeTable(targetNode.getPreState());
        // Reset the cachedAssumeEdges.
        G.resetCachedAssumeEdge();

        return targetNode.getPreState();
    }

    private boolean allMaximallyAdded(
            ObsGraph G,
            List<SharedEvent> deletePlusR,
            SharedEvent w) {
        for (SharedEvent e : deletePlusR) {
            // e is maximally added?
            List<SharedEvent> previous = new ArrayList<>();
            // Get previous for e.
            for (OGNode n : G.getNodes()) {
                // FIXME: when computing the previous, we consider events or nodes?
                if (n == w.getInNode()) break;
                for (SharedEvent ep : n.getRs()) {
                    if (G.lessThanOrEqual(ep, e) || G.porf(ep, w)) {
                        previous.add(ep);
                    }
                }
                for (SharedEvent ep : n.getWs()) {
                    if (G.lessThanOrEqual(ep, e) || G.porf(ep, w)) {
                        previous.add(ep);
                    }
                }
            }
            //
            boolean eIsWrite = e.getAType() == WRITE;
            SharedEvent ep = eIsWrite ? e : e.getReadFrom();
            Preconditions.checkState(ep != null, "");
            for (int i = previous.size() - 1; i >= 0; i--) {
                // Reverse search.
                SharedEvent ee = previous.get(i);
                if ((ee.getAType() == READ) && eIsWrite && (ee.getReadFrom() == e)) {
                    // \exists r = ee \in previous /\ G.rf(r) = e.
                    return false;
                }
                if (!previous.contains(ep)) {
                    // e' \not\in previous.
                    return false;
                }
                for (SharedEvent epmo : ep.getAllMoBefore()) {
                    if (previous.contains(epmo) && (epmo.getInNode() != ep.getInNode())) {
                        // ep \in previous /\ \exists epmo \in previous s.t. <ep, epmo>
                        // \in G.mo /\ ep, epmo not in the same block.
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Ref: <a herf="https://www.geeksforgeeks.org/detect-cycle-in-a-graph/"></a>
     * @return true if there is no any cycle in g.
     */
    // FIXME
    private boolean consistent(ObsGraph G) {
        int nodeNum = G.getNodes().size();
        if (nodeNum <= 0) return true;
        boolean[] visited = new boolean[nodeNum];
        boolean[] inTrace = new boolean[nodeNum];
        for (int i = 0; i < nodeNum; i++) {
            if (isCyclic(G, i, visited, inTrace))
                return false;
        }
        return true;
    }

    private boolean isCyclic(ObsGraph g, int i, boolean[] visited, boolean[] inTrace) {
        // mark g.getNodes().get(i) as visited and in trace.
        Preconditions.checkState(i >= 0 &&
                i < visited.length && i < inTrace.length);
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

    /**
     * @implNote
     */
    private List<SharedEvent> getDelete(ObsGraph G, SharedEvent r,
                                        SharedEvent w) {
        List<SharedEvent> delete = new ArrayList<>();
        int ridx = G.getNodes().indexOf(r.getInNode()),
                widx = G.getNodes().indexOf(w.getInNode());
        for (int i = ridx + 1; i < widx; i++) {
            OGNode ni = G.getNodes().get(i), nw = G.getNodes().get(widx);
            if (!porf(ni, nw)) {
                delete.addAll(ni.getRs());
                delete.addAll(ni.getWs());
            }
        }

        return delete;
    }

    private List<SharedEvent> getDeletePlusR(List<SharedEvent> delete, SharedEvent r) {
        // Assume:
        //      | r1 |
        //      | r2 |
        // in the same node, we think r1 > r2 if r = r2 and r2 > r1 if r = r1 as they are
        // unordered.
        // => Next step maybe we should store them in an array rather than a set.
        List<SharedEvent> deletePlusR = new ArrayList<>(delete);
        // deletePlusR.add(rp);
        deletePlusR.addAll(r.getInNode().getRs());
        // FIXME: Should we consider the writes?
//        deletePlusR.addAll(r.getInNode().getWs());

        return deletePlusR;
    }

    public static void setRelation(String type,
                                   ObsGraph G,
                                   SharedEvent e1,
                                   SharedEvent e2) {
        // set relation: <e1, e2> \in <_{type}
        OGNode e1n = e1.getInNode(), e2n = e2.getInNode();
        switch (type) {
            case "rf":
                try {
                    SharedEvent e2p = CSHandler.handleAssumeStatement(G, e2, e1);
                    if (!Objects.equals(e2p, e2)) {
                        // Having changed e2 to its coEvent.
                        e2n = e2p.getInNode();
                        e2 = e2p;
                    }
                } catch (UnsupportedCodeException e) {
                    e.printStackTrace();
                }

                // e1 <_rf e2, e2 reads from e1.
                Preconditions.checkArgument(e2.getReadFrom() != e1);
                SharedEvent e2rf = e2.getReadFrom();
                e2.setReadFrom(e1);
                if (e2rf != null) {
                    e2rf.getReadBy().remove(e2);
                    OGNode e2rfn = e2rf.getInNode();
                    // Remove e2rfn from e2n's read-from set if there is no event in
                    // other nodes reading from e2n.
                    List<OGNode> re2rfn =
                            e2n.getRs().stream().map(SharedEvent::getReadFrom)
                                    .filter(Objects::nonNull)
                                    .map(SharedEvent::getInNode)
                                    .filter(n0 -> n0 == e2rfn).collect(Collectors.toList());
                    if (re2rfn.isEmpty()) {
                        e2n.getReadFrom().remove(e2rfn);
                        e2rfn.getReadBy().remove(e2n);
                    }
                }
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

    private SharedEvent getCopyEvent(ObsGraph G, ObsGraph G0, SharedEvent e) {
        // e is in the graph G0, and G is the copy of G0.
        // Try to get the e's copy in G.
        int eidx = G0.getNodes().indexOf(e.getInNode());
        OGNode epn = G.getNodes().get(eidx);
        List<SharedEvent> eps = e.getAType() == READ
                ? epn.getRs().stream()
                .filter(e::accessSameVarWith).collect(Collectors.toList())
                : epn.getWs().stream()
                .filter(e::accessSameVarWith).collect(Collectors.toList());
        assert eps.size() == 1;

        return eps.iterator().next();
    }

    /**
     * @return true if node A is porf-before B.
     * @implNote porf only contains po and rf relations.
     */
    public static boolean porf(OGNode A, OGNode B) {
        // FIXME: avoid endless loop.
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
}