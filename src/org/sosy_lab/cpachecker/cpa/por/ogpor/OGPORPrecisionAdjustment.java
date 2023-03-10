package org.sosy_lab.cpachecker.cpa.por.ogpor;

import static com.google.common.collect.FluentIterable.from;
import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;
import org.sosy_lab.cpachecker.util.obsgraph.SharedVarsExtractor;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OGPORPrecisionAdjustment implements PrecisionAdjustment {

    // the map of between states and list<og>s. And this just points to the map created in
    // GlobalInfo.
    private final BiMap<AbstractState, List<ObsGraph>> ogsBiMap;

    private static final SharedVarsExtractor sharedVarsExtractor = new SharedVarsExtractor();

    private final LogManager logger;

    public OGPORPrecisionAdjustment(LogManager pLogger) {

        logger = pLogger;
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        if (ogInfo == null) {
            logger.log(Level.SEVERE, "When using OGPORCPA, the option 'utils.globalInfo.OGInfo" +
                    ".useBiMap' must be set as true!");
        }
        ogsBiMap = ogInfo.getBiOGMap();
    }

    @Override
    public Optional<PrecisionAdjustmentResult> prec(
            AbstractState state,
            Precision precision,
            UnmodifiableReachedSet reachedSet,
            Function<AbstractState, AbstractState> stateProjection,
            AbstractState fullState) throws CPAException, InterruptedException {

        //debug.
//        if (true)
//            return Optional.of(PrecisionAdjustmentResult.create(state, precision, PrecisionAdjustmentResult.Action.CONTINUE));
        assert fullState instanceof ARGState && state instanceof OGPORState;
        ARGState chArgState = (ARGState) fullState,
                parArgState = chArgState.getParents().iterator().next(); // we think parents.size() <= 1 now.
        OGPORState chOgState = (OGPORState) state,
                parOgState = AbstractStates.extractStateByType(parArgState, OGPORState.class);
        CFAEdge cfaEdge = parArgState.getEdgeToChild(chArgState);

        // 1. if there is not any obs graphs w.r.t. parOgState, then the exploration stops.
        if (ogsBiMap.get(parOgState) == null) {
            return Optional.empty();
        }
        // TODO: [s1, s2, .., sn], sn will be visited first, but the precision of s1 will be
        //  adjusted first. So we try to leave the ogGraphs for sn instead s1.
        List<ARGState> siblings = new ArrayList<>(parArgState.getChildren());
        if (!chArgState.equals(siblings.get(siblings.size() - 1))) {
            return Optional.of(PrecisionAdjustmentResult.create(state, precision,
                    PrecisionAdjustmentResult.Action.CONTINUE));
        }

        // 2. if there are some obs graphs w.r.t. parState, then we associate all graphs that don't
        // conflict with the cfaEdge (from parArgState -> chArgState) with the chOgState.
        // 1) firstly, extract shared vars' info from the cfaEdge.
        List<ObsGraph> parGraphs = ogsBiMap.get(parOgState);
        // check whether enter/exit/in block area.
        // checkAndSetBlockStatus(parOGPORState.getBlockStatus(), chOGPORState, cfaEdge);
        List<SharedEvent> sharedEvents = sharedVarsExtractor.extractSharedVarsInfo(cfaEdge);
        updateLastAccessTable(sharedEvents, parOgState, chOgState); // update the last access table
        if (sharedEvents.isEmpty()) {
            // no shared vars access, i.e., cfaEdge just access local vars.
            // in this case, we associate all graphs w.r.t. parOgState with chOgState directly.
            ogsBiMap.inverse().put(parGraphs, chOgState);
            return Optional.of(PrecisionAdjustmentResult.create(chOgState, precision,
                    PrecisionAdjustmentResult.Action.CONTINUE));
        }

        // 2) if the cfaEdge access global vars, then we generate a OGNode for it.
        OGNode newOGNode = genNewOGNode(sharedEvents, chOgState, parArgState);

        // 3) update the graphs in parGraphs by using newOGNode, and associate the newly produced
        // graphs with chOgState.
        List<ObsGraph> chGraphs = genAndUpdateObsGraphs(parGraphs, parOgState, newOGNode);
        if (chGraphs.isEmpty()) {
            // after the update, no new graphs produced because of the conflict, so the exploration
            // stop here.
            return Optional.empty();
        }

        // else, we associate the chGraphs with chOgState (remember that, till now the chGraphs
        // is associated with the parOgState).
        // s0 <-> parGraphs  ====>  s1 <-> chGraphs.
        ogsBiMap.inverse().put(chGraphs, chOgState);

        // 3. when 'chGraphs' is not empty, we need to run all possible revisit processes, which
        // include: forward revisit (for R) and backward revisit (for W).
        Map<ARGState, List<ObsGraph>> revisitResults = revisit(chGraphs);

        // 4. according to the revisitResults, update the ogsBiMap.
        updateObsBiMapForRevisit(reachedSet, revisitResults);

        return Optional.of(PrecisionAdjustmentResult.create(chOgState,
                precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }

    @Override
    public Optional<? extends AbstractState> strengthen(AbstractState pState, Precision pPrecision, Iterable<AbstractState> otherStates) throws CPAException, InterruptedException {
        return PrecisionAdjustment.super.strengthen(pState, pPrecision, otherStates);
    }

    private void updateLastAccessTable(List<SharedEvent> sharedEvents, OGPORState parState,
                                       OGPORState chState) {
        List<SharedEvent> WEvents = from(sharedEvents)
                .filter(e -> e.getAType().equals(SharedEvent.AccessType.WRITE))
                .toList();
        assert WEvents.size() <= 1 : "more than one W event in an edge is not supported now.";
        SharedEvent WEvent = WEvents.isEmpty() ? null : WEvents.get(0);
        Map<Var, SharedEvent> chTable = chState.getLastAccessTable();
        chTable.putAll(parState.getLastAccessTable());
        if (WEvent != null) {
            chTable.put(WEvent.getVar(), WEvent);
        }
    }

    private OGNode genNewOGNode(@NonNull List<SharedEvent> events, OGPORState state,
                                ARGState preARGState) {

        assert !events.isEmpty() : "OGNode must contains access to global vars!";
        String transInThread = state.getMultiThreadState().getTransThread();
        assert transInThread != null : "transInThread must not be null!";
        return new OGNode(events, state.getThreadStatusMap().get(transInThread),
                state.getBlockStatus(), preARGState);
    }

    /**
     * This method update the graphs in {@param oldGraphs} according to the new produced
     * {@param ogNode}.
     * @param oldGraphs the list of graphs that wait to update.
     * @param parState
     * @param ogNode
     * @return the list of updated graphs, could be empty.
     */
    private List<ObsGraph> genAndUpdateObsGraphs(List<ObsGraph> oldGraphs, OGPORState parState,
                                          OGNode ogNode) {

        if (oldGraphs.isEmpty()) {
            // oldGraphs is empty means that there isn't any OGNode added still now, so we create a
            // new graph which only contain the 'ogNode' and put the new graph into oldGraphs.
            ObsGraph newGraph = new ObsGraph();
            // add ogNode to oldGraphs with update the order relations.
            newGraph.addNewNode(ogNode);
            oldGraphs.add(newGraph);

            return oldGraphs;
        }

        // else, there some graphs w.r.t. parState, for each of them, we need to handle different
        // cases.
        Iterator<ObsGraph> it = oldGraphs.iterator();
        while(it.hasNext()) {
            ObsGraph G = it.next();
            OGNode nodeInG = G.get(ogNode); // handle the 'equals' method of OGNode carefully.
            // case1: the 'ogNode' has been in G.
            if (nodeInG != null) {
                // update the nodeInG's threadStatus by 'ogNode'.
                nodeInG.setThreadStatus(ogNode.getThreadStatus());
                if (G.hasConflict(nodeInG, parState)) {
                    it.remove();
                }
                continue;
            }
            // case2: ogNode is not in G, then we need add it to G.
            OGNode copyOfOgNode = ogNode.copy(); // deep copy.
            G.addNewNode(copyOfOgNode);
            G.setNeedToRevisit(true);
        }

        return oldGraphs;
    }

    private Map<ARGState, List<ObsGraph>> revisit(List<ObsGraph> obsGraphs) {
        assert !obsGraphs.isEmpty();

        Map<ARGState, List<ObsGraph>> results = new HashMap<>();
        //debug.
//        if (true) return results;
        for (ObsGraph G : obsGraphs) { // for every obsGraph.
            if (!G.needToRevisit()) { // not all graphs need to revisit.
                continue;
            }
            OGNode ogNodeInG = G.getLastNode();
            for (SharedEvent e : ogNodeInG.getEvents()) { // for every shared event.
                switch (e.getAType()) {
                    case READ:
                        forwardRevisit(G, ogNodeInG, e, results);
                        break;
                    case WRITE:
                        backwardRevisit(G, ogNodeInG, e, results);
                        break;
                    case UNKNOWN:
                    default:
                }
            }
            G.setNeedToRevisit(false);
        }

        return results;
    }

    /**
     * This method performs the revisit for the given R event in the lastNode on the graph G. The
     * result of one revisit is a pair of targetARGState s0 and graph G0, in which s0 is the
     * preARGState of the W event that we find R could read from, and G0 is the newly produced
     * graph after revisit process.
     * @param G input graph.
     * @param lastNode the lastNode in the {@param G}, and the start node of revisit.
     * @param r the R event that look for new W events to read.
     * @param results the list of <targetARGState, new graph>
     * @implNode after find the W event w1 that {@param r} could read from, we record their
     * location in {@param G} and copy a new graph 'newG' which we will finally put into the
     * results. We actually perform revisit in 'newG' instead {@param G}.
     */
    private void forwardRevisit(ObsGraph G, OGNode lastNode, SharedEvent r, Map<ARGState,
            List<ObsGraph>> results) {

        // r --> w0 ==> r --> w1, in which w1 is traceBefore w0.
        // if w0 <hb r, then w1 shouldn't <hb w0 (<hb = happen before).
        // if w0 !<hb r, then there is no limit to w1.
        SharedEvent w0 = r.getReadFrom(), w1 = w0;
        if (w0 == null) { // r doesn't read from any event till now.
            return;
        }
        boolean w0_hb_r = w0.hb(r);
        int rNodeIndexInG = G.getNodes().indexOf(lastNode),
                rIndexInNode = lastNode.getEvents().indexOf(r);
        assert rNodeIndexInG >= 0 && rIndexInNode >= 0;
        do {
            w1 = G.searchNewWEvent(w1);
            if (w1 == null) { // no new WEvent that is before r and could be read by r.
                break;
            }
            if (w0_hb_r && w1.hb(w0)) {
                continue;
            }
            // else, r can read from w1, we generate a new graph and put it into results.
            int w1NodeIndexInG = G.getNodes().indexOf(w1.getOgNode()),
                    w1IndexInNode = w1.getOgNode().getEvents().indexOf(w1);
            ObsGraph newG= new ObsGraph(G);
            newG.setReadFrom4FR(rNodeIndexInG, rIndexInNode, w1NodeIndexInG, w1IndexInNode);
            newG.resetLastNode(w1NodeIndexInG); // for newG, the last Node changes to w1Node.
            ARGState targetARGState = w1.getOgNode().getPreARGState();
            if (results.get(targetARGState) != null) {
                results.get(targetARGState).add(newG);
            } else {
                results.put(targetARGState, List.of(newG));
            }
        } while (true);
    }

    private void backwardRevisit(ObsGraph G, OGNode lastNode, SharedEvent w, Map<ARGState,
            List<ObsGraph>> results) {

        SharedEvent r = null, r0 = w;
        int wNodeIndexInG = G.getNodes().indexOf(lastNode), wIndexInNode =
                lastNode.getEvents().indexOf(w);
        do {
            r = G.searchNewREvent(r0);
            if (r == null) {
                break;
            }
            if (r.hb(w)) {
                r0 = r;
                continue;
            }
            // else, we need to check whether we could back revisit.
            boolean brIsAllowed = true;
            // first we find which event need to delete.
            List<OGNode> delete = new ArrayList<>();
            OGNode lastHbNode = lastNode; // record the last node that <hb lastNode (w in).
            for (OGNode pre = lastNode.getPredecessor(); !pre.equals(r.getOgNode()); pre =
                    pre.getPredecessor()) {
                if (pre.hb(lastHbNode)) {
                    lastHbNode = pre;
                    continue;
                }
                delete.add(pre);
            }
            if (delete.size() > 1) {
                // if delete has more than one event, then we check whether there exist any pair
                // <r, w>, where r reads from w is the result of a back revisit.
                for (OGNode n : delete) {
                    for (SharedEvent e : n.getEvents()) {
                        if (e.getAType().equals(SharedEvent.AccessType.READ)) {
                            SharedEvent tw = e.getReadFrom();
                            if ((tw != null)
                                    && (tw.getOgNode() != n) // here we use !=, instead equals.
                                    && (delete.contains(tw.getOgNode()))
                                    && (G.getNodes().indexOf(n) < G.getNodes().indexOf(tw.getOgNode()))) {
                                // TODO: the correctness of fourth condition guaranteed ?
                                brIsAllowed = false;
                            }
                            break;
                        }
                    }
                }
            }
            if (!brIsAllowed) {
                continue;
            }

            // if br is allowed, i.e., r can read from w, then we generate a new graph and put it
            // into results.
            int rNodeIndexInG = G.getNodes().indexOf(r.getOgNode()), rIndexInNode =
                    r.getOgNode().getEvents().indexOf(r);
            ObsGraph newG = new ObsGraph(G);
            newG.setReadFrom4BR(rNodeIndexInG, rIndexInNode, wNodeIndexInG, wIndexInNode, delete);
            int newLastNodeIndex = r.getOgNode().getPredecessor() != null ?
                    G.getNodes().indexOf(r.getOgNode().getPredecessor()) : -1;
            newG.resetLastNode(newLastNodeIndex);
            ARGState targetARGState = r.getOgNode().getPreARGState();
            if (results.get(targetARGState) != null) {
                results.get(targetARGState).add(newG);
            } else {
                results.put(targetARGState, List.of(newG));
            }

        } while (true);
    }

    /**
     * This method associate the graphs produced in revisit processes with some OGPORStates.
     * Assume that the revisit processes produce graphs ogs = {G1, G2, ..., Gn}, and these graphs
     * are associated with different ARGStates s0, s1, ..., sm. For each pair of them,
     * s0 -> <Gi, ..., Gj>, if s0 have child ARGStates in wait list, then we update the Graphs in
     * <Gi, ..., Gj> and associate the updated graphs (if exist) with OGPORStates in children.
     *
     * @param reachedSet
     * @param revisitResults a key-value pair is like: s1 -> <G1, G2, .., Gn>
     * @implNote the list<ObsGraph> in revisitResults shouldn't be the shallow copy of one in
     * ogsBiMap's values. And any list<ObsGraph> shouldn't contain two same element.
     */
    private void updateObsBiMapForRevisit(UnmodifiableReachedSet reachedSet, Map<ARGState,
            List<ObsGraph>> revisitResults) {

        if (revisitResults == null || revisitResults.isEmpty()) {
            return;
        }
        for (Map.Entry<ARGState, List<ObsGraph>> entry : revisitResults.entrySet()) {
            // s0 -> <G1, G2, ... , Gn>
            ARGState pARGState = entry.getKey();
            // TODO: pARGState's children may be not in wait list, how to solve the problem ?
            // And whether we could use block to help deal with it.
            pARGState = getTargetARGState(reachedSet, pARGState);
            List<ObsGraph> pGraphs = entry.getValue();
            OGPORState pOGState = AbstractStates.extractStateByType(pARGState, OGPORState.class);
            // next, we search whether there are any pARGState's children are still in wait list,
            // if so, then we update the pGraphs and associate the results with children.
            List<ARGState> chARGStates = from(pARGState.getChildren())
                    .stream()
                    .filter(s -> reachedSet.getWaitlist().contains(s))
                    .collect(Collectors.toList()); // Children that still in wait list.
            // update the pGraphs and associate the corresponding result with each child.
            for (ARGState chARGState : chARGStates) {
                OGPORState chOGState = AbstractStates.extractStateByType(chARGState, OGPORState.class);
                assert chOGState != null : "ARGState without an OGPORState is not allowed when use OGPORCPA";
                List<ObsGraph> chGraphs = ogsBiMap.get(chOGState);
                CFAEdge inEdge = pARGState.getEdgeToChild(chARGState);
                List<SharedEvent> sharedEvents = sharedVarsExtractor.extractSharedVarsInfo(inEdge);
                if (sharedEvents.isEmpty()) {
                    // associate pGraphs with child directly.
                    if (chGraphs == null) {
                        ogsBiMap.put(chOGState, pGraphs);
                    } else {
                        chGraphs.addAll(pGraphs);
                    }
                    continue;
                }
                // else, sharedEvents is not empty, we should remove graphs that conflict with
                // 'inEdge' from pGraphs.
                OGNode ogNode = genNewOGNode(sharedEvents, chOGState, pARGState);
                List<ObsGraph> nonConflictGraphs = genAndUpdateObsGraphs(pGraphs, pOGState, ogNode);
                if (!nonConflictGraphs.isEmpty()) {
                    if (chGraphs == null) {
                        ogsBiMap.put(chOGState, nonConflictGraphs);
                    } else {
                        chGraphs.addAll(nonConflictGraphs);
                    }
                }
            }

        }
    }

    private @NonNull ARGState getTargetARGState(UnmodifiableReachedSet reachedSet, ARGState p) {

        OGPORState pOgState = AbstractStates.extractStateByType(p, OGPORState.class);
        assert pOgState != null;
        if (pOgState.getMultiThreadState().getThreadIds().size() > 1) {
            // this means p is a target ARGState because there are some paths spawned from it.
            return p;
        }
        // else, p just have one child and can't be a target ARGState.
        ARGState ch = null;
        while (p != null) {
            Collection<ARGState> chs = p.getChildren(), chW;
            if (chs.size() > 1) {
                // this case means p has more than one edge (should be assume edge), we should
                // choose last visited and not in-waitlist child.
                // TODO: the last visited state id is minimal ?
                chW = chs.stream()
                        .filter(s -> reachedSet.getWaitlist().contains(s))
                        .collect(Collectors.toSet());
                chs.removeAll(chW);
                ch = chooseMinIdChild(chs);
                p = ch;
                continue;
            }
            if (chs.size() == 1) {
                p = chs.iterator().next();
                pOgState = AbstractStates.extractStateByType(p, OGPORState.class);
                if (pOgState.getMultiThreadState().getThreadIds().size() > 1) {
                    break;
                }
            }
        }

        return p;
    }

    private @NonNull ARGState chooseMinIdChild(Collection<ARGState> chs) {
        // p has more than one child.
        Iterator<ARGState> it = chs.iterator();
        ARGState minIdCh = it.next(), ch = null;
        while (it.hasNext()) {
            ch = it.next();
            minIdCh = minIdCh.getStateId() < ch.getStateId() ? minIdCh : ch;
        }

        return minIdCh;
    }
}
