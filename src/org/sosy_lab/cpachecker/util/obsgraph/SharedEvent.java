package org.sosy_lab.cpachecker.util.obsgraph;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;

public class SharedEvent implements Copier<SharedEvent> {

    public enum AccessType { WRITE, READ }
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

    // FIXME
    public List<SharedEvent> getAllMoBefore() {
        List<SharedEvent> allMoBefore = new ArrayList<>();
        SharedEvent next = this.getMoBefore();
        while (next != null) {
            assert !allMoBefore.contains(next) : "mo should be acyclic!";
            allMoBefore.add(next);
            next = next.getMoBefore();
        }

        return allMoBefore;
    }

    // FIXME
    public SharedEvent getCoEvent(List<SharedEvent> coEvents) {
        for (SharedEvent co : coEvents)
            if (co.accessSameVarWith(this)) return co.deepCopy(new HashMap<>());

        return null;
    }

    // Remove rf, fr and mo for this event.
    public void removeAllRelations() {
        List<SharedEvent> toRemove;
        // Rf
        if (readFrom != null)
            removeReadFrom();
        toRemove = new ArrayList<>(readBy);
        toRemove.forEach(this::removeReadBy);
        // readBy.forEach(this::removeReadBy);

        // Fr.
        toRemove = new ArrayList<>(fromRead);
        toRemove.forEach(this::removeFromRead);
        toRemove = new ArrayList<>(fromReadBy);
        toRemove.forEach(this::removeFromReadBy);
        // fromRead.forEach(this::removeFromRead);
        // fromReadBy.forEach(this::removeFromReadBy);

        // Mo.
        if (moAfter != null)
            removeMoAfter();
        if (moBefore != null)
            removeMoBefore();
    }

    /**
     * If (a, b) \in mo and we want to remove (a, b) from mo, then we just need
     * to call a.removeBefore() or b.removeMoAfter() once, not both.
     * Similarly, if (a, b) \in rf and we want to remove (a, b), then
     * just call a.removeReadRy(b) or b.removeReadFrom() once.
     * Note: we will follow the way above when handling other relations like fr,
     * and any possible new relations. Besides, for each method defined on
     * {@link SharedEvent}, we will also define a method with the same name on the
     * {@link OGNode}.
     */
    public void removeMoAfter() {
        assert moAfter != null && moAfter.moBefore == this :
                "Trying to remove a relation not exists!";
        OGNode maInNode = moAfter.inNode;
        moAfter.moBefore = null;
        moAfter = null;
        if (inNode.getRefCount("ma", maInNode) < 1) {
            inNode.removeMoAfter(maInNode);
            maInNode.removeMoBefore(inNode);
        }
    }

    public void removeMoBefore() {
        assert moBefore != null && moBefore.moAfter == this :
                "Trying to remove a relation not exists!";
        OGNode mbInNode = moBefore.inNode;
        moBefore.moAfter = null;
        moBefore = null;
        if (inNode.getRefCount("mb", mbInNode) < 1) {
            inNode.removeMoBefore(mbInNode);
            mbInNode.removeMoAfter(inNode);
        }
    }

    public void removeReadFrom() {
        assert readFrom != null && readFrom.readBy.contains(this) :
                "Trying to remove a relation not exists!";
        OGNode rfNode = readFrom.getInNode();
        readFrom.readBy.remove(this);
        readFrom = null;
        if (inNode.getRefCount("rf", rfNode) < 1) {
            inNode.removeReadFrom(rfNode);
            rfNode.removeReadBy(inNode);
        }
    }

    public void removeReadBy(SharedEvent rb) {
        assert rb != null && readBy.contains(rb) && rb.readFrom == this :
                "Trying to remove a relation not exists!";
        readBy.remove(rb);
        rb.readFrom = null;
        OGNode rbNode = rb.getInNode();
        if (inNode.getRefCount("rb", rbNode) < 1) {
            inNode.removeReadBy(rbNode);
            rbNode.removeReadFrom(inNode);
        }
    }

    public void removeFromRead(SharedEvent fr) {
        assert fr != null && fromRead.contains(fr)
                && fr.getFromReadBy().contains(this) :
                "Trying to remove a relation not exists!";
        fromRead.remove(fr);
        fr.fromReadBy.remove(this);
        OGNode frNode = fr.getInNode();
        if (inNode.getRefCount("fr", frNode) < 1) {
            inNode.removeFromRead(frNode);
            frNode.removeFromReadBy(inNode);
        }
    }

    public void removeFromReadBy(SharedEvent frb) {
        assert frb != null && fromReadBy.contains(frb)
                && frb.getFromRead().contains(this) :
                "Trying to remove a relation not exists!";
        fromReadBy.remove(frb);
        frb.fromRead.remove(this);
        OGNode frbNode = frb.getInNode();
        if (inNode.getRefCount("frb", frbNode) < 1) {
            inNode.removeFromReadBy(frbNode);
            frbNode.removeFromRead(inNode);
        }
    }

    /**
     * Similarly, when add (a, b) to rf, fr or mo, just call a.setReadFrom(b) or
     * b.setReadBy(a) once. And all method defined on {@link SharedEvent} will
     * also be defined on {@link OGNode}.
     */
    public void setReadFrom(SharedEvent rf) {
        assert rf != null;
        assert readFrom == null && !rf.readBy.contains(this) :
                "It's not allowed to add a new rf relation when there has been one!";
        readFrom = rf;
        rf.getReadBy().add(this);
        OGNode rfNode = rf.getInNode();
        if (!rfNode.readBy(inNode))
            rfNode.setReadBy(inNode);
        if (!inNode.readFrom(rfNode))
            inNode.setReadFrom(rfNode);
    }

    public void setReadBy(SharedEvent rb) {
        assert rb != null;
        assert rb.readFrom != this && !readBy.contains(this) :
                "It's not allowed to add a new rf relation when there has been one!";
        readBy.add(rb);
        rb.readFrom = this;
        OGNode rbNode = rb.getInNode();
        if (!inNode.readBy(rbNode))
            inNode.setReadBy(rbNode);
        if (!rbNode.readFrom(inNode))
            rbNode.setReadFrom(inNode);
    }


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
        if (memo.containsKey(System.identityHashCode(this))) {
            assert memo.get(System.identityHashCode(this)) instanceof SharedEvent;
            return (SharedEvent) memo.get(System.identityHashCode(this));
        }

        SharedEvent nEvent = new SharedEvent(this.var, this.aType, this.inEdge);
        memo.put(System.identityHashCode(this), nEvent);

        /* Read from & read by. */
        nEvent.readFrom = this.readFrom != null ? this.readFrom.deepCopy(memo) : null;
        this.readBy.forEach(rb -> nEvent.readBy.add(rb.deepCopy(memo)));

        /* Modification order. */
        nEvent.moAfter = this.moAfter != null ? this.moAfter.deepCopy(memo) : null;
        nEvent.moBefore = this.moBefore != null ? this.moBefore.deepCopy(memo) : null;

        /* Write before: no copy. */

        /* From read. */
        this.fromRead.forEach(fr -> nEvent.fromRead.add(fr.deepCopy(memo)));
        this.fromReadBy.forEach(frb -> nEvent.fromReadBy.add(frb.deepCopy(memo)));

        nEvent.inNode = this.inNode == null ? null : this.inNode.deepCopy(memo);

        return nEvent;
    }

    public SharedEvent getReadFrom() {
        return readFrom;
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

    public void setMoBefore(SharedEvent mb) {
        assert mb != null;
        assert moBefore != mb && mb.moAfter != this :
                "It's not allowed to add a new mo relation when there has been one!";
        moBefore = mb;
        mb.moAfter = this;
        OGNode mbNode = mb.getInNode();
        if (!inNode.moBefore(mbNode))
            inNode.setMoBefore(mbNode);
        if (!mbNode.moAfter(inNode))
            mbNode.setMoAfter(inNode);
    }

    public SharedEvent getMoAfter() {
        return moAfter;
    }

    public void setMoAfter(SharedEvent ma) {
        assert ma != null;
        assert moAfter != ma && ma.moBefore != this :
                "It's not allowed to add a new mo relation when there has been one!";
        moAfter = ma;
        ma.moBefore = this;
        OGNode maNode = ma.getInNode();
        if (!inNode.moAfter(maNode))
            inNode.setMoAfter(maNode);
        if (!maNode.moBefore(inNode))
            maNode.setMoBefore(inNode);
    }

    public void setFromRead(SharedEvent fr) {
        assert fr != null;
        assert !fr.fromReadBy.contains(this) && !fromRead.contains(fr) :
                "It's not allowed to add a new fr relation when there has been one!";
        fromRead.add(fr);
        fr.fromReadBy.add(this);
        OGNode frNode = fr.getInNode();
        if (!inNode.fromRead(frNode))
            inNode.setFromRead(frNode);
        if (!frNode.fromReadBy(inNode))
            frNode.setFromReadBy(inNode);
    }

    public void setFromReadBy(SharedEvent frb) {
        assert frb != null;
        assert !frb.fromRead.contains(this) && !fromReadBy.contains(frb) :
                "It's not allowed to add a new fr relation when there has been one!";
        fromReadBy.add(frb);
        frb.fromRead.add(this);
        OGNode frbNode = frb.getInNode();
        if (!inNode.fromReadBy(frbNode))
            inNode.setFromReadBy(frbNode);
        if (!frbNode.fromRead(inNode))
            frbNode.setFromRead(inNode);
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
    public void setInEdge(CFAEdge pEdge) { this.inEdge = pEdge; }


    public boolean accessSameVarWith(SharedEvent other) {
        return this.var.getName().equals(other.var.getName());
    }

    public boolean isRead() {
        return aType == AccessType.READ;
    }

    public boolean isWrite() {
        return aType == AccessType.WRITE;
    }

    public boolean inSameEdgeWith(SharedEvent e) {
        if (e == null) return false;
        return Objects.equals(inEdge, e.inEdge);
    }

    /**
     * Transfer relations (mo, rb, frb, etc.) to {@param e}. This will happen when
     * {@param e} covers this as a new write event in {@link OGNode#addEvents(List)}.
     * @param e The new write event that will cover this.
     */
    public void transferRelationsTo(SharedEvent e) {
        assert this.isWrite() && e.isWrite();
        // Mo.
        if (moAfter != null) {
            SharedEvent tmp = moAfter;
            moAfter.removeMoBefore(); // This will let moAfter = null;
            e.setMoAfter(tmp);
        }
        if (moBefore != null) {
            SharedEvent tmp = moBefore;
            moBefore.removeMoAfter();
            e.setMoBefore(tmp);
        }

        // Rb.
        List<SharedEvent> removedRbs = new ArrayList<>(readBy);
        removedRbs.forEach(rb -> {
            removeReadBy(rb);
            e.setReadBy(rb);
        });
        assert readBy.isEmpty() : "Some Read-By relations remained.";

        // Frb.
        List<SharedEvent> removedFrbs = new ArrayList<>(fromReadBy);
        removedFrbs.forEach(frb -> {
            removeFromReadBy(frb);
            e.setFromReadBy(frb);
        });
        assert fromReadBy.isEmpty() : "Some From-Read-By relations remained.";
    }

    public boolean lessThan(SharedEvent e) {
        assert inNode == e.inNode;
        return inNode.getEvents().indexOf(this) < inNode.getEvents().indexOf(e);
    }
}