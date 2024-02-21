package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    // FIXME: use index to indicate the last handled event.
//    private SharedEvent lastHandledEvent;
    private int lheIndex = -1;

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

    public OGNode(final CFAEdge pBlockStartEdge,
                   final List<CFAEdge> pBlockEdges,
                   boolean pSimpleNode,
                   boolean pContainNonDetVar) {
        blockStartEdge = pBlockStartEdge;
        blockEdges = pBlockEdges;
        simpleNode = pSimpleNode;
        containNonDetVar = pContainNonDetVar;
        Rs = new HashSet<>();
        Ws = new HashSet<>();
//        lastVisitedEdge = pBlockStartEdge;
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
        nNode.inThread = String.valueOf(this.inThread);
        nNode.threadLoc.putAll(this.threadLoc); /* Deep copy */
        // This variable is not in use now.
        nNode.isFirstNodeInThread = this.isFirstNodeInThread;
        nNode.inGraph = this.inGraph;
        nNode.lheIndex = this.lheIndex;
        nNode.lastVisitedEdge = this.lastVisitedEdge;

        /* preState & sucState */
        nNode.preState = this.preState; /* Shallow copy. */
        nNode.sucState = this.sucState; /* Shallow copy. */

        // The left part will need to be copied in a deep way.
        /* events */
        this.events.forEach(r -> nNode.events.add(r.deepCopy(memo)));
        nNode.lheIndex = this.lheIndex;
        nNode.lastVisitedEdge = this.lastVisitedEdge;
        /* Rs & Ws. */
        this.Rs.forEach(r -> nNode.Rs.add(r.deepCopy(memo)));
        this.Ws.forEach(w -> nNode.Ws.add(w.deepCopy(memo)));

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
        for (int i = 0; i < blockEdges.size(); i++) {
            CFAEdge e = blockEdges.get(i);
            if (i == 0) {
                str.append(e);
                str.append("\n");
                continue;
            }
            str.append(e.getPredecessor());
            str.append(" -{");
            str.append(e.getCode());
            str.append("}-> ");
            str.append(e.getSuccessor());
            str.append("\n");
        }

        if (loopDepth != 0) {
            str.append("@").append(loopDepth);
        }
        return str.toString();
    }

    public CFAEdge getLastVisitedEdge() {
        return lastVisitedEdge;
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
        // Because of the existence of the coNode, we should insert the event into some
        // proper location in events.
//        if (lastHandledEvent == null) {
        if (lheIndex == -1) {
            events.add(event);
        } else {
            int i = lheIndex;
            if (i < events.size() - 1) {
                // If lastHandledEvent is not the last event, then we insert the event
                // after it.
                events.add(i + 1, event);
            } else {
                // Else we just append the event to the end of the events.
                events.add(event);
            }
        }
        lheIndex++;
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
        SharedEvent lastHandledEvent = lheIndex < 0 ? null : events.get(lheIndex);
        if (event == lastHandledEvent) {
            lheIndex--;
        }
        events.remove(event);
        if (event.getAType() == READ) Rs.remove(event);
        else Ws.remove(event);
    }

    public SharedEvent getLastHandledEvent() {
        return (lheIndex >= 0 && lheIndex < events.size()) ? events.get(lheIndex) : null;
    }

    public int getLheIndex() {
        return lheIndex;
    }

    public void setLastHandledEvent(SharedEvent lastHandledEvent) {
        Preconditions.checkArgument(events.contains(lastHandledEvent),
                "Cannot set the event that is not in the node as the " +
                        "lastHandledEvent");
        this.lheIndex = events.indexOf(lastHandledEvent);
    }

    /**
     * When we are building the nodeMap and reach a branch d, whose coEdge !d has been
     * handled before, we should update the lastHandledEvent because d may lead some
     * new edges we haven't seen yet. We reset the lastHandledEvent as the original last
     * event handled before coEdge.
     * @param coEdge the coEdge that belongs to the coNode of this node.
     */
    public void updateLastHandledEvent(CFAEdge coEdge) {
        SharedEvent lastHandledEvent = (lheIndex < events.size() && lheIndex >= 0) ?
                events.get(lheIndex) : null;
        if (lastHandledEvent == null) {
            // We have set lastHandledEvent to be null in coEdge.inNode.
            // We just get this node by deep copy, so events should contain all events
            // in coEdge.inNode.
            // FIXME: we assume all atomic block access global vars, which means events
            //  must be not empty.
            Preconditions.checkArgument(!events.isEmpty(),
                    "Encountering the node has no shared events.");
            lheIndex = events.size() - 1;
            lastHandledEvent = events.get(lheIndex);
        }

        Preconditions.checkArgument(blockEdges.contains(coEdge));
        // Reset lastHandledEvent's inEdge should happen before coEdge.
        int i = blockEdges.indexOf(coEdge),
                j = blockEdges.indexOf(lastHandledEvent.getInEdge()),
                k = lheIndex;
        SharedEvent resetLastHandledE = lastHandledEvent;
        while (i <= j) {
            k = events.indexOf(resetLastHandledE) - 1;
            if (k < 0) break;
            resetLastHandledE = events.get(k);
            j = blockEdges.indexOf(resetLastHandledE.getInEdge());
        }
        Preconditions.checkArgument(i > j,
                "Update lastHandledEvent failed.");
//        lastHandledEvent = resetLastHandledE;
        lheIndex = k;
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
        if (sharedEvents == null) return;
        sharedEvents.forEach(e -> {
            switch (e.getAType()) {
                case READ:
                    Set<SharedEvent> sameR = Rs.stream()
                            .filter(r -> r.getVar().getName().equals(e.getVar().getName()))
                            .collect(Collectors.toSet()),
                            sameW = Ws.stream()
                                    .filter(w -> w.getVar().getName().equals(e.getVar().getName()))
                                    .collect(Collectors.toSet());
                    if (!sameR.isEmpty() || !sameW.isEmpty()) {
                        // For the same read var, only the first read will be added.
                        // If there is a w writes the same var with r, then r could be
                        // ignored, because it will always read the same value.
                    } else {
                        SharedEvent nE = e.deepCopy(new HashMap<>());
                        events.add(nE);
                        nE.setInNode(this);
                        Rs.add(nE);
                    }
                    break;

                case WRITE:
                    // For multiple writes to the same var, only the last one will be
                    // added.
                    sameW = Ws.stream()
                            .filter(w -> w.getVar().getName().equals(e.getVar().getName()))
                            .collect(Collectors.toSet());
                    if (!sameW.isEmpty()) {
                        sameW.forEach(this::removeEvent);
                    }
                    SharedEvent nE = e.deepCopy(new HashMap<>());
                    events.add(nE);
                    nE.setInNode(this);
                    Ws.add(nE);
                default:
            }
        });
    }

    // Replace conflicted assumeEdge d with its coEdge !d.
    public CFAEdge replaceCoEdge(final Map<Integer, List<SharedEvent>> edgeVarMap,
            CFAEdge d) {
        Preconditions.checkArgument(
                d.getPredecessor().getNumLeavingEdges() > 1,
                "Predecessor of assume edge has less then two outgoing " +
                        "edges is not allowed: " + d);
        CFAEdge nd = null, tmp;
        for (int i = 0; i < d.getLineNumber(); i++) {
            tmp = d.getPredecessor().getLeavingEdge(i);
            if (tmp instanceof AssumeEdge && tmp != d) {
                nd = tmp;
                break;
            }
        }

        Preconditions.checkState(nd != null,
                "Cannot find coEdge for: " + d);
        // FIXME: this node should contain nd?
//        Preconditions.checkState(blockEdges.contains(nd),
//                "Cannot replace edge not in blockEdges: " + nd);
        if (!blockEdges.contains(nd)) {
            // FIXME
            return null;
        }

        // Replace.
        int assumeEdgeIdx = blockEdges.indexOf(nd);
        // Remove all shared events in or after the nd.
        List<SharedEvent> toRemove = events.stream()
                .filter(e -> blockEdges.indexOf(e.getInEdge()) >= assumeEdgeIdx)
                .collect(Collectors.toList());
        // Before removing these events, clear their relations firstly.
        toRemove.forEach(SharedEvent::removeAllRelations);
        events.removeAll(toRemove);
        toRemove.forEach(Rs::remove);
        toRemove.forEach(Ws::remove);
        // Remove assumeEdge and all edges after it.
        blockEdges.removeIf(e -> blockEdges.indexOf(e) >= assumeEdgeIdx);
        blockEdges.add(d);

        // Update the last-visited edge.
//        lastVisitedEdge = coEdge;
        // Update last-visited event if necessary.
        // FIXME: more than one sharedEvent in an assume edge?
//        Preconditions.checkArgument(lheIndex < events.size() && lheIndex >= 0);
//        SharedEvent lastHandledEvent = events.get(lheIndex);
//        if (lastHandledEvent.getInEdge() == nd) {
//            // Replacing assumeEdge with its coEdge doesn't change the number of events.
//            addEvents(edgeVarMap.get(d.hashCode()));
//        }
        if (edgeVarMap.get(d.hashCode()) != null && !edgeVarMap.get(d.hashCode()).isEmpty()) {
            lheIndex = events.isEmpty() ? 0 : events.size();
            addEvents(edgeVarMap.get(d.hashCode()));
        } else {
            lheIndex = events.isEmpty() ? -1 : events.size() - 1;
        }

        return d;
    }

    public boolean shouldRevisit() {
        // FIXME
        return lheIndex < 0 || lheIndex < events.size() - 1;
    }

    public int getRefCount(String type, OGNode other) {
        int refCount = 0;
        switch (type) {
            case "rf":
                for (SharedEvent r : Rs)
                    if (other.Ws.contains(r.getReadFrom()))
                        refCount++;
                break;
            case "rb":
                for (SharedEvent w : Ws)
                    for (SharedEvent r : w.getReadBy())
                        if (other.Rs.contains(r))
                            refCount++;
                break;

            case "fr":
                for (SharedEvent r : Rs)
                    for (SharedEvent fr : r.getFromRead())
                        if (other.Ws.contains(fr))
                            refCount++;
                break;
            case "frb":
                for (SharedEvent w : other.Ws)
                    for (SharedEvent r : w.getFromReadBy())
                        if (Rs.contains(r))
                            refCount++;
                break;

            case "ma":
                for (SharedEvent w : Ws)
                    if (other.Ws.contains(w.getMoAfter()))
                        refCount++;
                break;

            case "mb":
                for (SharedEvent w : Ws)
                    if (other.Ws.contains(w.getMoBefore()))
                        refCount++;
                break;

            default:
        }

        return refCount;
    }

    // FIXME
//    public boolean needReplaceCoEdge(CFAEdge edge) {
//        if (blockEdges.isEmpty()) return false;
//        if (lastVisitedEdge == null) return false;
//        if (blockEdges.indexOf(lastVisitedEdge) == blockEdges.size() - 1) return false;
//        int coEdgeIdx = blockEdges.indexOf(lastVisitedEdge) + 1;
//        return Objects.equals(blockEdges.get(coEdgeIdx).getPredecessor(),
//                edge.getPredecessor());
//    }

    // Set events[i] = coEvent, at the same time, we also update Rs or Ws.
    public void setEvent(int i, SharedEvent coEvent) {
        Rs.remove(events.get(i));
        events.set(i, coEvent);
        Rs.add(coEvent);
    }

    public boolean isPredecessorOf(OGNode pNode) {
        // There two cases where this node is the predecessor of pNode.
        // Case1: the node locates in the same thread as pNode.
        if (inThread.equals(pNode.inThread)) {
            return true;
        }

        // Case2: pNode is the first node of its thread, and the node locates in
        // the parent thread of pNode.
        // Sets.difference(set1, set2): This method returns a set containing all elements
        // that are contained by set1 and not contained by set2.
        // FIXME: how to get correct parent thread?
        OGPORState state = AbstractStates.extractStateByType(preState, OGPORState.class),
                pState = AbstractStates.extractStateByType(pNode.getPreState(),
                        OGPORState.class);
        assert state != null && pState != null;
        String pParent = pState.getParentThread(pNode.getInThread());
        return pParent != null
                && threadLoc.containsKey(pParent)
                && Objects.equals(inThread, pParent)
                && !threadLoc.containsKey(pNode.getInThread());
    }

    public void getNewRs(@NonNull Set<SharedEvent> rFlag) {
        for (int i = lheIndex + 1; i < events.size(); i++) {
            if (events.get(i).getAType() == READ)
                rFlag.add(events.get(i));
        }
    }

    // Remove the events after e0.
    // Used in revisiting.
    public void removeEventAfter(SharedEvent e0) {
        assert events.contains(e0)  : "When removing events for revisiting of a read, " +
                "the " +
                "read(" + e0 +  ") not in the node: " + this;
        // FIXME: set e0 as the lhe of this node?
        lheIndex = events.indexOf(e0) != lheIndex ? events.indexOf(e0) : lheIndex;

        // Remove events and relations.
        List<SharedEvent> rmEvents = new ArrayList<>();
        for (int i = lheIndex + 1; i < events.size(); i++) {
            SharedEvent e = events.get(i);
            rmEvents.add(e);
            e.removeAllRelations();
        }
        events.removeAll(rmEvents);
        rmEvents.forEach(Rs::remove);
        rmEvents.forEach(Ws::remove);

        // Remove edges.
        assert blockEdges.contains(e0.getInEdge()) : "Revisited read's inEdge must " +
                "locate in the block edges: " + e0.getInEdge();
        List<CFAEdge> rmEdges = new ArrayList<>();
        for (int i = blockEdges.indexOf(e0.getInEdge()) + 1; i < blockEdges.size(); i++) {
            rmEdges.add(blockEdges.get(i));
        }
        blockEdges.removeAll(rmEdges);
    }
}
