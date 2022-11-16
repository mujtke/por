package org.sosy_lab.cpachecker.cpa.por.xpor;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.*;
import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.pcdpor.AbstractICComputer;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;

import java.util.*;

@Options(prefix = "cpa.por.xpor")
public class XPORTransferRelation extends SingleEdgeTransferRelation {

    @Option(secure = true,
    description = "With this option enabled, thread creation edge will be regarded as a normal" +
            "edge, which may reduces the redundant interleavings")
    private boolean regardThreadCreationAsNormalEdge = false;

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

    private final LocationsCPA locationsCPA;
    private final ConditionalDepGraph condDepGraph;
    private final AbstractICComputer icComputer;
    private final XPORStatistics stats;

    // record the exit node of mainFunction for judgement in handling 'nEdges'
    private final CFANode mainFunctionExitNode;

    // the filter to find the global access edge in given edges.
    private final Function<Iterable<CFAEdge>, List<CFAEdge>> globalAccessEdgeFilter =
            (allEdges) -> from(allEdges).filter(
                    edge -> determineEdgeType(edge).equals(EdgeType.GVAEdge)
            ).toList();

    private final Function<Iterable<CFAEdge>, List<CFAEdge>> normalEdgeFilter =
            (allEdges) -> from(allEdges).filter(
                    edge -> determineEdgeType(edge).equals(EdgeType.NEdge)
            ).toList();

    private final Function<Iterable<CFAEdge>, List<CFAEdge>> normalAssumeEdgeFilter =
            (allEdges) -> from(allEdges).filter(
                    edge -> determineEdgeType(edge).equals(EdgeType.NAEdge)
            ).toList();

    public XPORTransferRelation(Configuration config, LogManager logger, CFA cfa, XPORStatistics pStats)
        throws InvalidConfigurationException {
        config.inject(this);
        locationsCPA = LocationsCPA.create(config, logger, cfa);
        condDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
        stats = pStats;
        mainFunctionExitNode = GlobalInfo.getInstance().getCFAInfo().get().getCFA().getMainFunction().getExitNode();

        // According to 'depComputationStateType' determine the type of ICComputer
        // i.e. BDDICComputer or PredicateICComputer.
        Optional<ConfigurableProgramAnalysis> cpas = GlobalInfo.getInstance().getCPA();
        if(cpas.isPresent()) {
            if(depComputationStateType.equals("BDD")) {
                BDDCPA bddCPA = retrieveCPA(cpas.get(), BDDCPA.class);
                NamedRegionManager namedRegionManager = bddCPA.getManager();
                icComputer = new BDDICComputer(cfa, new PredicateManager(config, namedRegionManager, cfa), stats);
            } else if(depComputationStateType.equals("PREDICATE")) {
                PredicateCPA predicateCPA = retrieveCPA(cpas.get(), PredicateCPA.class);
                icComputer = new PredicateICComputer(predicateCPA, stats);
            }else {
                throw new InvalidConfigurationException(
                        "Invalid Configuration: not supported type for constraint dependency computation"
                                + depComputationStateType
                                +".");
            }
        } else {
            throw new InvalidConfigurationException("Try to get cpas failed!");
        }
    }

    public XPORTransferRelation() {
        locationsCPA = null;
        condDepGraph = null;
        icComputer = null;
        stats = null;
        mainFunctionExitNode = null;
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state, Precision precision, CFAEdge cfaEdge) throws CPATransferException, InterruptedException {

        XPORState curState = (XPORState) state;

        // TODO: we compute the new locs iff current edge is not in the sleep set of 'state'
        Map<Integer, Pair<String, Integer>> oldThreadIdNumbers = curState.getThreadIdNumbers();
        // if curEdge in curState's sleepSet or isolatedSleepSet, we can return empty.
        // in curState's sleepSet means that curEdge is in sleepSet as a single element rather like 'p.q'.
        int curThreadCounter = oldThreadIdNumbers.get(cfaEdge.hashCode()).getSecondNotNull();
        Pair<Integer, Integer> curEdge = Pair.of(curThreadCounter, cfaEdge.hashCode());
        if (curState.sleepSetContain(curEdge)) {
            stats.avoidExplorationTimes.inc();
            stats.realRedundantTimes.inc();
            return ImmutableList.of();
        }
        if (curState.isolatedSleepSetContains(curEdge)) {
            stats.avoidExplorationTimes.inc();
            return ImmutableList.of();
        }

        // compute the new locations.
        // if current edge is 'pthread_create', then locationsTransferRelation will create the new thread.
        Collection<? extends AbstractState> newLocStates = locationsCPA.getTransferRelation()
                .getAbstractSuccessorsForEdge(curState.getThreadLocs(), precision, cfaEdge);
        assert newLocStates.size() <= 1 : "the number of newly gotten locationsStates must be less or equal one";

        if(newLocStates.isEmpty()) {
            return ImmutableList.of();
        }

        // get the new locs.
        LocationsState newLocs = (LocationsState) newLocStates.iterator().next();

        // update the thread ids and their number.
        Pair<Integer, Map<Integer, Pair<String, Integer>>> newThreadInfo =
                updateThreadIdNumber(curState.getThreadCounter(), oldThreadIdNumbers, newLocs);
        int newThreadCounter = newThreadInfo.getFirstNotNull();
        Map<Integer, Pair<String, Integer>> newThreadIdNumbers = newThreadInfo.getSecondNotNull();

        // else, updated the sleep set and return.
        // generate the successor with empty sleepSet and isolatedSleepSet.
        // TODO: delay the update to 'strengthen' method: sleepSet, isolatedSleepSet.
        XPORState successor = new XPORState(
                newThreadCounter,
                cfaEdge,
                newLocs,
                newThreadIdNumbers,
                new HashSet<>(),
                new HashSet<>()
        );
        // TODO: if curEdge is 'gvaEdge' then updated the sleep set like:
        // update 'p.q' -> 'q' in curState's successor. Other single edge like 'q' are removed.
        if (determineEdgeType(cfaEdge).equals(EdgeType.GVAEdge)) {
            successor.updateSleepSet(curState.getSleepSet(), curEdge);
        } else {
            // else, successor's sleep set == curState's sleep set.
            // shallow copy have some problem.
            // successor.setSleepSet(curState.getSleepSet());
            // TODO: if we should keep the sleep set from parent. Maybe not.
            successor.getSleepSet().addAll(curState.getSleepSet());
        }

        return ImmutableList.of(successor);
    }

    Pair<Integer, Map<Integer, Pair<String, Integer>>> updateThreadIdNumber(
            int pOldThreadCounter,
            final Map<Integer, Pair<String, Integer>> pOldThreadIdNumbers,
            final LocationsState pNewLocs
    ) {
        stats.xporUpdateThreadIdNumTimer.start();
        // copy the threadIdNumber into the 'newThreadInfo' from 'pOldThreadIdNumber'.
        Map<String, Integer> oldThreadIdNumbers = new HashMap<>();
        pOldThreadIdNumbers.values().forEach(pair -> {
            oldThreadIdNumbers.put(pair.getFirstNotNull(), pair.getSecondNotNull());
        });
        Map<String, Integer> newThreadIdNumbers = new HashMap<>(oldThreadIdNumbers);

        // Part I: remove the threads which exited.
        // 1.get all thread-ids in new 'locationsState'
        Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
        // 2.get all thread-ids that doesn't exist in 'newThreadIds'
        ImmutableSet<String> tidsToRemoved =
                from(oldThreadIdNumbers.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
        // 3.according to 'tidsToRemoved', remove the corresponding pairs in 'newThreadIdNumbers'
        tidsToRemoved.forEach(t -> newThreadIdNumbers.remove(t));

        // Part II: add the thread id and number which created newly.
        ImmutableSet<String> tidsToAdded =
                from(newThreadIds).filter(t -> !newThreadIdNumbers.containsKey(t)).toSet();
        assert tidsToAdded.size() <= 1 : "the number of newly created thread must be less or equal one";
        // if nonempty, add the thread id and its number into the 'newThreadIdNumbers'.
        if(!tidsToAdded.isEmpty()) {
            newThreadIdNumbers.put(tidsToAdded.iterator().next(), ++pOldThreadCounter);
        }

        // finished the update of thread id and number.
        Map<Integer, Pair<String, Integer>> newThreadIdNumsWithEdge = new HashMap<>();
        // TODO: here we can get the info about sucEdges, so we don't have to delay the computation of 'newThreadIdNumbersWithEdges'
        //  to the 'strengthen' stage.
        Iterable<CFAEdge> sucEdges = pNewLocs.getOutgoingEdges();
        sucEdges.forEach(e -> {
            String activeThread = getActiveThread2(pNewLocs, e);
            newThreadIdNumsWithEdge.put(e.hashCode(), Pair.of(activeThread, newThreadIdNumbers.get(activeThread)));
        });

        stats.xporUpdateThreadIdNumTimer.stop();
        return Pair.of(pOldThreadCounter, newThreadIdNumsWithEdge);
    }

    private String getActiveThread2(LocationsState pNewLoc, CFAEdge pEdge) {
        Set<String> activeThreads = new HashSet<>();

        for (Map.Entry<String, SingleThreadState> entry : pNewLoc.getMultiThreadState().getThreadLocations().entrySet()) {
            if(entry.getValue().getLocation().equals(pEdge.getPredecessor())) {
                activeThreads.add(entry.getKey());
                break;
            }
        }

        assert activeThreads.size() <= 1 : "multiple active threads are not allowed: " + activeThreads;
        // then either the same function is called in different threads -> not supported.
        // (or CompositeCPA and ThreadingCPA do not work together)

        return activeThreads.isEmpty() ? null : Iterables.getOnlyElement(activeThreads);
    }

    @Override
    public Collection<? extends AbstractState> strengthen(AbstractState state, Iterable<AbstractState> otherStates, @Nullable CFAEdge cfaEdge, Precision precision) throws InterruptedException, CPATransferException{
        // get the threading state from 'otherStates'.
        ThreadingState curThreadingState = null;
        for (AbstractState s : otherStates) {
            if (s instanceof ThreadingState) {
                curThreadingState = (ThreadingState) s;
                break;
            }
        }
        assert curThreadingState != null : "Threading state should not be null here";

        assert state instanceof XPORState;
        XPORState curXPORState = (XPORState) state;

        // get the outgoing edges of curThreadingState.
        Iterable<CFAEdge> sucEdges = curThreadingState.getOutgoingEdges();
        // TODO: compute the new threadIdNumbers for the state(curXPORState).
        Map<Integer, Pair<String, Integer>> threadIdNums = curXPORState.getThreadIdNumbers();

        // classifying the edges according their type.
        List<CFAEdge> nEdges = normalEdgeFilter.apply(sucEdges);
        List<CFAEdge> nAEdges = normalAssumeEdgeFilter.apply(sucEdges);
        List<CFAEdge> gvaEdges = globalAccessEdgeFilter.apply(sucEdges);

        if(!nEdges.isEmpty()) {
            // handle the case where nEdges exist.
            // TODO: we first choose a valid 'nEdge'.
            CFAEdge nEdgeToExplore = getValidEdge(nEdges, curThreadingState, curXPORState);
            if(nEdgeToExplore != null) {
                // TODO: actually tid must be not equal for 'nEdges' that are not 'naEdges'.
                int nEdgeExploreTid = threadIdNums.get(nEdgeToExplore.hashCode()).getSecondNotNull();
                sucEdges.forEach(e -> {
                    int eTid = threadIdNums.get(e.hashCode()).getSecondNotNull();
                    if(!e.equals(nEdgeToExplore)
                            /* && eTid != nEdgeExploreTid */) {
                        // if one edge has the same tid with 'nEdgeToExplore', we don't add it to the isolatedSleepSet.
                        curXPORState.isolatedSleepSetAdd(Pair.of(eTid, e.hashCode()));
                    }
                });
                return Collections.singleton(state);
            }
        }
//        assert nEdges.size() == 0 : "here nEdges' size should be 0.";

        if(!nAEdges.isEmpty()) {
            // handle the case where nAEdges exist.
            // TODO: here we add the non-nAEdge to the curXPORState, all nAEdges need to be explored.
            // assume we choose 'nAEdges.get(0)' to explore.
            CFAEdge nAEdgeToExplore = nAEdges.get(0);
            int nAEdgeToExploreTid = threadIdNums.get(nAEdgeToExplore.hashCode()).getSecondNotNull();
            sucEdges.forEach(e -> {
                // we just explore the selected edge and its negative branch.
                int eTid = threadIdNums.get(e.hashCode()).getSecondNotNull();
                // nAEdgeToExploreTid != eTid && e.getPredecessor() != nAEdgeToExplore.getPredecessor()
                // TODO: just one is sufficient.
                if(!e.equals(nAEdgeToExplore)&& (nAEdgeToExploreTid != eTid)) {
                    // if one edge has the same tid with 'nEdgeToExplore', we don't add it to the isolatedSleepSet.
                    curXPORState.isolatedSleepSetAdd(Pair.of(eTid, e.hashCode()));
                }
            });
            return Collections.singleton(state);
        }
        assert nAEdges.isEmpty() : "here nAEdges' size should be 0.";

        if(!gvaEdges.isEmpty()) {
            // handle the case where gvaEdges exist.
            if(gvaEdges.size() > 1) {
                // sort 'sucEdges' by Tid.
                ImmutableList<CFAEdge> sucEdgesSortedByTid = ImmutableList.sortedCopyOf(
                        new Comparator<CFAEdge>() {
                            public int compare(CFAEdge AEdge, CFAEdge BEdge) {
                                int ATid = threadIdNums.get(AEdge.hashCode()).getSecondNotNull();
                                int BTid = threadIdNums.get(BEdge.hashCode()).getSecondNotNull();
                                return BTid - ATid;
                            }
                        },
                        sucEdges);

                AbstractState curComputerState = null;
                if (icComputer instanceof BDDICComputer) {
                    curComputerState = from(otherStates).filter(s -> s instanceof BDDState).iterator().next();
                } else if (icComputer instanceof PredicateICComputer) {
                    // TODO: when using 'PredicateICComputer', the curComputerState is not sure.
                    // we should use 'ARGState' but here we can not get it.
                    curComputerState = null;
                } else {
                    throw new CPATransferException("Unsupported ICComputer: " + icComputer.getClass().toString());
                }

                for(int i = 0; i < sucEdgesSortedByTid.size() - 1; i++) {
                    CFAEdge AEdge = sucEdgesSortedByTid.get(i);
                    // debug
                    if(!determineEdgeType(AEdge).equals(EdgeType.GVAEdge)) {
                        continue;
                    }
                    for(int j = i + 1; j < sucEdgesSortedByTid.size(); j++) {
                        CFAEdge BEdge = sucEdgesSortedByTid.get(j);
                        // debug
                        if(!determineEdgeType(BEdge).equals(EdgeType.GVAEdge)) {
                            continue;
                        }
                        if(canSkip(AEdge, BEdge, curComputerState)) {
                            // if the AEdge and BEdge are independent at curComputerState.
                            // add the [<BEdge-tid, BEdge.hashCode()>, <AEdge-tid, AEdge.hashCode()>]
                            // to the sleep set of curState.
                            ArrayList<Pair<Integer, Integer>> edgesToAddToSleepSet = new ArrayList<>();
                            Pair<Integer, Integer> BEdgeToAdd = Pair.of(threadIdNums.get(BEdge.hashCode()).getSecondNotNull(), BEdge.hashCode()),
                                    AEdgeToAdd = Pair.of(threadIdNums.get(AEdge.hashCode()).getSecondNotNull(), AEdge.hashCode());
                            edgesToAddToSleepSet.add(BEdgeToAdd);
                            edgesToAddToSleepSet.add(AEdgeToAdd);
                            //
                            curXPORState.sleepSetAdd(edgesToAddToSleepSet);
                        }
                    }
                }
            }
        }

        return Collections.singleton(state);
    }

    private boolean canSkip(CFAEdge AEdge, CFAEdge BEdge, AbstractState pComputeState) {

        stats.checkSkipTimes.inc();
        DGNode ANode = condDepGraph.getDGNode(AEdge.hashCode()),
                BNode = condDepGraph.getDGNode(BEdge.hashCode());

        // we can't determine the dependency of thread create edge.
        boolean containThreadCreateEdge =
                (isThreadCreationEdge(AEdge) || isThreadCreationEdge(BEdge));

        // compute the conditional dependency of the two nodes.
        // amounts to get the dependency between 'pCheckEdge' and 'pCurEdge'.
        CondDepConstraints ics = (CondDepConstraints) condDepGraph.dep(ANode, BNode);

        if (ics == null) {
            // they are unconditionally independent.
            // TODO: loop start points should be processed carefully.
            if (!containThreadCreateEdge
                    && !AEdge.getSuccessor().isLoopStart()
                    && !BEdge.getSuccessor().isLoopStart()) {

                stats.checkSkipUnIndepTimes.inc();
                return true;
            } else {
                stats.checkSkipOtherCaseTimes.inc();
                return false;
            }
        } else {
            // case 1: unconditionally dependent, we can't skip.
            if (ics.isUnCondDep()) {
                stats.checkSkipUnDepTimes.inc();
                return false;
            } else {
                // case 2: conditionally independent, we need to use constraints to check whether
                // they are really independent at the given computeState.
                boolean isCondDep = icComputer == null ? true : icComputer.computeDep(ics, pComputeState);

                // they are conditionally independent at the given state.
                if (!isCondDep) {
                    // same, need to process loop start point carefully.
                    if (!containThreadCreateEdge
                            && !AEdge.getSuccessor().isLoopStart()
                            && !BEdge.getSuccessor().isLoopStart()) {

                        stats.checkSkipCondIndepTimes.inc();
                        return true;
                    } else {
                        stats.checkSkipOtherCaseTimes.inc();
                        return false;
                    }
                } else {
                    // is conditionally dependent.
                    stats.checkSkipCondDepTimes.inc();
                    return false;
                }
            }
        }
    }

    EdgeType determineEdgeType(final CFAEdge pEdge) {
        assert pEdge != null : "pEdge shouldn't be null when judging its type!";

        if (isThreadCreationEdge(pEdge)) {
            if (regardThreadCreationAsNormalEdge) {
                // if regard "pthread_create()" as a normal edge, return "NEdge"
                return EdgeType.NEdge;
            } else {
                // else, regard it as a "GVAEdge" which access the global variable.
                return EdgeType.GVAEdge;
            }
        }

        if (condDepGraph.contains(pEdge.hashCode())) {
            return EdgeType.GVAEdge;
        } else if (pEdge instanceof CAssumeEdge) {
            return EdgeType.NAEdge;
        } else {
            return EdgeType.NEdge;
        }
    }

    public boolean isThreadCreationEdge(final CFAEdge pEdge) {
        switch (pEdge.getEdgeType()) {
            case StatementEdge: {
                AStatement statement = ((AStatementEdge) pEdge).getStatement();
                if (statement instanceof AFunctionCall) {
                    AFunctionCall functionCall = (AFunctionCall) statement;
                    AExpression functionNameExp = functionCall.getFunctionCallExpression().getFunctionNameExpression();
                    if (functionNameExp instanceof AIdExpression) {
                        AIdExpression functionNameIdExp = (AIdExpression) functionNameExp;
                        return functionNameIdExp.getName().contains("pthread_create");
                    }
                }
                return false;
            }
            default:
                return false;
        }
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

    private CFAEdge getValidEdge(List<CFAEdge> nEdges, ThreadingState threadingState, XPORState xporState) {

        stats.xporSelectValidEdgeTimer.start();
        CFAEdge result = null;
        for(int i = 0; i < nEdges.size(); i++) {
            CFAEdge e = nEdges.get(i);

            // 1.handle the case: e is 'FunctionReturnEdge'.
            if(e.getEdgeType().equals(CFAEdgeType.FunctionReturnEdge)) {
                // if 'e' is the funcReturnEdge
                String activeThread = xporState.getThreadIdNumbers().get(e.hashCode()).getFirstNotNull();
                CallstackState callstackState = (CallstackState) threadingState.getThreadCallstack(activeThread);
                CFANode callNode = e.getSuccessor().getEnteringSummaryEdge().getPredecessor();
                if(!callstackState.getCallNode().equals(callNode)) {
                    // this means that e is not the right return edge. cf 'CallstackTransferRelation' 135.
                    continue;
                }
            }

            // 2.handle the case: e's successor is the endOfMainThread.
            if(e.getSuccessor().equals(mainFunctionExitNode)) {
                continue;
            }

            // 3.handle the case: e is 'pthread_join'.
            // TODO: whether pthread_join() can be regarded as normal edge is determined by whether its successor is empty.
            if(e.getRawStatement().contains("pthread_join")) {
                if(e.getEdgeType().equals(CFAEdgeType.StatementEdge)) {
                    // if the 'pthread_join' is not the declaration edge.
                    assert e instanceof CStatementEdge;
                    CStatementEdge eStatementEdge = (CStatementEdge) e;
                    CStatement eStatement = eStatementEdge.getStatement();
                    CFunctionCallExpression eFuncCallExpr = null;
                    if(eStatement instanceof CFunctionCallAssignmentStatement) {
                        CFunctionCallAssignmentStatement eFuncCallAssignStatement = (CFunctionCallAssignmentStatement) eStatement;
                        eFuncCallExpr = eFuncCallAssignStatement.getRightHandSide();
                    } else if(eStatement instanceof CFunctionCallStatement) {
                        CFunctionCallStatement eFuncCallStatement = ((CFunctionCallStatement) eStatement);
                        eFuncCallExpr = eFuncCallStatement.getFunctionCallExpression();
                    }
                    if(eFuncCallExpr == null) {
                        continue;
                    }
                    List<CExpression> paras = eFuncCallExpr.getParameterExpressions();
                    assert paras.size() >= 1 : "pthread_join should have one parameter at least!";
                    CExpression endThreadIdExpr = paras.get(0);
                    assert endThreadIdExpr instanceof CIdExpression;
                    String endThreadId = ((CIdExpression) endThreadIdExpr).getName();
                    if(!threadingState.getThreadIds().contains(endThreadId)) {
                        // if the threadingState don't contain the 'endThreadId', which means the thread corresponding to 'endThreadId'
                        // has terminated, we can use it as the nEdge.
                        stats.xporSelectValidEdgeTimer.stop();
                        return e;
                    } else {
                        continue;
                    }
                }
            }

            // 4.handle the case: e is 'pthread_exit'.
            if(e.getRawStatement().contains("pthread_exit")) {
                continue;
            }

            // 5.handle the case: there exist locks in 'threadingState'.
            // TODO: atomic lock problem, if the current edge's active thread doesn't held the lock, then we ignore it.
            // don't choose it, because threadingTransferRelation will prune it.
            String activeThread = xporState.getThreadIdNumbers().get(e.hashCode()).getFirstNotNull();
            if(threadingState.hasLock("__CPAchecker_atomic_lock__")
            && !threadingState.hasLock(activeThread, "__CPAchecker_atomic_lock__")) {
                continue;
            }

            // 6.handle the case: 'e' tries to get lock 'x', but the 'x' has been held by other thread.
            if(e.getRawStatement().contains("pthread_mutex_lock")
                    && e.getEdgeType().equals(CFAEdgeType.StatementEdge)) {
                AStatement statement = ((AStatementEdge) e).getStatement();
                assert statement instanceof AFunctionCallStatement : "unknown case: pthread_mutex_lock is not a function call!";
                try {
                    String lockId = extractLockId((AFunctionCallStatement) statement);
                    if(threadingState.hasLock(lockId) && !threadingState.hasLock(activeThread, lockId)) {
                        continue;
                    }
                } catch (UnrecognizedCodeException ex) {
                    ex.printStackTrace();
                }
            }

            result = e;
            break;
        }

        stats.xporSelectValidEdgeTimer.stop();
        return result;
    }

    public String extractLockId(final AFunctionCallStatement statement) throws UnrecognizedCodeException {
        // first check for some possible errors and unsupported parts
        List<? extends AExpression> params = statement.getFunctionCallExpression().getParameterExpressions();
        if (!(params.get(0) instanceof CUnaryExpression)) {
            throw new UnrecognizedCodeException("unsupported thread locking", params.get(0));
        }

        String lockId = ((CUnaryExpression)params.get(0)).getOperand().toString();
        return lockId;
    }

}
