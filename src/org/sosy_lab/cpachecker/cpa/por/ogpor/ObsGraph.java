package org.sosy_lab.cpachecker.cpa.por.ogpor;

import java.util.ArrayList;
import java.util.List;

public class ObsGraph {

    private final List<OGNode> nodes;

    public ObsGraph() {
        nodes = new ArrayList<OGNode>();
    }

    public ObsGraph(List<OGNode> pCopy) {
        nodes = new ArrayList<OGNode>();
        nodes.addAll(pCopy);
    }

    public void addNewNode(OGNode pNewNode) {

    }
}
