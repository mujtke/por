package org.sosy_lab.cpachecker.cpa.errorTrace;

import static com.google.common.collect.Iterables.any;
import com.google.common.base.Function;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;

import java.util.Optional;

public class ErrorTracePrecisionAdjustment implements PrecisionAdjustment {
    @Override
    public Optional<PrecisionAdjustmentResult> prec(AbstractState state, Precision precision, UnmodifiableReachedSet states, Function<AbstractState, AbstractState> stateProjection, AbstractState fullState) throws CPAException, InterruptedException {

        assert fullState instanceof ARGState;
        ARGState argState = (ARGState) fullState;
        // if any state in the compositeState of fullState is 'ERRORState', try to print the error trace.
        if (any(argState.getWrappedStates(), AbstractStates::isTargetState)) {
            //
            System.out.println("Printing error trace ...");
        }

        return Optional.of(PrecisionAdjustmentResult.create(state, precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
}
