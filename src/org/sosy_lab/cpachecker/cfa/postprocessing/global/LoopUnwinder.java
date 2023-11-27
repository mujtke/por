package org.sosy_lab.cpachecker.cfa.postprocessing.global;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.MutableCFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.model.*;
import org.sosy_lab.cpachecker.cfa.model.c.*;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;

import java.util.*;

@Options(prefix = "cfa.loops")
public class LoopUnwinder {

    @Option(secure = true,
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
        final List<Loop> allLoops =
                new ArrayList<>(cfa.getLoopStructure().get().getAllLoops());
        // Map loop head to its loop.
        allLoops.forEach(l -> l.getLoopHeads().forEach(h -> loopHeads.put(h, l)));
        // Debug.
        List<Loop> alps = new ArrayList<>(allLoops);
        for (Loop loop : cfa.getLoopStructure().get().getAllLoops()) {
            if (!allLoops.contains(loop)) {
                continue;
            }
            // TODO: how to handle the multiple loop-heads?
            Preconditions.checkArgument(loop.getLoopHeads().size() == 1,
                    "More than one loop head.");
            CFANode loopHead = loop.getLoopHeads().iterator().next();
            handleLoop(allLoops, loopHeads, loop, loopHead);
        }
    }

    void handleLoop(final List<Loop> allLoops,
            Map<CFANode, Loop> loopHeads, Loop loop, CFANode loopHead) {
        // Debug.
        final boolean debug = true;
        if (!allLoops.contains(loop)) return;
        Map<CFANode, Integer> depths = new HashMap<>();
        depths.put(loopHead, 1);
        Set<CFAEdge> leavingEdges = loop.getOutgoingEdges(),
                // We should also record new leaving edges.
                newLeavingEdges = new HashSet<>(),
                // copies of the backward edges that connect to the loop start node.
                newLoopEdges = new HashSet<>(),
                // Record the edge return to the loop head if it exists.
                loopBackEdges = new HashSet<>(),
                newCreatedEdges = new HashSet<>();
        Set<CFANode> newCreatedNodes = new HashSet<>();
        Stack<CFANode> waitlist = new Stack<>(), newWaitlist = new Stack<>();
        waitlist.push(loopHead);
        while (!waitlist.isEmpty()) {
            CFANode curNode = waitlist.pop(),
                    newCurNode = newWaitlist.isEmpty() ? null : newWaitlist.pop();
            Preconditions.checkArgument(depths.containsKey(curNode),
                    "Missing depth for node: " + curNode);
            int curDepth = depths.get(curNode);
            if (debug) System.out.println("Depth = " + curDepth + ", Node: " + curNode +
                    " | newNode: " + newCurNode + ", entering edges: " + loop.getIncomingEdges().size());
            for (int i = 0; i < curNode.getNumLeavingEdges(); i++) {
                CFAEdge edge = curNode.getLeavingEdge(i);
                CFANode suc = edge.getSuccessor();
                if (curDepth == 1 && curDepth <= maxLoopDepth) {
                    if (leavingEdges.contains(edge)) {
                        // We will leave the loop without exploring
                        // other nodes further.
                    } else if (suc.isLoopStart()) {
                        if (loopHead.equals(suc)) {
                            // We reach the loop head again.
                            depths.put(suc, curDepth + 1);
                            loopBackEdges.add(edge);
                            waitlist.push(suc);
                            // Copy the edge and suc.
                            CFANode newSuc = new CFANode(suc.getFunction());
                            newCreatedNodes.add(newSuc);
                            newWaitlist.push(newSuc);
                            depths.put(newSuc, curDepth + 1);
                            CFAEdge newEdge = copyEdge(edge, curNode, newSuc);
                            newLoopEdges.add(newEdge);
                            newCreatedEdges.add(newEdge);
                        } else {
                            // We reach another loop start. Handle it recursively.
                            // TODO
                            Loop innerLoop = loopHeads.get(suc);
                            Preconditions.checkArgument(innerLoop.getLoopHeads().size() == 1,
                                    "More than one loop head.");
                            handleLoop(allLoops, loopHeads, innerLoop,
                                    innerLoop.getLoopHeads().iterator().next());
                            innerLoop.getOutgoingEdges().forEach(e -> {
                                waitlist.push(e.getPredecessor());
                                depths.put(e.getPredecessor(), curDepth);
                            });
                        }
                    } else {
                        // Other cases.
                        waitlist.push(suc);
                        depths.put(suc, curDepth);
                    }
                } else if (curDepth <= maxLoopDepth) {
                    // Depth > 1.
                    CFANode newSuc = new CFANode(suc.getFunction());
                    if (leavingEdges.contains(edge)) {
                        CFAEdge newEdge = null;
                        // FIXME: When reaching the loop bound, we set exit edge dummy
                        //  if it is an assume edge.
                        if (curDepth + 1 <= maxLoopDepth) {
                            newEdge = copyEdge(edge, newCurNode, suc);
                        } else {
                            if (edge instanceof AssumeEdge) {
                                newEdge = new BlankEdge("",
                                        FileLocation.DUMMY,
                                        newCurNode, suc, "");
                            } else {
                                newEdge = copyEdge(edge, newCurNode, suc);
                            }
                        }
                        newLeavingEdges.add(newEdge);
                    } else if (suc.isLoopStart()) {
                        if (loopHead.equals(suc)) {
                            // We reach the loop start again.
                            if (curDepth + 1 <= maxLoopDepth) {
                                // If curDepth + 1 > maxDepth, we finish the loop
                                // unwinding.
                                depths.put(suc, curDepth + 1);
                                depths.put(newSuc, curDepth + 1);
                                loopBackEdges.add(edge);
                                waitlist.push(suc);
                                // Copy the edge and suc.
                                newCreatedNodes.add(newSuc);
                                newWaitlist.push(newSuc);
                                CFAEdge newEdge = copyEdge(edge, newCurNode, newSuc);
                                newCreatedEdges.add(newEdge);
                                CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                            }
                        } else {
                            // Reach a new loop start, handle it recursively.
                            // TODO.
                            Loop innerLoop = loopHeads.get(suc);
                            Preconditions.checkArgument(innerLoop.getLoopHeads().size() == 1,
                                    "More than one loop head.");
                            CFAEdge newEdge = copyEdge(edge, newCurNode, suc);
                            CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                            handleLoop(allLoops, loopHeads, innerLoop,
                                    innerLoop.getLoopHeads().iterator().next());
                            innerLoop.getOutgoingEdges().forEach(e -> {
                                CFANode s = e.getSuccessor(), ns;
                                waitlist.push(s);
                                ns = new CFANode(s.getFunction());
                                newCreatedNodes.add(ns);
                                newWaitlist.push(ns);
                                depths.put(s, curDepth);
                                depths.put(ns, curDepth);
                            });
                        }
                    } else if (curDepth < maxLoopDepth) {
                        CFAEdge newEdge = copyEdge(edge, newCurNode, newSuc);
                        newCreatedNodes.add(newSuc);
                        CFACreationUtils.addEdgeUnconditionallyToCFA(newEdge);
                        waitlist.push(suc);
                        newWaitlist.push(newSuc);
                        depths.put(suc, curDepth);
                        depths.put(newSuc, curDepth);
                    }
                }
            }
        }

        // Post processes.
        newLeavingEdges.forEach(CFACreationUtils::addEdgeUnconditionallyToCFA);
        newLoopEdges.forEach(CFACreationUtils::addEdgeUnconditionallyToCFA);
        loopBackEdges.forEach(CFACreationUtils::removeEdgeFromNodes);
        newCreatedNodes.forEach(cfa::addNode);
        // FIXME: we can't add newCreatedEdges and newCreatedStates to loop, is this OK?

        // Before returning, remove the loop from allLoops because we have finished
        // handling it.
        allLoops.remove(loop);
    }

    private CFAEdge copyEdge(CFAEdge edge, CFANode pre, CFANode suc) {
        switch (edge.getEdgeType()) {
            case BlankEdge:
                BlankEdge blankEdge = (BlankEdge) edge;
                return new BlankEdge(blankEdge.getRawStatement(),
                        blankEdge.getFileLocation(),
                        pre,
                        suc,
                        blankEdge.getDescription());

            case StatementEdge:
                CStatementEdge statementEdge = (CStatementEdge) edge;
                return new CStatementEdge(statementEdge.getRawStatement(),
                        statementEdge.getStatement(),
                        statementEdge.getFileLocation(),
                        pre,
                        suc);

            case ReturnStatementEdge:
                CReturnStatementEdge returnStatementEdge = (CReturnStatementEdge) edge;
                return new CReturnStatementEdge(returnStatementEdge.getRawStatement(),
                        returnStatementEdge.getRawAST().get(),
                        returnStatementEdge.getFileLocation(),
                        pre,
                        (FunctionExitNode) suc);

            case DeclarationEdge:
                CDeclarationEdge declarationEdge = (CDeclarationEdge) edge;
                return new CDeclarationEdge(declarationEdge.getRawStatement(),
                        declarationEdge.getFileLocation(),
                        pre,
                        suc,
                        declarationEdge.getDeclaration());

            case FunctionCallEdge:
                CFunctionCallEdge functionCallEdge = (CFunctionCallEdge) edge;
                CFunctionSummaryEdge summaryEdge = functionCallEdge.getSummaryEdge(),
                        newSummaryEdge =
                                new CFunctionSummaryEdge(summaryEdge.getRawStatement(),
                                        summaryEdge.getFileLocation(),
                                        pre,
                                        suc,
                                        summaryEdge.getExpression(),
                                        summaryEdge.getFunctionEntry());
                return new CFunctionCallEdge(functionCallEdge.getRawStatement(),
                        functionCallEdge.getFileLocation(),
                        pre,
                        functionCallEdge.getSummaryEdge().getFunctionEntry(),
                        functionCallEdge.getSummaryEdge().getExpression(),
                        newSummaryEdge);

            case FunctionReturnEdge:
                CFunctionReturnEdge functionReturnEdge = (CFunctionReturnEdge) edge;
                CFunctionSummaryEdge functionSummaryEdge =
                        functionReturnEdge.getSummaryEdge(),
                        newFunctionSummaryEdge =
                                new CFunctionSummaryEdge(functionSummaryEdge.getRawStatement(),
                                        functionReturnEdge.getFileLocation(),
                                        pre,
                                        suc,
                                        functionSummaryEdge.getExpression(),
                                        functionSummaryEdge.getFunctionEntry());

                return new CFunctionReturnEdge(functionReturnEdge.getFileLocation(),
                        (FunctionExitNode) pre,
                        suc,
                        newFunctionSummaryEdge);

            case AssumeEdge:
                CAssumeEdge assumeEdge = (CAssumeEdge) edge;
                return new CAssumeEdge(assumeEdge.getRawStatement(),
                        assumeEdge.getFileLocation(),
                        pre,
                        suc,
                        assumeEdge.getExpression(),
                        assumeEdge.getTruthAssumption());

            default:
        }

        return null;
    }
}
