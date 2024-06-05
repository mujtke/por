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
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;
import org.sosy_lab.cpachecker.util.obsgraph.SharedEvent;

import java.util.*;

@Options(prefix = "utils.globalInfo.OGInfo")
public class OGInfo {

    /**
     * Store the states num and list<og>. One state may own more than one og,
     * so we use list to store them.
     */
    private static Map<Integer, List<ObsGraph>> OGMap;

    // For debugging.
    // StateId -> [ (graphId, graphStr), ... ]
    private static Map<Integer, List<Pair<Integer, String>>> fullOGMap;
    // StateId -> { graphId -> graphStr (produced in revisit) }
    private static Map<Integer, Map<Integer, List<String>>> revisitOGMap;

    private static OGTransfer transfer;

    private static OGRevisitor revisitor;

    // <next table.
    private final HashMap<Integer, Integer> nlt;

    public HashMap<Integer, List<SharedEvent>> getEdgeVarMap() {
        return edgeVarMap;
    }

    // edge-sharedVars map.
    private HashMap<Integer, List<SharedEvent>> edgeVarMap;

    @Option(secure = true,
            description = "this option is enabled iff we use OGPORCPA.")
    private boolean useOG = false;

    @Option(secure = true,
    description = "switch for debugging.")
    private boolean enableDebug = false;

    public OGInfo(final Configuration pConfig,
                  final ConfigurableProgramAnalysis pCpa,
                  final CFA pCfa,
                  final LogManager pLogger)
            throws InvalidConfigurationException {
        pConfig.inject(this);
        if (useOG) {
            OGMap = new HashMap<>();
            // Put an empty graph into the first state.
            OGMap.put(0, new ArrayList<>(Collections.singleton(new ObsGraph())));
            edgeVarMap = new HashMap<>();
            fullOGMap = new HashMap<>();
            revisitOGMap = new HashMap<>();
            transfer = new OGTransfer(OGMap, edgeVarMap, enableDebug);
            revisitor = new OGRevisitor(pConfig, pCfa, pLogger, enableDebug);
            nlt = new HashMap<>();
        } else {
            OGMap = null;
            nlt = null;
        }
    }

    public Map<Integer, List<ObsGraph>> getOGMap() {
        return OGMap;
    }

    public OGTransfer getTransfer() {
        return transfer;
    }

    public OGRevisitor getRevisitor() {
       return revisitor;
    }

    public Map<Integer, List<Pair<Integer, String>>> getFullOGMap() {
        return fullOGMap;
    }

    public Map<Integer, Map<Integer, List<String>>> getRevisitOGMap() {
        return revisitOGMap;
    }
    public HashMap<Integer, Integer> getNlt() {
        return nlt;
    }

    public boolean isEnableDebug() {
        return enableDebug;
    }
}