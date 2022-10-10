package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.por.pcdpor.AbstractICComputer;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;

public class PredicateICComputer extends AbstractICComputer {

    IPCDPORStatistics statistics;
    PredicateCPA predicateCPA;

    public PredicateICComputer(PredicateCPA pPredicateCPA, IPCDPORStatistics pStatistics) {
        predicateCPA = pPredicateCPA;
        statistics = pStatistics;
    }
    @Override
    public boolean computeDep(CondDepConstraints pICs, AbstractState pState) {
        return false;
    }
}
