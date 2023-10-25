package org.sosy_lab.cpachecker.cpa.por.ogpor;


import com.google.common.base.Preconditions;
import org.junit.Assume;
import org.sosy_lab.common.annotations.ReturnValuesAreNonnullByDefault;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.util.Pair;
import scala.Int;

import javax.annotation.Nonnull;
import java.util.*;

import static java.util.Objects.hash;

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
    private final Map<CFANode, CFANode> loopExitNodes = new HashMap<>();
    private final static Map<CFANode, Pair<Integer, Integer>> loopRange = new HashMap<>();

    @Override
    public int hashCode() {
        return hash(num, inThread, threads);
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
            // If we haven't computed the range for the pre.
            if (!loopRange.containsKey(pre)) {
                Preconditions.checkState(pre.getNumEnteringEdges() == 2, "Just two " +
                        "edges entering to a loop start node is expected.");
                CFAEdge loopStartEdge = pre.getEnteringEdge(0),
                        loopBackEdge = pre.getEnteringEdge(1);
                // Get the start line of the loop.
                int startLine = loopStartEdge.getFileLocation().getStartingLineNumber(),
                        endLine = -1;
                // Get the end line of the loop.
                CFANode lbePre = loopBackEdge.getPredecessor();
                if (!(loopBackEdge instanceof BlankEdge)) {
                    Preconditions.checkArgument(lbePre.getNumEnteringEdges() == 1);
                    loopBackEdge = lbePre.getEnteringEdge(0);
                    lbePre = loopBackEdge.getPredecessor();
                }
                for (int i = 0; i < lbePre.getNumEnteringEdges(); i++) {
                    CFAEdge ei = lbePre.getEnteringEdge(i);
                    if (ei instanceof BlankEdge) continue;
                    int tempLine = ei.getFileLocation().getEndingLineNumber();
                    if (tempLine > endLine) endLine = tempLine;
                }
                Preconditions.checkState(endLine > 0,
                        "Finding loop end line failed.");
                loopRange.put(pre, Pair.of(startLine, endLine));
            }
            if (!pre.equals(curLoop)) {
                // curLoop is null or pre != curLoop, which means we reach a new loop
                // start. We add a new loop item with initial depth = 1.
                loops.get(inThread).push(pre);
                loopDepthTable.put(pre, 1);
            } else {
                // pre == curLoop, which means we are in a loop and reach its loop start
                // again. In this case, we increase the loop depth.
                Preconditions.checkState(loopDepthTable.containsKey(curLoop));
                loopDepthTable.compute(curLoop, (k, v) -> v + 1);
            }
        } else {
            // Pre may be a loop exit node.
            if (curLoop != null && pre.equals(loopExitNodes.get(curLoop))) {
                // If pre is a loop exit node, then we exit the curLoop.
                loopDepthTable.remove(curLoop);
                loops.get(inThread).pop();
            } else if (curLoop != null && !loopExitNodes.containsKey(curLoop)) {
                // We haven't computed the exited node for the curLoop.
                if (!(cfaEdge instanceof BlankEdge)) {
                    // BlankEdge may have no file location.
                    Preconditions.checkArgument(loopRange.containsKey(curLoop));
                    int curLine = cfaEdge.getFileLocation().getStartingLineNumber(),
                            curLoopEndLine = loopRange.get(curLoop).getSecondNotNull();
                    if (curLine > curLoopEndLine) {
                        // curLine has exit the curLoop, and we add the exited node for
                        // curLoop in loopExitNodes.
                        loopDepthTable.remove(curLoop);
                        loops.get(inThread).pop();
                        loopExitNodes.put(curLoop, pre);
                    }
                }
            }
        }
        // Debug.
        System.out.println(cfaEdge + "@" + (!loops.get(inThread).isEmpty() ?
                loopDepthTable.get(loops.get(inThread).peek()) : 0));
    }

    /**
     * @return 0 if this state is not in any loop (just thinking of the inThread), the
     * depth of the current loop else.
     */
    public int getLoopDepth() {
        if (loops.get(inThread).isEmpty()) {
            return 0;
        }
//        CFANode curLoop = loops.get(inThread).peek();
//
//        return loopDepthTable.get(curLoop);
        int res = 0;
        for (CFANode loop : loops.get(inThread)) {
            int depth = loopDepthTable.get(loop);
            res = hash(res, loop, depth);
        }

        return res;
    }
}
