
package org.sosy_lab.cpachecker.cpa.por.ogpor;

import com.google.common.base.Function;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;

import java.util.*;

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

        // Update chOgState's sid.
        assert state instanceof OGPORState && fullState instanceof ARGState;
        OGPORState chOgState = (OGPORState) state;
        chOgState.setSid(((ARGState) fullState).getStateId());

        // Handle the early termination of the main thread.
        assert ((ARGState) fullState).getParents().size() == 1;
        ARGState parARGState = ((ARGState) fullState).getParents().iterator().next();
        OGPORState parOgState = AbstractStates.extractStateByType(parARGState, OGPORState.class);
        assert parOgState != null;
        if (parOgState.willExit()) {
            // return Optional.empty();
        }

        return Optional.of(PrecisionAdjustmentResult.create(state,
                precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
}