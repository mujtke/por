package org.sosy_lab.cpachecker.cpa.por.ogpor;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.DummyCFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;

import java.util.ArrayList;
import java.util.Collections;

@Options(prefix = "cpa.ogpor")
public class OGPORCPA extends AbstractCPA implements ConfigurableProgramAnalysis {

    private final Configuration config;
    private final CFA cfa;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

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
        config.inject(this);
        cfa = pCfa;
        logger = pLogger;
        shutdownNotifier = pShutdownNotifier;
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition)
            throws InterruptedException {

        String mainFuncName = cfa.getMainFunction().getFunctionName();
        OGPORState initState = new OGPORState(0, new DummyCFAEdge(null, null));
        initState.setSid(0);
        OGPORTransferRelation transferRelation = (OGPORTransferRelation) getTransferRelation();

        initState.getThreads().put(mainFuncName, "N" + node.getNodeNumber());
        initState.setCfa(cfa);
        initState.setLoopInfo();
        initState.setLogger(logger);
        initState.setEdgeVarMap();
        initState.setAtomicBegins(transferRelation.getAtomicBegins());
        initState.setAtomicEnds(transferRelation.getAtomicEnds());
        initState.setLockBegins(transferRelation.getLockBegins());
        initState.setLockEnds(transferRelation.getLockEnds());
        initState.extractPatterns();

        // initially, the first element of OGMap is set to be 'initState.sid <-> \empty'.
        GlobalInfo.getInstance().getOgInfo().getOGMap().put(initState.getSid(),
                new ArrayList<>(Collections.singleton(new ObsGraph())));

        return initState;
    }
}