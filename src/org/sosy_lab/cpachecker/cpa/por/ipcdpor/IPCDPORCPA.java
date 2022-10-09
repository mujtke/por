package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.interfaces.*;

import java.util.Collection;

public class IPCDPORCPA extends AbstractCPA implements ConfigurableProgramAnalysis, StatisticsProvider {

    private final CFA cfa;
    private final Configuration config;


    @Option(
            secure = true,
            description = "With this option enabled, function calls that occur"
                    + " in the CFA are followed. By disabling this option one can traverse a function"
                    + " without following function calls (in this case FunctionSummaryEdges are used)")
    private boolean followFunctionCalls = true;


    @Option(
            secure = true,
            description = "Which type of state should be used to compute the constrained "
                    + "dependency at certain state?"
                    + "\n- bdd: BDDState (default)"
                    + "\n- predicate: PredicateAbstractState"
                    + "\nNOTICE: Corresponding CPA should be used!",
            values = {"BDD", "PREDICATE"},
            toUppercase = true)
    private String depComputationStateType = "BDD";


    public IPCDPORCPA(
            Configuration pConfig,
            LogManager pLogger,
            CFA pCfa
    ) throws InvalidConfigurationException {
        super("sep", "sep", new IPCDPORTransferRelation(pConfig, pLogger, pCfa));

        pConfig.inject(this);

        cfa = pCfa;
        config = pConfig;
    }
    @Override
    public PrecisionAdjustment getPrecisionAdjustment() {
        return super.getPrecisionAdjustment();
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {
        return IPCDPORState.getInitialInstance(node, cfa.getMainFunction().getFunctionName(),
                followFunctionCalls);
    }

    @Override
    public void collectStatistics(Collection<Statistics> statsCollection) {

    }
}
