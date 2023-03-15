package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Triple;

import java.util.*;

public class OGNode {

    private final BlockStatus blockStatus;
    private final List<SharedEvent> events;
    private final int eventsNum;
    // s0 -- edge --> s1, edge => ogNode, ogNode.preARGState = s0.
    private ARGState preARGState;

    // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
    private Triple<Integer, Integer, Integer> threadStatus;
    private final String transThread;

    // the predecessor and successor in trace.
    private OGNode predecessor;
    private OGNode successor;

    // the sequence number in a trace, the bigger, the more behind. And -1 mean behind infinitely.
    private int numInTrace = -1;

    // judge whether this node is the first one of its thread.
    private boolean isFirstOneInThread = false;

    public Triple<Integer, Integer, Integer> spawnedThread = null;

    public OGNode(List<SharedEvent> pEvents,
                   Triple<Integer, Integer, Integer> pThreadStatus,
                   BlockStatus pBlockStatus,
                   ARGState pPreARGState,
                   String pTransThread) {
        events = pEvents;
        events.forEach(e -> e.setOgNode(this));
        threadStatus = pThreadStatus;
        transThread = pTransThread;
        blockStatus = pBlockStatus;
        eventsNum = pEvents.isEmpty() ? 0 : events.size();
        preARGState = pPreARGState;
        predecessor = null;
        successor = null;
    }

    public OGNode(OGNode other) {
        // return the deep copy of the other, because 'other' is newly created, so we don't have to
        // mind the order relations and the trace order.
        events = new ArrayList<>();
        // we don't handle the order relations here, instead of in ObsGraph.
        for (SharedEvent e : other.events) {
            SharedEvent eCopy = new SharedEvent(e.getVar(), e.getAType());
            events.add(eCopy);
        }
        events.forEach(e -> e.setOgNode(this));
        threadStatus = Triple.of(other.threadStatus.getFirst(),
                other.threadStatus.getSecond(),
                other.threadStatus.getThird());
        transThread = other.transThread;
        blockStatus = other.blockStatus;
        eventsNum = events.size();
        preARGState = other.preARGState;
    }

    public void setFirstOneInThread(boolean firstOneInThread) {
        isFirstOneInThread = firstOneInThread;
    }

    public boolean isFirstOneInThread() {
        return isFirstOneInThread;
    }

    public OGNode copy() {
        List<SharedEvent> newEvents = new ArrayList<>();
        // we don't handle the order relations here, instead of in ObsGraph.
        for (SharedEvent e : events) {
            SharedEvent eCopy = new SharedEvent(e.getVar(), e.getAType());
            newEvents.add(eCopy);
        }
        Triple<Integer, Integer, Integer> newThreadStatus = Triple.of(threadStatus.getFirst(),
                threadStatus.getSecond(),
                threadStatus.getThird());
        // we don't handle the trace order here, too.
        OGNode copy = new OGNode(newEvents, newThreadStatus, blockStatus, preARGState, transThread);
        copy.setFirstOneInThread(isFirstOneInThread); // TODO: should copy this?

        return copy;
    }

    // return true if 'this' is happen-before for o.
    public boolean hb(OGNode o) {
        if (this.numInTrace == o.numInTrace) {
            return true;
        } else if (this.numInTrace > o.numInTrace) {
            return false;
        } else { // this.numInTrace < o.numInTrace.
            Set<OGNode> nodes = new HashSet<>(); // collect the ogNodes that poAfter 'this'.
            for (int i = 0; i < eventsNum; i++) {
                List<SharedEvent> poBefore = events.get(i).getPoBefore();
                poBefore.forEach(e -> nodes.add(e.getOgNode()));
            }
            boolean isHbO = false;
            for (OGNode n : nodes) {
                isHbO |= n.hb(o);
                if (isHbO) {
                    break;
                }
            }
            return isHbO;
        }
    }

    public int getNumInTrace() {
        return numInTrace;
    }

    public void setNumInTrace(int numInTrace) {
        this.numInTrace = numInTrace;
    }

    public OGNode getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(OGNode predecessor) {
        this.predecessor = predecessor;
    }

    public OGNode getSuccessor() {
        return successor;
    }

    public void setSuccessor(OGNode successor) {
        this.successor = successor;
    }

    public ARGState getPreARGState() {
        return preARGState;
    }

    public void setPreARGState(ARGState preARGState) {
        this.preARGState = preARGState;
    }

    public Triple<Integer, Integer, Integer> getThreadStatus() {
        return threadStatus;
    }

    public void setThreadStatus(Triple<Integer, Integer, Integer> pThreadStatus) {
        threadStatus = Triple.of(pThreadStatus.getFirst(), pThreadStatus.getSecond(),
                pThreadStatus.getThird());
    }

    public int getEventsNum() {
        return eventsNum;
    }

    public List<SharedEvent> getEvents() {
        return events;
    }

    public BlockStatus getBlockStatus() {
        return blockStatus;
    }

    @Override
    public boolean equals(Object o) { // handle this carefully.
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OGNode ogNode = (OGNode) o;
        // TODO: only using threadStatus and transThread and blockStatus?
        // Node1 == Node2, maybe only the third nums in threadStatus must be equal.
        if (threadStatus.getThird() == null) {
            return false;
        }
        return  threadStatus.getThird().equals(ogNode.threadStatus.getThird())
                && blockStatus.equals(ogNode.blockStatus)
                && transThread.equals(ogNode.transThread);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockStatus, threadStatus, transThread);
    }

    @Override
    public String toString() {
        return "parARGState: " + preARGState.getStateId();
    }
}
