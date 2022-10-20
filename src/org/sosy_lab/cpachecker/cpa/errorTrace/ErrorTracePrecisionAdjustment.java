package org.sosy_lab.cpachecker.cpa.errorTrace;

import static com.google.common.collect.Iterables.any;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

import java.util.ArrayList;
import java.util.Optional;

public class ErrorTracePrecisionAdjustment implements PrecisionAdjustment {
    @Override
    public Optional<PrecisionAdjustmentResult> prec(AbstractState state, Precision precision, UnmodifiableReachedSet states, Function<AbstractState, AbstractState> stateProjection, AbstractState fullState) throws CPAException, InterruptedException {

        assert fullState instanceof ARGState;
        ARGState argState = (ARGState) fullState;
        // if any state in the compositeState of fullState is 'ERRORState', try to print the error trace.
        if (any(argState.getWrappedStates(), AbstractStates::isTargetState)) {
            //
            // System.out.println("Printing error trace ...");
            ArrayList<ARGState> visitedStates = new ArrayList<>();
            visitedStates.add(argState);

            // in CFA, 'Function entry node' could represent the function.
            CFANode mainFuncEntryNode = GlobalInfo.getInstance().getCFAInfo().get().getCFA().getMainFunction();

            // form the current ARGState, backtrace to the main-function entry.
            ImmutableList<ARGState> curStatePars = ImmutableList.copyOf(argState.getParents());
            assert curStatePars.size() ==1;
            ARGState parState = curStatePars.get(0);
            CFAEdge curInEdge = parState.getEdgeToChild(argState);
            assert curInEdge != null;
            CFANode preNode = curInEdge.getPredecessor();

            while (!preNode.equals(mainFuncEntryNode)) {
                visitedStates.add(parState);
                argState = parState;
                // if current State has more than one parent, we just use one.
                for(ARGState par : argState.getParents()) {
                    if (visitedStates.contains(par)) {
                        continue;
                    } else {
                        parState = par;
                        break;
                    }
                }

                curInEdge = parState.getEdgeToChild(argState);
                preNode = curInEdge.getPredecessor();
            }
            visitedStates.add(parState);

            System.out.println("digraph ART {");
            // print the trace inversely.
            for (int i = visitedStates.size() - 1; i > 0; i--) {
                // System.out.println("s" + visitedStates.get(i).getStateId());
                // current dot Node.
                ARGState curS= visitedStates.get(i - 1),
                        parS= visitedStates.get(i);
                int curStateId = curS.getStateId(),
                        parStateId = parS.getStateId();

                CFAEdge edge = parS.getEdgeToChild(curS);
                Preconditions.checkNotNull(edge,
                        "can't find the edge when trying to build the trace.");
                System.out.println(
                        ""
                        + parStateId
                        + " [fillcolor=\"cornflowerblue\" shape=\"box\" label=\"s"
                        + parStateId
                        + "\" id=\""
                        + parStateId
                        + "\"]"
                );
                // current dot Edge
                System.out.println(
                        ""
                        + parStateId
                        + " -> "
                        + curStateId
                        + " [label=\""
                        + edge
                        + "\" id=\""
                        + parStateId
                        + " -> "
                        + curStateId
                        + "\"]"
                );
            }
            System.out.println("}");
        }

        return Optional.of(PrecisionAdjustmentResult.create(state, precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
}
