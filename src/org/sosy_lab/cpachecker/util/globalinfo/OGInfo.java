package org.sosy_lab.cpachecker.util.globalinfo;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.algorithm.og.OGRevisitor;
import org.sosy_lab.cpachecker.core.algorithm.og.OGTransfer;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.OGNodeBuilder;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Options(prefix = "utils.globalInfo.OGInfo")
public class OGInfo {

    /**
     * biOGMap :: store the states num and list<og>. One state may own more than one og,
     * so we use list to store them.
     */
    private static Map<Integer, List<ObsGraph>> OGMap;

    private static Map<Integer, OGNode> nodeMap;

    // For debugging.
    private static Map<Integer, List<String>> fullOGMap;

    private static OGTransfer transfer;

    private static OGRevisitor revisitor;

    private OGNodeBuilder nodeBuilder;

    // <next table.
    private final HashMap<Integer, Integer> nlt;
    // edge-sharedVars map.
    private static HashMap<Integer, List<SharedEvent>> edgeVarMap;

    @Option(secure = true,
            description = "this option is enabled iff we use OGPORCPA.")
    private boolean useOG = false;

    @Option(secure = true,
            description = "this option is enabled when we use nodeMap.")
    private boolean useNodeMap = false;

    @Option(secure = true,
            description = "extract shared variables for every cfa edge.")
    private boolean extractVarsForCFAEdge = true;

    public OGInfo(final Configuration pConfig,
                  final ConfigurableProgramAnalysis pCpa,
                  final CFA pCfa,
                  final LogManager pLogger)
            throws InvalidConfigurationException {
        pConfig.inject(this);
        if (useOG) {
            OGMap = new HashMap<>();

            if (useNodeMap) {
                nodeBuilder = new OGNodeBuilder(pConfig, pCfa);
                nodeMap = nodeBuilder.build();
            }

            if (extractVarsForCFAEdge) {
                edgeVarMap = new HashMap<>();
                nodeBuilder.buildEdgeVarMap(edgeVarMap);
            }

            fullOGMap = new HashMap<>();
            transfer = new OGTransfer(OGMap, nodeMap, edgeVarMap);
            revisitor = new OGRevisitor(OGMap, nodeMap, pConfig, pCfa, pLogger);
            nlt = new HashMap<>();
        } else {
            OGMap = null;
            nodeMap = null;
            nlt = null;
        }
    }

    public Map<Integer, List<ObsGraph>> getOGMap() {
        return OGMap;
    }

    public Map<Integer, OGNode> getNodeMap() {
        return nodeMap;
    }

    public OGTransfer getTransfer() {
        return transfer;
    }

    public OGRevisitor getRevisitor() {
       return revisitor;
    }

    public Map<Integer, List<String>> getFullOGMap() {
        return fullOGMap;
    }

    public HashMap<Integer, Integer> getNlt() {
        return nlt;
    }
}