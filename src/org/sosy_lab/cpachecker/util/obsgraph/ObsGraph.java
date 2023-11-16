package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
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

    public ObsGraph() {
        traceLen = 0;
        RE = new ArrayList<>();
    }

    public List<SharedEvent> getRE() {
        if (lastNode != null) {
            List<SharedEvent> events = lastNode.getEvents();
            SharedEvent lastHandledE = lastNode.getLastHandledEvent();
            if (!RE.isEmpty()) RE.clear();
            if (lastHandledE == null) {
                RE.addAll(events);
            } else {
                // FIXME
                Preconditions.checkArgument(events.contains(lastHandledE));
                int i = events.indexOf(lastHandledE);
                i++;
                for (; i < events.size(); i++) {
                    RE.add(events.get(i));
                }
            }
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

    /**
     *
     * @param node
     * @param loopDepth
     * @param cfaNode
     * @return
     */
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
         // closure of po and rf, i.e, porf+.
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
     * When r locates in an assume edge and turns to read from a write event that
     * contradicts r, i.e., r /\ w -> false, we change r to its co-event cor. If r
     * comes from conditional branch d, then cor should come from !d. At the same time,
     * we should also replace the rNode (r in) with the corNode (cor in), and assign all
     * relations rNode has to corNode.
     * @return r's co-event cor.
     */
    public SharedEvent changeAssumeNode(SharedEvent r) {
        OGInfo ogInfo = GlobalInfo.getInstance().getOgInfo();
        Map<Integer, OGNode> nodeMap = ogInfo.getNodeMap();
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
                "Finding corEdge failed.");

        // Replace rNode with corNode.
        OGNode rNode = r.getInNode(), corNode = nodeMap.get(corEdge.hashCode());
        Preconditions.checkArgument(nodes.contains(rNode),
                "rNode " + rNode + " should locate in the graph.");
        nodes.set(nodes.indexOf(rNode), corNode);

        // Update the relations for corNode.
        Preconditions.checkArgument(rNode.getWs().isEmpty(),
                "AssumeEdge contains write events is not expected.");
        // Handle events.
        // We should set the read-from relations for the left read events in
        // the e2n (i.e., read events != cor, because we set read-from relation for cor
        // in method 'setRelation' later.
        SharedEvent cor = null;
        for (SharedEvent r0 : rNode.getRs()) {
            for (SharedEvent cor0 : corNode.getRs()) {
                if (!r0.accessSameVarWith(cor0)) continue;
                if (cor0.accessSameVarWith(r)) cor = cor0;

                // Read from.
                SharedEvent w0 = r0.getReadFrom();
                Preconditions.checkArgument(w0 != null,
                        "r0 should read from some write.");
                OGNode w0n = w0.getInNode();
                cor0.setReadFrom(w0);
                w0.getReadBy().add(cor0);
                if (!w0n.getReadBy().contains(corNode))
                    w0n.getReadBy().add(corNode);
                if (!corNode.getReadFrom().contains(w0n))
                    corNode.getReadFrom().add(w0n);
                r0.setReadFrom(null);
                w0.getReadBy().remove(r0);
                w0n.getReadBy().remove(rNode);
                rNode.getReadFrom().remove(w0n);

                // From read.
                List<SharedEvent> fr0 = r0.getFromRead();
                for (SharedEvent fr : fr0) {
                    fr.getFromReadBy().remove(r0);
                    fr.getInNode().getFromReadBy().remove(rNode);
                }
            }
        }
        // Po.
        OGNode predecessor = rNode.getPredecessor();
        List<OGNode> successors = rNode.getSuccessors();
        if (predecessor != null) {
            predecessor.getSuccessors().remove(rNode);
            predecessor.getSuccessors().add(corNode);
            corNode.setPredecessor(predecessor);
        }

        // Trace order.
        OGNode rTrAfter = rNode.getTrAfter(), rTrBefore = rNode.getTrBefore();
        if (rTrAfter != null) {
            rTrAfter.setTrBefore(corNode);
            corNode.setTrAfter(rTrAfter);
        }
        if (rTrBefore != null) {
            rTrBefore.setTrAfter(corNode);
            corNode.setTrBefore(rTrBefore);
        }

        for (OGNode suc : successors) {
            suc.setPredecessor(corNode);
            corNode.getSuccessors().add(suc);
        }

        corNode.setInGraph(rNode.isInGraph());
        if (rNode.equals(lastNode)) lastNode = corNode;
        corNode.setPreState(rNode.getPreState());
        // FIXME: sucState?

        // Handle threadLoc and inThread.
        corNode.setThreadsLoc(rNode.getThreadLoc());
        corNode.setInThread(corNode.getInThread());

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
}
