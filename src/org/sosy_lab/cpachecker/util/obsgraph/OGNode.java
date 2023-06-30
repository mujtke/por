package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import java.util.*;

public class OGNode {

    private final CFAEdge blockStartEdge;
    private final List<CFAEdge> blockEdges;
    private final boolean simpleNode;
    private final boolean containNonDetVar;
    private final Set<SharedEvent> Rs;
    private final Set<SharedEvent> Ws;

    // s0 -- edge --> s1, edge => ogNode, ogNode.preState = s0.
    private ARGState preState;
    private ARGState sucState;

    // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
    // public Triple<Integer, Integer, Integer> threadStatus;
    private String inThread;

    private boolean isFirstNodeInThread = false;

    // the predecessor and successor in OG.
    private OGNode predecessor;
    private List<OGNode> successors;

    // read from.
    private OGNode readFrom;
    private List<OGNode> readBy;

    // modification order.
    private OGNode moBefore;
    private OGNode moAfter;

    // write before.
    private List<OGNode> wBefore;
    private List<OGNode> wAfter;

    // from read.
    private List<OGNode> fromRead;
    private List<OGNode> fromReadBy;

    // trace order.
    private OGNode trBefore;
    private OGNode trAfter;

    public OGNode(final CFAEdge pBlockStartEdge,
                  final List<CFAEdge> pBlockEdges,
                  boolean pSimpleNode,
                  boolean pContainNonDetVar,
                  Set<SharedEvent> pRs,
                  Set<SharedEvent> pWs) {
        blockStartEdge = pBlockStartEdge;
        blockEdges = pBlockEdges;
        simpleNode = pSimpleNode;
        containNonDetVar = pContainNonDetVar;
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
        StringBuilder str = new StringBuilder();
        str.append(blockEdges.toString());
        return str.toString();
    }

    public CFAEdge getBlockStartEdge() {
        return this.blockStartEdge;
    }

    public List<CFAEdge> getBlockEdges() {
        return this.blockEdges;
    }

    public boolean isSimpleNode() {
        return this.simpleNode;
    }

    public boolean isContainNonDetVar() {
        return this.containNonDetVar;
    }

    public Set<SharedEvent> getRs() {
        return this.Rs;
    }

    public Set<SharedEvent> getWs() {
        return this.Ws;
    }

    public ARGState getPreState() {
        return this.preState;
    }

    public void setPreState(ARGState preState) {
        this.preState = preState;
    }

    public ARGState getSucState() {
        return this.sucState;
    }

    public void setSucState(ARGState sucState) {
        this.sucState = sucState;
    }

    public String getInThread() {
        return this.inThread;
    }

    public void setInThread(String inThread) {
        this.inThread = inThread;
    }

    public boolean isIsFirstNodeInThread() {
        return this.isFirstNodeInThread;
    }

    public void setIsFirstNodeInThread(boolean isFirstNodeInThread) {
        this.isFirstNodeInThread = isFirstNodeInThread;
    }

    public OGNode getPredecessor() {
        return this.predecessor;
    }

    public void setPredecessor(OGNode predecessor) {
        this.predecessor = predecessor;
    }

    public List<OGNode> getSuccessors() {
        return this.successors;
    }

    public void setSuccessors(List<OGNode> successors) {
        this.successors = successors;
    }

    public OGNode getReadFrom() {
        return this.readFrom;
    }

    public void setReadFrom(OGNode readFrom) {
        this.readFrom = readFrom;
    }

    public List<OGNode> getReadBy() {
        return this.readBy;
    }

    public void setReadBy(List<OGNode> readBy) {
        this.readBy = readBy;
    }

    public OGNode getMoBefore() {
        return this.moBefore;
    }

    public void setMoBefore(OGNode moBefore) {
        this.moBefore = moBefore;
    }

    public OGNode getMoAfter() {
        return this.moAfter;
    }

    public void setMoAfter(OGNode moAfter) {
        this.moAfter = moAfter;
    }

    public List<OGNode> getWBefore() {
        return this.wBefore;
    }

    public void setWBefore(List<OGNode> wBefore) {
        this.wBefore = wBefore;
    }

    public List<OGNode> getWAfter() {
        return this.wAfter;
    }

    public void setWAfter(List<OGNode> wAfter) {
        this.wAfter = wAfter;
    }

    public List<OGNode> getFromRead() {
        return this.fromRead;
    }

    public void setFromRead(List<OGNode> fromRead) {
        this.fromRead = fromRead;
    }

    public List<OGNode> getFromReadBy() {
        return this.fromReadBy;
    }

    public void setFromReadBy(List<OGNode> fromReadBy) {
        this.fromReadBy = fromReadBy;
    }

    public OGNode getTrBefore() {
        return this.trBefore;
    }

    public void setTrBefore(OGNode trBefore) {
        this.trBefore = trBefore;
    }

    public OGNode getTrAfter() {
        return this.trAfter;
    }

    public void setTrAfter(OGNode trAfter) {
        this.trAfter = trAfter;
    }
}
