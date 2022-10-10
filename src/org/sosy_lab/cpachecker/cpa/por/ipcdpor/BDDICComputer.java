package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.cpa.por.pcdpor.AbstractICComputer;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;

public class BDDICComputer extends AbstractICComputer {

    private final PredicateManager predmgr;
    private final MachineModel machineModel;
    private final IPCDPORStatistics statistics;

    public BDDICComputer(CFA pCFA, PredicateManager pPredmgr, IPCDPORStatistics pStatistics) {
        // why need this assert?

        predmgr = pPredmgr;
        machineModel = pCFA.getMachineModel();
        statistics = pStatistics;
    }

    /**
     * computeDep: compute whether the constraint 'pIcs' holds at the BDD state 'pState'
     * @param pICs constrains
     * @param pState the BDD state in which whether 'pIcs' holds is judged.
     * @return 'true' if ..., 'false' if...
     */
    @Override
    public boolean computeDep(CondDepConstraints pICs, AbstractState pState) {
        return false;
    }
}
