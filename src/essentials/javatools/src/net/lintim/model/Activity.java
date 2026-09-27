package net.lintim.model;

/**
 * Container class for an activity in an {@link EventActivityNetwork}.
 */

public class Activity {

    public enum ActivityType {
        CHANGE, DRIVE, HEADWAY, SYNC, WAIT, TURNAROUND
    }

    private Integer index;
    private ActivityType type;
    private Event fromEvent;
    private Event toEvent;
    private Link associatedLink;
    private Double lowerBound;
    private Double upperBound;
    private Double passengers;
    private Double duration;
    private Double initialDurationAssumption;

    /**
     * Constructor.
     *
     * @param index The activity index.
     * @param type The activity type.
     * @param fromEvent The activity's from event.
     * @param toEvent The activity's to event.
     * @param associatedLink If the activity is of type drive, this is the link
     * in the public transportation network underlying the event activity
     * network.
     * @param lowerBound The activity's (duration) lower bound.
     * @param upperBound The activity's (duration) upper bound.
     * @param passengers Number of passengers that use activity.
     * @param duration The activity's duration (if a timetable is given).
     * @param initialDurationAssumption
     */
    public Activity(Integer index, ActivityType type, Event fromEvent,
            Event toEvent, Link associatedLink, Double lowerBound, Double upperBound,
            Double passengers, Double duration, Double initialDurationAssumption) {

        this.index = index;
        this.type = type;
        this.fromEvent = fromEvent;
        this.toEvent = toEvent;
        this.associatedLink = associatedLink;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.passengers = passengers;
        this.duration = duration;
        this.initialDurationAssumption = initialDurationAssumption;

    }

    /**
     * Whether or not the activity is usable by passengers.
     *
     * @return true if it is usable and false otherwise.
     */
    public Boolean isPassengerUsable(){
        return type == ActivityType.DRIVE || type == ActivityType.WAIT ||
        type == ActivityType.CHANGE;
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setLowerBound(Double lowerBound) {
        this.lowerBound = lowerBound;
    }

    public void setUpperBound(Double upperBound) {
        this.upperBound = upperBound;
    }

    public void setPassengers(Double passengers) {
        this.passengers = passengers;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public void setInitialDurationAssumption(Double initialDurationAssumption) {
        this.initialDurationAssumption = initialDurationAssumption;
    }

    public void setLink(Link link) {
        this.associatedLink = link;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Integer getIndex() {
        return index;
    }

    public ActivityType getType() {
        return type;
    }

    public Event getFromEvent() {
        return fromEvent;
    }
    public Event getToEvent() {
        return toEvent;
    }
    public Double getLowerBound() {
        return lowerBound;
    }
    public Double getUpperBound() {
        return upperBound;
    }

    public Double getPassengers() {
        return passengers;
    }

    public Double getDuration() {
        return duration;
    }

    public Double getInitialDurationAssumption() {
        return initialDurationAssumption;
    }

    public Link getAssociatedLink() {
        return associatedLink;
    }

    /**
     * The activity's station.
     *
     * @return The activity's station, if it is wait or change, null otherwise.
     */
    public Station getStation() {
        if(type != ActivityType.WAIT && type != ActivityType.CHANGE || fromEvent == null){
            return null;
        }
        return fromEvent.getStation();
    }

    /**
     * The activity's line.
     *
     * @return The activity's line if it is drive or wait, null otherwise.
     */
    public Line getLine(){
        if(type != ActivityType.DRIVE && type != ActivityType.WAIT || fromEvent == null){
            return null;
        }
        return fromEvent.getLine();
    }

    // =========================================================================
    // === Other ===============================================================
    // =========================================================================

    protected String javaId() {
        return super.toString();
    }

    @Override
    public String toString() {
        return "   javaId index type  fEvId tEvId lBound uBound pass\n"
                + String.format(" %8s", javaId().split("@")[1])
                + String.format(" %5d", index)
                + String.format(" %5s", type.toString())
                + String.format(" %5d", fromEvent.getIndex())
                + String.format(" %5d", toEvent.getIndex())
                + String.format(" %4.1f", lowerBound)
                + String.format(" %4.1f", upperBound)
                + String.format(" %4.1f", passengers);
    }

}
