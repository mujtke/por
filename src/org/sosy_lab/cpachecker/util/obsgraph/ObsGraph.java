package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;

public class ObsGraph implements Copier<ObsGraph> {

    private final List<OGNode> nodes = new ArrayList<>();

    /**
     * This variable tracks the node that visible and to-max.
     * NOTE: to-max doesn't means that the node is always added last. I.e., the lastNode
     * isn't always the last one of the {@link #nodes}
     */
    private OGNode lastNode = null;

    private boolean needToRevisit = false;

    // The number of the nodes whose inGraph is set as true.
    private int traceLen = 0;

    // Record the current hold node for each thread: tid -> node.
    private final Map<String, OGNode> nodeTable = new HashMap<>();

    /**
     * This variable is used to record the assumption edges that read from indeterminate
     * assignments. We get loopDepth and pathLength from the {@link OGPORState},
     * specifically, get loopDepth by {@link OGPORState#getLoopDepth()} and pathLength
     * by {@link OGPORState#getPathLen()}.
     * tid -> [<assumeEdge, loopDepth, pathLength>, ... ]
     */
    private final Map<String, List<Triple<CFAEdge, Integer, Integer>>>
            cachedAssumeEdges = new HashMap<>();

    /**
     * Recording the next assumption edge we should visit.
     * tid -> i, 'i' is the index of the item in {@link #cachedAssumeEdges}.get(tid),
     * specifically, it means for thread with tid, next assumption edge we should meet
     * is the ith stored in the {@link #cachedAssumeEdges}.get(tid).
     */
    private final Map<String, Integer> assumeEdgeTable = new HashMap<>();

    // Based on object's memory address, so this should be different for every graph object.
    private final int identityHash = System.identityHashCode(this);

    // Debug: indicating where the graph is created.
    ARGState creationState = null;
    private static boolean enableDebug = false;

    public void enableDebug(boolean pEnableDebug) {
        enableDebug = pEnableDebug;
    }
    public ObsGraph() {
    }

    public int getIdentityHash() { return this.identityHash; }
    public ARGState getCreationState() {
        return creationState;
    }

    public void setCreationState(ARGState creationState) {
        this.creationState = creationState;
    }

    public Map<String, OGNode> getNodeTable() {
        return nodeTable;
    }

    /**
     * Judge whether the graph contains the node. This requires a correct implementation
     * of {@link OGNode#equals(Object)}.
     */
    public boolean contains(OGNode node) {
        return nodes.contains(node);
    }

    public void addNode(OGNode node) {
        assert !nodes.contains(node) && !node.hasBeenAddedToGraph():
                "Trying to add a node that has been added before!";
        nodes.add(node);
        node.setHasBeenAddedToGraph(true);
    }

    public void removeNode(OGNode node) {
        assert nodes.contains(node) :
                "Trying to remove a node that not in the graph!";
        nodes.remove(node);
    }

    /**
     * @return a list of the events that we need to revisit.
     * @implNote we find re-visitable events only in the last node.
     * FIXME: there may be the case where more than one node need to be revisited, but
     *  we only choose current last node?
     */
    public List<SharedEvent> getRE() {
        assert lastNode != null :
                "Try to revisit a graph which has no last node specified.";
        if (!lastNode.shouldRevisit())
            return List.of();
        return lastNode.getRE();
    }

    // Handle this carefully.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ObsGraph) {
            ObsGraph other = (ObsGraph) o;
            return needToRevisit == other.needToRevisit
                    && Objects.equals(nodes, other.nodes)
                    && Objects.equals(lastNode, other.lastNode);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash(nodes, lastNode, needToRevisit);
    }

    public List<OGNode> getNodes() {
        return nodes;
    }

    public OGNode getLastNode() {
        return lastNode;
    }

    public boolean needToRevisit() {
        return needToRevisit;
    }

    public int getTraceLen() {
        return traceLen;
    }

    public void setLastNode(OGNode lastNode) {
        this.lastNode = lastNode;
    }

    public void setNeedToRevisit(boolean needToRevisit) {
        this.needToRevisit = needToRevisit;
    }

    public void setTraceLen(int traceLen) {
        this.traceLen = traceLen;
    }

    @Override
    public ObsGraph deepCopy(Map<Object, Object> memo) {
        if (memo.containsKey(System.identityHashCode(this))) {
            assert memo.get(System.identityHashCode(this)) instanceof ObsGraph;
            return (ObsGraph) memo.get(System.identityHashCode(this));
        }

        ObsGraph nGraph = new ObsGraph();
        // Put the copy into memo.
        memo.put(System.identityHashCode(this), nGraph);
        // Copy nodes.
        this.nodes.forEach(n -> nGraph.nodes.add(n.deepCopy(memo)));
        // Node table.
        this.nodeTable.forEach((k, v) ->
                nGraph.nodeTable.put(k, v == null ? null : v.deepCopy(memo)));
        // CachedAssumeEdges.
        this.cachedAssumeEdges.forEach((k, v) -> {
            List<Triple<CFAEdge, Integer, Integer>> nList = new ArrayList<>();
            for (Triple<CFAEdge, Integer, Integer> triple : v)
                nList.add(Triple.of(triple.getFirst(), triple.getSecond(),
                        triple.getThird()));
            nGraph.cachedAssumeEdges.put(k, nList);
        });
        // AssumeEdgeTable.
        nGraph.assumeEdgeTable.putAll(this.assumeEdgeTable);

        nGraph.lastNode = this.lastNode == null ? null : this.lastNode.deepCopy(memo);
        nGraph.needToRevisit = this.needToRevisit;
        nGraph.traceLen = this.traceLen;

        return nGraph;
    }

    /**
     * @param a Based on this we find the events that access the same var with it.
     * @return A restrictive list of the events that access the same var with {@param a}.
     * NOTE: this method matters, because we don't return all but a part of the target
     *  events that has the same location with {@param a}, which may cause the
     *  incompleteness.
     */
    public List<SharedEvent> getSameLocationAs(SharedEvent a) {

        List<SharedEvent> result = new ArrayList<>(), exclusiveReadEvents = null;
        List<Pair<SharedEvent, SharedEvent>> removedRfs = null;
        List<OGNode> porfPres = new ArrayList<>();
        OGNode aNode = a.getInNode(), arfNode = null;
        if (a.isRead()) {
            SharedEvent arf = a.getReadFrom();
            assert arf != null :
                    "ReadFrom must be not null When revisiting a read event.";
            arfNode = arf.getInNode();
            // When event 'a' is a read, maybe we shouldn't consider rf relations coming
            // from event 'a' and those after it in the aNode.
            exclusiveReadEvents = getExclusiveReadEvents(aNode, a);
            // Storing rfs for exclusive read events.
            removedRfs = getRemovedRfs(exclusiveReadEvents);
            // Remove rfs for exclusive read events.
            exclusiveReadEvents.forEach(SharedEvent::removeReadFrom);

            // FIXME: if arfNode exclusivePorf aNode, then we cannot revisit a?
            if (exclusivePorf(arfNode, aNode, a)) {
                restoreDeleteRfs(removedRfs);
                return result;
            }
        }

        // FIXME: Which nodes we should consider?
        for (int i = nodes.indexOf(a.getInNode()) - 1; i >= 0; i--) {
            OGNode nodei = nodes.get(i);
            if (!nodei.isInGraph()) {
                // FIXME: how to handle the nodes not in the graph?
            }

            if (a.isRead()) {
                if (nodei.getWs().stream().noneMatch(w -> w.accessSameVarWith(a)))
                    continue;
                // Else, nodei has the write events that access to the same var with 'a'.
                if (exclusivePorf(nodei, aNode, a)) {
                    if (porfPres.stream().anyMatch(pre -> porf(nodei, pre))) {
                        // nodei porf some nodes in the porfPres. In this case, event
                        // 'a' cannot read from nodei.
                        porfPres.add(nodei);
                        continue;
                    }
                    porfPres.add(nodei);
                }

                if (nodei == arfNode)
                    continue;

                // Otherwise, the write event in nodei should be considered.
                SharedEvent w = nodei.getWriteToSameVar(a);
                assert w != null :
                        "Error when trying to get same-location write!";
                result.add(w);

            }  // 'a' is a READ.
            else { // 'a' is WRITE.
                // same-location read.
                SharedEvent r = nodei.getReadToSameVar(a);
                if (r == null /*|| this.porf(r, a) */)
                    continue;
                // Else, r accesses to the same var as and !porf 'a'.
                SharedEvent rf = r.getReadFrom();
                assert rf != null;
                OGNode rfNode = rf.getInNode();
                if (!rfNode.isInGraph()) {
                    exclusiveReadEvents = getExclusiveReadEvents(r.getInNode(), r);
                    removedRfs = getRemovedRfs(exclusiveReadEvents);
                    exclusiveReadEvents.forEach(SharedEvent::removeReadFrom);
                    if (exclusivePorf(rfNode, r.getInNode(), r)) {
                        // Cannot revisit event r.
                        restoreDeleteRfs(removedRfs);
                        continue;
                    }
                    restoreDeleteRfs(removedRfs);
                }
                result.add(r);
            }
        }

        if (a.isRead()) { // Restoring the rfs removed before if necessary.
            restoreDeleteRfs(removedRfs);
        }

        return result;
    }

    private List<SharedEvent> getExclusiveReadEvents(OGNode rNode, SharedEvent r) {
        assert rNode.getEvents().contains(r);
        int rIndex = rNode.getEvents().indexOf(r);
        return rNode.getRs().stream()
                .filter(e -> rNode.getEvents().indexOf(e) > rIndex)
                .collect(Collectors.toList());
    }

    private List<Pair<SharedEvent, SharedEvent>> getRemovedRfs(
            List<SharedEvent> exclusiveReadEvents) {
        return exclusiveReadEvents.stream().map(e ->
                Pair.of(e, e.getReadFrom())).collect(Collectors.toList());
    }

    private void restoreDeleteRfs(List<Pair<SharedEvent, SharedEvent>> removedRfs) {
        if (removedRfs != null && !removedRfs.isEmpty()) {
            removedRfs.forEach(rfpair -> {
                SharedEvent r = rfpair.getFirstNotNull(), rf = rfpair.getSecondNotNull();
                r.setReadFrom(rf);
            });
        }
    }

    // Judge whether nodei still porf aNode in the case where event 'a' reads from some
    // write event in nodei, but we ignore the rf relation.
    private boolean exclusivePorf(OGNode nodei,
            OGNode aNode,
            SharedEvent a) {
        SharedEvent arf = a.getReadFrom();
        assert arf != null;
        if (arf.getInNode() != nodei)
            return OGRevisitor.porf(nodei, aNode);
        // Else, arf.inNode == nodei.
        a.removeReadFrom();
        if (OGRevisitor.porf(nodei, aNode)) {
            a.setReadFrom(arf);
            return true;
        }

        a.setReadFrom(arf);
        return false;
    }

    // FIXME: This method may be not correct.
    public boolean porf(SharedEvent a, SharedEvent b) {
        // Assume a in node A, and b in node B.
        OGNode A = a.getInNode(), B = b.getInNode();
        // Case 1: A == B.
        if (A == B) {
            if (a == b) return false;
            int aEdgeIndex = A.getBlockEdges().indexOf(a.getInEdge()),
                    bEdgeIndex = B.getBlockEdges().indexOf(b.getInEdge());
            return aEdgeIndex < bEdgeIndex;
        }
        // Case 2: A != B.
        // If A porf B, then we think a porf b too.
        return OGRevisitor.porf(A, B);
    }

    /**
     * Judge whether (A, B) \in porf^+.
     */
    public boolean porf(OGNode A, OGNode B) {
        assert A != null && B != null;
        return OGRevisitor.porf(A, B);
    }

    /**
     * Judge whether node A should happen before node B in a trace.
     */
    public boolean hb(OGNode A, OGNode B) {
        assert A != null && B != null;
        for (OGNode n : A.getSuccessors()) {
            if (n == B || hb(n, B))
                return true;
        }

        for (OGNode n : A.getReadBy()) {
            if (n == B || hb(n, B))
                return true;
        }

        for (OGNode n : A.getFromRead()) {
            if (n == B || hb(n, B))
                return true;
        }

        return false;
    }

    /**
     * @param delete events to remove.
     * @param rp the upper bound of the deleted events (not including {@param rp}).
     * FIXME: remove cached assumption edges here?
     */
     public void removeDelete(List<SharedEvent> delete, SharedEvent rp) {

         OGNode rpn = rp.getInNode();
         // In rpn, some events may get delete, and we need to remove corresponding edges, too.
         CFAEdge rpe = rp.getInEdge();
         assert rpn.contains(rpe);
         int rpeIndex = rpn.getBlockEdges().indexOf(rpe);
         Set<CFAEdge> edgesToRemove = rpn.getBlockEdges().stream()
                 .filter(edge -> rpn.getBlockEdges().indexOf(edge) > rpeIndex)
                 .collect(Collectors.toSet());
         Set<OGNode> nodesToRemove = new HashSet<>();

         // remove relations before removing nodes.
         delete.forEach(e -> {
             // For e.
             e.removeAllRelations();

             // For e.inNode.
             OGNode en = e.getInNode();
             if (!Objects.equals(rpn, en)) {
                 // Remove node en.
                 nodesToRemove.add(en);
             } else {
                 // Don't remove node rpn, just remove event e.
                 rpn.removeEvent(e);
             }
         });

         rpn.removeEdges(edgesToRemove);
         nodesToRemove.forEach(OGNode::removeAllRelations);
         nodes.removeAll(nodesToRemove);

         // Remove the corresponding cached assumption edges because of the removal of
         // deleted events.
         removeAssumeEdges(delete, rp);
     }

    public void deduceFromRead() {
         // Deduce the fr according the po and rf in the graph.
         // Use adjacency matrix and Floyd Warshall Algorithm to compute the transitive
         // closure of po and rf, i.e., porf+.
         int i, j, k, n = nodes.size();
         boolean[][] porf = new boolean[n][n];
         // Fill in the porf matrix with the original po and rf in the graph.
         for (i = 0; i < n; i++) {
             for (j = 0; j < n; j++) {
                 OGNode nodei = nodes.get(i), nodej = nodes.get(j);
                 if (nodei.getSuccessors().contains(nodej) /* po */
                         || nodei.getReadBy().contains(nodej) /* rf */) {
                     porf[i][j] = true;
                 }
             }
         }
         // Calculate the transitive closure porf+.
         for (k = 0; k < n; k++) {
             for (i = 0; i < n; i++) {
                 for (j = 0; j < n; j++) {
                     // i porf j => i porf j, or there exists k, s.t., i porf k and k
                     // porf j.
                     porf[i][j] = porf[i][j] || (porf[i][k] && porf[k][j]);
                 }
             }
         }

         for (OGNode node : nodes) {
             if (node.getRs().isEmpty()) continue;
             for (Iterator<SharedEvent> it = node.getRs().iterator(); it.hasNext();) {
                 SharedEvent r = it.next(), w = r.getReadFrom();
                 // Debug.
                 if (w == null) continue;
                 // Deduce fr caused by r and w.
                 OGNode wNode = w.getInNode();
                 assert wNode.readBy(node) && node.readFrom(wNode);
                 for (int m = 0; m < n; m++) {
                     if (porf[nodes.indexOf(wNode)][m] && m != nodes.indexOf(node)) {
                         // if wNode porf nodes[m] and nodes[m] != node (wNode must
                         // porf node, and a node cannot fr itself.
                         OGNode frn = nodes.get(m);
                         SharedEvent frnw = frn.getWriteToSameVar(r);
                         if (frnw == null) continue;
                         r.setFromRead(frnw);
                     }
                 }
             }
         }
     }

    public boolean lessThanOrEqual(SharedEvent e1, SharedEvent e2) {
        assert e1 != null && e2 != null;
        return e1 == e2 || this.lessThan(e1, e2);
    }

    public boolean lessThan(SharedEvent e1, SharedEvent e2) {
        // Judge whether e1 < e2, and the criterion is the order they are added.
        OGNode en1 = e1.getInNode(), en2 = e2.getInNode();
        if (en1 == en2) { // e1 and e2 in the same node.
            return en1.getEvents().indexOf(e1) < en1.getEvents().indexOf(e2);
        } else { // e1 and e2 in different nodes.
            return nodes.indexOf(en1) < nodes.indexOf(en2);
        }
    }

    /**
     * When r locates in an assumption edge and turns to read from a write event that
     * contradicts r, i.e., r /\ w -> false, we change r to its co-event cor
     * ('co' means conjugate) . If r comes from conditional branch d, then cor should
     * come from !d. Precondition: before we change r to its co-event, the events that
     * are in the same node with and behind r have been removed (including the
     * corresponding edges).
     * @return r's co-event cor.
     * @implNote we don't need to copy r for getting cor, just need to change r's
     * inEdge to r.inEdge's co-edge.
     */
    public SharedEvent changeAssumeEdge(SharedEvent r) {
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        Map<Integer, List<SharedEvent>> edgeVarMap = ogInfo.getEdgeVarMap();
        // Find the corEdge.
        CFAEdge rEdge = r.getInEdge(), corEdge = getCoCFAEdge(rEdge);
        OGNode rNode = r.getInNode();
        assert rNode.contains(rEdge) && !rNode.contains(corEdge);

        // Replace rEdge with corEdge.
        int rEdgeIdx = rNode.getBlockEdges().indexOf(rEdge);
        assert rEdgeIdx == rNode.getBlockEdges().size() - 1 :
                "When changing an assumption edge, it must be the last one in the node!";
        rNode.getBlockEdges().set(rEdgeIdx, corEdge);
        // NOTE: correct r's inEdge to corEdge. After this, the only change is that r's
        //  inEdge become corEdge from rEdge, and we don't need to change anything else.
        r.setInEdge(corEdge);

        return r;
    }

    private CFAEdge getCoCFAEdge(CFAEdge edge) {
        CFAEdge coEdge = null;
        Preconditions.checkArgument(edge instanceof AssumeEdge);
        CFANode pre = edge.getPredecessor();
        Preconditions.checkArgument(pre.getNumLeavingEdges() == 2,
                "AssumeEdge " + edge + " has " + pre.getNumLeavingEdges() + " != 2 " +
                        "leaving edges.");
        for (int i = 0; i < 2; i++) {
            CFAEdge leavingEdge = pre.getLeavingEdge(i);
            if (!edge.equals(leavingEdge)) {
                coEdge = leavingEdge;
                break;
            }
        }
        Preconditions.checkArgument(coEdge != null,
                "Finding corEdge failed: " + edge);

        return coEdge;
    }

    // Set initial current nodes for threads.
    public void setInitialCurrentNodeTable(ARGState initialState) {
        assert !nodes.isEmpty();
        OGNode firstNode = nodes.get(0);
        String curThread = firstNode.getInThread();
        nodeTable.put(curThread, firstNode);

        // set other threads' current nodes as null.
        // FIXME: this may change if we don't choose the earliest state as the
        //  pivotState all the time.
        for (String thd : nodeTable.keySet()) {
            if (!Objects.equals(thd, curThread)) {
                nodeTable.put(thd, null);
            }
        }
    }

    public OGNode getCurrentNode(String curThread) {
        return nodeTable.get(curThread);
    }

    public void updateCurrentNode(String curThread, OGNode node) {
        nodeTable.put(curThread, node);
    }


    // Update correct current nodes for threads.
    public void updateCurrentNodeTable(String curThread, OGNode node) {
        assert curThread != null && node != null;
        // If the node has no successor for curThread, then we set value null for the
        // current thread. Else, we will set its value as some node below.
        nodeTable.put(curThread, null);
        for (OGNode suc : node.getSuccessors()) {
            String sucThd = suc.getInThread();
            nodeTable.put(sucThd, suc);
        }
    }

    public void addVisitedAssumeEdge(String curThd,
            CFAEdge edge,
            OGPORState chOgState) {
        // Add assume edges to the cache when we meet them at the first time.
        List<Triple<CFAEdge, Integer, Integer>> tripleList =
                cachedAssumeEdges.computeIfAbsent(curThd, k -> new ArrayList<>());
        tripleList.add(Triple.of(edge, chOgState.getLoopDepth(), chOgState.getPathLen()));

       if (assumeEdgeTable.containsKey(curThd)) {
           assumeEdgeTable.computeIfPresent(curThd, (k, v) -> v + 1);
       } else {
           assumeEdgeTable.put(curThd, 0);
       }
    }

    public boolean cachedEdgeMatch(String curThread, CFAEdge edge, OGPORState chOgState) {
        // Check whether the edge is equals to the storing edge of current thread.
        if (cachedAssumeEdges.containsKey(curThread)) {
            List<Triple<CFAEdge, Integer, Integer>> curThdAssumeEdgeList =
                    cachedAssumeEdges.get(curThread);
            assert curThdAssumeEdgeList != null;
            if (!curThdAssumeEdgeList.isEmpty()) {
                assert assumeEdgeTable.containsKey(curThread);
                int i = assumeEdgeTable.get(curThread);
                try {
                    Triple<CFAEdge, Integer, Integer> triple = curThdAssumeEdgeList.get(i);
                    assert triple.getFirst() != null
                            && triple.getSecond() != null
                            && triple.getThird() != null;
                    CFAEdge assumeEdge = triple.getFirst();
                    int loopDepth = triple.getSecond();

                    if (Objects.equals(edge, assumeEdge)
                            && loopDepth == chOgState.getLoopDepth()) {
                        // Update the assumeEdgeTable.
                        assumeEdgeTable.put(curThread, i + 1);
                        return true;
                    }
                } catch (IndexOutOfBoundsException e) {
                    // Not matched.
                }
            }
        }

        return false;
    }

    /**
     * Reset the {@link #assumeEdgeTable}. For tid, set the next assumption edge that we
     * will meet to the first one in {@link #cachedAssumeEdges}.get(tid).
     */
    public void resetCachedAssumeEdge() {
        for (String t : assumeEdgeTable.keySet()) {
            assumeEdgeTable.put(t, 0);
        }
    }

    /**
     * After removing the events in {@param delete}, we also should remove the
     * corresponding cached assume edges. Specifically, we should remove those after
     * r and deleted events. We use pathLength as the criteria whether an assumption
     * edge is after r or an event in {@param delete}.
     * @param delete The events we removed during the revisit.
     * @param r the upper bound of the deleted events (not include r).
     */
    public void removeAssumeEdges(List<SharedEvent> delete, SharedEvent r) {
        if (cachedAssumeEdges.isEmpty())
            return;
        Map<String, Integer> startPointForRemove = new HashMap<>();
        // Handle r.
        OGNode rNode = r.getInNode();
        OGPORState rOgporState = AbstractStates.extractStateByType(rNode.getPreState(),
                OGPORState.class);
        assert rOgporState != null && rOgporState.getInThread() != null;
        int rNodeStartPathLen = rOgporState.getPathLen();
        assert rNode.contains(r.getInEdge());
        startPointForRemove.put(rOgporState.getInThread(),
                rNodeStartPathLen + rNode.getBlockEdges().indexOf(r.getInEdge()));

        // Handle the events in delete. FIXME: more effective way.
        List<OGNode> deleteNodes = delete.stream().map(SharedEvent::getInNode)
                .collect(Collectors.toList());
        for (OGNode node : deleteNodes) {
            OGPORState ogporState =
                    AbstractStates.extractStateByType(node.getPreState(), OGPORState.class);
            assert ogporState != null && ogporState.getInThread() != null;
            int pathLen = ogporState.getPathLen();
            String thd = ogporState.getInThread();
            // For a thread, we need only a minimal start num.
            if (!startPointForRemove.containsKey(thd)
                    || pathLen < startPointForRemove.get(thd))
                startPointForRemove.put(thd, pathLen);
        }

        // Remove assume edges.
        for (String t : cachedAssumeEdges.keySet()) {
            if (!startPointForRemove.containsKey(t))
                continue;

            int tStartPathLen = startPointForRemove.get(t);
            List<Triple<CFAEdge, Integer, Integer>> assumeEdgeList =
                    cachedAssumeEdges.get(t), remove = new ArrayList<>();
            for (Triple<CFAEdge, Integer, Integer> triple : assumeEdgeList) {
                assert triple.getThird() != null;
                int pathLen = triple.getThird();
                if (pathLen > tStartPathLen) { // We don't remove r.inEdge, so it cannot be
                    // equal.
                    remove.add(triple);
                }
            }

            assumeEdgeList.removeAll(remove);
        }
    }

    public void clearFR() {
        // Remove all from-read relations.
        // TODO: a more effective way to do this?
        for (OGNode node : nodes) {
            if (node.getRs().isEmpty()) continue;
            for (SharedEvent r : node.getRs()) {
                List<SharedEvent> toRemove = new ArrayList<>(r.getFromRead());
                toRemove.forEach(r::removeFromRead);
            }
        }
    }

    /**
     * @param n provide write events that we used to set rf relations for events in
     * {@param rFlag} and update/set mo relations for events in {@param wFlag}.
     * @param rFlag the set of read events that we need to set rf relations for.
     * @param wFlag the set of write events that we need to update/set mo relations for.
     * @implNote For mo, if we keep mo relations after revisiting, then what we do here
     * is to update it. Otherwise, we set it here.
     */
    public void setRelations(OGNode n, Set<SharedEvent> rFlag, Set<SharedEvent> wFlag) {
        for (SharedEvent nw : n.getWs()) {
            Set<SharedEvent> toRemove = new HashSet<>();
            // Rf.
            for (SharedEvent r : rFlag) {
                if (r.accessSameVarWith(nw)) {
                    // let r read from w.
                    r.setReadFrom(nw);
                    toRemove.add(r);
                }
            }
            rFlag.removeAll(toRemove);
            toRemove.clear();

            // Mo.
            // NOTE: we don't remove previous mo relations.
            // We use rules follow:
            // rule1:
            //      Old mo: nw --> nwmb
            //      New mo: nw --> w --> nwmb
            // rule2:
            //      Old mo: nw --> null
            //      new mo: nw --> w
            //
            // rule3: (nw, w) have been in the mo.
            //      Old mo: nw --> w
            //      new mo: nw --> w
            for (SharedEvent w : wFlag) {
                if (w.accessSameVarWith(nw)) {
                    SharedEvent nwmb = nw.getMoBefore();
                    if (nwmb == null) {      // nwmb == null
                        w.setMoAfter(nw);    // Add new mo for j.
                    } else if (nwmb != w) {
                        nwmb.setMoAfter(w);
                        w.setMoAfter(nw);
                    }
                    toRemove.add(w);
                }
            }
            wFlag.removeAll(toRemove);
            toRemove.clear();
        }
    }

    /**
     * When visiting a node, we add rf relations for the events that behind the
     * last-handled event of the node, update mo relations for all write events, and
     * update po for the node.
     * @param isTerminated Whether the node has terminated.
     */
    public void visitNode(OGNode node, boolean isTerminated) {
        Set<SharedEvent> rFlag = new HashSet<>(), wFlag = new HashSet<>();
        node.getRsNeedToVisit(rFlag);
        node.getWsNeedToVisit(wFlag);
        // Indicate whether we have found the predecessor(po) of the node.
        boolean preFlag = node.getPredecessor() != null;
        // Till now, the node hasn't been added to the graph, so we choose the last
        // node in the trace as the start of backtracking.
        OGNode n = lastNode;
        // Backtracking along with the trace.
        while (n != null) {
            // FIXME: is there the case where the node isn't in the graph?
            assert n.isInGraph() :
                    "Trying to visit a node not in graph when backtracking!";
            if (!preFlag && n.isPredecessorOf(node)) {
                n.setSuccessor(node);
                node.setPredecessor(n);
                preFlag = true;
            }
            if (rFlag.isEmpty() && wFlag.isEmpty()) {
                // All events in rFlag and wFlag have been handled.
                if (preFlag) {
                    // If we have found the predecessor of the node, then stop backtracking.
                    break;
                } else {
                    // Else, continue to find predecessor.
                    n = n.getTrAfter();
                    continue;
                }
            }

            setRelations(n, rFlag, wFlag);
            assert !enableDebug || !DebugAndTest.acyclicMo(this) :
                    "Circle from mo found!";
            n = n.getTrAfter();
        }

        if (!contains(node)) {
            // Add the node to the graph if we visit it the first time.
            addNode(node);
        }

        // Update info for the node and graph if the node has terminated.
        if (isTerminated) {
            node.setInGraph(true);
            if (lastNode != null) {
                lastNode.setTrBefore(node);
                node.setTrAfter(lastNode);
            }
            setLastNode(node);
            setTraceLen(traceLen + 1);
        }
    }

    /**
     * @return the re-visitable node.
     * FIXME:
     * 1) should the number of the re-visitable nodes be 1? Otherwise, there is
     * no or more than one re-visitable node in the graph. Is that allowed?
     * 2) is the returned node to-max? If so, the node is complete and we have add
     * relations for it.
     */
    public OGNode getRevisitNode() {
        List<OGNode> nodesToRevisit =
                nodes.stream().filter(OGNode::shouldRevisit).collect(Collectors.toList());
        assert nodesToRevisit.size() == 1 : "More than one nodes need to revisit.";
        OGNode result =nodesToRevisit.get(0);
//        assert result == lastNode : "The re-visitable node is not the to-max one.";
        return result;
    }

    /**
     * Get previous for e.
     * FIXME: how to get correct 'previous'?
     */
    public List<SharedEvent> getPrevious(SharedEvent e, SharedEvent w) {
        List<SharedEvent> result = new ArrayList<>();
        for (OGNode n : nodes) {
            // e.getInNode() must be added before w.getInNode()
            for (SharedEvent ep : n.getEvents()) {
                if (lessThanOrEqual(ep, e) || porf(ep, w))
                    result.add(ep);
            }
        }

        return result;
    }
}