package org.sosy_lab.cpachecker.util.globalinfo;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.util.obsgraph.OGNode;
import org.sosy_lab.cpachecker.util.obsgraph.OGNodeBuilder;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Options(prefix = "utils.globalInfo.OGInfo")
public class OGInfo {

    /**
     * biOGMap :: store the states num and list<og>. One state may own more than one og,
     * so we use list to store them.
     */
    private Map<Integer, List<ObsGraph>> OGMap;

    private Map<Integer, OGNode> nodeMap;

    private OGNodeBuilder nodeBuilder;

    @Option(secure = true,
            description = "this option is enabled iff we use OGPORCPA. When enabled")
    private boolean useOG = false;

    public OGInfo(final Configuration pConfig, final CFA pCfa)
            throws InvalidConfigurationException {
        pConfig.inject(this);
        if (useOG) {
            OGMap = new HashMap<>();
            nodeBuilder = new OGNodeBuilder(pConfig, pCfa);
            nodeMap = nodeBuilder.build();
        } else {
            OGMap = null;
            nodeMap = null;
        }
    }

    public Map<Integer, List<ObsGraph>> getOGMap() {
        return OGMap;
    }

    public Map<Integer, OGNode> getNodeMap() {
        return nodeMap;
    }
}
