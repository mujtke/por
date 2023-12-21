package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;

import java.util.*;
import java.util.stream.Collectors;

import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.READ;

public class OGNode implements Copier<OGNode> {

    // This variable is used to distinguish two edges that locate in the same
    // loop but with different loop depth.
    // loopDepth = 0 means the node is not in a loop.
    private int loopDepth = 0;
    // This variable is used to record all coNodes of the current node. CoNodes exist
    // when there is any conditional branches inside an atomic block.
    // FIXME: we don't deeply copy this variable.
    private final Map<CFANode, OGNode> coNodes = new HashMap<>();
    private final CFAEdge blockStartEdge;
    private final List<CFAEdge> blockEdges;
    private final boolean simpleNode;
    private final boolean containNonDetVar;
    private final Set<SharedEvent> Rs;
    private final Set<SharedEvent> Ws;
    private final List<SharedEvent> events = new ArrayList<>();

    // s0 -- edge --> s1, edge => ogNode, ogNode.preState = s0.
    private ARGState preState;
    private ARGState sucState;

    private String inThread;
    // Use 'threadLoc' to recognize the OGNodes that have the same edges
    // but belong to different program locations.
    private Map<String, String> threadLoc = new HashMap<>();

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
    private SharedEvent lastHandledEvent;
    private CFAEdge lastVisitedEdge;

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

    public OGNode (final CFAEdge pBlockStartEdge,
                   final List<CFAEdge> pBlockEdges,
                   boolean pSimpleNode,
                   boolean pContainNonDetVar) {
        blockStartEdge = pBlockStartEdge;
        blockEdges = pBlockEdges;
        simpleNode = pSimpleNode;
        containNonDetVar = pContainNonDetVar;
        Rs = new HashSet<>();
        Ws = new HashSet<>();
        lastVisitedEdge = pBlockStartEdge;
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
                new ArrayList<>(),
//                this.blockEdges,        /* Shallow copy. */
                this.simpleNode,        /* Shallow copy. */
                this.containNonDetVar,  /* Shallow copy. */
                new HashSet<>(),
                new HashSet<>());
        nNode.blockEdges.addAll(this.blockEdges);
        // Put the copy into memo.
        memo.put(this, nNode);

        // The threadsLoc and inThread is used to distinguish different OGNodes that has
        // the same 'blockEdges', so they should be copied deeply.
        // Because String is immutable, so shallow copy has the same effect with a deep
        // one.
        nNode.loopDepth = this.loopDepth;
        // FIXME: When we copy the node from the nodeMap, we may miss the threadLoc.
        nNode.inThread = this.inThread;
        nNode.threadLoc.putAll(this.threadLoc); /* Deep copy */
        // This variable is not in use now.
        nNode.isFirstNodeInThread = this.isFirstNodeInThread;
        nNode.inGraph = this.inGraph;

        // The left part will need to be copied in a deep way.
        /* events */
        this.events.forEach(r -> nNode.events.add(r.deepCopy(memo)));
        nNode.lastHandledEvent = this.lastHandledEvent != null ?
            this.lastHandledEvent.deepCopy(memo) : null;
        nNode.lastVisitedEdge = this.lastVisitedEdge;
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

    public void setThreadInfo(ARGState chState) {
        OGPORState ogporState = AbstractStates.extractStateByType(chState, OGPORState.class);
        Preconditions.checkArgument(ogporState != null);
        Preconditions.checkArgument(ogporState.getThreads() != null);
        this.inThread = ogporState.getInThread();
        this.threadLoc.putAll(ogporState.getThreads());
    }

    public Map<CFANode, OGNode> getCoNodes() {
        return coNodes;
    }

    public List<SharedEvent> getEvents() {
        return events;
    }

    public void addEvent(SharedEvent event) {
        // Because of the existence of coNode, we should insert the event into some
        // proper location in events.
        if (lastHandledEvent == null) {
            events.add(event);
        } else {
            int i = events.indexOf(lastHandledEvent);
            if (i < events.size() - 1) {
                // If lastHandledEvent is not the last event, then we insert the event
                // after it.
                events.add(i + 1, event);
            } else {
                // Else we just append the event to the end of the events.
                events.add(event);
            }
        }
        lastHandledEvent = event;
        event.setInNode(this);
        if (event.getAType() == READ) {
            Rs.add(event);
        } else if (event.getAType() == SharedEvent.AccessType.WRITE) {
            Ws.add(event);
        } else {
            throw new UnsupportedOperationException("Unknown access type in edge: "
                    + event.getInEdge() + ".");
        }
    }

    public void removeEvent(SharedEvent event) {
        if (event == lastHandledEvent) {
            int i = events.indexOf(event);
            lastHandledEvent = i > 0 ? events.get(i) : null;
        }
        events.remove(event);
        if (event.getAType() == READ) Rs.remove(event);
        else if (event.getAType() == SharedEvent.AccessType.WRITE) Ws.remove(event);
    }

    public SharedEvent getLastHandledEvent() {
        return lastHandledEvent;
    }

    public void setLastHandledEvent(SharedEvent lastHandledEvent) {
        this.lastHandledEvent = lastHandledEvent;
    }

    /**
     * When we are building the nodeMap and reach a branch d, whose coEdge !d has been
     * handled before, we should update the lastHandledEvent because d may lead some
     * new edges we haven't seen yet. We reset the lastHandledEvent as the original last
     * event handled before coEdge.
     * @param coEdge the coEdge that belongs to the coNode of this node.
     */
    public void updateLastHandledEvent(CFAEdge coEdge) {
        if (lastHandledEvent == null) {
            // We have set lastHandledEvent to be null in coEdge.inNode.
            // We just get this node by deep copy, so events should contain all events
            // in coEdge.inNode.
            // FIXME: we assume all atomic block access global vars, which means events
            //  must be not empty.
            Preconditions.checkArgument(!events.isEmpty(),
                    "Encountering the node has no shared events.");
            lastHandledEvent = events.get(events.size() - 1);
        }

        Preconditions.checkArgument(events.contains(lastHandledEvent)
                && blockEdges.contains(coEdge));
        // Reset lastHandledEvent's inEdge should happen before coEdge.
        int i = blockEdges.indexOf(coEdge),
                j = blockEdges.indexOf(lastHandledEvent.getInEdge()),
                k = events.indexOf(lastHandledEvent);
        SharedEvent resetLastHandledE = lastHandledEvent;
        while (i <= j) {
            k = events.indexOf(resetLastHandledE) - 1;
            if (k < 0) break;
            resetLastHandledE = events.get(k);
            j = blockEdges.indexOf(resetLastHandledE.getInEdge());
        }
        Preconditions.checkArgument(i > j,
                "Update lastHandledEvent failed.");
        lastHandledEvent = resetLastHandledE;
    }

    /**
     * If the edge we are visiting has a coEdge that has been visited, then we should
     * delete the edges after coEdge (including coEdge) because we are now in a new
     * branch and may meet some new edges.
     */
    public void removeCoEdge(CFAEdge coEdge) {
        Iterator<CFAEdge> it = blockEdges.iterator(), ite = blockEdges.iterator();
        CFAEdge tmp = it.next();
        while (tmp != coEdge && it.hasNext()) {
            ite.next();
            tmp = it.next();
        }
        Preconditions.checkState(tmp == coEdge,
                "coEdge not in node: " + this);
        while (ite.hasNext()) {
            ite.next();
            ite.remove();
        }
    }

    public int contains(CFAEdge edge) {
        return blockEdges.indexOf(edge);
    }

    // Set the new lastVisitedEdge, and return its index(-1 if we have reached the end
    // of blockEdges).
    public int setLastVisitedEdge(int idx) {
        lastVisitedEdge = blockEdges.get(idx);
        return idx + 1 < blockEdges.size() ? idx : -1;
    }

    public void setLastVisitedEdge(CFAEdge cfaEdge) {
        lastVisitedEdge = cfaEdge;
    }

    public OGNode sameThrdSuc() {
        List<OGNode> sts = successors.stream()
                .filter(n -> inThread.equals(n.getInThread()))
                .collect(Collectors.toList());
        Preconditions.checkArgument(sts.size() <= 1,
                "At most one same-thread successor is allowed.");
        return sts.isEmpty() ? null : sts.iterator().next();
    }

     // Add new extracted events to the node.
    public void addEvents(List<SharedEvent> sharedEvents) {
        sharedEvents.forEach(e -> {
            events.add(e);
            if (e.getAType() ==  READ) {
                Rs.add(e);
            } else {
                Ws.add(e);
            }
        });
    }

    // Replace assumeEdge with assumeEdge's coEdge.
    public void replaceCoEdge(final Map<Integer, List<SharedEvent>> edgeVarMap,
            CFAEdge assumeEdge) {
        Preconditions.checkArgument(
                assumeEdge.getPredecessor().getNumLeavingEdges() > 1,
                "Cannot find coEdge: " + assumeEdge);
        CFAEdge coEdge = null, tmp;
        for (int i = 0; i < assumeEdge.getLineNumber(); i++) {
            tmp = assumeEdge.getPredecessor().getLeavingEdge(i);
            if (tmp instanceof AssumeEdge && tmp != assumeEdge) {
                coEdge = tmp;
                break;
            }
        }

        Preconditions.checkState(coEdge != null,
                "Cannot find coEdge: " + assumeEdge);
        Preconditions.checkState(blockEdges.contains(coEdge),
                "Cannot replace edge not existed: " + coEdge);

        // Replace.
        // Remove all edges after the coEdge.
        int coEdgeIdx = blockEdges.indexOf(coEdge);
        blockEdges.removeIf(e -> blockEdges.indexOf(e) >= coEdgeIdx);
        blockEdges.add(assumeEdge);
        // Remove all shared events after the coEdge.
        List<SharedEvent> toRemove = events.stream()
                .filter(e -> blockEdges.indexOf(e.getInEdge()) >= coEdgeIdx)
                .collect(Collectors.toList());
        events.removeAll(toRemove);
        toRemove.forEach(Rs::remove);
        toRemove.forEach(Ws::remove);

        // Update last-visited edge if necessary.
        if (lastHandledEvent.getInEdge() == coEdge) {
            Preconditions.checkState(edgeVarMap.get(assumeEdge.hashCode()) != null,
                    "Expected non-empty shared vars: " + assumeEdge);
            addEvents(edgeVarMap.get(assumeEdge.hashCode()));
            List<SharedEvent> tmps = edgeVarMap.get(assumeEdge.hashCode())
                    .stream().filter(e -> e.accessSameVarWith(lastHandledEvent)
                                    && e.getAType() == READ).collect(Collectors.toList());
            Preconditions.checkArgument(tmps.size() == 1,
                    "Cannot replace old lastHandledEdge in " + coEdge);
            lastHandledEvent = tmps.iterator().next();
        }
    }
}
