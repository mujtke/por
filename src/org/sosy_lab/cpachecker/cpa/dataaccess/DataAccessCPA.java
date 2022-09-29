package org.sosy_lab.cpachecker.cpa.dataaccess;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.cpa.cintp.CIntpCPAStatistics;
import org.sosy_lab.cpachecker.cpa.cintp.CIntpStatistics;

import java.util.Collection;


public class DataAccessCPA extends AbstractCPA implements ConfigurableProgramAnalysis {

    public static RaceNum raceNum;

    public static CPAFactory factory() {
        return AutomaticCPAFactory.forType(DataAccessCPA.class);
    }

    public DataAccessCPA() {
        super("sep", "sep", new DataAccessTransferRelation());
        raceNum = new RaceNum();
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {
        return DataAccessState.getInitialInstance();
    }
}
