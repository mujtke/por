package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.util.Pair;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IPCDPORState implements AbstractState {

    private PeepholeState curState;
    private EdgeType transferInEdgeType;
    // sleep-set, {<thread_id, edge_hashCode>, ...}
    private Set<Pair<Integer, Integer>> sleepSet;
    private boolean hasUpdated;

    public static IPCDPORState getInitialInstance(CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
        assert pInitNode.getNumLeavingEdges() == 1 : "more one edge is not allowed for init CFAnode!";

        PeepholeState tmpInitState = PeepholeState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

        return new IPCDPORState(tmpInitState, EdgeType.NEdge, new HashSet<>(), false);
    }

    public IPCDPORState(
            PeepholeState pCurState,
            EdgeType pTransferInedgeType,
            Set<Pair<Integer, Integer>> pSleepSet,
            boolean pHasUpdated) {
        curState = pCurState;
        transferInEdgeType = pTransferInedgeType;
        sleepSet = pSleepSet;
        hasUpdated = pHasUpdated;
    }

    @Override
    public int hashCode() {
        return curState.hashCode() + transferInEdgeType.hashCode() + sleepSet.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else {
            if (obj != null && obj instanceof IPCDPORState) {
                IPCDPORState other = (IPCDPORState) obj;
                if ((curState == null && other.curState == null)
                || (curState != null && other.curState != null)) {
                    if (curState.equals(other.curState)
                    && transferInEdgeType.equals(other.transferInEdgeType)
                    && sleepSet.equals(other.sleepSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "curState: "
                + curState.toString()
                + ", inEdgeType: "
                + transferInEdgeType.toString()
                + ", sleepSet: "
                + sleepSet
                + ")";
    }

    public LocationsState getCurThreadLocs() {
        return curState.getThreadLocs();
    }

    public Map<String, Integer> getCurThreadNumbers() {
        return curState.getThreadIdNumbers();
    }

    public int getCurThreadCounter() {
        return curState.getThreadCounter();
    }
}
