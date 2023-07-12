package org.sosy_lab.cpachecker.cpa.por.ogpor;


import org.sosy_lab.common.annotations.ReturnValuesAreNonnullByDefault;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;

import java.util.*;

public class OGPORState implements AbstractState, Graphable {

    private final int num;
    // Assume there is an edge: sn -- Ei --> sm, then the value of 'inThread' will be
    // the activeThread of 'Ei', which comes from the threadingState in sn. We set
    // its value in strengthen process.
    private String inThread;
    private final Map<String, String> threads;
    // For a block, we record its preState when we meet its start edge. By doing so, we
    // could avoid to backtrack along the path.
    private AbstractState preservedState;

    @Override
    public int hashCode() {
        return Objects.hash(num, inThread, threads);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof OGPORState) {
            OGPORState other = (OGPORState) obj;
            return num == other.num
                    && inThread.equals(other.inThread)
                    && threads.equals(other.threads);
        }
        return false;
    }

    @Override
    public String toString() {
        return "[" + num + "] " + inThread + "@" + threads.get(inThread);
    }

    @Override
    public String toDOTLabel() {
        StringBuilder str = new StringBuilder();
        str.append(threads);
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

    public String getInThread() {
        return this.inThread;
    }

    public void setInThread(String thread) {
        this.inThread = thread;
    }

    public AbstractState getPreservedState() {
        return preservedState;
    }

    public void setPreservedState(AbstractState preservedState) {
        this.preservedState = preservedState;
    }
}
