package net.lintim.model;

/**
 * A class representing an periodic headway activity, i.e., an arc in the periodic event activity network (EAN) that
 * represents a headway.
 */
public class PeriodicHeadway extends PeriodicActivity {

    protected PeriodicHeadway correspondingHeadway;

    /**
     * Create a new headway activity, i.e., an arc in the event activity network that is a headway. The corresponding
     * headway activity can be accessed and set with {@link #getCorrespondingHeadway()} and
     * {@link #setCorrespondingHeadway(PeriodicHeadway)} respectively.
     *
     * @param activityId         id of the activity
     * @param type               type of the activity
     * @param sourceEvent        source event
     * @param targetEvent        target event
     * @param lowerBound         lower bound on the time the activity is allowed to take
     * @param upperBound         upper bound on the time the activity is allowed to take
     * @param numberOfPassengers number of passengers using the activity
     */
    public PeriodicHeadway(int activityId, ActivityType type, PeriodicEvent sourceEvent, PeriodicEvent targetEvent, int
        lowerBound, int upperBound, double numberOfPassengers) {
        super(activityId, type, sourceEvent, targetEvent, lowerBound, upperBound, numberOfPassengers);
    }

    public PeriodicHeadway getCorrespondingHeadway(){
        return correspondingHeadway;
    }

    public void setCorrespondingHeadway(PeriodicHeadway correspondingHeadway){
        this.correspondingHeadway = correspondingHeadway;
    }
}
