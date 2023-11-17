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

        if (OGMap.get(parOGState.getNum()) == null) {
            return Set.of();
        }

        // Initialize the fields. Update them in other place like 'strengthen'.
        OGPORState chOGState = new OGPORState(-1);
        chOGState.setLoops(parOGState.getLoops());
        chOGState.setLoopDepthTable(parOGState.getLoopDepthTable());
        chOGState.setNodeTable(parOGState.getNodeTable());
        chOGState.setLocks(parOGState.getLocks());

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

        // Update loop depth table.
        ogState.updateLoopDepth(cfaEdge);
        // Debug.
//        System.out.println("\u001b[31m" + cfaEdge + " @" + ogState.getLoopDepth() +
//                "\u001b[0m");

        // Update the current node map.
        updateNodeTable(ogState, cfaEdge, activeThread);

        return Set.of(state);
    }

    // TODO.
    private void updateNodeTable(OGPORState ogState, CFAEdge cfaEdge, String activeThread) {
        String rawStatement = cfaEdge.getRawStatement();
        Map<String, OGNode> nodeTable = ogState.getNodeTable();
        Map<String, Stack<String>> locks = ogState.getLocks();
        if (atomicBlockBeginFuncs.stream().anyMatch(rawStatement::contains)) {
            // __VERIFIER_atomic_begin, etc.
            if (locks.get(activeThread) != null) {
               if (locks.get(activeThread).isEmpty()) {
                    // No atomic block has already existed.
                   locks.get(activeThread).push("__VERIFIER_atomic_begin");
                   OGNode newNode = new OGNode(cfaEdge,
                           new ArrayList<>(Collections.singleton(cfaEdge)),
                           false,
                           false);
                   ogState.updateNode(activeThread, newNode);
               } else {
                   // some atomic blocks have existed.
                   // We don't start a new block in this case.
                   locks.get(activeThread).push("__VERIFIER_atomic_begin");
               }
            } else {
                // No atomic block has already existed.
                locks.put(activeThread, new Stack<>());
                locks.get(activeThread).push("__VERIFIER_atomic_begin");
                OGNode newNode = new OGNode(cfaEdge,
                        new ArrayList<>(Collections.singleton(cfaEdge)),
                        false,
                        false);
                ogState.updateNode(activeThread, newNode);
            }
        } else if (lockBeginFuncs.stream().anyMatch(rawStatement::contains)) {
            // common locks.
            // FIXME: it's not necessary to extract the shared vars each time we meet the
            //  edge.
            List<SharedEvent> sharedEvents = extractor.extractSharedVarsInfo(cfaEdge);
            Preconditions.checkArgument(sharedEvents.size() == 1,
                    "Exactly one lock variable is expected: " + cfaEdge);
            String lock = sharedEvents.iterator().next().getVar().getName();
            if (locks.get(activeThread) != null) {
                if (locks.get(activeThread).isEmpty()) {
                    // No atomic block has already existed.
                    locks.get(activeThread).push(lock);
                    OGNode newNode = new OGNode(cfaEdge,
                            new ArrayList<>(Collections.singleton(cfaEdge)),
                            false,
                            false);
                    ogState.updateNode(activeThread, newNode);
                } else {
                    // some atomic blocks have existed.
                    // We don't start a new block in this case.
                    locks.get(activeThread).push(lock);
                }
            } else {
                // No atomic block has already existed.
                locks.put(activeThread, new Stack<>());
                locks.get(activeThread).push(lock);
                OGNode newNode = new OGNode(cfaEdge,
                        new ArrayList<>(Collections.singleton(cfaEdge)),
                        false,
                        false);
                ogState.updateNode(activeThread, newNode);
            }
        } else if (atomicBlockEndFuncs.stream().anyMatch(rawStatement::contains)) {
            Preconditions.checkArgument(locks.get(activeThread) != null
                            && !locks.get(activeThread).isEmpty(),
                    "Atomic end without a begin.");
            // FIXME: multiple atomic-begin statements?
            Preconditions.checkArgument("__VERIFIER_atomic_begin".equals(locks.get(activeThread).peek()),
                    "Lock mismatches.");
            locks.get(activeThread).pop();
            if (locks.get(activeThread).isEmpty()) {
                // An atomic block ends.
                ogState.updateNode(activeThread, null);
            } else {
                // Atomic block hasn't ended yet because of the outer atomic blocks.
            }
        } else if (lockEndFuncs.stream().anyMatch(rawStatement::contains)) {
            List<SharedEvent> sharedEvents = extractor.extractSharedVarsInfo(cfaEdge);
            Preconditions.checkArgument(sharedEvents.size() == 1,
                    "Exactly one lock variable is expected." + cfaEdge);
            String lock = sharedEvents.iterator().next().getVar().getName();
            Preconditions.checkArgument(lock.equals(locks.get(activeThread).peek()),
                    "Lock mismatches.");
            locks.get(activeThread).pop();
            if (locks.get(activeThread).isEmpty()) {
                // An atomic block ends.
                ogState.updateNode(activeThread, null);
            } else {
                // Atomic block hasn't ended yet because of the outer atomic blocks.
            }
        } else {
            // Inside an atomic block or not.
            List<SharedEvent> sharedEvents = extractor.extractSharedVarsInfo(cfaEdge);
            if (!sharedEvents.isEmpty()) {
                OGNode curNode = nodeTable.get(activeThread);
                if (curNode != null) {
                    // We are inside a block, and just need to handle the events.
                    handleEvents(sharedEvents, curNode);
                } else {
                    // Not in a block, we create a new node for the cfaEdge and update
                    // the node table.
                    OGNode newNode = new OGNode(cfaEdge,
                            new ArrayList<>(Collections.singleton(cfaEdge)),
                            true, /* Simple node */
                            false);
                    handleEvents(sharedEvents, newNode);
                    ogState.updateNode(activeThread, newNode);
                }
            } else {
                // In this case, we have nothing to do whether we are inside a block or not.
            }
        }
    }

    private void handleEvents(List<SharedEvent> sharedEvents, OGNode ogNode) {

        sharedEvents.forEach(e -> {
            switch (e.getAType()) {
                case READ:
                    Set<SharedEvent> sameR = ogNode.getRs()
                            .stream()
                            .filter(r -> r.getVar().getName().equals(e.getVar().getName()))
                            .collect(Collectors.toSet()),
                            sameW = ogNode.getWs()
                                    .stream()
                                    .filter(w -> w.getVar().getName().equals(e.getVar().getName()))
                                    .collect(Collectors.toSet());
                    if (!sameR.isEmpty() || !sameW.isEmpty()) {
                        // For the same read var, only the first read will be added.
                        // If there is a w writes the same var with r, then r could be
                        // ignored, because it will always read the same value.
                    } else {
                        ogNode.addEvent(e);
                    }
                    break;
                case WRITE:
                    // for the same write var, only the last write will be added.
                    sameW = ogNode.getWs()
                            .stream()
                            .filter(w -> w.getVar().getName().equals(e.getVar().getName()))
                            .collect(Collectors.toSet());
                    if (!sameW.isEmpty()) {
                        sameW.forEach(ogNode::removeEvent);
                    }
                    ogNode.addEvent(e);
                default:
            }
        });
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
