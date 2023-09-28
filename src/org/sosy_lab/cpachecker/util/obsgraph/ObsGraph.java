package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer.setRelation;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.READ;
import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.WRITE;

public class ObsGraph implements Copier<ObsGraph> {

    private final List<OGNode> nodes = new ArrayList<>();

    private OGNode lastNode = null;

    private boolean needToRevisit = false;

    private int traceLen;

    private List<SharedEvent> RE;

    public ObsGraph() {
        traceLen = 0;
        RE = new ArrayList<>();
    }

    public List<SharedEvent> getRE() {
        return RE;
    }

    public void addNode(OGNode n) {
        nodes.add(n);
        RE.addAll(n.getRs());
        RE.addAll(n.getWs());
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
     * Given a graph and a OGNode, judge whether the graph contains the node.
     * @return A positive integer if the graph contains the node, -1 if not.
     */
    public int contain(OGNode node) {
        for (int i = 0; i < nodes.size(); i++) {
            assert nodes.get(i) != null;
            if (nodes.get(i).equals(node)) {
                return i;
            }
        }

        return -1;
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
        this.nodes.forEach(n -> {
            OGNode nN = n.deepCopy(memo);
            nGraph.nodes.add(nN);
        });

        assert this.lastNode != null;
        nGraph.lastNode = this.lastNode.deepCopy(memo);
        nGraph.needToRevisit = this.needToRevisit;
        nGraph.traceLen = this.traceLen;

        return nGraph;
    }

    public List<SharedEvent> getSameLocationAs(SharedEvent a) {
        List<SharedEvent> result = new ArrayList<>();

        for (int i = nodes.indexOf(a.getInNode()) - 1; i >= 0; i--) {
            OGNode nodei = nodes.get(i);
            if (a.getAType() == READ) {
                SharedEvent arf = a.getReadFrom();
                // jump nodes after arf.inNode.
                if (i > nodes.indexOf(arf.getInNode())) continue;
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

    public boolean porf(SharedEvent e1, SharedEvent e2) {
        // TODO
        return false;
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

     public void setReadFrom(SharedEvent r, SharedEvent w) {
         // After setting the rf, we should also deduce the fr.
         setRelation("rf", w, r);
         // Deduce the fr caused by the new rf.
         // For the sake of saving space, use map M to replace the matrix T.
         // T(i, j) == M(k), k = hash(i, j), and i, j are the indexes of the two nodes.
         Map<Integer, Boolean> adjacencyMatrix = new HashMap<>();
         OGNode rNode = r.getInNode(), wNode = w.getInNode(), porfPre = wNode;
         Preconditions.checkState(wNode.getReadBy().contains(rNode)
                 && rNode.getReadFrom().contains(wNode));
         Stack<OGNode> waitlist = new Stack<>();
         Set<OGNode> frNodes = new HashSet<>();
         waitlist.add(porfPre);
         while (!waitlist.isEmpty()) {
             porfPre = waitlist.pop();
             int i = nodes.indexOf(porfPre);
             Set<OGNode> porfSucs = new HashSet<>();
             porfSucs.addAll(porfPre.getReadBy());
             porfSucs.addAll(porfPre.getSuccessors());
             porfSucs.forEach(porfSuc -> {
                 int j = nodes.indexOf(porfSuc), k = hash(i, j), kp = hash(j, i);
                 if (adjacencyMatrix.get(kp) == null) {
                     // Only if We haven't computed the relation for <j, i> , we could
                     // compute it for <i, j>.
                     if (adjacencyMatrix.putIfAbsent(k, true) == null) {
                         // When it's the first time to access the porfSuc, do sth.
                         // Otherwise, we won't handle it again.
                         if (porfSuc.containWriteToSameVar(w)) {
                             frNodes.add(porfSuc);
                         }
                         waitlist.add(porfSuc);
                     };
                 }
             });
         }
         // frNodes contains rNode, but we rNode can't fr itself.
         frNodes.remove(rNode);
         frNodes.forEach(frn -> {
             SharedEvent frnw = frn.getWriteToSameVar(r);
             Preconditions.checkState(frnw != null);
             setRelation("fr", r, frnw);
         });
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
}
