package net.lintim.model;

import java.util.ArrayList;

public class Event {
    private Integer index;
    private String type;
    private Integer station;
    private Integer line;
    private Double passengers;

    public Event(Integer index, String type, Integer station, Integer line, Double passengers) {
        this.index = index;
        this.type = type;
        this.station = station;
        this.line = line;
        this.passengers = passengers;
    }

    public Event() {
    }

    public ArrayList<Activity> getIncomingActivities(ArrayList<Activity> listOfActivities) {
        ArrayList<Activity> listOfIncomingActivites = new ArrayList<Activity>();
        for (Activity activity : listOfActivities) {
            if (activity.getToEvent() == index) {
                listOfIncomingActivites.add(activity);
            }
        }
        return listOfIncomingActivites;
    }

    public ArrayList<Activity> getOutgoingActivities(ArrayList<Activity> listOfActivities) {
        ArrayList<Activity> listOfOutgoingActivites = new ArrayList<Activity>();
        for (Activity activity : listOfActivities) {
            if (activity.getFromEvent() == index) {
                listOfOutgoingActivites.add(activity);
            }
        }
        return listOfOutgoingActivites;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getStation() {
        return station;
    }

    public void setStation(Integer station) {
        this.station = station;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public Double getPassengers() {
        return passengers;
    }

    public void setPassengers(Double passengers) {
        this.passengers = passengers;
    }

}
