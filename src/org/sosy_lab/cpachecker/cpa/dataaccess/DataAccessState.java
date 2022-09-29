package org.sosy_lab.cpachecker.cpa.dataaccess;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;

//
public class DataAccessState implements AbstractState, Graphable {
//public class DataAccessState implements AbstractState {
    private List<DataState> dataAccess;

    private List<DataState> dataRace;

    private List<String> pathFunc;

    private List<String> pathNum;

    private String ans;


    private boolean isRace = false;

    public boolean isRace() {
        return isRace;
    }

    public void setRace(boolean race) {
        isRace = race;
    }


    public String getAns() {
        return ans;
    }

// 构造方法

    public static DataAccessState getInitialInstance() {
        return new DataAccessState();
    }

    public DataAccessState() {
        dataAccess = new ArrayList<DataState>();
        dataRace = new ArrayList<DataState>();
        pathFunc = new ArrayList<String>();
        pathNum = new ArrayList<String>();
    }

    //
    public DataAccessState(List<DataState> dataAccess, List<DataState> dataRace, List<String> pathFunc, List<String> pathNum) {
        this.dataAccess = newData(dataAccess);
        this.dataRace = newData(dataRace);
        this.pathFunc = new ArrayList<>(pathFunc);
        this.pathNum = new ArrayList<>(pathNum);
    }

    public List<DataState> newData(List<DataState> dataAccess) {
        List<DataState> res = new ArrayList<DataState>();
        for (DataState tmp : dataAccess) {
            res.add(new DataState(tmp.getN(), tmp.getA()));
        }
        return res;
    }


    public void setans(String r) {
        ans = r;
    }

//    public DataAccessState(List<DataState> dataAccess, List<DataState> dataRace, List<String> path) {
//        this.dataAccess = dataAccess;
//        this.dataRace = dataRace;
//        this.path = path;
//    }

    // dataAccess的方法

    public List<DataState> getDataAccess() {
        return dataAccess;
    }

    public void setDataAccess(State e) {
        if (dataAccess.isEmpty()) {
            dataAccess.add(new DataState(e.getName(), e));
            return;
        }

        boolean notIsExists = true;
        for (DataState data : dataAccess) {
            if (data.getN() == e.getName()) {
                notIsExists = false;
                data.append(e);
            }
        }

        if (notIsExists) {
            dataAccess.add(new DataState(e.getName(), e));
            return;
        }
    }

    public void setDataAccess(DataState dataAccess) {
        this.dataAccess.get(actionListPosition(dataAccess.getN())).setAll(dataAccess);
    }

    public void add(DataState e) {
        dataAccess.add(e);
    }

    public int Is_exist(String Name) {
        /**
         * 判断当前 Name 是否在数据访问集中, 在返回序号，不在返回-1
         */

        for (int i = 0; i <= dataAccess.size(); i++) {
            if (dataAccess.get(i).getN() == Name) return i;
        }
        return -1;
    }

    public int actionListPosition(String Name) {
        /**
         * 判断当前 Name 是否在数据访问集中, 在返回序号，不在返回-1
         */

        for (int i = 0; i < dataAccess.size(); i++) {
            if (dataAccess.get(i).getN().equals(Name)) return i;
        }

        return -1;
    }

    // dataRace 的方法
    public List<DataState> getDataRace() {
        return dataRace;
    }

    public void setDataRace(List<DataState> dataRace) {
        this.dataRace = dataRace;
    }

    public boolean isInDataRace(String Name) {
        for (DataState data : dataRace) {
            if (data.getN() == Name) {
                return true;
            }
        }
        return false;
    }

    public void setDataRace(DataState race) {
        dataRace.add(race);
    }


    // path 的方法
    public List<String> getpathFunc() {
        return pathFunc;
    }

    public boolean setPath(String road) {

        if (pathFunc.isEmpty()) {
            pathFunc.add(road);
            return true;
        }


        if (pathFunc.get(pathFunc.size() - 1) == road) {
            return true;
        }

        if (pathFunc.contains(road)) {
            for (int i = 0; i < pathFunc.size(); i++) {
                if (pathFunc.get(i) == road) {
                    return false;
                }
            }
        }

        pathFunc.add(road);
        return true;
    }

    public List<String> getPathNum() {
        return pathNum;
    }

    public void setPathNum(String pathNum) {
        this.pathNum.add(pathNum);
    }

    public void poppath(int idx) {
        for (int i = idx + 1; i < pathFunc.size(); i++) {
            pathFunc.remove(i);
        }
    }

    public List<String> involvedPaths(String road) {
        List<String> re = new ArrayList<String>();
        for (int i = pathFunc.size() - 1; i >= 0; i--) {
            if (pathFunc.get(i) == road) {
                break;
            }
            re.add(pathFunc.get(i));
        }
        return re;
    }

    public void disposePath(State ec, String mainFunction) {
        if (ec.getTask() != mainFunction) return;

        int index = isExistPath(ec.getTask());
        if (index != -1) {
            poppath(index);
        }
    }

    public int isExistPath(String Task) {
        for (int i = pathFunc.size() - 2; i >= 0; i--) {
            if (pathFunc.get(i) == Task) {
                return i;
            }
        }

        return -1;
    }

    public void DataRace(State ec, String mainFunction) {
        /**
         * 数据冲突检测
         * @param ActionList 与 ec 同名的共享变量之前的所有操作集合
         * @param ec 当前状态 ec
         * @return 返回一个数据冲突对 (ep,er,ec)，或返回一个 null
         */

        int index = this.actionListPosition(ec.getName());

        if (index == -1) {
            DataState e = new DataState(ec.getName(), ec);
            ans = "add this state:" + ec;
            return;
        }

        DataState actionList = dataAccess.get(index);

        String[][] patternList = {{"R", "W", "R"}, {"W", "W", "R"}, {"R", "W", "W"}, {"W", "R", "W"}};    // 冲突模式集

        // 判断 变量ec.Name 的 DataAccess 的大小，小于2，直接加入
        if (actionList.getA().size() < 2) {
            actionList.append(ec);
//            System.out.println("\n             For share_var " + ec.getName() + ", not enough elements in DataAccess");
            ans = "\nFor share_var " + ec.getName() + ", not enough elements in DataAccess";

            return;
        }

        Interruptinformation get_race = actionList.get_ep(ec, involvedPaths(ec.getName()));   // 倒序搜索 ep
        if (get_race.getEp_position() == -1) {     // 没找到相应的 ep
//            System.out.println("\n             For state " + ec.toString() + ", the appropriate ep and er were not found, causing a data conflict with them");
            ans = "\nFor state " + ec.toString() + ", the appropriate ep and er were not found, causing a data conflict with them";
            actionList.UpdateDataAccess(get_race.ep_position, ec, 2, mainFunction);
            return;
        }

        // 找到了相应的 ep， 找出合适的 er， 查看是否会产生冲突
        for (int i = 0; i < get_race.getInter_operation().size(); i++) {
            State ep = actionList.getA().get(get_race.getEp_position());
            State er = get_race.getInter_state().get(i);

            String[] pattern = {ep.getAction(), er.getAction(), ec.getAction()};

            // 冲突检测，是否有 patternList 中的 pattern
            for (int j = 0; j < 4; j++) {
                if (Arrays.equals(patternList[j], pattern)) {
                    ans = "For the three access states {" + ep.toString() + "," + er.toString() + "," + ec.toString() + "} of the variable " + ep.getName() + " a data conflict occurs";
//                    System.out.println(ans);
                    actionList.UpdateDataAccess(get_race.ep_position, ec, 0, mainFunction);
//                    String[][] patternList = {{"R", "W", "R"}, {"W", "W", "R"}, {"R", "W", "W"}, {"W", "R", "W"}};    // 冲突模式集
                    isRace = true;
                    DataState race = new DataState(ep.getName(), ep);
                    race.append(er);
                    race.append(ec);
                    dataRace.add(race);

                    String str = race.toString();
                    if ( j == 0 ) {
                        DataAccessCPA.raceNum.setraceRWRSet(str);
                    } else if (j == 1) {
                        DataAccessCPA.raceNum.setraceWWRSet(str);
                    } else if (j == 2) {
                        DataAccessCPA.raceNum.setraceRWWSet(str);
                    } else if (j == 3) {
                        DataAccessCPA.raceNum.setraceWRWSet(str);
                    }
                    DataAccessCPA.raceNum.setRace(str);



                    return;
                }
            }
        }

        // 当都不发生数据冲突时
        ans = "\nFor state " + ec + ", we didn't find two states with which we could conflict with it\n";
        actionList.UpdateDataAccess(get_race.getEp_position(), ec, 1, mainFunction);
    }


    public void toprint() {
        if (!isRace) {
            System.out.println(ans);

//            System.out.println("\nNow the DataAccess is :");
//            System.out.println(getString());
            System.out.println("\n\n");
            return;
        }
        isRace = false;
        System.out.println("\n\033[31m" + ans);
        System.out.println("\nNow the DataAccess is :");
        System.out.println(getString());
        System.out.println("\n\n\033[0m");
    }


    public String getString() {
        return "DataAccess=" + dataAccess.toString() + ", \nDataRace=" + dataRace.toString() + ", \npathFunc = " + pathFunc.toString() + ", \npathNum = " + pathNum.toString() + "\n\n";
    }

    @Override
    public String toString() {
        return "DataAccessState{" + "dataAccess=" + dataAccess + ", dataRace=" + dataRace + ", pathFunc=" + pathFunc + ", pathNum=" + pathNum  + ", ans='" + ans + '\'' + ", isRace=" + isRace + '}';
    }

    @Override
    public String toDOTLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        sb.append(ans).append("\n").append(getString());
        sb.append(pathFunc).append("\n").append(pathNum);
        return sb.toString();
    }

    @Override
    public boolean shouldBeHighlighted() {
        if (ans.contains("three access states")) {
            return true;
        }
        return false;
    }

}


