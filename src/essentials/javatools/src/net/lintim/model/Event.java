package net.lintim.model;

import net.lintim.exception.DataInconsistentException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;


/**
 * Container class for an event in an {@link EventActivityNetwork}.
 */

public class Event {

    public enum EventType {
        ARRIVAL, DEPARTURE
    }

    public enum LineDirection {
        FORWARDS, BACKWARDS
    }

    // =========================================================================
    // === Fields ==============================================================
    // =========================================================================

    // --- Properties ----------------------------------------------------------
    private Integer index;
    private EventType type;
    private Station station;
    private Line line;
    private Integer instance;
    private Activity associatedDriveActivity;
    private Activity associatedWaitActivity;
    private Double passengers;
    private Double time;
    private LineDirection direction;
    private int frequencyRepetition;

    // --- Network Structure ---------------------------------------------------
    LinkedHashMap<Event, LinkedHashSet<Activity>> outgoingEventActivitiesMap =
        new LinkedHashMap<Event, LinkedHashSet<Activity>>();
    LinkedHashMap<Event, LinkedHashSet<Activity>> incomingEventActivitiesMap =
        new LinkedHashMap<Event, LinkedHashSet<Activity>>();
    LinkedHashSet<Activity> outgoingActivities = new LinkedHashSet<Activity>();
    LinkedHashSet<Activity> incomingActivities = new LinkedHashSet<Activity>();

    /**
     * Constructor.
     *
     * @param index The event index.
     * @param type The event type.
     * @param station The station of the event.
     * @param line The events line.
     * @param instance The events' frequency instance. Out of order at the
     * moment due to possible loops in lines. Has the right value on
     * construction, but is null when network is read in.
     * @param associatedDriveActivity The events associated drive activity.
     * @param associatedWaitActivity The events associated wait activity. May
     * be null in case it is a line start or end.
     * @param passengers The number of passengers using the event.
     * @param time The events time in the timetable.
     */
    public Event(Integer index, EventType type, Station station, Line line,
            Integer instance, Activity associatedDriveActivity,
            Activity associatedWaitActivity, Double passengers, Double time,
            LineDirection direction, int frequencyRepetition) {

        this.index = index;
        this.type = type;
        this.station = station;
        this.line = line;
        this.instance = instance;
        this.associatedDriveActivity = associatedDriveActivity;
        this.associatedWaitActivity = associatedWaitActivity;
        this.passengers = passengers;
        this.time = time;
        this.direction = direction;
        this.frequencyRepetition = frequencyRepetition;
    }

    // =========================================================================
    // === Network Structure Operators =========================================
    // =========================================================================
    /**
     * Adds an outgoing activity, i.e. this event is source.
     *
     * @param activity The activity to add.
     * @throws DataInconsistentException
     */
    public void addOutgoingActivity(Activity activity)
    throws DataInconsistentException{

        if(activity.getFromEvent() != this){
            throw new DataInconsistentException("addOutgoingActivity: from " +
                    "event does not match this event");
        }

        Event toEvent = activity.getToEvent();
        LinkedHashSet<Activity> outgoingActivitiesToEvent =
            outgoingEventActivitiesMap.get(toEvent);

        if(outgoingActivitiesToEvent == null){
            outgoingActivitiesToEvent = new LinkedHashSet<Activity>();
            outgoingEventActivitiesMap.put(toEvent, outgoingActivitiesToEvent);
        }
        outgoingActivitiesToEvent.add(activity);

        outgoingActivities.add(activity);

//        if(activity.isPassengerUsable()){
//            passengerUsableOutgoingActivities.add(activity);
//        }
    }

    /**
     * Adds an incoming activity, i.e. this event is target.
     *
     * @param activity The activity to add.
     * @throws DataInconsistentException
     */
    public void addIncomingActivity(Activity activity)
    throws DataInconsistentException{
        if(activity.getToEvent() != this){
            throw new DataInconsistentException("addIncomingActivity: from " +
                    "event does not match this event");
        }

        Event fromEvent = activity.getFromEvent();
        LinkedHashSet<Activity> incomingActivitiesFromEvent =
            incomingEventActivitiesMap.get(fromEvent);

        if(incomingActivitiesFromEvent == null){
            incomingActivitiesFromEvent = new LinkedHashSet<Activity>();
            incomingEventActivitiesMap.put(fromEvent, incomingActivitiesFromEvent);
        }
        incomingActivitiesFromEvent.add(activity);

        incomingActivities.add(activity);

//        if(activity.isPassengerUsable()){
//            passengerUsableIncomingActivities.add(activity);
//        }
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setLine(Line line) {
        this.line = line;
    }

    public void setPassengers(Double passengers) {
        this.passengers = passengers;
    }

    public void setTime(Double time) {
        this.time = time;
    }

    public void setAssociatedDriveActivity(Activity associatedDriveActivity) {
        this.associatedDriveActivity = associatedDriveActivity;
    }

    public void setInstance(Integer instance) {
        this.instance = instance;
    }

    public void setAssociatedWaitActivity(Activity associatedWaitActivity) {
        this.associatedWaitActivity = associatedWaitActivity;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Integer getIndex() {
        return index;
    }

    public EventType getType() {
        return type;
    }

    public Station getStation() {
        return station;
    }

    public Line getLine() {
        return line;
    }

    public Double getPassengers() {
        return passengers;
    }

    public Double getTime() {
        return time;
    }

    public LineDirection getLineDirection() {
        return direction;
    }

    public int getFrequencyRepetition(){
        return frequencyRepetition;
    }

    public LinkedHashSet<Activity> getOutgoingActivities() {
        return outgoingActivities;
    }

    public LinkedHashSet<Activity> getIncomingActivities() {
        return incomingActivities;
    }

    public LinkedHashMap<Event, LinkedHashSet<Activity>> getOutgoingEventActivitiesMap() {
        return outgoingEventActivitiesMap;
    }

    public LinkedHashMap<Event, LinkedHashSet<Activity>> getIncomingEventActivitiesMap() {
        return incomingEventActivitiesMap;
    }

    public Integer getInstance() {
        return instance;
    }

    public Activity getAssociatedDriveActivity() {
        return associatedDriveActivity;
    }

    public Activity getAssociatedWaitActivity() {
        return associatedWaitActivity;
    }

    public Link getAssociatedLink(){
        if(associatedDriveActivity == null){
            return null;
        }
        return associatedDriveActivity.getAssociatedLink();
    }

    // =========================================================================
    // === Other ===============================================================
    // =========================================================================
    protected String javaId() {
        return super.toString();
    }

    @Override
    public String toString() {
        return "   javaId index type    SId  LnId  time pass\n"
                + String.format(" %8s", javaId().split("@")[1])
                + String.format(" %5d", index)
                + String.format(" %5s", type.toString())
                + String.format(" %4d", station.getIndex())
                + String.format(" %4d", line.getIndex())
                + String.format(" %5d", time)
                + String.format(" %4.1f", passengers);
    }

}
