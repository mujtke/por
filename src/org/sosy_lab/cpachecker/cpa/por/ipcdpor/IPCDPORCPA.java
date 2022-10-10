package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;

import java.util.Collection;
import java.util.Optional;

@Options(prefix = "cpa.por.ipcdpor")
public class IPCDPORCPA extends AbstractCPA implements ConfigurableProgramAnalysis, StatisticsProvider {

    private final CFA cfa;
    private final Configuration config;
    private final IPCDPORStatistics statistics;


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
        statistics = new IPCDPORStatistics();
    }
    @Override
    public PrecisionAdjustment getPrecisionAdjustment() {
        try {
            // According to 'depComputationStateType' determinate the type of ICComputer
            // i.e. BDDIcComputer or PredicateICComputer.
            Optional<ConfigurableProgramAnalysis> cpas = GlobalInfo.getInstance().getCPA();
            if (cpas.isPresent()) {
               if (depComputationStateType.equals("BDD")) {

                   BDDCPA bddCPA = retrieveCPA(cpas.get(), BDDCPA.class);
                   NamedRegionManager namedRegionManager = bddCPA.getManager();

                   return new IPCDPORPrecisionAdjustment(
                           GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph(),
                           new BDDICComputer(cfa, new PredicateManager(config, namedRegionManager, cfa), statistics)
                   );
               } else if (depComputationStateType.equals("PREDICATE")) {

                   PredicateCPA predicateCPA = retrieveCPA(cpas.get(), PredicateCPA.class);

                   return new IPCDPORPrecisionAdjustment(
                           GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph(),
                           new PredicateICComputer(predicateCPA, statistics)
                   );
               }
            } else {
                throw new InvalidConfigurationException(
                        "Invalid Configuration: not support for the type of constrained dependency computation: " +
                                depComputationStateType
                                + ".");
            }
        } catch (InvalidConfigurationException e) {
            //
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {
        return IPCDPORState.getInitialInstance(node, cfa.getMainFunction().getFunctionName(),
                followFunctionCalls);
    }

    @Override
    public void collectStatistics(Collection<Statistics> statsCollection) {

    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigurableProgramAnalysis> T
    retrieveCPA(final ConfigurableProgramAnalysis pCPA, Class<T> pClass)
    throws InvalidConfigurationException {
        if (pCPA.getClass().equals(pClass)) {
            return (T) pCPA;
        } else if (pCPA instanceof WrapperCPA) {
            WrapperCPA wrapperCPA = (WrapperCPA) pCPA;
            T result = wrapperCPA.retrieveWrappedCpa(pClass);

            if (result != null) {
                return result;
            }
        }
        throw new InvalidConfigurationException("could not find the CPA " + pClass + " from " + pCPA);
    }

}
