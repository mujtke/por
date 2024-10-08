package org.sosy_lab.cpachecker.core.algorithm.og;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.DummyCFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bdd.ConditionalStatementHandler;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.DebugAndTest;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.*;

@Options(prefix = "algorithm.og")
public class OGRevisitor {

  private static boolean enableDebug = false;

  public enum REVISIT_TYPE {
    READ, WRITE
  }

  // Handle conditional statements.
  private static ConditionalStatementHandler CSHandler;

  public OGRevisitor(Configuration config, CFA cfa, LogManager logger)
          throws InvalidConfigurationException {
    CSHandler = new ConditionalStatementHandler(config, cfa, logger);
  }


  public void enableDebug(boolean pEnableDebug) { enableDebug = pEnableDebug; }

  public boolean isEnableDebug() { return enableDebug; }

  /**
   * @param precision useful when computing the satisfiability for reading from an
   *                  indeterminate assignment.
   * @param graphs The list of graphs on which revisit will be performed if needed.
   * @param result All results produced by revisit process.
   */
  public void apply(ARGState parState,
                    ARGState chState,
                    Precision precision,
                    List<ObsGraph> graphs,
                    List<Pair<AbstractState, ObsGraph>> result) {
    if (graphs.isEmpty())
      return;

    for (ObsGraph graph : graphs) {
      if (!graph.needToRevisit())
        continue;
      result.addAll(revisit(parState, chState, precision, graph));
    }
  }

  public List<Pair<AbstractState, ObsGraph>> apply(
          final ReachedSet reachedSet,
          ObsGraph graph) {
    OGNode lastNode = graph.getLastNode();
    assert lastNode != null :
            "Trying to revisit a graph without last-added node!";
    ARGState chState = lastNode.getSucState();
    assert chState != null :
            "Missing sucState for the re-visitable node!";
    assert chState.getParents().size() == 1 : "ARG state s" +
            chState.getStateId() + " has more than one parents!";
    ARGState parState = chState.getParents().iterator().next();
    assert reachedSet.contains(chState) :
            "Missing precision for state s" + chState.getStateId();
    Precision chPrecision = reachedSet.getPrecision(chState);
    return new ArrayList<>(revisit(parState, chState, chPrecision, graph));
  }

  // parState: indicating where the revisit takes place.
  private List<Pair<AbstractState, ObsGraph>> revisit(ARGState parState,
                                                      ARGState chState,
                                                      Precision precision,
                                                      ObsGraph g) {
    List<Pair<AbstractState, ObsGraph>> result = new ArrayList<>();
    // List of the graphs that need to revisit.
    List<ObsGraph> RG = new ArrayList<>();
    RG.add(g);


    while (!RG.isEmpty()) {
      ObsGraph G0 = RG.remove(0);

      // List of the events that need to revisit.
      List<SharedEvent> RE = new ArrayList<>(G0.getRE());
      for (SharedEvent a; !RE.isEmpty();) {
        // If we are handling event e, then in the resulting graphs, it will not be
        // handled again. Otherwise, we may get redundant results.
        a = RE.remove(0);
        // Update event 'a' as the new last-handled event.
        a.getInNode().setLastHandledEvent(a);
        // FIXME: Update the status whether G0 is still re-visitable.
        G0.setNeedToRevisit(!RE.isEmpty());
        // events that access the same var with event 'a'.
        List<SharedEvent> sameLocationA;
        switch (a.getAType()) {
          case READ:
            sameLocationA = G0.getSameLocationAs(a);
            for (SharedEvent w : sameLocationA) {
              Map<Object, Object> memo = new HashMap<>();
              ObsGraph Gr = G0.deepCopy(memo), coGr;
              // NOTE: After the deep copy, because 'a' is not in Gr,
              //  we use its deep copy 'ap' for later revisit.
              assert memo.containsKey(System.identityHashCode(a))
                      && memo.containsKey(System.identityHashCode(w));
              SharedEvent ap = (SharedEvent) memo.get(System.identityHashCode(a)),
                      wp = (SharedEvent) memo.get(System.identityHashCode(w));
              // There are also some events we need to delete when performing
              // a revisit for a read event, i.e., those in the same node
              // with and behind 'ap'.
              List<SharedEvent> delete =
                      Gr.getDelete(REVISIT_TYPE.READ, ap, wp);
              // Maximality should always hold when revisiting a read.
              Gr.removeDelete(delete, ap);
              // FIXME: The next revisit cannot be performed until the node become complete.
              Gr.setNeedToRevisit(false);
              Pair<ObsGraph, ObsGraph> GrAndcoGr =
                      setReadFrom(Gr, ap, wp, REVISIT_TYPE.READ, precision);

              Gr = GrAndcoGr.getFirstNotNull(); // Gr must not be null.
              // NOTE: Use g rather than G0 as the most original source of Gr.
              // Because Gr may be the copy of g, too. This is used
              // for debugging, specifically, for outputting right data
              // that will be stored in output/revisitDot.json.
              handleRevisitResult(result, RG, g, Gr, chState);
              coGr = GrAndcoGr.getSecond(); // coGr may be null.
              handleRevisitResult(result, RG, g, coGr, chState);
            }
            break;

          case WRITE:
            sameLocationA = G0.getSameLocationAs(a);
            for (SharedEvent r : sameLocationA) {
              Map<Object, Object> memo = new HashMap<>(); ObsGraph Gw = G0.deepCopy(memo), coGw;
              assert memo.containsKey(System.identityHashCode(a))
                      && memo.containsKey(System.identityHashCode(r));
              SharedEvent ap = (SharedEvent) memo.get(System.identityHashCode(a)),
                      rp = (SharedEvent) memo.get(System.identityHashCode(r));

              List<SharedEvent> delete =
                      Gw.getDelete(REVISIT_TYPE.WRITE, rp, ap);
              List<SharedEvent> deletePlusR = getDeletePlusR(delete, rp);
              if (!allMaximallyAdded(Gw, deletePlusR, ap, rp))
                continue;
              // Else, the check for maximality passes.
              List<SharedEvent> loseRfRs = Gw.removeDelete(delete, rp);
              handleLoseRfRs(Gw, loseRfRs);
              Pair<ObsGraph, ObsGraph> GwAndcoGw =
                      setReadFrom(Gw, rp, ap, REVISIT_TYPE.WRITE, precision);

              Gw = GwAndcoGw.getFirstNotNull(); // Gw must not be null.
              handleRevisitResult(result, RG, g, Gw, chState);

              coGw = GwAndcoGw.getSecond(); // coGw may be null.
              handleRevisitResult(result, RG, g, coGw, chState);
            }
            break;

          default:
            //
        }
      }
    }

    return result;
  }

  /**
   * @param result If {@param G} should be transferred, then add it into this.
   * @param RG If {@param G} could be revisited further, then add it into this.
   * @param G0 The most original graph where {@param G} comes from.
   * @param G The result of revisiting.
   * @param chState Used for debugging.
   */
  private void handleRevisitResult(final List<Pair<AbstractState, ObsGraph>> result,
                                   final List<ObsGraph> RG,
                                   final ObsGraph G0,
                                   final ObsGraph G,
                                   final ARGState chState) {
    if (G == null) // If G == null, do nothing.
      return;

    if (consistent(G)) {
      // If G is consistent, add it to the result.
    } else {
      if (G.needToRevisit()) {
        // If G is not consistent but re-visitable, then just add it to the RG,
        // and waiting for the next revisit.
        RG.add(G);
        return;
      } else {
        // If G is not consistent and re-visitable, then we also need to add it
        // to the result, because G may become re-visitable in the future.
        // Otherwise, G will get blocked somewhere.
      }
    }

    // FIXME
    if (G.getRE().stream().anyMatch(e -> e.getAType() == READ
            && e.getReadFrom().getAType() == DUMMY)) {
      RG.add(G);
      return;
    }

    AbstractState pivotState = getPivotState(G);
    // Set 'needToRevisit' to false, whether a further revisit is needed is
    // specified in the future.
    G.setNeedToRevisit(false);
    result.add(Pair.of(pivotState, G));
    // debug.
    G.setCreationState(chState);
    if (isEnableDebug())
      debugActions(G0, G, chState);
  }

  // FIXME: the case where some read events in the last node of G have no rfs.
  private boolean handleLoseRfRs(ObsGraph G, List<SharedEvent> loseRfRs) {
    boolean result = false;
    // List<SharedEvent> loseRfRs = G.getRE().stream().filter(e -> e.isRead()
    //                         && (e.getReadFrom() == null
    //                         || e.getReadFrom().getAType() == DUMMY))
    //         .collect(Collectors.toList());
    if (!loseRfRs.isEmpty()) { // We need to set rf for rs in loseRfRs by continuing to revisit.
      loseRfRs.forEach(r -> {
        if (r.getReadFrom() == null) {
          SharedEvent dummyWrite = new SharedEvent(null,
                  DUMMY,
                  new DummyCFAEdge(null, null));
          // G.getDummyNode().addEvents(List.of(dummyWrite));
          G.getDummyNode().getEvents().add(dummyWrite);
          G.getDummyNode().getWs().add(dummyWrite);
          dummyWrite.setInNode(G.getDummyNode());
          r.setReadFrom(dummyWrite);
        }
      });
      result = true;
    }
    return result;
  }

  /**
   * By letting {@param r} read from {@param w}, we get a new rf.
   * NOTE: When {@param w} contains indeterminacy, we may get two graphs as the
   *  result, one of them is G and the other is coG.
   * @param G The graph that {@param r} and {@param w} locate in.
   * @param r The read event which will read from {@param w}.
   * @param w The write event that will be read by {@param r}.
   * @param type the type of revisit.
   * @param precision //
   * @return a pair of G and coG.
   * @implNote we deduce new fr after setting the new rf above.
   */
  private Pair<ObsGraph, ObsGraph> setReadFrom(ObsGraph G,
                                               SharedEvent r,
                                               SharedEvent w,
                                               REVISIT_TYPE type,
                                               Precision precision) {
    assert r.getReadFrom() != null :
            "Revisiting requires that the read event must read from some value.";
    // Remove the old rf.
    r.removeReadFrom();
    // Clear the old fr.
    G.clearFR();

    // When setting read-from relation, we may get a new graph because of the indeterminacy.
    ObsGraph coG = null;
    SharedEvent corp = null;
    Pair<Boolean, Boolean> evaluation = null;
    try {
      // evaluation = <A, B>
      // A = true if it leads to conflict that r reads from w.
      // B = true if the w is an indeterminate assignment.
      evaluation = CSHandler.handleAssumeStatement(r, w, precision);
    } catch (UnsupportedCodeException e) {
      //
    }

    assert evaluation != null;
    boolean hasConflict = evaluation.getFirstNotNull(),
            hasIndeterminacy = evaluation.getSecondNotNull();
    // hasIndeterminacy => !hasConflict
    if (hasIndeterminacy) {
      // We will get a new graph because r reads from an indeterminate value.
      Map<Object, Object> memo = new HashMap<>();
      coG = G.deepCopy(memo);
      assert memo.containsKey(System.identityHashCode(r))
              && memo.containsKey(System.identityHashCode(w));
      SharedEvent rp = (SharedEvent) memo.get(System.identityHashCode(r)),
              wp = (SharedEvent) memo.get(System.identityHashCode(w));

      corp = coG.changeAssumeEdge(rp);
      corp.setReadFrom(wp);

      // Handle G.
      r.setReadFrom(w);
    }

    else if (hasConflict) {
      // We don't need to create a new graph despite the conflict. Instead, we
      // replace r with the event co-r.
      SharedEvent cor = G.changeAssumeEdge(r);
      cor.setReadFrom(w);
    } else { // No conflict.
      r.setReadFrom(w);
    }

    G.deduceFromRead();
    if (coG != null)
      coG.deduceFromRead();

    // Debug. If type == REVISIT_TYPE.READ, then r and corp should be the last-handled
    // events in their nodes. Otherwise, w should be the last-handled event in its
    // node.
    if (type == REVISIT_TYPE.READ) {
      r.getInNode().checkLHE(r);
      if (corp != null)
        corp.getInNode().checkLHE(corp);
    } else {
      w.getInNode().checkLHE(w);
    }

    return Pair.of(G, coG);
  }

  private AbstractState getPivotState(ObsGraph G) {
    // TODO: try not going back to the first state.
    OGNode targetNode;
    // Use the preState of the first node, for the simplicity.
    targetNode = G.getNodes().get(0);
    G.setLastNode(null);
    // Before returning, clear the trace order and modify the order for nodes that
    // trace after the target node. At the same time, set them invisible in the graph.
    for (OGNode next = targetNode; next != null;) {
      OGNode tmp = next.getTrBefore();
      // Trace order.
      if (next.getTrBefore() != null)
        next.removeTrBefore();
      if (next.getTrAfter() != null)
        next.removeTrAfter();
//            next.setTrAfter(null);
//            next.setTrBefore(null);

      // NOTE: don't remove mo relations here.
      next.getHappenBefore().forEach(next::removeHappenBefore);
      next.getHappenAfter().forEach(next::removeHappenAfter);

      // Set the node invisible.
      next.setInGraph(false);
      G.setTraceLen(G.getTraceLen() - 1);
      next = tmp;
    }

    assert targetNode != null && targetNode.getPreState() != null;

    G.setInitialCurrentNodeTable(targetNode.getPreState());
    // Reset the cachedAssumeEdges.
    G.resetCachedAssumeEdge();

    return targetNode.getPreState();
  }


  /**
   * Checking whether all events in {@param deletePlusR} are added maximally.
   *
   * @param deletePlusR events need to check.
   */
  private boolean allMaximallyAdded(
          ObsGraph G,
          List<SharedEvent> deletePlusR,
          SharedEvent w,
          SharedEvent r) {
    for (SharedEvent e : deletePlusR) {
      List<SharedEvent> previous = G.getPrevious(e, w);
      // e is maximally added?
      if (!maximallyAdded(G, previous, e, w, r))
        return false;
    }
    return true;
  }

  /**
   * Checking whether e is added maximally by traversing all events in
   * {@param previous}.
   *
   * @param G
   * @param previous the events must be kept after the revisit?
   * @param w        The event that the revisit performed on.
   * @param r        The event that reads from w after the revisit.
   */
  private boolean maximallyAdded(ObsGraph G,
                                 List<SharedEvent> previous,
                                 SharedEvent e,
                                 SharedEvent w,
                                 SharedEvent r) {
    boolean eIsWrite = e.getAType() == WRITE;
    SharedEvent ep = eIsWrite ? e : e.getReadFrom();
    assert ep != null : "Cannot find ep for event: " + e;

    if (!previous.contains(ep)) {
      // e' \not\in previous.
      return false;
    }

    for (SharedEvent epmo : ep.getAllMoBefore()) {
      // FIXME: if epmo locates in w.inNode or r.inNode?
      boolean cond = previous.contains(epmo)
              && !Objects.equals(epmo.getInNode(), w.getInNode());
      if (cond) {
        // ep \in previous /\ \exists epmo \in previous s.t. <ep, epmo>
        // \in G.mo /\ ep, epmo not in the same block (FIXME: with w or r?)
        return false;
      }
    }

    for (int i = previous.size() - 1; i >= 0; i--) {
      // Reverse search.
      SharedEvent ee = previous.get(i);

      if (ee.isRead() && eIsWrite && (ee.getReadFrom() == e)) {
        // \exists r = ee \in previous /\ G.rf(r) = e.
        return false;
      }
    }
    return true;
  }

  /**
   * Ref: <a herf="https://www.geeksforgeeks.org/detect-cycle-in-a-graph/"></a>
   * @return true if there is no any cycle in g.
   */
  // FIXME
  private boolean consistent(ObsGraph G) {
    int nodeNum = G.getNodes().size();
    if (nodeNum <= 0) return true;
    boolean[] visited = new boolean[nodeNum];
    boolean[] inTrace = new boolean[nodeNum];
    for (int i = 0; i < nodeNum; i++) {
      if (isCyclic(G, i, visited, inTrace))
        return false;
    }
    return true;
  }

  private boolean isCyclic(ObsGraph g, int i, boolean[] visited, boolean[] inTrace) {
    // mark g.getNodes().get(i) as visited and in trace.
    Preconditions.checkState(i >= 0 &&
            i < visited.length && i < inTrace.length);
    visited[i] = true;
    inTrace[i] = true;

    OGNode nodei = g.getNodes().get(i);
    Set<Integer> neighbours = new HashSet<>();
    List<OGNode> nodes = g.getNodes();
    for (OGNode suc : nodei.getSuccessors()) {
      if (nodes.contains(suc))
        neighbours.add(nodes.indexOf(suc));
    }
    for (OGNode rbn : nodei.getReadBy()) {
      if (nodes.contains(rbn))
        neighbours.add(nodes.indexOf(rbn));
    }
    for (OGNode frn : nodei.getFromRead()) {
      if (nodes.contains(frn))
        neighbours.add(nodes.indexOf(frn));
    }
    for (Integer n : neighbours) {
      if (inTrace[n]) {
        return true;
      }
      else if (!visited[n] && isCyclic(g, n, visited, inTrace)) {
        return true;
      }
    }
    inTrace[i] = false;

    return false;
  }

  // FIXME: we should consider all events that locate in the same node with r?
  private List<SharedEvent> getDeletePlusR(List<SharedEvent> delete, SharedEvent r) {
    List<SharedEvent> deletePlusR = new ArrayList<>(delete);
//        deletePlusR.addAll(r.getInNode().getEvents().stream()
//                .filter(r::inSameEdgeWith).collect(Collectors.toList()));
    for (SharedEvent e : r.getInNode().getEvents()) {
      if (!delete.contains(e))
        deletePlusR.add(e);
    }

    return deletePlusR;
  }

  // Debug.
  private void debugActions(ObsGraph G, ObsGraph Gp, ARGState chState) {
    // Gp is produced by revisiting G.
    Map<Integer, Map<Integer, List<String>>>  revisitOGMap =
            GlobalInfo.getInstance().getOgInfo().getRevisitOGMap();
    Map<Integer, List<String>> revisitOgsMap =
            revisitOGMap.computeIfAbsent(chState.getStateId(), k -> new HashMap<>());
    List<String> revisitOgs =
            revisitOgsMap.computeIfAbsent(System.identityHashCode(G), k -> new ArrayList<>());
    String revisitOg = DebugAndTest.getDotStr(Gp);
    revisitOgs.add(revisitOg);
  }

  // Debug.
  private int p(ObsGraph g) {
    return DebugAndTest.print(g);
  }
}