package org.sosy_lab.cpachecker.cpa.por.ogpor;


import com.google.common.base.Preconditions;
import org.sosy_lab.common.annotations.ReturnValuesAreNonnullByDefault;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;

import java.util.*;

public class OGPORState implements AbstractState, Graphable {

    private int num;
    // Assume there is an edge: sn -- Ei --> sm, then the value of 'inThread' will be
    // the activeThread of 'Ei', which comes from the threadingState in sn. We set
    // its value in strengthen process.
    private String inThread;
    private final Map<String, String> threads;
    // For a block, we record its preState when we meet its start edge. By doing so, we
    // could avoid to backtrack along the path.
    private AbstractState preservedState;

    // The variable is used to record all loops that all alive threads in.
    // Structure: thread -> Stack<CFANode>
    // Use stack to record all loop starts we have met because the innter loops should
    // always terminate before the outer ones.
    private final Map<String, Stack<CFANode>> loops = new HashMap<>();
    // The variable is used to record the depth of each loop we have met.
    private final Map<CFANode, Integer> loopDepthTable = new HashMap<>();

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
        threads = new HashMap<>();
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

    public Map<String, Stack<CFANode>> getLoops() {
        return loops;
    }

    public void setLoops(final Map<String, Stack<CFANode>> pLoops) {
        pLoops.forEach((k, v) -> {
            Stack<CFANode> stack = new Stack<>();
            stack.addAll(pLoops.get(k));
            loops.put(k, stack);
        });
    }

    public Map<CFANode, Integer> getLoopDepthTable() {
        return loopDepthTable;
    }

    public void setLoopDepthTable(final Map<CFANode, Integer> pLoopDepthTable) {
        loopDepthTable.putAll(pLoopDepthTable);
    }

    public void setNum(int pNum) {
        this.num = pNum;
    }

    public void updateLoopDepth(CFAEdge cfaEdge) {
        Preconditions.checkArgument(cfaEdge != null,
                "A CFA edge is required.");
        CFANode pre = cfaEdge.getPredecessor(), curLoop = null;
        if (!loops.containsKey(inThread)) {
            loops.put(inThread, new Stack<>());
        }
        Stack<CFANode> curLoops = loops.get(inThread);
        if (!curLoops.isEmpty()) {
            curLoop = loops.get(inThread).peek();
        }

        if (pre.isLoopStart()) {
            if (!pre.equals(curLoop)) {
                // curLoop is null, i.e., we are not in any loop.
                // Or pre != curLoop, i.e., we reach a new loop start.
                // In both cases, we should add new loop item with initial depth = 1.
                Preconditions.checkState(!loops.get(inThread).contains(pre),
                        "Try to start a new loop with the current loop not exited.");
                loops.get(inThread).push(pre);
                Preconditions.checkState(!loopDepthTable.containsKey(pre));
                loopDepthTable.put(pre, 1);
            } else {
                // pre == curLoop, which means we are in a loop and reach its loop start
                // again. In this case, we should check whether we will exit the loop.
                AssumeEdge assumeEdge = (AssumeEdge) cfaEdge;
                if (!assumeEdge.getTruthAssumption()) {
                    // If-false branch. We exit the loop.
                    loopDepthTable.remove(curLoop);
                    loops.get(inThread).pop();
                } else {
                    // If-true branch. We increase the loop depth.
                    Preconditions.checkState(loopDepthTable.containsKey(curLoop));
                    loopDepthTable.compute(curLoop, (k, v) -> v + 1);
                }
            }
        }
    }

    /**
     * @return 0 if this state is not in any loop (just thinking of the inThread), the
     * depth of the current loop else.
     */
    public int getLoopDepth() {
        if (loops.get(inThread).isEmpty()) {
            return 0;
        }
        CFANode curLoop = loops.get(inThread).peek();

        return loopDepthTable.get(curLoop);
    }
}
