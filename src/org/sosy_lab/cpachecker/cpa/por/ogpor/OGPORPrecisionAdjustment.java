
package org.sosy_lab.cpachecker.cpa.por.ogpor;

import com.google.common.base.Function;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.obsgraph.*;

import java.util.*;

public class OGPORPrecisionAdjustment implements PrecisionAdjustment {

    private final LogManager logger;

    private final Map<Integer, List<ObsGraph>> OGMap;
    private final Map<Integer, OGNode> nodeMap;
    private final OGTransfer transfer;

    public OGPORPrecisionAdjustment(LogManager pLogger) {
        logger = pLogger;
        OGMap = GlobalInfo.getInstance().getOgInfo().getOGMap();
        nodeMap = GlobalInfo.getInstance().getOgInfo().getNodeMap();
        assert OGMap != null && nodeMap != null;
        this.transfer = GlobalInfo.getInstance().getOgInfo().getTransfer();
    }

    @Override
    public Optional<PrecisionAdjustmentResult> prec(
            AbstractState state,
            Precision precision,
            UnmodifiableReachedSet reachedSet,
            Function<AbstractState, AbstractState> stateProjection,
            AbstractState fullState) throws CPAException, InterruptedException {

        assert fullState instanceof ARGState;
        ARGState chState =  (ARGState) fullState;
        // Only one parent exists is required in OG based algorithm.
        assert chState.getParents().size() == 1;
        ARGState parState = chState.getParents().iterator().next();

        CFAEdge edge = parState.getEdgeToChild(chState);
        assert edge != null;

        OGNode inNode = nodeMap.get(edge.hashCode());

        OGPORState parOGState = AbstractStates.extractStateByType(parState,
                OGPORState.class),
                chOGState = (OGPORState) state;
        assert parOGState != null;
        // Set inThread and threadLoc for inNode.
        // This will change the value of 'inThread' and 'inThread' for OGNode in
        // nodeMap, is it fine to do so? Although we update them whenever we meet
        // a node. TODO.
        if (inNode != null) {
            // For complex node, set thread loc info only when we meet the start edge.
            if (inNode.isSimpleNode() || edge.equals(inNode.getBlockStartEdge())) {
                inNode.setInThread(chOGState.getInThread());
                inNode.setThreadsLoc(chOGState.getThreads());
                // For coNodes of the inNode, we set their thread locations, too.
                for (OGNode coNode : inNode.getCoNodes().values()) {
                    coNode.setInThread(chOGState.getInThread());
                    coNode.setThreadsLoc(chOGState.getThreads());
                }
            }
        }

        // Here, set the num for chOGState.
        chOGState.setNum(chState.getStateId());

        return Optional.of(PrecisionAdjustmentResult.create(state,
                precision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
}