package org.sosy_lab.cpachecker.cpa.por.ogpor;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.annotations.ReturnValuesAreNonnullByDefault;
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
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.globalinfo.OGInfo;
import org.sosy_lab.cpachecker.util.obsgraph.*;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadOperator;
import org.sosy_lab.cpachecker.cpa.location.LocationState;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        if (nodeMap == null || OGMap == null) {
            throw new InvalidConfigurationException("Please enable the utils.globalInfo" +
                    ".OGInfo.useOG");
        }
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state,
            Precision precision,
            CFAEdge cfaEdge)
            throws CPATransferException, InterruptedException {

        OGPORState parOGState = (OGPORState) state;

        // generate the child state with thread info updated.
        OGPORState chOGState = new OGPORState(parOGState.getNum() + 1);

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
        for (AbstractState s : otherStates) {
            if (s instanceof ThreadingState) {
                ThreadingState threadingState = (ThreadingState) s;
                // Set 'threads' for ogState.
                threadingState.getThreadIds().forEach(tid -> {
                    ogState.getThreads().put(tid,
                            threadingState.getThreadLocation(tid)
                                    .getLocationNode()
                                    .toString());
                });
                // Set 'inThread'.
                ogState.setInThread(getActiveThread(cfaEdge, threadingState));
            }
        }

        return Set.of(state);
    }

    @Nullable
    private String getActiveThread(final CFAEdge pEdge,
                                   final ThreadingState threadingState) {
        Set<String> activeThreads = new HashSet<>();
        for (String tid : threadingState.getThreadIds()) {
            if (Iterables.contains(
                    /* Get all outgoing edges for thread location of thread tid. */
                    threadingState.getThreadLocation(tid).getOutgoingEdges(),
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
