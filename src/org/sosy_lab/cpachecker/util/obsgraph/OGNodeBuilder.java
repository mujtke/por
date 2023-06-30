package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;

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
                        edge = pre.getLeavingSummaryEdge();
                        suc = edge.getSuccessor();
                    }

                    // debug
                    System.out.println(edge);

                    if (withBlock.contains(pre)) {
                        OGNode preNode = blockNodeMap.get(pre);
                        Preconditions.checkState(preNode != null, "Missing OGNode for " +
                                        "edge: %s", edge.getRawStatement());
                        if (!hasAtomicEnd(edge)) {
                            withBlock.add(suc);
                            handleEdge(edge, preNode);
                        }
                        preNode.getBlockEdges().add(edge);
                        ogNodes.put(edge.hashCode(), preNode);
                        blockNodeMap.put(suc, preNode);
                    } else {
                        Preconditions.checkState(!ogNodes.containsKey(edge.hashCode()),
                                "Duplicated key for Edge: %s", edge.getRawStatement());
                        if (hasAtomicBegin(edge)) {
                            withBlock.add(suc);
                            OGNode newBlockNode = new OGNode(edge,
                                    new ArrayList<CFAEdge>(List.of(edge)),
                                    false,
                                    false,
                                    new HashSet<SharedEvent>(),
                                    new HashSet<SharedEvent>());
                            ogNodes.put(edge.hashCode(), newBlockNode);
                            blockNodeMap.put(suc, newBlockNode);
                        } else {
                            // else, normal edge not in a block.
                            OGNode newNonBlockNode = new OGNode(edge,
                                    List.of(edge),
                                    true,
                                    false,
                                    new HashSet<SharedEvent>(),
                                    new HashSet<SharedEvent>());
                            handleEdge(edge, newNonBlockNode);
                            ogNodes.put(edge.hashCode(), newNonBlockNode);
                            blockNodeMap.put(suc, newNonBlockNode);
                        }
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

    private void handleEdge(CFAEdge edge, OGNode ogNode) {

        List<SharedEvent> sharedEvents = extractor.extractSharedVarsInfo(edge);
        
        if (sharedEvents == null || sharedEvents.isEmpty()) return;

        sharedEvents.forEach(e -> {
            switch (e.aType) {
                case READ:
                    // for the same read var, only the first read will be added.
                    Set<SharedEvent> sameR = ogNode.getRs()
                            .stream()
                            .filter(r -> {
                                return r.var.getName().equals(e.var.getName()); })
                            .collect(Collectors.toSet());
                    if (!sameR.isEmpty()) {
                        break;
                    } else {
                        ogNode.getRs().add(e);
                    }
                case WRITE:
                    // for the same write var, only the last write will be added.
                    Set<SharedEvent> sameW = ogNode.getWs()
                            .stream()
                            .filter(w -> {
                                return w.var.getName().equals(e.var.getName()); })
                            .collect(Collectors.toSet());
                    if (!sameW.isEmpty()) {
                        ogNode.getWs().removeAll(sameW);
                    }
                    ogNode.getWs().add(e);
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

    private void export(Map<Integer, OGNode> nodes) {
        Path dotPath = Paths.get("output/ogNodesTable.dot");
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
