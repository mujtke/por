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
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;

import static java.util.Objects.hash;
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
    Collection<? extends AbstractState> successors;
    try {
      successors = transferRelation.getAbstractSuccessors(state, precision);

    } finally {
      // Stop timer for transfer.
    }

    if (successors.isEmpty())
      return false;

    ARGState parState = (ARGState) state, chState;
    successors = reorder(parState, successors);
    List<ObsGraph> parGraphs = OGMap.get(parState.getStateId()), chGraphs = null;
    assert parGraphs != null && !parGraphs.isEmpty() :
        "Require one graph at least but not found in s" + parState.getStateId() + "!";
    List<Pair<AbstractState, ObsGraph>> revisitResult = new ArrayList<>();

    if (transfer.exitEarly(parState)) {
      List<ObsGraph> rollbackGraphs = handleRollback(parGraphs, revisitResult);
      // When we should go back, we won't visit successors any longer.
      successors.clear();
      // Update OGMap.
      parGraphs.clear();
    }

    List<Pair<AbstractState, Precision>> withGraphs = new ArrayList<>(),
        noGraphs = new ArrayList<>();
    // Use this array of boolean to indicate whether a graph has been removed
    // from the parent state.
    boolean[] hasBeenRemoved = new boolean[parGraphs.size()];
    // Map from index of graph to CFANode, e.g., i -> N0.
    // FIXME: This is the special handle for indeterminate conditional branches.
    Map<Integer, CFANode> nonDetTable = new HashMap<>();
    Set<ObsGraph> blockedGraphs = new HashSet<>();

    // Adjust precision and split children into two parts if possible.
    for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext();) {
      AbstractState s = it.next();
      PrecisionAdjustmentResult precAdjustmentResult;
      try {
        Optional<PrecisionAdjustmentResult>  precisionAdjustmentOptional =
                precisionAdjustment.prec(s, precision, reachedSet,
                        Functions.identity(), s);
        assert precisionAdjustmentOptional.isPresent();
        // if (precisionAdjustmentOptional.isEmpty()) continue;
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
          blockedGraphs.add(parGraph);
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

    // FIXME: will there be some graphs get blocked?
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
    while (!graphsForRevisit.isEmpty() || !revisitResult.isEmpty()) {
      // 1.Revisit.
      //      if (!graphsForRevisit.isEmpty()) {
      for (Iterator<ObsGraph> it = graphsForRevisit.iterator(); it.hasNext();) {
//        ObsGraph graph = graphsForRevisit.remove(0); // graph for revisit.
        ObsGraph graph = it.next();
        it.remove();
        assert graph.needToRevisit() : "Try to revisit a graph should not be!";
        revisitResult.addAll(revisitor.apply(reachedSet, graph));
        // Debug.
        if (graph.getRevisitNode() != null) {
          assert false : "Some graphs keep re-visitable after the revisit!";
        }
      }

      // 2.Transfer.
      Pair<List<ObsGraph>, List<Pair<AbstractState, ObsGraph>>>
          multiTransferResult = performMultiStepTransferFor(revisitResult);
      assert multiTransferResult.getFirst() != null
          && multiTransferResult.getSecond() != null;
      graphsForRevisit.addAll(multiTransferResult.getFirst());
      revisitResult.addAll(multiTransferResult.getSecond());
//      graphsForRevisit.addAll(performMultiStepTransferFor(revisitResult));
    }

    return false;
  }

  private List<ObsGraph> handleRollback(List<ObsGraph> parGraphs,
                              List<Pair<AbstractState, ObsGraph>> revisitResult) {
    assert revisitResult != null;
    List<ObsGraph> rollbackGraphs = new ArrayList<>();
    ARGState preState = null;
    for (ObsGraph g : parGraphs) {
      // When we need to go back, the main thread must be in some node.
      // OGNode nodeOfMain = g.getCurrentNode(OGPORState.getEntryFunctionName());
      OGNode nodeOfMain = g.getLastNode();
      assert nodeOfMain != null :
          "Rolling back requires the node of the main thread not null!";
      assert Objects.equals(nodeOfMain.getInThread(),
          OGPORState.getEntryFunctionName()) :
          "When rolling back, the last node of the graph should be in main thread.";

      // When trying to roll back, there shouldn't be any nodes(come from other threads)
      // not in the graph.
      if (g.getNodeTable().values().stream().filter(Objects::nonNull)
          .anyMatch(n -> g.hb(nodeOfMain, n))) {
        // We cannot send g back in this case.
        rollbackGraphs.add(g);
        continue;
      }

      assert preState == null
          || Objects.equals(preState, nodeOfMain.getPreState())
          : "When rolling back, all graphs' nodes in main thread " +
          "should have the same pre-ARGState.";
      preState = nodeOfMain.getPreState();
      assert preState != null : "Expect a nonnull pre-ARGState.";
      if (nodeOfMain.getTrAfter() != null) {
        g.setLastNode(nodeOfMain.getTrAfter());
//        nodeOfMain.getTrAfter().removeTrBefore();
//        nodeOfMain.removeTrAfter();
      }
      g.removeNode(nodeOfMain, true);
      revisitResult.add(Pair.of(preState, g));
    }

    if (preState != null) {
      // Block main thread at preState.
      OGPORState preOgState =
          AbstractStates.extractStateByType(preState, OGPORState.class);
      assert preOgState != null;
      preOgState.block(OGPORState.getEntryFunctionName());
    }

    return rollbackGraphs;
  }

  private boolean mayRollback(ARGState parState) {
    OGPORState parOgState =
        AbstractStates.extractStateByType(parState, OGPORState.class);
    assert parOgState != null;
    // If main thread is not in any block when exiting, then we needn't go back.
    String entryFunc = OGPORState.getEntryFunctionName();
    if (parOgState.atBlockEndFor(entryFunc)) {
      if (parOgState.hasNonBlockedThread()) {
        // We should block the main thread because we need to explore other threads first.
        parOgState.block(entryFunc);
        return true;
      }
    }

    return false;
  }

  private boolean exitEarly(ARGState parState) {
    OGPORState parOgState =
        AbstractStates.extractStateByType(parState, OGPORState.class);
    assert parOgState != null;
    return parOgState.willExit();
  }

  private List<ObsGraph> getGraphsForRevisit(
          List<Pair<AbstractState, Precision>> withGraphs) {
    List<ObsGraph> result = new ArrayList<>();
    for (Pair<AbstractState, Precision> p : withGraphs) {
      ARGState state = (ARGState) p.getFirstNotNull();
      List<ObsGraph> graphs = OGMap.get(state.getStateId());
      assert graphs != null && !graphs.isEmpty();
      result.addAll(graphs.stream().filter(ObsGraph::needToRevisit)
              .collect(Collectors.toList()));
    }

    return result;
  }

  /**
   * Perform multi-step transfer for all graphs in {@param revisitResult}.
   * @return graphs need to revisit.
   */
  private Pair<List<ObsGraph>, List<Pair<AbstractState, ObsGraph>>>
  performMultiStepTransferFor(List<Pair<AbstractState, ObsGraph>> revisitResult) {
    List<ObsGraph> graphsToRevisitOnly = new ArrayList<>();
    List<Pair<AbstractState, ObsGraph>> graphsToTransfer = new ArrayList<>();
    while (!revisitResult.isEmpty()) {
      Pair<AbstractState, ObsGraph> pair = revisitResult.remove(0);
      ARGState leadState = (ARGState) pair.getFirstNotNull();
      ObsGraph graph = pair.getSecondNotNull();
//            Pair<AbstractState, ObsGraph> transferResult =
//                    transfer.multiStepTransfer(waitlist, leadState, new ArrayList<>(List.of(graph)));
      Triple<AbstractState, ObsGraph, Boolean> transferResult =
              transfer.multiStepTransfer(waitlist, leadState, new ArrayList<>(List.of(graph)));
      if (transferResult != null) {
        // FIXME: some graphs in the result may be re-visitable, how to handle them?
        assert transferResult.getFirst() != null
                && transferResult.getSecond() != null
                && transferResult.getThird() != null;
        if (transferResult.getSecond() == ObsGraph.DUMMY) {
          assert graph.needToRevisit() : "Blocked but not re-visitable graph found!";
          logger.log(Level.INFO, "Blocked but re-visitable graph found at s" +
                  ((ARGState) transferResult.getFirst()).getStateId() +
                  " during the multi-step transfer.");
          graphsToRevisitOnly.add(graph);
          assert !transferResult.getThird() : "Shouldn't transfer a blocked graph.";
        } else {
          if (transferResult.getSecond().needToRevisit()) {
            if (transferResult.getThird()) { // We should continue to transfer the graph.
//              revisitResult.add(Pair.of(transferResult.getFirst(),
//                  transferResult.getSecond()));
              graphsToTransfer.add(Pair.of(transferResult.getFirst(),
                  transferResult.getSecond()));
              graphsToRevisitOnly.add(transferResult.getSecond());
            } else {
//              graphsNeedRevisitOnly.add(transferResult.getSecond().deepCopy(new HashMap<>()));
              graphsToRevisitOnly.add(transferResult.getSecond());
              // FIXME: set lastNode of transferResult.getSecond() re-visited.
//              transferResult.getSecond().setLastNodeRevisited();
            }
          }
        }
      } else {
        // TODO: In this case, have some graphs not been transferred to a proper state?
        logger.log(Level.WARNING,
                "Some graph hasn't been transferred to a proper state.");
      }
    }

    // Debug.
    if (graphsToRevisitOnly.stream().anyMatch(g -> !g.needToRevisit()))
      throw new UnsupportedOperationException("Find a graph not re-visitable!");
    return Pair.of(graphsToRevisitOnly, graphsToTransfer);
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
