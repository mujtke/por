package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Triple;

import java.util.*;
import java.util.stream.Collectors;

public class OGNode {

    private final BlockStatus blockStatus;
    private final List<SharedEvent> events;
    private final int eventsNum;
    // s0 -- edge --> s1, edge => ogNode, ogNode.preARGState = s0.
    private ARGState preARGState;

    // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
    public Triple<Integer, Integer, Integer> threadStatus;

    // the predecessor and successor in trace.
    private OGNode predecessor;
    private OGNode successor;

    // the sequence number in a trace, the bigger, the more behind. And -1 mean behind infinitely.
    private int numInTrace = -1;

    public OGNode (List<SharedEvent> pEvents,
                   Triple<Integer, Integer, Integer> pThreadStatus,
                   BlockStatus pBlockStatus,
                   ARGState pPreARGState) {
        events = pEvents;
        events.forEach(e -> e.setOgNode(this));
        threadStatus = pThreadStatus;
        blockStatus = pBlockStatus;
        eventsNum = pEvents.isEmpty() ? 0 : events.size();
        preARGState = pPreARGState;
        predecessor = null;
        successor = null;
    }

    public OGNode copy() {
        List<SharedEvent> newEvents = new ArrayList<>(events);
        Triple newThreadStatus = Triple.of(threadStatus.getFirst(), threadStatus.getSecond(),
                threadStatus.getThird());
        return new OGNode(newEvents, newThreadStatus, blockStatus, preARGState);
    }

    // return true if 'this' is happen-before for o.
    public boolean hb(OGNode o) {
        if (this.numInTrace == o.numInTrace) {
            return true;
        } else if (this.numInTrace > o.numInTrace) {
            return false;
        } else { // this.numInTrace < o.numInTrace.
            Set<OGNode> nodes = new HashSet<>(); // collect the ogNodes that poBefore 'this'.
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
        return  threadStatus.equals(ogNode.threadStatus)
                && Objects.equals(events, ogNode.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockStatus, events, threadStatus);
    }
}
