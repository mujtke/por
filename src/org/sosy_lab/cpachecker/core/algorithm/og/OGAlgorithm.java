package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Functions;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
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
    }

    @Override
    public AlgorithmStatus run(ReachedSet reachedSet)
            throws CPAException,
            InterruptedException,
            CPAEnabledAnalysisPropertyViolationException {
        try {
            return run0(reachedSet);
        } finally {

        }
    }

    private AlgorithmStatus run0(final ReachedSet reachedSet)
    throws CPAException, InterruptedException {
        while (hasWaitingState()) {

            // final AbstractState state = reachedSet.popFromWaitlist();
            final AbstractState state = waitlist.lastElement();
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

        Collection<? extends AbstractState>  successors;
        try {
            successors = transferRelation.getAbstractSuccessors(state, precision);
        } finally {
            // Stop timer for transfer.
        }

        List<Pair<AbstractState, Precision>> withGraphs = new ArrayList<>(),
                noGraphs = new ArrayList<>();

        // Precision adjustment and Split children into two parts if possible.
        for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext(); ) {

            AbstractState s = it.next();
            PrecisionAdjustmentResult precAdjustmentResult;
            try {
                Optional<PrecisionAdjustmentResult>  precisionAdjustmentOptional =
                        precisionAdjustment.prec(s, precision, reachedSet,
                                Functions.identity(), s);
                if (!precisionAdjustmentOptional.isPresent()) {
                    continue;
                }
                precAdjustmentResult = precisionAdjustmentOptional.orElseThrow();
            } finally {
                // Stop time for precision adjustment.
            }

            AbstractState suc = precAdjustmentResult.abstractState();
            Precision pre = precAdjustmentResult.precision();
            Action action = precAdjustmentResult.action();

            // TODO: handle the case where action == BREAK.
            // Whole algorithm stop here?
            if (action == Action.BREAK) {
                logger.log(Level.FINER, "Break signalled, OGAlgorithm will stop.");
                reachedSet.add(suc, pre);
                if (it.hasNext()) {
                    // Copy from CPAAlgorithm.
                    // TODO: Algorithm stops here, so it doesn't matter
                    // to re-add 'state' to waitlist?
                    waitlist.add(state);
                    reachedSet.reAddToWaitlist(state);
                }

                return true;
            }

            if (OGMap.get(((ARGState)suc).getStateId()) != null) {
                // There are some graphs relate with 'suc'.
                withGraphs.add(Pair.of(suc, pre));
            } else {
                // No graph relates with 'suc'.
                noGraphs.add(Pair.of(suc, pre));
            }
        }

        // Add children without graphs to reachedSet first. (Add states to
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
            assert apPair.getFirstNotNull() instanceof ARGState;
            ARGState ch = (ARGState) apPair.getFirstNotNull();
            List<ObsGraph> chGraphs = OGMap.get(ch.getStateId());
            assert chGraphs != null;
            revisitor.apply(chGraphs, revisitResult);
        }

        // Perform transferring for all graphs in 'revisitResult'.
        for (Iterator<Pair<AbstractState, ObsGraph>> it = revisitResult.iterator();
             it.hasNext(); ) {
            Pair<AbstractState, ObsGraph> aoPair = it.next();
            assert aoPair.getFirstNotNull() instanceof ARGState;
            ARGState leadState = (ARGState) aoPair.getFirstNotNull();
            ObsGraph graph = aoPair.getSecondNotNull();
            transfer.multiStepTransfer(waitlist, leadState, graph);
        }

        return false;
    }

    private boolean hasWaitingState() {
        return !this.waitlist.isEmpty();
    }
}