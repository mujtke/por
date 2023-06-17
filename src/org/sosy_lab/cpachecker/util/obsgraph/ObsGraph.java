package org.sosy_lab.cpachecker.util.obsgraph;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;

public class ObsGraph {

    public List<OGNode> nodes;

    public OGNode lastNode = null;

    public boolean needToRevisit = false;

    public int traceLen;

    public ObsGraph() {
        nodes = new ArrayList<>();
        traceLen = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObsGraph obsGraph = (ObsGraph) o;
        return needToRevisit == obsGraph.needToRevisit
                && Objects.equals(nodes, obsGraph.nodes)
                && Objects.equals(lastNode, obsGraph.lastNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, lastNode, needToRevisit);
    }

}
