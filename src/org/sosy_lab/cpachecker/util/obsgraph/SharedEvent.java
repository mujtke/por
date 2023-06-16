package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.ArrayList;
import java.util.List;

public class SharedEvent {

    public enum AccessType { WRITE, READ, UNKNOWN; }
    public final Var var;
    public final AccessType aType;
    // an event can read from one event at most.
    public SharedEvent readFrom;
    // an event may read by many events.
    public List<SharedEvent> readBy;
    public List<SharedEvent> moBefore;
    public SharedEvent moAfter;
    public List<SharedEvent> wBefore;
    public List<SharedEvent> wAfter;
    public List<SharedEvent> fromRead;
    public List<SharedEvent> fromReadBy;

    // ogNode this event in.
    public OGNode inNode;

    @Override
    public String toString() {
        return aType + "(" + var + ")";
    }

    public SharedEvent (Var pVar,
                        AccessType pAccessType) {
        this.var = pVar;
        this.aType = pAccessType;
        this.inNode = null;
        this.readFrom = null;
        this.readBy = new ArrayList<>();
        this.moAfter = null;
        this.moBefore = new ArrayList<>();
        this.wBefore = new ArrayList<>();
        this.wAfter = new ArrayList<>();
        this.fromRead = new ArrayList<>();
        this.fromReadBy = new ArrayList<>();
    }
}
