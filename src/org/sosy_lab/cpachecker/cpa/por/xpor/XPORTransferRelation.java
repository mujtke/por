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

//        // get the new locs.
        LocationsState newLocs = (LocationsState) newLocStates.iterator().next();
//        Map<String, Integer> oldThreadIdNumbers = curState.getThreadIdNumbers();
//
//        // update the thread ids and their number.
//        Pair<Integer, Map<String, Integer>> newThreadInfo =
//                updateThreadIdNumber(curState.getThreadCounter(), oldThreadIdNumbers, newLocs);
//        int newThreadCounter = newThreadInfo.getFirstNotNull();
//        Map<String, Integer> newThreadIdNumbers = newThreadInfo.getSecondNotNull();
//
//        // if 'pCfaEdge' in curState's sleepSet or isolatedSleepSet, return empty.
//        Pair<Integer, Integer> curEdge = Pair.of(newThreadCounter, cfaEdge.hashCode());

        int curEdgeTid = curState.getThreadIdNumbers().get(cfaEdge.hashCode()).getSecondNotNull();
        Pair<Integer, Integer> curEdge = Pair.of(curEdgeTid, cfaEdge.hashCode());

        // if curEdge in curState's sleepSet or isolatedSleepSet, we can return empty.
        // in curState's sleepSet means that curEdge is in sleepSet as a single element rather like 'p.q'.
        if (curState.sleepSetContain(curEdge) || curState.isolatedSleepSetContains(curEdge)) {
            return ImmutableList.of();
        }

        // else, updated the sleep set and return.
        // generate the successor with empty sleepSet and isolatedSleepSet.
        // TODO: delay the update to 'strengthen' method: sleepSet, isolatedSleepSet, threadIdNumbers
        XPORState successor = new XPORState(
                curState.getThreadCounter(),
                cfaEdge,
                newLocs,
                curState.getThreadIdNumbers(),
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

    Pair<Integer, Map<String, Integer>> updateThreadIdNumber(
            int pOldThreadCounter,
            final Map<String, Integer> pOldThreadIdNumbers,
            final LocationsState pNewLocs
    ) {
        // copy the threadIdNumber into the 'newThreadInfo' from 'pOldThreadIdNumber'.
        Map<String, Integer> newThreadIdNumbers = new HashMap<>(pOldThreadIdNumbers);

        // Part I: remove the threads which exited.
        // 1.get all thread-ids in new 'locationsState'
        Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
        // 2.get all thread-ids that doesn't exist in 'newThreadIds'
        ImmutableSet<String> tidsToRemoved =
                from(newThreadIdNumbers.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
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
        return Pair.of(pOldThreadCounter, newThreadIdNumbers);
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
        assert curThreadingState != null : "threading state is null is not allowed here";

        assert state instanceof XPORState;
        XPORState curXPORState = (XPORState) state;

        // get the outgoing edges of curThreadingState.
        // TODO: for the thread creation edge, the edge form new thread we can not get now.
        Iterable<CFAEdge> sucEdges = curThreadingState.getOutgoingEdges();

        // try to obtain the corresponding tid of the edges.
        // use 'tmpThreadingState' to replace 'curThreadingState', or else the transform method will report error.
        updateThreadIdNumbers2(curThreadingState, sucEdges, curXPORState);
        ThreadingState tmpThreadingState = curThreadingState;
        // get the List of Triple<edge, tid, edge-hashCode>
        Map<CFAEdge, Pair<Integer, Integer>> edgesWithTid = new HashMap<>();
        sucEdges.forEach(e -> {
            int tid = curXPORState.getThreadIdNumbers().get(e.hashCode()).getSecondNotNull();
            edgesWithTid.put(e, Pair.of(tid, e.hashCode()));
        });

        // classifying the edges according their type.
        List<CFAEdge> nEdges = normalEdgeFilter.apply(sucEdges);
        List<CFAEdge> nAEdges = normalAssumeEdgeFilter.apply(sucEdges);
        List<CFAEdge> gvaEdges = globalAccessEdgeFilter.apply(sucEdges);

        if(!nEdges.isEmpty()) {
            // handle the case where nEdges exist.
            // TODO: here we add the non-nEdgeToExplore to the curXPORState's 'isolatedSleepSet'.
            // we keep the 'nEdges.get(0)'
            CFAEdge nEdgeToExplore = nEdges.get(0);
            sucEdges.forEach(e -> {
                if(!e.equals(nEdgeToExplore)) {
//                    ArrayList<Pair<Integer, Integer>> edgeAddToSleepSet = new ArrayList<>();
//                    edgeAddToSleepSet.add(edgesWithTid.get(e));
//                    curXPORState.sleepSetAdd(edgeAddToSleepSet);
                    curXPORState.isolatedSleepSetAdd(edgesWithTid.get(e));
                }
            });
            return Collections.singleton(state);
        }
        assert nEdges.size() == 0 : "here nEdges' size should be 0.";

        if(!nAEdges.isEmpty()) {
            // handle the case where nAEdges exist.
            // TODO: here we add the non-nAEdge to the curXPORState, all nAEdges need to be explored.
            // assume we choose 'nAEdges.get(0)' to explore.
            CFAEdge nAEdgeToExplore = nAEdges.get(0);
            sucEdges.forEach(e -> {
                // we just explore the selected edge and its negative branch.
                if(!e.equals(nAEdgeToExplore)
                        && (!e.getPredecessor().equals(nAEdgeToExplore.getPredecessor()))) {
//                    ArrayList<Pair<Integer, Integer>> edgeAddToSleepSet = new ArrayList<>();
//                    edgeAddToSleepSet.add(edgesWithTid.get(e));
//                    curXPORState.sleepSetAdd(edgeAddToSleepSet);
                    curXPORState.isolatedSleepSetAdd(edgesWithTid.get(e));
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
                                int ATid = edgesWithTid.get(AEdge).getFirstNotNull();
                                int BTid = edgesWithTid.get(BEdge).getFirstNotNull();
                                return ATid - BTid;
                            }
                        },
                        sucEdges);
                ImmutableList<CFAEdge> sucEdgesRemoveInSleepSet = from(sucEdgesSortedByTid)
                        .filter(e -> !curXPORState.sleepSetContain(edgesWithTid.get(e))).toList();

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
                            edgesToAddToSleepSet.add(edgesWithTid.get(BEdge));
                            edgesToAddToSleepSet.add(edgesWithTid.get(AEdge));
                            curXPORState.sleepSetAdd(edgesToAddToSleepSet);
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

    public void updateThreadIdNumbers2(ThreadingState curState, Iterable<CFAEdge> edges, XPORState curXPORState) {
        int oldThreadCounter = curXPORState.getThreadCounter();
        Map<Integer, Pair<String, Integer>> oldThreadIdNumbers = curXPORState.getThreadIdNumbers();
        Map<Integer, Pair<String, Integer>> newThreadIdNumbers = new HashMap<>();

        for(CFAEdge e : edges) {
            String activeThread = getActiveThread(e, curState);
            if(!oldThreadIdNumbers.containsKey(e.hashCode())) {
                CFAEdge curXPORStateProcEdge = curXPORState.getProcEdge();
                if(curXPORStateProcEdge.getSuccessor().equals(e.getPredecessor())) {
                    // if the 'e' is the successive edge of curXPORState's prcEdge.
                    newThreadIdNumbers.put(e.hashCode(), oldThreadIdNumbers.get(curXPORStateProcEdge.hashCode()));
                } else {
                    // TODO: if 'e' is the first of a newly created thread.
                    newThreadIdNumbers.put(e.hashCode(), Pair.of(activeThread, ++oldThreadCounter));
                }
            } else {
                // else, for those 'e' both in oldThreadIdNumbers and in 'newThreadIdNumbers', we keep them unchanged.
                newThreadIdNumbers.put(e.hashCode(), oldThreadIdNumbers.get(e.hashCode()));
            }
        }
        // update the curXPORState's threadIdNumbers with 'newThreadIdNumbers'
        curXPORState.setThreadIdNumbers(newThreadIdNumbers);
    }
}
