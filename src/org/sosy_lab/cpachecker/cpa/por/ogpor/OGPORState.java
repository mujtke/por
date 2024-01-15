package org.sosy_lab.cpachecker.cpa.por.ogpor;


import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.CFATerminationNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.CriticalAreaAction.*;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.LockStatus.*;

public class OGPORState implements AbstractState, Graphable {

    private static CFA cfa;
    private int num;
    // Assume there is an edge: sn -- Ei --> sm, then the value of 'inThread' will be
    // the activeThread of 'Ei', which comes from the threadingState in sn. We set
    // its value in a 'strengthen' process.
    private String inThread;
    private final Map<String, String> threads;

    // The variable is used to record all loops that all alive threads in.
    // Structure: thread -> Stack<CFANode>
    // Use stack to record all loop starts we have met because the inner loops should
    // always terminate before the outer ones.
    private final Map<String, Stack<CFANode>> loops = new HashMap<>();
    // The variable is used to record the depth of each loop we have met.
    private final Map<CFANode, Integer> loopDepthTable = new HashMap<>();
    private static final Map<CFANode, Set<CFANode>> loopExitNodes = new HashMap<>();

    private final Map<String, Stack<String>> locks = new HashMap<>();

    public CriticalAreaAction getInCaa() {
        assert inThread != null;
        return caas.get(inThread);
    }

    public enum LockStatus {
        LOCK,
        UNLOCK,
        LOCK_FREE,
    }

    public enum CriticalAreaAction {
        START, /* start a critical area */
        CONTINUE, /* be inside a critical area */
        END, /* end a critical area */
        NOT_IN, /* not any one above, lock-free */
    }

    // private CriticalAreaAction caa = NOT_IN;
    private final Map<String, CriticalAreaAction> caas = new HashMap<>();

    // Record the entering edge.
    CFAEdge enteringEdge;
    // Record whether the enteringEdge is the normal edge.
    boolean isNormalEnteringEdge = true;
    private static HashMap<Integer, List<SharedEvent>> edgeVarMap;
    private static Set<String> atomicBegins = ImmutableSet.of("__VERIFIER_atomic_begin");
    private static Set<String> atomicEnds = ImmutableSet.of("__VERIFIER_atomic_end");
    private static Set<String> lockBegins = ImmutableSet.of("pthread_mutex_lock",
            "pthread_lock", "lock");
    private static Set<String> lockEnds = ImmutableSet.of("pthread_mutex_unlock",
            "pthread_unlock", "unlock");

    public void setLocks(Map<String, Stack<String>> pLocks) {
        // Deep copy needed for 'Stack<Sting>'
        pLocks.forEach((k, v) -> {
            Stack<String> newLocks = new Stack<>();
            newLocks.addAll(v);
            locks.put(k, newLocks);
        });
    }

    public Map<String, Stack<String>> getLocks() {
        return locks;
    }

    public boolean enteringEdgeIsNormal() {
        return isNormalEnteringEdge;
    }

    public CFAEdge getEnteringEdge() {
        return enteringEdge;
    }

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

    public OGPORState(int pNum, CFAEdge pEdge) {
        num = pNum;
        threads = new HashMap<>();
        enteringEdge = pEdge;
        if (edgeVarMap == null) {
            try {
                GlobalInfo globalInfo = GlobalInfo.getInstance();
                assert globalInfo != null : "Initialize edgeVarMap for OGPORState " +
                        "failed since the absence of the GlobalInfo";
                OGInfo ogInfo = globalInfo.getOgInfo();
                assert ogInfo != null : "Initialize edgeVarMap for OGPORState failed " +
                        "since the absence of the ogInfo.";
                edgeVarMap = ogInfo.getEdgeVarMap();
            } catch (Exception e) {
                throw e;
            }
        }
        isNormalEnteringEdge = isNormalEdge(pEdge);
    }

    public Map<String, CriticalAreaAction> getCaas() {
        return caas;
    }

    public void setCaas(Map<String, CriticalAreaAction> pCaas) {
        caas.putAll(pCaas);
    }

    private boolean isNormalEdge(CFAEdge edge) {
        // FIXME: should we consider the block ends?
        // Check whether the given edge is a normal edge, i.e., the edge neither accesses
        // any shared vars nor begin any atomic block.

        if (edge instanceof CStatementEdge) {
            CStatement cStatement = ((CStatementEdge) edge).getStatement();
            if (cStatement instanceof CFunctionCallStatement)
                if (hasAtomicBegin(edge) || hasLockBegin(edge))
                    return false;
        } else if (edge instanceof CFunctionCallEdge) {
            if (hasAtomicBegin(edge) || hasLockBegin(edge))
                return false;
        }

        // Not a fun call.
        if (edgeVarMap.get(edge.hashCode()) != null &&
                !edgeVarMap.get(edge.hashCode()).isEmpty())
            return false;

        return true;
    }

    public void setLoopInfo() {
        Preconditions.checkArgument(cfa.getLoopStructure().isPresent(),
                "Missing loop structure.");
        LoopStructure loopStructure = cfa.getLoopStructure().get();
        for (Loop loop : loopStructure.getAllLoops()) {
            // one loop just have one loop head?
            Set<CFANode> loopStarts = loop.getIncomingEdges()
                    .stream().map(CFAEdge::getSuccessor).collect(Collectors.toSet());
            Preconditions.checkState(loopStarts.size() == 1,
                    "Just one loop start node is expected.");
            CFANode loopStart = loopStarts.iterator().next();
            if (!loopStart.isLoopStart()) {
                // In some special cases, loopStart is not the real loop start node. In
                // this case, we enumerate the nodes in loop to get the loop start node.
                loopStart = null;
                for (CFANode node : loop.getLoopNodes()) {
                    if (node.isLoopStart()) {
                        loopStart = node;
                        break;
                    }
                }
            }
            Preconditions.checkState(loopStart != null,
                    "Finding loop start node failed.");
            // Corresponding loopStart to its loop exit nodes.
            Set<CFANode> loopExits = loop.getOutgoingEdges()
                    .stream().map(CFAEdge::getSuccessor).collect(Collectors.toSet());
            loopExitNodes.put(loopStart, loopExits);
        }
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

    public void setCfa(CFA pCfa) {
        cfa = pCfa;
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
            if (curLoop != null) {
                Preconditions.checkArgument(!loopExitNodes.isEmpty()
                                && loopExitNodes.containsKey(curLoop),
                        "Obtain loop structure failed.");
                if (loopExitNodes.get(curLoop).contains(pre)) {
                    // If pre is a loop exit node, then we exit the curLoop.
                    loopDepthTable.remove(curLoop);
                    loops.get(inThread).pop();
                }
            }
        }
        // Debug.
//        System.out.println(cfaEdge + "@" + (!loops.get(inThread).isEmpty() ?
//                loopDepthTable.get(loops.get(inThread).peek()) : 0));
    }

    /**
     * @return 0 if this state is not in any loop (just thinking of the inThread), the
     * depth of the current loop else.
     */
    public int getLoopDepth() {
        if (loops.get(inThread).isEmpty()) {
            return 0;
        }
        int res = 0;
        for (CFANode loop : loops.get(inThread)) {
            int depth = loopDepthTable.get(loop);
            res = hash(res, loop, depth);
        }

        return res;
    }

    // Update the lock status.
    public void updateLockStatus(CFAEdge edge) {
        if (!locks.containsKey(inThread)) {
            locks.put(inThread, new Stack<>());
        }

        if (!caas.containsKey(inThread)) {
            caas.put(inThread, NOT_IN);
        }

        // Possible lock variable in edge.
        Pair<LockStatus, String> l = getLock(edge);
        Stack<String> curLocks = locks.get(inThread);

        // FIXME: critical area end caused by exit and termination edge.
        if (willTerminate(edge)
                && (caas.get(inThread) == START || caas.get(inThread) == CONTINUE)) {
            caas.put(inThread, END);
            return;
        }

        caas.put(inThread, handleLock(curLocks, l));
    }

    private boolean willTerminate(CFAEdge edge) {
        // If the thread/program terminates when the next edge comes.
        CFANode successor = edge.getSuccessor();
        // FIXME: only one exit edge?
        if (successor.getNumLeavingEdges() != 1)
            return false;

        CFAEdge nextEdge = successor.getLeavingEdge(0);

        return isTerminatingEdge(nextEdge) || isEndOfMainFunction(nextEdge);
    }

    /** the whole program will terminate after this edge */
    private static boolean isTerminatingEdge(CFAEdge edge) {
        return edge.getSuccessor() instanceof CFATerminationNode;
    }

    /** the whole program will terminate after this edge */
    private boolean isEndOfMainFunction(CFAEdge edge) {
        return Objects.equals(cfa.getMainFunction().getExitNode(), edge.getSuccessor());
    }

    // Get the name of the lock var form the given edge.
    private Pair<LockStatus, String> getLock(CFAEdge edge) {

        if (!(edge instanceof CStatementEdge) && !(edge instanceof CFunctionCallEdge)) {
            return Pair.of(LOCK_FREE, null);
        }

        if (edge instanceof CStatementEdge
                && !(((CStatementEdge) edge).getStatement() instanceof CFunctionCallStatement)) {
            return Pair.of(LOCK_FREE, null);
        }

        if (hasAtomicBegin(edge)) {
            return Pair.of(LOCK, "__VERIFIER_atomic_begin");
        }
        if (hasAtomicEnd(edge)) {
            return Pair.of(UNLOCK, "__VERIFIER_atomic_end");
        }

        if (hasLockBegin(edge)) {
            return Pair.of(LOCK, getLockVarName(edge));
        }
        if (hasLockEnd(edge)) {
            return Pair.of(UNLOCK, getLockVarName(edge));
        }

        return Pair.of(LOCK_FREE, null);
    }

    private boolean hasLockEnd(CFAEdge cfaEdge) {
        for (String le : lockEnds)
            if (cfaEdge.getRawStatement().contains(le)) return true;
        return false;
    }

    private boolean hasAtomicEnd(CFAEdge cfaEdge) {
        for (String ae : atomicEnds)
            if (cfaEdge.getRawStatement().contains(ae)) return true;
        return false;
    }

    private boolean hasLockBegin(CFAEdge cfaEdge) {
        for (String lb : lockBegins)
            if (cfaEdge.getRawStatement().contains(lb)) {
                return !Objects.equals(lb, "lock") ||
                        !cfaEdge.getRawStatement().contains("unlock");
            }
        return false;
    }

    private boolean hasAtomicBegin(CFAEdge cfaEdge) {
        for (String ab : atomicBegins)
            if (cfaEdge.getRawStatement().contains(ab)) return true;
        return false;
    }

    private String getLockVarName(CFAEdge edge) {
        List<? extends AExpression> arguments;
        if (edge instanceof CFunctionCallEdge) {
            arguments = ((CFunctionCallEdge) edge).getArguments();
        } else {
            assert edge instanceof CStatementEdge;
            CStatement cStatement = ((CStatementEdge) edge).getStatement();
            assert cStatement instanceof CFunctionCallStatement;
            CFunctionCallStatement cFunctionCallStatement =
                    (CFunctionCallStatement) cStatement;
            arguments = cFunctionCallStatement.getFunctionCallExpression().getParameterExpressions();
        }

        Preconditions.checkArgument(arguments.size() == 1,
                "More than one vars in lock function is not supported." + edge);
        AExpression arg = arguments.iterator().next();
        Preconditions.checkArgument(arg instanceof CUnaryExpression);
        CUnaryExpression unaryExpression = (CUnaryExpression) arg;
        CIdExpression lockIdExpression = (CIdExpression) unaryExpression.getOperand();
        return lockIdExpression.getName();
    }

    private CriticalAreaAction handleLock(Stack<String> curLocks,
            Pair<LockStatus, String> pL) {
        String l = pL.getSecond(), l0;
        LockStatus lockStatus = pL.getFirstNotNull();
        CriticalAreaAction caa = caas.get(inThread);
        assert caa != null;

        if (lockStatus == LOCK_FREE) {
            // Current egde doesn't have a lock.
            return (caa == START || caa == CONTINUE) ? CONTINUE : NOT_IN;
        }

        // l != null, which means lockStatus == LOCK or lockStatus == UNLOCK
        switch (caa) {
            case START:
                assert !curLocks.isEmpty();
                l0 = curLocks.peek();
                if (lockStatus == LOCK) {
                    // FIXME
                    // Push a new lock after having pushed one before.
                    curLocks.push(l);
                    return CONTINUE;
                } else {
                    if (lockMatch(l, l0)) {
                        // Unlock.
                        curLocks.pop();
                        // FIXME: empty-content block?
                        return curLocks.isEmpty() ? END : CONTINUE;
                    } else if (curLocks.contains(l)) {
                        // Current critical area preserved.
                        curLocks.remove(l);
                        Preconditions.checkArgument(!curLocks.isEmpty(),
                                "Unlocking a lock that doesn't at the top of " +
                                        "stack should leave locks not empty");
                        return CONTINUE;
                    }
                    throw new UnsupportedOperationException("Cannot unlock a lock not held");
                }

            case CONTINUE:
                if (lockStatus == LOCK) {
                    // FIXME: nested locks.
                    curLocks.push(l);
                    // Add a new lock couldn't terminate the current critical area.
                    return CONTINUE;
                } else {
                    l0 = curLocks.peek();
                    if (lockMatch(l, l0)) {
                        // Unlock.
                        curLocks.pop();
                        return curLocks.isEmpty() ? END : CONTINUE;
                    } else if (curLocks.contains(l)) {
                        curLocks.remove(l);
                        Preconditions.checkArgument(!curLocks.isEmpty(),
                                "Unlocking a lock that doesn't at the top of " +
                                        "stack should make locks not empty");
                        return CONTINUE;
                    }
                    throw new UnsupportedOperationException("Cannot unlock a lock not held");
                }

            case END:
                assert curLocks.isEmpty();
            case NOT_IN:
                if (lockStatus == LOCK) {
                    curLocks.push(l);
                    return START;
                } else {
                    throw new UnsupportedOperationException("Unlocking " +
                            "is not allowed when no lock held.");
                }

            default:
        }

        return NOT_IN;
    }


    private boolean lockMatch(String l, String l0) {
        if (Objects.equals(l, l0)) {
            return true;
        }
        // else, l0 and l must be like '__VERIFIER_atomic_begin' and
        // '__VERIFIER_atomic_end'.
        if (!atomicBegins.contains(l0) || !atomicEnds.contains(l)) {
            return false;
        }

        String lPrefix, l0Prefix;
        if (l0.matches(".*(begin|Begin|BEGIN)$")
                || l0.matches(".*(start|Start|START)$")) {
            l0Prefix = l0.substring(0, l0.length() - 5); /* e.g., '__VERIFIER_atomic_' */
            if (l.matches(".*(end|End|END)$")) {
                lPrefix = l.substring(0, l.length() - 3); /* '__VERIFER_atomic_' */
                return Objects.equals(l0Prefix, lPrefix);
            }
        }
        return false;
    }
}