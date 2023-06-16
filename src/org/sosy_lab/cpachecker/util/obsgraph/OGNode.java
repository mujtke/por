package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Triple;

import java.util.*;

public class OGNode {

    public final List<SharedEvent> Rs;
    public final List<SharedEvent> Ws;
    // s0 -- edge --> s1, edge => ogNode, ogNode.preState = s0.
    public ARGState preState;
    public ARGState sucState;
    // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
    // public Triple<Integer, Integer, Integer> threadStatus;
    public String inThread;
    public boolean isFirstNodeInThread = false;
    // the predecessor and successor in OG.
    public OGNode predecessor;
    public List<OGNode> successors;
    // read from.
    public OGNode readFrom;
    public List<OGNode> readby;
    // modification order.
    public OGNode moBefore;
    public OGNode moAfter;
    // write before.
    public List<OGNode> wBefore;
    public List<OGNode> wAfter;
    // from read.
    public List<OGNode> fromRead;
    public List<OGNode> fromReadBy;
    // trace order.
    public OGNode trBefore;
    public OGNode trAfter;

    public OGNode(List<SharedEvent> pRs,
                  List<SharedEvent> pWs) {
        Rs = pRs;
        Ws = pWs;
    }

    // return true if 'this' is happen-before for o.
    public boolean hb(OGNode o) {
        return false;
    }

    @Override
    public boolean equals(Object o) { // handle this carefully.
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        OGNode oNode = (OGNode) o;
        return Rs.equals(oNode.Rs)
                && Ws.equals(oNode.Ws)
                && inThread.equals(oNode.inThread);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Rs, Ws, inThread);
    }

    @Override
    public String toString() {
        return "";
    }
}
