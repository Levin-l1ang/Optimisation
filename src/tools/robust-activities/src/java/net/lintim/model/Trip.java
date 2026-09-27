package net.lintim.model;

import java.util.ArrayList;

public class Trip {

    private ArrayList<Activity> listOfActivities;

    public Trip() {
        listOfActivities = new ArrayList<Activity>();
    }

    public Trip(ArrayList<Activity> listOfActivities) {
        this.listOfActivities = listOfActivities;
    }

    public void addActivityAt(Integer index, Activity activity) {
        listOfActivities.add(index, activity);
    }

    public void addActivity(Activity activity) {
        listOfActivities.add(activity);
    }

    public ArrayList<Activity> getListOfActivities() {
        return listOfActivities;
    }

    public void setListOfActivities(ArrayList<Activity> listOfActivities) {
        this.listOfActivities = listOfActivities;
    }

}
