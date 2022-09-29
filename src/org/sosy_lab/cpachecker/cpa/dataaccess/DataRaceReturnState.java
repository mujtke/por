package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.ArrayList;
import java.util.List;

public class DataRaceReturnState {
    public DataState Actionlist;
    public DataState race;

    public DataRaceReturnState() {
    }
    public DataRaceReturnState(DataState actionlist) {
        Actionlist = actionlist;
        race = null;
    }

    public DataRaceReturnState(DataState actionlist, DataState race) {
        Actionlist = actionlist;
        this.race = race;
    }

    public DataState getActionlist() {
        return Actionlist;
    }

    public DataState getRace() {
        return race;
    }

    @Override
    public String toString() {
        return "DataRaceReturnState{" +
                "Actionlist=" + Actionlist.toString() +
                ", race=" + race.toString() +
                '}';
    }
}
