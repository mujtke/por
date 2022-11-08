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
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.pcdpor.AbstractICComputer;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;

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

    public XPORTransferRelation(Configuration config, LogManager logger, CFA cfa)
        throws InvalidConfigurationException {
        config.inject(this);
        locationsCPA = LocationsCPA.create(config, logger, cfa);
        condDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
        stats = new XPORStatistics();
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

        // compute the new locations.
        Collection<? extends AbstractState> newLocStates = locationsCPA.getTransferRelation()
                .getAbstractSuccessorsForEdge(curState.getThreadLocs(), precision, cfaEdge);
        assert newLocStates.size() <= 1 : "the number of newly gotten locationsStates must be less or equal one";

        if(newLocStates.isEmpty()) {
            return ImmutableList.of();
        }

        // get the new locs.
        LocationsState newLocs = (LocationsState) newLocStates.iterator().next();
        Map<Integer, Pair<String, Integer>> oldThreadIdNumbers = curState.getThreadIdNumbers();

        // update the thread ids and their number.
        Pair<Integer, Map<Integer, Pair<String, Integer>>> newThreadInfo =
                updateThreadIdNumber(curState.getThreadCounter(), oldThreadIdNumbers, newLocs);
        int newThreadCounter = newThreadInfo.getFirstNotNull();
        Map<Integer, Pair<String, Integer>> newThreadIdNumbers = newThreadInfo.getSecondNotNull();

        // if curEdge in curState's sleepSet or isolatedSleepSet, we can return empty.
        // in curState's sleepSet means that curEdge is in sleepSet as a single element rather like 'p.q'.
        int curThreadCounter = oldThreadIdNumbers.get(cfaEdge.hashCode()).getSecondNotNull();
        Pair<Integer, Integer> curEdge = Pair.of(curThreadCounter, cfaEdge.hashCode());
        if (curState.sleepSetContain(curEdge) || curState.isolatedSleepSetContains(curEdge)) {
            return ImmutableList.of();
        }

        // else, updated the sleep set and return.
        // generate the successor with empty sleepSet and isolatedSleepSet.
        // TODO: delay the update to 'strengthen' method: sleepSet, isolatedSleepSet, threadIdNumbers
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
            successor.getSleepSet().addAll(curState.getSleepSet());
        }

        return ImmutableList.of(successor);
    }

    Pair<Integer, Map<Integer, Pair<String, Integer>>> updateThreadIdNumber(
            int pOldThreadCounter,
            final Map<Integer, Pair<String, Integer>> pOldThreadIdNumbers,
            final LocationsState pNewLocs
    ) {
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
//            System.out.println("oldThreadCounter = " + (pOldThreadCounter - 1) + ", newThreadCounter = " + pOldThreadCounter);
        }

        // finished the update of thread id and number.
        Map<Integer, Pair<String, Integer>> newThreadIdNumsWithEdge = new HashMap<>();
        // TODO: here the edge_hash is not known yet. So we use other hash_code to replace temporarily.
        newThreadIdNumbers.forEach((k,v) -> {
            newThreadIdNumsWithEdge.put(k.hashCode() + v.hashCode(), Pair.of(k, v));
        });
        return Pair.of(pOldThreadCounter, newThreadIdNumsWithEdge);
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
        ArrayList<CFAEdge> sucEdges = new ArrayList<>();
        curThreadingState.getOutgoingEdges().forEach(e -> sucEdges.add(e));

        // TODO: compute the new threadIdNumbers for the state(curXPORState).
        // here, we have gotten the <main, 0>, just need to get the corresponding edge_hash.
        // { [edge_hash, <main, 0>], ... }
        ThreadingState tmpThreadingState = curThreadingState;
        Map<Integer, Pair<String, Integer>> oldThreadIdNums = curXPORState.getThreadIdNumbers(),
                threadIdNums = new HashMap<>();
        // get the map of ids & nums.
        Map<String, Integer> idNums = new HashMap<>();
        oldThreadIdNums.forEach((k, v) -> idNums.put(v.getFirstNotNull(), v.getSecondNotNull()));
        sucEdges.forEach(e -> {
            String eActiveThread = getActiveThread(e, tmpThreadingState);
            assert eActiveThread != null : "eActiveThread should not be null!";
            int num = idNums.containsKey(eActiveThread) ? idNums.get(eActiveThread) : -1;
            assert num != -1 : "can't find the eActiveThread in oldThreadIdNums!";
            threadIdNums.put(e.hashCode(), Pair.of(eActiveThread, num));
        });
        // replace the 'oldThreadIdNums' with 'threadIdNums'.
        curXPORState.setThreadIdNumbers(threadIdNums);


//        // get the List of Triple<edge, tid, edge-hashCode>
//        Map<CFAEdge, Pair<Integer, Integer>> edgesWithTid = curXPORStateThreadIdNums;

        // classifying the edges according their type.
        List<CFAEdge> nEdges = normalEdgeFilter.apply(sucEdges);
        List<CFAEdge> nAEdges = normalAssumeEdgeFilter.apply(sucEdges);
        List<CFAEdge> gvaEdges = globalAccessEdgeFilter.apply(sucEdges);

        if(!nEdges.isEmpty()) {
            // handle the case where nEdges exist.
            // TODO: just let 'nEdgeToExplore = nEdges.get(0)' has some problem: if 'nEdgeToExplore' is endOfMainThread
            // other edges removed wrong.
            // CFAEdge nEdgeToExplore = nEdges.get(0);
            CFAEdge nEdgeToExplore = getValidEdge(nEdges, tmpThreadingState);
            if(nEdgeToExplore != null) {
                int nEdgeExploreTid = threadIdNums.get(nEdgeToExplore.hashCode()).getSecondNotNull();
                sucEdges.forEach(e -> {
                    int eTid = threadIdNums.get(e.hashCode()).getSecondNotNull();
                    if(!e.equals(nEdgeToExplore)
                            && (eTid != nEdgeExploreTid)) {
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
                if(!e.equals(nAEdgeToExplore)
                        && (!e.getPredecessor().equals(nAEdgeToExplore.getPredecessor()))
                        && (nAEdgeToExploreTid != eTid)) {
                    // if one edge has the same tid with 'nEdgeToExplore', we don't add it to the isolatedSleepSet.
                    curXPORState.isolatedSleepSetAdd(Pair.of(eTid, e.hashCode()));
                }
            });
            return Collections.singleton(state);
        }
        assert nAEdges.isEmpty() : "here nAEdges' size should be 0.";

        if(!gvaEdges.isEmpty()) {
            // handle the case where gvaEdges exist.
            // not important: if the nEdges is not empty, add the edges in it(should be the endOfMainFunction).
//            if(!nEdges.isEmpty()) {
//                nEdges.forEach(e -> curXPORState.isolatedSleepSetAdd(edgesWithTid.get(e)));
//            }
            if(gvaEdges.size() > 1) {
                // sort 'sucEdges' by Tid.
                ImmutableList<CFAEdge> sucEdgesSortedByTid = ImmutableList.sortedCopyOf(
                        new Comparator<CFAEdge>() {
                            public int compare(CFAEdge AEdge, CFAEdge BEdge) {
                                int ATid = threadIdNums.get(AEdge.hashCode()).getSecondNotNull();
                                int BTid = threadIdNums.get(BEdge.hashCode()).getSecondNotNull();
                                return ATid - BTid;
                            }
                        },
                        sucEdges);
                // if one edge has been in sleep set of curXPORState, then we don't consider it.
                ImmutableList<CFAEdge> sucEdgesRemoveInSleepSet = from(sucEdgesSortedByTid)
                        .filter(e -> !curXPORState.sleepSetContain(
                                Pair.of(threadIdNums.get(e.hashCode()).getSecondNotNull(), e.hashCode())
                        )).toList();

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

                for(int i = 0; i < sucEdgesRemoveInSleepSet.size() - 1; i++) {
                    CFAEdge AEdge = sucEdgesRemoveInSleepSet.get(i);
                    for(int j = i + 1; j < sucEdgesRemoveInSleepSet.size(); j++) {
                        CFAEdge BEdge = sucEdgesRemoveInSleepSet.get(j);
                        if(canSkip(AEdge, BEdge, curComputerState)) {
                            // if the AEdge and BEdge are independent at curComputerState.
                            // add the [<BEdge-tid, BEdge.hashCode()>, <AEdge-tid, AEdge.hashCode()>]
                            // to the sleep set of curState.
                            ArrayList<Pair<Integer, Integer>> edgesToAddToSleepSet = new ArrayList<>();
                            Pair<Integer, Integer> BEdgeToAdd = Pair.of(threadIdNums.get(BEdge.hashCode()).getSecondNotNull(), BEdge.hashCode()),
                                    AEdgeToAdd = Pair.of(threadIdNums.get(AEdge.hashCode()).getSecondNotNull(), BEdge.hashCode());
                            edgesToAddToSleepSet.add(BEdgeToAdd);
                            edgesToAddToSleepSet.add(AEdgeToAdd);
                        }
                    }
                }
            }
        }

        return Collections.singleton(state);
    }

    private boolean canSkip(CFAEdge AEdge, CFAEdge BEdge, AbstractState pComputeState) {

//        stat.checkSkipTimes.inc();
        DGNode ANode = condDepGraph.getDGNode(AEdge.hashCode()),
                BNode = condDepGraph.getDGNode(BEdge.hashCode());

        // we can't determine the dependency of thread create edge.
        boolean containThreadCreateEdge =
                (isThreadCreateEdge(AEdge) || isThreadCreateEdge(BEdge));

        // compute the conditional dependency of the two nodes.
        // amounts to get the dependency between 'pCheckEdge' and 'pCurEdge'.
        CondDepConstraints ics = (CondDepConstraints) condDepGraph.dep(ANode, BNode);

        if (ics == null) {
            // they are unconditionally independent.
            // TODO: loop start points should be processed carefully.
            if (!containThreadCreateEdge
                    && !AEdge.getSuccessor().isLoopStart()
                    && !BEdge.getSuccessor().isLoopStart()) {

//                stat.checkSkipUnIndepTimes.inc();
                return true;
            } else {
//                stat.checkSkipFailedTimes.inc();
                return false;
            }
        } else {
            // case 1: unconditionally dependent, we can't skip.
            if (ics.isUnCondDep()) {
//                stat.checkSkipUnDepTimes.inc();
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

//                        stat.checkSkipCondIndepTimes.inc();
                        return true;
                    } else {
//                        stat.checkSkipFailedTimes.inc();
                        return false;
                    }
                } else {
                    // is conditionally dependent.
//                    stat.checkSkipCondDepTimes.inc();
                    return false;
                }
            }
        }
    }

    private boolean isThreadCreateEdge(final CFAEdge pEdge) {
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

    /** Search for the thread, where the current edge is available.
     * The result should be exactly one thread, that is denoted as 'active',
     * or NULL, if no active thread is available.
     *
     * This method is needed, because we use the CompositeCPA to choose the edge,
     * and when we have several locations in the threadingState,
     * only one of them has an outgoing edge matching the current edge.
     */
    @Nullable
    private String getActiveThread(final CFAEdge cfaEdge, final ThreadingState threadingState) {
        final Set<String> activeThreads = new HashSet<>();
        for (String id : threadingState.getThreadIds()) {
            if (Iterables.contains(threadingState.getThreadLocation(id).getOutgoingEdges(), cfaEdge)) {
                activeThreads.add(id);
            }
        }

        assert activeThreads.size() <= 1 : "multiple active threads are not allowed: " + activeThreads;
        // then either the same function is called in different threads -> not supported.
        // (or CompositeCPA and ThreadingCPA do not work together)

        return activeThreads.isEmpty() ? null : Iterables.getOnlyElement(activeThreads);
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

//    public void updateThreadIdNumbers2(ThreadingState curState, Iterable<CFAEdge> edges, XPORState curXPORState) {
//        int oldThreadCounter = curXPORState.getThreadCounter();
//        Map<Integer, Pair<String, Integer>> oldThreadIdNumbers = curXPORState.getThreadIdNumbers();
//        Map<Integer, Pair<String, Integer>> newThreadIdNumbers = new HashMap<>();
//
//        for(CFAEdge e : edges) {
//            String activeThread = getActiveThread(e, curState);
//            if(!oldThreadIdNumbers.containsKey(e.hashCode())) {
//                CFAEdge curXPORStateProcEdge = curXPORState.getProcEdge();
//                if(curXPORStateProcEdge.getSuccessor().equals(e.getPredecessor())) {
//                    // if the 'e' is the successive edge of curXPORState's prcEdge.
//                    newThreadIdNumbers.put(e.hashCode(), oldThreadIdNumbers.get(curXPORStateProcEdge.hashCode()));
//                } else {
//                    // TODO: if 'e' is the first of a newly created thread.
//                    newThreadIdNumbers.put(e.hashCode(), Pair.of(activeThread, ++oldThreadCounter));
//                }
//            } else {
//                // else, for those 'e' both in oldThreadIdNumbers and in 'newThreadIdNumbers', we keep them unchanged.
//                newThreadIdNumbers.put(e.hashCode(), oldThreadIdNumbers.get(e.hashCode()));
//            }
//        }
//        // update the curXPORState's threadIdNumbers with 'newThreadIdNumbers'
//        curXPORState.setThreadIdNumbers(newThreadIdNumbers);
//    }

    private CFAEdge getValidEdge(List<CFAEdge> nEdges, ThreadingState threadingState) {
        // get one nEdge that is not the endOfMainThread
        CFAEdge result = null;
        for(int i = 0; i < nEdges.size(); i++) {
            CFAEdge e = nEdges.get(i);
            if(e.getSuccessor().equals(mainFunctionExitNode)) {
                continue;
            }
            // TODO: pthread_join() is regarded as normal edge.
            if(e.getRawStatement().contains("pthread_join") || e.getRawStatement().contains("pthread_exit")) {
                continue;
            }
            // TODO: atomic lock problem, if the current edge's active thread doesn't held the lock, then we ignore it.
            // don't choose it, because threadingTransferRelation will prune it.
            String activeThread = getActiveThread(e, threadingState);
            if(threadingState.hasLock("__CPAchecker_atomic_lock__")
            && !threadingState.hasLock(activeThread, "__CPAchecker_atomic_lock__")) {
                continue;
            }
            result = e;
            break;
        }
        return result;
    }
}
