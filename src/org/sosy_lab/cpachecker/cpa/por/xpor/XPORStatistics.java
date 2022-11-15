package org.sosy_lab.cpachecker.cpa.por.xpor;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

import java.io.PrintStream;

public class XPORStatistics implements Statistics {

    // timer
    final StatTimer xporComputeDepTimer = new StatTimer("XPOR dependency compute time");
    final StatTimer xporUpdateThreadIdNumTimer = new StatTimer("XPOR update threadIdNum time");
    final StatTimer xporSelectValidEdgeTimer = new StatTimer("XPOR select valid edge time");

    // counter.
    final StatCounter depComputeTimes = new StatCounter("XPOR dependency check times");
    final StatCounter depConstraintsEntailTimes = new StatCounter("XPOR constraint entail times");
    final StatCounter depConstraintsNotEntailTimes =
            new StatCounter("XPOR constraint not entail times");
    final StatCounter depConstraintsOtherCaseTimes =
            new StatCounter("XPOR constraints unknown times");

    final StatCounter checkSkipTimes = new StatCounter("XPOR check skip times");
    final StatCounter checkSkipUnDepTimes =
            new StatCounter("XPOR check-skip unconditional dependent times");
    final StatCounter checkSkipUnIndepTimes =
            new StatCounter("XPOR check-skip unconditional independent times");
    final StatCounter checkSkipCondDepTimes =
            new StatCounter("XPOR check-skip conditional dependent times");
    final StatCounter checkSkipCondIndepTimes =
            new StatCounter("XPOR check-skip conditional independent times");
    final StatCounter checkSkipOtherCaseTimes =
            new StatCounter("XPOR check-skip failed times (other cases)");
    final StatCounter realRedundantTimes = new StatCounter("XPOR real redundant times");
    final StatCounter avoidExplorationTimes = new StatCounter("XPOR avoid exploration times");

    @Override
    public void printStatistics(PrintStream out, CPAcheckerResult.Result result, UnmodifiableReachedSet reached) {

        out.println(
                "XPOR dependency computation overhead: ("
                        + xporComputeDepTimer.getConsumedTime()
                        + ", "
                        + depComputeTimes.getUpdateCount()
                        + ")");
        out.println("XPOR constraint entailment information: ");
        out.println("   Entail:        " + depConstraintsEntailTimes.getUpdateCount());
        out.println("   Not Entail:    " + depConstraintsNotEntailTimes.getUpdateCount());
        out.println("   Other Cases:   " + depConstraintsOtherCaseTimes.getUpdateCount());
        out.println("XPOR check skip information: ");
        out.println(
                "   Check Times:                                       "
                        + checkSkipTimes.getUpdateCount());
        out.println(
                "   Unconditional Dependent Times:                     "
                        + checkSkipUnDepTimes.getUpdateCount());
        out.println(
                "   Unconditional Independent Times:                   "
                        + checkSkipUnIndepTimes.getUpdateCount());
        out.println(
                "   Conditional Dependent Times:                       "
                        + checkSkipCondDepTimes.getUpdateCount());
        out.println(
                "   Conditional Independent Times:                     "
                        + checkSkipCondIndepTimes.getUpdateCount());
        out.println(
                "   Other Cases (loop start or thread creation) Times: "
                        + checkSkipOtherCaseTimes.getUpdateCount());
        out.println("XPOR avoid exploration information: ");
        out.println(
                "   Avoid Exploration Total Times:             "
                        + avoidExplorationTimes.getUpdateCount());
        out.println(
                "   Real Redundant (Constraint Computation):   "
                        + realRedundantTimes.getUpdateCount());
        out.println("XPOR additional overheads:");
        out.println(
                "   XPOR update threadIdNum time: "
                + xporUpdateThreadIdNumTimer.getConsumedTime());
        out.println(
                "   XPOR select valid edge time: "
                + xporSelectValidEdgeTimer.getConsumedTime());
    }

    @Override
    public @Nullable String getName() {
        return "XPORCPA";
    }
}
