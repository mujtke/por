package org.sosy_lab.cpachecker.cpa.por.ogpor;


import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.obsgraph.BlockStatus;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadInfoProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OGPORState implements AbstractState, ThreadInfoProvider, Graphable {

    // record the info: transferInEdge's threadId, threadLocations
    private final MultiThreadState multiThreadState;

    // record the thread status by using map.
    // String :: thread id.
    // Triple :: threadStatus
    private final Map<String, Triple<Integer, Integer, Integer>> threadStatusMap;

    private BlockStatus blockStatus = BlockStatus.NOT_IN_BLOCK;

    // the table records all global vars' last accesses in a trace.
//    private final Map<Var, SharedEvent> lastAccessTable;

    // when a thread is newly created, we put its num into 'waitingForThreads' to represent that
    // we still don't meet the first shared event of the thread.
    private final Set<Integer> waitingThreads = new HashSet<>();

    // indicate that whether we need to delay putting the state into the waitlist.
    private boolean needDelay = false;

    // TODO
    public boolean graphsOriginallyEmpty = true;
    public Triple<Integer, Integer, Integer> spawnThread = null;

    public boolean isNeedDelay() {
        return needDelay;
    }

    public void setNeedDelay(boolean needDelay) {
        this.needDelay = needDelay;
    }

    public Set<Integer> getWaitingThreads() {
        return waitingThreads;
    }

    public BlockStatus getBlockStatus() {
        return blockStatus;
    }

    public void setBlockStatus(BlockStatus blockStatus) {
        this.blockStatus = blockStatus;
    }

    @Override
    public int hashCode() {
        return multiThreadState.hashCode() + threadStatusMap.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        //debug.
        // TODO: this influence the cover status.
        if (true) {
            return false;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OGPORState)) {
            return false;
        }
        OGPORState other = (OGPORState) obj;
        return other.getMultiThreadState().equals(this.multiThreadState)
                && other.getThreadStatusMap().equals(this.threadStatusMap);
    }

    @Override
    public String toString() {
        return multiThreadState.getThreadLocations().toString() + threadStatusMap.toString();
    }

    @Override
    public String toDOTLabel() {
        StringBuilder str = new StringBuilder();
        str.append(threadStatusMap.toString());
        str.append("\n");
        str.append(multiThreadState.getThreadLocations().toString());
        str.append("\n");
        str.append("active thread: " + multiThreadState.getTransThread());
        return str.toString();
    }

    @Override
    public boolean shouldBeHighlighted() {
        return true;
    }

    public OGPORState(
        MultiThreadState pState,
        Map<String, Triple<Integer, Integer, Integer>> pThreadStatus) {
        multiThreadState = pState;
        threadStatusMap = pThreadStatus;
//        lastAccessTable = new HashMap<>();
    }

    public OGPORState(final OGPORState pOther) {
        assert pOther != null;
        MultiThreadState multiOther = pOther.getMultiThreadState();
        Map<String, Triple<Integer, Integer, Integer>> mapOther = pOther.getThreadStatusMap();
//        Map<Var, SharedEvent> tableOther = pOther.getLastAccessTable();
        // the 'locations' in MultiThreadState is a 'final' field, so the shallow copy will lead
        // all copies point to a same location.
        this.multiThreadState = new MultiThreadState(new HashMap<>(multiOther.getThreadLocations()),
                multiOther.getTransThread(), multiOther.isFollowFunctionCalls());
        this.threadStatusMap = new HashMap<>(mapOther);
//        this.lastAccessTable = new HashMap<>(tableOther);
    }

    public Map<String, Triple<Integer, Integer, Integer>> getThreadStatusMap() {
        return threadStatusMap;
    }

    public MultiThreadState getMultiThreadState() {
        return multiThreadState;
    }

    @Override
    public void removeThreadId(String pThreadId) {
        multiThreadState.removeThreadId(pThreadId);
        threadStatusMap.remove(pThreadId); // exit thread for threadStatusMap.
    }
}
