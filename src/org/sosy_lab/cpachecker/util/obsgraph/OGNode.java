package org.sosy_lab.cpachecker.util.obsgraph;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.SMTHeapReadAndWriteTest;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.sosy_lab.cpachecker.util.obsgraph.SharedEvent.AccessType.READ;

public class OGNode implements Copier<OGNode> {

    // This variable is used to distinguish two edges that locate in the same
    // loop but with different loop depth.
    // loopDepth = 0 means the node is not in a loop.
    private int loopDepth = 0;
    // NOTE: we will store every edge we meet, i.e., include those access to the
    //  thread-local vars but are inside the block.
    private final List<CFAEdge> blockEdges = new ArrayList<>();
    private boolean simpleNode; /* contains only one edge */
    private final Set<SharedEvent> Rs = new HashSet<>();
    private final Set<SharedEvent> Ws = new HashSet<>();
    // We add events in the order we meet them.
    private final List<SharedEvent> events = new ArrayList<>();

    // s0 -- edge --> s1, edge => ogNode, ogNode.preState = s0.
    private ARGState preState;
    private ARGState sucState;

    private String inThread;
    // Use 'threadLoc' to recognize the OGNodes that have the same edges
    // but belong to different program locations, e.g., different nodes
    // come from the same edge 'X = 1' inside a loop.
    private Map<String, String> threadLoc = new HashMap<>();

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
    private boolean inGraph = false;
    // Indicate whether the node has been added to the graph. It is also set as true when
    // the field inGraph is set as true.
    private boolean hasBeenAddedToGraph = false;
    // Index of the last-handled event. For handled events, we don't handle them again.
    // private int LHEIndex = -1;
    private int LHEIndex = -2;

    // FIXME: just used for the node that has been added to the graph. For a totally
    //  new node, set as null.
    private CFAEdge lastVisitedEdge;

    public OGNode(List<CFAEdge> pBlockEdges,
            boolean pSimpleNode,
            ARGState preState,
            ARGState sucState) {
        blockEdges.addAll(pBlockEdges);
        simpleNode = pSimpleNode;
        if (sucState != null)
            setThreadInfo(sucState);
        setPreState(preState);
        setSucState(sucState);
    }

    public OGNode() {

    }

    public void updatePreAndSucState(ARGState pPreState, ARGState pSucState) {
        if (pPreState != null)
            setPreState(pPreState);
        if (pSucState != null)
            setSucState(pSucState);
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
        OGNode nNode = new OGNode();
        nNode.blockEdges.addAll(this.blockEdges);
        // Put the copy into memo.
        memo.put(System.identityHashCode(this), nNode);

        // The threadsLoc and inThread are used to distinguish different OGNodes that has
        // the same 'blockEdges', so they should be copied deeply.
        // Because String is immutable, so shallow copy has the same effect with a deep
        // one.
        nNode.loopDepth = this.loopDepth;
        nNode.simpleNode = this.simpleNode;
        // FIXME: When we copy the node from the nodeMap, we may miss the threadLoc.
        nNode.inThread = String.valueOf(this.inThread);
        nNode.threadLoc.putAll(this.threadLoc); /* Deep copy */
        nNode.inGraph = this.inGraph;
        nNode.LHEIndex = this.LHEIndex;
        nNode.lastVisitedEdge = this.lastVisitedEdge;
        nNode.hasBeenAddedToGraph = this.hasBeenAddedToGraph;

        /* preState & sucState */
        nNode.preState = this.preState; /* Shallow copy. */
        nNode.sucState = this.sucState; /* Shallow copy. */

        // The left part will need to be copied in a deep way.
        /* events */
        this.events.forEach(e -> nNode.events.add(e.deepCopy(memo)));
        nNode.LHEIndex = this.LHEIndex;
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
    public boolean equals(Object o) { // Note: handle this carefully.
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

    public OGNode getPredecessor() {
        return this.predecessor;
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

    public OGNode getTrAfter() {
        return this.trAfter;
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

    // Get write to the same var which is also accessed by e.
    // NOTE: in a node, there is one write to a same var at most.
    public SharedEvent getWriteToSameVar(SharedEvent e) {
        List<SharedEvent> sameWrite =
                Ws.stream().filter(e::accessSameVarWith).collect(Collectors.toList());
        if (!sameWrite.isEmpty()) {
            assert sameWrite.size() == 1 :
                    "More then one write to a same var is not allowed in a node.";
            return sameWrite.get(0);
        }

        return null;
    }

    // NOTE: similarly, there is one read to a same var at most.
    public SharedEvent getReadToSameVar(SharedEvent e) {
        List<SharedEvent> sameRead =
                Rs.stream().filter(e::accessSameVarWith).collect(Collectors.toList());
        if (!sameRead.isEmpty()) {
            assert sameRead.size() == 1 :
                    "More then one write to a same var is not allowed in a node.";
            return sameRead.get(0);
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
        assert ogporState != null;
        assert ogporState.getThreads() != null;
        this.inThread = ogporState.getInThread();
        this.threadLoc.putAll(ogporState.getThreads());
    }

    public List<SharedEvent> getEvents() {
        return events;
    }

    public void removeEvent(SharedEvent e) {
        assert events.contains(e) && (Rs.contains(e) || Ws.contains(e)) :
                "Trying to remove a event not in the node!";
        int i = events.indexOf(e);
        if (LHEIndex >= 0 && i <= LHEIndex) {
            // LHEIndex >= 0 means we have updated it, i.e., the node is not totally new.
            // Else, LHEIndex = -1, the node is totally new, deleting an event won't
            // change the LHEIndex.
            LHEIndex--;
        }
        events.remove(e);
        // Don't forget to remove event from Rs or Ws.
        if (e.isRead()) {
            Rs.remove(e);
        } else {
            Ws.remove(e);
        }
    }

    public void removeEdges(Collection<CFAEdge> toRemove) {
        // Debug, check the legality to remove all edges in toRemove.
        List<CFAEdge> shouldKeep =
                events.stream().map(SharedEvent::getInEdge).collect(Collectors.toList());
        assert toRemove.stream().noneMatch(shouldKeep::contains) :
                "Cannot remove an edge some of whose corresponding events are still in the node";

        blockEdges.removeAll(toRemove);
    }

    public SharedEvent getLastHandledEvent() {
        return (LHEIndex >= 0 && LHEIndex < events.size()) ? events.get(LHEIndex) : null;
    }

    public int getLheIndex() {
        return LHEIndex;
    }

    public void setLastHandledEvent(SharedEvent lastHandledEvent) {
        assert events.contains(lastHandledEvent) :
                "Cannot set the event not in the node as the new last-handled event";
        int newLHEIndex = events.indexOf(lastHandledEvent);
        assert newLHEIndex == LHEIndex + 1 :
                "Index error when setting new last-handled event, expect " + (LHEIndex + 1)
                + ", but " + newLHEIndex + " given.";
        this.LHEIndex = newLHEIndex;
    }

    public boolean contains(CFAEdge edge) {
        return blockEdges.contains(edge);
    }

    public void setLastVisitedEdge(CFAEdge cfaEdge) {
        lastVisitedEdge = cfaEdge;
    }

    /**
     * @param eList source of the events that we are going to add.
     * @implNote It should be guaranteed that all events we will add are deep copies of
     * the corresponding events stored in {@link OGInfo#getEdgeVarMap()}. To do so,
     * we copy the events in {@param eList} before we add events to the node.
     */
    public void addEvents(List<SharedEvent> eList) {
        if (eList == null) return;
        List<SharedEvent> eventCopies = new ArrayList<>();
        eList.forEach(e -> {
            Map<Object, Object> memo = new HashMap<>();
            eventCopies.add(e.deepCopy(memo));
        });
        eventCopies.forEach(e -> {
            switch (e.getAType()) {
                case READ:
                    SharedEvent sameR = getReadToSameVar(e),
                            sameW = getWriteToSameVar(e);
                    if (sameR != null || sameW != null) {
                        // For the reads to the same var, only the first will be added.
                        // If there is a write that accesses the same var with r, then r could be
                        // ignored, because it will always read from the write inside the node.
                    } else { // sameR == null && sameW == null.
                        addEvent(e);
                    }
                    break;

                case WRITE:
                    sameW = getWriteToSameVar(e);
                    addEvent(e);
                    // For multiple writes to the same var, only the last one will be added.
                    if (sameW != null) {
                        // The sameW will be covered by e, so we remove it.
                        assert events.contains(sameW) && Ws.contains(sameW) :
                                "Trying to cover a write event not existed!";
                        removeEvent(sameW);
                        // Transfer relations owned by sameW to e.
                        sameW.transferRelationsTo(e);
                    }
                    break;

                case DUMMY:
                    addEvent(e);
                    break;

                default:
            }
        });
    }

    private void addEvent(SharedEvent e) {
        assert !events.contains(e) : "Trying to add an existed event again!";
        events.add(e);
        e.setInNode(this);
        if (e.isRead()) {
            Rs.add(e);
        } else if (e.isWrite()) {
            Ws.add(e);
        } else if (e.isDummy()) {
            Ws.add(e);
        }
    }

    /**
     * Replace oldEdge with newEdge.
     * @param oldEdge the edge that is inside the node and coEdge of the edge we meet in ARG.
     * @param newEdge the edge we met in ARG.
     */
    public void replaceCoEdge(CFAEdge oldEdge, CFAEdge newEdge) {
        assert blockEdges.contains(oldEdge) :
                "Cannot replace an edge not in the node: " + oldEdge;
        // Replace.
        int assumeEdgeIdx = blockEdges.indexOf(oldEdge);

        // Remove all shared events in and after the oldEdge.
        List<SharedEvent> toRemove = events.stream()
                .filter(e -> blockEdges.indexOf(e.getInEdge()) >= assumeEdgeIdx)
                .collect(Collectors.toList());

        // Before removing these events, clear their relations firstly.
        toRemove.forEach(SharedEvent::removeAllRelations);
        toRemove.forEach(this::removeEvent);
        // Remove assumeEdge and all edges after it.
        blockEdges.removeIf(e -> blockEdges.indexOf(e) >= assumeEdgeIdx);
        blockEdges.add(newEdge);

        // FIXME: Does last-visited event get updated only when revisiting?
        //  And what if nd contains more than one sharedEvent?
    }

    /**
     * @return Whether the node should be revisited.
     * FIXME
     */
    public boolean shouldRevisit() {
        // FIXME?
        if (!inGraph)
            return false;
        if (events.isEmpty())
            return false;
        if (LHEIndex == -2) {
            // We haven't performed any revisit for the node, and still not meet
            // the end of the node.
            return false;
        }

        // Else, check whether there are some events we should revisit.
        // FIXME: As we regard an edge atomic, i.e., we always keep all the events that come
        // form the same edge, so when computing the events need to revisit, if there are some
        // events locating the same edge with lhe(events.get(LHEIndex)), then we will ignore them.
        int handledIndex = LHEIndex;
        if (0 <= LHEIndex && LHEIndex < events.size() - 1) {
            // Check the events in the same edge with events.get(LHEIndex).
            for (int i = LHEIndex + 1; i < events.size(); i++) {
                if (Objects.equals(events.get(i).getInEdge(), events.get(LHEIndex).getInEdge())) {
                    handledIndex = i;
                    continue;
                }
                break;
            }
        }
        return handledIndex < events.size() - 1;
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
                for (SharedEvent w : Ws)
                    for (SharedEvent r : w.getFromReadBy())
                        if (other.Rs.contains(r))
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

    // Get the read events that need to visit when we are visiting the corresponding node.
    public void getRsNeedToVisit(@NonNull Set<SharedEvent> rFlag) {
        Rs.forEach(e -> {
            if (events.indexOf(e) > LHEIndex && e.getReadFrom() == null)
                rFlag.add(e);
        });
    }

    // Get the write events that need to visit.
    public void getWsNeedToVisit(@NonNull Set<SharedEvent> wFlag) {
        wFlag.addAll(Ws);
    }

    public Set<OGNode> getAllMoPredecessors() {
        Set<OGNode> result = new HashSet<>(),
                visitedNodes = new HashSet<>(),
                waitlist = new HashSet<>(moAfter),
                tmp = new HashSet<>();
        while (!waitlist.isEmpty()) {
            result.addAll(waitlist);
            waitlist.forEach(n -> {
                if (!visitedNodes.contains(n)) {
                    tmp.addAll(n.moAfter);
                    visitedNodes.add(n);
                }
            });
            waitlist.clear();
            waitlist.addAll(tmp);
            tmp.clear();
        }

        return result;
    }

    // FIXME
    // Remove the events after e0.
    // Used in revisiting.
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
    public void removeEventsFromEdge(CFAEdge edge) {
        Predicate<SharedEvent> filter = e -> Objects.equals(edge, e.getInEdge());
        List<SharedEvent> toRemove = events.stream().filter(filter).collect(Collectors.toList());
        toRemove.forEach(this::removeEvent);
    }

    public boolean hasBeenAddedToGraph() {
        return hasBeenAddedToGraph;
    }

    public void setHasBeenAddedToGraph(boolean pHasBeenAddedToGraph) {
        hasBeenAddedToGraph = pHasBeenAddedToGraph;
    }

    // FIXME
    public void addEdgeWithEvents(CFAEdge edge, List<SharedEvent> sharedEvents) {
        blockEdges.add(edge);
        if (sharedEvents != null)
            addEvents(sharedEvents);
    }

    public void setLHEIndex(int pLHEIndex) {
        LHEIndex = pLHEIndex;
    }

    public List<OGNode> getHappenBefore() { return happenBefore; }

    public List<OGNode> getHappenAfter() { return happenAfter; }

    /**
     * For rf, mo, fr and other relations that could be defined on shared events, we update
     * the relations between nodes accordingly when relations between events changed.
     */
    public void removeMoAfter(OGNode maNode) {
        assert maNode != null && moAfter.contains(maNode);
        moAfter.remove(maNode);
    }

    public void removeMoBefore(OGNode mbNode) {
        assert mbNode != null && moBefore.contains(mbNode);
        moBefore.remove(mbNode);
    }

    public void removeReadFrom(OGNode rfNode) {
        assert rfNode != null && readFrom.contains(rfNode);
        readFrom.remove(rfNode);
    }

    public void removeReadBy(OGNode rbNode) {
        assert rbNode != null && readBy.contains(rbNode);
        readBy.remove(rbNode);
    }

    public void removeFromRead(OGNode frNode) {
        assert frNode != null && fromRead.contains(frNode);
        fromRead.remove(frNode);
    }

    public void removeFromReadBy(OGNode frbNode) {
        assert frbNode != null && fromReadBy.contains(frbNode);
        fromReadBy.remove(frbNode);
    }

    public boolean readBy(OGNode node) {
        assert node != null;
        return readBy.contains(node) && node.readFrom.contains(this);
    }

    public boolean readFrom(OGNode node) {
        assert node != null;
        return readFrom.contains(node) && node.readBy.contains(this);
    }

    public void setReadBy(OGNode rbNode) {
        assert rbNode != null;
        readBy.add(rbNode);
    }

    public void setReadFrom(OGNode rfNode) {
        assert rfNode != null;
        readFrom.add(rfNode);
    }

    public boolean moBefore(OGNode node) {
        assert node != null;
        return moBefore.contains(node) && node.moAfter.contains(this);
    }

    public boolean moAfter(OGNode node) {
        assert node != null;
        return moAfter.contains(node) && node.moBefore.contains(this);
    }

    public void setMoBefore(OGNode mbNode) {
        assert mbNode != null;
        moBefore.add(mbNode);
    }

    public void setMoAfter(OGNode maNode) {
        assert maNode != null;
        moAfter.add(maNode);
    }

    public boolean fromRead(OGNode node) {
        assert node != null;
        return fromRead.contains(node) && node.fromReadBy.contains(this);
    }

    public boolean fromReadBy(OGNode node) {
        assert node != null;
        return fromReadBy.contains(node) && node.fromRead.contains(this);
    }

    public void setFromRead(OGNode frNode) {
        assert frNode != null;
        fromRead.add(frNode);
    }

    public void setFromReadBy(OGNode frbNode) {
        fromReadBy.add(frbNode);
    }

    public void setPredecessor(OGNode pre) {
        predecessor = pre;
    }
    public void removePredecessor() {
        assert predecessor != null;
        predecessor = null;
    }

    public void setSuccessor(OGNode suc) {
        assert suc != null && !successors.contains(suc);
        successors.add(suc);
    }

    public void removeSuccessor(OGNode suc) {
        assert suc != null && successors.contains(suc);
        successors.remove(suc);
    }

    public void setTrBefore(OGNode tbNode) {
        assert tbNode != null;
        trBefore = tbNode;
    }

    public void removeTrBefore() {
        assert trBefore != null;
        trBefore = null;
    }

    public void setTrAfter(OGNode taNode) {
        assert taNode != null;
        trAfter = taNode;
    }

    public void removeTrAfter() {
        assert trAfter != null;
        trAfter = null;
    }

    public void setHappenBefore(OGNode hbNode) {
        assert hbNode != null && !happenBefore.contains(hbNode);
        happenBefore.add(hbNode);
    }

    public void setHappenAfter(OGNode haNode) {
        assert haNode != null && !happenAfter.contains(haNode);
        happenAfter.add(haNode);
    }

    public void removeHappenBefore(OGNode hbNode) {
        assert hbNode != null && happenBefore.contains(hbNode);
        happenBefore.remove(hbNode);
    }

    public void removeHappenAfter(OGNode haNode) {
        assert haNode != null && happenAfter.contains(haNode);
        happenBefore.remove(haNode);
    }

    /**
     * Note: here is a strong assumption: before removing all relations for this,
     * we should have remove all relations for all events that belongs to this node.
     * @implNote For rf, fr and mo, we don't need to remove them here, we should have
     * done that when we remove all relations for the events in the node.
     */
    public void removeAllRelations() {
        // Remove po (and rf, fr, mo).
        OGNode tmp;
        // po.
        if (predecessor != null) {
            predecessor.removeSuccessor(this);
            removePredecessor();
        }
        successors.forEach(OGNode::removePredecessor);
        successors.clear();

        // rf.
//        readFrom.forEach(rf -> rf.removeReadBy(this));
//        readFrom.clear();
//        readBy.forEach(rb -> rb.removeReadFrom(this));
//        readBy.clear();
        assert readFrom.isEmpty() && readBy.isEmpty();

        // fr.
//        fromRead.forEach(fr -> fr.removeFromReadBy(this));
//        fromRead.clear();
//        fromReadBy.forEach(frb -> frb.removeFromRead(this));
//        fromReadBy.clear();
        assert fromRead.isEmpty() && fromReadBy.isEmpty();

        // mo.
//        moBefore.forEach(mb -> mb.removeMoAfter(this));
//        moBefore.clear();
//        moAfter.forEach(ma -> ma.removeMoBefore(this));
//        moAfter.clear();
        assert moBefore.isEmpty() && moAfter.isEmpty();

        // Remove to in another place.
    }

    /**
     * @return list of the events that need to revisit.
     * @implNote When calling this method, the node should be re-visitable, i.e.,
     * shouldRevisit() return true.
     */
    public List<SharedEvent> getRE() {
//        assert shouldRevisit() :
//                "Trying to get re-visitable events in a node that not re-visitable.";
        List<SharedEvent> RE = events.stream().filter(e -> events.indexOf(e) > LHEIndex)
                .collect(Collectors.toList());
//        assert !RE.isEmpty();
        return RE;
    }

    /**
     * @return events used for checking conflict when transferring graph along the ARG.
     * FIXME: Check the writes after the last-handled(LHE) event only?
     */
    public List<SharedEvent> getToCheckEvents() {
//        return Ws.stream().filter(w -> events.indexOf(w) > LHEIndex)
//                .collect(Collectors.toList());
        return new ArrayList<>(Ws);
    }

    // Tests.
    public void checkLHE(SharedEvent e) {
        assert getLastHandledEvent() == e : "Last-handled event is not set correctly!";
    }
}