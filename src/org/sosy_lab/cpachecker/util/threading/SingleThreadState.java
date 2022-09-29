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
package org.sosy_lab.cpachecker.util.threading;

import org.sosy_lab.cpachecker.cfa.model.CFANode;

public class SingleThreadState {

  // Thread location node.
  private final CFANode location;
  // Each thread is assigned to an Integer for identifying the cloned functions. (different thread
  // owns a unique number)
  private final int num;

  public SingleThreadState(CFANode pLoc, int pNum) {
    location = pLoc;
    num = pNum;
  }

  public CFANode getLocation() {
    return location;
  }

  public int getNum() {
    return num;
  }

  public String getFunctionName() {
    return location.getFunctionName();
  }

  public CFANode getLocationNode() {
    return location;
  }

  @Override
  public int hashCode() {
    return location.hashCode();
  }

  @Override
  public boolean equals(Object pOther) {
    if (!(pOther instanceof SingleThreadState)) {
      return false;
    }
    SingleThreadState other = (SingleThreadState) pOther;
    return location.equals(other.location);
  }

  @Override
  public String toString() {
    return location + " @@ " + num;
  }

}
