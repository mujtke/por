package org.sosy_lab.cpachecker.util.globalinfo;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.obsgraph.ObsGraph;

import java.util.List;

@Options(prefix = "utils.globalInfo.OGInfo")
public class OGInfo {

    /**
     * biOGMap :: store the states and list<og>. One state may own multiple ogs, so we use list to
     * store them.
     */
    private BiMap<AbstractState, List<ObsGraph>> biOGMap;

    @Option(
            secure = true,
            description = "this option is enabled iff we use OGPORCPA. When enabled, we build a " +
                    "biMap to store the relation between state and ObsGraphs"
    )
    private boolean useBiOGMap = false;

    public OGInfo(Configuration pConfig) throws InvalidConfigurationException {
        pConfig.inject(this);
        if (useBiOGMap) {
            biOGMap = HashBiMap.create();
        } else {
            biOGMap = null;
        }
    }

    public BiMap<AbstractState, List<ObsGraph>> getBiOGMap() {
        return biOGMap;
    }
}
