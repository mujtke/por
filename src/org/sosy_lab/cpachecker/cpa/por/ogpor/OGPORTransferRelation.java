package org.sosy_lab.cpachecker.cpa.por.ogpor;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@Options(prefix="cpa.ogpor")
public class OGPORTransferRelation extends SingleEdgeTransferRelation {

    private final SharedVarsExtractor extractor = new SharedVarsExtractor();

    // Map: stateId -> List of OG.
    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, List<SharedEvent>> edgeVarMap;

    private final String mainThreadId;
    private final CFANode mainExitNode;

    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    @Option(secure = true,
            description = "the set of functions which begin atomic areas.")
    private Set<String> atomicBegins = ImmutableSet.of("__VERIFIER_atomic_begin"
            , "__VERIFIER_atomic_r", "__VERIFIER_atomic_w");

    @Option(secure = true,
            description = "the set of functions which end atomic areas.")
    private Set<String> atomicEnds = ImmutableSet.of(
            "__VERIFIER_atomic_end");

    // FIXME: A lock doesn't always start an atomic area. As an example, if only one
    //  thread try to acquire the lock, then other threads could still be scheduled
    //  after the former enter the critical area.
    @Option(secure = true,
            description = "the set of functions which acquire locks.")
    private Set<String> lockBegins = ImmutableSet.of("pthread_mutex_lock",
            "pthread_lock", "lock");

    @Option(secure = true,
            description = "the set of functions which release locks.")
    private Set<String> lockEnds = ImmutableSet.of("pthread_mutex_unlock",
            "pthread_unlock", "unlock");
    public Set<String> getLockEnds() { return lockEnds; }

    public Set<String> getLockBegins() { return lockBegins; }

    public Set<String> getAtomicEnds() { return atomicEnds; }

    public Set<String> getAtomicBegins() { return atomicBegins; }
    public OGPORTransferRelation(Configuration pConfig,
            CFA pCfa, 
            LogManager pLogger,
            ShutdownNotifier pShutdownNotifier)
            throws InvalidConfigurationException {
        pConfig.inject(this);
        mainThreadId = pCfa.getMainFunction().getFunctionName();
        mainExitNode = pCfa.getMainFunction().getExitNode();
        assert mainExitNode != null;
        logger = pLogger;
        shutdownNotifier = pShutdownNotifier;
        OGMap = GlobalInfo.getInstance().getOgInfo().getOGMap();
        edgeVarMap = GlobalInfo.getInstance().getOgInfo().getEdgeVarMap();
        if (OGMap == null || edgeVarMap == null) {
            throw new InvalidConfigurationException("OGMap or edgeVarMap unavailable, " +
                    "enable them by setting utils.globalInfo.OGInfo.useOG = true.");
        }
    }

    @Override
        public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
            AbstractState state,
            Precision precision,
            CFAEdge cfaEdge)
            throws CPATransferException, InterruptedException {

        OGPORState parOGState = (OGPORState) state;
//        parOGState.setWillExit(cfaEdge);

        if (OGMap.get(parOGState.getSid()) == null) {
            return Set.of();
        }

        if (!edgeVarMap.containsKey(cfaEdge.hashCode())) {
            edgeVarMap.put(cfaEdge.hashCode(), extractor.extractSharedVarsInfo(cfaEdge));
        }

        return Set.of(createOGState(parOGState, cfaEdge));
    }

    private OGPORState createOGState(OGPORState parOGState, CFAEdge cfaEdge) {

        OGPORState chOGState = new OGPORState(parOGState.getPathLen() + 1, cfaEdge);
        // initialize some fields of chOGState by using parOGState's. We will update
        // them in 'strengthen' method if needed.
        chOGState.setLoops(parOGState.getLoops());
        chOGState.setLoopDepthTable(parOGState.getLoopDepthTable());
        chOGState.setLocks(parOGState.getLocks());
        chOGState.setCaas(parOGState.getCaas());
        chOGState.setThreads(parOGState.getThreads());
        chOGState.setParentThread(parOGState.getParentThread());
        chOGState.setBlockedThreads(parOGState.getBlockedThreads());

        return chOGState;
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
        ThreadingState threadingState = null;
        for (AbstractState s : otherStates) {
            if (s instanceof ThreadingState) {
                threadingState = (ThreadingState) s;
                break;
            }
        }

        assert threadingState != null :
                "Failed to get threadingState and set the thread relatives.";

        // FIXME: This requires OGPORCPA is put before ThreadingCPA.
        Set<String> createdThreads = new HashSet<>(Sets.difference(threadingState.getThreadIds(),
                ogState.getThreads().keySet())),
                exitedThreads = new HashSet<>(Sets.difference(ogState.getThreads().keySet(),
                        threadingState.getThreadIds()));

        // Set 'threads' for ogState.
        ThreadingState finalThreadingState = threadingState;
        threadingState.getThreadIds().forEach(tid -> ogState.getThreads().put(tid,
                finalThreadingState.getThreadLocation(tid).getLocationNode().toString()));

        // Set 'inThread'.
        String activeThread = getActiveThread(cfaEdge, threadingState);
        assert activeThread != null : "Failed to get active thread: " + cfaEdge;
        ogState.setInThread(activeThread);


        // Remove exited threads.
        exitedThreads.forEach(t -> {
            // If a thread exited, remove it from the parent thread map.
            ogState.removeParentThread(t);
            ogState.getThreads().remove(t);
        });

        // Set the parent threads for newly created threads.
        createdThreads.forEach(t -> ogState.setParentThread(t, activeThread));

        assert cfaEdge != null;
        // Update loop depth table.
        ogState.updateLoopDepth(cfaEdge);
        // Check exit.
        LocationState mainLoc = threadingState.getThreadLocation(mainThreadId);
        mainLoc.getOutgoingEdges().forEach(ogState::setWillExit);

        // Debug.
//        System.out.println(
//                "\u001b[31m" + cfaEdge + " @" + ogState.getLoopDepth() + "\u001b[0m");

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
                "Multiple active threads are not allowed: " + activeThreads + "!";

        return activeThreads.isEmpty() ? null : Iterables.getOnlyElement(activeThreads);
    }
}