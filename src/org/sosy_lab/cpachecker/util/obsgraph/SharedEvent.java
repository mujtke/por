package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.ArrayList;
import java.util.List;

public class SharedEvent {

    public enum AccessType { WRITE, READ, UNKNOWN; }

    private final Var var;

    private SharedEvent readFrom;
    private List<SharedEvent> readBy; // an event may read by many events.
    private SharedEvent poAfter;
    private List<SharedEvent> poBefore; // an event may poBefore many events.

    // access type
    private final AccessType aType;

    private OGNode ogNode;

    public Var getVar() {
        return var;
    }

    public OGNode getOgNode() {
        return ogNode;
    }

    public void setOgNode(OGNode ogNode) {
        this.ogNode = ogNode;
    }

    public AccessType getAType() {
        return aType;
    }

    public SharedEvent getReadFrom() {
        return readFrom;
    }

    public void setReadFrom(SharedEvent readFrom) {
        this.readFrom = readFrom;
    }

    public List<SharedEvent> getReadBy() {
        return readBy;
    }

    public void setReadBy(List<SharedEvent> readBy) {
        this.readBy = readBy;
    }

    public void readByAdd(SharedEvent e) {
        readBy.add(e);
    }

    public SharedEvent getPoAfter() {
        return poAfter;
    }

    public void setPoAfter(SharedEvent poAfter) {
        this.poAfter = poAfter;
    }

    public List<SharedEvent> getPoBefore() {
        return poBefore;
    }

    public void setPoBefore(List<SharedEvent> poBefore) {
        this.poBefore = poBefore;
    }

    public void poBeforeAdd(SharedEvent e) {
        poBefore.add(e);
    }

    public SharedEvent (
            Var pVar,
            AccessType pAccessType) {
        this.var = pVar;
        this.aType = pAccessType;
        this.ogNode = null;
        this.readFrom = null;
        this.readBy = new ArrayList<>();
        this.poAfter = null;
        this.poBefore = new ArrayList<>();
    }

    // return true, if 'this' occurs before 'other' in the trace.
    public boolean traceBefore(SharedEvent other) {

        int thisNum = this.getOgNode().getNumInTrace(),
                otherNum = other.getOgNode().getNumInTrace();
        // the num smaller, the position in trace more behind. -1 means behind infinitely.
        // if thisNum == -1 && otherNum == -1, we return false;
        if ((thisNum < 0) || (thisNum > 0 && otherNum > thisNum)) {
            return false;
        }

        return true;
    }

    // return true if 'this' is happen-before for 'other'.
    // TODO: hb should consider the po and rf relation together.
    public boolean hb(SharedEvent other) {
        assert other != null;

        // note that, both 'this' and 'other' have existed in the trace, which mean their
        // ogNodes' numInTrace > 0;
        OGNode thisNode = this.ogNode, otherNode = other.ogNode;
        int thisNumInTrace = thisNode.getNumInTrace(), otherNumInTrace = otherNode.getNumInTrace();
        assert thisNumInTrace > 0 && otherNumInTrace > 0;
        if (thisNumInTrace > otherNumInTrace) {
            return false;
        }
        // if 'this' and 'other' locate at the same ogNode.
        if (thisNode.equals(otherNode)) { // i.e., thisNumInTrace == otherNumInTrace.
            return thisNode.getEvents().indexOf(this) < otherNode.getEvents().indexOf(other);
        }

        // else, 'this' and 'other' locate at different ogNodes.
        // recursive check whether 'this' is happen-before for 'other'.
        // if eNode.poBefore() = { n1, n2, ..., nk }, then
        // eNode.isHb(oNode) = n1.isHb(oNode) || n2.isHb(oNode) || ... || nk.isHb(oNode).
        boolean isHbOther = false;
        for (SharedEvent e : this.getPoBefore()) {
            OGNode eNode = e.getOgNode();
            isHbOther = isHbOther || eNode.hb(otherNode);
            if (isHbOther) {
                break;
            }
        }

        return isHbOther;
    }
}
