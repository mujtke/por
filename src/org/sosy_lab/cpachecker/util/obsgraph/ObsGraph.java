package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;

import java.util.*;

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
        return Objects.hash(nodes, lastNode, needToRevisit);
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
            if (a.getAType() == SharedEvent.AccessType.READ) {
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
         delete.forEach(this::removeAllRelationsForEvent);
        delete.forEach(this.nodes::remove);
     }

     public void setReadFrom(SharedEvent r, SharedEvent w) {
        // TODO.
     }

     private void removeAllRelationsForEvent(SharedEvent e) {
        // TODO.
     }
}
