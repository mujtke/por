/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cpa.por.lppor;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;

public class LPPORState implements AbstractState {

  private int threadCounter;
  private CFAEdge transferInEdge;
  private LocationsState threadLocs;
  private Map<String, Integer> threadIdNumbers;

  public static LPPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;
    int initThreadCounter = 0;
    CFAEdge initEdge = pInitNode.getLeavingEdge(0);

    LocationsState initThreadLocs =
        LocationsState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

    Map<String, Integer> initThreadIdNumbers = new HashMap<>();
    initThreadIdNumbers.put(pMainThreadId, initThreadCounter);

    return new LPPORState(initThreadCounter, initEdge, initThreadLocs, initThreadIdNumbers);
  }

  public LPPORState(
      final int pThreadCounter,
      final CFAEdge pEdge,
      final LocationsState pLocs,
      final Map<String, Integer> pThreadIdNumbers) {
    assert pThreadCounter >= 0;

    threadCounter = pThreadCounter;
    transferInEdge = checkNotNull(pEdge);
    threadLocs = checkNotNull(pLocs);
    threadIdNumbers = checkNotNull(pThreadIdNumbers);
  }

  @Override
  public int hashCode() {
    return transferInEdge.hashCode() + threadLocs.hashCode() + threadIdNumbers.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof LPPORState) {
      LPPORState other = (LPPORState) pObj;
      return transferInEdge.equals(other.transferInEdge)
          && threadLocs.equals(other.threadLocs)
          && threadIdNumbers.equals(other.threadIdNumbers);
    }

    return false;
  }

  @Override
  public String toString() {
    MultiThreadState threadStates = threadLocs.getMultiThreadState();
    Set<String> threads = threadStates.getThreadIds();
    String result = "(";

    for (String thread : threads) {
      CFANode threadLoc = threadStates.getThreadLocation(thread);
      assert threadLoc != null && threadIdNumbers.containsKey(thread);
      int threadIdNumber = threadIdNumbers.get(thread);

      result += thread + "::" + threadIdNumber + "::" + threadLoc + " ";
    }

    return result + ")";
  }

  public int getThreadCounter() {
    return threadCounter;
  }

  public CFAEdge getTransferInEdge() {
    return transferInEdge;
  }

  public LocationsState getThreadLocs() {
    return threadLocs;
  }

  public Map<String, Integer> getThreadIdNumbers() {
    return threadIdNumbers;
  }

  public int getThreadIdNumber(String pThreadName) {
    assert threadIdNumbers.containsKey(pThreadName);
    return threadIdNumbers.get(pThreadName);
  }

  public int getTransferIdEdgeThreadId() {
    CFANode inEdgeSuc = transferInEdge.getSuccessor();
    String thread = threadLocs.getThreadName(inEdgeSuc);
    thread = (thread != null) ? thread : threadLocs.getThreadName(transferInEdge.getPredecessor());

    assert thread != null && threadIdNumbers.containsKey(thread);
    return threadIdNumbers.get(thread);
  }
}
