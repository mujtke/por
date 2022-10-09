package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import static com.google.common.collect.FluentIterable.from;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

import java.util.*;

@Options(prefix = "cpa.por.ipcdpor")
public class IPCDPORTransferRelation extends SingleEdgeTransferRelation {

    @Option(
           secure = true,
           description = "With this option enabled, thread creation edge will" +
                   "be regarded as a normal edge, which may reduces the redundant" +
                   "interleavings")
    private boolean regardThreadCreationAsNormalEdge = false;

    private final LocationsCPA locationsCPA;
    private final ConditionalDepGraph conDepGraph;

    public IPCDPORTransferRelation (Configuration pConfig, LogManager pLogger, CFA pCfa)
        throws InvalidConfigurationException {
        pConfig.inject(this);
        locationsCPA = LocationsCPA.create(pConfig, pLogger, pCfa);
        conDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState state, Precision precision, CFAEdge cfaEdge) throws CPATransferException, InterruptedException {

        IPCDPORState curState = (IPCDPORState) state;

        // compute the new locations.
        Collection<? extends AbstractState> newLocStates = locationsCPA.getTransferRelation().getAbstractSuccessorsForEdge(curState.getCurThreadLocs(), precision, cfaEdge);
        assert newLocStates.size() <= 1 : "the number of newly gotten locationsState must be less or equal 1";

        if (newLocStates.isEmpty()) {
            // no successor produced.
            return ImmutableSet.of();
        }

        // got the new locs(actually, the new loc).
        LocationsState newLocs = (LocationsState) newLocStates.iterator().next();
        Map<String, Integer> oldThreadNumbers = curState.getCurThreadNumbers();

        //update the thread id and number.
        Pair<Integer, Map<String, Integer>> newThreadIdInfo =
                updateThreadIdNumber(curState.getCurThreadCounter(), oldThreadNumbers, newLocs);
        int newThreadCounter = newThreadIdInfo.getFirst();
        Map<String, Integer> newThreadIdNumbers = newThreadIdInfo.getSecond();

        // note: here, we only generate the successor of cfaEdge, the POR process is conducted
        // in precision adjustment part.
        return ImmutableSet.of(
                new IPCDPORState(
                        new PeepholeState(
                                newThreadCounter,
                                cfaEdge,
                                newLocs,
                                newThreadIdNumbers),
                        determineEdgeType(cfaEdge),
                        new HashSet<>(),
                        false)
        );
    }

    Pair<Integer, Map<String, Integer>> updateThreadIdNumber (
            int pOldThreadCounter,
            final Map<String, Integer> pOldThreadIdNumbers,
            final LocationsState pNewLocs) {
        // copy the threadIdNumber into the 'newThreadIdNumber' from 'pOldThreadIdNumber'
        Map<String, Integer> newThreadIdNumbers = new HashMap<>(pOldThreadIdNumbers);

        // remove the threads which exited.
        // 1.get all thread-ids in new 'locationsState'
        Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
        // 2.get all thread-ids that doesn't exist in 'newThreadIds'
        ImmutableSet<String> TidsNeedRemoved =
                from(newThreadIdNumbers.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
        // 3. according to 'TidsNeedRemoved', remove the related pairs in 'newThreadIdNumbers'
        TidsNeedRemoved.forEach(t -> newThreadIdNumbers.remove(t));

        // add the thread's id and number which created newly.
        ImmutableSet<String> TidsNeedAdded =
                from(newThreadIds).filter(t -> !newThreadIdNumbers.containsKey(t)).toSet();
        assert TidsNeedAdded.size() <= 1 : "the number of newly created thread must be less or equal 1";
        // if nonempty, add the thread id and its number
        if (!TidsNeedAdded.isEmpty()) {
            newThreadIdNumbers.put(TidsNeedAdded.iterator().next(), ++pOldThreadCounter);
        }

        // here 'pOldThreadCounter' has increased, so it is 'newThreadCounter' actually.
        // Pair<Integer, Map<String, Integer>>中，兩个integer应该是相等的
        return Pair.of(pOldThreadCounter, newThreadIdNumbers);
    }

    EdgeType determineEdgeType(final CFAEdge pEdge) {
        assert pEdge != null : "pEdge shouldn't be null when judging its type!";

        if (isThreadCreationEdge(pEdge)) {
            if (regardThreadCreationAsNormalEdge) {
                // if regard "pthread_create()" as a normal edge, return "NEdge"
                return EdgeType.NEdge;
            } else {
                // else, regard it as a "GVAEdge" which access the global variable.
                return EdgeType.GVAEdge;
            }
        }

        if (conDepGraph.contains(pEdge.hashCode())) {
            return EdgeType.GVAEdge;
        } else if (pEdge instanceof CAssumeEdge) {
            return EdgeType.NAEdge;
        } else {
            return EdgeType.NEdge;
        }

    }

    public boolean isThreadCreationEdge(final CFAEdge pEdge) {
        switch (pEdge.getEdgeType()) {
            case StatementEdge: {
                AStatement statement = ((AStatementEdge) pEdge).getStatement();
                if (statement instanceof AFunctionCall) {
                    AFunctionCall functionCall = (AFunctionCall) statement;
                    AExpression functionNameExp = functionCall.getFunctionCallExpression().getFunctionNameExpression();
                    if (functionNameExp instanceof AIdExpression) {
                        AIdExpression functionNameIdExp = (AIdExpression) functionNameExp;
                        return functionNameIdExp.getName().contains("pthread_create");
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }


    @Override
    public Collection<? extends AbstractState> strengthen(AbstractState state, Iterable<AbstractState> otherStates, @Nullable CFAEdge cfaEdge, Precision precision) throws CPATransferException, InterruptedException {
        return super.strengthen(state, otherStates, cfaEdge, precision);
    }
}
