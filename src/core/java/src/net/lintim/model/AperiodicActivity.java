package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;

/**
 * A class representing an aperiodic activity, i.e., an arc in the aperiodic event activity network (EAN).
 */
public class AperiodicActivity implements Edge<AperiodicEvent> {
    protected int activityId;
    protected int periodicActivityId;
    protected ActivityType type;
    protected AperiodicEvent sourceEvent;
    protected AperiodicEvent targetEvent;
    protected int lowerBound;
    protected int upperBound;
    protected double numberOfPassengers;

    /**
     * Create a new aperiodic activity, i.e., an arc in the aperiodic event activity network.
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
    public AperiodicActivity(int activityId, int periodicActivityId, ActivityType type, AperiodicEvent sourceEvent,
                             AperiodicEvent targetEvent, int lowerBound, int upperBound, double numberOfPassengers) {
        this.activityId = activityId;
        this.periodicActivityId = periodicActivityId;
        this.type = type;
        this.sourceEvent = sourceEvent;
        this.targetEvent = targetEvent;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.numberOfPassengers = numberOfPassengers;
    }

    @Override
    public int getId() {
        return activityId;
    }

    @Override
    public void setId(int id) {
        activityId = id;
    }

    @Override
    public AperiodicEvent getLeftNode() {
        return sourceEvent;
    }

    @Override
    public AperiodicEvent getRightNode() {
        return targetEvent;
    }

    @Override
    public boolean isDirected() {
        // An activity is always directed.
        return true;
    }

    /**
     * Get the id of the corresponding periodic activity.
     *
     * @return the id of the corresponding activity
     */
    public int getPeriodicActivityId() {
        return periodicActivityId;
    }

    /**
     * Get the type of an activity, which is specified in ActivityType.
     *
     * @return the type of the activity
     */
    public ActivityType getType() {
        return type;
    }

    /**
     * Get the lower bound of the time the activity is allowed to take.
     *
     * @return the lower bound of the activity
     */
    public int getLowerBound() {
        return lowerBound;
    }

    /**
     * Get the upper bound of the time the activity is allowed to take.
     *
     * @return the upper bound of the activity
     */
    public int getUpperBound() {
        return upperBound;
    }

    /**
     * Get the number of passengers using the activity.
     *
     * @return the number of passengers using the activity
     */
    public double getNumberOfPassengers() {
        return numberOfPassengers;
    }

    public void setNumberOfPassengers(double numberOfPassengers) {
        this.numberOfPassengers = numberOfPassengers;
    }

    /**
     * Check whether the duration of the activity, i.e., the difference between its start and end time, is feasible,
     * i.e., between the lower and the upper bound of the activity.
     *
     * @return whether the duration of the activity is feasible
     */
    public boolean checkFeasibilityDuration() {
        int startTime = getLeftNode().getTime();
        int endTime = getRightNode().getTime();
        return endTime - startTime >= getLowerBound() && endTime - startTime <= getUpperBound();
    }

    /**
     * Return a string array, representing the activity for a LinTim csv file
     * @return the csv representation of this activity
     */
    public String[] toCsvStrings(){
        // We dont want to output any od activities
        if (getType() == ActivityType.OD) {
            return new String[0];
        }
        return new String[]{
            String.valueOf(getId()),
            String.valueOf(getPeriodicActivityId()),
            getType().toString(),
            String.valueOf(getLeftNode().getId()),
            String.valueOf(getRightNode().getId()),
            String.valueOf(getLowerBound()),
            String.valueOf(getUpperBound()),
            CsvWriter.shortenDecimalValueForOutput(getNumberOfPassengers())
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AperiodicActivity that = (AperiodicActivity) o;

        if (activityId != that.activityId) return false;
        if (getPeriodicActivityId() != that.getPeriodicActivityId()) return false;
        if (getLowerBound() != that.getLowerBound()) return false;
        if (getUpperBound() != that.getUpperBound()) return false;
        if (Double.compare(that.getNumberOfPassengers(), getNumberOfPassengers()) != 0) return false;
        if (getType() != null ? !getType().equals(that.getType()) : that.getType() != null) return false;
        if (sourceEvent != null ? !sourceEvent.equals(that.sourceEvent) : that.sourceEvent != null) return false;
        return targetEvent != null ? targetEvent.equals(that.targetEvent) : that.targetEvent == null;
    }

    @Override
    public int hashCode() {
        int result = activityId;
        result = 31 * result + getPeriodicActivityId();
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        result = 31 * result + (sourceEvent != null ? sourceEvent.hashCode() : 0);
        result = 31 * result + (targetEvent != null ? targetEvent.hashCode() : 0);
        result = 31 * result + getLowerBound();
        result = 31 * result + getUpperBound();
        return result;
    }

    @Override
    public String toString(){
        return "Aperiodic Activity "+ Arrays.toString(toCsvStrings());
    }
}
