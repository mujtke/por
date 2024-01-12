package org.sosy_lab.cpachecker.cpa.por.ogpor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.annotations.ReturnValuesAreNonnullByDefault;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.stream.Collectors;

@Options(prefix="cpa.ogpor")
public class OGPORTransferRelation extends SingleEdgeTransferRelation {

    @Option(secure = true,
            description = "the set of functions which begin atomic areas.")
    private Set<String> atomicBlockBeginFuncs = ImmutableSet.of("__VERIFIER_atomic_begin");

    @Option(secure = true,
            description = "the set of functions which end atomic areas.")
    private Set<String> atomicBlockEndFuncs = ImmutableSet.of("__VERIFIER_atomic_end");

    @Option(secure = true,
            description = "the set of functions which acquire locks.")
    private Set<String> lockBeginFuncs = ImmutableSet.of("pthread_mutex_lock", "lock");

    @Option(secure = true,
            description = "the set of functions which release locks.")
    private Set<String> lockEndFuncs = ImmutableSet.of("pthread_mutex_unlock", "unlock");

    private final SharedVarsExtractor extractor = new SharedVarsExtractor();

    // Map: stateId -> List of OG.
    private final Map<Integer, List<ObsGraph>> OGMap;

    // Map: edge.hashCode() -> OGNode.
    private final Map<Integer, OGNode> nodeMap;
    private final String mainThreadId;
    private final CFANode mainExitNode;

    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    public OGPORTransferRelation(Configuration pConfig, 
            CFA pCfa, 
            LogManager pLogger,
            ShutdownNotifier pShutdownNotifier)
            throws InvalidConfigurationException {
        pConfig.inject(this);
        mainThreadId = pCfa.getMainFunction().getFunctionName();
        mainExitNode = pCfa.getMainFunction().getExitNode();
        logger = pLogger;
        shutdownNotifier = pShutdownNotifier;
        nodeMap = GlobalInfo.getInstance().getOgInfo().getNodeMap();
        OGMap = GlobalInfo.getInstance().getOgInfo().getOGMap();
        if (OGMap == null) {
            throw new InvalidConfigurationException("OGMap unusable, please enable the " +
                    "utils.globalInfo.OGInfo.useOG");
        }
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state,
            Precision precision,
            CFAEdge cfaEdge)
            throws CPATransferException, InterruptedException {

        OGPORState parOGState = (OGPORState) state;

        if (OGMap.get(parOGState.getNum()) == null) {
            return Set.of();
        }

        // Initialize the fields. Update them in another place like 'strengthen'.
        OGPORState chOGState = new OGPORState(-1, cfaEdge);
        chOGState.setLoops(parOGState.getLoops());
        chOGState.setLoopDepthTable(parOGState.getLoopDepthTable());
        chOGState.setLocks(parOGState.getLocks());
        chOGState.setCaas(parOGState.getCaas());

        return Set.of(chOGState);
    }

    @Override
    @ParametersAreNonnullByDefault
    @ReturnValuesAreNonnullByDefault
    public Collection<? extends AbstractState>
    strengthen(AbstractState state,
               Iterable<AbstractState> otherStates,
               @Nullable CFAEdge cfaEdge,
               Precision precision)
            throws CPATransferException, InterruptedException {
        OGPORState ogState = (OGPORState) state;
        // Update threads.
        String activeThread = null;
        for (AbstractState s : otherStates) {
            if (s instanceof ThreadingState) {
                ThreadingState threadingState = (ThreadingState) s;
                // Set 'threads' for ogState.
                threadingState.getThreadIds().forEach(tid -> ogState.getThreads()
                        .put(tid, threadingState.getThreadLocation(tid)
                                .getLocationNode()
                                .toString()));
                // Set 'inThread'.
                activeThread = getActiveThread(cfaEdge, threadingState);
                ogState.setInThread(activeThread);
            }
        }
        Preconditions.checkState(activeThread != null,
                "Failed to get active thread: " + cfaEdge);

        assert cfaEdge != null;
        // Update loop depth table.
        ogState.updateLoopDepth(cfaEdge);

        // Debug.
//        System.out.println("\u001b[31m" + cfaEdge + " @" + ogState.getLoopDepth() +
//                "\u001b[0m");

        // Update lock status.
        ogState.updateLockStatus(cfaEdge);

        return Set.of(state);
    }

    @Nullable
    private String getActiveThread(final CFAEdge pEdge,
                                   final ThreadingState threadingState) {
        Set<String> activeThreads = new HashSet<>();
        for (String tid : threadingState.getThreadIds()) {
            if (Iterables.contains(
                    // Get all Ingoing edges (because the threading state is in the
                    // child OGPORState, so we should get the Ingoing edges) for thread
                    // location of thread tid.
                    threadingState.getThreadLocation(tid).getIngoingEdges(),
                    /* If one of them matches pEdge, then tid should be active thread. */
                    pEdge)) {
                activeThreads.add(tid);
            }
        }
        assert activeThreads.size() <= 1:
                "multiple active threads are not allowed: " + activeThreads;

        return activeThreads.isEmpty() ? null : Iterables.getOnlyElement(activeThreads);
    }
}
