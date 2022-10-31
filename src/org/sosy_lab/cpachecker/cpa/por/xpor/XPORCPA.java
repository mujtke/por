package org.sosy_lab.cpachecker.cpa.por.xpor;

import com.google.common.base.Optional;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.*;

@Options(prefix = "cpa.por.xpor")
public class XPORCPA extends AbstractCPA implements ConfigurableProgramAnalysis {


    private final CFA cfa;
    private final Configuration config;

    @Option(
            secure = true,
            description = "With this option enabled, function calls that occur"
                    + " in the CFA are followed. By disabling this option one can traverse a function"
                    + " without following function calls (in this case FunctionSummaryEdges are used)")
    private boolean followFunctionCalls = true;

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {
        return XPORState.getInitialInstance(node, cfa.getMainFunction().getFunctionName(), followFunctionCalls);
    }

    public static CPAFactory factory() { return AutomaticCPAFactory.forType(XPORCPA.class); }

    public XPORCPA(
            Configuration pConfig,
            LogManager pLogger,
            CFA pCfa
    ) throws InvalidConfigurationException {
        super("sep", "sep", new XPORTransferRelation(pConfig, pLogger, pCfa));
        pConfig.inject(this);

        cfa = pCfa;
        config = pConfig;
    }
}
