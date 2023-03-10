package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.cpa.por.ogpor.OGPORState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;

public class ObsGraph {

    private final List<OGNode> nodes;

    private OGNode lastNode = null; // lastNode doesn't always equal to the last element of nodes.

    private boolean needToRevisit = false;

    public ObsGraph() {
        nodes = new ArrayList<>();
    }

    public ObsGraph(final ObsGraph other) {
        nodes = new ArrayList<>(other.getNodes());
    }

    public List<OGNode> getNodes() {
        return nodes;
    }

    public void setNeedToRevisit(boolean v) {
        this.needToRevisit = v;
    }

    public boolean needToRevisit() {
        return this.needToRevisit;
    }

    public void setLastNode(OGNode lastNode) {
        this.lastNode = lastNode;
    }

    public OGNode getLastNode() {
        return lastNode;
    }

    /**
     * Add the new OGNode to the graph (this), and set the order relation for the events in the
     * new OGNode {@param pNewNode}.
     * @param pNewNode
     */
    public void addNewNode(OGNode pNewNode) {

        nodes.add(pNewNode);

        // 1. set the predecessor and 'numInTrace' for pNewNode
        // && update the last node of the graph (this).
        pNewNode.setPredecessor(lastNode);
        int numInTrace = 1; // pNewNode could be the first OGNode in a trace.
        if (lastNode != null) {
            lastNode.setSuccessor(pNewNode);
            numInTrace = lastNode.getNumInTrace() + 1;
        }
        pNewNode.setNumInTrace(numInTrace);
        lastNode = pNewNode;

        // 2. update the order relation at the same time.
        // a OGNode may have many events, but there is a write event at most, i.e.,
        // W = R1 + R2 + R3 + R4 ....
        Set<SharedEvent> REvents = new HashSet<>(), WEvents = new HashSet<>();
        pNewNode.getEvents().forEach(e -> {
            switch (e.getAType()) {
                case READ:
                    REvents.add(e);
                    break;
                case WRITE:
                    WEvents.add(e);
                    break;
                case UNKNOWN:
                default:
            }
        });
        assert WEvents.size() <= 1 : "more than one write events in an edge is not support now!";
        SharedEvent wEvent = WEvents.size() > 0 ? WEvents.iterator().next() : null;
        if (!REvents.isEmpty()) {
            Iterator<SharedEvent> it = REvents.iterator();
            SharedEvent preEvent = it.next(), sucEvent = preEvent;
            // search forward, set the poBefore and readFrom objects of the firstEvent.
            SharedEvent poBeforeFirst = searchPoBefore(preEvent, pNewNode.getThreadStatus()),
                    readByFirst = searchReadFrom(preEvent);
            if (poBeforeFirst != null) {
                preEvent.setPoAfter(poBeforeFirst);
                poBeforeFirst.poBeforeAdd(preEvent);
            }
            if (readByFirst != null) {
                preEvent.setReadFrom(readByFirst);
                readByFirst.readByAdd(preEvent);
            }

            // after that, set the poBefore objects for the left read events, and set the w event
            // poAfter the last read event.
            while (it.hasNext()) {
                preEvent = sucEvent;
                sucEvent = it.next();
                sucEvent.setPoAfter(preEvent);
                preEvent.poBeforeAdd(sucEvent);
            }
            if (wEvent != null) {
                wEvent.setPoAfter(sucEvent);
                sucEvent.poBeforeAdd(wEvent);
            }
        } else { // no R events.
            if (wEvent != null) {
                SharedEvent poBeforeWEvent = searchPoBefore(wEvent, pNewNode.getThreadStatus());
                if (poBeforeWEvent != null) {
                    wEvent.setPoAfter(poBeforeWEvent);
                    poBeforeWEvent.poBeforeAdd(wEvent);
                }
            }
        }
    }

    // search for the shared event that poBefore 'e'
    private SharedEvent searchPoBefore(SharedEvent e, Triple<Integer, Integer, Integer> ets) {
        assert ets.getFirst() != null && ets.getSecond() != null && ets.getThird() != null;

        SharedEvent poBeforeE;
        OGNode pre = e.getOgNode().getPredecessor();
        if (ets.getThird() == 1) {
            // the thread that e locates at is newly created.
            if (pre == null) { // which means e is the first node globally.
                return null;
            }
            int eParTid = ets.getFirst();
            while (pre != null) {
                assert pre.getThreadStatus().getSecond() != null;
                if (pre.getThreadStatus().getSecond() == eParTid)  {
                    // find the first ogNode that belongs to parent thread of e.
                    // return the last event.
                    poBeforeE = pre.getEvents().get(pre.getEventsNum() - 1);
                    return poBeforeE;
                }
                pre = pre.getPredecessor();
            }
        }

        // else, the thread that e in is not newly create.
        while (pre != null) {
            Triple<Integer, Integer, Integer> nts = pre.getThreadStatus();
            assert nts.getFirst() != null && nts.getSecond() != null && nts.getThird() != null;
            if ((ets.getFirst().intValue() == nts.getFirst().intValue())
                    && (ets.getSecond().intValue() == nts.getSecond().intValue()
                    && (ets.getThird() > nts.getThird()))) {
                // Note: there may be some non-shared events between ets and nts, so ets.getThird()
                // may be more than 1 bigger than nts.getThird().
                poBeforeE = pre.getEvents().get(pre.getEventsNum() - 1);
                return poBeforeE;
            }
            pre = pre.getPredecessor();
        }

        return null;
    }

    // search for the shared event that readBy 'e'.
    private SharedEvent searchReadFrom(SharedEvent e) {

        OGPORState eOGPORState = AbstractStates.extractStateByType(e.getOgNode().getPreARGState(),
                OGPORState.class);
        assert eOGPORState != null;
        return (eOGPORState.getLastAccessTable() == null) ? null :
                eOGPORState.getLastAccessTable().get(e.getVar());
//        OGNode pre = e.getOgNode().getPredecessor();
//        while (pre != null) {
//            List<SharedEvent> eventsInPre = pre.getEvents();
//            for (int j = pre.getEventsNum() - 1; j >= 0; j--) {
//                SharedEvent je = eventsInPre.get(j);
//                if (je.getVar().equals(e.getVar())
//                && je.getAType().equals(SharedEvent.AccessType.WRITE)) {
//                    return je;
//                }
//            }
//            pre = pre.getPredecessor();
//        }
//        return null;
    }

    public int contain(OGNode ogNode) {
        int index = nodes.indexOf(ogNode);
        return index;
    }

    // if ogNode (N1) has a same node in G, then this method return that node (N2) in G.
    // N1 equals N2, but N1 is not in G.
    public OGNode get(OGNode ogNode) {
        int index = this.contain(ogNode);
        return index == -1 ? null : nodes.get(index);
    }

    /**
     * Detect whether the {@param nodeInG} conflicts with the Graph (this).
     * @return
     * @implNote the {@param nodeInG} must be in the Graph (this), or else we don't need to
     * detect the conflict.
     * TODO: this implementation hasn't consider the block factor yet.
     */
    public boolean hasConflict(OGNode nodeInG, OGPORState parState) {
        boolean hasConflict = false;
        for (SharedEvent e : nodeInG.getEvents()) {
            SharedEvent.AccessType aType = e.getAType();
            switch (aType) {
                case WRITE:
                    hasConflict = wConflict(e, nodeInG, parState);
                    break;
                case READ:
                    hasConflict = rConflict(e, nodeInG, parState);
                    break;
                case UNKNOWN:
                default:
                    throw new AssertionError("event in OGNode has a known access type is "
                            + "not allowed: " + e);
            }
        }
        return hasConflict;
    }

    private boolean wConflict(SharedEvent w0, OGNode ogNode, OGPORState parState) {

        // if 'w0 <--- r0' and r0 has happened before w0    ===> conflict.
        Var wVar = w0.getVar();
        List<SharedEvent> r0s = w0.getReadBy();
        for (SharedEvent r0 : r0s) {
            if (r0.traceBefore(w0)) {
                return true;
            }
        }
        // when 'lastW <--- r', r hasn't happened before w0 ===> conflict.
        SharedEvent lastW = parState.getLastAccessTable().get(wVar);
        for (SharedEvent r : lastW.getReadBy()) {
            if ((!r.traceBefore(w0))) {
                return true;
            }
        }

        return false;
    }

    private boolean rConflict(SharedEvent r0, OGNode ogNode, OGPORState parState) {

        // if 'r0 ---> w0' but 'lastW != w0'     ===> conflict.
        // if 'r0 ---> null' but 'lastW != null' ===> conflict.
        Var rVar = r0.getVar();
        SharedEvent w0 = r0.getReadFrom(), lastW =
                parState.getLastAccessTable().get(rVar);
        if ((w0 != null && !w0.equals(lastW)) || (w0 == null && lastW != null)) {
            return true;
        }

        return false;
    }

    public void setReadFrom4FR(int rNodePos, int rEventPos, int wNodePos, int wEventPos) {
        SharedEvent r = nodes.get(rNodePos).getEvents().get(rEventPos),
                w = nodes.get(wNodePos).getEvents().get(wEventPos), w0;
        w0 = r.getReadFrom();
        w0.getReadBy().remove(r);
        r.setReadFrom(w);
        w.getReadBy().add(r);
    }

    public void setReadFrom4BR(int rNodePos, int rEventPos, int wNodePos, int wEventPos,
                               List<OGNode> delete) {
        OGNode rNode = nodes.get(rNodePos), wNode = nodes.get(wNodePos);
        SharedEvent r = rNode.getEvents().get(rEventPos), w = wNode.getEvents().get(wEventPos);
        // in br, we need to 'delete' some nodes.
        for (OGNode pre = rNode, next = pre.getSuccessor(); !next.equals(wNode); next =
                next.getSuccessor()) {
            if (delete.contains(pre)) {
                next.setPredecessor(null);
                pre.setSuccessor(next.getSuccessor());
                next.setSuccessor(null);
//                next.setNumInTrace(-2); // -2 means the node regarded as not in graph.
                nodes.remove(next); // TODO: could we not remove the node.
                pre = pre.getSuccessor();
            }
        }
        SharedEvent w0 = r.getReadFrom();
        if (w0 != null) {
            w0.getReadBy().remove(r);
        }
        r.setReadFrom(w);
        w.getReadBy().add(r);
    }

    public void resetLastNode(int newLastNodeIndex) {
        // all nodes after newLastNode need to change their numInTrace and predecessor/successor.
        // newLastNodeIndex = -1 represents that all nodes need to change.
        newLastNodeIndex = Math.max(newLastNodeIndex, 0);
        lastNode = nodes.get(newLastNodeIndex);
        assert lastNode != null;
        OGNode cur = lastNode, next = lastNode.getSuccessor();
        while (next != null) {
            cur.setSuccessor(null);
            next.setPredecessor(null);
            next.setNumInTrace(-1);
            cur = next;
            next = next.getSuccessor();
        }
    }

    public SharedEvent searchNewWEvent(SharedEvent w1) {
        // backtrack to find a w2 that access the same var as w1.
        // we think that one OGNode just has one predecessor at most, but can have more than one
        // successor.
        SharedEvent w2 = null, tmp;
        boolean stop = false;
        OGNode preNode = w1.getOgNode().getPredecessor();
        while (preNode != null) {
            for (int i = preNode.getEventsNum() - 1; i >= 0; i--) {
                tmp = preNode.getEvents().get(i);
                if (tmp.getVar().equals(w1.getVar())
                        && tmp.getAType().equals(SharedEvent.AccessType.WRITE)) {
                    w2 = tmp;
                    stop = true;
                    break;
                }
            }
            if (stop) {
                break;
            }
            preNode = preNode.getPredecessor();
        }

        return w2;
    }

    public SharedEvent searchNewREvent(SharedEvent w) {

        SharedEvent r = null, tmp;
        boolean stop = false;
        OGNode preNode = w.getOgNode().getPredecessor();
        while (preNode != null) {
            for (int i = preNode.getEventsNum() - 1; i >= 0; i--) {
                tmp = preNode.getEvents().get(i);
                if (tmp.getVar().equals(w.getVar())
                        && tmp.getAType().equals(SharedEvent.AccessType.READ)) {
                    r = tmp;
                    stop = true;
                    break;
                }
            }
            if (stop) {
                break;
            }
            preNode = preNode.getPredecessor();
        }

        return r;
    }

    /**
     * run possible revisit processes for the graph (this).
     * @param ogNode
     * @implNote the revisit processes include "forward revisit" and "back revisit".
     */
    public void revisit(OGNode ogNode, Object ... table) {

        // forward revisit.

        // back revisit.

    }
}
