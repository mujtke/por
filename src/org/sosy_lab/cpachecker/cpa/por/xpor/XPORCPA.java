package org.sosy_lab.cpachecker.cpa.por.xpor;

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
public class XPORCPA extends AbstractCPA {


    private final CFA cfa;
    private final Configuration config;
    private final LogManager logger;

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

    @Override
    public TransferRelation getTransferRelation() {
        // by call XPORTransferRelation(...), set the icComputer.
        try {
            return new XPORTransferRelation(config, logger, cfa);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        // TODO: if exception occurs, return null?
        return null;
    }

    public XPORCPA(
            Configuration pConfig,
            LogManager pLogger,
            CFA pCfa
    ) throws InvalidConfigurationException {
//        super("sep", "sep", new XPORTransferRelation(pConfig, pLogger, pCfa));
        super("sep", "sep", new XPORTransferRelation());
        pConfig.inject(this);

        cfa = pCfa;
        config = pConfig;
        logger = pLogger;
    }
}
