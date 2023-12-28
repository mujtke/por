package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;

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

    public void removeAllRelations() {
        // Remove rf, fr and mo for this event.
        // Rf.
        OGNode tmp;
        if (readFrom != null) {
            tmp = readFrom.inNode;
            readFrom.getReadBy().remove(this);
            readFrom = null;
            if (inNode.getRefCount("rf", tmp) < 1) {
                inNode.getReadFrom().remove(tmp);
                tmp.getReadBy().remove(inNode);
            }
        }
        // Rb.
        if (!readBy.isEmpty()) {
            Set<OGNode> rbns = new HashSet<>();
            readBy.forEach(rb -> {
                rbns.add(rb.inNode);
                rb.setReadFrom(null);
            });
            readBy.clear();
            rbns.forEach(rbn -> {
                if (inNode.getRefCount("rb", rbn) < 1) {
                    inNode.getReadBy().remove(rbn);
                    rbn.getReadFrom().remove(inNode);
                }
            });
        }

        // Fr.
        if (!fromRead.isEmpty()) {
            Set<OGNode> frns = new HashSet<>();
            fromRead.forEach(fr -> {
                frns.add(fr.inNode);
                fr.getFromReadBy().remove(this);
            });
            fromRead.clear();
            frns.forEach(frn -> {
                if (inNode.getRefCount("fr", frn) < 1) {
                    inNode.getFromRead().remove(frn);
                    frn.getFromReadBy().remove(inNode);
                }
            });
        }
        // Frb.
        if (!fromReadBy.isEmpty()) {
            Set<OGNode> frbns = new HashSet<>();
            fromRead.forEach(fr -> {
                frbns.add(fr.inNode);
                fr.getFromReadBy().remove(this);
            });
            fromRead.clear();
            frbns.forEach(frbn -> {
                if (inNode.getRefCount("frb", frbn) < 1) {
                    inNode.getFromRead().remove(frbn);
                    frbn.getFromReadBy().remove(inNode);
                }
            });
        }

        // Mo.
        if (moAfter != null) {
            tmp = moAfter.inNode;
            moAfter.setMoBefore(null);
            moAfter = null;
            if (inNode.getRefCount("ma", tmp) < 1) {
                inNode.getMoAfter().remove(tmp);
                tmp.getMoBefore().remove(inNode);
            }
        }
        if (moBefore != null) {
            tmp = moBefore.inNode;
            moBefore.setMoAfter(null);
            moBefore = null;
            if (inNode.getRefCount("mb", tmp) < 1) {
                inNode.getMoBefore().remove(tmp);
                tmp.getMoAfter().remove(inNode);
            }
        }
    }

    public SharedEvent getCoEvent(List<SharedEvent> coEvents) {
        for (SharedEvent co : coEvents)
            if (co.accessSameVarWith(this)) return co.deepCopy(new HashMap<>());

        return null;
    }

    public void copyRelations(SharedEvent coEvent) {
        // InNode.
        coEvent.inNode = inNode;
        // Rf.
        if (readFrom != null) {
            coEvent.readFrom = readFrom;
            readFrom.readBy.remove(this);
            readFrom.readBy.add(coEvent);
            readFrom = null;
        }
        // Rb.
        if (!readBy.isEmpty()) {
            readBy.forEach(rb -> {
                rb.readFrom = coEvent;
                coEvent.readBy.add(rb);
            });
            readBy.clear();
        }
        // Fr.
        if (!fromRead.isEmpty()) {
            fromRead.forEach(fr -> {
                fr.fromReadBy.remove(this);
                fr.fromReadBy.add(coEvent);
                coEvent.fromRead.add(fr);
            });
            fromRead.clear();
        }
        // Frb.
        if (!fromReadBy.isEmpty()) {
            fromReadBy.forEach(frb -> {
                frb.fromRead.remove(this);
                frb.fromRead.add(coEvent);
                coEvent.fromReadBy.add(frb);
            });
            fromReadBy.clear();
        }
        // Ma.
        if (moAfter != null) {
            coEvent.moAfter = moAfter;
            moAfter.moBefore = coEvent;
            moAfter = null;
        }
        // Mb.
        if (moBefore != null) {
            coEvent.moBefore = moBefore;
            moBefore.moAfter = coEvent;
            moBefore = null;
        }
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

        nEvent.inNode = this.inNode == null ? null : this.inNode.deepCopy(memo);

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
