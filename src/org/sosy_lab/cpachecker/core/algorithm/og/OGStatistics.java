package org.sosy_lab.cpachecker.core.algorithm.og;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;

import java.io.PrintStream;

public class OGStatistics implements Statistics {
  final StatCounter ogCounter = new StatCounter("Number of Observing graphs");
  @Override
  public void printStatistics(PrintStream out, CPAcheckerResult.Result result, UnmodifiableReachedSet reached) {
    // TODO
    out.println("Number of OG:\t" + ogCounter.getUpdateCount());
  }

  @Override
  public @Nullable String getName() {
    return "OGAlgorithm general";
  }
}
