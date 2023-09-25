package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Functions;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getAllDot;
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
        assert OGMap != null && nodeMap != null;
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
                    // if algorithm should terminate.
                    return status;
                }
            } catch (Exception e) {
                // Re-add 'state' to the waitlist, According CPAAlgorithm, there might be
                // some unhandled successors when exception happened.
                throw e;
            }
        }

        // No error found after explore the all states.
        return status;
    }

    /**
     *
     * @param state
     * @param precision
     * @param reachedSet
     * @return {@true} if analysis should terminate, {@false} if analysis should continue
     * with next state.
     * @throws CPATransferException
     * @throws InterruptedException
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
            debug = true;
            if (debug) {
                ARGState pars = (ARGState) state;
                for (AbstractState ch : successors) {
                    ARGState chs = (ARGState) ch;
                    CFAEdge chtp = pars.getEdgeToChild(chs);
                    int parId = pars.getStateId(), chId = chs.getStateId();
//                    if (OGMap.get(parId) != null)
//                        System.out.println("s" + parId
//                                + "[label=\"s"
//                                + pars.getStateId()
//                                + " gs = " + OGMap.get(pars.getStateId()).size()
//                                + "\"]");
//                    if (OGMap.get(chId) != null)
//                        System.out.println("s" + chId
//                                + "[label=\"s"
//                                + chs.getStateId()
//                                + " gs = " + OGMap.get(chs.getStateId()).size()
//                                + "\"]");
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

        List<? extends AbstractState> nSuccessors = reorder(parState, successors);

        // Adjust precision and split children into two parts if possible.
        for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext(); ) {

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

            // Perform all possible single step transfer.
            // I.e., transfer graphs from parent to its children.
            // NOTE: 'parGraphs == null' != 'parGraphs.isEmpty()'
            CFAEdge edge = parState.getEdgeToChild(chState);
            assert edge != null;
            List<ObsGraph> parGraphs = OGMap.get(parState.getStateId()),
                    toRemove = new ArrayList<>();
            if (parGraphs == null) {
                // This means all graphs in parState have been transferred.
                noGraphs.add(Pair.of(suc, pre));
                continue;
            }
            for (ObsGraph parGraph : parGraphs) {
                getAllDot(parGraph);
                System.out.println("");
                ObsGraph chGraph =
                        transfer.singleStepTransfer(parGraph, edge, parState, chState);
                if (chGraph != null) {
                    // If parGraph could be transferred to chState, we should relate chGraph
                    // with chState.
                    List<ObsGraph> chGraphs =
                            OGMap.computeIfAbsent(((ARGState) suc).getStateId(),
                            k -> new ArrayList<>());
                    chGraphs.add(chGraph);
                    toRemove.add(parGraph);
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
        // waitlist too).
        noGraphs.forEach(sp -> {
            waitlist.add(sp.getFirstNotNull());
            reachedSet.add(sp.getFirstNotNull(), sp.getSecondNotNull());
        });
        withGraphs.forEach(sp -> {
            waitlist.add(sp.getFirstNotNull());
            reachedSet.add(sp.getFirstNotNull(), sp.getSecondNotNull());
        });

        // Perform revisit for states with graphs.
        List<Pair<AbstractState, ObsGraph>> revisitResult = new ArrayList<>();
        for (Iterator<Pair<AbstractState, Precision>> it = withGraphs.iterator();
             it.hasNext(); ) {
            Pair<AbstractState, Precision> apPair = it.next();
            ARGState ch = (ARGState) apPair.getFirstNotNull();
            List<ObsGraph> chGraphs = OGMap.get(ch.getStateId());
            assert chGraphs != null;
            revisitor.apply(chGraphs, revisitResult);
        }

        // Perform transfer for all graphs in 'revisitResult'.
        for (Iterator<Pair<AbstractState, ObsGraph>> it = revisitResult.iterator();
             it.hasNext(); ) {
            Pair<AbstractState, ObsGraph> aoPair = it.next();
            ARGState leadState = (ARGState) aoPair.getFirstNotNull();
            ObsGraph graph = aoPair.getSecondNotNull();
            getAllDot(graph);
            System.out.printf("");
            transfer.multiStepTransfer(waitlist, leadState, graph);
        }

        return false;
    }

    private List<AbstractState> reorder(ARGState parState, Collection<?
            extends AbstractState> successors) {
        ArrayList<AbstractState> result = new ArrayList<>(successors);
        if (!(result.size() == 1)) {
            for (int i = result.size() - 2; i >= 0; i--) {
                for (int j = 0; j <= i; j++) {
                    // jth should <next (j+1)th.
                    CFAEdge ej1 = parState.getEdgeToChild((ARGState) result.get(j)),
                            ej2 = parState.getEdgeToChild((ARGState) result.get(j + 1));
                    assert ej1 != null && ej2 != null;
                    Integer p1 = hash(ej1.hashCode(), ej2.hashCode()),
                            p2 = hash(ej2.hashCode(), ej1.hashCode()),
                            cmp1, cmp2;
                    // handle assume statement;
                    if ((ej1 instanceof AssumeEdge)
                            && (ej2 instanceof AssumeEdge)
                            && ej1.getPredecessor().equals(ej2.getPredecessor())) {
                        // 0 means not comparable.
                        nlt.putIfAbsent(p1, 0);
                        nlt.putIfAbsent(p2, 0);
                    } else {
                        nlt.putIfAbsent(p1, 1);
                        nlt.putIfAbsent(p2, -1);
                    }
                    cmp1 = nlt.get(p1); cmp2 = nlt.get(p2);
                    if ((cmp1 == 0 || cmp2 == 0) ||
                            (cmp1 > 0 && cmp2 < 0)) continue;
                    // If ej2 <next ej1, swap result[j] and result[j + 1].
                    AbstractState tmp = result.get(j);
                    result.set(j, result.get(j + 1));
                    result.set(j + 1, tmp);
                }
            }
        }
        return result;
    }

    private boolean hasWaitingState() {
        return !this.waitlist.isEmpty();
    }
}