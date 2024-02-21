package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;

import java.util.*;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor.setRelation;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.READ;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.WRITE;

public class ObsGraph implements Copier<ObsGraph> {

    private final List<OGNode> nodes = new ArrayList<>();

    private OGNode lastNode = null;

    private boolean needToRevisit = false;

    private int traceLen;

    private final List<SharedEvent> RE;

    private final Map<String, OGNode> nodeTable = new HashMap<>();

    // thread -> <assumeEdge, loopDepthHash, pathLengthHash>
    private final Map<String, List<Triple<CFAEdge, Integer, Integer>>>
            cachedAssumeEdges = new HashMap<>();

    // Recording the next assumption edge we should visit.
    private final Map<String, Integer> assumeEdgeTable = new HashMap<>();

    public ObsGraph() {
        traceLen = 0;
        RE = new ArrayList<>();
    }

    public Map<String, OGNode> getNodeTable() {
        return nodeTable;
    }

    public List<SharedEvent> getRE() {
        if (lastNode != null) {
            List<SharedEvent> events = lastNode.getEvents();
            SharedEvent lastHandledE = lastNode.getLastHandledEvent();
            if (!RE.isEmpty()) RE.clear();
            if (lastHandledE == null) {
                RE.addAll(events);
            } else {
                for (int i = lastNode.getLheIndex() + 1; i < events.size(); i++) {
                    RE.add(events.get(i));
                }
            }
        }
        // FIXME
        else if (!RE.isEmpty()) {
            RE.clear();
        }

        return RE;
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

    public boolean isNeedToRevisit() {
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


    /**
     * Given a graph and a OGNode A, judge whether the graph contains a node B that
     * is equal to A.
     * @return A non-negative integer if the graph contains the node, -1 if not.
     * @implNote We assign loopDepth to A temporarily for finding the target node B
     * that is equal to A after we assign a certain loop depth.
     */
    public int contain(OGNode node, int loopDepth) {
        // Set the loopDepth for the node temporarily so that we can judge whether the
        // graph contains the node. Before returning the result, we reset the loopDepth
        // to the default value 0.
        int oldLoopDepth = node.getLoopDepth();
        node.setLoopDepth(loopDepth);
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).equals(node)) {
                node.setLoopDepth(oldLoopDepth);
                return i;
            }
        }

        node.setLoopDepth(oldLoopDepth);
        return -1;
    }

    public int contain(OGNode node, int loopDepth, CFANode cfaNode) {

        int oldLoopDepth = node.getLoopDepth();
        node.setLoopDepth(loopDepth);
        OGNode tmp;
        for (int i = 0; i < nodes.size(); i++) {
            tmp = nodes.get(i);
            if (tmp.equals(node)) {
                node.setLoopDepth(oldLoopDepth);
                return i;
            }
            // If tmp isn't equal to node, then it may be equal to node's some coNode.
            if (!node.getCoNodes().isEmpty()) {
                for (CFANode cfaN : node.getCoNodes().keySet()) {
                    if (!cfaN.equals(cfaNode)) {
                        // We need to skip the cfaNode, because the coNode it
                        // corresponds to conjugates with the node. For example, if e1 and
                        // e2 are two edges stem from the cfaNode, and e2.inNode is the
                        // coNode of e1.inNode, then when we judge if e1.inNode is in
                        // the graph, we need to skip the e2.inNode. Because if a graph
                        // could be transferred along the e1, then it wouldn't be
                        // along the e2.
                        if (tmp.equals(node.getCoNodes().get(cfaN))) {
                            node.setLoopDepth(oldLoopDepth);
                            return i;
                        }
                    }
                }
            }
        }

        node.setLoopDepth(oldLoopDepth);
        return -1;
    }

    public OGNode get(OGNode node, int loopDepth) {
        node.setLoopDepth(loopDepth);
        for (OGNode n : nodes) {
            if (n.equals(node)) {
                node.setLoopDepth(0);
                return n;
            }
        }

        node.setLoopDepth(0);
        return null;
    }

    @Override
    public ObsGraph deepCopy(Map<Object, Object> memo) {
        if (memo.containsKey(this)) {
            assert memo.get(this) instanceof ObsGraph;
            return (ObsGraph) memo.get(this);
        }

        ObsGraph nGraph = new ObsGraph();
        // Put the copy into memo.
        memo.put(this, nGraph);
        // Copy nodes.
        this.nodes.forEach(n -> nGraph.nodes.add(n.deepCopy(memo)));
        this.RE.forEach(re -> nGraph.RE.add(re.deepCopy(memo)));
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

//        assert this.lastNode != null;
        nGraph.lastNode = this.lastNode == null ? null : this.lastNode.deepCopy(memo);
        nGraph.needToRevisit = this.needToRevisit;
        nGraph.traceLen = this.traceLen;

        return nGraph;
    }

    // FIXME.
    public List<SharedEvent> getSameLocationAs(SharedEvent a) {
        List<SharedEvent> result = new ArrayList<>();

        for (int i = nodes.indexOf(a.getInNode()) - 1; i >= 0; i--) {
            OGNode nodei = nodes.get(i);

            // FIXME: how to handle the nodes not in the graph?
            if (!nodei.isInGraph()) continue;

            if (a.getAType() == READ) {
                SharedEvent arf = a.getReadFrom();
                // fixme: could we skip some nodes.
                if (i == nodes.indexOf(arf.getInNode())) continue;
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

        return result;
    }

    public boolean porf(SharedEvent a, SharedEvent b) {
        // FIXME: This method may be not correct.
        // Assume a in node A, and b in node B.
        OGNode A = a.getInNode(), B = b.getInNode();
        // Case 1: A == B.
        if (A == B) {
            // In the same node, we assume read events always po before write events.
            // For the case that both a and b are read or write events, a po before b
            // is always true.
            return a.getAType() == READ || b.getAType() == WRITE;
        }
        // Case 2: A != B.
        // If A porf B, then we think a porf b too.
        return OGRevisitor.porf(A, B);
    }

     public void RESubtract(SharedEvent a) {
         Preconditions.checkState(RE.contains(a), "Event a not in RE.");
         RE.remove(a);
     }

     public void removeDelete(List<SharedEvent> delete) {
        // remove the relations before remove the nodes.
         delete.forEach(e -> {
             // For e.
             removeAllRelations(e);
             // For e.inNode.
             OGNode en = e.getInNode();
             removeAllRelations(e.getInNode());
             // Remove node en.
             nodes.remove(en);
         });
     }

     private void removeAllRelations(Object o) {
        Preconditions.checkArgument(o instanceof SharedEvent || o instanceof OGNode);
        if (o instanceof SharedEvent) {
            SharedEvent e = (SharedEvent) o, tmp;
            // Remove rf, fr and mo for e.
            // rf.
            tmp = e.getReadFrom();
            if (tmp != null) {
                tmp.getReadBy().remove(e);
                e.setReadFrom(null);
            }
            // fr.
            e.getFromRead().forEach(fr -> fr.getFromReadBy().remove(e));
            e.getFromRead().clear();
            // mo.
            tmp = e.getMoAfter();
            if (tmp != null) {
                tmp.setMoBefore(null);
                e.setMoAfter(null);
            }
            tmp = e.getMoBefore();
            if (tmp != null) {
                tmp.setMoAfter(null);
                e.setMoBefore(null);
            }
        } else {
            OGNode n = (OGNode) o, tmp;
            // Remove po, rf, fr, to and mo for n.
            // po.
            tmp = n.getPredecessor();
            if (tmp != null) {
                n.setPredecessor(null);
                tmp.getSuccessors().remove(n);
            }
            n.getSuccessors().forEach(suc -> suc.setPredecessor(null));
            n.getSuccessors().clear();
            // rf.
            n.getReadFrom().forEach(rfn -> rfn.getReadBy().remove(n));
            n.getReadFrom().clear();
            n.getReadBy().forEach(rbn -> rbn.getReadFrom().remove(n));
            n.getReadBy().clear();
            // fr.
            n.getFromRead().forEach(frn -> frn.getFromReadBy().remove(n));
            n.getFromRead().clear();
            n.getFromReadBy().forEach(frbn -> frbn.getFromRead().remove(n));
            n.getFromReadBy().clear();
            // mo.
            n.getMoBefore().forEach(mb -> mb.getMoAfter().remove(n));
            n.getMoBefore().clear();
            n.getMoAfter().forEach(ma -> ma.getMoBefore().remove(n));
            n.getMoAfter().clear();
        }
     }

    /**
     * @implNote This method is only used in the revisiting process.
     */
     public void setReadFromAndFromRead(SharedEvent r, SharedEvent w) {
         setRelation("rf", this, w, r);
         // After setting the rf, we should also deduce the fr.
         deduceFromRead();
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
                 if (nodei.getSuccessors().contains(nodej)
                 || nodei.getReadBy().contains(nodej)) {
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
                 Preconditions.checkArgument(w != null,
                         "Event r should read from some write.");
                 // Deduce fr caused by r and w.
                 OGNode wNode = w.getInNode();
                 Preconditions.checkState(wNode.getReadBy().contains(node)
                         && node.getReadFrom().contains(wNode));
                 for (int m = 0; m < n; m++) {
                     if (porf[nodes.indexOf(wNode)][m] && m != nodes.indexOf(node)) {
                         // if wNode porf nodes[m] and nodes[m] != node (wNode must
                         // porf node, but a node cannot fr itself.
                         OGNode frn = nodes.get(m);
                         if (!frn.containWriteToSameVar(w)) continue;
                         SharedEvent frnw = frn.getWriteToSameVar(r);
                         Preconditions.checkState(frnw != null);
                         setRelation("fr", this, r, frnw);
                     }
                 }
             }
         }
     }

    public boolean lessThanOrEqual(SharedEvent e1, SharedEvent e2) {
        Preconditions.checkArgument(e1 != null && e2 != null);
        return e1 == e2 || this.lessThan(e1, e2);
    }

    public boolean lessThan(SharedEvent e1, SharedEvent e2) {
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
            // e1 and e2 in the same node.
            if (e1.getAType() == e2.getAType()) {
                // both e1 and e2 are read or write.
                return true;
            }
            return e1.getAType() == READ;
        } else {
            int en1idx = this.nodes.indexOf(en1), en2idx = this.nodes.indexOf(en2);
            return en1idx < en2idx;
        }
    }

    public void setRE() {
        if (!RE.isEmpty()) RE.clear();
        RE.addAll(lastNode.getRs());
        RE.addAll(lastNode.getWs());
    }

    /**
     * FIXME
     * When r locates in an assume edge and turns to read from a write event that
     * contradicts r, i.e., r /\ w -> false, we change r to its co-event cor. If r
     * comes from conditional branch d, then cor should come from !d. At the same time,
     * we should also replace the rNode (r in) with the corNode (cor in), and assign all
     * relations rNode has to corNode.
     * @return r's co-event cor.
     */
    public SharedEvent changeAssumeNode(SharedEvent r) {
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        Map<Integer, List<SharedEvent>> edgeVarMap = ogInfo.getEdgeVarMap();
        // Find the corEdge.
        CFAEdge rEdge = r.getInEdge(), corEdge = null;
        Preconditions.checkArgument(rEdge instanceof AssumeEdge);
        CFANode pre = rEdge.getPredecessor();
        Preconditions.checkArgument(pre.getNumLeavingEdges() == 2,
                "AssumeEdge " + rEdge + " has " + pre.getNumLeavingEdges() + " != 2 " +
                        "leaving edges.");
        for (int i = 0; i < 2; i++) {
            CFAEdge leavingEdge = pre.getLeavingEdge(i);
            if (!rEdge.equals(leavingEdge)) {
                corEdge = leavingEdge;
                break;
            }
        }
        Preconditions.checkArgument(corEdge != null,
                "Finding corEdge failed: " + rEdge);

        OGNode rNode = r.getInNode();
        Preconditions.checkArgument(nodes.contains(rNode),
                "rNode " + rNode + " should locate in the graph.");

        // Removing rEdge and all edges after it.
        int rEdgeIdx = rNode.getBlockEdges().indexOf(rEdge);
        List<CFAEdge> toRemove = new ArrayList<>();
        for (int i = rEdgeIdx; i < rNode.getBlockEdges().size(); i++) {
            toRemove.add(rNode.getBlockEdges().get(i));
        }
        rNode.getBlockEdges().removeAll(toRemove);
        // Replace rEdge with corEdge.
        rNode.getBlockEdges().add(corEdge);

        // We remove all events after the rEdge, and all relations they have.
        // FIXME: Relations of the events before rEdge remain unchanged. After
        //  replacing, events in rEdge and corEdge have the same relations.
        int rIdx = rNode.getEvents().indexOf(r);
        SharedEvent cor = null;
        List<SharedEvent> coEvents = edgeVarMap.get(corEdge.hashCode());
        assert coEvents != null && !coEvents.isEmpty();
        for (int i = rIdx; i < rNode.getEvents().size(); i++) {
            SharedEvent event = rNode.getEvents().get(i);
            if (Objects.equals(event.getInEdge(), rEdge)) {
                // Events in rEdge.
                SharedEvent coEvent = event.getCoEvent(coEvents);
                assert coEvent != null;
                if (i == rIdx) cor = coEvent;
                // Replace event with coEvent.
                rNode.setEvent(i, coEvent);
                // Set relations for coEvent.
                event.copyRelations(coEvent);
            } else {
                // Events after rEdge.
                rNode.getEvents().remove(event);
                event.removeAllRelations();
            }
        }

        assert cor != null;
        return cor;
    }

    public void replaceCoNode(int idx,
            OGNode node) {
        Preconditions.checkArgument(idx < nodes.size(),
                "Try to remove a node not in Graph.nodes.");
        OGNode rmNode = nodes.get(idx);
        Preconditions.checkArgument(rmNode.equals(this.lastNode),
                "CoNode should be the last node when trying to remove it.");
        // When remove the rmNode, update the last node to its tr-predecessor.
        this.setLastNode(rmNode.getTrAfter());
        this.traceLen -= 1;
        rmNode.getEvents().forEach(this::removeAllRelations);
        removeAllRelations(rmNode);
        // Replace the rmNode with the node.
        nodes.set(idx, node);
    };

    // Set initial current nodes for threads.
    public void setInitialCurrentNodeTable(ARGState initialState) {
        assert !nodes.isEmpty();
        OGNode firstNode = nodes.get(0);
        String curThread = firstNode.getInThread();
        nodeTable.put(curThread, firstNode);
        OGPORState initialOgState =
                AbstractStates.extractStateByType(initialState, OGPORState.class);
        assert initialOgState != null;
        // FIXME: initial state have one and only one thread.
        for (String thrd : initialOgState.getThreads().keySet()) {
            if (!Objects.equals(curThread, thrd)) {
                nodeTable.put(thrd, null);
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

    public void resetCachedAssumeEdge() {
        // TODO.
        // Adjust the cachedAssumeEdges after revisiting.
        for (String t : assumeEdgeTable.keySet()) {
            assumeEdgeTable.put(t, 0);
        }
    }

    public void removeAssumeEdges(SharedEvent r, List<SharedEvent> delete) {
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
}
