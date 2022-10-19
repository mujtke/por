package org.sosy_lab.cpachecker.cpa.errorTrace;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.*;

public class ErrorTraceCPA extends AbstractCPA implements ConfigurableProgramAnalysis {

    public ErrorTraceCPA() {
        super("sep", "sep", new ErrorTraceTransferRelation());
    }

    public static CPAFactory factory() {
        return AutomaticCPAFactory.forType(ErrorTraceCPA.class);
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {
        return new ErrorTraceState();
    }

    @Override
    public PrecisionAdjustment getPrecisionAdjustment() {
        return new ErrorTracePrecisionAdjustment();
    }
}
