
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
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OGPORPrecisionAdjustment implements PrecisionAdjustment {

    private final LogManager logger;

    public OGPORPrecisionAdjustment(LogManager pLogger) {

        logger = pLogger;
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
        assert cfaEdge != null && parOgState != null;

        /*
        // debug.
        int parStateId = parArgState.getStateId();
        int a = parStateId;

        // TODO: [s1, s2, .., sn], sn will be visited first, but the precision of s1 will be
        //  adjusted first. How to deal with this?

        // 1. if there is not any obs graphs w.r.t. parOgState, then the exploration stops.
        if (ogsBiMap.get(parOgState) == null) {
            return Optional.empty();
        }

        // 2. if there are some obs graphs w.r.t. parState, then we associate all graphs that don't
        // conflict with the cfaEdge (from parArgState -> chArgState) with the chOgState.
        if (ogsBiMap.get(parOgState).isEmpty()) {
            // if it's the case that graph w.r.t. parOgState is empty originally.

            // else, it's the case that all graphs w.r.t. parOgState have been transmitted.
            if (!parOgState.graphsOriginallyEmpty) {
                return Optional.of(PrecisionAdjustmentResult.create(state, precision,
                        PrecisionAdjustmentResult.Action.CONTINUE));
            }
        }
        parOgState.graphsOriginallyEmpty = false;

        // 3. else, there are still some graphs w.r.t. in parOgState, we need to deal with them.
        // 1) firstly, extract shared vars' info from the cfaEdge.
        List<ObsGraph> parGraphs = ogsBiMap.get(parOgState);
        // check whether enter/exit/in block area.
        // checkAndSetBlockStatus(parOGPORState.getBlockStatus(), chOGPORState, cfaEdge);
        List<SharedEvent> sharedEvents = sharedVarsExtractor.extractSharedVarsInfo(cfaEdge);
//        updateLastAccessTable(sharedEvents, parOgState, chOgState); // update the last access table
        if (sharedEvents.isEmpty()) {
            // no shared vars access, i.e., cfaEdge just access local vars.
            // in this case, we associate all graphs w.r.t. parOgState with chOgState directly.
            // and set the chOgState as delayed.
            // TODO: not sure for the reversePut method.
//            ogsBiMap.reversePut(parGraphs, parOgState, chOgState);
//            parOgState.setHasChildDelayed(true);
            List<ObsGraph> chGraphs = new ArrayList<>(parGraphs);
            parGraphs.clear();
            ogsBiMap.put(chOgState, chGraphs);
            chOgState.setNeedDelay(true); // need to delay, whenever the chGraphs is not null.
            return Optional.of(PrecisionAdjustmentResult.create(chOgState, precision,
                    PrecisionAdjustmentResult.Action.CONTINUE));
        }

        // 2) if the cfaEdge access global vars, then we generate a OGNode for it.
        OGNode newOGNode = genNewOGNode(sharedEvents, chOgState, parArgState);

        // 3) by using newOGNode, find the graphs that need to be associated with chOgState, and
        // disassociating them with parOgState.
        List<ObsGraph> chGraphs = genAndUpdateObsGraphs(parGraphs, parOgState, newOGNode);
        if (chGraphs.isEmpty()) {
            // after the update, no new graphs produced because of the conflict, so the exploration
            // stop here.
            return Optional.empty();
        }

        // else, we associate the chGraphs with chOgState
//        ogsBiMap.reversePut(chGraphs, parOgState, chOgState);
        ogsBiMap.put(chOgState, chGraphs);
        chOgState.setNeedDelay(true);

        // 3. when 'chGraphs' is not empty, we need to run all possible revisit processes, which
        // include: forward revisit (for R) and backward revisit (for W).
        Map<ARGState, List<ObsGraph>> revisitResults = revisit(chGraphs);

        // 4. according to the revisitResults, update the ogsBiMap.
        updateObsBiMapForRevisit(reachedSet, revisitResults);
        */

        return Optional.of(PrecisionAdjustmentResult.create(chOgState,
                precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }

    @Override
    public Optional<? extends AbstractState> strengthen(AbstractState pState, 
            Precision pPrecision, 
            Iterable<AbstractState> otherStates) 
            throws CPAException, InterruptedException {
        return PrecisionAdjustment.super.strengthen(pState, pPrecision, otherStates);
    }
}