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

    // FIXME
    // thread -> <assumeEdge, loopDepthHash, pathLengthHash>
    private final Map<String, List<Triple<CFAEdge, Integer, Integer>>>
            cachedAssumeEdges = new HashMap<>();

    // FIXME
    // Recording the next assumption edge we should visit.
    private final Map<String, Integer> assumeEdgeTable = new HashMap<>();

    // Based on object's memory address, so this should be different for every graph object.
    private final int identityHash = System.identityHashCode(this);

    // Debug: indicating where the graph is created.
    ARGState creationState = null;

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
     * Judge whether the graph contains the node, this requires the correct
     * implementation of {@link OGNode#equals(Object)}.
     */
    public boolean contains(OGNode node) {
        return nodes.contains(node);
    }

    public void addNode(OGNode node) {
        assert !nodes.contains(node) :
                "Trying to add a node that has been added before!";
        nodes.add(node);
    }

    public void removeNode(OGNode node) {
        assert nodes.contains(node) :
                "Trying to remove a node that not in the graph!";
        nodes.remove(node);
    }

    /**
     * @return a list of the events that we need to revisit.
     * @implNote find re-visitable events in the last node.
     */
    public List<SharedEvent> getRE() {
        assert lastNode != null :
                "Try to revisit a graph which has no last node specified.";
        return lastNode.getRE();
    }

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
     * FIXME
     * @param a Based on this we find the events that access the same var with it.
     * @return A restrictive list of the events that access the same var with {@param a}.
     * NOTE: this method matters, if we don't return all but a part of the target
     * events that has the same location with {@param a}.
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
            int aIndex = aNode.getEvents().indexOf(a);
            exclusiveReadEvents = aNode.getRs().stream()
                    .filter(r -> aNode.getEvents().indexOf(r) > aIndex)
                    .collect(Collectors.toList());
            // Storing rfs for exclusive read events.
            removedRfs = exclusiveReadEvents.stream().map(exr -> Pair.of(exr,
                    exr.getReadFrom())).collect(Collectors.toList());
            // Remove rfs for exclusive read events.
            exclusiveReadEvents.forEach(SharedEvent::removeReadFrom);
        }

        for (int i = nodes.indexOf(a.getInNode()) - 1; i >= 0; i--) {
            OGNode nodei = nodes.get(i);
            // FIXME: how to handle the nodes not in the graph?
//            if (!nodei.isInGraph()) continue;

            if (a.isRead()) {
                // FIXME: could we skip some nodes.
                if (nodei.getWs().stream().noneMatch(w -> w.accessSameVarWith(a)))
                    continue;
//                if (i == nodes.indexOf(arf.getInNode())) continue;
                if (porfPres.isEmpty()) {
                    if (exclusivePorf(nodei, aNode, a))
                        porfPres.add(nodei);
                    if (nodes.indexOf(arfNode) == i)
                        continue;
                }
                else {
                    if (exclusivePorf(nodei, aNode, a)) {
                        List<OGNode> coveredPorfPres = porfPres.stream()
                                .filter(pre -> OGRevisitor.porf(nodei, pre))
                                .collect(Collectors.toList());
//                        porfPres.removeAll(coveredPorfPres);
                        porfPres.add(nodei);
                        // If nodei porf some nodes in porfPres, then we cannot use the
                        // write event comes from it.
                        if (!coveredPorfPres.isEmpty())
                            continue;
                    }
                    if (nodes.indexOf(arfNode) == i)
                        continue;
                }

                // The write event in nodei should be considered.
                for (SharedEvent w : nodei.getWs()) {
                    if (w.accessSameVarWith(a)) {
                        result.add(w);
                        break;
                    }
                }
            } else {
                // WRITE
                for (SharedEvent r : nodei.getRs()) {
                    if (r.accessSameVarWith(a) && !this.porf(r, a)) {
                        result.add(r);
                        break;
                    }
                }
            }
        }

        if (a.isRead()) { // Restoring the rfs removed before if necessary.
            if (removedRfs != null && !removedRfs.isEmpty()) {
                removedRfs.forEach(rfpair -> {
                    SharedEvent rf = rfpair.getSecondNotNull(),
                            r = rfpair.getFirstNotNull();
                    r.setReadFrom(rf);
                });
            }
        }

        return result;
    }

    // FIXME
    // Compute whether nodei still porf aNode in the case where event 'a' reads from some
    // write event in nodei, but we will ignore the rf relation.
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
//            return a.getAType() == READ || b.getAType() == WRITE;
        }
        // Case 2: A != B.
        // If A porf B, then we think a porf b too.
        return OGRevisitor.porf(A, B);
    }

    /**
     * @param delete events to remove.
     * @param rp the upper bound of the deleted events (not including {@param rp}).
     * FIXME: remove cached assumption edges here?
     */
     public void removeDelete(List<SharedEvent> delete, SharedEvent rp) {
         OGNode rpn = rp.getInNode();
         // In rpn, some events may get delete, and we need to remove corresponding
         // edges, too.
         Set<CFAEdge> toRemove = new HashSet<>();
         // remove relations before removing nodes.
         delete.forEach(e -> {
             // For e.
             e.removeAllRelations();

             // For e.inNode.
             OGNode en = e.getInNode();
             if (!Objects.equals(rpn, en)) {
                 en.removeAllRelations();
                 // Remove node en.
                 nodes.remove(en);
             } else {
                 // Don't remove node rpn, just remove event e.
                 rpn.removeEvent(e);
                 toRemove.add(e.getInEdge());
             }
         });

         rpn.removeEdges(toRemove);

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
                 Preconditions.checkState(wNode.getReadBy().contains(node)
                         && node.getReadFrom().contains(wNode));
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

     // FIXME
    public boolean lessThanOrEqual(SharedEvent e1, SharedEvent e2) {
        Preconditions.checkArgument(e1 != null && e2 != null);
        return e1 == e2 || this.lessThan(e1, e2);
    }

    // FIXME
    public boolean lessThan(SharedEvent e1, SharedEvent e2) {
        // FIXME: define '<'.
        // Judge whether <e1, e2> in <.
        // Assume:
        //      | r1 |
        //      | r2 |
        //      | w1 |
        // r1 < w1 && r2 < w1.
        // r1 and r2 are unordered => both r1 < r2 && r2 < r1?
        // Assume when choose r1 as e1, and r2 as e2, then e1 < e2.
        // When choose r2 as e1, and r1 as e1, then e1 < e2.
        // Same for the case in which both e1 and e2 are write.
        OGNode en1 = e1.getInNode(), en2 = e2.getInNode();
        if (en1 == en2) {
            // FIXME
            // e1 and e2 in the same node.
//            if (e1.getAType() == e2.getAType()) {
//                return true;
//            }
//            return e1.getAType() == READ;
            int e1EdgeIndex = en1.getBlockEdges().indexOf(e1.getInEdge()),
                     e2EdgeIndex = en2.getBlockEdges().indexOf(e2.getInEdge());
            if (e1EdgeIndex < e2EdgeIndex) {
                return true;
            }
            else if (e1EdgeIndex == e2EdgeIndex) {
                return en1.getEvents().indexOf(e1) < en2.getEvents().indexOf(e2);
            }
            return false;
        } else {
            int en1idx = this.nodes.indexOf(en1), en2idx = this.nodes.indexOf(en2);
            return en1idx < en2idx;
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
        // FIXME: this may change in future.
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
            String sucThrd = suc.getInThread();
            nodeTable.put(sucThrd, suc);
        }
    }

    public void addVisitedAssumeEdge(String curThread,
            CFAEdge edge,
            OGPORState chOgState) {
        // Add assume edges to the cache when we meet them at the first time.
        if (!cachedAssumeEdges.containsKey(curThread)) {
            cachedAssumeEdges.put(curThread, new ArrayList<>());
        }

        cachedAssumeEdges.get(curThread).add(
                Triple.of(edge, chOgState.getLoopDepth(), chOgState.getNum()));

       if (assumeEdgeTable.containsKey(curThread)) {
           assumeEdgeTable.computeIfPresent(curThread, (k, v) -> v + 1);
       } else {
           assumeEdgeTable.put(curThread, 0);
       }
    }

    public boolean matchCachedEdge(String curThread, CFAEdge edge, OGPORState chOgState) {
        // Check whether the edge is equals to the storing edge of current thread.
        if (cachedAssumeEdges.containsKey(curThread)) {
            List<Triple<CFAEdge, Integer, Integer>> curThreadAssumeEdgeList =
                    cachedAssumeEdges.get(curThread);
            if (curThreadAssumeEdgeList != null) {
                assert assumeEdgeTable.containsKey(curThread);
                int i = assumeEdgeTable.get(curThread);
                CFAEdge assumeEdge =
                        curThreadAssumeEdgeList.get(i).getFirst();
                assert curThreadAssumeEdgeList.get(i).getSecond() != null
                        && curThreadAssumeEdgeList.get(i).getThird() != null;
                int loopDepth = curThreadAssumeEdgeList.get(i).getSecond().intValue();

                if (Objects.equals(edge, assumeEdge)
                        && loopDepth == chOgState.getLoopDepth()) {
                    // Update the assumeEdgeTable.
                    assumeEdgeTable.put(curThread, i + 1);
                    return true;
                }
            }
        }
        return false;
    }

    // FIXME
    public void resetCachedAssumeEdge() {
        // Adjust the cachedAssumeEdges after revisiting.
        for (String t : assumeEdgeTable.keySet()) {
            assumeEdgeTable.put(t, 0);
        }
    }

    // FIXME
    public void removeAssumeEdges(List<SharedEvent> delete, SharedEvent r) {
        // Remove the corresponding cached assume edges after having removed the events in
        // revisiting.
        if (cachedAssumeEdges.isEmpty()) return;
        Map<String, Integer> removeStartPoint = new HashMap<>();
        // Handle r.
        OGNode rNode = r.getInNode();
        OGPORState rOgporState = AbstractStates.extractStateByType(rNode.getPreState(),
                OGPORState.class);
        assert rOgporState != null && rOgporState.getInThread() != null;
        int rNodeStartNum = rOgporState.getNum();
        assert rNode.getBlockEdges().contains(r.getInEdge());
        removeStartPoint.put(rOgporState.getInThread(),
                rNodeStartNum + rNode.getBlockEdges().indexOf(r.getInEdge()));

        // Handle delete. FIXME: more effective way.
        List<OGNode> handledNodes = new ArrayList<>();
        for (SharedEvent e : delete) {
            OGNode node = e.getInNode();
            if (handledNodes.contains(node))
                continue;
            OGPORState ogporState =
                    AbstractStates.extractStateByType(node.getPreState(), OGPORState.class);
            assert ogporState != null && ogporState.getInThread() != null;
            int num = ogporState.getNum();
            String thrd = ogporState.getInThread();
            if (!removeStartPoint.containsKey(thrd)) {
                removeStartPoint.put(thrd, num);
            } else {
                if (num < removeStartPoint.get(thrd))
                    removeStartPoint.put(thrd, num);
            }
            handledNodes.add(node);
        }

        // Remove assume edges.
        for (String t : cachedAssumeEdges.keySet()) {
            if (!removeStartPoint.containsKey(t))
                continue;

            int removeStartNum = removeStartPoint.get(t);
            List<Triple<CFAEdge, Integer, Integer>> assumeEdgeList =
                    cachedAssumeEdges.get(t), remove = new ArrayList<>();
            for (Triple<CFAEdge, Integer, Integer> triple : assumeEdgeList) {
                int num = triple.getThird().intValue();
                if (num >= removeStartNum) {
                    remove.add(triple);
                }
            }

            assumeEdgeList.removeAll(remove);
        }
    }

    public void clearFR() {
        // Remove all from-read relations.
        // FIXME: more effective way to do this?
        for (OGNode node : nodes) {
            if (node.getRs().isEmpty()) continue;
            for (SharedEvent r : node.getRs()) {
                // Handle events first.
                List<SharedEvent> frs = r.getFromRead();
                frs.forEach(fr -> fr.getFromReadBy().remove(r));
                r.getFromRead().clear();

                // Handle nodes.
                OGNode rNode = r.getInNode();
                List<OGNode> frns = rNode.getFromRead();
                frns.forEach(frn -> frn.getFromReadBy().remove(rNode));
                rNode.getFromRead().clear();
            }
        }
    }

    public void deduceFromRead(SharedEvent w, SharedEvent r) {
        // TODO
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
}
