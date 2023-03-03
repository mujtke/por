package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.util.Triple;

import java.util.ArrayList;
import java.util.List;

public class OGNode {

    private final List<SharedEvent> events;

    private final Triple<Integer, Integer, Integer> threadStatus;

    public OGNode (List<SharedEvent> pEvents, Triple<Integer, Integer, Integer> pThreadStatus) {
        events = new ArrayList<SharedEvent>();
        threadStatus = pThreadStatus;
    }

}
