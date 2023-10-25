package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Options(prefix="util.obsgraph")
public class OGNodeBuilder {

    Configuration config;
    CFA cfa;

    private static final String THREAD_MUTEX_LOCK = "pthread_mutex_lock";
    private static final String THREAD_MUTEX_UNLOCK = "pthread_mutex_unlock";
    private static final String VERIFIER_ATOMIC_BEGIN = "__VERIFIER_atomic_begin";
    private static final String VERIFIER_ATOMIC_END = "__VERIFIER_atomic_end";

    private final SharedVarsExtractor extractor = new SharedVarsExtractor();
    public OGNodeBuilder(Configuration pConfig, CFA pCfa)
        throws InvalidConfigurationException {
        cfa = pCfa;
        config = pConfig;
        config.inject(this);
    };

    public Map<Integer, OGNode> build() {

        Map<Integer, OGNode> ogNodes = new HashMap<>();
        Set<CFANode> visitedFuncs = new HashSet<>();

        for (CFANode funcEntryNode : cfa.getAllFunctionHeads()) {

            if (visitedFuncs.contains(funcEntryNode)) continue;
            Stack<CFANode> waitlist = new Stack<>();
            Stack<Var> locks = new Stack<>();
            Set<CFANode> withBlock = new HashSet<>();
            Set<Integer> visitedEdges = new HashSet<>();
            Map<CFANode, OGNode> blockNodeMap = new HashMap<>();
            waitlist.push(funcEntryNode);

            while (!waitlist.isEmpty()) {
                CFANode pre = waitlist.pop();

                if (pre instanceof FunctionExitNode) continue;

                for (int i = 0; i < pre.getNumLeavingEdges(); i++) {
                    CFAEdge edge = pre.getLeavingEdge(i);
                    CFANode suc = edge.getSuccessor();

                    if (visitedEdges.contains(edge.hashCode())) continue;

                    if (pre.getLeavingSummaryEdge() != null) {
                        // edge is a function call. Don't enter the inside of the func,
                        // use summaryEdge as a substitute, and suc should also change.
//                        edge = pre.getLeavingSummaryEdge();
//                        suc = edge.getSuccessor();
                        suc = pre.getLeavingSummaryEdge().getSuccessor();
                    }

                    // debug
                    System.out.println(edge);

                    if (withBlock.contains(pre)) {
                        OGNode preNode = blockNodeMap.get(pre);
                        Preconditions.checkState(preNode != null,
                                "Missing OGNode for edge: " + edge);
                        List<SharedEvent> sharedEvents =
                                extractor.extractSharedVarsInfo(edge);
                        if (sharedEvents != null && !sharedEvents.isEmpty()) {
                            handleEvents(sharedEvents, preNode);
                        }
                        if (!hasAtomicEnd(locks, edge, sharedEvents)) {
                            withBlock.add(suc);
                        }
                        preNode.getBlockEdges().add(edge);
                        ogNodes.put(edge.hashCode(), preNode);
                        blockNodeMap.put(suc, preNode);
                    } else {
                        Preconditions.checkState(!ogNodes.containsKey(edge.hashCode()),
                                "Duplicated key for Edge: %s", edge.getRawStatement());
                        List<SharedEvent> sharedEvents =
                                extractor.extractSharedVarsInfo(edge);
                        OGNode newBlockNode = null;
                        if (hasAtomicBegin(locks, edge, sharedEvents)) {
                            newBlockNode = new OGNode(edge,
                                    new ArrayList<CFAEdge>(List.of(edge)),
                                    false, /* Complicated Node */
                                    false,
                                    new HashSet<SharedEvent>(),
                                    new HashSet<SharedEvent>());
                            withBlock.add(suc);
                            if (!sharedEvents.isEmpty()) {
                                handleEvents(sharedEvents, newBlockNode);
                            }
                        } else {
                            // else, normal edge not in a block.
                            // If no shared events, just skip.
                            newBlockNode = new OGNode(edge,
                                    new ArrayList<CFAEdge>(List.of(edge)),
                                    true, /* Simple node */
                                    false,
                                    new HashSet<SharedEvent>(),
                                    new HashSet<SharedEvent>());
                            if (sharedEvents.isEmpty()) {
                                visitedEdges.add(edge.hashCode());
                                waitlist.add(suc);
                                continue;
                            }
                            handleEvents(sharedEvents, newBlockNode);
                        }
                        ogNodes.put(edge.hashCode(), newBlockNode);
                        blockNodeMap.put(suc, newBlockNode);
                    }
                    visitedEdges.add(edge.hashCode());
                    waitlist.add(suc);
                }
            }
        }

        // export.
        export(ogNodes);

        return ogNodes;
    }

    private void handleEvents(List<SharedEvent> sharedEvents, OGNode ogNode) {

        sharedEvents.forEach(e -> {
            switch (e.getAType()) {
                case READ:
                    // for the same read var, only the first read will be added.
                    Set<SharedEvent> sameR = ogNode.getRs()
                            .stream()
                            .filter(r -> r.getVar().getName().equals(e.getVar().getName()))
                            .collect(Collectors.toSet()),
                            sameW = ogNode.getWs()
                                    .stream()
                                    .filter(w -> w.getVar().getName().equals(e.getVar().getName()))
                                    .collect(Collectors.toSet());
                    if (!sameR.isEmpty()) {
                        break;
                    } else if(!sameW.isEmpty()) {
                        // If there is a w writes the same var with r, then r could be
                        // ignored, because it will always read the same value.
                        break;
                    } else {
                        ogNode.getRs().add(e);
                        e.setInNode(ogNode);
                    }
                    break;
                case WRITE:
                    // for the same write var, only the last write will be added.
                    sameW = ogNode.getWs()
                            .stream()
                            .filter(w -> w.getVar().getName().equals(e.getVar().getName()))
                            .collect(Collectors.toSet());
                    if (!sameW.isEmpty()) {
                        ogNode.getWs().removeAll(sameW);
                    }
                    ogNode.getWs().add(e);
                    e.setInNode(ogNode);
                default:
            }
        });
    }

    boolean hasAtomicBegin(Stack<Var> locks, CFAEdge edge, List<SharedEvent> sharedEvents) {
        // Skip the declaration.
        if (edge instanceof CDeclarationEdge) {
            return false;
        }

        if (edge.getRawStatement().contains(VERIFIER_ATOMIC_BEGIN)) {
            return true;
        } else if (edge.getRawStatement().contains(THREAD_MUTEX_LOCK)) {
            Preconditions.checkArgument(sharedEvents.size() == 1,
                    "Exactly one lock variable is expected.");
            Var curLock = sharedEvents.iterator().next().getVar();
            locks.push(curLock);
            return true;
        };

        return false;
    }

    boolean hasAtomicEnd(Stack<Var> locks, CFAEdge edge, List<SharedEvent> sharedEvents) {
        if(edge.getRawStatement().contains(THREAD_MUTEX_UNLOCK)
                || edge.getRawStatement().contains(VERIFIER_ATOMIC_END)) {
            if (edge.getRawStatement().contains(VERIFIER_ATOMIC_END)) return true;
            // Else, edge unlock some lock, in this case we need to judge whether multi
            // locks are held. FIXME: if so, how can we end the atomic block?
            Preconditions.checkArgument(!locks.isEmpty(),
                    "Try to unlock without locking.");
            Var curLock = null;
            if (sharedEvents.isEmpty()) {
                // Which means the lock variable is local.
                // TODO: Extract local lock variable.
            } else {
                // Which means the lock variable is gloal.
                Preconditions.checkArgument(sharedEvents.size() == 1,
                        "Exactly one lock variable is expected.");
                curLock = sharedEvents.iterator().next().getVar();
            }
            Preconditions.checkArgument(curLock != null,
                    "Get lock failed.");
            if (curLock.equals(locks.peek())) {
                locks.pop();
            } else if (curLock.equals(locks.get(0))) {
                // If the curLock is equal to the first lock in locks, we clear all
                // locks, Which maybe unsound.
                locks.clear();
            }

            if (locks.isEmpty()) {
                // When locks is empty, we think we reach the end of the lock block.
                return true;
            }
        };

        return false;
    }

    private void export(Map<Integer, OGNode> nodes) {
        Path dotPath = Paths.get("output/ogNodesTable");
        try {
            FileWriter fw = new FileWriter(dotPath.toFile(), Charset.defaultCharset());
            Iterator<Map.Entry<Integer, OGNode>> it = nodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, OGNode> entry = it.next();
                fw.write("Node" 
                        + entry.getKey().toString() 
                        + ":\n"
                        + entry.getValue().toString()
                        + "\n");
            }
            fw.close();
        } catch(IOException e){
            e.printStackTrace();
        }
    }
}
