package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import org.sosy_lab.cpachecker.cfa.ast.*;
import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.cfa.model.*;
import org.sosy_lab.cpachecker.cfa.model.c.*;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;
import static com.google.common.collect.FluentIterable.from;

public class SharedVarsExtractor {

    private static final String nonDetFunction = "__VERIFIER_nondet_";
    // The pair of empty sets w.r.t. read and wrote vars.
    private static Pair<Set<Var>, Set<Var>> emptyRWVarsSet = Pair.of(Set.of(), Set.of());
    // The cache to store the astNode and its corresponding R & W vars.
    // Use IdentityHashMap. Why?
    private final static Map<AAstNode, Pair<Set<Var>, Set<Var>>> astCache = new IdentityHashMap<>();

    // Function to filter out the local variables.
    private static Function<Set<Var>, Set<Var>> sharedVarsFilter =
            set0 -> from(set0).filter(var -> var.isGlobal()).toSet();

    private static ExpressionVisitor visitor = new ExpressionVisitor();

    public SharedVarsExtractor() {

    }

    public List<SharedEvent> extractSharedVarsInfo(final CFAEdge cfaEdge) {
        switch (cfaEdge.getEdgeType()) {
            case BlankEdge:
                return List.of();

            case AssumeEdge:
                final AssumeEdge assumeEdge = (AssumeEdge) cfaEdge;
                return handleAssumeEdge(assumeEdge, assumeEdge.getExpression());

            case StatementEdge:
                final AStatementEdge stmtEdge = (AStatementEdge) cfaEdge;
                return handleStatementEdge(stmtEdge, stmtEdge.getStatement());

            case DeclarationEdge:
                final ADeclarationEdge declEdge = (ADeclarationEdge) cfaEdge;
                return handleDeclarationEdge(declEdge, declEdge.getDeclaration());

            case ReturnStatementEdge:
                final AReturnStatementEdge retEdge = (AReturnStatementEdge) cfaEdge;
                return handleReturnStatementEdge(retEdge);

            case FunctionCallEdge:
                final FunctionCallEdge funCallEdge = (FunctionCallEdge) cfaEdge;
                return handleFunctionCallEdge(funCallEdge);

            case FunctionReturnEdge:
                final FunctionReturnEdge funRetEdge = (FunctionReturnEdge) cfaEdge;
                return handleFunctionReturnEdge(funRetEdge);

            default:
                return null;
        }
    }

    /** Statement level */
    private <T extends AAstNode> Pair<Set<Var>, Set<Var>> extract0(final T astNode) {
        if (astNode == null) {
            return emptyRWVarsSet;
        }
        // First, look up in the astCache.
        if (astCache.containsKey(astNode)) {
            return astCache.get(astNode);
        }
        // Found nothing, then extract the info.
        final Pair<Set<Var>, Set<Var>> varsInAstNode = extract(astNode);

        return varsInAstNode;
    }

    private <T extends AAstNode> Pair<Set<Var>, Set<Var>> extract(final T astNode) {

        if (astNode instanceof CRightHandSide) {
            // CRightHandSide has two cases: 1) CExpression. 2) CFunctionCallExpression
            if (astNode instanceof CExpression) {
                // Note: the variables in CRightHandSide are read by default.
                return Pair.of(((CExpression) astNode).accept(visitor), Set.of());
            } else if (astNode instanceof CFunctionCallExpression) {
                return extractFunctionCall(astNode);
            }

        } else if (astNode instanceof CStatement) {
            // CStatement has four cases: 1) CFunctionCallStatement.
            // 2) CFunctionCallAssignmentStatement. 3) CExpressionStatement.
            // 4) CExpressionAssignmentStatement
            if (astNode instanceof CFunctionCallStatement) {
                return extractFunctionCall(astNode);
            } else if (astNode instanceof CFunctionCallAssignmentStatement) {
                return extractFunctionCall(astNode);
            } else if (astNode instanceof CExpressionStatement) {
                // We only process the function and assignment statements.
                return emptyRWVarsSet;
            } else if (astNode instanceof CExpressionAssignmentStatement) {
                CExpressionAssignmentStatement expAssignmentStmt =
                        (CExpressionAssignmentStatement) astNode;
                // TODO: how to handle with CExpressionAssignmentStatements.
                return Pair.of(extract0(expAssignmentStmt.getRightHandSide()).getFirst(),
                        extract0(expAssignmentStmt.getLeftHandSide()).getFirst());
            }
        } else if (astNode instanceof CDesignator) {
            // CDesignator has three cases: 1) CArrayDesignator. 2) CFieldDesignator. 3)
            // CArrayRangeDesignator.
            if (astNode instanceof CArrayDesignator) {
                // int a[10] = { [x + 1] = 2 }; => x + 1 (TODO: only constant allowed?)
                return extract0(((CArrayDesignator) astNode).getSubscriptExpression());
            } else if (astNode instanceof CFieldDesignator) {
                // struct Point { int x; int y;}; struct Point p = { .x = 1, .y = 0; } => x, y
                return emptyRWVarsSet;
            } else if (astNode instanceof CArrayRangeDesignator) {
                // int a[10] = { [3 ... x] = 2 } => 3 ... x
                Pair<Set<Var>, Set<Var>> floorExpVars =
                        extract0(((CArrayRangeDesignator) astNode).getFloorExpression());
                Pair<Set<Var>, Set<Var>> ceilExpVars =
                        extract0(((CArrayRangeDesignator) astNode).getCeilExpression());
                return Pair.of(Sets.union(floorExpVars.getFirst(), ceilExpVars.getFirst()),
                        Set.of());
            }

        } else if (astNode instanceof CInitializer) {
            // CInitializer has three cases: 1) CInitializerList. 2) CInitializerExpression. 3)
            // CDesignatedInitializer.
            if (astNode instanceof CInitializerList) {
                return extractMany(((CInitializerList) astNode).getInitializers());
            } else if (astNode instanceof CInitializerExpression) {
                return extract0(((CInitializerExpression) astNode).getExpression());
            } else if (astNode instanceof CDesignatedInitializer) {
                // TODO: how to deal with CDesignator?
                return extract0(((CDesignatedInitializer) astNode).getRightHandSide());
            }
        } else if (astNode instanceof CSimpleDeclaration) {
            // CSimpleDeclaration has more than one case, but we just consider:
            // 1) CVariableDeclaration.
            if (astNode instanceof CVariableDeclaration) {
                CVariableDeclaration decl = (CVariableDeclaration) astNode;
                CInitializer declInitializer = decl.getInitializer();
                if (declInitializer != null) {
                    // var initialized.
                    CIdExpression varExp = new CIdExpression(decl.getFileLocation(),
                            decl.getType(), decl.getName(), decl);
                    Set<Var> declaredVars = varExp.accept(visitor);
                    Pair<Set<Var>, Set<Var>> varsInInit= extract0(declInitializer);

                    return Pair.of(varsInInit.getFirst(), declaredVars);
                } else {
                    return emptyRWVarsSet;
                }
            } else {
                return emptyRWVarsSet;
            }
        } else if (astNode instanceof CReturnStatement) {
            // CReturnStatement just has one case.
            Optional<CAssignment> retAssignment = ((CReturnStatement) astNode).asAssignment();
            if (retAssignment.isPresent()) {
                return extract0(retAssignment.get());
            }
            return emptyRWVarsSet;
        }

        // Type of the astNode is not included in the above, then throw an error.
        throw new AssertionError("Unhandled ASTNode " + astNode + " of " + astNode.getClass());
    }

    private List<SharedEvent> handleAssumeEdge(AssumeEdge assumeEdge, AExpression assumeExpr) {

        // AssumeEdge should only contain the read access of global vars.
        Set<Var> gRVars = sharedVarsFilter.apply(extract0(assumeExpr).getFirst());
        if (gRVars.isEmpty()) {
            return List.of();
        }
        List<SharedEvent> results = from(gRVars)
                .transform(var -> new SharedEvent(var, SharedEvent.AccessType.READ))
                .toList();

        return results;
    }

    private List<SharedEvent> handleStatementEdge(AStatementEdge stmtEdge, AStatement stmt) {

        if (stmtEdge instanceof CStatementEdge) {
            // We don't consider the summary edge.
            if (stmtEdge instanceof CFunctionSummaryStatementEdge) {
                return List.of();
            }

            Pair<Set<Var>, Set<Var>> vars = extract0(stmt);
            Set<Var> gRVars = sharedVarsFilter.apply(vars.getFirst()),
                    gWVars = sharedVarsFilter.apply(vars.getSecond());

            List<SharedEvent> results = new ArrayList<>();
            gRVars.forEach(var -> {
                results.add(new SharedEvent(var, SharedEvent.AccessType.READ));
            });
            gWVars.forEach(var -> {
                results.add(new SharedEvent(var, SharedEvent.AccessType.WRITE));
            });

            return results;
        }

        throw new AssertionError("this method is not implemented in subclass" + stmtEdge.getClass().getSimpleName());
    }

    private List<SharedEvent> handleDeclarationEdge(ADeclarationEdge declEdge,
                                                    ADeclaration declaration) {
        if (declaration instanceof CDeclaration) {
            Pair<Set<Var>, Set<Var>> vars = extract0(declaration);
            Set<Var> gRVars = sharedVarsFilter.apply(vars.getFirst()),
                    gWVars = sharedVarsFilter.apply(vars.getSecond());
            List<SharedEvent> results = new ArrayList<>();
            gRVars.forEach(var -> {
                results.add(new SharedEvent(var, SharedEvent.AccessType.READ));
            });
            gWVars.forEach(var -> {
                results.add(new SharedEvent(var, SharedEvent.AccessType.WRITE));
            });

            return results;
        }

        throw new AssertionError("this method is not implemented in subclass" + declEdge.getClass().getSimpleName());
    }

    private List<SharedEvent> handleReturnStatementEdge(AReturnStatementEdge retEdge) {
        if (retEdge instanceof CReturnStatementEdge) {
            Optional<CAssignment> retAssignment = ((CReturnStatementEdge) retEdge).asAssignment();

            if (retAssignment.isPresent()) {
                // Note: the lhs variable is a built-in local variable (e.g., func::__retval__).
                Set<Var> gRVars = sharedVarsFilter.apply(extract0(retAssignment.get()).getFirst());
                List<SharedEvent> results = from(gRVars)
                        .transform(var -> new SharedEvent(var, SharedEvent.AccessType.READ))
                        .toList();

                return results;
            }

            return List.of();
        }

        throw new AssertionError("this method is not implemented in subclass" + retEdge.getClass().getSimpleName());
    }

    private List<SharedEvent> handleFunctionCallEdge(FunctionCallEdge funCallEdge) {

        if (funCallEdge instanceof CFunctionCallEdge) {
            List<CExpression> arguments = ((CFunctionCallEdge) funCallEdge).getArguments();
            Set<Var> gRVars = sharedVarsFilter.apply(extractMany(arguments).getFirst());

            // TODO: how to handle the fun calls like '__VERIFIER_atomic_xxx' and 'pthread_mutex_xxx'
            List<SharedEvent> results = new ArrayList<>();
            gRVars.forEach(var -> results.add(new SharedEvent(var, SharedEvent.AccessType.READ)));

            return results;
        }

        throw new AssertionError("this method is not implemented in subclass" + funCallEdge.getClass().getSimpleName());
    }

    private List<SharedEvent> handleFunctionReturnEdge(FunctionReturnEdge funRetEdge) {

        if (funRetEdge instanceof CFunctionReturnEdge) {
            FunctionSummaryEdge funSummaryEdge =
                    ((CFunctionReturnEdge) funRetEdge).getSummaryEdge();
            AFunctionCall funSummaryExpr = funSummaryEdge.getExpression();
            if (funSummaryExpr instanceof CFunctionCallAssignmentStatement) {
                CLeftHandSide lshExpr =
                        ((CFunctionCallAssignmentStatement) funSummaryExpr).getLeftHandSide();
                // Should be the w vars?
                Set<Var> gWVars = sharedVarsFilter.apply(extract0(lshExpr).getSecond());
                List<SharedEvent> results = from(gWVars)
                        .transform(var -> new SharedEvent(var, SharedEvent.AccessType.WRITE))
                        .toList();

                return results;
            }
        }

        throw new AssertionError("this method is not implemented in subclass" + funRetEdge.getClass().getSimpleName());
    }

    private Pair<Set<Var>, Set<Var>> extractFunctionCall(final AAstNode funCall) {
        // The 'funCall' should be one type of 'CFunctionCallAssignmentStatement',
        // 'CFunctionCallStatement' or 'CFunctionCallExpression'.
        assert (funCall != null) && ((funCall instanceof CFunctionCallAssignmentStatement)
        || (funCall instanceof CFunctionCallStatement)
        || (funCall instanceof CFunctionCallExpression));

        if (funCall instanceof CFunctionCallAssignmentStatement) {
            // Note: only the parameters need to be processed (they are read vars).
            // NOTICE THAT:
            // 1) For the case: x = fun(a, y);
            //    when the function called, the 'assignment' action is not performed immediately,
            //    instead it is done when the function returns (i.e., the 'assignment' action is
            //    processed in the handling of CReturnStatement).
            // 2) For the case: x = __VERIFIER_nondet_int();
            //    the 'assignment' action is performed immediately once the func
            //    '__VERIFIER__nondet_int()' called.
            String funName =
                    ((CFunctionCallAssignmentStatement) funCall).getFunctionCallExpression()
                            .getFunctionNameExpression().toString();
            if (funName.startsWith(nonDetFunction)) {
                // TODO: how to deal with 'VERIFIER_nondet_int()'.
                Pair<Set<Var>, Set<Var>> lhsVars =
                        extract0(((CFunctionCallAssignmentStatement) funCall).getLeftHandSide()),
                        rhsVars =
                                extract0(((CFunctionCallAssignmentStatement) funCall).getRightHandSide());
                // all vars from RHS are read.
                return Pair.of(Sets.union(rhsVars.getFirst(), rhsVars.getSecond()),
                        lhsVars.getFirst());
            } else {
                // Just need to process the RHS (or the CFunctionCallExpression?).
                return extractFunctionCall(((CFunctionCallAssignmentStatement) funCall).getRightHandSide());
                // return extractFunctionCall(((CFunctionCallAssignmentStatement) funCall).getFunctionCallExpression());
            }
        } else if (funCall instanceof CFunctionCallStatement) {
            return extractFunctionCall(((CFunctionCallStatement) funCall).getFunctionCallExpression());
        } else if (funCall instanceof CFunctionCallExpression) {
            // Note: only the parameters are need to be processed (they are read vars).
            List<CExpression> funParas =
                    ((CFunctionCallExpression) funCall).getParameterExpressions();
            if (funParas != null) {
                return extractMany(funParas);
            }
            return emptyRWVarsSet;
        }

        throw new AssertionError("Unhandled ASTNode " + funCall + " of " + funCall.getClass());
    }

    private <T extends AAstNode> Pair<Set<Var>, Set<Var>> extractMany(final List<T> astNodeList) {
        Pair<Set<Var>, Set<Var>> res = Pair.of(Set.of(), Set.of()), tmp;
        for (T astNode : astNodeList) {
            tmp = extract0(astNode);
//            res.getFirst().addAll(tmp.getFirst());
//            res.getSecond().addAll(tmp.getSecond());
            res = Pair.of(Sets.union(res.getFirst(), tmp.getFirst()), Sets.union(res.getSecond(),
                    tmp.getSecond()));
        }

        return res;
    }
}
