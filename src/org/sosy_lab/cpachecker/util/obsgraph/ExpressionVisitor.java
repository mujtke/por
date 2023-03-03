package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.collect.Sets;
import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.exceptions.NoException;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.Set;

public class ExpressionVisitor extends DefaultCExpressionVisitor<Set<Var>, NoException> {
    @Override
    protected Set<Var> visitDefault(CExpression exp) throws NoException {
        return Set.of();
    }

    @Override
    public Set<Var> visit(CArraySubscriptExpression e) throws NoException {
        // Process array name.
        Set<Var> arrVars = e.getArrayExpression().accept(this);
        Set<Var> arrName = Set.of(replace(arrVars.iterator().next(), e));
        // Process the subscript.
        Set<Var> subscriptVars = e.getSubscriptExpression().accept(this);

        return Sets.union(arrName, subscriptVars);
    }

    @Override
    public Set<Var> visit(CBinaryExpression e) throws NoException {
        return Sets.union(e.getOperand1().accept(this), e.getOperand2().accept(this));
    }

    @Override
    public Set<Var> visit(CCastExpression e) throws NoException {
        return e.getOperand().accept(this);
    }

    @Override
    public Set<Var> visit(CComplexCastExpression e) throws NoException {
        return e.getOperand().accept(this);
    }

    @Override
    public Set<Var> visit(CFieldReference e) throws NoException {
        Set<Var> vars = e.getFieldOwner().accept(this);
        // Like using the conservative computation.
        return vars.isEmpty() ? Set.of() : Set.of(replace(vars.iterator().next(), e));
    }

    private Var replace(Var var, CExpression exp) {
        assert var != null && exp != null;
        return new Var(var.getName(), exp, var.getVarType(), var.isGlobal());
    }

    /**
     * just process {@link CIdExpression} whose declaration is {@link CVariableDeclaration} or
     * {@link CParameterDeclaration}, because other {@link CDeclaration} types have nothing to
     * do with the global or local variables.
     * @param e
     * @return the set of vars in CidExpression e.
     * @throws NoException
     */
    @Override
    public Set<Var> visit(CIdExpression e) throws NoException {
        // get the declaration of 'e'.
        CSimpleDeclaration declaration = e.getDeclaration();

        if (declaration instanceof CVariableDeclaration) {
            CVariableDeclaration cVarDeclaration = (CVariableDeclaration) declaration;
            return Set.of(new Var(cVarDeclaration.getName(), e, cVarDeclaration.getType(),
                    cVarDeclaration.isGlobal()));
        } else if (declaration instanceof CParameterDeclaration) {
            // the variables in CParameterDeclaration should be local variables.
            CParameterDeclaration cParaDeclaration = (CParameterDeclaration) declaration;
            return Set.of(new Var(cParaDeclaration.getName(), e, cParaDeclaration.getType(),
                    false));
        }

        return Set.of();
    }

    @Override
    public Set<Var> visit(CCharLiteralExpression e) throws NoException {
        return super.visit(e);
    }

    @Override
    public Set<Var> visit(CImaginaryLiteralExpression e) throws NoException {
        return super.visit(e);
    }

    @Override
    public Set<Var> visit(CFloatLiteralExpression e) throws NoException {
        return super.visit(e);
    }

    @Override
    public Set<Var> visit(CIntegerLiteralExpression e) throws NoException {
        return super.visit(e);
    }

    @Override
    public Set<Var> visit(CStringLiteralExpression e) throws NoException {
        return super.visit(e);
    }

    @Override
    public Set<Var> visit(CTypeIdExpression e) throws NoException {
        return super.visit(e);
    }

    @Override
    public Set<Var> visit(CUnaryExpression e) throws NoException {
        // return e.getOperand().accept(this);
        Set<Var> vars = e.getOperand().accept(this);
        return vars.isEmpty() ? Set.of() : Set.of(replace(vars.iterator().next(), e));
    }

    @Override
    public Set<Var> visit(CPointerExpression e) throws NoException {
        Set<Var> vars = e.getOperand().accept(this);
        return vars.isEmpty() ? Set.of() : Set.of(replace(vars.iterator().next(), e));
    }

    @Override
    public Set<Var> visit(CAddressOfLabelExpression e) throws NoException {
        return super.visit(e);
    }
}
