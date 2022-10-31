package org.sosy_lab.cpachecker.cpa.por.xpor;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.util.Pair;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class XPORState implements AbstractState {

    // TODO: which fields we need.
    private int threadCounter;
    private CFAEdge procEdge;
    private LocationsState threadLocs;
    private Map<String, Integer> threadIdNumbers;

    // TODO: sleep set implements.
    // sleep set: { [<thread-id, edge-hashCode>, ..], .. }
    private Set<List<Pair<Integer, Integer>>> sleepSet;

    public static XPORState getInitialInstance(CFANode pInitNode, String pMainFunc, boolean pIsFollowFunctionCall) {
        assert pInitNode.getNumLeavingEdges() == 1 : "more than one edge is not allowed for init CFAnode";
        int initThreadCounter = 0;
        CFAEdge initEdge = pInitNode.getLeavingEdge(0);

        LocationsState initThreadLocs = LocationsState.getInitialInstance(pInitNode, pMainFunc, pIsFollowFunctionCall);
        Map<String, Integer> initThreadIdNumbers = new HashMap<>();

        return new XPORState(initThreadCounter, initEdge, initThreadLocs, initThreadIdNumbers, new HashSet<>());
    }

    public Set<List<Pair<Integer, Integer>>> getSleepSet() {
        return sleepSet;
    }

    public int getThreadCounter() {
        return threadCounter;
    }

    public Map<String, Integer> getThreadIdNumbers() {
        return threadIdNumbers;
    }

    public LocationsState getThreadLocs() {
        return threadLocs;
    }

    public XPORState(
            final int pThreadCounter,
            final CFAEdge pEdge,
            final LocationsState pLocs,
            final Map<String, Integer> pThreadIdNumbers,
            final Set<List<Pair<Integer, Integer>>> pSleepSet
    ) {
        threadCounter = pThreadCounter;
        procEdge = checkNotNull(pEdge);
        threadLocs = checkNotNull(pLocs);
        sleepSet = checkNotNull(pSleepSet);
    }
}
