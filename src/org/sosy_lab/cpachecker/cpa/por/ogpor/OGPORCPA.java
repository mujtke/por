package org.sosy_lab.cpachecker.cpa.por.ogpor;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;

import java.util.HashMap;
import java.util.Map;

@Options(prefix = "cpa.ogpor")
public class OGPORCPA extends AbstractCPA implements ConfigurableProgramAnalysis {

    private final Configuration config;
    private final CFA cfa;

   @Option(
           secure = true,
           description = "With this option enabled, function calls that occur in the CFA are "
               + "followed. By disabling this option one can traverse a function without "
               + "following function calls (in this case FunctionSummaryEdges are used)."
   )
    private boolean followFunctionCall = true;


    public static CPAFactory factory() {return AutomaticCPAFactory.forType(OGPORCPA.class); }

    public OGPORCPA(
        Configuration pConfig,
        CFA pCfa) throws InvalidConfigurationException {
        super("sep", "sep",
            new OGPORTransferRelation(pConfig, pCfa));
        config = pConfig;
        cfa = pCfa;
    }
    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {

        SingleThreadState initMainState = new SingleThreadState(node, 0);
        Map<String, SingleThreadState> initMainThreadLoc = new HashMap<>();
        String mainFuncName = cfa.getMainFunction().getFunctionName();
        initMainThreadLoc.put(mainFuncName, initMainState);
        MultiThreadState initMultiState = new MultiThreadState(initMainThreadLoc, mainFuncName,
                followFunctionCall);

        Map<String, Triple<Integer, Integer, Integer>> initThreadStatus = new HashMap<>();
        initThreadStatus.put(mainFuncName, Triple.of(-1, 0, 0));
        OGPORState initState = new OGPORState(initMultiState, initThreadStatus);

        return initState;
    }

}
