package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;

/**
 * A class representing a periodic activity, i.e., an arc in the periodic event activity network (EAN).
 */
public class PeriodicActivity implements Edge<PeriodicEvent> {
    protected int activityId;
    protected ActivityType type;
    protected PeriodicEvent sourceEvent;
    protected PeriodicEvent targetEvent;
    protected double lowerBound;
    protected double upperBound;
    protected double numberOfPassengers;

    /**
     * Create a new activity, i.e., an arc in the event activity network.
     *
     * @param activityId         id of the activity
     * @param type               type of the activity
     * @param sourceEvent        source event
     * @param targetEvent        target event
     * @param lowerBound         lower bound on the time the activity is allowed to take
     * @param upperBound         upper bound on the time the activity is allowed to take
     * @param numberOfPassengers number of passengers using the activity
     */
    public PeriodicActivity(int activityId, ActivityType type, PeriodicEvent sourceEvent, PeriodicEvent targetEvent, double
        lowerBound, double upperBound, double numberOfPassengers) {
        this.activityId = activityId;
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
    public PeriodicEvent getLeftNode() {
        return sourceEvent;
    }

    @Override
    public PeriodicEvent getRightNode() {
        return targetEvent;
    }

    @Override
    public boolean isDirected() {
        // An activity is always directed
        return true;
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
    public double getLowerBound() {
        return lowerBound;
    }

    /**
     * Get the upper bound of the time the activity is allowed to take.
     *
     * @return the upper bound of the activity
     */
    public double getUpperBound() {
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

    /**
     * Set the number of passengers using the activity.
     *
     * @param newPassengers the new number of passengers
     */
    public void setNumberOfPassengers(double newPassengers) {
        this.numberOfPassengers = newPassengers;
    }

    /**
     * Check whether the periodic duration of the activity, i.e., the difference between its start and end time mod
     * periodLength, is feasible,
     * i.e., between the lower and the upper bound of the activity.
     *
     * @param periodLength the length of the period
     * @return whether the duration of the activity is feasible
     */
    public boolean checkFeasibilityDuration(int periodLength) {
        int startTime = getLeftNode().getTime();
        int endTime = getRightNode().getTime();
        return ((endTime - startTime) % periodLength + periodLength) % periodLength >= getLowerBound()
            && ((endTime - startTime) % periodLength + periodLength) % periodLength <= getUpperBound();
    }

    /**
     * Get the duration of the activity. This will compute the length of the activity, the difference between the target
     * and the source event, but respect the crossing of a time period. I.e., this method will return the smallest
     * non-negative integer, such that
     * <ul>
     *     <li>duration >= lowerBound</li>
     *     <li>duration mod periodLength == (endTime - startTime) mod periodLength</li>
     * </ul>
     * @param periodLength the period length
     * @return the duration of the activity
     */
    public int getDuration(int periodLength) {
        int startTime = getLeftNode().getTime();
        int endTime = getRightNode().getTime();
        int duration = endTime - startTime;
        while (duration < lowerBound) {
            duration += periodLength;
        }
        return duration;
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
            getType().toString(),
            String.valueOf(getLeftNode().getId()),
            String.valueOf(getRightNode().getId()),
            CsvWriter.shortenDecimalValueForOutput(getLowerBound()),
            CsvWriter.shortenDecimalValueForOutput(getUpperBound()),
            CsvWriter.shortenDecimalValueForOutput(getNumberOfPassengers())
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PeriodicActivity that = (PeriodicActivity) o;

        if (activityId != that.activityId) return false;
        if (Double.compare(that.lowerBound, lowerBound) != 0) return false;
        if (Double.compare(that.upperBound, upperBound) != 0) return false;
        if (Double.compare(that.numberOfPassengers, numberOfPassengers) != 0) return false;
        if (type != that.type) return false;
        if (sourceEvent != null ? !sourceEvent.equals(that.sourceEvent) : that.sourceEvent != null) return false;
        return targetEvent != null ? targetEvent.equals(that.targetEvent) : that.targetEvent == null;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = activityId;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (sourceEvent != null ? sourceEvent.hashCode() : 0);
        result = 31 * result + (targetEvent != null ? targetEvent.hashCode() : 0);
        temp = Double.doubleToLongBits(lowerBound);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(upperBound);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Periodic Activity " + Arrays.toString(toCsvStrings());
    }
}
