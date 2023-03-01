package org.sosy_lab.cpachecker.cpa.por.ogpor;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadOperator;

import java.util.*;

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

    private final String mainThreadId;

    // use ThreadOperator to update the thread info.
    private final ThreadOperator threadOptr;


    public OGPORTransferRelation (
            Configuration pConfig,
            CFA pCfa)
    throws InvalidConfigurationException {
        pConfig.inject(this);
        mainThreadId = pCfa.getMainFunction().getFunctionName();
        threadOptr = new ThreadOperator(
                forConcurrent,
                allowMultipleLHS,
                maxNumberOfThreads,
                useClonedFunctions,
                useAllPossibleClones,
                useIncClonedFunc,
                mainThreadId,
                pCfa);
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state, Precision precision, CFAEdge cfaEdge) throws CPATransferException, InterruptedException {

        OGPORState parOGPORState = (OGPORState) state;

        // case1: there is not any obs graph w.r.t parState.

        // if not the case, we first generate child state & update the thread info at the same time.
        OGPORState chOGPORState = genAndUpdateThreadInfo(parOGPORState, cfaEdge);

        // case2: there are some obs graphs in parState, then we add all graphs that don't
        // conflict with the cfaEdge to the new state after handling all events w.r.t the cfaEdge.

        return chOGPORState == null ? Collections.emptySet() : Set.of(chOGPORState);
    }

    private OGPORState genAndUpdateThreadInfo(OGPORState parState, CFAEdge cfaEdge) {

        MultiThreadState parMultiThreadState = parState.getMultiThreadState();
        Map<String, Triple<Integer, Integer, Integer>> newThreadStatusMap =
                new HashMap<>(), oldThreadStatusMap = parState.getThreadStatusMap();

        // first: handle the exitThreads.
        AbstractState chStateExitThreads = threadOptr.exitThreads(parState,
                parState.getClass());
        assert chStateExitThreads instanceof OGPORState && chStateExitThreads != null;
        MultiThreadState chState = ((OGPORState) chStateExitThreads).getMultiThreadState();
        for (Iterator<String> it = oldThreadStatusMap.keySet().iterator(); it.hasNext();) {
            String tid = it.next();
            if (chState.getThreadIds().contains(tid)) {
                newThreadStatusMap.put(tid, oldThreadStatusMap.get(tid));
            }
        }

        String transInThread = threadOptr.getThreadIdThroughEdge(parMultiThreadState, cfaEdge);
        if (transInThread == null) {
            return null;
        }

        // next: generate new thread info if a new thread is created.
        // newThreadInfo = [thread_id, <SingleThreadState>].
        // <SingleThreadState> = <cfaNode, thread_num>.
        Pair<String, SingleThreadState> newThreadInfo = null;
        try {
            newThreadInfo = threadOptr.genNewThreadTransferInfo(parMultiThreadState, cfaEdge);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (newThreadInfo != null) {
            // a new thread created.
            int parThreadNum = parMultiThreadState.getThreadLocations().get(transInThread).getNum();
            newThreadStatusMap.put(newThreadInfo.getFirstNotNull(), Triple.of(parThreadNum,
                    newThreadInfo.getSecondNotNull().getNum(), 1));
            chState.getThreadLocations().put(newThreadInfo.getFirstNotNull(),
                    newThreadInfo.getSecondNotNull());
        }

        // third: update the NO.x of transInThread & update the location which caused by cfaEdge.
        // l0 --- cfaEdge ---> l1
        Triple<Integer, Integer, Integer> oldThreadStatus = newThreadStatusMap.get(transInThread);
        int fir = oldThreadStatus.getFirst(), sec = oldThreadStatus.getSecond(), thi =
                oldThreadStatus.getThird() + 1;
        newThreadStatusMap.put(transInThread, Triple.of(fir, sec, thi));

        // update location.
        SingleThreadState transInThreadState = chState.getThreadLocations().get(transInThread);
        SingleThreadState newSingleThreadState = new SingleThreadState(cfaEdge.getSuccessor(),
                transInThreadState.getNum());
        chState.getThreadLocations().put(transInThread, newSingleThreadState);
        chState.setTransThread(transInThread);
        OGPORState chOGPORState = new OGPORState(chState, newThreadStatusMap);
//         debug.
//        System.out.println(cfaEdge + "\n" + chOGPORState.getMultiThreadState().toString());

        return chOGPORState;
    }
}
