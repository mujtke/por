package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.pcdpor.AbstractICComputer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

import java.util.*;

public class IPCDPORPrecisionAdjustment implements PrecisionAdjustment {

    private final ConditionalDepGraph condDepGraph;
    // for POR of non-global edges. E.g. a: local_1 = 1; b: local_2 = 2;
    // both a & b are enabled at state s0, then just one sequence 'a.b' or
    // 'b.a' is explored instead both.
    private final Map<Integer, Integer> nExploredChildCache;
    private final AbstractICComputer icComputer;
    // TODO: we need BDDCPA to calculate the independence at states in future
    private final BDDCPA bddCPA;

    private IPCDPORStatistics stat;

    // the Filter that find the global access edge's successors of
    // the given parent state.
    private static final Function<ARGState, Set<ARGState>> globalAccessEdgeFilter =
            (s) -> from(s.getChildren()).filter(
                    cs -> AbstractStates.extractStateByType(cs, IPCDPORState.class)
                            .getTransferInEdgeType().equals(EdgeType.GVAEdge)
            ).toSet();

    private static final Function<ARGState, Set<ARGState>> normalEdgeFilter =
            (s) -> from(s.getChildren()).filter(
                    cs -> AbstractStates.extractStateByType(cs, IPCDPORState.class)
                            .getTransferInEdgeType().equals(EdgeType.NEdge)
            ).toSet();

    private static final Function<ARGState, Set<ARGState>> normalAssumeEdgeFilter =
            (s) -> from(s.getChildren()).filter(
                    cs -> AbstractStates.extractStateByType(cs, IPCDPORState.class)
                            .getTransferInEdgeType().equals(EdgeType.NAEdge)
            ).toSet();

    public IPCDPORPrecisionAdjustment (
            ConditionalDepGraph pCondDepGraph,
            AbstractICComputer pIcComputer,
            IPCDPORStatistics pStatistics) throws InvalidConfigurationException {
        condDepGraph = checkNotNull(pCondDepGraph, "No condDepGraph " +
                "found, please enable the option: utils.edgeinfo.buildDepGraph!");
        nExploredChildCache = new HashMap<>();
        icComputer = pIcComputer;
        GlobalInfo globalInfo = GlobalInfo.getInstance();
        Optional<ConfigurableProgramAnalysis> cpas = globalInfo.getCPA();
        assert cpas.isPresent() : "error when get cpa, it shouldn't be empty";
        bddCPA = retrieveCPA(cpas.get(), BDDCPA.class);
        stat = pStatistics;
    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigurableProgramAnalysis> T
    retrieveCPA (final ConfigurableProgramAnalysis pCPA, Class<T> pClass)
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
        throw new InvalidConfigurationException("could not find CPA " + pClass + " from " + pCPA);
    }


    @Override
    public Optional<PrecisionAdjustmentResult> prec(AbstractState state, Precision precision, UnmodifiableReachedSet states, Function<AbstractState, AbstractState> stateProjection, AbstractState fullState) throws CPAException, InterruptedException {

        // we need to know the parent state of current state.
        if (fullState instanceof ARGState) {
            ARGState argCurState = (ARGState) fullState;
            // So, here we think curState just have one ParState ?
            ARGState argParState = argCurState.getParents().iterator().next();

            // clean up the caches for some refinement based algorithms.
            // like CEGAR ?
            if (argParState.getStateId() == 0) {
                nExploredChildCache.clear();
            }

            // get all kinds of successors of the argParState.
            Set<ARGState> gvaSuccessors = globalAccessEdgeFilter.apply(argParState);
            Set<ARGState> naSuccessors = normalAssumeEdgeFilter.apply(argParState);
            Set<ARGState> nSuccessors = normalEdgeFilter.apply(argParState);

            IPCDPORState ipcdporCurState = AbstractStates.extractStateByType(argCurState, IPCDPORState.class),
                    ipcdporParState = AbstractStates.extractStateByType(argParState, IPCDPORState.class);
            int argParStateId = argParState.getStateId();

            // get the transfer-in edge of ipcdporCurState and its predecessor node number.
            CFAEdge ipcdporCurStateInEdge = ipcdporCurState.getTransferInEdge();
            int curStateInEdgePreNode = ipcdporCurStateInEdge.getPredecessor().getNodeNumber();

            // explore the 'normal' successors. I.e. those not global access or assume edge.
            if (!nSuccessors.isEmpty()) {
                if (nSuccessors.contains(argCurState) && !nExploredChildCache.containsKey(argParStateId)) {
                    // it's the first time we explore the normal successor of argParState.
                    // nSuccessor.contains(argCurState) means the curState is a normal successor of argParState.
                    // And curState should be the only normal successor ? Other normal successors should be pruned.
                    nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
                    // update the sleep set.
                    ipcdporCurState.setSleepSet(ipcdporParState.getSleepSet());

                    // in this case, curState shouldn't be pruned.
                    return Optional.of(
                            PrecisionAdjustmentResult.create(state, precision, PrecisionAdjustmentResult.Action.CONTINUE)
                    );
                } else {
                    // this normal successor could be pruned.
                    // i.e. we don't need to explore other normal successors.
                    stat.avoidExplorationTimes.inc();
                    return Optional.empty();
                }
            }
            assert nSuccessors.isEmpty() : "nSuccessors should be empty now.";

            // explore the 'assume normal' successors
            if (!naSuccessors.isEmpty()) {
                if (naSuccessors.contains(argCurState)
                && (!nExploredChildCache.containsKey(argParStateId)
                || nExploredChildCache.get(argParStateId).equals(curStateInEdgePreNode))) {
                    // two cases: 1. it's the first time we explore the normal successor of argParState.
                    // 2. we have explored one normal successor, and we still need to explore the other
                    // normal successor of assume edge. (For condition branch, one statement corresponds
                    // two assume edges.
                    nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
                    // update sleep set.
                    ipcdporCurState.setSleepSet(ipcdporParState.getSleepSet());

                    return Optional.of(
                            PrecisionAdjustmentResult.create(state, precision, PrecisionAdjustmentResult.Action.CONTINUE)
                    );
                } else {
                    //
                    stat.avoidExplorationTimes.inc();
                    return Optional.empty();
                }
            }
            assert naSuccessors.isEmpty() : "naSuccessors should be empty now.";

            // explore the 'global access' successors.
            if (!gvaSuccessors.isEmpty()) {
                // firstly, we need to update the sleep sets of all gvaSuccessors.
                if (!ipcdporParState.isUpdated()) {
                    // if just one gvaEdge, then ... (don't need to compute the dependency).
                    if (gvaSuccessors.size() > 1) {
                        ImmutableList<IPCDPORState> updatedGVASuccessors =
                                from(gvaSuccessors)
                                        .transform(s -> AbstractStates.extractStateByType(s, IPCDPORState.class))
                                        .toList();

                        // obtain parent computation state to determine the dependency of successor transitions.
                        AbstractState parComputeState = null;
                        if (icComputer instanceof BDDICComputer) {
                            parComputeState = AbstractStates.extractStateByType(argParState, BDDState.class);
                        } else if (icComputer instanceof PredicateICComputer) {
                            parComputeState = argParState;
                        } else {
                            throw new CPAException("Unsupported ICComputer: " + icComputer.getClass().toString());
                        }

                        // dependency computation.
                        // i.e. whether any two edges depart form parState are dependent at parState.
                        for (int i = 0; i < updatedGVASuccessors.size() - 1; i++) {
                            // get A state, the in-edge of A state and the 'thread id' of the in-edge.
                            IPCDPORState ipcdporAState = updatedGVASuccessors.get(i);
                            CFAEdge ipcdporAStateInEdge = ipcdporAState.getTransferInEdge();
                            int ipcdporAStateInEdgeThreadId = ipcdporAState.getTransferInEdgeThreadId();

                            // flag for judge whether A-InEdge could be an isolated transition.
                            boolean AStateInEdgeMaybeIsolated = true;

                            for (int j = i + 1; j < updatedGVASuccessors.size(); j++) {
                                IPCDPORState ipcdporBState = updatedGVASuccessors.get(j);
                                CFAEdge ipcdporBStateInEdge = ipcdporBState.getTransferInEdge();

                                // determine whether the transfer-info of A-state is independent with the
                                // transfer-info of B-state.
                                if (canSkip(ipcdporAStateInEdge, ipcdporBStateInEdge, parComputeState)) {
                                    // add the A-InEdge to the sleep set of B-State.
                                    // i.e. A-InEdge's exploration can be avoided.
                                    ipcdporBState.sleepSetAdd(Pair.of(ipcdporAStateInEdgeThreadId, ipcdporAStateInEdge.hashCode()));
                                } else {
                                    // if two edges are dependent, then A-InEdge can't be the isolated transition.
                                    AStateInEdgeMaybeIsolated = false;
                                }
                            }

                            if (AStateInEdgeMaybeIsolated) {
                                // A-InEdge maybe an isolated transition.
                                // if just two gvaSuccessors, pcdpor can guarantee the optimality.
                                if (updatedGVASuccessors.size() <= 2) {
                                    continue;
                                }
                                ImmutableList<IPCDPORState> rmAGVASuccessors =
                                        from(updatedGVASuccessors).filter(s -> !s.equals(ipcdporAState)).toList();
                                ImmutableList<CFAEdge> rmAGVATransferInEdges =
                                        from(rmAGVASuccessors).transform(s -> s.getTransferInEdge()).toList();

                                // calculate whether A-InEdge is isolated at parComputeState.
                                boolean AStateInEdgeIsolated = isIsolated(ipcdporAStateInEdge, rmAGVATransferInEdges, parComputeState);

                                if (AStateInEdgeIsolated) {
                                    // if isolated, add the corresponding edges to the corresponding ipcdporState's sleep set.
                                    // TODO: here somme edge are added twice
                                    for (IPCDPORState ipcdporState : rmAGVASuccessors) {
                                        ImmutableSet<Pair<Integer, Integer>> edgesAddToSleepSet =
                                                // other edge should be added to sleep set of the current 'ipcdporState'
                                                from(rmAGVASuccessors).filter(s -> !s.equals(ipcdporState))
                                                        .transform(s -> {
                                                            int edgeThreadId = s.getTransferInEdgeThreadId();
                                                            CFAEdge edge = s.getTransferInEdge();
                                                            return Pair.of(edgeThreadId, edge.hashCode());
                                                        }).toSet();
                                       // add to sleep set.
                                       edgesAddToSleepSet.forEach(pair -> ipcdporState.sleepSetAdd(pair));
                                    }
                                } else {
                                    // if not ...
                                }
                            } else {
                                // A-InEdge can't be an isolated transition.

                            }
                        }
                    }
                    // after updating, set as updated.
                    ipcdporParState.setAsUpdated();
                }

                // next, check whether current transfer-in edge is in the sleep set of the parState.
                int curTransferInEdgeThreadId = ipcdporCurState.getTransferInEdgeThreadId();
                // an element in sleep set is a pair: <thread-id, edge_hashCode()>
                Pair<Integer, Integer> curTransferInfo = Pair.of(
                        curTransferInEdgeThreadId,
                        ipcdporCurStateInEdge.hashCode()
                );
                if (ipcdporParState.sleepSetContains(curTransferInfo)) {
                    // curTransferInEdge \in the sleep set of parState, so we don't need to explore this edge.
                    stat.realRedundantTimes.inc();
                    stat.avoidExplorationTimes.inc();
                    return Optional.empty();
                } else {
                    // we need to explore this edge.
                    return Optional.of(
                            PrecisionAdjustmentResult.create(state, precision,
                                    PrecisionAdjustmentResult.Action.CONTINUE));
                }
            }
        } else {
            throw new AssertionError("IPCDPOR needs use the information of the current state's parent ARGState");
        }

        return Optional.of(
                PrecisionAdjustmentResult.create(state, precision, PrecisionAdjustmentResult.Action.CONTINUE)
        );
    }

    private boolean canSkip(CFAEdge pCheckEdge, CFAEdge pCurEdge, AbstractState pComputeState) {

        stat.checkSkipTimes.inc();
        DGNode checkNode = condDepGraph.getDGNode(pCheckEdge.hashCode()),
                curNode = condDepGraph.getDGNode(pCurEdge.hashCode());

        // we can't determine the dependency of thread create edge.
        boolean containThreadCreateEdge =
                (isThreadCreateEdge(pCheckEdge) || isThreadCreateEdge(pCurEdge));

        // compute the conditional dependency of the two nodes.
        // amounts to get the dependency between 'pCheckEdge' and 'pCurEdge'.
        CondDepConstraints ics = (CondDepConstraints) condDepGraph.dep(checkNode, curNode);

        if (ics == null) {
            // they are unconditionally independent.
            // TODO: loop start points should be processed carefully.
            if (!containThreadCreateEdge
            && !pCheckEdge.getSuccessor().isLoopStart()
            && !pCurEdge.getSuccessor().isLoopStart()) {

                stat.checkSkipUnIndepTimes.inc();
                return true;
            } else {
                stat.checkSkipFailedTimes.inc();
                return false;
            }
        } else {
            // case 1: unconditionally dependent, we can't skip.
            if (ics.isUnCondDep()) {
                stat.checkSkipUnDepTimes.inc();
                return false;
            } else {
                // case 2: conditionally independent, we need to use constraints to check whether
                // they are really independent at the given computeState.
                boolean isCondDep = icComputer == null ? true : icComputer.computeDep(ics, pComputeState);

                // they are conditionally independent at the given state.
                if (!isCondDep) {
                    // same, need to process loop start point carefully.
                    if (!containThreadCreateEdge
                    && !pCheckEdge.getSuccessor().isLoopStart()
                    && !pCurEdge.getSuccessor().isLoopStart()) {

                        stat.checkSkipCondIndepTimes.inc();
                        return true;
                    } else {
                        stat.checkSkipFailedTimes.inc();
                        return false;
                    }
                } else {
                    // is conditionally dependent.
                    stat.checkSkipCondDepTimes.inc();
                    return false;
                }
            }
        }
    }

    public boolean isThreadCreateEdge(final CFAEdge pEdge) {
        switch (pEdge.getEdgeType()) {
            case StatementEdge: {
                AStatement statement = ((AStatementEdge) pEdge).getStatement();
                if (statement instanceof AFunctionCall) {
                    AExpression functionNameExp = ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
                    if (functionNameExp instanceof AIdExpression) {
                        return ((AIdExpression) functionNameExp).getName().contains("pthread_create");
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    /**
     *
     * @param pCheckEdge
     * @param pEdges
     * @param pComputeState
     * @return
     * @throws CPATransferException
     * @throws InterruptedException
     */
    public boolean isIsolated(final CFAEdge pCheckEdge, ImmutableList<CFAEdge> pEdges, AbstractState pComputeState) throws CPATransferException, InterruptedException {

        assert pEdges.size() >= 1 : "When judge isolation the size of 'pEdges' must be >= 1";
        if (pComputeState instanceof BDDState) {
            BDDState bddState = (BDDState) pComputeState;

            // recursive bound: pEdges.size() == 1
            if (pEdges.size() == 1) {
                return canSkip(pCheckEdge, pEdges.get(0), bddState);
            } else {
                // recursive compute the isolation.
                boolean result = true;
                // for every edge p in 'pEdges', calculate whether pCheckEdge and p are independent
                // at the successor of p.
                for (CFAEdge p : pEdges) {
                    //
                    ArrayList<BDDState> bddSuccessors = (ArrayList<BDDState>) bddCPA
                            .getTransferRelation()
                            .getAbstractSuccessorsForEdge(pComputeState, null, p);

                    assert bddSuccessors.size() <= 1;

                    if (bddSuccessors.size() == 0) {
                        // if the successor of p is unreachable, return false, i.e. not isolated.
                        result = false;
                        break;
                    } else {
                        BDDState computeState = bddSuccessors.get(0);
                        ImmutableList<CFAEdge> rmPFromEdges =
                                from(pEdges).filter(s -> !s.equals(p)).toList();
                        if(!isIsolated(pCheckEdge, rmPFromEdges, computeState)) {
                            // if 'pCheckEdge' and any edge in 'rmPFromEdges' are not independent
                            // at the successor of p.
                            result = false;
                            break;
                        } else {
                            continue;
                        }
                    }
                }
                return result;
            }
        } else {
            // TODO: when pComputeState is not BDD state, return false conservatively.
            return false;
        }
    }
}
