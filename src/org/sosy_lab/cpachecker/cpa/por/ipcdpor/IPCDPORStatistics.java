package org.sosy_lab.cpachecker.cpa.por.ipcdpor;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

import java.io.PrintStream;

public class IPCDPORStatistics implements Statistics {

    // timer
    final StatTimer ipcdporComputeDepTimer = new StatTimer("IPCDPOR dependency computation time");

    // counter
    final StatCounter depComputeTimes = new StatCounter("IPCDPOR dependency check times");
    final StatCounter depConstraintsEntailTimes = new StatCounter("IPCDPOR constraint entail times");
    final StatCounter depConstraintsNotEntailTimes = new StatCounter("IPCDPOR constraint entail times");
    final StatCounter depConstraintsOtherCaseTimes = new StatCounter("IPCDPOR constraint unknown times");
    final StatCounter checkSkipTimes = new StatCounter("IPCDPOR check skip times");
    final StatCounter checkSkipUnDepTimes = new StatCounter("IPCDPOR check-skip unconditionally dependent times");
    final StatCounter checkSkipUnIndepTimes = new StatCounter("IPCDPOR check-skip unconditionally independent times");
    final StatCounter checkSkipCondDepTimes = new StatCounter("IPCDPOR check-skip conditionally dependent times");
    final StatCounter checkSkipCondIndepTimes = new StatCounter("IPCDPOR check-skip conditionally independent times");
    final StatCounter checkSkipFailedTimes = new StatCounter("IPCDPOR check-skip failed times (other cases)");
    final StatCounter realRedundantTimes = new StatCounter("IPCDPOR real redundant times");
    final StatCounter avoidExplorationTimes = new StatCounter("IPCDPOR avoid exploration times");
    @Override
    public void printStatistics(PrintStream out, CPAcheckerResult.Result result, UnmodifiableReachedSet reached) {
        out.println(
                "IPCDPOR dependency computation overheads: ("
                + ipcdporComputeDepTimer.getConsumedTime()
                + ", "
                + depComputeTimes.getUpdateCount()
                + ")"
        );
        out.println("IPCDPOR constraint entailing information: ");
        out.println("\tEntail:         " + depConstraintsEntailTimes.getUpdateCount());
        out.println("\tNot Entail:     " + depConstraintsNotEntailTimes.getUpdateCount());
        out.println("\tOther Cases:    " + depConstraintsOtherCaseTimes.getUpdateCount());
        out.println("IPCDPOR check skip information: ");
        out.println("\tCheck Times:                        " + checkSkipTimes.getUpdateCount());
        out.println("\tUnconditionally Dependent Times:    " + checkSkipUnDepTimes.getUpdateCount());
        out.println("\tUnconditionally Independent Times:  " + checkSkipUnIndepTimes.getUpdateCount());
        out.println("\tConditionally Dependent Times:      " + checkSkipCondDepTimes.getUpdateCount());
        out.println("\tConditionally Independent Times:    " + checkSkipCondIndepTimes.getUpdateCount());
        out.println("\tOther Cases (loop start or thread creation) Times: " + checkSkipFailedTimes.getUpdateCount());

        out.println("Avoid Exploration Total Times: " + avoidExplorationTimes.getUpdateCount());
        out.println("Real Redundant (By Constraint Computation): " + realRedundantTimes.getUpdateCount());
    }

    @Override
    public @Nullable String getName() {
        return "IPCDPORCPA";
    }
}
