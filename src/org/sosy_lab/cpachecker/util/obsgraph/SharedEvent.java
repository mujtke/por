package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

public class SharedEvent {

    public enum AccessType { WRITE, READ, UNKNOWN; }

    private final Var var;

    private SharedEvent readFrom;
    private SharedEvent readBy;
    private SharedEvent poAfter;
    private SharedEvent poBefore;

    // access type
    private final AccessType aType;

    public AccessType getAType() {
        return aType;
    }

    public SharedEvent getReadFrom() {
        return readFrom;
    }

    public void setReadFrom(SharedEvent readFrom) {
        this.readFrom = readFrom;
    }

    public SharedEvent getReadBy() {
        return readBy;
    }

    public void setReadBy(SharedEvent readBy) {
        this.readBy = readBy;
    }

    public SharedEvent getPoAfter() {
        return poAfter;
    }

    public void setPoAfter(SharedEvent poAfter) {
        this.poAfter = poAfter;
    }

    public SharedEvent getPoBefore() {
        return poBefore;
    }

    public void setPoBefore(SharedEvent poBefore) {
        this.poBefore = poBefore;
    }

    public SharedEvent (
            Var pVar,
            AccessType pAccessType) {
        this.var = pVar;
        this.aType = pAccessType;
    }
}
