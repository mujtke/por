package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import java.util.*;

public class OGNode implements Copier<OGNode> {

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
     *
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
        nNode.inThread = this.inThread;
        nNode.threadLoc = this.threadLoc; /* Deep copy */
        // This variable is not in use now.
        nNode.isFirstNodeInThread = this.isFirstNodeInThread;
        nNode.inGraph = this.inGraph;

        // The left part will need to be copied in a deep way.
        /* Rs & Ws. */
        this.Rs.forEach(r -> {
            SharedEvent nr = r.deepCopy(memo);
            nNode.Rs.add(nr);
        });
        this.Ws.forEach(w -> {
            SharedEvent nw = w.deepCopy(memo);
            nNode.Ws.add(nw);
        });

        /* preState & sucState */
        ARGState nPreState = this.preState, /* Shallow copy. */
                nSucState = this.sucState;  /* Shallow copy. */
        nNode.preState = nPreState;
        nNode.sucState = nSucState;

        /* predecessor & successors */
        OGNode nPredecessor = this.predecessor != null
                ? this.predecessor.deepCopy(memo) : null;
        nNode.predecessor = nPredecessor;
        this.successors.forEach(suc -> {
            OGNode nSuc = suc.deepCopy(memo);
            nNode.successors.add(nSuc);
        });

        /* readFrom & fromRead */
        this.readFrom.forEach(rf -> {
            OGNode nRf = rf.deepCopy(memo);
            nNode.readFrom.add(nRf);
        });
        this.fromRead.forEach(rb -> {
            OGNode nRb = rb.deepCopy(memo);
            nNode.readBy.add(nRb);
        });

        /* Modification order */
        this.moBefore.forEach(mb -> {
            OGNode nMb = mb.deepCopy(memo);
            nNode.moBefore.add(nMb);
        });
        this.moAfter.forEach(ma -> {
            OGNode nMa = ma.deepCopy(memo);
            nNode.moAfter.add(nMa);
        });

        /* Write before */
        this.wBefore.forEach(wb -> {
            OGNode nWb = wb.deepCopy(memo);
            nNode.wBefore.add(nWb);
        });
        this.wAfter.forEach(wa -> {
            OGNode nWa = wa.deepCopy(memo);
            nNode.wAfter.add(nWa);
        });

        /* From read */
        this.fromRead.forEach(fr -> {
            OGNode nFr = fr.deepCopy(memo);
            nNode.fromRead.add(nFr);
        });
        this.fromReadBy.forEach(frb -> {
            OGNode nFrb = frb.deepCopy(memo);
            nNode.fromReadBy.add(nFrb);
        });

        /* Trace order */
        OGNode nTrBefore = this.trBefore != null ? this.trBefore.deepCopy(memo) : null,
                nTrAfter = this.trAfter != null ? this.trAfter.deepCopy(memo) : null;
        nNode.trBefore = nTrBefore;
        nNode.trAfter = nTrAfter;

        return nNode;
    }

    @Override
    public boolean equals(Object o) { // Handle this carefully.
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        OGNode oNode = (OGNode) o;
        // Use 'blockEdges', 'inThread' and 'threadsLoc' to distinguish two
        // different OGNodes.
        return blockEdges.equals(oNode.blockEdges)
                && inThread.equals(oNode.inThread)
                && threadLoc.equals(oNode.threadLoc);
    }

    // If we override the 'equals' method, then we should also
    // override the 'hashCode()' to make sure they behave consistently.
    @Override
    public int hashCode() {
        return Objects.hash(blockEdges, inThread, threadLoc);
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
}
