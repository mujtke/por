package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Triple;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OGNode {

    private final BlockStatus blockStatus;
    private final List<SharedEvent> events;
    private final int eventsNum;
    // s0 -- edge --> s1, edge => ogNode, ogNode.preARGState = s0.
    private ARGState preARGState;

    // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
    public final Triple<Integer, Integer, Integer> threadStatus;


    public OGNode (List<SharedEvent> pEvents,
                   Triple<Integer, Integer, Integer> pThreadStatus,
                   BlockStatus pBlockStatus,
                   ARGState pPreARGState) {
        events = pEvents;
        threadStatus = pThreadStatus;
        blockStatus = pBlockStatus;
        eventsNum = pEvents.isEmpty() ? 0 : events.size();
        preARGState = pPreARGState;
    }

    public Triple<Integer, Integer, Integer> getThreadStatus() {
        return threadStatus;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OGNode ogNode = (OGNode) o;
        return  blockStatus.equals(ogNode.blockStatus)
                && Objects.equals(events, ogNode.events)
                && threadStatus.equals(ogNode.threadStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockStatus, events, threadStatus);
    }
}
