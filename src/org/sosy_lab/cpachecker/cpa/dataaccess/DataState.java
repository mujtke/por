package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.ArrayList;
import java.util.List;

public class DataState {
    /**
     * 数据访问集中存放的类型及相关方法
     */
    private String N;
    private List<State> A;

    public DataState(){

    }

    public DataState(String Name) {
        /* 初始化 DataAccess[var]=[] */
        N = Name;
        A = new ArrayList<State>();
    }

    public DataState(String Name, State e) {
        N = Name;
        A = new ArrayList<State>();
        A.add(e);
    }

    public DataState(String N, List<State> A) {
        this.N = N;
        this.A = new ArrayList<>(A);
    }


    public String getN() {
        return N;
    }

    public void setN(String n) {
        N = n;
    }

    public List<State> getA() {
        return A;
    }

    public void setA(List<State> a) {
        A = a;
    }



    public boolean isempty() {
        return N.isEmpty() && A.isEmpty();
    }

    public void append(State e) {
        A.add(e);
    }

    public int length() {
        return A.size();
    }

    public void delActionlist(int idx) {
        for (int i = idx, len = A.size(); i < len; i++) {
            A.remove(i);
            len--;
            i--;
        }
    }

    public void setAll(DataState e) {
        N = e.N;
        A = e.A;
    }

    public void getEmpty() {
        A = new ArrayList<State>();
    }

    public void UpdateDataAccess(Integer ep_position, State ec, Integer flag, String mainFunction) {
        /**
         * 更新数据访问集 DataAccess
         * 更新数据访问集 DataAccess
         * @param ActionList 数据访问集
         * @param ep_position ep的位置
         * @param ec 当前状态
         * @param flag 标志当前产生的数据冲突种类
         */


        // 未跳出中断，仍在执行中断，或在主函数中
        if (flag == 2) {
            if (ec.getTask() == mainFunction) {
                // 删除： 中断内的所有操作 + 状态 ep
                A = new ArrayList<State>();
                this.append(ec);
            } else {
                this.append(ec);
            }
        }

        // 跳出了中断，但未产生数据冲突
        else if (flag == 1) {
            State ep = A.get(ep_position);
            if (ep.getTask() == mainFunction) {
                // 删除： 中断内的所有操作 + 状态 ep
                this.delActionlist(ep_position);
                this.append(ec);
            } else {
                if ((ep.getAction() != ec.getAction()) || (ep.getAction() == ec.getAction() && ep.getAction() == "W")) {
                    this.delActionlist(ep_position + 1);
                    this.append(ec);
                } else {
                    this.delActionlist(ep_position);
                    this.append(ec);
                }
            }
        }

        // 产生了数据冲突
        else if (flag == 0) {
            this.getEmpty();
        }

    }

    public Interruptinformation get_ep(State ec,List<String> involvedPaths) {
        /**
         * 得到 ep 的位置
         * @param ActionList 与 ec 同名的共享变量之前的所有操作集合
         * @param Name 被操作的共享变量所在的函数位置
         * @return ans = {ep_osition, inter_operation, inter_state}.
         */
        State er = A.get(A.size() - 1);    // 默认 er 为 ActionList 的最后一个

        Interruptinformation ans = new Interruptinformation();

        // 倒序搜索 ep
        for (int i = A.size() - 1; i >= 0; i--) {
            State ep = A.get(i);

            // 找到了 ep
            if (!(ep.getTask().contains("isr"))) {
                if(ans.inter_state.isEmpty()){
                    continue;
                }
                ans.setEp_position(i);
                return ans;
            }

            // 没找到，但找到了中断中对共享变量的其它操作
            if((involvedPaths.contains(ep.getTask()) || !(ans.getInter_location().contains(ep.getTask())))&& ep.getTask().contains("isr")){
                ans.setInter_location(ep.getTask());
                ans.setInter_operation(ep.getAction());
                ans.setInter_state(ep);
            }
        }

        return ans;
    }


    @Override
    public String toString() {
        return "\n" +
                N + ':' + toprint() +
                '}';
    }

    public String toprint(){
        StringBuffer str = new StringBuffer();
        for(int i=0;i<A.size();i++){
            if(i==0){
                str.append(A.get(i));
                str.append("\n");
                continue;
            }
            str.append("                               "+A.get(i));
            str.append("\n");
        }
        return String.valueOf(str);
    }

    public boolean isSame(DataState data){
        String str1 = data.toString();
        String str2 = this.toString();

        if(str1.equals(str2)){
            return true;
        }
        return false;
    }
}
