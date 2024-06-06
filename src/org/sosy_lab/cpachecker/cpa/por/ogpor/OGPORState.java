package org.sosy_lab.cpachecker.cpa.por.ogpor;


import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.DummyCFAEdge;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.CFATerminationNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.identifiers.GeneralLocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.CriticalAreaAction.*;
import static org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState.LockStatus.*;

public class OGPORState implements AbstractState, Graphable {
    private static LogManager logger;
    private static CFA cfa;
    // Use this var to record the length of the path till the current state.
    private int num;
    /**
     * Assume there is an edge: sn -- Ei --> sm, then the value of 'inThread' will be
     * the activeThread of 'Ei', which comes from the threadingState in sn. We set
     * its value in {@link OGPORTransferRelation#strengthen(AbstractState, Iterable,
     * CFAEdge, Precision)}.
     */
    private String inThread;
    private final Map<String, String> threads;

    // This variable is used to record loops that all alive threads in.
    // Structure: thread -> Stack<CFANode>
    // Use stack to record all loop starts we have met because the inner loops should
    // always terminate before the outer ones.
    private final Map<String, Stack<CFANode>> loops = new HashMap<>();
    // The variable is used to record the depth of each loop we have met.
    private final Map<CFANode, Integer> loopDepthTable = new HashMap<>();
    private static final Map<CFANode, Set<CFANode>> loopExitNodes = new HashMap<>();

    private final Map<String, Stack<String>> locks = new HashMap<>();

    // tid -> parent's tid
    private final Map<String, String> parentThread = new HashMap<>();

    public CriticalAreaAction getInCaa() {
        assert inThread != null;
        return caas.get(inThread);
    }

    public String getParentThread(String inThread) {
        assert inThread != null;
        return parentThread.get(inThread);
    }

    public Map<String, String> getParentThread() { return parentThread; }

    public void setParentThread(Map<String, String> pParentThread) {
        parentThread.putAll(pParentThread);
    }

    public enum LockStatus {
        LOCK,
        UNLOCK,
        LOCK_FREE,
    }

    public enum CriticalAreaAction {
        START,     /* start a critical area */
        CONTINUE,  /* be inside a critical area */
        END,       /* end a critical area */
        NOT_IN,    /* not any one above, lock-free */
    }

    // tid -> caa, recording each thread's caa.
    private final Map<String, CriticalAreaAction> caas = new HashMap<>();

    // Record the entering edge.
    CFAEdge enteringEdge;
    // Record whether the enteringEdge is normal, i.e., it doesn't access to any shared
    // vars or start an atomic area.
    boolean isNormalEnteringEdge;
    private static HashMap<Integer, List<SharedEvent>> edgeVarMap;
    private static Set<String> atomicBegins;
    private static Set<String> atomicEnds;
    private static Set<String> lockBegins;
    private static Set<String> lockEnds;
    private final static Set<Pattern> atomicBeginPatterns = new HashSet<>();
    private final static Set<Pattern> atomicEndPatterns = new HashSet<>();
    private final static Set<Pattern> lockBeginPatterns = new HashSet<>();
    private final static Set<Pattern> lockEndPatterns = new HashSet<>();

    public void setLocks(Map<String, Stack<String>> pLocks) {
        // NOTE: handle this carefully.
        pLocks.forEach((k, v) -> {
            Stack<String> newLocks = new Stack<>();
            newLocks.addAll(v);
            locks.put(k, newLocks);
        });
    }

    // Remove the parent thread for thread t.
    public void removeParentThread(String t) { parentThread.remove(t); }

    // Set the parent thread for thread t.
    public void setParentThread(String t, String parent) {
        parentThread.putIfAbsent(t, parent);
        assert Objects.equals(parent, parentThread.get(t));
    }

    public Map<String, Stack<String>> getLocks() { return locks; }

    public CFAEdge getEnteringEdge() { return enteringEdge; }

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
    public boolean shouldBeHighlighted() { return true; }

    public OGPORState(int pNum, CFAEdge pEdge) {
        num = pNum;
        threads = new HashMap<>();
        enteringEdge = pEdge;
        if (!(pEdge instanceof DummyCFAEdge))
            isNormalEnteringEdge = isNormalEdge(pEdge);
    }

    /**
     * Extract patterns from the {@link #atomicBegins}, {@link #atomicEnds},
     * {@link #lockBegins}, {@link #lockEnds}.
     */
    public void extractPatterns() {
        extractPatterns(atomicBegins, atomicBeginPatterns);
        extractPatterns(atomicEnds, atomicEndPatterns);
        extractPatterns(lockBegins, lockBeginPatterns);
        extractPatterns(lockEnds, lockEndPatterns);
    }

    public void setAtomicBegins(Set<String> pAtomicBegins) { atomicBegins = pAtomicBegins; }
    public void setAtomicEnds(Set<String> pAtomicEnds) { atomicEnds = pAtomicEnds; }
    public void setLockBegins(Set<String> pLockBegins) { lockBegins = pLockBegins; }
    public void setLockEnds(Set<String> pLockEnds) { lockEnds = pLockEnds; }
    public void setLogger(LogManager pLogger) { logger = pLogger; }
    public void setEdgeVarMap() {
        edgeVarMap = GlobalInfo.getInstance().getOgInfo().getEdgeVarMap();
        assert edgeVarMap != null : "Initializing edgeVarMap for OGPORState failed!";
    }

    private void extractPatterns(Set<String> atomicStmts, Set<Pattern> atomicPatterns) {
        atomicStmts.forEach(stmt -> {
            // FIXME: current implementation need jdk11.
            String patternStr = "^" + stmt + " *(.*)";
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            atomicPatterns.add(pattern);
        });
    }

    public Map<String, CriticalAreaAction> getCaas() { return caas; }

    public void setCaas(Map<String, CriticalAreaAction> pCaas) {
        caas.putAll(pCaas);
    }

    private boolean isNormalEdge(CFAEdge edge) {
        // Check whether the given edge is normal, i.e., the edge neither accesses
        // to any shared vars nor start any atomic block.
        if (edge instanceof CStatementEdge) {
            CStatement cStatement = ((CStatementEdge) edge).getStatement();
            if (cStatement instanceof CFunctionCallStatement) {
                String funcName = ((CFunctionCallStatement) cStatement).getFunctionCallExpression()
                        .getFunctionNameExpression().toString();
                if (hasAtomicBegin(funcName) || hasLockBegin(funcName))
                    return false;
            }
        } else if (edge instanceof CFunctionCallEdge) {
            CFunctionCallEdge functionCallEdge = (CFunctionCallEdge) edge;
            String funcName = functionCallEdge.getSummaryEdge().getExpression()
                    .getFunctionCallExpression().getFunctionNameExpression().toString();
            if (hasAtomicBegin(funcName) || hasLockBegin(funcName))
                return false;
        }

        // Not a fun call.
        if (edgeVarMap.get(edge.hashCode()) != null &&
                !edgeVarMap.get(edge.hashCode()).isEmpty())
            return false;

        return true;
    }

    public void setLoopInfo() {
        assert cfa.getLoopStructure().isPresent() :
                "Missing loop structure when trying to set the loop info!";
        LoopStructure loopStructure = cfa.getLoopStructure().get();
        for (Loop loop : loopStructure.getAllLoops()) {
            // A loop just have one loop head?
            Set<CFANode> loopStarts = loop.getIncomingEdges()
                    .stream().map(CFAEdge::getSuccessor).collect(Collectors.toSet());
            assert loopStarts.size() == 1 : "Just one loop start node is expected!";
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
            assert loopStart != null : "Finding loop start node failed!";
            // Corresponding loopStart to its loop exit nodes.
            // NOTE: one loop start node may has more than one exit node.
            Set<CFANode> loopExits = loop.getOutgoingEdges()
                    .stream().map(CFAEdge::getSuccessor).collect(Collectors.toSet());
            loopExitNodes.put(loopStart, loopExits);
        }
    }

    public Map<String, String> getThreads() { return threads; }

    public void setThreads(Map<String, String> pThreads) {
        threads.putAll(pThreads);
    }
    public int getNum() { return num; }

    public String getInThread() { return this.inThread; }

    public void setInThread(String thread) { this.inThread = thread; }

    public Map<String, Stack<CFANode>> getLoops() { return loops; }

    public void setLoops(final Map<String, Stack<CFANode>> pLoops) {
        pLoops.forEach((k, v) -> {
            Stack<CFANode> stack = new Stack<>();
            stack.addAll(pLoops.get(k));
            loops.put(k, stack);
        });
    }

    public Map<CFANode, Integer> getLoopDepthTable() { return loopDepthTable; }

    public void setLoopDepthTable(final Map<CFANode, Integer> pLoopDepthTable) {
        loopDepthTable.putAll(pLoopDepthTable);
    }

    public void setNum(int pNum) { this.num = pNum; }

    public void setCfa(CFA pCfa) { cfa = pCfa; }

    public void updateLoopDepth(CFAEdge cfaEdge) {
        assert cfaEdge != null :
                "Missing CFA edge when trying to update the loop depth.";
        CFANode pre = cfaEdge.getPredecessor(), curLoop = null;
        Stack<CFANode> curLoops = loops.computeIfAbsent(inThread, k -> new Stack<>());
        if (!curLoops.isEmpty()) {
            curLoop = loops.get(inThread).peek();
        }

        if (pre.isLoopStart()) {
            if (!pre.equals(curLoop)) {
                // curLoop is null or pre != curLoop, which means we reach a new loop
                // start. We add a new loop item with initial depth = 1.
                // loops.get(inThread).push(pre);
                curLoops.push(pre);
                loopDepthTable.put(pre, 1);
            } else {
                // pre == curLoop, which means we are in a loop and reach its loop start
                // again. In this case, we increase the loop depth.
                assert loopDepthTable.containsKey(curLoop);
                loopDepthTable.compute(curLoop, (k, v) -> v + 1);
            }
        } else {
            // Pre may be a loop exit node.
            if (curLoop != null) {
                assert loopExitNodes.containsKey(curLoop) :
                        "Cannot get loop exit nodes for loop start " + curLoop + ".";
                if (loopExitNodes.get(curLoop).contains(pre)) {
                    // If pre is a loop exit node, then we exit the curLoop.
                    loopDepthTable.remove(curLoop);
                    CFANode removedLoop = loops.get(inThread).pop();
                    assert Objects.equals(removedLoop, curLoop) :
                            "Try to remove a loop " + removedLoop + ", which mismatches" +
                                    " the current loop " + curLoop + "!.";
                }
            }
        }
    }

    /**
     * @return 0 if this state is not inside any loop (just considering the inThread), the
     * depth of the current loop else.
     */
    public int getLoopDepth() {
        assert loops.containsKey(inThread);
        if (loops.get(inThread).isEmpty()) {
            return 0;
        }
        // At current state, we may locate in nested loops. For this case, we compute
        // loop depth by hashing, until we get a non-zero hash value.
        int res = 0;
        do {
            for (CFANode loop : loops.get(inThread)) {
                int depth = loopDepthTable.get(loop);
                res = hash(res, loop, depth);
            }
        } while (res != 0);

        return res;
    }

    // Update the lock status.
    public void updateLockStatus(CFAEdge edge) {
        locks.putIfAbsent(inThread, new Stack<>());
        caas.putIfAbsent(inThread, NOT_IN);

        // Get lock variable in the edge if exists.
        Pair<LockStatus, String> l = getLock(edge);
        Stack<String> curLocks = locks.computeIfAbsent(inThread, k -> new Stack<>());

        // NOTE: end of the critical area that is caused by exit and termination edge.
        if (willTerminate(edge)
                && (caas.get(inThread) == START || caas.get(inThread) == CONTINUE)) {
            caas.put(inThread, END);
            return;
        }

        caas.put(inThread, handleLock(curLocks, l));
    }

    public boolean terminateBlock(String thd) {
        if (caas.get(thd) == START || caas.get(thd) == CONTINUE){
            caas.put(thd, END);
            return true;
        }
        return false;
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
        if (edge.getSuccessor() instanceof CFATerminationNode) {
            return true;
        } else if (edge instanceof CStatementEdge) { // Call of 'abort()'.
            CStatement cStatement = ((CStatementEdge) edge).getStatement();
            if (!(cStatement instanceof CFunctionCallStatement)) {
                return false;
            }
            CFunctionCallExpression funcCallExpr =
                    ((CFunctionCallStatement) cStatement).getFunctionCallExpression();
            // FIXME: do a better match.
            if (Objects.equals(getFunctionName(funcCallExpr), "abort")) {
                return true;
            }
        } else if (edge instanceof CFunctionCallEdge) {
            CFunctionCallEdge cFunctionCallEdge = (CFunctionCallEdge) edge;
            CFunctionCallExpression funcCallExpr = cFunctionCallEdge.getSummaryEdge()
                    .getExpression().getFunctionCallExpression();
            // FIXME: do a better match.
            if (Objects.equals(getFunctionName(funcCallExpr), "abort")) {
                return true;
            }
        }

        return false;
    }

    private static String getFunctionName(CFunctionCallExpression cFuncCallExpr) {
        assert cFuncCallExpr.getFunctionNameExpression() instanceof CIdExpression;
        String funcName = ((CIdExpression) cFuncCallExpr.getFunctionNameExpression()).getName();
        assert funcName != null;
        return funcName;
    }

    /** the whole program will terminate after this edge */
    private static boolean isEndOfMainFunction(CFAEdge edge) {
        return Objects.equals(cfa.getMainFunction().getExitNode(), edge.getSuccessor());
    }

    // Get the name of the lock var form the given edge.
    private Pair<LockStatus, String> getLock(CFAEdge edge) {

        if (!(edge instanceof CStatementEdge) && !(edge instanceof CFunctionCallEdge)) {
            // FIXME: handle the function return edge, which may terminate a atomic block.
            if (edge instanceof CFunctionReturnEdge) {
                return getLockFromFunctionReturnEdge((CFunctionReturnEdge) edge);
            }
            return Pair.of(LOCK_FREE, null);
        }

        if (edge instanceof CStatementEdge
                && !(((CStatementEdge) edge).getStatement() instanceof CFunctionCallStatement)) {
            return Pair.of(LOCK_FREE, null);
        }

        // Else: 1) edge instanceof CStatementEdge.
        // or 2) edge instanceof CStatementEdge && edge.getStatement() instanceof CFunctionCallStatement.
        String funcName;
        if (edge instanceof CStatementEdge) {
            CFunctionCallStatement cFunctionCallStatement =
                    (CFunctionCallStatement) ((CStatementEdge) edge).getStatement();
            funcName = cFunctionCallStatement.getFunctionCallExpression().getFunctionNameExpression().toString();
        } else {
            // Edge must be CFunctionCallEdge.
            CFunctionCallEdge cFunctionCallEdge = (CFunctionCallEdge) edge;
            funcName = cFunctionCallEdge.getSummaryEdge().getExpression()
                    .getFunctionCallExpression()
                    .getFunctionNameExpression().toString();
        }

        if (hasAtomicBegin(funcName)) {
            return Pair.of(LOCK, "__VERIFIER_atomic_begin");
//            return Pair.of(LOCK, funcName);
        }
        if (hasAtomicEnd(funcName)) {
            return Pair.of(UNLOCK, "__VERIFIER_atomic_end");
//            return Pair.of(UNLOCK, funcName);
        }

        if (hasLockBegin(funcName)) {
            return Pair.of(LOCK, getLockVarName(edge));
        }
        if (hasLockEnd(funcName)) {
            return Pair.of(UNLOCK, getLockVarName(edge));
        }

        return Pair.of(LOCK_FREE, null);
    }

    private Pair<LockStatus, String>
    getLockFromFunctionReturnEdge(CFunctionReturnEdge pFuncRetEdge) {
        String funcName = pFuncRetEdge.getFunctionEntry().getFunctionName(),
                curLockName = locks.get(inThread).isEmpty() ? null : locks.get(inThread).peek();
        assert funcName != null :
                "Cannot get the name of the called function for the return edge" + pFuncRetEdge;
        if (Objects.equals(funcName, curLockName)) {
            // Matched. The block ends by pFuncRetEdge.
            return Pair.of(UNLOCK, curLockName);
        }

        return Pair.of(LOCK_FREE, null);
    }

    private boolean hasLockEnd(String funcName) {
        for (Pattern pattern : lockEndPatterns) {
            if (pattern.matcher(funcName).find()) // Matched.
                return true;
        }
        return false;
    }

    private boolean hasAtomicEnd(String funcName) {
        for (Pattern pattern : atomicEndPatterns) {
            if (pattern.matcher(funcName).find()) // Matched.
                return true;
        }
        return false;
    }

    // FIXME: correct the match method.
    private boolean hasLockBegin(String funcName) {
        for (Pattern pattern : lockBeginPatterns) {
            if (pattern.matcher(funcName).find()) // Matched
                return true;
        }
        return false;
    }

    private boolean hasAtomicBegin(String funcName) {
        for (Pattern pattern : atomicBeginPatterns) {
            if (pattern.matcher(funcName).find()) // Matched
                return true;
        }
        return false;
    }

    // FIXME: Use table to record the extracted lock name.
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
        CIdExpression lockIdExpression;
        String lockName;
        try {
            lockIdExpression = (CIdExpression) unaryExpression.getOperand();
            lockName = lockIdExpression.getName();
        } catch (ClassCastException e) { // Operand is not the CIdExpression.
            try {
                // Just handle the simplest case, like: lock(&lock.mutex);
                CFieldReference fieldReference = (CFieldReference) unaryExpression.getOperand();
                CExpression fieldOwner = fieldReference.getFieldOwner();
                CIdExpression fieldOwnerIdExpr = (CIdExpression) fieldOwner;
                lockName = fieldOwnerIdExpr.getName() + "." + fieldReference.getFieldName();
            } catch (ClassCastException ee) { // Operand is not the simple field reference expression.
                // FIXME: How to handle the complex field reference.
                throw new ClassCastException("Complex lock var name is not supported yet.");
            }
        }

        return lockName;
    }

    /**
     * Update the lock status according to the locks of the current thread, and the lock
     * and lock status obtained from the current edge.
     * @param curLocks locks of the current thread.
     * @param pL pair of the lock and lock status obtained from the {@link #enteringEdge}.
     * @return Updated critical area action {@link CriticalAreaAction}.
     */
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
                    // NOTE: nested locks.
                    // Push a new lock after having pushed one before.
                    curLocks.push(l);
                    return CONTINUE;
                } else {
                    if (lockMatch(l, l0)) {
                        // Unlock l0, just remove it from the curLocks.
                        curLocks.pop();
                        return curLocks.isEmpty() ? END : CONTINUE;
                    } else if (curLocks.contains(l)) {
                        // FIXME: in this case, locks aren't acquired or released like a
                        //  stack: { ABBA }, instead they are nested out of the order like
                        //  : { ABAB }. Should we keep the critical area till all locks
                        //  released?
                        // Current critical area preserved.
                        curLocks.remove(l);
                        assert !curLocks.isEmpty() :
                                "Removing a lock not at the top of the " +
                                        "stack should leave locks not empty!";
                        return CONTINUE;
                    }
                    throw new UnsupportedOperationException(
                            "Trying to release a lock not held!");
                    // logger.log(Level.WARNING, "Release a lock not held!");
                }

            case CONTINUE:
                if (lockStatus == LOCK) {
                    // FIXME: nested locks.
                    curLocks.push(l);
                    // Add a new lock won't terminate the current critical area.
                    return CONTINUE;
                } else {
                    l0 = curLocks.peek();
                    if (lockMatch(l, l0)) {
                        // Unlock l0.
                        curLocks.pop();
                        return curLocks.isEmpty() ? END : CONTINUE;
                    } else if (curLocks.contains(l)) {
                        curLocks.remove(l);
                        assert !curLocks.isEmpty() :
                                "Removing a lock not at the top of the " +
                                        "stack should leave locks not empty!";
                        return CONTINUE;
                    }
                    throw new UnsupportedOperationException(
                            "Trying to release a lock not held!");
                }

            case END:
                assert curLocks.isEmpty();
            case NOT_IN:
                if (lockStatus == LOCK) {
                    curLocks.push(l);
                    return START;
                } else {
                    throw new UnsupportedOperationException(
                            "Trying to release a lock not held!");
                }

            default:
        }

        return NOT_IN;
    }


    // TODO: find a better implementation.
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