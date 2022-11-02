package org.sosy_lab.cpachecker.cpa.por.xpor;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.util.Pair;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class XPORState implements AbstractState {

    // TODO: which fields we need.
    private int threadCounter;
    // curState's in-Edge.
    private CFAEdge procEdge;
    private LocationsState threadLocs;
    // { [edge_hashCode, <main, 0>], ... }
    private Map<Integer, Pair<String, Integer>> threadIdNumbers;

    // TODO: sleep set implements.
    // sleep set: { [<thread-id, edge-hashCode>, ..], .. }
    private Set<ArrayList<Pair<Integer, Integer>>> sleepSet;
    // TODO: a cache to store the edges pruned by isolated transition.
    private Set<Pair<Integer, Integer>> isolatedSleepSet;

    public static XPORState getInitialInstance(CFANode pInitNode, String pMainFunc, boolean pIsFollowFunctionCall) {
        assert pInitNode.getNumLeavingEdges() == 1 : "more than one edge is not allowed for init CFAnode";
        int initThreadCounter = 0;
        CFAEdge initEdge = pInitNode.getLeavingEdge(0);

        LocationsState initThreadLocs = LocationsState.getInitialInstance(pInitNode, pMainFunc, pIsFollowFunctionCall);
        Map<Integer, Pair<String, Integer>> initThreadIdNumbers = new HashMap<>();
        // TODO: for initialState, set the edge_hashCode == initEdge.hashCode()
        initThreadIdNumbers.put(initEdge.hashCode(), Pair.of(pMainFunc, initThreadCounter));

        return new XPORState(initThreadCounter, initEdge, initThreadLocs, initThreadIdNumbers, new HashSet<>(), new HashSet<>());
    }

    public Set<ArrayList<Pair<Integer, Integer>>> getSleepSet() {
        return sleepSet;
    }

    public int getThreadCounter() {
        return threadCounter;
    }

    public Map<Integer, Pair<String, Integer>> getThreadIdNumbers() {
        return threadIdNumbers;
    }

    public LocationsState getThreadLocs() {
        return threadLocs;
    }

    public XPORState(
            final int pThreadCounter,
            final CFAEdge pEdge,
            final LocationsState pLocs,
            final Map<Integer, Pair<String, Integer>> pThreadIdNumbers,
            final Set<ArrayList<Pair<Integer, Integer>>> pSleepSet,
            final Set<Pair<Integer, Integer>> pIsolatedSleepSet
    ) {
        threadCounter = pThreadCounter;
        procEdge = checkNotNull(pEdge);
        threadLocs = checkNotNull(pLocs);
        sleepSet = checkNotNull(pSleepSet);
        threadIdNumbers = checkNotNull(pThreadIdNumbers);
        isolatedSleepSet = checkNotNull(pIsolatedSleepSet);
    }

    public void updateSleepSet(final Set<ArrayList<Pair<Integer, Integer>>> oldSleepSet, Pair<Integer, Integer> curEdge) {

        for(ArrayList l : oldSleepSet) {
            assert l.size() >= 1 : "every list in sleep set should have one element at least";
            if(l.size() == 1) {
                // TODO: only one edge, we can skip?
                continue;
            }
            Iterator ite = l.iterator();
            if(ite.hasNext()) {
                Pair<Integer, Integer> firstEdge = (Pair<Integer, Integer>) ite.next();
                // only when 'firstEdge' == 'curEdge' and there are other edges after the 'firstEdge',
                // we can remove the 'curEdge' from the 'l'.
                if (firstEdge.equals(curEdge) && ite.hasNext()) {
                    ArrayList newEdgeAddToSleepSet = new ArrayList<>();
                    newEdgeAddToSleepSet.add(ite.next());
                    sleepSet.add(newEdgeAddToSleepSet);
                }
            }
        }
    }

    public void sleepSetAdd(ArrayList<Pair<Integer, Integer>> newEdges) {
        sleepSet.add(newEdges);
    }

    public void setSleepSet(Set<ArrayList<Pair<Integer, Integer>>> pSleepSet) {
        sleepSet = pSleepSet;
    }

    public boolean sleepSetContain(Pair<Integer, Integer> curEdge) {
        boolean result = false;
        for(ArrayList l : sleepSet) {
            Iterator ite = l.iterator();
            if(ite.hasNext()) {
                Pair<Integer, Integer> firstEdge = (Pair<Integer, Integer>) ite.next();
                // iff 'l' contains and only contains 'curEdge', we think curEdge is in its predecessor's sleep set.
                if(firstEdge.equals(curEdge) && !ite.hasNext()) {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    public void isolatedSleepSetAdd(Pair<Integer, Integer> curEdge) {
        isolatedSleepSet.add(curEdge);
    }

    public boolean isolatedSleepSetContains(Pair<Integer, Integer> curEdge) {
        return isolatedSleepSet.contains(curEdge);
    }

    public CFAEdge getProcEdge() {
        return procEdge;
    }

    public void setThreadIdNumbers(Map<Integer, Pair<String, Integer>> newThreadIdNumbers) {
        threadIdNumbers = newThreadIdNumbers;
    }

    public boolean equals(Object other) {
        return true;
    }

    public int hashCode() {
        return procEdge.hashCode() + sleepSet.hashCode() + threadLocs.hashCode();
    }
}
