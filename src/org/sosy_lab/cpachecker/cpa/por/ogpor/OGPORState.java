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

public class OGPORState implements AbstractState, Graphable {

    /*
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

    public Triple<Integer, Integer, Integer> spawnThread = null;

     */

    private int num;
    private Map<String, String> threads;

    @Override
    public int hashCode() {
        return threads.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
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
        return num == other.num && threads.equals(other.threads);
    }

    @Override
    public String toString() {
        return threads.toString();
    }

    @Override
    public String toDOTLabel() {
        StringBuilder str = new StringBuilder();
        str.append(threads.toString());
        str.append("\n");
        return str.toString();
    }

    @Override
    public boolean shouldBeHighlighted() {
        return true;
    }

    public OGPORState(int pNum) {
        num = pNum;
        threads = new HashMap<String, String>();
    }

    public Map<String, String> getThreads() {
        return threads;
    }

    public int getNum() {
        return num;
    }
}
