package org.sosy_lab.cpachecker.cpa.errorTrace;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import java.util.Collection;
import java.util.Collections;

public class ErrorTraceTransferRelation extends SingleEdgeTransferRelation {

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state, Precision precision, CFAEdge cfaEdge) throws CPATransferException, InterruptedException {

        return Collections.singleton(state);
    }

}
