package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;

import java.util.*;
import java.util.function.Predicate;
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

    // Restriction used in transfer.
    private final List<OGNode> happenBefore = new ArrayList<>();
    private final List<OGNode> happenAfter = new ArrayList<>();

    // Indicate whether this node is in a graph. The true means this node is in the trace
    // of the graph.
    private boolean inGraph;
    // Indicate whether the node has been added to the graph. It is also set as true when
    // the field inGraph is set as true.
    private boolean hasBeenAddedToGraph;
    // Index of the last-handled event.
    private int LHEIndex = -1;
    // DEBUG: used to indicate the position in event of the last added read event.
    private int lastReadIndex = -1;

    // FIXME: just used for the node that has been added to the graph. For a totally
    //  new node, set as null.
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
        hasBeenAddedToGraph = false;
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
        hasBeenAddedToGraph = false;
    }

    /**
     * @param memo Store the original object and its copied object.
     * @return The deep copy of this OGNode.
     */
    public OGNode deepCopy(Map<Object, Object> memo) {
        if (memo.containsKey(System.identityHashCode(this))) {
            // The current object has been copied somewhere.
            assert memo.get(System.identityHashCode(this)) instanceof OGNode;
            return (OGNode) memo.get(System.identityHashCode(this));
        }
        // Else, try to copy 'this' to a new object.
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
//        memo.put(this, nNode);
        memo.put(System.identityHashCode(this), nNode);

        // The threadsLoc and inThread are used to distinguish different OGNodes that has
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
        nNode.LHEIndex = this.LHEIndex;
        nNode.lastVisitedEdge = this.lastVisitedEdge;
        nNode.hasBeenAddedToGraph = this.hasBeenAddedToGraph;
        nNode.lastReadIndex = this.lastReadIndex;

        /* preState & sucState */
        nNode.preState = this.preState; /* Shallow copy. */
        nNode.sucState = this.sucState; /* Shallow copy. */

        // The left part will need to be copied in a deep way.
        /* events */
        this.events.forEach(e -> nNode.events.add(e.deepCopy(memo)));
        nNode.LHEIndex = this.LHEIndex;
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
        /* From read */
        this.fromRead.forEach(fr -> nNode.fromRead.add(fr.deepCopy(memo)));
        this.fromReadBy.forEach(frb -> nNode.fromReadBy.add(frb.deepCopy(memo)));

        /* Trace order */
        nNode.trBefore = this.trBefore != null ? this.trBefore.deepCopy(memo) : null;
        nNode.trAfter = this.trAfter != null ? this.trAfter.deepCopy(memo) : null;

        // Happen-before and happen-after.
//        this.happenBefore.forEach(hb -> nNode.happenBefore.add(hb.deepCopy(memo)));
//        this.happenAfter.forEach(ha -> nNode.happenAfter.add(ha.deepCopy(memo)));

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
        // When set this value as true, it also means the node has been added to the graph.
        if (pInGraph)
            this.hasBeenAddedToGraph = true;
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
        // FIXME: update lastReadIndex?
        // Because of the existence of the coNode, we should insert the event into some
        // proper location in events.
        if (LHEIndex == -1) {
            if (event.isRead()) {
                events.add(++lastReadIndex, event);
            } else {
                events.add(event);
            }
            // events.add(event);
        } else {
            int i = LHEIndex;
            if (i < events.size() - 1) {
                // If lastHandledEvent is not the last event, then we insert the event after it.
//                events.add(i + 1, event);
                if (event.isRead()) {
                    assert LHEIndex <= lastReadIndex :
                            "Read event should always be handled before write.";
                    lastReadIndex++;
                } else {
                    assert LHEIndex >= lastReadIndex :
                            "Write event should always be handled after read.";
                }
                events.add(i + 1, event);
            } else {
                // Else we just append the event to the end of the events.
//                events.add(event);
                if (event.isRead()) {
                    events.add(++lastReadIndex, event);
                } else {
                    events.add(event);
                }
            }
        }
        LHEIndex++;
        event.setInNode(this);
        if (event.isRead()) {
            Rs.add(event);
        } else if (event.isWrite()) {
            Ws.add(event);
        } else {
            throw new UnsupportedOperationException("Unknown access type in edge: "
                    + event.getInEdge() + ".");
        }
    }

    public void removeEvent(SharedEvent event) {
        // FIXME: update lastReadIndex?
        SharedEvent lastHandledEvent = LHEIndex < 0 ? null : events.get(LHEIndex);
        if (event == lastHandledEvent) LHEIndex--;
        events.remove(event);
        if (event.isRead()) {
            lastReadIndex--;
            Rs.remove(event);
        } else {
            Ws.remove(event);
        }

        assert lastReadIndex < events.size() : "Index out of bound.";
    }

    public SharedEvent getLastHandledEvent() {
        return (LHEIndex >= 0 && LHEIndex < events.size()) ? events.get(LHEIndex) : null;
    }

    public int getLheIndex() {
        return LHEIndex;
    }

    public void setLastHandledEvent(SharedEvent lastHandledEvent) {
        Preconditions.checkArgument(events.contains(lastHandledEvent),
                "Cannot set the event that is not in the node as the " +
                        "lastHandledEvent");
        this.LHEIndex = events.indexOf(lastHandledEvent);
    }

    /**
     * When we are building the nodeMap and reach a branch d, whose coEdge !d has been
     * handled before, we should update the lastHandledEvent because d may lead some
     * new edges we haven't seen yet. We reset the lastHandledEvent as the original last
     * event handled before coEdge.
     * @param coEdge the coEdge that belongs to the coNode of this node.
     */
    public void updateLastHandledEvent(CFAEdge coEdge) {
        SharedEvent lastHandledEvent = (LHEIndex < events.size() && LHEIndex >= 0) ?
                events.get(LHEIndex) : null;
        if (lastHandledEvent == null) {
            // We have set lastHandledEvent to be null in coEdge.inNode.
            // We just get this node by deep copy, so events should contain all events
            // in coEdge.inNode.
            // FIXME: we assume all atomic block access global vars, which means events
            //  must be not empty.
            Preconditions.checkArgument(!events.isEmpty(),
                    "Encountering the node has no shared events.");
            LHEIndex = events.size() - 1;
            lastHandledEvent = events.get(LHEIndex);
        }

        Preconditions.checkArgument(blockEdges.contains(coEdge));
        // Reset lastHandledEvent's inEdge should happen before coEdge.
        int i = blockEdges.indexOf(coEdge),
                j = blockEdges.indexOf(lastHandledEvent.getInEdge()),
                k = LHEIndex;
        SharedEvent resetLastHandledE = lastHandledEvent;
        while (i <= j) {
            k = events.indexOf(resetLastHandledE) - 1;
            if (k < 0) break;
            resetLastHandledE = events.get(k);
            j = blockEdges.indexOf(resetLastHandledE.getInEdge());
        }
        Preconditions.checkArgument(i > j,
                "Update lastHandledEvent failed.");
        LHEIndex = k;
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

     // Add new extracted events to the node.
    public void addEvents(List<SharedEvent> eList, boolean shouldKeepEList) {
        if (eList == null) return;
        List<SharedEvent> deepCopiedEvents = new ArrayList<>();
        eList.forEach(e -> {
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
                        // NOTE: add read event at the index equal to lastReadIndex,
                        //  update the latter after finishing the add.
                        events.add(++lastReadIndex, nE);
                        nE.setInNode(this);
                        Rs.add(nE);
                        if (shouldKeepEList)
                            deepCopiedEvents.add(nE);
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
                    // NOTE: For write events, just append them to the end of the events.
                    events.add(nE);
                    nE.setInNode(this);
                    Ws.add(nE);
                    if (shouldKeepEList)
                        deepCopiedEvents.add(nE);
                default:
            }
        });

        if (shouldKeepEList) {
            eList.clear();
            eList.addAll(deepCopiedEvents);
        }
    }

    // Replace coEdge nd with d.
    // d: the edge we meet in ARG.
    // nd: the coEdge of d that inside the node.
    public void replaceCoEdge(
            final Map<Integer, List<SharedEvent>> edgeVarMap, CFAEdge d, CFAEdge nd) {

        // Replace.
        int assumeEdgeIdx = blockEdges.indexOf(nd);

        // Remove all shared events in or after the nd.
        List<SharedEvent> toRemove = events.stream()
                .filter(e -> blockEdges.indexOf(e.getInEdge()) >= assumeEdgeIdx)
                .collect(Collectors.toList());

        // Before removing these events, clear their relations firstly.
        toRemove.forEach(SharedEvent::removeAllRelations);
//        events.removeAll(toRemove);
//        toRemove.forEach(Rs::remove);
//        toRemove.forEach(Ws::remove);
        // FIXME: update the lastReadIndex.
        toRemove.forEach(this::removeEvent);
        // Remove assumeEdge and all edges after it.
        blockEdges.removeIf(e -> blockEdges.indexOf(e) >= assumeEdgeIdx);
        blockEdges.add(d);

        // Update the last-visited edge.
        setLastVisitedEdge(d);

        // FIXME: Does last-visited event get updated only when revisiting?
        //  And what if nd contains more than one sharedEvent?
    }

    public boolean shouldRevisit() {
        // FIXME
        return LHEIndex < 0 || LHEIndex < events.size() - 1;
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
        for (int i = LHEIndex + 1; i < events.size(); i++) {
            if (events.get(i).getAType() == READ)
                rFlag.add(events.get(i));
        }
    }

    // Remove the events after e0.
    // Used in revisiting.
    // FIXME: remove events that locates in the same node with e0?
    public void removeEventAfter(SharedEvent e0) {
        assert events.contains(e0)  : "When removing events for revisiting of a read, " +
                "the read(" + e0 +  ") not in the node: " + this;
        // FIXME: set e0 as the lhe of this node?
        LHEIndex = events.indexOf(e0) != LHEIndex ? events.indexOf(e0) : LHEIndex;

        // Remove events and relations.
        List<SharedEvent> rmEvents = new ArrayList<>();
        for (int i = LHEIndex + 1; i < events.size(); i++) {
            SharedEvent e = events.get(i);
            // FIXME: remove events whose inEdge is equal to or after the e0.inEdge?
            if (blockEdges.indexOf(e.getInEdge()) >= blockEdges.indexOf(e0.getInEdge())) {
                rmEvents.add(e);
                e.removeAllRelations();
            }
        }
        rmEvents.forEach(this::removeEvent);

        // Remove edges.
        assert blockEdges.contains(e0.getInEdge()) : "Revisited read's inEdge must " +
                "locate in the block edges: " + e0.getInEdge();
        List<CFAEdge> rmEdges = new ArrayList<>();
        for (int i = blockEdges.indexOf(e0.getInEdge()) + 1; i < blockEdges.size(); i++) {
            rmEdges.add(blockEdges.get(i));
        }
        blockEdges.removeAll(rmEdges);
    }

    // Remove events that come from the edge.
    public void removeEvent(CFAEdge edge) {
        // FIXME: update lastReadIndex.
        Predicate<SharedEvent> filter = e -> Objects.equals(edge, e.getInEdge());
//        events.removeIf(filter);
//        Rs.removeIf(filter);
//        Ws.removeIf(filter);
        List<SharedEvent> toRemove = events.stream().filter(filter).collect(Collectors.toList());
        toRemove.forEach(this::removeEvent);
    }

    public boolean hasBeenAddedToGraph() {
        return hasBeenAddedToGraph;
    }

    public void addEdge(CFAEdge edge, List<SharedEvent> sharedEvents) {
        blockEdges.add(edge);
        if (sharedEvents != null)
            addEvents(sharedEvents, false);
    }

    public void setLheIndex(int pLheIndex) {
        LHEIndex = pLheIndex;
    }

    // Some events get deleted during the revisit, for example: in X = Y1,
    // Write(X) gets deleted because X reads from a new location.
    // We need to re-add Write(X) when it should be done.
    public void addDeletedEvents(List<SharedEvent> sharedEvents, CFAEdge edge) {
        // Precondition: this node contains the edge.
        SharedEvent lastEvent = events.get(events.size() - 1);
        assert lastEvent != null;
        if (!Objects.equals(edge, lastEvent.getInEdge())) {
            // If the last event's inEdge != edge, then we don't need to add the sharedEvents.
            return;
        }

        // Else, we need to add the events.
        // At least, one event from the edge is in events. So, we add events of
        // sharedEvents form index 1 at least.
        int addedEventsNum = 0;
        for (int i = 0; i < sharedEvents.size(); i++) {
            SharedEvent e = events.get(i);
            addedEventsNum++;
            // FIXME: Assumption: in an edge, there is a read or write to the same var at
            //  most.
            if (e.getAType() == lastEvent.getAType()
                    && Objects.equals(e.getVar().getName(), lastEvent.getVar().getName())) {
                break;
            }
        }

        // FIXME: a strong assumption: the order of the events keeps unchanged when these
        //  events are added to the node.
        addEvents(sharedEvents.subList(addedEventsNum, sharedEvents.size()), false);
    }

    public void addEdge(CFAEdge edge) {
        blockEdges.add(edge);
    }

    public List<OGNode> getHappenBefore() { return happenBefore; }

    public List<OGNode> getHappenAfter() { return happenAfter; }
}
