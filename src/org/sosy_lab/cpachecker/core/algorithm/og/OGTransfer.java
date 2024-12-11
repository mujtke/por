package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.CriticalAreaAction;
import static org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest.getDotStr;

@Options(prefix = "og.transfer")
public class OGTransfer {

  @Option(description = "Using CDG to reduce state space.")
  private boolean useCDG = false;
  public enum ConflictType {
    NONE, /* Has no conflict */
    TRUE, /* Conflict is certain */
    TEMP, /* Conflict is temporary */
    BLOCKED, /* Conflict because of missing re-visitable events */
  }

  // Activated thread for the current transfer edge.
  String curThd = null;
  // OGNode for the current thread.
  OGNode node = null;

  private final Map<Integer, List<ObsGraph>> OGMap;
  private final Map<Integer, List<SharedEvent>> edgeVarMap;
  private final NLTComparator nltcmp = new NLTComparator();
  private static boolean enableDebug = false;
  private final OGStatistics stat;

  public OGTransfer(
      Map<Integer, List<ObsGraph>> pOGMap,
      HashMap<Integer, List<SharedEvent>> pEdgeVarMap,
      Configuration config,
      OGStatistics pStat) throws InvalidConfigurationException {
    this.OGMap = pOGMap;
    this.edgeVarMap = pEdgeVarMap;
    this.stat = pStat;
    config.inject(this);
    if (useCDG) { ObsGraph.useCDG = true; }
  }

  public NLTComparator getNltcmp() { return nltcmp; }

  // FIXME

  /**
   * @return deep copy of the {@param graph} when needed.
   * @implNote Only copy graph when needed.For co-edges, we copy the graph only when
   * we meet the first one of them, which implies an implicit assumption that the order
   * of parState's outgoing edges keep unchanged across the whole algorithm. For
   * example, if parState has two outgoing edges d and !d, then when we traverse all
   * outgoing edges of parState, we will always meet d before !d. By assuming so, we
   * will only copy the graph when we meet edge d.
   */
  public ObsGraph handleNonDet(ObsGraph graph,
                               ARGState parState,
                               CFAEdge edge) {
    assert edge instanceof AssumeEdge;
    for (ARGState ch : parState.getChildren()) {
      CFAEdge tmpEdge = parState.getEdgeToChild(ch);
      assert tmpEdge != null;
      if (Objects.equals(edge.getPredecessor(), tmpEdge.getPredecessor())) {
        if (Objects.equals(edge, tmpEdge)) {
          return graph.deepCopy(new HashMap<>());
        }

        break; // Only for the first edge, for the second one, we don't copy the graph.
      }
    }

    return null;
  }

  public void enableDebug(boolean pEnableDebug) {
    enableDebug = pEnableDebug;
  }

  private static class NLTComparator implements Comparator<AbstractState> {
    // NLT => <next
    @Override
    public int compare(AbstractState ps1, AbstractState ps2) {
//            Preconditions.checkArgument(ps1 instanceof ARGState
//                    && ps2 instanceof ARGState);
      ARGState s1 = (ARGState) ps1, s2 = (ARGState) ps2;
      Map<Integer, Integer> nlt = GlobalInfo.getInstance().getOgInfo().getNlt();
      ARGState par = s1.getParents().iterator().next();
      assert par == s2.getParents().iterator().next() : "s1 and s2 must " +
              "have the same parent.";
      CFAEdge e1 = par.getEdgeToChild(s1), e2 = par.getEdgeToChild(s2);
      assert e1 != null && e2 != null;
      int cmp1 = 1, cmp2 = -1; // <next by default.
      try {
        cmp1 = nlt.get(hash(e1.hashCode(), e2.hashCode()));
        cmp2 = nlt.get(hash(e2.hashCode(), e1.hashCode()));
      } catch (NullPointerException e) {
        // When e1 == e2, null-pointer exception may happen.
        if (e1 != e2) throw e;
      }
      if (cmp1 == 0 || cmp2 == 0) return 0; // equal.
      if (cmp1 == 1 && cmp2 == -1) return -1; // <
      return 1; // >, cmp1 == -1 && cmp2 == 1.
    }
  }

  /**
   *                          SingleStepTransfer Framework.
   * This method transfers a given graph from {@param parState} to {@param chState}.
   * For the given {@param edge}, there must be some node corresponds it. If the node
   * conflicts with the graph, then transfer stops and returns <null, null> as result.
   * If no conflict exists, then there are two possible cases need to be considered:
   * 1) The graph has contained the node.
   * 2) The graph meets the node first time.
   * For both cases, we need to handle the edge and events according to the types of the
   * edge and critical area.
   * @param parState Initial ARGState where the transferring begin.
   * @param chState Final ARGState where the transferring stop.
   * @return <graph, copiedGraph> CopiedGraph is used for the case where the indeterminacy
   * exists.
   * @implNote When adding events to the node, we use deep copies of the events in
   * {{@link #edgeVarMap}. Similarly, copiedGraph also comes from the deep copying of
   * the graph in {@param graphWrapper}.
   */
  Pair<ObsGraph, ObsGraph> singleStepTransfer(
      ARGState parState, ARGState chState, ObsGraph g) {
    // For debugging.
    int parId = parState.getStateId(), chId = chState.getStateId();

    OGPORState parOgState = getOGPORState(parState),
        chOgState = getOGPORState(chState);
    CriticalAreaAction caa = chOgState.getInCaa();
    Pair<ObsGraph, ObsGraph> result = null;

    // Update chOgState's blockedThreads if needed. Next, if current thread is blocked,
    // we should transfer graph in other threads (if existed).
    updateBlockedThread(parOgState, chOgState);
    if (shouldBeBlocked(g, parOgState, chOgState)) {
      return Pair.of(null, null);
    }

    // Handle different criticalAreaAction.
    switch (caa) {
      case START: result = handleBlockStart(g, parState, chState);
        break;
      case CONTINUE: result = handleBlockContinue(g, parState, chState);
        break;
      case END: result = handleBlockTerminated(g, parState, chState);
        break;
      case NOT_IN: result = handleBlockNotIn(g, parState, chState);
    }
    assert result != null;
    return result;
  }

  private void updateBlockedThread(OGPORState parOgState,
                                   OGPORState chOgState) {
    if (!parOgState.getBlockedThreads().isEmpty()
        && chOgState.getBlockedThreads().isEmpty()) {
      chOgState.setBlockedThreads(parOgState.getBlockedThreads());
    }
  }

  private boolean shouldBeBlocked(
      ObsGraph g, OGPORState par, OGPORState ch) {
    if (g.hasAccessLock()
        // Access lock for a thread not spawned yet is useless.
        && par.hasSpawnedThread(g.getAccessLock())
        && !g.hasAccessLockFor(ch.getInThread())) {
      // We should visit thread that holds a access lock.
      return true;
    }
    // Else, g has no access lock or g has access lock for s.inThread.
    if (g.hasAccessLockFor(ch.getInThread())) {
      return false;
    }
    // Else, g has no access lock.
    if (!g.hasAccessLock() && ch.isBlocked()) {
      if (ch.hasNonBlockedThread()) {
        // We should block at s.
        return true;
      } else {
        // There is no more thread except s.inThread,
        // in this case we should unblock s.blockedThread.
        assert ch.blockFor(ch.getInThread()) :
            "Trying to unblock a thread not should be.";
        ch.unblock(ch.getInThread());
      }
    }

    return false;
  }

  private int getEdgeType(List<SharedEvent> sharedEvents, CFAEdge edge) {
    boolean hasSharedVars = !(sharedEvents == null || sharedEvents.isEmpty()),
            isAssumeEdge = edge instanceof AssumeEdge;
    if (!hasSharedVars && !isAssumeEdge) {
      return 0;
    } else if (!hasSharedVars) { // !hasSharedVars && isAssumeEdge.
      return 1;
    } else if (!isAssumeEdge) { // hasSharedVars && !isAssumeEdge.
      return 2;
    } else { // hasSharedVars && isAssumeEdge.
      return 3;
    }

  }

  public @NonNull OGPORState getOGPORState(AbstractState state) {
    OGPORState result = AbstractStates.extractStateByType(state, OGPORState.class);
    assert result != null;
    return result;
  }

  public @NonNull CFAEdge getEdgeFromTo(ARGState parState, ARGState chState) {
    assert parState != null && chState != null;
    CFAEdge edge = parState.getEdgeToChild(chState);
    assert edge != null;
    return edge;
  }

  private Pair<ObsGraph, ObsGraph> handleBlockNotIn(
      ObsGraph graph,
      ARGState parState,
      ARGState chState) {
    // Caa = NOT_IN.
    ObsGraph copiedGraph = null;
    @NonNull OGPORState chOgState = getOGPORState(chState);
    curThd = getInThread(chOgState);
    node = graph.getCurrentNode(curThd);
    @NonNull CFAEdge edge = getEdgeFromTo(parState, chState);
    List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
    int edgeType = getEdgeType(sharedEvents, edge);
    ConflictType conflict = ConflictType.NONE;
    Pair<ObsGraph, ObsGraph> result = null;

    if (edgeType == 0) { // Local non-assumption edge.
      //
      node = null; // We still don't meet the node.
    } else if (edgeType == 1) { // Local assumption edge.
      node = null;
      CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
      if (coARGEdge != null && graph.meetNewAssumeEdge(curThd)) {
        // In this case indeterminacy exists, and it's the first time that the
        // graph meet the edge. We need to traverse both two edges.
        // This is done only when a graph meets the edge at the first
        // time, otherwise, the graph has known which edge to choose. This
        // is because the graph has stored the corresponding edge at the
        // first time.
        // Specifically, if graph G choose edge(d), then its deep copy nG will
        // choose coEdge(!d). And either of them will remember their choices.
        copiedGraph = handleNonDet(graph, parState, edge);
        graph.addVisitedAssumeEdge(curThd, edge, chOgState);
        copiedGraph.addVisitedAssumeEdge(curThd, coARGEdge, chOgState);
      } else if (coARGEdge != null) {
        // Not the first time that graph meets the edge.
        if (!graph.cachedEdgeMatch(curThd, edge, chOgState))
          // graph = null;
          return Pair.of(null, null);
      }
    } else if (edgeType == 2) { // Shared non-assumption edge
      if (node != null) {
        // In this case, we must check the conflict.
        assert node.contains(edge) :
                "A simple node must contains its only edge!";
        conflict = graph.hasConflict(node);
        if (conflict == ConflictType.TRUE) {
          return Pair.of(null, null);
        }
        // Otherwise, we need to check the mo-deduced conflict
        // if ConflictType.TEMP detected.
      } else if (hasUnmetNode(graph)) { // Node == null and there are unmet nodes.
        // Transfer requires no unmet nodes.
        return Pair.of(null, null);
      } else { // Node == null and no unmet nodes.
        // In this case, there must be no conflict.
        node = new OGNode(
                new ArrayList<>(Collections.singleton(edge)),
                true,
                parState,
                chState);
        node.addEvents(sharedEvents);
        graph.addNode(node);
        // Indicate there is no need to check the conflict.
      }
      // edgeType = 2
    } else { // Shared assumption edge.
      if (node != null) {
        // FIXME: check the conflict first?
        conflict = graph.hasConflict(node);
        if (conflict == ConflictType.TRUE) {
          return Pair.of(null, null);
        }
        boolean edgeInNode = node.contains(edge);
        if (!edgeInNode) { // The node doesn't contain the edge.
          // In this case, we should transfer the graph along the coEdge,
          // which requires coEdge should exist.
          CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
          // FIXME: we may need to replace the edge.
//                    if (coARGEdge == null)
//                        throw new UnsupportedOperationException(
//                                "Graph gets blocked at s" + parState.getStateId());
          return Pair.of(null, null);
        }
        // Else, the node contains the edge.
      } else if (hasUnmetNode(graph)) { // Node == null and there are unmet nodes.
        // Transfer requires no unmet nodes.
        return Pair.of(null, null);
      } else { // Node == null and no unmet nodes exist.
        CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
        if (coARGEdge != null && graph.meetNewAssumeEdge(curThd)) {
          // Indeterminacy exists, and it's the first time that graph meets
          // the edge.
          copiedGraph = handleNonDet(graph, parState, edge);
          List<ARGState> coSucStates = parState.getChildren()
              .stream().filter(s -> Objects.equals(coARGEdge, parState.getEdgeToChild(s)))
              .collect(Collectors.toList());
          assert coSucStates.size() == 1;
          OGNode copiedNode = new OGNode(
              new ArrayList<>(Collections.singleton(coARGEdge)),
              true,
              parState,
              coSucStates.get(0));
          copiedGraph.addNode(copiedNode);
          copiedNode.setInGraph(false);
        }

        node = new OGNode(new ArrayList<>(Collections.singleton(edge)),
                true,
                parState,
                chState);
        node.addEvents(sharedEvents);
        graph.addNode(node);
      }
    }

    assert graph != null;
    if (node != null) {
      if (node.getLheIndex() == -2)
        node.setLHEIndex(-1);
      graph.visitNode(node, true);
      conflict = hasConflictForBlockNotIn(graph, curThd, node, conflict);
      node.updatePreAndSucState(parState, chState);
      node.setLoopDepth(chOgState.getLoopDepth());
    }

    graph.setNeedToRevisit(node != null && node.shouldRevisit());
    // we have reached the end of the node, so update the current node for curThd.
    if (node != null) {
      graph.updateCurrentNodeTable(curThd, node);
      graph.setAccessLock();
    }
    debugActions(graph, parState, chState, edge);
    if (conflict == ConflictType.TEMP) {
      graph.setHasConflicts(true);
      return Pair.of(graph, null);
    } else if (conflict == ConflictType.BLOCKED)
      return Pair.of(null, null);
    result = Pair.of(graph, copiedGraph);

    return result;
  }

  private ConflictType hasConflictForBlockNotIn(
          ObsGraph graph,
          String curThd,
          OGNode node,
          ConflictType conflict) {
    conflict = graph.hasConflict(node);
    if (conflict == ConflictType.TEMP) {
      conflict = node.hasEventsNeedRevisit() ?
              ConflictType.TEMP : ConflictType.BLOCKED;
    }

    if (conflict == ConflictType.TRUE) {
      throw new UnsupportedOperationException("Visited a node shouldn't be.");
    }

    return conflict;
  }

  private Pair<ObsGraph, ObsGraph> handleBlockTerminated(
      ObsGraph graph,
      ARGState parState,
      ARGState chState) {
    // Caa = END. This means edge should be a funCall and node terminates.
    ObsGraph copiedGraph = null;
    @NonNull OGPORState chOgState = getOGPORState(chState);
    curThd = getInThread(chOgState);
    node = graph.getCurrentNode(curThd);
    assert node != null; // node must be not null.
    CFAEdge edge = getEdgeFromTo(parState, chState);
    List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
    int edgeType = getEdgeType(sharedEvents, edge);
    boolean edgeInNode = node.contains(edge);
    Pair<ObsGraph, ObsGraph> result = null;

    if (edgeType == 0) { // Local non-assumption edge.
      if (!edgeInNode) {
        node.addEdgeWithEvents(edge, null);
      } else {
        //
      }
      // TODO: check the conflict.
    }

    else if (edgeType == 2) { // Shared non-assumption edge.
      if (!edgeInNode) {
        node.addEdgeWithEvents(edge, sharedEvents);
      } else {
        //
      }
    }

    else { // Special cases.
      // In normal cases, an assumption edge cannot terminate a node.
      // However, it will happen that an assumption edge appears as the last
      // edge of the node when an abnormal termination happens because of the
      // call of functions like 'exit()' and 'abort()'.
      if (edgeType == 1) { // local-assumption edge.
        if (node.hasBeenAddedToGraph() && edgeInNode) {
          //
        }
        else if (node.hasBeenAddedToGraph() && !edgeInNode) {
          Pair<ObsGraph, ObsGraph> handleResult =
                  handleAssumeEdgeNotInNode(graph, node, edge, parState,
                          chOgState, curThd, false);
          graph = handleResult.getFirst();
          copiedGraph = handleResult.getSecond();
        } else { // Totally new node.
          Pair<ObsGraph, ObsGraph> handleResult =
                  handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                          chOgState, curThd, true);
          graph = handleResult.getFirst();
          copiedGraph = handleResult.getSecond();
        }
        // edgeType == 1
      } else { // edgeType == 3, shared-assumption edge.
        if (node.hasBeenAddedToGraph() && edgeInNode) {
          //
        } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
          Pair<ObsGraph, ObsGraph> handleResult =
                  handleAssumeEdgeNotInNode(graph, node, edge, parState,
                          chOgState, curThd, true);
          graph = handleResult.getFirst();
          copiedGraph = handleResult.getSecond();
        } else { // Totally new node.
          Pair<ObsGraph, ObsGraph> handleResult =
                  handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                          chOgState, curThd, true);
          graph = handleResult.getFirst();
          copiedGraph = handleResult.getSecond();
        }
      }
    }

    ConflictType conflict = ConflictType.NONE;
    if (graph != null) {
      // Even if the node has been added to the graph, we may still need to set
      // relations for the events after lhe.
      graph.visitNode(node, true);
      assert !enableDebug || !DebugAndTest.acyclicMo(graph) : "Mo circle found!";
      // Check possible conflict after having visited the node.
      conflict = hasConflictForBlockTerminated(graph, curThd, node, conflict);
      if (conflict == ConflictType.TRUE) {
        return Pair.of(null, null);
      }
      if (node.getLheIndex() == -2)
        node.setLHEIndex(-1);
    }

    if (graph != null) {
      node.updatePreAndSucState(null, chState);
      node.setLoopDepth(chOgState.getLoopDepth());
      graph.setNeedToRevisit(node.shouldRevisit()); // having reached the end of the node, update the current node for curThd.
      graph.updateCurrentNodeTable(curThd, node);
      graph.setAccessLock();
      debugActions(graph, parState, chState, edge);
    }
    if (conflict == ConflictType.TEMP) {
      graph.setHasConflicts(true);
      return Pair.of(graph, null);
    } else if (conflict == ConflictType.BLOCKED)
      return Pair.of(null, null);

    result = Pair.of(graph, copiedGraph);
    return result;
  }

  private ConflictType hasConflictForBlockTerminated(
          ObsGraph graph,
          String curThd,
          OGNode node,
          ConflictType conflict) {
    conflict = graph.hasConflict(node);
    if (conflict == ConflictType.TEMP) {
      conflict = node.hasEventsNeedRevisit() ?
              ConflictType.TEMP : ConflictType.BLOCKED;
    }
    if (conflict == ConflictType.TRUE) {
      // throw new UnsupportedOperationException("Visited a node shouldn't be.");
    }

    return conflict;
  }

  private Pair<ObsGraph, ObsGraph> handleBlockContinue(
      ObsGraph graph,
      ARGState parState,
      ARGState chState) {
    // Caa = CONTINUE means we are inside a *complex* node.
    ObsGraph copiedGraph = null; // For handling an indeterminate assignment.
    @NonNull OGPORState chOgState = getOGPORState(chState);
    curThd = getInThread(chOgState);
    node = graph.getCurrentNode(curThd);
    assert node != null;
    @NonNull CFAEdge edge = getEdgeFromTo(parState, chState);
    List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
    int edgeType = getEdgeType(sharedEvents, edge);
    boolean edgeInNode = node.contains(edge);
    Pair<ObsGraph, ObsGraph> result = null, handleAssumeResult = null;

    if (edgeType == 0) { // Local non-assumption edge.
      if (node.hasBeenAddedToGraph() && edgeInNode) {
        // The node has been added to the graph.
      }
      else if (node.hasBeenAddedToGraph() && !edgeInNode) {
        // The node has been added to the graph, but some edges get deleted
        // during the revisiting. In this case, we add the edge to the node.
        node.addEdgeWithEvents(edge, null);
      }
      else { // The node is totally new.
        // Strictly, edge shouldn't be inside the node here, because we are constructing
        // the node. But some special edges, like 'functionStartDummyEdge' may cause the
        // assertion error, because we cannot distinguish them.
        // assert !edgeInNode;
        node.addEdgeWithEvents(edge, null);
      }
      // edgeType == 0
    }

    else if (edgeType == 1) { // Local assumption edge.
      if (node.hasBeenAddedToGraph() && edgeInNode) {
        //
      } else if (node.hasBeenAddedToGraph() && !edgeInNode) {
        handleAssumeResult =
                handleAssumeEdgeNotInNode(graph, node, edge, parState,
                        chOgState, curThd, false);
      } // node.hasBeenAddedToGraph() && !edgeInNode
      else { // Totally new node.
        handleAssumeResult =
                handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                        chOgState, curThd, false);
      }
      // edgeType == 1
    }

    else if (edgeType == 2) { // Shared non-assumption edge.
      if (node.hasBeenAddedToGraph() && edgeInNode) {
        //
      }
      else if (node.hasBeenAddedToGraph() && !edgeInNode) {
        node.addEdgeWithEvents(edge, sharedEvents);
      }
      else { // The node is totally new.
        node.addEdgeWithEvents(edge, sharedEvents);
      }
      // edgeType == 2
    }

    else { // Shared assumption edge.
      if (node.hasBeenAddedToGraph() && edgeInNode) {
        //
      }
      else if (node.hasBeenAddedToGraph() && !edgeInNode) {
        handleAssumeResult =
                handleAssumeEdgeNotInNode(graph, node, edge, parState, chOgState,
                        curThd, true);
      } else { // Totally new node.
        handleAssumeResult =
                handleAssumeEdgeWithNewNode(graph, node, edge, parState,
                        chOgState, curThd, true);
      }
    }

    if (handleAssumeResult != null) {
      graph = handleAssumeResult.getFirst();
      copiedGraph = handleAssumeResult.getSecond();
    }
    if (graph != null) {
      node.updatePreAndSucState(null, chState);
      graph.setNeedToRevisit(false);
      debugActions(graph, parState, chState, edge);
    }
    // For copied graph, we don't update info because it doesn't transfer along the edge.
    result = Pair.of(graph, copiedGraph);

    return result;
  }

  // Check whether we need to check conflict caused by mo.
  // If we need to add some shared events to the node, then we put them into
  // toAddEvents. If we need to check conflict, we put some events into toCheckEvents.
  private void getToAddToCheckEvents(OGNode node,
                                     CFAEdge edge,
                                     List<SharedEvent> sharedEvents,
                                     List<SharedEvent> toAddEvents,
                                     List<SharedEvent> toCheckEvents) {
    toAddEvents.addAll(sharedEvents);
    int edgeStartIndex = -1, i;
    for (i = 0; i < node.getEvents().size(); i++) {
      SharedEvent e = node.getEvents().get(i);
      if (edgeStartIndex < 0) {
        if (Objects.equals(e.getInEdge(), edge)) {
          edgeStartIndex = i;
          i--;
          continue;
        }
        for (Iterator<SharedEvent> it = toAddEvents.iterator(); it.hasNext();) {
          SharedEvent ei = it.next();
          // When ei is a read and accesses the save var with e, e will cover ei.
          if (ei.isRead() && ei.accessSameVarWith(e))
            it.remove();
          // FIXME: when both ei and e are write, and they access the same
          //  var, then ei will cover e.
        }
      } else if (Objects.equals(edge, e.getInEdge())){
        // edgeStartIndex >= 0 && edge == e.getInEdge()
        for (Iterator<SharedEvent> it = toAddEvents.iterator(); it.hasNext();) {
          SharedEvent ei = it.next();
          // When e and ei access the same var and have the same access type,
          // ei will have no need to be added.
          if (ei.accessSameVarWith(e) && (e.getAType() == ei.getAType()))
            it.remove();
        }
      } else {
        // edgeStartIndex >= 0 && edge != e.getInEdge()
        for (Iterator<SharedEvent> it = toAddEvents.iterator(); it.hasNext();) {
          SharedEvent ei = it.next();
          // When both e and ei are write and access the same var. ei will be
          // covered by e. NOTE: such e may don't exist. In that case, we will
          //  add ei into toAddEvents (Strictly, this is not correct, but the
          //  ei added here will be covered by other write events added later).
          if (ei.accessSameVarWith(e)
                  && e.getAType() == SharedEvent.AccessType.WRITE
                  && ei.getAType() == SharedEvent.AccessType.WRITE)
            it.remove();
        }
      }
    }

    if (toCheckEvents != null) {
      if (edgeStartIndex >= 0) { // Node has added some events in sharedEvents.
        for (int j = edgeStartIndex; j < node.getEvents().size(); j++) {
          SharedEvent ej = node.getEvents().get(j);
          if (!Objects.equals(ej.getInEdge(), edge))
            break;
          if (ej.isWrite()) // Only check write events.
            toCheckEvents.add(ej);
        }
      }
      // Writes newly added should also be checked.
      toAddEvents.forEach(e -> {
        if (e.isWrite())
          toCheckEvents.add(e);
      });
    }
  }

  private Pair<ObsGraph, ObsGraph> handleBlockStart(
          ObsGraph graph,
          ARGState parState,
          ARGState chState) {
    // Caa = START. This means edge should be a funCall and we will enter a node.
    assert graph != null;
    curThd = getInThread(chState);
    node = graph.getCurrentNode(curThd);
    CFAEdge edge = parState.getEdgeToChild(chState);
    assert edge != null;
    List<SharedEvent> sharedEvents = edgeVarMap.get(edge.hashCode());
    int edgeType = getEdgeType(sharedEvents, edge);
    ObsGraph copiedGraph = null;
    Pair<ObsGraph, ObsGraph> result = null;

    if (edgeType == 0) { // Local non-assumption edge.
      if (node != null) {
        // Only complex node can contain a block start edge. Besides, the node
        // should contain the edge when node != null, this means we have created
        // the node before.
        assert !node.isSimpleNode() && node.contains(edge);
        // NOTE: here is an implicit strong assumption: block start edge contains
        //  no writes.
        // Check the conflict.
//        if (hasConflictForBlockStart(graph, curThd, node, parState, chState))
//          return Pair.of(null, null);
      }
      else { // node == null.
        // We start a new node and enter it if no conflicts exist.
        if (hasUnmetNode(graph)) {
          // Conflicted, we need to meet some other nodes first.
          return Pair.of(null, null);
        }
        // No unmet nodes.
        node = new OGNode(
                new ArrayList<>(Collections.singleton(edge)),
                false,
                parState,
                chState);
        graph.visitNode(node, false);
      }
      // edgeType == 0
    } else if (edgeType == 2) { // Shared non-assumption edge.
      if (node != null) {
        assert !node.isSimpleNode() && node.contains(edge);
//        if (hasConflictForBlockStart(graph, curThd, node, parState, chState))
//          return Pair.of(null, null);
      } else { // Node == null.
        if (hasUnmetNode(graph)) {
          return Pair.of(null, null);
        }
        node = new OGNode(
                new ArrayList<>(Collections.singleton(edge)),
                false,
                parState,
                chState);
        graph.visitNode(node, false);
      }
    } else {
      throw new UnsupportedOperationException(
              "Incorrect edge type: " + edgeType + ", 0 or 2 required.");
    }

    // When we get here, it means the graph is transferred successfully.
    assert graph != null && node != null;
    // update the pre/suc state.
    node.updatePreAndSucState(parState, chState);
    graph.updateCurrentNode(curThd, node);
    graph.setAccessLock();
    graph.setNeedToRevisit(node.shouldRevisit());
    result = Pair.of(graph, null);

    debugActions(graph, parState, chState, edge);
    return result;
  }

  private @NonNull String getInThread(AbstractState state) {
    assert state != null;
    if (state instanceof OGPORState) {
      return ((OGPORState) state).getInThread();
    } else {
      OGPORState ogState =
          AbstractStates.extractStateByType(state, OGPORState.class);
      assert ogState != null;
      return ogState.getInThread();
    }
  }

  private boolean hasConflictForBlockStart(
          ObsGraph graph,
          String curThd,
          OGNode node,
          ARGState parState,
          ARGState chState) {
    ConflictType conflict = graph.hasConflict(node);
    switch (conflict) {
      case TRUE:
        return true;
      case TEMP:
        return !node.hasEventsNeedRevisit();
      case NONE:
      default:
    }
    return false;
  }

  // Get edge's coEdge that comes from parState.
  private CFAEdge getCoEdgeFromARG(ARGState parState, CFAEdge edge) {
    for (ARGState chState : parState.getChildren()) {
      CFAEdge tmp = parState.getEdgeToChild(chState);
      if (tmp instanceof AssumeEdge
              && tmp != edge
              && Objects.equals(tmp.getPredecessor(), edge.getPredecessor())) {
        return tmp;
      }
    }

    return null;
  }

  // Get edge's coEdge that locates in the same CFA and has the same CFA predecessor
  // with the edge.
  private CFAEdge getCoEdgeFromCFA(CFAEdge edge) {
    Preconditions.checkArgument(
            edge.getPredecessor().getNumLeavingEdges() > 1,
            "Predecessor of assume edge has less then two outgoing " +
                    "edges is not allowed: " + edge);
    CFAEdge tmp = null;
    for (int i = 0; i < edge.getPredecessor().getNumLeavingEdges(); i++) {
      tmp = edge.getPredecessor().getLeavingEdge(i);
      if (tmp instanceof AssumeEdge && tmp != edge) {
        break;
      }
    }

    assert tmp != null : "Cannot find the coEdge of: " + edge;
    return tmp;
  }

  /**
   * FIXME
   * Handle the case where the node has been added to the graph but the edge
   * not in the node.
   * Assume: edge = d, co-edge = !d, then d is not inside the node.
   * There are four main cases, and each of them has two sub-cases according to whether
   * the edge contains shared vars:
   * (1) !d is not in the ARG but the node:
   *      (A).if edge don't access shared vars, then * we need to replace
   *          !d(co-edge) with d(edge) and transfer the graph along the edge(d).
   *      (B).Else, transfer gets blocked at {@param parState} because we cannot
   *          replace an edge contains shared vars.
   * (2) !d is neither in the ARG nor the node, then add the edge(d) to the node
   *      and transfer the graph along the edge(d) no matter whether the edge
   *      contains shared vars.
   * (3) !d is in the ARG but the node, then we need to handle the indeterminacy.
   *      Despite whether the edge contains shared vars, we need to copy the graph.
   * (4) !d is in the ARG and the node, then transfer should stop here no matter
   *      whether the edge contains shared vars. Because we should transfer the graph
   *      along !d.
   * @param graph current ObsGraph.
   * @param node current OGNode.
   * @param edge an assumption edge.
   * @param parState antecedent ARGState of the edge.
   * @param chOgState successive OGPORState of the edge.
   * @param curThd the thread that edge in.
   * @param isShared whether the edge contains shared vars.
   * @return Pair of the graph and its deep copy (if needed).
   */
  private Pair<ObsGraph, ObsGraph> handleAssumeEdgeNotInNode(
          ObsGraph graph,
          OGNode node,
          CFAEdge edge,
          ARGState parState,
          OGPORState chOgState,
          String curThd,
          boolean isShared) {
    ObsGraph copiedGraph = null;
    CFAEdge coCFAEdge = getCoEdgeFromCFA(edge),
            coARGEdge = getCoEdgeFromARG(parState, edge);
    boolean coCFAEdgeInNode = node.contains(coCFAEdge);
    if (coCFAEdgeInNode && coARGEdge == null) { // case (1)
      if (!isShared) {
        // Replacing coCFAEdge(!d) with edge(d).
        node.replaceCoEdge(coCFAEdge, edge);
        assert node.getBlockEdges().contains(edge) :
                "Replacing edge " + edge + "Failed!";
      } else {
        // Replacement won't happen for shared assumption edge because the graph
        // remembers which edge it has met. Therefore, transfer gets blocked here.
        return Pair.of(null, null);
//        throw new UnsupportedOperationException(
//            "Mismatched shared assume edge found at s" + parState.getStateId());
      }
    } // case (1)

    else if (!coCFAEdgeInNode && coARGEdge == null) { // case (2)
      // Replacement shouldn't happen. Add the edge(d) to the node.
      node.addEdgeWithEvents(edge,
              isShared ? edgeVarMap.get(edge.hashCode()) : null);
    }

    else if (!coCFAEdgeInNode) { // case (3), coARGEdge != null
      // In this case indeterminacy exists. When indeterminacy exists, we need to
      // traverse both two edges. If it's the first time that the graph meets the
      // edge.
      // FIXME: In this case, the node doesn't contain the edge, which means that
      //  even if it's not the first time the graph meets the edge, the graph has
      //  forgotten the edge it visited before because of the revisit, which
      //  causes some edges to get deleted. Therefore, we handle the case just like
      //  the graph meets the edge the first time.
      copiedGraph = handleNonDet(graph, parState, edge);
      node.addEdgeWithEvents(edge,
              isShared ? edgeVarMap.get(edge.hashCode()) : null);
      OGNode copiedNode = copiedGraph.getNodes().get(graph.getNodes().indexOf(node));
      copiedNode.addEdgeWithEvents(coARGEdge,
          isShared ? edgeVarMap.get(coARGEdge.hashCode()) : null);
      if (!isShared) {
        graph.addVisitedAssumeEdge(curThd, edge, chOgState);
        copiedGraph.addVisitedAssumeEdge(curThd, coARGEdge, chOgState);
      } else {
        // FIXME: should we store the edge if it contains shared vars?
      }
    } // case (3)

    else { // case (4), coCFAEgeInNode && coARGEdge != null
      graph = null;
    }

    return Pair.of(graph, copiedGraph);
  }

  private Pair<ObsGraph, ObsGraph> handleAssumeEdgeWithNewNode(
          ObsGraph graph,
          OGNode node,
          CFAEdge edge,
          ARGState parState,
          OGPORState chOgState,
          String curThd,
          boolean isShared) {
    // It must be the first time we meet the edge.
    assert graph.meetNewAssumeEdge(curThd);
    ObsGraph copiedGraph = null;
    // Copy the graph when indeterminacy exists.
    CFAEdge coARGEdge = getCoEdgeFromARG(parState, edge);
    if (coARGEdge != null) { // Indeterminacy exists.
      copiedGraph = handleNonDet(graph, parState, edge);
      // node.addEdgeWithEvents(edge, null);
      node.addEdgeWithEvents(edge,
          isShared ? edgeVarMap.get(edge.hashCode()) : null);
      OGNode copiedNode = copiedGraph.getNodes().get(graph.getNodes().indexOf(node));
      copiedNode.addEdgeWithEvents(coARGEdge,
          isShared ? edgeVarMap.get(coARGEdge.hashCode()) : null);
      if (!isShared) {
        graph.addVisitedAssumeEdge(curThd, edge, chOgState);
        copiedGraph.addVisitedAssumeEdge(curThd, coARGEdge, chOgState);
      } else {
        // FIXME: cache the edge when it has share vars?
      }
    }
    return Pair.of(graph, copiedGraph);
  }

  private void debugActions(ObsGraph graph,
                            ARGState parState, ARGState chState, CFAEdge edge) {

    if (!enableDebug) return;
    if (graph == null) return;
    addGraphToFull(graph, chState.getStateId());
    System.out.println("Transferring from s" + parState.getStateId()
            + " -> s" + chState.getStateId() + ": " + edge);
  }

  private boolean hasUnmetNode(ObsGraph graph) {
    // Judge whether there is any node in the graph yet to meet.
    // traceLen != nodes.size()
    return graph.getTraceLen() != graph.getNodes().size();
  }

  /**
   * @param task
   * @param revisitTasks
   * @param transferTasks
   * @param waitlist
   * TODO: reorder waitlist according whether a state in it holds some graph.
   */
  void multiStepTransfer(
      final Pair<ARGState, ObsGraph> task,
      final List<Pair<ARGState, ObsGraph>> revisitTasks,
      final List<Pair<ARGState, ObsGraph>> transferTasks,
      final Vector<AbstractState> waitlist) {

    ARGState leadState = task.getFirst();
    ObsGraph parGraph = task.getSecond();
    assert leadState != null && parGraph != null;
    Collection<ARGState> successors = leadState.getChildren();

    if (successors.isEmpty() && exitNormally(leadState) && !waitlist.contains(leadState)) {
      // System.out.println("Leaf state: s" + leadState.getStateId());
      stat.ogCounter.inc();
    }

    for (ARGState suc : successors) {
      Pair<ObsGraph, ObsGraph> sr = singleStepTransfer(leadState, suc, parGraph);
      ObsGraph tg = sr.getFirst();

      if (exitEarly(suc)) {
        if (tg != null) {
          // TODO: roll back.
          assert sr.getSecond() == null;
          if (!handleRollbackForSingleGraph(suc, tg, transferTasks)) {
            if (tg.needToRevisit()) {
              revisitTasks.add(Pair.of(suc, tg));
            }
          }
          return;
        }
        continue;
      }

      else if (tg != null) {
        if (tg.hasConflicts()) {
          assert sr.getSecond() == null;
          if (tg.needToRevisit()) {
            tg.setHasConflicts(false);
            revisitTasks.add(Pair.of(suc, tg));
          }
          // Else, tg gets blocked at suc.
          return;
        }

        ObsGraph cotg = sr.getSecond();
        if (cotg != null) {
          transferTasks.add(Pair.of(leadState, cotg));
        }
        if (tg.needToRevisit()) {
          revisitTasks.add(Pair.of(suc, tg));
        }
        if (waitlist.contains(suc)) {
          transferGraphTo(suc, tg);
          return;
        } else {
          if (suc.getChildren().isEmpty()) {
            waitlist.add(suc);
            transferGraphTo(suc, tg);
          }
          // Transfer of tg will happen after its revisit if
          // it is re-visitable.
          transferTasks.add(Pair.of(suc, tg));
          return;
        }
      }
      // tg == null, continue to transfer the parGraph.
    }
  }

  public boolean exitNormally(ARGState state) {
    OGPORState ogporState =
        AbstractStates.extractStateByType(state, OGPORState.class);
    assert ogporState != null;
    return ogporState.willExit() || ogporState.exitNormally();
  }

  public void transferGraphTo(ARGState state, ObsGraph g) {
    OGPORState ogporState = AbstractStates.extractStateByType(state, OGPORState.class);
    assert ogporState != null;
    int index = ogporState.getSid();
    List<ObsGraph> graphs =
        OGMap.computeIfAbsent(index,
            k -> new ArrayList<>());
    graphs.add(g);
  }

  private void adjustWaitlist(Map<Integer, List<ObsGraph>> OGMap,
                              Vector<AbstractState> waitlist,
                              ARGState state) {
    int i = waitlist.indexOf(state), j = i + 1;
    for (; j < waitlist.size(); j++) {
      assert waitlist.get(j) instanceof ARGState;
      ARGState other = (ARGState) waitlist.get(j);
      // Just searching for the state's siblings that are closer to the end of the
      // waitlist.
      if (Collections.disjoint(state.getParents(), other.getParents())) {
        // If we find some states that belong to different parents with state,
        // We can stop.
        break;
      }
      // Find a sibling of state. If the sibling has graphs, then just skip.
      // Else swap it with state. TODO: nonnull but empty?
      if (OGMap.get(other.getStateId()) == null
              || OGMap.get(other.getStateId()).isEmpty()) {
        continue;
      }
      // Swap.
      ARGState tmp = other;
      waitlist.set(j, state);
      waitlist.set(i, tmp);
      i = j; // Update i, it points to the original state.
    }
  }

  public boolean exitEarly(ARGState argState) {
    OGPORState ogState =
        AbstractStates.extractStateByType(argState, OGPORState.class);
    assert ogState != null;
    if (ogState.blockFor(OGPORState.getEntryFunctionName())) {
      // When main thread is blocked, the program won't exit early.
      return false;
    }
    // If there is no unblocked child thread, then program exit normally?
    Set<String> unblockedChTs = new HashSet<>(ogState.getThreads().keySet());
    unblockedChTs.remove(OGPORState.getEntryFunctionName());
    unblockedChTs.removeAll(ogState.getBlockedThreads());
    return ogState.willExit() && !unblockedChTs.isEmpty();
  }

  /**
   * @return Whether g has been sent back.
   */
  private boolean handleRollbackForSingleGraph(
      final ARGState chState,
      final ObsGraph g,
      final List<Pair<ARGState, ObsGraph>> transferTasks) {
    ARGState backTo = null;
    OGNode nodeOfMain = (g.getLastNode() != null
        && (g.getLastNode().getSucState() == chState))
        ? g.getLastNode() : null;
    if (nodeOfMain != null) {
      if (g.getNodeTable().values().stream().filter(Objects::nonNull)
          .anyMatch(n -> g.hb(nodeOfMain, n))) {
        // For such g, it won't be transferred or sent back.
        return false;
      }
      backTo = g.getRollbackState(nodeOfMain.getPreState(), nodeOfMain.getInThread());
      if (nodeOfMain.getTrAfter() != null) {
        g.setLastNode(nodeOfMain.getTrAfter());
      }
      g.removeNode(nodeOfMain, true);
      transferTasks.add(Pair.of(backTo, g));
    }
    else { // case(2)
      if (g.getLastNode() != null) {
        backTo = g.getLastNode().getSucState();
        backTo = g.getRollbackState(backTo, OGPORState.getEntryFunctionName());
        transferTasks.add(Pair.of(backTo, g));
      } else {
        // logger.log(Level.WARNING, "The program exits early, nothing to do with it.");
        return false;
      }
    }

    if (backTo != null) {
      // Block main thread at state backTo.
      OGPORState backToOgState =
          AbstractStates.extractStateByType(backTo, OGPORState.class);
      assert backToOgState != null;
      backToOgState.block(OGPORState.getEntryFunctionName());
    }
    return true;
  }

  // Debug.
  public void addGraphToFull(ObsGraph graph, Integer stateId) {
    String gStr = getDotStr(graph);
    OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
    assert ogInfo != null;
//        List<String> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
//                k -> new ArrayList<>());
//        Map<Integer, String> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
//                k -> new HashMap<>());
    List<Pair<Integer, String>> ogs = ogInfo.getFullOGMap().computeIfAbsent(stateId,
            k -> new ArrayList<>());
//        ogs.add(gStr);
    ogs.add(Pair.of(graph.getIdentityHash(), gStr));
  }

  // Debug.
  public void removeGraphFromFull(ObsGraph graph, Integer stateId) {
    OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
    assert ogInfo != null;
//        Map<Integer, String> ogs = ogInfo.getFullOGMap().get(stateId);
    List<Pair<Integer, String>> ogs = ogInfo.getFullOGMap().get(stateId);
//        assert ogs != null && ogs.containsKey(graph.getIdentityHash()) :
//                "Missing graph at s" + stateId;
    assert ogs != null : "Missing graph a s" + stateId;
    int index = -1, i = 0;
    for (; i < ogs.size(); i++) {
      if (Objects.equals(ogs.get(i).getFirst(), graph.getIdentityHash())) {
        index = i;
        break;
      }
    }
    assert index >= 0 : "Missing graph a s" + stateId;
//        ogs.remove(graph.getIdentityHash());
    ogs.remove(index);
  }

  // Debug.
  private int p(ObsGraph g) {
    return DebugAndTest.print(g);
  }
}