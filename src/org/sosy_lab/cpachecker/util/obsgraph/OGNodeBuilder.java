package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
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
            Set<CFANode> withBlock = new HashSet<>();
            Set<Integer> visitedEdges = new HashSet<>();
            Map<CFANode, Stack<Var>> locks = new HashMap<>();
            // Because of inner branches, one cfa node may correspond to more than one
            // OGNode.
            Map<CFANode, Stack<OGNode>> blockNodeMap = new HashMap<>();
            waitlist.push(funcEntryNode);

            while (!waitlist.isEmpty()) {
                CFANode pre = waitlist.pop();

                if (pre instanceof FunctionExitNode) continue;

                for (int i = 0; i < pre.getNumLeavingEdges(); i++) {
                    CFAEdge edge = pre.getLeavingEdge(i);
                    CFANode suc = edge.getSuccessor();

                    if (visitedEdges.contains(edge.hashCode())) {
                        // FIXME: The edge may be visited by other coNodes, if so, we
                        //  should not skip it for the current node.
                        if (!(withBlock.contains(pre)
                                && ogNodes.containsKey(edge.hashCode())
                                && ogNodes.get(edge.hashCode()).getBlockEdges().contains(edge))) {
                            continue;
                        }
                        // Else, we are in a block and reach an edge we have visited,
                        // we add the edge to the current Node.
                        // FIXME: current node is the last one in blockNodeMap.get(pre)?
                        Preconditions.checkArgument(blockNodeMap.containsKey(pre),
                                "Missing key for CFANode: " + pre);
                        OGNode curNode = blockNodeMap.get(pre).peek();
                        curNode.getBlockEdges().add(edge);
                    }

                    if (pre.getLeavingSummaryEdge() != null) {
                        // edge is a function call. Don't enter the inside of the func,
                        // use summaryEdge as a substitute, and suc should also change.
//                        edge = pre.getLeavingSummaryEdge();
//                        suc = edge.getSuccessor();
                        suc = pre.getLeavingSummaryEdge().getSuccessor();
                    }

                    // debug
//                    System.out.println(edge);

                    if (withBlock.contains(pre)) {
                        Preconditions.checkState(!blockNodeMap.get(pre).isEmpty(),
                                "Missing OGNode for edge: " + edge);
                        OGNode preNode = blockNodeMap.get(pre).peek();

                        // Handle the conditional branches inside the block.
                        if (edge instanceof AssumeEdge) {
                            Preconditions.checkArgument(pre.getNumLeavingEdges() == 2,
                                    "The number of conditional branches != 2.");
                            CFAEdge coEdge = edge.equals(pre.getLeavingEdge(0)) ?
                                    pre.getLeavingEdge(1) : pre.getLeavingEdge(0);
                            if (ogNodes.containsKey(coEdge.hashCode())) {
                                // If coEdge has been processed, then we should create a
                                // new node (coNode) for the current edge.
                                OGNode node = preNode.deepCopy(new HashMap<>());
                                // Before setting the coNode, we have to remove the
                                // coEdge of the current edge, and update the
                                // blockEdges and read events.
                                node.getCoNodes().put(pre, preNode);
                                node.getCoNodes().putAll(preNode.getCoNodes());
                                // We should the lastHandledEvent for the node.
                                node.updateLastHandledEvent(coEdge);
                                node.removeCoEdge(coEdge);
                                preNode.getCoNodes().put(pre, node);
                                preNode = node;
                            }
                        }

                        List<SharedEvent> sharedEvents =
                                extractor.extractSharedVarsInfo(edge);
                        if (sharedEvents != null && !sharedEvents.isEmpty()) {
                            handleEvents(sharedEvents, preNode);
                        }

                        if (hasAtomicBegin(locks, edge, sharedEvents)) {
                            // FIXME: Inside a block, we don's start a new block.
                        }

                        if (!hasAtomicEnd(locks, edge, sharedEvents)) {
                            withBlock.add(suc);
                        } else {
                            // FIXME At the end fo a block, we set lastHandledEvent to be
                            //  null?
                            preNode.setLastHandledEvent(null);
                        }

                        if (locks.containsKey(pre)) {
                            if (locks.containsKey(suc)) {
                                locks.get(suc).clear();
                                locks.get(suc).addAll(locks.get(pre));
                            } else {
                                locks.put(suc, new Stack<>());
                                locks.get(suc).addAll(locks.get(pre));
                            }
                        }

                        preNode.getBlockEdges().add(edge);
                        if (!ogNodes.containsKey(edge.hashCode())) {
                            ogNodes.put(edge.hashCode(), preNode);
                        }
                        Stack<OGNode> nodeStack =
                                blockNodeMap.computeIfAbsent(suc, cfaN -> new Stack<>());
                        nodeStack.push(preNode);
                    } else {
                        Preconditions.checkState(!ogNodes.containsKey(edge.hashCode()),
                                "Duplicated key for Edge: %s", edge.getRawStatement());
                        List<SharedEvent> sharedEvents =
                                extractor.extractSharedVarsInfo(edge);
                        OGNode newBlockNode;
                        if (hasAtomicBegin(locks, edge, sharedEvents)) {
                            // FIXME: an atomic block may have no global variables.
                            newBlockNode = new OGNode(edge,
                                    new ArrayList<>(List.of(edge)),
                                    false, /* Complicated Node */
                                    false,
                                    new HashSet<>(),
                                    new HashSet<>());
                            withBlock.add(suc);
                            if (!sharedEvents.isEmpty()) {
                                handleEvents(sharedEvents, newBlockNode);
                            }
                        } else {
                            // else, normal edge not in a block.
                            // If no shared events, just skip.
                            newBlockNode = new OGNode(edge,
                                    new ArrayList<>(List.of(edge)),
                                    true, /* Simple node */
                                    false,
                                    new HashSet<>(),
                                    new HashSet<>());
                            if (locks.containsKey(pre)) {
                                if (locks.containsKey(suc)) {
                                    locks.get(suc).clear();
                                    locks.get(suc).addAll(locks.get(pre));
                                } else {
                                    locks.put(suc, new Stack<>());
                                    locks.get(suc).addAll(locks.get(pre));
                                }
                            }
                            if (sharedEvents.isEmpty()) {
                                visitedEdges.add(edge.hashCode());
                                waitlist.add(suc);
                                continue;
                            }
                            handleEvents(sharedEvents, newBlockNode);
                        }
                        ogNodes.put(edge.hashCode(), newBlockNode);
                        Stack<OGNode> nodeStack =
                                blockNodeMap.computeIfAbsent(suc, cfaN -> new Stack<>());
                        nodeStack.push(newBlockNode);
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

    boolean hasAtomicBegin(Map<CFANode, Stack<Var>> locks,
                           CFAEdge edge,
                           List<SharedEvent> sharedEvents) {
        // Skip the declaration.
        if (edge instanceof CDeclarationEdge) {
            return false;
        }

        if (edge.getRawStatement().contains(VERIFIER_ATOMIC_BEGIN)) {
            return true;
        } else if (edge.getRawStatement().contains(THREAD_MUTEX_LOCK)) {
            Preconditions.checkArgument(sharedEvents.size() == 1,
                    "Exactly one lock variable is expected." + edge);
            Var curLock = sharedEvents.iterator().next().getVar();
            if (locks.containsKey(edge.getPredecessor())) {
                if (locks.containsKey(edge.getSuccessor())) {
                    locks.get(edge.getSuccessor()).clear();
                    locks.get(edge.getSuccessor()).addAll(locks.get(edge.getPredecessor()));
                    locks.get(edge.getPredecessor()).push(curLock);
                } else {
                    locks.put(edge.getSuccessor(), new Stack<>());
                    locks.get(edge.getSuccessor()).addAll(locks.get(edge.getPredecessor()));
                    locks.get(edge.getSuccessor()).push(curLock);
                }
            } else {
                // If the predecessor of the node has no lock, then so should the
                // successor.
                locks.put(edge.getSuccessor(), new Stack<>());
                locks.get(edge.getSuccessor()).push(curLock);
            }
            return true;
        };

        return false;
    }

    boolean hasAtomicEnd(Map<CFANode, Stack<Var>> locks,
                         CFAEdge edge,
                         List<SharedEvent> sharedEvents) {
        // Skip the declaration.
        if (edge instanceof CDeclarationEdge) {
            return false;
        }

        if (edge.getRawStatement().contains(VERIFIER_ATOMIC_END)) {
            return true;
        } else if (edge.getRawStatement().contains(THREAD_MUTEX_UNLOCK)) {
            // Else, edge unlock some lock, in this case we need to judge whether multi
            // locks are held. FIXME: if so, how can we end the atomic block?
            CFANode pre = edge.getPredecessor(), suc = edge.getSuccessor();
            Preconditions.checkArgument(locks.containsKey(pre)
                            && !locks.get(pre).isEmpty(),
                    "Try to unlock without locking.");
            Var curLock = null;
            if (sharedEvents.isEmpty()) {
                // Which means the lock variable is local.
                // TODO: Extract local lock variable.
            } else {
                // Which means the lock variable is global.
                Preconditions.checkArgument(sharedEvents.size() == 1,
                        "Exactly one lock variable is expected.");
                curLock = sharedEvents.iterator().next().getVar();
            }
            Preconditions.checkArgument(curLock != null,
                    "Get lock failed" + ": " + edge);
            if (curLock.equals(locks.get(pre).peek())) {
                if (locks.containsKey(suc)) {
                    locks.get(suc).clear();
                    locks.get(suc).addAll(locks.get(pre));
                    locks.get(suc).pop();
                } else {
                    locks.put(suc, new Stack<>());
                    locks.get(suc).addAll(locks.get(pre));
                    locks.get(suc).pop();
                }
            } else if (curLock.equals(locks.get(pre).get(0))) {
                // FIXME: If the curLock is equal to the first lock in locks, we clear all
                //  locks, Which maybe unsound.
                if (locks.containsKey(suc)) {
                    locks.get(suc).clear();
                } else {
                    locks.put(suc, new Stack<>());
                }
            }

            if (locks.get(suc).isEmpty()) {
                // When locks held by suc is empty, we think we reach an end of the lock
                // block.
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
