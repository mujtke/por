package org.sosy_lab.cpachecker.util.obsgraph;

import java.util.Collection;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

public class OutputInfo {

    private static final String argDotFile = "./output/infoArg.dot";
    private final OutputInfo instance;
    
    public OutputInfo getInstance() {
        return this.instance;
    }

    public OutputInfo() {
        this.instance = this;
    }

    public void printArgDot(ARGState parent, Collection<? extends AbstractState> children) {
        
    }
}