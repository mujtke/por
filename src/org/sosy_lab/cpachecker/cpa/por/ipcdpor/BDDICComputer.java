package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDVectorCExpressionVisitor;
import org.sosy_lab.cpachecker.cpa.bdd.BitvectorManager;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.cpa.por.pcdpor.AbstractICComputer;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.java_smt.api.SolverException;

public class BDDICComputer extends AbstractICComputer {

    private final PredicateManager predmgr;
    private final MachineModel machineModel;
    private final IPCDPORStatistics statistics;

    public BDDICComputer(CFA pCFA, PredicateManager pPredmgr, IPCDPORStatistics pStatistics) {
        // why need this assert?
        // assert pCFA.getVarClassification().isEmpty();

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

        if (pState instanceof BDDState) {
            BDDState bddState = (BDDState) pState;

            statistics.ipcdporComputeDepTimer.start();
            statistics.depComputeTimes.inc();
            assert(!pICs.isUnCondDep()) : "When compute Dep, the 'pICs'" + pICs + "shouldn't" +
                    " be unconditionally dependent.";

            //
            Pair<CExpression, String> ic = pICs.getConstraints().iterator().next();

            final Region[] expRegion = computeExpRegion(ic.getFirst(), bddState.getBvmgr(), predmgr);

            if (expRegion != null) {
                Region evaluated = bddState.getBvmgr().makeOr(expRegion);

                NamedRegionManager rmgr = bddState.getManager();
                try {
                    if (rmgr.entails(bddState.getRegion(), evaluated)) {
                        //
                        statistics.ipcdporComputeDepTimer.stop();
                        statistics.depConstraintsEntailTimes.inc();
                        return false;
                    } else {
                        //
                        statistics.ipcdporComputeDepTimer.stop();
                        statistics.depConstraintsNotEntailTimes.inc();
                        return true;
                    }
                } catch (SolverException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                // conservative, return true.
                statistics.ipcdporComputeDepTimer.stop();
                statistics.depConstraintsOtherCaseTimes.inc();
                return true;
            } else {
                // if expRegion == null
                statistics.ipcdporComputeDepTimer.stop();
                statistics.depConstraintsOtherCaseTimes.inc();
                return true;
            }
        } else {
            return true;
        }
    }

    private Region[] computeExpRegion(CExpression pExp, BitvectorManager pBvMgr, PredicateManager pPredMgr) {
        Region[] value = null;
        try {
            value =
                    pExp.accept(new BDDVectorCExpressionVisitor(pPredMgr, null, pBvMgr, machineModel, null));
        } catch (UnsupportedCodeException e) {
            e.printStackTrace();
            return null;
        }
        return value;
    }
}
