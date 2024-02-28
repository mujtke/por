package org.sosy_lab.cpachecker.cpa.bdd;

import com.google.common.base.Preconditions;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.cfa.model.*;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionManager;
import org.sosy_lab.cpachecker.util.variableclassification.Partition;
import org.sosy_lab.cpachecker.util.variableclassification.VariableClassification;

import java.util.List;

@Options(prefix = "cpa.bdd.csh")
public class ConditionalStatementHandler {

    private final VariableClassification varClass;
    private final BitvectorManager bvmgr;
    private final NamedRegionManager nrmgr;
    private final PredicateManager predmgr;
    private final BitvectorComputer bvComputer;

    @Option(
            secure = true,
            description = "use a smaller bitsize for all vars, that have only intEqual values"
    )
    private boolean compressIntEqual = true;

    public ConditionalStatementHandler(Configuration pConfig, CFA pCfa, LogManager pLogger)
            throws InvalidConfigurationException {
        this.varClass = pCfa.getVarClassification().orElseThrow();
        RegionManager rmgr =
                new BDDManagerFactory(pConfig, pLogger).createRegionManager();
        this.nrmgr = new NamedRegionManager(rmgr);
        this.bvmgr = new BitvectorManager(rmgr);
        this.predmgr = new PredicateManager(pConfig, nrmgr, pCfa);
        this.bvComputer = new BitvectorComputer(
                compressIntEqual,
                varClass,
                bvmgr,
                nrmgr,
                predmgr,
                pCfa.getMachineModel());
    }

    /**
     * FIXME
     * Before setting read-from relation, we should handle the case that r locates in
     * an assume statement (e.g., x > 1). When setting r to read from w that makes
     * the condition not hold, i.e., (x > 1) not hold, we replace r with the event
     * co-r ('co' means conjugate) that comes from the assume statement [!(x > 1)].
     * @return r if r is not in an assume edge or making r read from w doesn't affect
     * the truth of the assume statement, or co-r else.
     */
    public SharedEvent handleAssumeStatement(
            ObsGraph G,
            SharedEvent r,
            SharedEvent w) throws UnsupportedCodeException {

        OGNode rNode = r.getInNode(), wNode = w.getInNode();
        if (G.contain(rNode, rNode.getLoopDepth()) < 0) {
            // We are going to set read-from relation for the newly added node, so we
            // don't have to change the node.
            return r;
        }
        CFAEdge rEdge = r.getInEdge(), wEdge = w.getInEdge();

        if (!(rEdge instanceof AssumeEdge)) {
            return r;
        }

        SharedEvent cor = r;
        CFANode rLocation = rEdge.getSuccessor(), wLocation = wEdge.getSuccessor();
        // r locates in an assumption edge.
        final AssumeEdge assumption = (AssumeEdge) rEdge;
        Partition rPartition = varClass.getPartitionForEdge(rEdge);
        CExpression rExpression = (CExpression) assumption.getExpression();
        // FIXME: do we need precision here?
        final Region[] assumeOperand = bvComputer.evaluateVectorExpression(rPartition,
                rExpression, CNumericTypes.INT, rLocation, null);
        Preconditions.checkArgument(assumeOperand != null, "Assumption cannot be " +
                "evaluated.");
        Region assumeEvaluated = bvmgr.makeOr(assumeOperand), assignFormula = null;

        if (!assumption.getTruthAssumption()) { // If false-branch.
            assumeEvaluated = nrmgr.makeNot(assumeEvaluated);
        }

        switch (wEdge.getEdgeType()) {
            case StatementEdge:
                final AStatementEdge statementEdge = (AStatementEdge) wEdge;
                CStatement statement = (CStatement) statementEdge.getStatement();
                if (statement instanceof CAssignment) {
                    CAssignment assignment = (CAssignment) statement;
                    CExpression lhs = assignment.getLeftHandSide();
                    final String varName;
                    if (lhs instanceof CIdExpression) {
                        varName = ((CIdExpression) lhs).getName();
                        Preconditions.checkArgument(w.getVar().getName().equals(varName),
                                "Wrong cfaEdge '" + wEdge + "' for event in " + wNode);
                    } else {
                        throw new UnsupportedOperationException("Lhs" + lhs + " is not " +
                                "a CIdExpression.");
                    }

                    final CType targetType = lhs.getExpressionType();

                    CRightHandSide rhs = assignment.getRightHandSide();
                    if (rhs instanceof CExpression) {
                        final CExpression exp = (CExpression) rhs;
                        final Partition partition = varClass.getPartitionForEdge(wEdge);

                        if (isUsedInExpression(varName, exp)) {
                            // make tmp for assignment, this is done to handle assignments
                            // like "a = !a;" as "tmp = !a; a = tmp;"
                            String tmpVarName = predmgr.getTmpVariableForPartition(partition);
                            final Region[] tmp = predmgr.createPredicateWithoutPrecisionCheck(
                                    tmpVarName, bvComputer.getBitsize(partition, targetType));
                            // make region for RIGHT SIDE and build equality of var and region
                            // FIXME: do we need precision here?
                            final Region[] regionRHS =
                                    bvComputer.evaluateVectorExpression(partition, exp,
                                            targetType, wLocation, null);
                            // Delete var, make tmp equal to (new) var, then delete tmp.
                            final Region[] var = predmgr.createPredicate(scopeVar(lhs),
                                    targetType, wLocation,
                                    bvComputer.getBitsize(partition, targetType),
                                    null);
                            assignFormula = nrmgr.makeExists(nrmgr.makeTrue(), var);
                            assignFormula = assignment(var, tmp, false);
                            assignFormula = nrmgr.makeExists(assignFormula, tmp);

                            break;
                        } else {
                            final Region[] var = predmgr.createPredicate(scopeVar(lhs),
                                    targetType, wLocation,
                                    bvComputer.getBitsize(partition, targetType),
                                    null);
                            final Region[] regionRHS =
                                    bvComputer.evaluateVectorExpression(partition,
                                            (CExpression) rhs, targetType, wLocation,
                                            null);
                            assignFormula = nrmgr.makeExists(nrmgr.makeTrue(), var);
                            assignFormula = assignment(var, regionRHS, false);

                            break;
                        }
                    } else if (rhs instanceof CFunctionCallExpression) {
                        // handle params of functionCall, maybe there is a side effect.
                        // 1) y = f(x), f write x, like scanf("%d", &x). In this case,
                        // we need to handle the parameters of the function f. Even f
                        // may write x more than once, but in all cases, the result is that
                        // x can take any value. We just preserve the last result?
                        // 2) x = f(), assign the return value to x. The result is that
                        // x can take any value.
                        // 3) x = f(x), f write x (may more than once) and finally we
                        // assign the return value to x. In this case, we preserve the
                        // result of the assignment?
                        // FIXME: f may write x, or y and x are same var.
                        CFunctionCallExpression funCallExpr =
                                (CFunctionCallExpression) rhs;
                        final List<CExpression> params =
                                funCallExpr.getParameterExpressions();
                        final Partition partition = varClass.getPartitionForEdge(wEdge);
                        // A flag indicates that we find the write event.
                        boolean findAssignment = false;
                        for (final CExpression param : params) {
                            /* special case: external functioncall with possible side-effect!
                             * this is the only statement, where a pointer-operation is allowed
                             * and the var can be boolean, intEqual or intAdd,
                             * because we know, the variable can have a random (unknown) value after the functioncall.
                             * example: "scanf("%d", &input);" */
                            CExpression unpackedParam = param;
                            while (unpackedParam instanceof CCastExpression) {
                                unpackedParam = ((CCastExpression) param).getOperand();
                            }
                            if (unpackedParam instanceof CUnaryExpression) {
                                CUnaryExpression unaryExpression =
                                        ((CUnaryExpression) unpackedParam);
                                // AMPER = '&'.
                                if (CUnaryExpression.UnaryOperator.AMPER == unaryExpression.getOperator()
                                && unaryExpression.getOperand() instanceof CIdExpression) {
                                    final CIdExpression id =
                                            (CIdExpression) unaryExpression.getOperand();
                                    if (!id.getName().equals(r.getVar().getName())) {
                                        continue;
                                    }
                                    // FIXME: How to handle multi-times writes to the
                                    //  same var?
                                    findAssignment = true;
                                    final Region[] var = predmgr.createPredicate(
                                            scopeVar(id),
                                            id.getExpressionType(),
                                            wEdge.getSuccessor(),
                                            bvComputer.getBitsize(partition, targetType),
                                            null); // is default bitsize enough?
                                    assignFormula = nrmgr.makeExists(nrmgr.makeTrue(),
                                            var);
                                }
                            } else {
                                // "printf("%d", output);" or "assert(exp);"
                                // TODO: can we do something here?
                            }
                        }

                        if (varName.equals(r.getVar().getName())) {
                            findAssignment = true;
                            final Region[] var = predmgr.createPredicate(
                                    scopeVar(lhs),
                                    targetType,
                                    wEdge.getSuccessor(),
                                    bvComputer.getBitsize(partition, targetType),
                                    null);
                            assignFormula = nrmgr.makeExists(nrmgr.makeTrue(), var);
                        }
                        Preconditions.checkState(findAssignment,
                                "Doesn't find the write event to"
                                        + r.getVar().getName() + " in edge" + wEdge);
                    } else {
                        throw new UnsupportedCodeException("Not handled edge", wEdge);
                    }
                }
                break;

            case FunctionReturnEdge:
                final String callerFunctionName = wEdge.getSuccessor().getFunctionName();
                final FunctionReturnEdge fnReturnEdge = (FunctionReturnEdge) wEdge;
                final FunctionSummaryEdge summaryEdge = fnReturnEdge.getSummaryEdge();
                final CFunctionCall summaryExpr = (CFunctionCall) summaryEdge.getExpression();

                Partition partition = varClass.getPartitionForEdge(wEdge);
                // Handle assignments like "y = f(x);"
                if (summaryExpr instanceof CFunctionCallAssignmentStatement) {
                    Preconditions.checkArgument(
                            summaryEdge.getFunctionEntry().getReturnVariable().isPresent());
                    final String returnVar =
                            summaryEdge.getFunctionEntry().getReturnVariable().get().getQualifiedName();
                    CFunctionCallAssignmentStatement cAssignment =
                            (CFunctionCallAssignmentStatement) summaryExpr;
                    CExpression lhs = cAssignment.getLeftHandSide();
                    final int size = bvComputer.getBitsize(partition, lhs.getExpressionType());

                    // make variable (predicate) for LEFT SIDE of assignment,
                    // delete variable, if it was used before, this is done with an existential operator
                    final Region[] var = predmgr.createPredicate(scopeVar(lhs),
                            lhs.getExpressionType(), wLocation, size, null),
                            retVar = predmgr.createPredicate(returnVar,
                                    summaryExpr.getFunctionCallExpression().getExpressionType(),
                                    wLocation, size, null);
                    assignFormula = assignment(var, retVar, false);

                    // Remove returnVar?
                    if (predmgr.getTrackedVars().contains(returnVar)) {
                        nrmgr.makeExists(assignFormula,
                                predmgr.createPredicateWithoutPrecisionCheck(returnVar));
                    }

                    break;
                } else {
                    // No assignment, nothing to do.
                    assert summaryExpr instanceof CFunctionCallStatement;
                }
                break;

            case DeclarationEdge:
                CDeclarationEdge declarationEdge = (CDeclarationEdge) wEdge;
                CDeclaration declaration = declarationEdge.getDeclaration();
                if (declaration instanceof CVariableDeclaration) {
                    CVariableDeclaration vDecl= (CVariableDeclaration) declaration;
                    if (vDecl.getType().isIncomplete()) {
                        // Variables of such types cannot store values, only their address
                        // can be taken.
                        // FIXME: how to handle this case.
                        throw new UnsupportedCodeException("Declared variable in edge " +
                                "is incomplete.", wEdge);
                    }

                    CInitializer initializer = vDecl.getInitializer();
                    CExpression init = null;
                    if (initializer instanceof CInitializerExpression) {
                        init = ((CInitializerExpression) initializer).getExpression();
                    }

                    // make variable (predicate) for LEFT SIDE of declaration,
                    // delete variable, if it was initialized before i.e.
                    // in another block, with an existential operator
                    partition = varClass.getPartitionForEdge(wEdge);
                    Region[] var = predmgr.createPredicate(
                            vDecl.getQualifiedName(),
                            vDecl.getType(),
                            wEdge.getSuccessor(),
                            bvComputer.getBitsize(partition, vDecl.getType()),
                            null);
//                    assignFormula = nrmgr.makeExists(nrmgr.makeTrue(), var);

                    // initializer on the RIGHT SIDE available, make a region for it.
                    if (init != null) {
                        final Region[] rhs = bvComputer.evaluateVectorExpression(
                                partition,
                                init,
                                vDecl.getType(),
                                wEdge.getSuccessor(),
                                null);
                        assignFormula = assignment(var, rhs, false);
                    } else {
                        throw new UnsupportedCodeException("Initializer on the right " +
                                "side should not be null", wEdge);
                    }
                } else {
                    throw new UnsupportedCodeException("W event in a " +
                            "non-CVariableDeclaration edge is not supported.", wEdge);
                }
                break;

            default:
        }

        Preconditions.checkArgument(assignFormula != null,
                "Calculate formula for the write event failed.");
        if (nrmgr.makeAnd(assumeEvaluated, assignFormula).isFalse()) {
            // Change the node.
            cor = G.changeAssumeNode(r);
        }
        return cor;
    }

    /** This function returns true if the variable is used in the Expression. */
    private static boolean isUsedInExpression(String varName, CExpression exp) {
        return exp.accept(new VarCExpressionVisitor(varName));
    }

    private String scopeVar(final CExpression exp) {
        if (exp instanceof CIdExpression) {
            return ((CIdExpression) exp).getDeclaration().getQualifiedName();
        } else {
//            return functionName + "::" + exp.toASTString();
            throw new UnsupportedOperationException("exp " + exp + " is not a " +
                    "CIdExpression.");
        }
    }

    /**
     * Ref {@code @BDDState#addAssignment}
     */
    private Region assignment(Region[] leftSide, Region[] rightSide,
                              boolean addIncreasing) {
        Preconditions.checkArgument(leftSide != null && rightSide != null);
        Preconditions.checkArgument(leftSide.length == rightSide.length,
                "left side and right side should have equal length: "
                + leftSide.length + " != " + rightSide.length);
        final Region[] assignRegions = bvmgr.makeBinaryEqual(leftSide, rightSide);

        Region result;

        if (addIncreasing) {
            result = assignRegions[0];
            for (int i = 1; i < assignRegions.length; i++) {
                result = nrmgr.makeAnd(result, assignRegions[i]);
            }
        } else {
            result = assignRegions[assignRegions.length - 1];
            for (int i = assignRegions.length - 2; i >= 0; i--) {
                result = nrmgr.makeAnd(result, assignRegions[i]);
            }
        }

        return result;
    }
}
