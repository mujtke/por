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
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.dumpToJson;

import java.util.*;
import java.util.logging.Level;

public class OGAlgorithm implements Algorithm {

    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    private final AlgorithmStatus status;

    private final TransferRelation transferRelation;
    private final PrecisionAdjustment precisionAdjustment;

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final OGRevisitor revisitor;
    private final OGTransfer transfer;

    private final HashMap<Integer, Integer> nlt;

    // We don't use the waitlist provided by reachedSet, it's read-only.
    // Instead, use the 'waitlist' we define. But it is better to keep
    // their behavior synchronous except when we adjust the order of
    // states in 'waitlist'. In other cases, if we perform some
    // operation on a state, e.g., pop a state from 'waitlist', then we
    // should perform the same or similar operation on the waitlist in
    // reachedSet.
    private final Vector<AbstractState> waitlist;
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
        this.nodeMap = ogInfo.getNodeMap();
//        assert OGMap != null && nodeMap != null;
        assert OGMap != null;
        this.revisitor = ogInfo.getRevisitor();
        this.transfer = ogInfo.getTransfer();
        this.waitlist = new Vector<>();
        this.nlt = ogInfo.getNlt();
    }

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
            // When using OGAlgorithm, it's possible that the original waitlist is not
            // empty after the algorithm has finished. Clear the original waitlist to
            // avoid the 'UNKNOWN' result.
            while (!reachedSet.getWaitlist().isEmpty()) {
                reachedSet.popFromWaitlist();
            }
            // Debug.
            dumpToJson(reachedSet);
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
     * @return {@true} if analysis should terminate, {@false} if analysis should continue
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

            // debug.
            boolean debug = false;
//            debug = true;
            if (debug) {
                ARGState pars = (ARGState) state;
                for (AbstractState ch : successors) {
                    ARGState chs = (ARGState) ch;
                    CFAEdge chtp = pars.getEdgeToChild(chs);
                    int parId = pars.getStateId(), chId = chs.getStateId();
                    // Debug.
                    System.out.println("s" + parId
                            + " -> s" + chId
                            + " [label=\"" + chtp + "\"]");
                }
            }

        } finally {
            // Stop timer for transfer.
        }

        List<Pair<AbstractState, Precision>> withGraphs = new ArrayList<>(),
                noGraphs = new ArrayList<>();
        ARGState parState = (ARGState) state, chState;
        List<ObsGraph> parGraphs = OGMap.get(parState.getStateId());

//        Pair<Boolean, Map<String, Pair<Integer, Integer>>> nonDetInfo =
//                transfer.hasNonDet(parState,
//                successors);
//        if (hasNonDet) {
//            // Handle indeterminate successors.
//            for (int i = 0; i < parGraphs.size(); i++) {
//                ObsGraph parGraph = parGraphs.get(i);
//                List<AbstractState> graphNonDetSuc = new ArrayList<>(nonDetSucs);
//                handleNonDet(parGraph, parState, graphNonDetSuc);
//                nonDetTable.put(i, Pair.of(graphNonDetSuc.size(), 1));
//            }
//        }
        // Initializing all to false?
        boolean[] hasBeenRemoved = new boolean[parGraphs.size()];
        // Map from index of graph to CFANode, e.g., i -> N0.
        Map<Integer, CFANode> nonDetTable = new HashMap<>();

        List<? extends AbstractState> nSuccessors = reorder(parState, successors);

        // TODO.
        boolean findError = false;
        // Adjust precision and split children into two parts if possible.
        for (Iterator<? extends AbstractState> it = nSuccessors.iterator(); it.hasNext(); ) {

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
            Precision pre = precAdjustmentResult.precision();
            chState = (ARGState) suc;

            // Perform all possible single-step transferring.
            // I.e., transfer graphs from parent to its children.
            // NOTE: 'parGraphs == null' != 'parGraphs.isEmpty()'
            CFAEdge edge = parState.getEdgeToChild(chState);
            assert edge != null;

            if (parGraphs == null) {
                // This means all graphs in parState have been transferred.
                noGraphs.add(Pair.of(suc, pre));
                continue;
            }

//            List<ObsGraph> toRemove = new ArrayList<>();
            for (int i = 0; i < parGraphs.size(); i++) {
                if (hasBeenRemoved[i]) continue;
                // TODO: transferred graph should be deep copy one?
                ObsGraph parGraph = parGraphs.get(i);
                // Indicate that whether the transfer is designated.
                boolean constrainedTransfer = false;
                if (nonDetTable.containsKey(i)) {
                    CFANode wantedCFANode = nonDetTable.get(i);
                    if (!Objects.equals(edge.getPredecessor(), wantedCFANode)) {
                        continue;
                    }
                    constrainedTransfer = true;
                }

                // Single transfer.
                ObsGraph chGraph =
                        transfer.singleStepTransfer(new ArrayList<>(List.of(parGraph)),
                                edge,
                                parState,
                                chState);
                if (chGraph != null) {
                    // If parGraph could be transferred to chState, we should relate chGraph
                    // with chState.
                    OGMap.putIfAbsent(chState.getStateId(), new ArrayList<>());
                    List<ObsGraph> chGraphs = OGMap.get(chState.getStateId());
                    chGraphs.add(chGraph);
                    // So if nonDetSucs contains suc, we should not add parGraph to the
                    // toRemove, because we also need to transfer parGraph to the other
                    // suc in the nonDetSucs.
                    if (edge instanceof AssumeEdge) {
                        if (constrainedTransfer) {
                            hasBeenRemoved[i] = true;
                        } else {
                            CFANode constriantedCFANode =
                                    transfer.handleNonDet();
                        }
                    } else {
                        // Suc belongs to a determinate statement.
//                        toRemove.add(parGraph);
                        hasBeenRemoved[i] = true;
                    }
                }
            }
            // Remove transferred graphs.
            parGraphs.removeAll(toRemove);
            // If no graphs in parState, set its graphs to be null?
            if (parGraphs.isEmpty()) OGMap.put(parState.getStateId(), null);

            if (OGMap.get(chState.getStateId()) != null) {
                // There are some graphs relate with 'suc'.
                withGraphs.add(Pair.of(suc, pre));
            } else {
                // No graph relates with 'suc'.
                noGraphs.add(Pair.of(suc, pre));
            }
        }

        // Add children without graphs to reachedSet first. (which will add states to
        // the waitlist too).
        noGraphs.forEach(sp -> {
            // FIXME: destroyed ARGState?
            if (!((ARGState) sp.getFirstNotNull()).isDestroyed()) {
                waitlist.add(sp.getFirstNotNull());
                reachedSet.add(sp.getFirstNotNull(), sp.getSecondNotNull());
            }
        });
        withGraphs.forEach(sp -> {
            if (!((ARGState) sp.getFirstNotNull()).isDestroyed()) {
                waitlist.add(sp.getFirstNotNull());
                reachedSet.add(sp.getFirstNotNull(), sp.getSecondNotNull());
            }
        });

        // Perform revisit for states with graphs.
        List<Pair<AbstractState, ObsGraph>> revisitResult = new ArrayList<>();
        do {
            for (Iterator<Pair<AbstractState, Precision>> it = withGraphs.iterator();
                 it.hasNext(); ) {
                Pair<AbstractState, Precision> apPair = it.next();
                ARGState ch = (ARGState) apPair.getFirstNotNull();
                List<ObsGraph> chGraphs = OGMap.get(ch.getStateId());
                assert chGraphs != null;
                revisitor.apply(chGraphs, revisitResult);
            }

            // Perform transfer for all graphs in 'revisitResult'.
            List<Pair<AbstractState, ObsGraph>> toAdd = new ArrayList<>();
            for (Iterator<Pair<AbstractState, ObsGraph>> it = revisitResult.iterator();
                 it.hasNext(); ) {
                Pair<AbstractState, ObsGraph> aoPair = it.next();
                ARGState leadState = (ARGState) aoPair.getFirstNotNull();
                ObsGraph graph = aoPair.getSecondNotNull();
                // Before transferring the graph, set current nodes for threads.
//                setInitalNodeTable(leadState, graph);
                Pair<AbstractState, ObsGraph> transferResult =
                        transfer.multiStepTransfer(waitlist, leadState,
                        new ArrayList<>(List.of(graph)));
                if (transferResult != null) {
                    graph = transferResult.getSecondNotNull();
                    if (graph.isNeedToRevisit()) {
//                        revisitResult.add(transferResult);
                        toAdd.add(transferResult);
                    }
                }
//                it.remove();
            }
            revisitResult.clear();
            revisitResult.addAll(toAdd);
        } while (!revisitResult.isEmpty());

        return false;
//        return findError;
    }

    /**
     * We Assume a total order (<next) on all statements (edges), and this method
     * reorders the successors according to the assumed order. If the order on some
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

    private boolean hasWaitingState() {
        return !this.waitlist.isEmpty();
    }
}
