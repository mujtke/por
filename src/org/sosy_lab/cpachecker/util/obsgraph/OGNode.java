package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import java.util.*;

public class OGNode implements Copier<OGNode> {

    // The variable is used to distinguish two edges that locate in the same
    // loop but with different loop depth.
    // loopDepth = 0 means the node is not in a loop, i.e., if a node was in a loop,
    // then its loopDepth >= 1.
    private int loopDepth = 0;
    private final CFAEdge blockStartEdge;
    private final List<CFAEdge> blockEdges;
    private final boolean simpleNode;
    private final boolean containNonDetVar;
    private final Set<SharedEvent> Rs;
    private final Set<SharedEvent> Ws;

    // s0 -- edge --> s1, edge => ogNode, ogNode.preState = s0.
    private ARGState preState;
    private ARGState sucState;

    private String inThread;
    // Use 'threadLoc' to recognize the OGNodes that have the same edges
    // but belong to different program locations.
    private Map<String, String> threadLoc;

    private boolean isFirstNodeInThread = false;

    // the predecessor and successor in OG.
    private OGNode predecessor;
    private final List<OGNode> successors = new ArrayList<>();

    // read from.
    private final List<OGNode> readFrom = new ArrayList<>();
    private final List<OGNode> readBy = new ArrayList<>();

    // modification order.
    private final List<OGNode> moBefore = new ArrayList<>();
    private final List<OGNode> moAfter = new ArrayList<>();

    // write before.
    private final List<OGNode> wBefore = new ArrayList<>();
    private final List<OGNode> wAfter = new ArrayList<>();

    // from read.
    private final List<OGNode> fromRead = new ArrayList<>();
    private final List<OGNode> fromReadBy = new ArrayList<>();

    // trace order.
    private OGNode trBefore;
    private OGNode trAfter;

    // Indicate whether this node is in a graph.
    // Here, in a graph means this node is in the trace of that graph.
    private boolean inGraph;

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

    /**
     * @param memo Store the original object and its copied object.
     * @return The deep copy of this OGNode.
     */
    public OGNode deepCopy(Map<Object, Object> memo) {
        if (memo.containsKey(this)) {
            // If current object has been copied.
            assert memo.get(this) instanceof OGNode;
            return (OGNode) memo.get(this);
        }
        // Else, try copying 'this' to a new object.
        OGNode nNode = new OGNode(
                this.blockStartEdge,    /* Shallow copy. */
                this.blockEdges,        /* Shallow copy. */
                this.simpleNode,        /* Shallow copy. */
                this.containNonDetVar,  /* Shallow copy. */
                new HashSet<>(),
                new HashSet<>());
        // Put the copy into memo.
        memo.put(this, nNode);

        // The threadsLoc and inThread is used to distinguish different OGNodes that has
        // the same 'blockEdges', so they should be copied deeply.
        // Because String is immutable, so shallow copy has the same effect with a deep
        // one.
        nNode.loopDepth = this.loopDepth;
        nNode.inThread = this.inThread;
        nNode.threadLoc = this.threadLoc; /* Deep copy */
        // This variable is not in use now.
        nNode.isFirstNodeInThread = this.isFirstNodeInThread;
        nNode.inGraph = this.inGraph;

        // The left part will need to be copied in a deep way.
        /* Rs & Ws. */
        this.Rs.forEach(r -> nNode.Rs.add(r.deepCopy(memo)));
        this.Ws.forEach(w -> nNode.Ws.add(w.deepCopy(memo)));

        /* preState & sucState */
        nNode.preState = this.preState; /* Shallow copy. */
        nNode.sucState = this.sucState; /* Shallow copy. */

        /* predecessor & successors */
        nNode.predecessor = this.predecessor != null
                ? this.predecessor.deepCopy(memo) : null;
        this.successors.forEach(suc -> nNode.successors.add(suc.deepCopy(memo)));

        /* readFrom & readBy */
        this.readFrom.forEach(rf -> nNode.readFrom.add(rf.deepCopy(memo)));
        this.readBy.forEach(rb -> nNode.readBy.add(rb.deepCopy(memo)));

        /* Modification order: no copy. */
        this.moBefore.forEach(mb -> nNode.moBefore.add(mb.deepCopy(memo)));
        this.moAfter.forEach(ma -> nNode.moAfter.add(ma.deepCopy(memo)));
        /* Write before: no copy. */
        /* From read: no copy. */

        /* Trace order */
        nNode.trBefore = this.trBefore != null ? this.trBefore.deepCopy(memo) : null;
        nNode.trAfter = this.trAfter != null ? this.trAfter.deepCopy(memo) : null;

        return nNode;
    }

    @Override
    public boolean equals(Object o) { // Handle this carefully.
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        OGNode oNode = (OGNode) o;
        // Use 'loopDepth', 'blockEdges', 'inThread' and 'threadsLoc[inThread]' to
        // distinguish two different OGNodes.
        return loopDepth == oNode.loopDepth
                && blockEdges.equals(oNode.blockEdges)
                && inThread.equals(oNode.inThread)
                && threadLoc.get(inThread).equals(oNode.threadLoc.get(oNode.inThread));
    }

    // If we override the 'equals' method, then we should also
    // override the 'hashCode()' to make sure they behave consistently.
    @Override
    public int hashCode() {
        return Objects.hash(loopDepth, blockEdges, inThread, threadLoc.get(inThread));
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(blockEdges.toString());
        if (loopDepth > 0) {
            str.append("@").append(loopDepth);
        }
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

    public List<OGNode> getReadFrom() {
        return this.readFrom;
    }

    public List<OGNode> getReadBy() {
        return this.readBy;
    }

    public List<OGNode> getMoBefore() {
        return this.moBefore;
    }

    public List<OGNode> getMoAfter() {
        return this.moAfter;
    }

    public List<OGNode> getWBefore() {
        return this.wBefore;
    }

    public List<OGNode> getWAfter() {
        return this.wAfter;
    }

    public List<OGNode> getFromRead() {
        return this.fromRead;
    }

    public List<OGNode> getFromReadBy() {
        return this.fromReadBy;
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

    public Map<String, String> getThreadLoc() {
        return this.threadLoc;
    }

    public void setThreadsLoc(Map<String, String> pThreadLoc) {
       this.threadLoc = pThreadLoc;
    }

    public boolean isInGraph() {
        return this.inGraph;
    }

    public void setInGraph(boolean pInGraph) {
        this.inGraph = pInGraph;
    }

    public CFAEdge getLastBlockEdge() {
        if (simpleNode) return blockStartEdge;
        int edgeNum = blockEdges.size();
        assert edgeNum > 1:"Non simple block OG node should have more than one edge";
        return blockEdges.get(edgeNum - 1);
    }

    public boolean containWriteToSameVar(SharedEvent w) {
        return Ws.stream().anyMatch(w::accessSameVarWith);
    }

    public SharedEvent getWriteToSameVar(SharedEvent r) {
        for (SharedEvent w : Ws) {
            if (w.accessSameVarWith(r))
                return w;
        }

        return null;
    }

    public int getLoopDepth() {
        return loopDepth;
    }

    public void setLoopDepth(int loopDepth) {
        this.loopDepth = loopDepth;
    }

}
