package net.lintim.model;

/**
 * A class representing an aperiodic headway activity, i.e., an arc in the aperiodic event activity network (EAN) that
 * represents a headway.
 */
public class AperiodicHeadway extends AperiodicActivity {

    protected AperiodicHeadway correspondingHeadway;

    /**
     * Create a new aperiodic headway activity, i.e., an arc in the aperiodic event activity network that is a headway.
     * The corresponding headway activity can be accessed and set with {@link #getCorrespondingHeadway()} and
     * {@link #setCorrespondingHeadway(AperiodicHeadway)} respectively.
     *
     * @param activityId         id of the activity
     * @param periodicActivityId id of the corresponding periodic activity
     * @param type               type of the activity
     * @param sourceEvent        source event
     * @param targetEvent        target event
     * @param lowerBound         lower bound on the time the activity is allowed to take
     * @param upperBound         upper bound on the time the activity is allowed to take
     * @param numberOfPassengers number of passengers using the activity
     */
    public AperiodicHeadway(int activityId, int periodicActivityId, ActivityType type, AperiodicEvent sourceEvent,
                             AperiodicEvent targetEvent, int lowerBound, int upperBound, double numberOfPassengers) {
        super(activityId, periodicActivityId, type, sourceEvent, targetEvent, lowerBound, upperBound, numberOfPassengers);
    }

    public AperiodicHeadway getCorrespondingHeadway(){
        return correspondingHeadway;
    }

    public void setCorrespondingHeadway(AperiodicHeadway correspondingHeadway){
        this.correspondingHeadway = correspondingHeadway;
    }
}
