package org.sosy_lab.cpachecker.cpa.bdd;

import com.google.common.base.Preconditions;
import jdd.bdd.BDD;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.cfa.model.*;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.interfaces.WrapperPrecision;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionManager;
import org.sosy_lab.cpachecker.util.variableclassification.Partition;
import org.sosy_lab.cpachecker.util.variableclassification.VariableClassification;

import java.sql.Wrapper;
import java.util.*;

@Options(prefix = "cpa.bdd.csh")
public class ConditionalStatementHandler {

    private final VariableClassification varClass;
    private final BitvectorManager bvmgr;
    private final NamedRegionManager nrmgr;
    private final PredicateManager predmgr;
    private final BitvectorComputer bvComputer;

    // Debug.
    private Configuration config;
    private CFA cfa;

    @Option(
            secure = true,
            description = "use a smaller bitsize for all vars, that have only intEqual values"
    )
    private boolean compressIntEqual = true;

    @Option(
            description = "list of indeterminate assignment function."
    )
    private String randomFunctions[] = {
            "__VERIFIER_nondet_bool",
            "__VERIFIER_nondet_int"
    };

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

        config = pConfig;
        cfa = pCfa;
    }

    /**
     * FIXME
     * Precondition: r locates in an assume statement (e.g., x > 1).
     * When setting r to read from w that makes the condition not hold, i.e., (x > 1) not
     * hold, set A as true (has Conflict). If w contains indeterminate assignment, then we
     * set B as true (has indeterminacy).
     * @return < A, B >
     * A = true if letting r read from w leads to conflict.
     * B = true if the w is an indeterminate assignment.
     */
    public Pair<Boolean, Boolean> handleAssumeStatement(
            SharedEvent r,
            SharedEvent w,
            Precision precision)
            throws UnsupportedCodeException {

        boolean hasConflict, hasIndeterminacy;
        CFAEdge rEdge = r.getInEdge();
        if (!(rEdge instanceof AssumeEdge)) {
            return Pair.of(false, false);
        }

        final AssumeEdge assumption = (AssumeEdge) rEdge;
        CFAEdge wEdge = w.getInEdge();
        OGNode wNode = w.getInNode();

        hasConflict = hasConflict(assumption, wNode, precision);
        hasIndeterminacy = hasIndeterminacy(w, wNode, wEdge);

        return Pair.of(hasConflict, hasIndeterminacy);
    }

    private boolean hasIndeterminacy(SharedEvent w, OGNode wNode, CFAEdge wEdge)
            throws UnsupportedCodeException {

        boolean hasIndeterminacy = false;
        String varName = w.getVar().getName();

        switch (wEdge.getEdgeType()) {
            case StatementEdge:
                final AStatementEdge statementEdge = (AStatementEdge) wEdge;
                CStatement statement = (CStatement) statementEdge.getStatement();
                if (statement instanceof CAssignment) {
                    CAssignment assignment = (CAssignment) statement;
                    CExpression lhs = assignment.getLeftHandSide();
                    final String lhsVarName;
                    if (lhs instanceof CIdExpression) {
                        lhsVarName = ((CIdExpression) lhs).getName();
                        assert Objects.equals(varName, lhsVarName) :
                                "Wrong cfaEdge '" + wEdge + "' for event in " + wNode;
                    } else {
                        throw new UnsupportedOperationException(
                                "Lhs " + lhs + " is not a CIdExpression.");
                    }

                    CRightHandSide rhs = assignment.getRightHandSide();
                    if (rhs instanceof CExpression) {
                        // FIXME: In this case, no indeterminacy exists?
                        //
                    } else if (rhs instanceof CFunctionCallExpression) {
                        // FIXME: here we assume that only the function call like
                        //  'x = __VERIFIER_nondet_int();' contains the indeterminacy.
                        CFunctionCallExpression funCallExpr =
                                (CFunctionCallExpression) rhs;
                        if (Arrays.stream(randomFunctions).anyMatch(f -> funCallExpr
                                .getFunctionNameExpression().toString().contains(f))) {
                            hasIndeterminacy = true;
                        }
                    } else {
                        throw new UnsupportedCodeException("Not handled edge", wEdge);
                    }
                }
                break;

            case FunctionReturnEdge:

            case DeclarationEdge:

            default:
        }

        return hasIndeterminacy;
    }

    private boolean hasConflict(AssumeEdge assumption, OGNode wNode, Precision precision) {
        BDDState wBDDState = AbstractStates.extractStateByType(wNode.getSucState(),
                        BDDState.class);
        assert wBDDState != null;
        AExpression aExpression = assumption.getExpression();
        assert aExpression instanceof CExpression;
        CExpression cExpression = (CExpression) aExpression;
        Region[] assumeRegion = null;
        try {
            PredicateManager predMgr = new PredicateManager(config, wBDDState.getManager(), cfa);
            assumeRegion = cExpression.accept(new BDDVectorCExpressionVisitor(predMgr,
                    null, wBDDState.getBvmgr(), cfa.getMachineModel(), null));
        } catch (Exception e) {
            throw new UnsupportedOperationException(
                    "Cannot compute the BDD region for " + assumption);
        }

        assert assumeRegion != null;

        Region assumeRegionEvaluated = wBDDState.getBvmgr().makeOr(assumeRegion);
        if (!assumption.getTruthAssumption()) {
            assumeRegionEvaluated = wBDDState.getManager().makeNot(assumeRegionEvaluated);
        }

        Collection<BDDState> tmpSuccessors;
        try {
            Optional<ConfigurableProgramAnalysis> cpa = GlobalInfo.getInstance().getCPA();
            assert cpa.isPresent();
            BDDCPA bddCpa = retriveCPA(cpa.get(), BDDCPA.class);
            BDDTransferRelation bddTransfer =
                    (BDDTransferRelation) bddCpa.getTransferRelation();
            VariableTrackingPrecision bddPrecision =
                    retrivePrecision(precision, VariableTrackingPrecision.class);
            tmpSuccessors =
                    bddTransfer.getAbstractSuccessorsForEdge(wBDDState, bddPrecision, assumption);
            if (tmpSuccessors.isEmpty()) {
                // Log something.
            }
        } catch (InvalidConfigurationException | CPATransferException
                 | InterruptedException e) {
            throw new RuntimeException(e);
        }

        Region region =
                wBDDState.getManager().makeAnd(wBDDState.getRegion(), assumeRegionEvaluated);

        // FIXME: tmpSuccessors.isEmpty() is enough?
        return tmpSuccessors.isEmpty() || region.isFalse();
    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigurableProgramAnalysis> T
    retriveCPA(final ConfigurableProgramAnalysis pCPA, Class<T> pClass)
            throws InvalidConfigurationException {
        if (pCPA.getClass().equals(pClass)) {
            return (T) pCPA;
        } else if (pCPA instanceof WrapperCPA) {
            WrapperCPA wCPAs = (WrapperCPA) pCPA;
            T result = wCPAs.retrieveWrappedCpa(pClass);

            if (result != null) {
                return result;
            }
        }
        throw new InvalidConfigurationException("Could not find the CPA " + pClass + " from " + pCPA);
    }

    public <T extends Precision> T retrivePrecision(final Precision pPrecision,
            Class<T> pClass) throws InvalidConfigurationException {
        if (Objects.equals(pPrecision.getClass(), pClass)) {
            return (T) pPrecision;
        } else if (pPrecision instanceof WrapperPrecision) {
            WrapperPrecision wrapperPrecision = (WrapperPrecision) pPrecision;
            T result = wrapperPrecision.retrieveWrappedPrecision(pClass);
            if (result != null)
                return result;
        }
        throw new InvalidConfigurationException("Could not find the CPA " + pClass + " " +
                "from " + pPrecision);
    }
}
