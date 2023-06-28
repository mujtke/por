package org.sosy_lab.cpachecker.cpa.por.ogpor;

import org.sosy_lab.common.ShutdownNotifier;
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
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Options(prefix = "cpa.ogpor")
public class OGPORCPA extends AbstractCPA implements ConfigurableProgramAnalysis {

    private final Configuration config;
    private final CFA cfa;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

   @Option(secure = true,
           description = "With this option enabled, function calls that occur in the CFA are "
               + "followed. By disabling this option one can traverse a function without "
               + "following function calls (in this case FunctionSummaryEdges are used).")
    private boolean followFunctionCall = true;


    public static CPAFactory factory() { return AutomaticCPAFactory.forType(OGPORCPA.class); }

    @Override
    public PrecisionAdjustment getPrecisionAdjustment() {
        return new OGPORPrecisionAdjustment(logger);
    }

    public OGPORCPA(
        Configuration pConfig,
        CFA pCfa, LogManager pLogger,
        ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {
        super("sep", "sep",
            new OGPORTransferRelation(pConfig, pCfa, pLogger, pShutdownNotifier));
        config = pConfig;
        cfa = pCfa;
        logger = pLogger;
        shutdownNotifier = pShutdownNotifier;
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {

//        SingleThreadState initMainState = new SingleThreadState(node, 0);
//        Map<String, SingleThreadState> initMainThreadLoc = new HashMap<>();
        String mainFuncName = cfa.getMainFunction().getFunctionName();
//        initMainThreadLoc.put(mainFuncName, initMainState);
//        MultiThreadState initMultiState = new MultiThreadState(initMainThreadLoc, mainFuncName,
//                followFunctionCall);

//        Map<String, Triple<Integer, Integer, Integer>> initThreadStatus = new HashMap<>();
//        initThreadStatus.put(mainFuncName, Triple.of(-1, 0, 0));
        OGPORState initState = new OGPORState(0);
        initState.getThreads().put(mainFuncName, "N" + node.getNodeNumber());

        // initially, the first element of osgBiMap is set as 'initState.num <-> \empty'.
        GlobalInfo.getInstance().getOgInfo().getOGMap().put(initState.getNum(),
                new ArrayList<ObsGraph>());

        return initState;
    }

}