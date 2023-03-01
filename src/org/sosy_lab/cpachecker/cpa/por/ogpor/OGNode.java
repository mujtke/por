package org.sosy_lab.cpachecker.cpa.por.ogpor;

import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

import java.util.ArrayList;
import java.util.List;

public class OGNode {

    private final List<event> events;

    public OGNode () {
        events = new ArrayList<event>();
    }

   private static class event {

       private Var var;

       // access type

       private Object readFrom;

       private Object readBy;

       // thread status: <parent_idNum, self_thread_idNum, NO.x_in_selfThread>.
       public Triple<Integer, Integer, Integer> threadStatus;

       public event (
               Var pVar,
               Object pReadFrom,
               Object pReadBy,
               Triple<Integer, Integer, Integer> pThreadStatus
       ) {
           this.var = pVar;
           this.readFrom = pReadFrom;
           this.readBy = pReadBy;
           this.threadStatus = pThreadStatus;
       }
   }
}
