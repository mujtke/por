package org.sosy_lab.cpachecker.cfa.postprocessing.global;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.MutableCFA;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;

import java.util.*;

@Options(prefix = "cfa.loops")
public class LoopUnwinder {

    @Option(secure=true,
            description = "Max depth for unwinding a loop.")
    private int maxLoopDepth = 5;

    private final MutableCFA cfa;

    public LoopUnwinder(final MutableCFA pCfa, final Configuration pConfig)
            throws InvalidConfigurationException {
        cfa = pCfa;
        pConfig.inject(this);
    }

    public void unwindLoop() {
        assert cfa.getLoopStructure().isPresent();
        Map<CFANode, Loop> loopHeads = new HashMap<>();
        List<Loop> allLoops = new ArrayList<>(cfa.getLoopStructure().get().getAllLoops());
        // Map loop head to its loop.
        allLoops.forEach(l -> l.getLoopHeads().forEach(h -> loopHeads.put(h, l)));
        for (Loop loop : cfa.getLoopStructure().get().getAllLoops()) {
            if (!allLoops.contains(loop)) {
                continue;
            }
            // TODO: how to handle the multiple loop-heads?
            Preconditions.checkArgument(loop.getLoopHeads().size() == 1,
                    "More than one loop head.");
            CFANode loopHead = loop.getLoopHeads().iterator().next();
            handleLoop(loopHeads, loop, loopHead);
        }
    }

    void handleLoop(Map<CFANode, Loop> loopHeads, Loop loop, CFANode loopHead) {
        int depth = 1;
        Set<CFAEdge> incomingEdges = loop.getIncomingEdges(),
                leavingEdges = loop.getOutgoingEdges(),
                newLeavingEdges = new HashSet<>(),
                loopBackEdges = new HashSet<>(),
                newCreatedEdges = new HashSet<>();
        Set<CFANode> newCreatedNodes = new HashSet<>();
        // Record the edge return to the loop head if it exists.
        Stack<CFANode> waitlist = new Stack<>(), newWaitlist = new Stack<>();
        waitlist.push(loopHead);
        while (depth <= maxLoopDepth && !waitlist.isEmpty()) {
            CFANode curNode = waitlist.pop();
            for (int i = 0; i < curNode.getNumLeavingEdges(); i++) {
                CFAEdge edge = curNode.getLeavingEdge(i);
                CFANode suc = edge.getSuccessor();
                if (depth == 1 && depth <= maxLoopDepth) {
                    if (leavingEdges.contains(edge)) {
                        // We will leave the loop without exploring
                        // other nodes further.
                    } else if (suc.isLoopStart()) {
                        if (loopHead.equals(suc)) {
                            // We reach the loop head again.
                            depth++;
                            loopBackEdges.add(edge);
                            waitlist.push(suc);
                            // Copy the edge and suc.
                            CFANode newSuc = new CFANode(suc.getFunction());
                            newCreatedNodes.add(newSuc);
                            newWaitlist.push(newSuc);
                            CFAEdge newEdge = copyEdge(edge, curNode, newSuc);
                            newCreatedEdges.add(newEdge);
                            CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                        } else {
                            // We reach another loop start. Handle it recursively.
                            // TODO
                            Loop innerLoop = loopHeads.get(suc);
                            Preconditions.checkArgument(innerLoop.getLoopHeads().size() == 1,
                                    "More than one loop head.");
                            handleLoop(loopHeads, innerLoop,
                                    innerLoop.getLoopHeads().iterator().next());
                            innerLoop.getOutgoingEdges().forEach(e -> waitlist.push(e.getPredecessor()));
                        }
                    } else {
                        // Other cases.
                        waitlist.push(suc);
                    }
                } else if (depth <= maxLoopDepth) {
                    // Depth > 1.
                    CFANode newCurNode = newWaitlist.pop(),
                            newSuc = new CFANode(suc.getFunction());
                    if (leavingEdges.contains(edge)) {
                        CFAEdge newEdge = copyEdge(edge, newCurNode, newSuc);
                        CFACreationUtils.addEdgeUnconditionallyToCFA(edge);
                    } else if (suc.isLoopStart()) {
                        if (loopHead.equals(suc)) {
                            depth++;
                            loopBackEdges.add(edge);
                            waitlist.push(suc);
                            // Copy the edge and suc.
                            newCreatedNodes.add(newSuc);
                            newWaitlist.push(newSuc);
                            CFAEdge newEdge = copyEdge(edge, newCurNode, newSuc);
                            newCreatedEdges.add(newEdge);
                            CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                        } else {
                            // Reach a new loop start, handle it recursively.
                            // TODO.
                            Loop innerLoop = loopHeads.get(suc);
                            Preconditions.checkArgument(innerLoop.getLoopHeads().size() == 1,
                                    "More than one loop head.");
                            CFAEdge newEdge = copyEdge(edge, newCurNode, newSuc);
                            CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                            handleLoop(loopHeads, innerLoop,
                                    innerLoop.getLoopHeads().iterator().next());
                            innerLoop.getOutgoingEdges().forEach(e -> {
                                CFANode s = e.getSuccessor(), ns;
                                waitlist.push(s);
                                ns = new CFANode(s.getFunction());
                                newWaitlist.push(ns);
                            });
                        }
                    } else {
                        CFAEdge newEdge = copyEdge(edge, newCurNode, newSuc);
                        CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                        waitlist.push(suc);
                        newWaitlist.push(newSuc);
                    }
                }
            }
        }
    }

    private CFAEdge copyEdge(CFAEdge edge, CFANode pre, CFANode suc) {
        switch (edge.getEdgeType()) {
            case BlankEdge:
                break;
            case StatementEdge:
                break;
            case ReturnStatementEdge:
                break;
            case DeclarationEdge:
                break;
            case FunctionCallEdge:
                break;
            case FunctionReturnEdge:
                break;
            case AssumeEdge:
                break;
            default:
        }

        return null;
    }
}
