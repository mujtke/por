package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SharedEvent implements Copier<SharedEvent> {

    public List<SharedEvent> getAllMoBefore() {
        List<SharedEvent> allMoBefore = new ArrayList<>();
        SharedEvent next = this.getMoBefore();
        while (next != null) {
            Preconditions.checkState(!allMoBefore.contains(next),
                    "mo should be acyclic.");
            allMoBefore.add(next);
            next = next.getMoBefore();
        }

        return allMoBefore;
    }

    public enum AccessType { WRITE, READ, UNKNOWN; }
    private final Var var;
    private final AccessType aType;

    /* Read form and read by. */
    // An event can read from one event at most.
    private SharedEvent readFrom;
    // An event may read by many events.
    private final List<SharedEvent> readBy = new ArrayList<>();

    /* TODO: Modification order. Just record one event? */
    private SharedEvent moBefore;
    private SharedEvent moAfter;

    /* Write before. */
    private final List<SharedEvent> wBefore = new ArrayList<>();
    private final List<SharedEvent> wAfter = new ArrayList<>();

    /* From read. */
    private final List<SharedEvent> fromRead = new ArrayList<>();
    private final List<SharedEvent> fromReadBy = new ArrayList<>();

    // ogNode this event in.
    private OGNode inNode;
    // CFAEdge this event in.
    private CFAEdge inEdge;

    @Override
    public String toString() {
        return aType + "(" + var + ")@" + inEdge;
    }

    public SharedEvent (Var pVar,
                        AccessType pAccessType,
                        CFAEdge pInEdge) {
        this.var = pVar;
        this.aType = pAccessType;
        this.inEdge = pInEdge;
    }

    public SharedEvent deepCopy(Map<Object, Object> memo) {
        if (memo.containsKey(this)) {
            assert memo.get(this) instanceof SharedEvent;
            return (SharedEvent) memo.get(this);
        }

        SharedEvent nEvent = new SharedEvent(this.var, this.aType, this.inEdge);
        memo.put(this, nEvent);

        /* Read from & read by. */
        nEvent.readFrom = this.readFrom != null ? this.readFrom.deepCopy(memo) : null;
//        this.readBy.forEach(rb -> nEvent.readBy.add(rb.deepCopy(memo)));
        for (SharedEvent rb : readBy) {
            SharedEvent nrb = rb.deepCopy(memo);
            nEvent.readBy.add(nrb);
        }

        /* Modification order: no copy. */
        nEvent.moAfter = this.moAfter != null ? this.moAfter.deepCopy(memo) : null;
        nEvent.moBefore = this.moBefore != null ? this.moBefore.deepCopy(memo) : null;

        /* Write before: no copy. */

        /* From read: no copy. */

        nEvent.inNode = this.inNode.deepCopy(memo);

        return nEvent;
    }

    public SharedEvent getReadFrom() {
        return readFrom;
    }

    public void setReadFrom(SharedEvent readFrom) {
        this.readFrom = readFrom;
    }

    public SharedEvent getMoBefore() {
        return moBefore;
    }

    public List<SharedEvent> getFromRead() {
        return fromRead;
    }

    public List<SharedEvent> getFromReadBy() {
        return fromReadBy;
    }

    public void setInNode(OGNode inNode) {
        this.inNode = inNode;
    }

    public OGNode getInNode() {
        return this.inNode;
    }

    public void setMoBefore(SharedEvent moBefore) {
        this.moBefore = moBefore;
    }

    public SharedEvent getMoAfter() {
        return moAfter;
    }

    public void setMoAfter(SharedEvent moAfter) {
        this.moAfter = moAfter;
    }

    public List<SharedEvent> getReadBy() {
        return this.readBy;
    }

    public List<SharedEvent> getWAfter() {
        return this.wAfter;
    }

    public List<SharedEvent> getWBefore() {
        return this.wBefore;
    }

    public Var getVar() {
        return this.var;
    }

    public AccessType getAType() {
        return aType;
    }

    public CFAEdge getInEdge() { return inEdge; }

    public boolean accessSameVarWith(SharedEvent other) {
        return this.var.getName().equals(other.var.getName());
    }
}
