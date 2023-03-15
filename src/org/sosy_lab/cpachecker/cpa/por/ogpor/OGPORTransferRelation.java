package org.sosy_lab.cpachecker.cpa.por.ogpor;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.*;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadOperator;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Options(prefix="cpa.ogpor")
public class OGPORTransferRelation extends SingleEdgeTransferRelation {

    @Option(
            secure = true,
            description = "With this option enabled, the migration between two abstract states will"
                + " apply to concurrent programs (compatible with serial programs)."
    )
    private boolean forConcurrent = true;

    @Option(
            secure = true,
            description = "allow assignments of a new thread to the same LHS as an existing thread."
    )
    private boolean allowMultipleLHS = false;

    @Option(
            secure = true,
            description = "the maximal number of parallel threads, -1 for infinite. "
                + "When combined with 'useClonedFunctions=true', we need at least N cloned "
                + "functions. The option 'cfa.cfaCloner.numberOfCopies' should be set to N."
    )
    private int maxNumberOfThreads = 5;

    @Option(
            secure = true,
            description = "do not use the original functions from the CFA, but use cloned noes. "
                + "See cfa.postprocessing.CFACloner for details."
    )
    private boolean useClonedFunctions = false;

    @Option(
            secure = true,
            description = "in case of witness validation we need to check all possible function "
                + "calls of cloned CFAs."
    )
    private boolean useAllPossibleClones = false;

    @Option(
            secure = true,
            description = "use increased number of each newly created same thread."
                + "When this option is enabled, we need not to clone a thread function many times"
                + "if every thread function is only used once (i.e., cfa.cfaCloner.numberOfCopies"
                + "can be set to 1)."
    )
    private boolean useIncClonedFunc = false;

    @Option(
            secure = true,
            description = "the set of functions which begin atomic areas."
    )
    private Set<String> atomicBlockBeginFuncs = ImmutableSet.of("__VERIFIER_atomic_begin");

    @Option(
            secure = true,
            description = "the set of functions which end atomic areas."
    )
    private Set<String> atomicBlockEndFuncs = ImmutableSet.of("__VERIFIER_atomic_end");

    @Option(
            secure = true,
            description = "the set of functions which acquire locks."
    )
    private Set<String> lockBeginFuncs = ImmutableSet.of("pthread_mutex_lock", "lock");

    @Option(
            secure = true,
            description = "the set of functions which release locks."
    )
    private Set<String> lockEndFuncs = ImmutableSet.of("pthread_mutex_unlock", "unlock");

    private final String mainThreadId;
    private final CFANode mainExitNode;

    // use ThreadOperator to update the thread info.
    private final ThreadOperator threadOptr;

    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    public OGPORTransferRelation (Configuration pConfig,CFA pCfa, LogManager pLogger,
                                  ShutdownNotifier pShutdownNotifier)
    throws InvalidConfigurationException {
        pConfig.inject(this);
        mainThreadId = pCfa.getMainFunction().getFunctionName();
        mainExitNode = pCfa.getMainFunction().getExitNode();
        threadOptr = new ThreadOperator(
                forConcurrent,
                allowMultipleLHS,
                maxNumberOfThreads,
                useClonedFunctions,
                useAllPossibleClones,
                useIncClonedFunc,
                mainThreadId,
                pCfa);
        logger = pLogger;
        shutdownNotifier = pShutdownNotifier;
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state, Precision precision, CFAEdge cfaEdge) throws CPATransferException, InterruptedException {

        OGPORState parOGPORState = (OGPORState) state;

        // generate the child state with thread info updated.
        OGPORState chOGPORState = genAndUpdateThreadInfo(parOGPORState, cfaEdge);
        if (chOGPORState == null) {
            return Collections.emptySet();
        }

        // check whether enter/exit/in block area and update the block status.
        checkAndSetBlockStatus(parOGPORState.getBlockStatus(), chOGPORState, cfaEdge);

        return Set.of(chOGPORState);
    }

    /**
     * The function update the threadStatusMap and threadLocations
     * @param parState
     * @param cfaEdge
     * @return
     */
    private OGPORState genAndUpdateThreadInfo(OGPORState parState, CFAEdge cfaEdge) {

        Map<String, Triple<Integer, Integer, Integer>> newThreadStatusMap =
                new HashMap<>(), oldThreadStatusMap = parState.getThreadStatusMap();

        // first: handle the exitThreads.
        OGPORState exitThreads = (OGPORState) threadOptr.exitThreads(parState,
                parState.getClass()); // after exiting the thread, we still in parState.
        MultiThreadState exitMultiThrState = exitThreads.getMultiThreadState();
        for (Iterator<String> it = oldThreadStatusMap.keySet().iterator(); it.hasNext();) {
            String tid = it.next();
            if (exitMultiThrState.getThreadIds().contains(tid)) {
                newThreadStatusMap.put(tid, oldThreadStatusMap.get(tid));
            }
        }

        String transInThread = threadOptr.getThreadIdThroughEdge(exitMultiThrState, cfaEdge);
        if (transInThread == null) {
            return null;
        }

        // next: generate new thread info if a new thread is created.
        // newThreadInfo = [thread_id, <SingleThreadState>].
        // <SingleThreadState> = <cfaNode, thread_num>.
        Pair<String, SingleThreadState> newThreadInfo = null;
        try {
            // note, here we should use the exitMultiThrState that has exited the terminated
            // threads, instead of the original one.
            newThreadInfo = threadOptr.genNewThreadTransferInfo(exitMultiThrState, cfaEdge);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (newThreadInfo != null) {
            // a new thread created.
            int parThreadNum =
                    exitMultiThrState.getThreadLocations().get(transInThread).getNum();
            newThreadStatusMap.put(newThreadInfo.getFirstNotNull(), Triple.of(parThreadNum,
                    newThreadInfo.getSecondNotNull().getNum(), 1));
            exitMultiThrState.getThreadLocations().put(newThreadInfo.getFirstNotNull(),
                    newThreadInfo.getSecondNotNull());
        }

        // third: update the event number of the transInThread
        Triple<Integer, Integer, Integer> oldThreadStatus = newThreadStatusMap.get(transInThread);
        int fir = oldThreadStatus.getFirst(), sec = oldThreadStatus.getSecond(), thi =
                oldThreadStatus.getThird() + 1;
        newThreadStatusMap.put(transInThread, Triple.of(fir, sec, thi));

        // fourth: update the location whose change is caused by cfaEdge: l0 --- cfaEdge ---> l1
        SingleThreadState transInThreadState = exitMultiThrState.getThreadLocations().get(transInThread);
        SingleThreadState newSingleThreadState = new SingleThreadState(cfaEdge.getSuccessor(),
                transInThreadState.getNum());
        exitMultiThrState.getThreadLocations().put(transInThread, newSingleThreadState);
        exitMultiThrState.setTransThread(transInThread);
        OGPORState chOGPORState = new OGPORState(exitMultiThrState, newThreadStatusMap);

        // fifth: if the newThreadInfo != null, then we put the newly create thread into the
        // waitingThreads of chOGPORState.
        chOGPORState.getWaitingThreads().addAll(parState.getWaitingThreads());
        if (newThreadInfo != null) {
            chOGPORState.getWaitingThreads().add(newThreadInfo.getSecondNotNull().getNum());
            chOGPORState.spawnThread = newThreadStatusMap.get(newThreadInfo.getFirstNotNull());
        }

        return chOGPORState;
    }

    private void checkAndSetBlockStatus(BlockStatus parBlockStatus, OGPORState chState,
                                        CFAEdge cfaEdge) {
        if (cfaEdge instanceof CFunctionCallEdge) {
            // if cfaEdge is the fun call like '__VERIFIER_atomic_begin()',
            // 'pthread_mutex_lock' or other block begin functions.
            Optional<CFunctionCall> funCall = ((CFunctionCallEdge) cfaEdge).getRawAST();
            assert funCall.isPresent() : "function edge should have a funCall expression";
            String funcName =
                    funCall.get().getFunctionCallExpression().getFunctionNameExpression().toASTString();
            if (atomicBlockBeginFuncs.contains(funcName) || lockBeginFuncs.contains(funcName)) {
                // block begin.
                chState.setBlockStatus(BlockStatus.BLOCK_BEGIN);
            } else if (atomicBlockEndFuncs.contains(funcName) || lockEndFuncs.contains(funcName)) {
                // block end.
                chState.setBlockStatus(BlockStatus.BLOCK_END);
            }
        } else {
            // non-functionCallEdge.
            if (parBlockStatus == BlockStatus.BLOCK_BEGIN || parBlockStatus == BlockStatus.BLOCK_INSIDE) {
                // current cfaEdge is inside a block.
                chState.setBlockStatus(BlockStatus.BLOCK_INSIDE);
            } else {
                // not in a block.
                chState.setBlockStatus(BlockStatus.NOT_IN_BLOCK);
            }
        }
    }

}
