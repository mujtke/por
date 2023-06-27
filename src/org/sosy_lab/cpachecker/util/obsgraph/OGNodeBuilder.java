package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

import java.util.*;
import java.util.stream.Collectors;

@Options(prefix="util.obsgraph")
public class OGNodeBuilder {

    Configuration Config;
    CFA cfa;

    private static final String THREAD_MUTEX_LOCK = "pthread_mutex_lock";
    private static final String THREAD_MUTEX_UNLOCK = "pthread_mutex_unlock";
    private static final String VERIFIER_ATOMIC_BEGIN = "__VERIFIER_atomic_begin";
    private static final String VERIFIER_ATOMIC_END = "__VERIFIER_atomic_end";

    private final SharedVarsExtractor extractor = new SharedVarsExtractor();
    public OGNodeBuilder(Configuration pConfig,
                         CFA pCfa) {

    };

    public Map<Integer, OGNode> build() {

        Map<Integer, OGNode> ogNodes = new HashMap<>();

        for (CFANode funcEntryNode : cfa.getAllFunctionHeads()) {
            Stack<CFANode> waitlist = new Stack<>();
            Set<CFANode> withBlock = new HashSet<>();
            waitlist.push(funcEntryNode);

            while (!waitlist.isEmpty()) {
                CFANode pre = waitlist.pop();
                for (int i = 0; i < pre.getNumLeavingEdges(); i++) {
                    CFAEdge edge = pre.getLeavingEdge(i);
                    CFANode suc = edge.getSuccessor();
                    if (hasAtomicBegin(edge)) {
                        withBlock.add(suc);
                        ogNodes.put(edge.hashCode(), new OGNode(edge,
                                List.of(edge),
                                false,
                                false,
                                Set.of(),
                                Set.of()));
                        continue;
                    }
                    if (withBlock.contains(pre)) {
                        OGNode preNode = ogNodes.get(pre.hashCode());
                        Preconditions.checkState(preNode != null, "Missing OGNode for " +
                                        "edge: %s", edge.getRawStatement());
                        handleEdge(edge, preNode);
                        ogNodes.put(edge.hashCode(), preNode);
                        if (!hasAtomicEnd(edge)) {
                            withBlock.add(suc);
                        }
                    }
                    // else, normal edge not in a block.
                    OGNode ogNode = new OGNode(edge,
                            List.of(edge),
                            true,
                            false,
                            Set.of(),
                            Set.of());
                    handleEdge(edge, ogNode);
                    Preconditions.checkState(ogNodes.containsKey(edge.hashCode()),
                            "Duplicated key for Edge: %s", edge.getRawStatement());
                    ogNodes.put(edge.hashCode(), ogNode);
                    if (!waitlist.contains(suc)) {
                        waitlist.add(suc);
                    }
                }
            }
        }

        return ogNodes;
    }

    private void handleEdge(CFAEdge edge, OGNode ogNode) {

        List<SharedEvent> sharedEvents = extractor.extractSharedVarsInfo(edge);

        sharedEvents.forEach(e -> {
            switch (e.aType) {
                case READ:
                    // for the same read var, only the first read will be added.
                    Set<SharedEvent> sameR = ogNode.Rs
                            .stream()
                            .filter(r -> {
                                return r.var.getName().equals(e.var.getName()); })
                            .collect(Collectors.toSet());
                    if (!sameR.isEmpty()) {
                        break;
                    } else {
                        ogNode.Rs.add(e);
                    }
                case WRITE:
                    // for the same write var, only the last write will be added.
                    Set<SharedEvent> sameW = ogNode.Ws
                            .stream()
                            .filter(w -> {
                                return w.var.getName().equals(e.var.getName()); })
                            .collect(Collectors.toSet());
                    if (!sameW.isEmpty()) {
                        ogNode.Ws.removeAll(sameW);
                    }
                    ogNode.Ws.add(e);
                default:
            }
        });
    }

    boolean hasAtomicBegin(CFAEdge edge) {
        return edge.getRawStatement().contains(THREAD_MUTEX_LOCK)
                || edge.getRawStatement().contains(VERIFIER_ATOMIC_BEGIN);
    }

    boolean hasAtomicEnd(CFAEdge edge) {
        return edge.getRawStatement().contains(THREAD_MUTEX_UNLOCK)
                || edge.getRawStatement().contains(VERIFIER_ATOMIC_END);
    }
}
