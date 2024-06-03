package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bdd.ConditionalStatementHandler;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.READ;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.WRITE;

@Options(prefix = "algorithm.og")
public class OGRevisitor {

    private boolean enableDebug;
    public enum REVISIT_TYPE {
        READ, WRITE
    }

    // Handle conditional statements.
    private static ConditionalStatementHandler CSHandler;

    public OGRevisitor(
            Configuration config,
            CFA cfa,
            LogManager logger,
            boolean pEnableDebug) throws InvalidConfigurationException {
        CSHandler = new ConditionalStatementHandler(config, cfa, logger);
        this.enableDebug = pEnableDebug;
    }

    /**
     * @param precision
     * @param graphs The list of graphs on which revisit will be performed if needed.
     * @param result All results produced by revisit process.
     */
    public void apply(ARGState parState,
            Precision precision,
            List<ObsGraph> graphs,
            List<Pair<AbstractState, ObsGraph>> result) {
        if (graphs.isEmpty())
            return;

        for (ObsGraph graph : graphs) {
            if (!graph.needToRevisit())
                continue;
            result.addAll(revisit(parState, precision, graph));
        }
    }

    // parState: indicating where the revisit takes place.
    private List<Pair<AbstractState, ObsGraph>> revisit(ARGState parState,
            Precision precision,
            ObsGraph g) {
        List<Pair<AbstractState, ObsGraph>> result = new ArrayList<>();
        // List of the graphs that need to revisit.
        List<ObsGraph> RG = new ArrayList<>();
        RG.add(g);

        while (!RG.isEmpty()) {
            ObsGraph G0 = RG.remove(0);

            // List of the events that need to revisit.
            List<SharedEvent> RE = new ArrayList<>(G0.getRE());
            for (SharedEvent a; !RE.isEmpty();) {
                // If we are handling event e, then in the resulting graphs, it will not be
                // handled again. Otherwise, we may get redundant results.
                a = RE.remove(0);
                // Update event 'a' as the new last-handled event.
                a.getInNode().setLastHandledEvent(a);
                // events that access the same var with event 'a'.
                List<SharedEvent> sameLocationA;
                switch (a.getAType()) {
                    case READ:
                        sameLocationA = G0.getSameLocationAs(a);
                        for (SharedEvent w : sameLocationA) {
                            Map<Object, Object> memo = new HashMap<>();
                            ObsGraph Gr = G0.deepCopy(memo), coGr;
                            // NOTE: After the deep copy, because 'a' is not in Gr,
                            //  we use its deep copy 'ap' for later revisit.
                            assert memo.containsKey(System.identityHashCode(a))
                                    && memo.containsKey(System.identityHashCode(w));
                            SharedEvent ap = (SharedEvent) memo.get(System.identityHashCode(a)),
                                    wp = (SharedEvent) memo.get(System.identityHashCode(w));
                            // TODO: remove cached assumption edges when needed.
                            Pair<ObsGraph, ObsGraph> GrAndcoGr =
                                    setReadFrom(Gr, ap, wp, REVISIT_TYPE.READ, precision);

                            Gr = GrAndcoGr.getFirstNotNull(); // Gr must not be null.
                            handleRevisitResult(result, RG, Gr, parState, enableDebug);
                            coGr = GrAndcoGr.getSecond(); // coGr may be null.
                            if (coGr != null)
                                handleRevisitResult(result, RG, coGr, parState, enableDebug);
                        }
                        break;

                    case WRITE:
                        sameLocationA = G0.getSameLocationAs(a);
                        for (SharedEvent r : sameLocationA) {
                            Map<Object, Object> memo = new HashMap<>();
                            ObsGraph Gw = G0.deepCopy(memo), coGw;
                            assert memo.containsKey(System.identityHashCode(a))
                                    && memo.containsKey(System.identityHashCode(r));
                            SharedEvent ap = (SharedEvent) memo.get(System.identityHashCode(a)),
                                    rp = (SharedEvent) memo.get(System.identityHashCode(r));

                            List<SharedEvent> delete = getDelete(Gw, rp, ap);
                            List<SharedEvent> deletePlusR = getDeletePlusR(delete, rp);
                            if (!allMaximallyAdded(Gw, deletePlusR, ap))
                                continue;
                            // Else, the check for maximality passes.
                            Gw.removeDelete(delete, rp);
                            Pair<ObsGraph, ObsGraph> GwAndcoGw =
                                    setReadFrom(Gw, rp, ap, REVISIT_TYPE.WRITE, precision);

                            Gw = GwAndcoGw.getFirstNotNull(); // Gw must not be null.
                            handleRevisitResult(result, RG, Gw, parState, enableDebug);
                            coGw = GwAndcoGw.getSecond(); // coGw may be null.
                            if (coGw != null)
                                handleRevisitResult(result, RG, coGw, parState, enableDebug);
                        }
                        break;

                    default:
                        //
                }
            }
        }

        return result;
    }

    private void handleRevisitResult(final List<Pair<AbstractState, ObsGraph>> result,
            final List<ObsGraph> RG,
            final ObsGraph G,
            final ARGState parState,
            boolean enableDebug) {
        if (G == null)
            return;

        AbstractState pivotState = getPivotState(G);
        if (consistent(G)) {
            // If G is consistent, add it to the result.
        } else {
            if (G.needToRevisit()) {
                // If G is not consistent but re-visitable, then just add it to the RG,
                // and waiting for the next revisit.
                RG.add(G);
                return;
            } else {
                // If G is not consistent and re-visitable, then we also need to add it
                // to the result, because G may become re-visitable in the future.
                // Otherwise, G will get blocked somewhere.
            }
        }

        result.add(Pair.of(pivotState, G));
        // debug.
        G.setCreationState(parState);
    }

    /**
     * FIXME
     * By letting {@param r} read from {@param w}, we get a new rf.
     * NOTE: When {@param w} contains indeterminacy, we may get two graphs as the
     * result, one of them is G and the other is coG.
     * @param G The graph that {@param r} and {@param w} locate in.
     * @param r The read event which will read from {@param w}.
     * @param w The write event that will be read by {@param r}.
     * @param type the type of revisit.
     * @param precision //
     * @return a pair of G and coG.
     * @implNote we deduce new fr after setting the new rf above.
     */
    private Pair<ObsGraph, ObsGraph> setReadFrom(ObsGraph G,
            SharedEvent r,
            SharedEvent w,
            REVISIT_TYPE type,
            Precision precision) {
        // When setting read-from relation, we may get a new graph because of the indeterminacy.
        ObsGraph coGraph = null;
        Pair<Boolean, Boolean> evaluation = null;
        try {
            // evaluation = <A, B>
            // A = true if it leads to conflict that r reads from w.
            // B = true if the w is an indeterminate assignment.
            evaluation = CSHandler.handleAssumeStatement(G, r, w, precision);
        } catch (UnsupportedCodeException e) {
//            e.printStackTrace();
        }

        assert evaluation != null;
        boolean hasConflict = evaluation.getFirstNotNull(),
                hasIndeterminacy = evaluation.getSecondNotNull();
        // hasIndeterminacy => !hasConflict
        if (hasIndeterminacy) {
            // We get a new graph because r reads from a indeterminate value.
            Map<Object, Object> memo = new HashMap<>();
            coGraph = G.deepCopy(memo);
            assert memo.containsKey(System.identityHashCode(r))
                    && memo.containsKey(System.identityHashCode(w)) : "Wrong copy result.";
            SharedEvent rp = (SharedEvent) memo.get(System.identityHashCode(r)),
                    wp = (SharedEvent) memo.get(System.identityHashCode(w));

            SharedEvent corp = coGraph.changeAssumeNode(rp);
            setRelation("rf", coGraph, wp, corp);
            if (type == REVISIT_TYPE.READ) {
                // FIXME: remove cached assume edges?
                corp.getInNode().removeEventAfter(corp);
            }
            coGraph.deduceFromRead();
            // FIXME: Have we set corp as the last-handled event already?
            OGNode corpNode = corp.getInNode();
            assert  corpNode != null;
            corpNode.setLastHandledEvent(corp);
            setRelation("rf", G, w, r);
            if (type == REVISIT_TYPE.READ) {
                // FIXME: remove cached assume edges?
                r.getInNode().removeEventAfter(r);
            }
            G.deduceFromRead();
        }

        if (hasConflict) {
            // We don't need to create a new graph despite the conflict.
            // Instead, we replace r with the event co-r ('co' means conjugate) that
            // comes from the assume statement [!(x > 1)].
            SharedEvent cor = G.changeAssumeNode(r);
            setRelation("rf", G, w, cor);
            if (type == REVISIT_TYPE.READ) {
                // FIXME: remove cached assume edges?
                cor.getInNode().removeEventAfter(cor);
            }
            G.deduceFromRead();
            // FIXME: Have we set corp as the last-handled event already?
            OGNode corNode = cor.getInNode();
            assert corNode != null;
            corNode.setLastHandledEvent(cor);
        } else {
            // No conflict.
            setRelation("rf", G, w, r);
            if (type == REVISIT_TYPE.READ) {
                // FIXME: remove cached assume edges?
                r.getInNode().removeEventAfter(r);
            }
            G.deduceFromRead();
        }

        return Pair.of(G, coGraph);
    }

    private AbstractState getPivotState(ObsGraph G) {
        // TODO: try not going back to the first state.
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

            // FIXME: don't remove mo relations here?
            // Modify order.
            // Events.
//            next.getWs().forEach(w -> {
//                if (w.getMoAfter() != null) {
//                    w.getMoAfter().setMoBefore(null);
//                    w.setMoAfter(null);
//                }
//                if (w.getMoBefore() != null) {
//                    w.getMoBefore().setMoAfter(null);
//                    w.setMoBefore(null);
//                }
//            });
            // Nodes.
            OGNode finalNext = next;
//            next.getMoAfter().forEach(n -> n.getMoBefore().remove(finalNext));
//            next.getMoAfter().clear();
//            next.getMoBefore().forEach(n -> n.getMoAfter().remove(finalNext));
//            next.getMoBefore().clear();

            // FIXME: happen-before relation for nodes.
            next.getHappenBefore().forEach(n -> n.getHappenAfter().remove(finalNext));
            next.getHappenBefore().clear();
            next.getHappenAfter().forEach(n -> n.getHappenBefore().remove(finalNext));
            next.getHappenAfter().clear();

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


    /**
     * Checking whether all events in {@param deletePlusR} are added maximally.
     * @param deletePlusR events need to check.
     */
    private boolean allMaximallyAdded(
            ObsGraph G,
            List<SharedEvent> deletePlusR,
            SharedEvent w) {
        for (SharedEvent e : deletePlusR) {
            List<SharedEvent> previous = new ArrayList<>();
            // Get previous for e.
            // FIXME: how to get correct 'previous'?
            for (OGNode n : G.getNodes()) {
                // e.getInNode() must be added before w.getInNode()
                for (SharedEvent ep : n.getEvents()) {
                    if (G.lessThanOrEqual(ep, e) || G.porf(ep, w))
                        previous.add(ep);
                }
            }

            // e is maximally added?
            boolean maximallyAdded = checkMaximality(previous, e);
            if (!maximallyAdded)
                return false;
        }
        return true;
    }

    /**
     * Checking whether e is added maximally by traversing all events in
     * {@param previous}.
     * @param previous the events must be kept after the revisit?
     */
    private boolean checkMaximality(List<SharedEvent> previous, SharedEvent e) {
        boolean eIsWrite = e.getAType() == WRITE;
        SharedEvent ep = eIsWrite ? e : e.getReadFrom();
        assert ep != null : "Cannot find ep for event: " + e;
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
     * Get the events that will get removed after the revisit.
     * @param r the event after this will get remove when rules satisfied.
     * @param w the event {@param r} read from.
     * @implNote some statements like X = Y may contain more than one event, for this
     * case, we regard the statement atomic, i.e., write to X won't get delete when
     * {@param r} is the read to Y.
     * NOTE: If the algorithm is correct, then there shouldn't be previous results
     *  in the deleted events.
     * A deleted event e should follow these rules:
     * 1. e is added after {@param r}.
     * 2. e shouldn't porf {@param w}.
     * FIXME: the rules above matters.
     */
    private List<SharedEvent> getDelete(ObsGraph G, SharedEvent r, SharedEvent w) {
        List<SharedEvent> delete = new ArrayList<>();

        // Handle the node that r in.
        OGNode rNode = r.getInNode();
        List<SharedEvent> events = r.getInNode().getEvents();
        for (int i = events.indexOf(r); i < events.size(); i++) {
            SharedEvent e = events.get(i);
            if (r.inSameEdgeWith(e))
                continue;
            assert rNode.getBlockEdges().indexOf(e.getInEdge()) >
                    rNode.getBlockEdges().indexOf(r.getInEdge()) :
                    "Trying to delete an event that shouldn't be!";
            delete.add(e);
        }

        // Handle other nodes that added after rNode.
        int rNodeIdx = G.getNodes().indexOf(r.getInNode()),
                wNodeIdx = G.getNodes().indexOf(w.getInNode());
        for (int i = rNodeIdx + 1; i < wNodeIdx; i++) {
            OGNode ni = G.getNodes().get(i), nw = G.getNodes().get(wNodeIdx);
            if (!porf(ni, nw)) {
                delete.addAll(ni.getEvents());
            }
        }

        return delete;
    }

    // FIXME: we should consider all events that locate in the same node with r?
    private List<SharedEvent> getDeletePlusR(List<SharedEvent> delete, SharedEvent r) {
        List<SharedEvent> deletePlusR = new ArrayList<>(delete);
        deletePlusR.addAll(r.getInNode().getEvents().stream()
                .filter(r::inSameEdgeWith).collect(Collectors.toList()));

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
                // e1 <_rf e2, e2 reads from e1.
//                Preconditions.checkArgument(e2.getReadFrom() != e1);
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
//                Preconditions.checkArgument(!e1.getFromRead().contains(e2));
                e1.getFromRead().add(e2);
                e2.getFromReadBy().add(e1);
                if (!e1n.getFromRead().contains(e2n)) e1n.getFromRead().add(e2n);
                if (!e2n.getFromReadBy().contains(e1n)) e2n.getFromReadBy().add(e1n);
                break;

            case "mo":
//                Preconditions.checkArgument(e1.getMoBefore() != e2);
                e1.setMoBefore(e2);
                e2.setMoAfter(e1);
                if (!e1n.getMoBefore().contains(e2n)) e1n.getMoBefore().add(e2n);
                if (!e2n.getMoAfter().contains(e1n)) e2n.getMoAfter().add(e1n);
                break;
            default:
        }
    }

    // FIXME: replace this method, get value from the map used in deep copy.
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
        if (A == null || B == null)
            return false;

        for (OGNode n : A.getSuccessors()) {
            if (n == B || porf(n, B))
                return true;
        }

        for (OGNode n : A.getReadBy()) {
            if (n == B || porf(n, B))
                return true;
        }

        // FIXME: using fr or not?
//        for (OGNode n : A.getFromRead()) {
//            if (n == B || porf(n, B))
//                return true;
//        }

        return false;
    }
}