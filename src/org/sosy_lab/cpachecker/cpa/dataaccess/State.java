package org.sosy_lab.cpachecker.cpa.dataaccess;

public class State {
    /**
     * 每一个节点应包含的状态
     */
    private String Name;
    private String Task;
    private int Loaction;

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getTask() {
        return Task;
    }

    public void setTask(String task) {
        Task = task;
    }

    public int getLoaction() {
        return Loaction;
    }

    public void setLoaction(int loaction) {
        Loaction = loaction;
    }

    public String getAction() {
        return Action;
    }

    public void setAction(String action) {
        Action = action;
    }

    private String Action;

    public State() {
    }

    public State(String name, String task, int loaction, String action) {
        Name = name;
        Task = task;
        Loaction = loaction;
        Action = action;
    }

    @Override
    public String toString() {
        return "(" + Name + "," + Task + ", '" + Loaction + ", '" + Action + ')';
    }



}
