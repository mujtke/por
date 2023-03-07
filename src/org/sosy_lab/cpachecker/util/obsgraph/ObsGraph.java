package org.sosy_lab.cpachecker.util.obsgraph;

import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.*;

public class ObsGraph {

    private final List<OGNode> nodes;

    public ObsGraph() {
        nodes = new ArrayList<OGNode>();
    }

    public ObsGraph(final List<OGNode> pCopy) {
        nodes = new ArrayList<OGNode>();
        nodes.addAll(pCopy);
    }

    /**
     * Add the new OGNode to the graph (this), and set the order relation for the events in the
     * new OGNode {@param pNewNode}.
     * @param pNewNode
     */
    public void addNewNode(OGNode pNewNode) {

        nodes.add(pNewNode);
//        // debug
//        if (true) return;

        // update the order relation at the same time.
        // a OGNode may have many events, but there is a write event at most, i.e.,
        // W = R1 + R2 + R3 + R4 ....
        Set<SharedEvent> REvents = new HashSet<>(), WEvents = new HashSet<>();
        pNewNode.getEvents().forEach(e -> {
            switch (e.getAType()) {
                case READ:
                    REvents.add(e);
                case WRITE:
                    WEvents.add(e);
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
            preEvent.setPoAfter(poBeforeFirst);
            preEvent.setReadFrom(readByFirst);
            // after that, set the poBefore objects for other read events, and set the w event
            // poAfter the last read event.
            while (it.hasNext()) {
                preEvent = sucEvent;
                sucEvent = it.next();
                sucEvent.setPoAfter(preEvent);
                preEvent.setPoBefore(sucEvent);
            }
            if (wEvent != null) {
                wEvent.setPoAfter(sucEvent);
            }
        } else {
            if (wEvent != null) {
                SharedEvent poBeforeWEvent = searchPoBefore(wEvent, pNewNode.getThreadStatus());
                if (poBeforeWEvent != null) {
                    wEvent.setPoAfter(poBeforeWEvent);
                    poBeforeWEvent.setPoBefore(wEvent);
                }
            }
        }
    }

    // search for the shared event that poBefore 'e'
    private SharedEvent searchPoBefore(SharedEvent e, Triple<Integer, Integer, Integer> ets) {

        SharedEvent poBeforeE;
        for (int i = nodes.size() - 2; i >= 0; i--) {
            OGNode n = nodes.get(i);
            Triple<Integer, Integer, Integer> nts = n.getThreadStatus();
            if ((ets.getFirst() == nts.getFirst())
                    && (ets.getSecond() == nts.getSecond()
                    && (ets.getThird() == nts.getThird() + 1))) {
                poBeforeE = n.getEvents().get(n.getEventsNum() - 1);
                return poBeforeE;
            }

        }

        return null;
    }

    // search for the shared event that readBy 'e'.
    private SharedEvent searchReadFrom(SharedEvent e) {

        SharedEvent readByE;
        for (int i = nodes.size() - 2; i >= 0; i--) {
            OGNode n = nodes.get(i);
            List<SharedEvent> eventsInN = n.getEvents();
            for (int j = n.getEventsNum() - 1; j >= 0; j--) {
                if (eventsInN.get(j).getAType().equals(SharedEvent.AccessType.WRITE)) {
                    readByE = eventsInN.get(j);
                    return readByE;
                }
            }
        }

        return null;
    }

    public int contain(OGNode ogNode) {
        int index = nodes.indexOf(ogNode);
        return index;
    }

    public OGNode get(OGNode ogNode) {
        int index = this.contain(ogNode);
        return index == -1 ? null : nodes.get(index);
    }

    /**
     * Detect whether the {@param ogNode} conflicts with the Graph (this).
     * @param ogNode
     * @param threadStatus the total threadStatus (include all existing threads) when
     *                     the {@param ogNode} is produced.
     * @return
     * @implNote this implementation doesn't consider the block factor.
     */
    public boolean hasConflict(OGNode ogNode,
                               Collection<Triple<Integer, Integer, Integer>> threadStatus) {
        boolean hasConflict = false;
        for (SharedEvent e : ogNode.getEvents()) {
            SharedEvent.AccessType aType = e.getAType();
            switch (aType) {
                case WRITE:
                    hasConflict = wConflict(e, ogNode, threadStatus);
                    break;
                case READ:
                    hasConflict = rConflict(e, ogNode, threadStatus);
                    break;
                case UNKNOWN:
                default:
                    throw new AssertionError("event in OGNode has a known access type is "
                            + "not allowed: " + e);
            }
        }
        return hasConflict;
    }

    private boolean wConflict(SharedEvent event, OGNode ogNode,
                              Collection<Triple<Integer, Integer, Integer>> threadStatus) {
        return false;
    }

    private boolean rConflict(SharedEvent event, OGNode ogNode,
                              Collection<Triple<Integer, Integer, Integer>> threadStatus) {
        return false;
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
