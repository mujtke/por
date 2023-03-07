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
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;
import org.sosy_lab.cpachecker.util.obsgraph.SharedVarsExtractor;

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

        // 2. if there are some obs graphs w.r.t. parState, then we associate all graphs that don't
        // conflict with the cfaEdge (from parArgState -> chArgState) with the chOgState.
        // 1) firstly, extract shared vars' info from the cfaEdge.
        List<ObsGraph> parGraphs = ogsBiMap.get(parOgState);
        // check whether enter/exit/in block area.
        // checkAndSetBlockStatus(parOGPORState.getBlockStatus(), chOGPORState, cfaEdge);
        List<SharedEvent> sharedEvents = sharedVarsExtractor.extractSharedVarsInfo(cfaEdge);
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
        List<ObsGraph> chGraphs = genAndUpdateObsGraphs(parGraphs, chOgState, newOGNode);
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
        Map<ARGState, List<ObsGraph>> revisitResults = revisit(chGraphs, newOGNode);

        // 4. according to the revisitResults, update the ogsBiMap.
        updateObsBiMapForRevisit(reachedSet, revisitResults);

        return Optional.of(PrecisionAdjustmentResult.create(chOgState,
                precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }

    @Override
    public Optional<? extends AbstractState> strengthen(AbstractState pState, Precision pPrecision, Iterable<AbstractState> otherStates) throws CPAException, InterruptedException {
        return PrecisionAdjustment.super.strengthen(pState, pPrecision, otherStates);
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
     * @param chState
     * @param ogNode
     * @return the list of updated graphs, could be empty.
     */
    private List<ObsGraph> genAndUpdateObsGraphs(List<ObsGraph> oldGraphs,
                                          OGPORState chState, OGNode ogNode) {

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
            // case1: the ogNode has been in G.
            if (nodeInG != null) {
                if (G.hasConflict(nodeInG, chState.getThreadStatusMap().values())) {
                    it.remove();
                }
                continue;
            }
//            // case2: ogNode is not in G, then we need add it to G.
            G.addNewNode(ogNode);
        }

        return oldGraphs;
    }

    private Map<ARGState, List<ObsGraph>> revisit(List<ObsGraph> graphs, OGNode node) {

        return null;
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
                List<ObsGraph> nonConflictGraphs = genAndUpdateObsGraphs(pGraphs, chOGState, ogNode);
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
}
