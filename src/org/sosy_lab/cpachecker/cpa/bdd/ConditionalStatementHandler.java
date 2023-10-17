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
     * Before setting read-from relation, we should handle the case that r locates in
     * an assume statement (e.g., x > 1). When setting r to read from w makes
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
        CFAEdge rEdge = rNode.getBlockStartEdge(), wEdge = w.getInEdge();

        if (!(rEdge instanceof AssumeEdge)) {
            return r;
        }

        SharedEvent cor = r;
        CFANode rLocation = rEdge.getSuccessor(), wLocation = wEdge.getSuccessor();
        // r locates in an assume edge.
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
                    } else {
                        throw new UnsupportedCodeException("Not handled edge", wEdge);
                    }
                }

            case FunctionReturnEdge:
                final String callerFunctionName = wEdge.getSuccessor().getFunctionName();
                final FunctionReturnEdge fnReturnEdge = (FunctionReturnEdge) wEdge;
                final FunctionSummaryEdge summaryEdge = fnReturnEdge.getSummaryEdge();
                final CFunctionCall summaryExpr = (CFunctionCall) summaryEdge.getExpression();

                final Partition partition = varClass.getPartitionForEdge(wEdge);
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

            default:
        }

        Preconditions.checkArgument(assignFormula != null, "Calculate formula for the " +
                "write event failed.");
        if (nrmgr.makeAnd(assumeEvaluated, assignFormula).isFalse()) {
            // Change the node.
            cor = G.changeAssumeNode(r);
        }
        return cor;
    }

    /** This function returns, if the variable is used in the Expression. */
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
