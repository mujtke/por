package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Functions;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.dumpToJson2;

import java.util.*;
import java.util.logging.Level;

public class OGAlgorithm implements Algorithm, StatisticsProvider {

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final OGStatistics stat = new OGStatistics();

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

  public OGAlgorithm(CFA cfa,
                     ConfigurableProgramAnalysis cpa,
                     LogManager pLog,
                     Configuration config,
                     ShutdownNotifier pShutdownNotifier) {
    this.logger = pLog;
    this.shutdownNotifier = pShutdownNotifier;
    this.status = AlgorithmStatus.SOUND_AND_PRECISE;
    this.transferRelation = cpa.getTransferRelation();
    this.precisionAdjustment = cpa.getPrecisionAdjustment();
    OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
    this.OGMap = ogInfo.getOGMap();
    assert OGMap != null;
    this.revisitor = new OGRevisitor(config, cfa, stat, logger);
    this.revisitor.enableDebug(ogInfo.isEnableDebug());
    this.transfer = new OGTransfer(ogInfo.getOGMap(), ogInfo.getEdgeVarMap(), stat);
    this.transfer.enableDebug(ogInfo.isEnableDebug());
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
    Collection<? extends AbstractState> successors;
    try {
      successors = transferRelation.getAbstractSuccessors(state, precision);

    } finally {
      // Stop timer for transfer.
    }

    ARGState parState = (ARGState) state, chState;
    successors = reorder(parState, successors);
    List<ObsGraph> parGraphs = OGMap.get(parState.getStateId()), chGraphs = null;
    if ((parGraphs == null || parGraphs.isEmpty())) {
      return false;
    }

    for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext();) {
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
          addState(reachedSet, waitlist, suc, prec);
          return true;
        }
      }
      addState(reachedSet, waitlist, suc, prec);
    }

    // transfer and revisit.
    List<Pair<ARGState, ObsGraph>> transferTasks = new ArrayList<>(),
        revisitTasks = new ArrayList<>();
    parGraphs.forEach(g -> transferTasks.add(Pair.of(parState, g)));
    parGraphs.clear();
    while (!transferTasks.isEmpty()) {
      // 1.Transfer.
      Pair<ARGState, ObsGraph> tTask = transferTasks.remove(0);
      transfer.multiStepTransfer(tTask, revisitTasks, transferTasks, waitlist);

      // 2.Revisit.
      while (!revisitTasks.isEmpty()) {
        Pair<ARGState, ObsGraph> rTask = revisitTasks.remove(0);
        transferTasks.addAll(revisitor.apply(reachedSet, rTask));
      }
    }

    return false;
  }

  private void addState(final ReachedSet pReachedSet,
                         final Vector<AbstractState> pWaitlist,
                         AbstractState state,
                         Precision precision) {
      // NOTE: a destroyed ARGState shouldn't be added to reachedSet.
      if (!((ARGState) state).isDestroyed()) {
        pWaitlist.add(state);
        pReachedSet.add(state, precision);
      }
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

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    statsCollection.add(stat);
  }
}