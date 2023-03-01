package org.sosy_lab.cpachecker.cpa.por.ogpor;


import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadInfoProvider;

import java.util.HashMap;
import java.util.Map;

public class OGPORState implements AbstractState, ThreadInfoProvider, Graphable {

    // record the info: transferInEdge's threadId, threadLocations
    private final MultiThreadState multiThreadState;

    // record the thread status by using map.
    // String :: thread id.
    // Triple :: threadStatus
    private final Map<String, Triple<Integer, Integer, Integer>> threadStatusMap;

    @Override
    public int hashCode() {
        return multiThreadState.hashCode() + threadStatusMap.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OGPORState) || obj == null) {
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
    }

    public OGPORState(final OGPORState pOther) {
        assert pOther != null;
        MultiThreadState multiOther = pOther.getMultiThreadState();
        Map<String, Triple<Integer, Integer, Integer>> mapOther = pOther.getThreadStatusMap();
        // the 'locations' in MultiThreadState is a 'final' field, so the shallow copy will lead
        // all copies point to a same location.
        this.multiThreadState = new MultiThreadState(new HashMap<>(multiOther.getThreadLocations()),
                multiOther.getTransThread(), multiOther.isFollowFunctionCalls());
        this.threadStatusMap = new HashMap<>(mapOther);
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
    }
}
