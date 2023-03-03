package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

public class SharedEvent {

    private final Var var;

    // access type

    private Object readFrom;

    private Object readBy;

    private Object poAfter;

    private Object poBefore;

    // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
    public Triple<Integer, Integer, Integer> threadStatus;

    public SharedEvent (
            Var pVar,
            Object pReadFrom,
            Object pReadBy,
            Object pPoAfter,
            Object pPoBefore,
            Triple<Integer, Integer, Integer> pThreadStatus) {
        this.var = pVar;
        this.readFrom = pReadFrom;
        this.readBy = pReadBy;
        this.poAfter = pPoAfter;
        this.poBefore = pPoBefore;
        this.threadStatus = pThreadStatus;
    }
}
