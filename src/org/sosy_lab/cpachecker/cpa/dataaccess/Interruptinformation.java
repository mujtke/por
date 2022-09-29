package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.ArrayList;
import java.util.List;

public class Interruptinformation {
    public Integer ep_position;
    public List<String> inter_operation;
    public List<State> inter_state;

    private List<String> inter_location;


    public Interruptinformation() {
        this.ep_position = -1;
        this.inter_operation = new ArrayList<String>();
        this.inter_state = new ArrayList<State>();
        this.inter_location = new ArrayList<String>();
    }

    public Integer getEp_position() {
        return ep_position;
    }

    public void setEp_position(Integer ep_position) {
        this.ep_position = ep_position;
    }

    public List<String> getInter_operation() {
        return inter_operation;
    }

    public void setInter_operation(String inter_operation) {
        this.inter_operation.add(inter_operation);
    }

    public List<State> getInter_state() {
        return inter_state;
    }

    public void setInter_state(State inter_state) {
        this.inter_state.add(inter_state);
    }

    public List<String> getInter_location() {
        return inter_location;
    }

    public void setInter_location(String inter_location) {
        this.inter_location.add(inter_location);
    }

    public int getindexInter_operation(String Action) {
        for (int i=0;i<inter_operation.size();i++) {
            if (inter_operation.get(i) == Action) {
                return i;
            }
        }
        return -1;
    }
}
