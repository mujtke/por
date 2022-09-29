package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeVtx;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.globalinfo.EdgeInfo;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

public class DataAccessTransferRelation extends SingleEdgeTransferRelation {
    private EdgeInfo edgeInfo;   // 用于获取主函数名
    private ConditionalDepGraph conDepGraph;  // 用于获取边上信息


    public DataAccessTransferRelation() {
        conDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
        edgeInfo = GlobalInfo.getInstance().getEdgeInfo();
    }

    @Override
    public Collection<DataAccessState> getAbstractSuccessorsForEdge(AbstractState pstate, Precision precision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {
        /**
         * @param state 父节点的信息
         * @param precision 精度，这用不到
         * @param cfaEdge 边上的信6息
         */
        System.out.println("*************************************************************** begin this Race Test ***************************************************************");
        System.out.println(pCfaEdge);
        // 将父节点中的 Dataaccess 取出
        DataAccessState lastDataAccess = (DataAccessState) pstate;
        System.out.println(lastDataAccess);
        int line = pCfaEdge.getLineNumber();
        if(line == 27 || line == 43){
            System.out.println("in");
        }
        EdgeVtx edgeVtx = (EdgeVtx) conDepGraph.getDGNode(pCfaEdge.hashCode());
        // 如果边信息为空，则直接返回父节点信息
        if (edgeVtx == null) {
            lastDataAccess.setans("No share variable.\n\n");
            lastDataAccess.toprint();
            return Collections.singleton(lastDataAccess);
        }

        DataAccessState dataAccess = new DataAccessState(lastDataAccess.getDataAccess(), lastDataAccess.getDataRace(), lastDataAccess.getpathFunc(),lastDataAccess.getPathNum());

        String mainFunction = edgeInfo.getCFA().getMainFunction().getFunctionName();

        // 获取边上的共享节点的信息

        String pathNum = pCfaEdge.getFileLocation().toString();
        dataAccess.setPathNum(pathNum);

        // 得到读写信息
        Set<Var> gRVars = edgeVtx.getgReadVars(), gWVars = edgeVtx.getgWriteVars();

        // 得到边所在的函数名
        String task = edgeVtx.getBlockStartEdge().getPredecessor().getFunctionName();

        // 先判断读   因为对于任何一条语句， 无论怎样都是先读后写
        if (!gRVars.isEmpty()) {
            for (Var var : gRVars) {

                //如果变量已经被检测过了，则不在检测
                if (dataAccess.isInDataRace(var.getName())) continue;

                int location = var.getExp().getFileLocation().getEndingLineNumber();
                State ec = new State(var.getName(), task, location, "R");

                if (dataAccess.setPath(task)) { // 保证了，进入DataRace的一定是再path中已经存在函数，且不是最后一个
                    dataAccess.DataRace(ec, mainFunction);
                    dataAccess.setDataAccess(ec);
                    dataAccess.setans("add this state:" + ec);
//                    System.out.println(dataAccess.getAns());
                    return Collections.singleton(dataAccess);
                }

                // 进行数据冲突检测
                dataAccess.DataRace(ec, mainFunction);
            }


        }

        // 后判断写
        if (!gWVars.isEmpty()) {
            for (Var var : gWVars) {

                //如果变量已经被检测过了，则不在检测
                if (dataAccess.isInDataRace(var.getName())) continue;

                int location = var.getExp().getFileLocation().getEndingLineNumber();
                State ec = new State(var.getName(), task, location, "W");

                if (dataAccess.setPath(task)) {
                    dataAccess.setDataAccess(ec);
                    dataAccess.setans("add this state:" + ec);
//                    System.out.println(dataAccess.getAns());
                    return Collections.singleton(dataAccess);
                }

                // 进行数据冲突检测
                dataAccess.DataRace(ec, mainFunction);

            }
        }


//        System.out.println("***************************************************************end this test***************************************************************");
        return Collections.singleton(dataAccess);

    }
}
