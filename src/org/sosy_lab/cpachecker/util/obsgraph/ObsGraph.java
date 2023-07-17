package org.sosy_lab.cpachecker.util.obsgraph;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;

public class ObsGraph implements Copier<ObsGraph> {

    private final List<OGNode> nodes = new ArrayList<>();

    private OGNode lastNode = null;

    private boolean needToRevisit = false;

    private int traceLen;

    public ObsGraph() {
        traceLen = 0;
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
}
