package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Functions;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.dumpToJson;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.dumpToJson2;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OGAlgorithm implements Algorithm {

    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    private final AlgorithmStatus status;

    private final TransferRelation transferRelation;
    private final PrecisionAdjustment precisionAdjustment;

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final OGRevisitor revisitor;
    private final OGTransfer transfer;

    private final HashMap<Integer, Integer> nlt;

    // We don't use the waitlist provided by reachedSet, because it's read-only.
    // Instead, use the 'waitlist' we define. But it is better to keep
    // their behavior synchronous except when we adjust the order of
    // states in 'waitlist'. In other cases, if we perform some
    // operation on a state, e.g., pop a state from 'waitlist', and then we
    // should perform the same or similar operation on the waitlist in
    // reachedSet.
    private Vector<AbstractState> waitlist = new Vector<>();

    // Debug.
    private boolean enableDebug = false;

    public OGAlgorithm(ConfigurableProgramAnalysis cpa,
                       LogManager pLog,
                       ShutdownNotifier pShutdownNotifier) {
        this.logger = pLog;
        this.shutdownNotifier = pShutdownNotifier;
        this.status = AlgorithmStatus.SOUND_AND_PRECISE;
        this.transferRelation = cpa.getTransferRelation();
        this.precisionAdjustment = cpa.getPrecisionAdjustment();
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        this.OGMap = ogInfo.getOGMap();
        assert OGMap != null;
        this.revisitor = ogInfo.getRevisitor();
        this.transfer = ogInfo.getTransfer();
        this.nlt = ogInfo.getNlt();
        this.enableDebug = ogInfo.isEnableDebug();
    }

    public Vector<AbstractState> getWaitlist() { return waitlist; }

    @Override
    public AlgorithmStatus run(ReachedSet reachedSet)
            throws CPAException,
            InterruptedException,
            CPAEnabledAnalysisPropertyViolationException {
        try {
            // Initialize the waitlist we define.
            waitlist.addAll(reachedSet.getWaitlist());
            return run0(reachedSet);
        } finally {
            // NOTE: When using OGAlgorithm, it's possible that the original waitlist is not
            // empty after the algorithm has finished. Clear the original waitlist to
            // avoid the 'UNKNOWN' result.
            while (!reachedSet.getWaitlist().isEmpty()) {
                reachedSet.popFromWaitlist();
            }
            // Debug.
            if (enableDebug) {
                // Export observing graphs.
                // dumpToJson(reachedSet);
                dumpToJson2(reachedSet);
            }
        }
    }

    private AlgorithmStatus run0(final ReachedSet reachedSet)
    throws CPAException, InterruptedException {
        while (hasWaitingState()) {

            // final AbstractState state = reachedSet.popFromWaitlist();
            final AbstractState state = waitlist.lastElement();
            // Remove the popped state.
            waitlist.remove(state);
            final Precision precision = reachedSet.getPrecision(state);

            logger.log(Level.FINER, "Retrieved state from watilist");
            try {
                if (handleState(state, precision, reachedSet)) {
                    // if the algorithm should terminate.
                    return status;
                }
            } catch (Exception e) {
                // Re-add 'state' to the waitlist. According CPAAlgorithm, there might be
                // some unhandled successors when exception happened.
                throw e;
            }
        }

        // No error found after explore the all states.
        return status;
    }

    /**
     * @return true if analysis should terminate, false if analysis should continue
     * with next state.
     */
    private boolean handleState(
            final AbstractState state,
            final Precision precision,
            final ReachedSet reachedSet)
    throws InterruptedException, CPAException {
        logger.log(Level.ALL, "Current state is ", state, " with precision", precision);

        // debug.
        int curStateId = ((ARGState) state).getStateId();
        Collection<? extends AbstractState>  successors;
        try {
            successors = transferRelation.getAbstractSuccessors(state, precision);

        } finally {
            // Stop timer for transfer.
        }

        if (successors.isEmpty())
            return false;

        ARGState parState = (ARGState) state, chState;
        List<? extends AbstractState> nSuccessors = reorder(parState, successors);

        List<Pair<AbstractState, Precision>> withGraphs = new ArrayList<>(),
                noGraphs = new ArrayList<>();
        List<ObsGraph> parGraphs = OGMap.get(parState.getStateId()), chGraphs = null;
        assert parGraphs != null && !parGraphs.isEmpty() :
                "Require one graph at least but not found in s" + parState.getStateId() + "!";

        // Use this array of boolean to indicate whether a graph has been removed
        // from the parent state.
        boolean[] hasBeenRemoved = new boolean[parGraphs.size()];
        // Map from index of graph to CFANode, e.g., i -> N0.
        // FIXME: This is the special handle for indeterminate conditional branches.
        Map<Integer, CFANode> nonDetTable = new HashMap<>();
        //
        Set<ObsGraph> blockedGraphs2 = new HashSet<>();

        // Adjust precision and split children into two parts if possible.
        for (Iterator<? extends AbstractState> it = nSuccessors.iterator(); it.hasNext();) {
            AbstractState s = it.next();
            PrecisionAdjustmentResult precAdjustmentResult;
            try {
                Optional<PrecisionAdjustmentResult>  precisionAdjustmentOptional =
                        precisionAdjustment.prec(s, precision, reachedSet,
                                Functions.identity(), s);
                assert precisionAdjustmentOptional.isPresent();
                precAdjustmentResult = precisionAdjustmentOptional.orElseThrow();
            } finally {
                // Stop time for precision adjustment.
            }

            AbstractState suc = precAdjustmentResult.abstractState();
            Precision prec = precAdjustmentResult.precision();
            Action action = precAdjustmentResult.action();

            // FIXME: handle the action?
            if (action == Action.BREAK) {
                // Without stop operation, does 'break' mean termination?
                if (AbstractStates.isTargetState(suc)) {
                    reachedSet.add(suc, prec);
                    waitlist.add(suc);
                    return true;
                }
            }

            chState = (ARGState) suc;
            chGraphs = null;

            // Perform all possible single-step transferring.
            // I.e., transfer as many as graphs from parState to chState.
            CFAEdge edge = parState.getEdgeToChild(chState);
            assert edge != null;

            for (int i = 0; i < parGraphs.size(); i++) {
                if (hasBeenRemoved[i]) // This graph has been transferred, just skip it.
                    continue;
                ObsGraph parGraph = parGraphs.get(i);
                boolean shouldKeepGraph = false,
                        /* Special handle for indeterminate conditional branches, if
                        * shouldKeepGraph == true, it means we need to keep the graph because
                        * it will be used for the other conditional branch. I.e., we will
                        * use the graph twice. */
                        secondTransfer = false; /* The flag indicate that whether we are
                                                        transferring the graph second time. */
                /* nonDetTable records the index of the graph in array 'hasBeenRemoved', e.g.,
                * the <1, N15> means the graph (parGraphs.get(1)) will need to be transferred along
                * the edge whose predecessor CFANode is N15. And before that, we have transferred
                * the graph along one edge has the same predecessor CFANode N15. This is necessary
                * when indeterminacy exists.
                 */
                if (nonDetTable.containsKey(i)) {
                    CFANode wantedCFANode = nonDetTable.get(i);
                    if (!Objects.equals(edge.getPredecessor(), wantedCFANode)) {
                        // When nonDetTable is not empty, we should transfer the graph
                        // along the edge in it.
                        continue;
                    }
                    secondTransfer = true;
                }

                // Single-step transfer.
                Pair<ObsGraph, ObsGraph> transferResult = transfer.singleStepTransfer(
                        new ArrayList<>(List.of(parGraph)),
                        edge,
                        parState,
                        chState,
                        true);

                ObsGraph chGraph = transferResult.getFirst(),
                        copiedGraph = transferResult.getSecond();
                if (chGraph == ObsGraph.DUMMY) {
                    hasBeenRemoved[i] = true;
                    blockedGraphs2.add(parGraph);
                    continue;
                }
                if (copiedGraph != null) {
                    nonDetTable.put(i, edge.getPredecessor());
                    // Replace the ith graph with copiedGraph, the former has been transferred,
                    // so we use its deep copy to replace it.
                    parGraphs.set(i, copiedGraph);
                    shouldKeepGraph = true;
                    // TODO: copiedGraph's creationState?
                    // copiedGraph.setCreationState(parState);
                }

                if (chGraph != null) {
                    // ParGraph has been transferred to the chState.
                    OGMap.putIfAbsent(chState.getStateId(), new ArrayList<>());
                    chGraphs = OGMap.get(chState.getStateId());
                    chGraphs.add(chGraph);
                    if (!shouldKeepGraph) {
                        hasBeenRemoved[i] = true;
                    }
                    if (secondTransfer) {
                        // Copied graph has been transferred, now we remove it.
                        nonDetTable.remove(i);
                    }
                }
            }

            if (chGraphs != null && !chGraphs.isEmpty()) {
                withGraphs.add(Pair.of(suc, prec));
            } else {
                noGraphs.add(Pair.of(suc, prec));
            }
        }

        List<Pair<AbstractState, ObsGraph>> revisitResult = new ArrayList<>();
        // FIXME: will there be some graphs get blocked?
        // List<ObsGraph> blockedGraphs = getBlockedGraphs(parGraphs, hasBeenRemoved);
        List<ObsGraph> blockedGraphs = new ArrayList<>(blockedGraphs2);
        if (!blockedGraphs.isEmpty()) {
            logger.log(Level.WARNING,
                    "Blocked graphs found at state s" + parState.getStateId());
            blockedGraphs.forEach(
                    g -> performRevisitForBlockedGraph(g, parState, precision, revisitResult));
        }

        // Remove transferred graphs from parGraphs.
        parGraphs.clear();

        // Add children to reachedSet and waitlist ('noGraphs' first).
        addStates(reachedSet, waitlist, noGraphs);
        addStates(reachedSet, waitlist, withGraphs);

        // Revisit and transfer(multi-step).
        List<ObsGraph> graphsForRevisit = getGraphsForRevisit(withGraphs);
        // Debug.
        List<ObsGraph> revisitedGraphs = new ArrayList<>();
        // Perform revisit for graphs if necessary, and fill the results into revisitResult.
        for (Iterator<Pair<AbstractState, Precision>> it = withGraphs.iterator();
             it.hasNext();) {
            Pair<AbstractState, Precision> pair = it.next();
            ARGState ch = (ARGState) pair.getFirstNotNull();
            chGraphs = OGMap.get(ch.getStateId());
            assert chGraphs != null;
            // Debug.
            revisitedGraphs.addAll(chGraphs);
            revisitor.apply(parState, ch, precision, chGraphs, revisitResult);
        }
        // Debug, after the revisit, no graph in revisitedGraphs is re-visitable.
        if (revisitedGraphs.stream().anyMatch(g -> g.getRevisitNode() != null)) {
            List<ObsGraph> reVisitableGraphs = revisitedGraphs.stream()
                    .filter(g -> g.getRevisitNode() != null).collect(Collectors.toList());
            System.out.println("Some graphs still keep re-visitable after the revisit.");
        }

        // Perform multi-step transfer for all graphs in 'revisitResult'.
        for (Iterator<Pair<AbstractState, ObsGraph>> it = revisitResult.iterator();
             it.hasNext();) {
            Pair<AbstractState, ObsGraph> pair = it.next();
            ARGState leadState = (ARGState) pair.getFirstNotNull();
            ObsGraph graph = pair.getSecondNotNull();
            Pair<AbstractState, ObsGraph> transferResult =
                    transfer.multiStepTransfer(waitlist, leadState, new ArrayList<>(List.of(graph)));
            if (transferResult != null) {
                // TODO: do something here, like print result to log?
                // FIXME: some graphs in the result may be re-visitable, how to handle them?
                if (transferResult.getSecond() != null && transferResult.getSecond().needToRevisit()) {
                    assert false : "Some graphs need a further revisit.";
                }
            } else {
                // TODO: In this case, have some graphs not been transferred to a proper state?
//                throw new UnsupportedOperationException(
//                        "Some graph hasn't been transferred to a proper state");
            }
        }

        return false;
    }

    private List<ObsGraph> getGraphsForRevisit(
            List<Pair<AbstractState, Precision>> withGraphs) {
        List<ObsGraph> result = new ArrayList<>();
        return result;
    }

    private List<ObsGraph> getBlockedGraphs(
            List<ObsGraph> parGraphs, boolean[] hasBeenRemoved) {
        List<ObsGraph> blockedGraphs = new ArrayList<>();
        for (int i = 0; i < hasBeenRemoved.length; i++) {
            if (!hasBeenRemoved[i])
                blockedGraphs.add(parGraphs.get(i));
        }

        return blockedGraphs;
    }

    private void addStates(final ReachedSet pReachedSet,
                           final Vector<AbstractState> pWaitlist,
                           List<Pair<AbstractState, Precision>> pStates) {
        pStates.forEach(sp -> {
            // NOTE: a destroyed ARGState shouldn't be added to reachedSet.
            if (!((ARGState) sp.getFirstNotNull()).isDestroyed()) {
                pWaitlist.add(sp.getFirstNotNull());
                pReachedSet.add(sp.getFirstNotNull(), sp.getSecondNotNull());
            }
        });
    }

    /**
     * FIXME
     * Perform revisit for the blocked graphs.
     */
    private void performRevisitForBlockedGraph(
            ObsGraph graph,
            ARGState parState,
            Precision precision,
            List<Pair<AbstractState, ObsGraph>> revisitResult) {
        // Get the re-visitable nodes of the graph.
        OGNode revisitNode = graph.getRevisitNode();
        graph.setNeedToRevisit(true);

        // FIXME
        assert parState.getParents().size() == 1;
        ARGState revisitParState = parState.getParents().iterator().next();

        revisitor.apply(revisitParState, parState, precision, List.of(graph), revisitResult);
    }

    /**
     * We assume a total order (<next) on all statements (edges), and this method
     * reorders the successors according to an assumed order. If the order on some
     * successors hasn't been computed, this method will compute it first.
     */
    private List<AbstractState> reorder(ARGState parState, Collection<?
            extends AbstractState> successors) {
        ArrayList<AbstractState> result = new ArrayList<>(successors);
        if (!(result.size() == 1)) {
            for (int i = 0; i < result.size() - 1; i++) {
                for (int j = i; j < result.size(); j++) {
                    // Compute the <next and put the result into the nlt, if we haven't
                    // compared ei with ej yet.
                    CFAEdge ei = parState.getEdgeToChild((ARGState) result.get(i)),
                            ej = parState.getEdgeToChild((ARGState) result.get(j));
                    assert ei != null && ej != null;
                    Integer p1 = hash(ei.hashCode(), ej.hashCode()),
                            p2 = hash(ej.hashCode(), ei.hashCode());
                    // If we have computed the <next for ei and ej, just continue;
                    if (nlt.containsKey(p1) && nlt.containsKey(p2)) continue;
                    // Else, computing the <next for ei and ej.
                    if ((ei instanceof AssumeEdge)
                            && (ej instanceof AssumeEdge)
                            && ei.getPredecessor().equals(ej.getPredecessor())) {
                        // handle assume statement;
                        // 0 means not comparable.
                        nlt.put(p1, 0);
                        nlt.put(p2, 0);
                    } else {
                        nlt.put(p1, 1);
                        nlt.put(p2, -1);
                    }
                }
            }
            // Reorder the result by <next.
            result.sort(transfer.getNltcmp());
        }
        return result;
    }

    private boolean hasWaitingState() { return !this.waitlist.isEmpty(); }

    // Just for debugging. Printing a given graph g.
    private int p(ObsGraph g) {
        return DebugAndTest.print(g);
    }
}
