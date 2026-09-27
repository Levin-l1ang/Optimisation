package net.lintim.model;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.Event.EventType;
import net.lintim.util.BiLinkedHashMap;
import net.lintim.util.MathHelper;
import net.lintim.util.SinglePair;
import net.lintim.util.TriLinkedHashMap;

import org.apache.commons.math.util.MathUtils;

/**
 * Implementation of an event activity network (EAN).
 */
public class EventActivityNetwork {

    public enum NetworkInputState {
        DRIVE_WAIT_SYNC, CHANGE_HEADWAY
    }

    public enum ModelChange {
        SIMPLE, LCM_SIMPLIFICATION
    }

    public enum ModelFrequency {
        FREQUENCY_AS_ATTRIBUTE, FREQUENCY_AS_MULTIPLICITY
    }

    public enum ModelHeadway {
        NO_HEADWAYS, SIMPLE, PRODUCT_OF_FREQUENCIES, LCM_OF_FREQUENCIES,
        LCM_REPRESENTATION
    }

    protected NetworkInputState networkInputState =
        NetworkInputState.DRIVE_WAIT_SYNC;

    protected ModelChange modelChange;
    protected ModelFrequency modelFrequency;
    protected ModelHeadway modelHeadway;

    protected PublicTransportationNetwork ptn;
    protected LineCollection lc;

    protected LinkedHashSet<Event> events = new LinkedHashSet<Event>();
    protected LinkedHashSet<Event> departureEvents = new LinkedHashSet<Event>();
    protected LinkedHashSet<Event> arrivalEvents = new LinkedHashSet<Event>();

    protected Boolean eventIdentificationMapAvailable = false;
    protected TriLinkedHashMap<EventType, Station, Line, Event>
    eventIdentificationMap =
        new TriLinkedHashMap<EventType, Station, Line, Event>();

    protected LinkedHashSet<Activity> activities = new LinkedHashSet<Activity>();
    protected LinkedHashSet<Activity> driveActivities = new LinkedHashSet<Activity>();
    protected LinkedHashSet<Activity> waitActivities = new LinkedHashSet<Activity>();
    protected LinkedHashSet<Activity> changeActivities =
        new LinkedHashSet<Activity>();
    protected LinkedHashSet<Activity> headwayActivities =
        new LinkedHashSet<Activity>();
    protected LinkedHashSet<Activity> passengerUsableActivities =
        new LinkedHashSet<Activity>();

    protected Integer smallestFreeEventIndex = null;
    protected Integer smallestFreeActivityIndex = null;
    protected Boolean linesUndirected = true;
    protected Boolean timetableGiven = false;
    protected Boolean activityPathsGiven = false;
    protected Double periodLength = null;

    protected LinkedHashMap<Integer, Event> indexEventMap =
        new LinkedHashMap<Integer, Event>();
    protected LinkedHashMap<Integer, Activity> indexActivityMap =
        new LinkedHashMap<Integer, Activity>();

    protected LinkedHashMap<Event, Line> eventUndirectedLineMap =
        new LinkedHashMap<Event, Line>();
    protected LinkedHashSet<Event> unalignedEvents = new LinkedHashSet<Event>();

    protected BiLinkedHashMap<Link, Line, LinkedHashSet<Activity>>
    linkDriveActivityMap =
        new BiLinkedHashMap<Link, Line, LinkedHashSet<Activity>>();
    protected LinkedHashMap<Station, LinkedHashSet<Event>> stationDepartureEventMap =
        new LinkedHashMap<Station, LinkedHashSet<Event>>();
    protected LinkedHashMap<Station, LinkedHashSet<Event>> stationArrivalEventMap =
        new LinkedHashMap<Station, LinkedHashSet<Event>>();

    protected BiLinkedHashMap<Station, Station, LinkedHashSet<Activity>> odPathMap =
        new BiLinkedHashMap<Station, Station, LinkedHashSet<Activity>>();

    public EventActivityNetwork(LineCollection lc, ModelFrequency modelFrequency,
            ModelChange modelChange, ModelHeadway modelHeadway) {
        this.ptn = lc.getPublicTransportationNetwork();
        this.lc = lc;
        this.modelFrequency = modelFrequency;
        this.modelChange = modelChange;
        this.modelHeadway = modelHeadway;
    }

    /**
     * Constructor, redirects to {@link #EventActivityNetwork(LineCollection,
     * ModelFrequency, ModelChange, ModelHeadway)}.
     *
     * @param lc The line concept to build the event activity network on.
     * @param config The configuration to get parameters from, see source.
     * @throws DataInconsistentException
     */
    public EventActivityNetwork(LineCollection lc, Configuration config)
    throws DataInconsistentException {
        this(lc, ModelFrequency
                .valueOf(config.getStringValue("ean_model_frequency")
                        .trim().toUpperCase()),
                 ModelChange.valueOf(config.getStringValue("ean_model_change")
                         .trim().toUpperCase()),
                 ModelHeadway.valueOf(config.getStringValue("ean_model_headway")
                         .trim().toUpperCase()));
    }

    /**
     * Adds an event with undirected line, i.e. the line is determined when
     * drive activities are added.
     *
     * @param event The event to add.
     * @param undirectedLineIndex The index of the undirected line.
     * @throws DataInconsistentException
     */
    public void addEventLineUndirected(Event event, Integer undirectedLineIndex)
            throws DataInconsistentException {
        addEvent(event, true);
        Line undirectedLine = lc.getLineByUndirectedIndex(undirectedLineIndex);
        if (undirectedLine == null) {
            throw new DataInconsistentException("line with undirected index "
                    + undirectedLineIndex + " not found");
        }
        eventUndirectedLineMap.put(event, undirectedLine);
        unalignedEvents.add(event);
    }

    /**
     * Adds an event.
     *
     * @param event The event to add.
     * @throws DataInconsistentException
     */
    public void addEvent(Event event) throws DataInconsistentException {
        addEvent(event, false);
        linesUndirected = false;
    }

    protected void addEvent(Event event, Boolean nullLineAllowed)
            throws DataInconsistentException {

        if (event == null) {
            throw new DataInconsistentException("event is null");
        }

        Integer index = event.getIndex();

        if (index == null) {
            throw new DataInconsistentException("event index is null");
        }

        if (!nullLineAllowed && event.getLine() == null) {

            throw new DataInconsistentException("event index " + index
                    + ": line is null");
        }

        if (indexEventMap.keySet().contains(index)) {
            throw new DataInconsistentException("event with index " + index
                    + " already exists");
        }

        if (smallestFreeEventIndex == null || index >= smallestFreeEventIndex) {

            smallestFreeEventIndex = index + 1;
        }

        Station station = event.getStation();
        if (station == null) {
            throw new DataInconsistentException("event index " + index
                    + ": station is null");
        }

        EventType type = event.getType();
        if (type == null) {
            throw new DataInconsistentException("event index " + index
                    + ": type is null");

        }

        events.add(event);
        indexEventMap.put(index, event);

        if (type == EventType.DEPARTURE) {
            LinkedHashSet<Event> oldStationEvents = stationDepartureEventMap
            .get(station);
            if (oldStationEvents == null) {
                oldStationEvents = new LinkedHashSet<Event>();
                stationDepartureEventMap.put(station, oldStationEvents);
            }
            oldStationEvents.add(event);
            departureEvents.add(event);

        } else if (type == EventType.ARRIVAL) {
            LinkedHashSet<Event> oldStationEvents = stationArrivalEventMap
            .get(station);
            if (oldStationEvents == null) {
                oldStationEvents = new LinkedHashSet<Event>();
                stationArrivalEventMap.put(station, oldStationEvents);
            }
            oldStationEvents.add(event);
            arrivalEvents.add(event);

        }

        invalidateCache();

    }

    /**
     * Adds an activity.
     *
     * @param activity The activity to add.
     * @throws DataInconsistentException
     */
    public void addActivity(Activity activity) throws DataInconsistentException {

        if (activity == null) {
            throw new DataInconsistentException("activity is null");
        }

        Integer index = activity.getIndex();

        if (index == null) {
            throw new DataInconsistentException("activity index is null");
        }

        if (indexActivityMap.keySet().contains(index)) {
            throw new DataInconsistentException("activity with index " + index
                    + " already exists");
        }

        if (smallestFreeActivityIndex == null
                || index >= smallestFreeActivityIndex) {

            smallestFreeActivityIndex = index + 1;
        }

        Event fromEvent = activity.getFromEvent();
        if (fromEvent == null) {
            throw new DataInconsistentException("activity index " + index
                    + ": fromEvent is null");
        }
        if (!events.contains(fromEvent)) {
            throw new DataInconsistentException("event set does not contain "
                    + "fromEvent; activity index is " + index);
        }

        Event toEvent = activity.getToEvent();
        if (toEvent == null) {
            throw new DataInconsistentException("activity index " + index
                    + ": toEvent is null");
        }
        if (!events.contains(toEvent)) {
            throw new DataInconsistentException("event set does not contain "
                    + "toEvent; activity index is " + index);
        }

        if (fromEvent == toEvent) {
            throw new DataInconsistentException("activity is a loop; index is "
                    + index);
        }

        // Set of all activities with same fromEvent and toEvent
        LinkedHashSet<Activity> currentActivities = fromEvent.
        getOutgoingEventActivitiesMap().get(toEvent);

//        if (currentActivities == null) {
//            currentActivities = new LinkedHashSet<Activity>();
//            adjacencyMatrix.put(fromEvent, toEvent, currentActivities);
//        }

        Line fromLine = fromEvent.getLine();
        Line toLine = toEvent.getLine();

        Station fromStation = fromEvent.getStation();
        Station toStation = toEvent.getStation();

        EventType fromType = fromEvent.getType();
        EventType toType = toEvent.getType();

        switch (activity.getType()) {
        case CHANGE:
            if (networkInputState != NetworkInputState.CHANGE_HEADWAY){
                networkInputState = NetworkInputState.CHANGE_HEADWAY;
            }

            if (fromType != EventType.ARRIVAL || toType != EventType.DEPARTURE) {
                throw new DataInconsistentException("change activity index "
                        + index + ": not between arrival and departure");
            }

//            if (fromStation != toStation) {
//                throw new DataInconsistentException("change activity index "
//                        + index + ": from- and toStation different");
//            }

//            if (fromLine == toLine) {
//                throw new DataInconsistentException("change activity index "
//                        + index + ": same line for both events");
//            }

            // For ModelChange.SIMPLE no parallel changing activities are allowed
            if (currentActivities != null && modelChange == ModelChange.SIMPLE) {
                throw new DataInconsistentException(
                        "a change activity from event index "
                                + fromEvent.getIndex()
                                + " to event index "
                                + toEvent.getIndex()
                                + " already exists, namely activity index "
                                + currentActivities.iterator().next()
                                        .getIndex());
            }

            changeActivities.add(activity);
            break;

        case DRIVE:
            if (networkInputState != NetworkInputState.DRIVE_WAIT_SYNC){
                throw new DataInconsistentException("did not expect " +
                        "drive activity " + index + " at input state " +
                        networkInputState);
            }

            if (fromType != EventType.DEPARTURE || toType != EventType.ARRIVAL) {
                throw new DataInconsistentException("drive activity index "
                        + index + ": not between departure and arrival");
            }

            if (fromStation == toStation) {
                throw new DataInconsistentException("drive activity index "
                        + index + ": from- and toStation not different");
            }

            if (fromLine != toLine) {
                throw new DataInconsistentException("drive activity index "
                        + index + ": line changed in between");
            }

            Activity departureDriveActivity = fromEvent.getAssociatedDriveActivity();
            if (departureDriveActivity == null){
                fromEvent.setAssociatedDriveActivity(activity);
            }
            else if (departureDriveActivity != activity) {
                throw new DataInconsistentException("drive activity "
                        + "index " + index + ": drive activity from "
                        + "event index " + fromEvent.getIndex()
                        + " was already assigned different index "
                        + departureDriveActivity.getIndex());
            }

            Activity arrivalDriveActivity = toEvent.getAssociatedDriveActivity();
            if(arrivalDriveActivity == null){
                toEvent.setAssociatedDriveActivity(activity);
            }
            else if (arrivalDriveActivity != activity) {
                throw new DataInconsistentException("drive activity "
                        + "index " + index + ": drive activity from "
                        + "event index " + fromEvent.getIndex()
                        + " was already assigned different index "
                        + arrivalDriveActivity.getIndex());
            }

            // toLine will be null automatically, because there cannot be an
            // event with two adjacent drive activities
            if (fromLine == null) {
                Line line1 = eventUndirectedLineMap.get(fromEvent);
                Line line2 = line1.getUndirectedCounterpart();
                LinkedList<SinglePair<Link>> firstLinkPairList =
                    line1.getAdjacentLinks(fromStation);
                LinkedList<SinglePair<Link>> secondLinkPairList =
                    line2.getAdjacentLinks(fromStation);

                for(SinglePair<Link> firstLinkPair : firstLinkPairList){
                    for(SinglePair<Link> secondLinkPair : secondLinkPairList){
                        if (firstLinkPair.second != null
                                && toStation == firstLinkPair.second.getToStation()) {
                            fromEvent.setLine(line1);
                            toEvent.setLine(line1);
                        } else if (secondLinkPair.second != null
                                && toStation == secondLinkPair.second.getToStation()) {

                            fromEvent.setLine(line2);
                            toEvent.setLine(line2);
                        } else {
                            throw new DataInconsistentException("cannot align "
                                    + "event " + fromEvent.getIndex());
                        }
                        unalignedEvents.remove(fromEvent);
                        unalignedEvents.remove(toEvent);
                        break;
                    }
                }

            }

            if (currentActivities != null) {
                throw new DataInconsistentException(
                        "a drive activity from event index "
                                + fromEvent.getIndex()
                                + " to event index "
                                + toEvent.getIndex()
                                + " already exists, namely activity index "
                                + currentActivities.iterator().next()
                                        .getIndex());
            }

            Link link = null;

            for(SinglePair<Link> possibleLinkPair : fromEvent.getLine().getAdjacentLinks(
                    fromEvent.getStation())){

                Link possibleLink = possibleLinkPair.second;

                // possibleLink can be null if lines contain loops
                if(possibleLink != null){ 
                if(possibleLink.getFromStation() == fromStation &&
                        possibleLink.getToStation() == toStation){
                    if(link == null){
                        link = possibleLink;
                    }
                    else if(link != possibleLink) {
                        throw new DataInconsistentException("line " +
                                fromLine.getIndex() + " has " +
                                "a loop over a double edge, impossible to " +
                                "identify link of drive activity " +
                                activity.getIndex());
                    }
                }
                }
            }

            if(link == null){
                throw new DataInconsistentException("could not find an edge " +
                        " corresponding to drive activity with index "
                        + activity.getIndex()
                        + " from event index "
                        + fromEvent.getIndex()
                        + " to event index "
                        + toEvent.getIndex());
            }

            Link oldLink = activity.getAssociatedLink();
            if(oldLink != null && oldLink != link){
                throw new DataInconsistentException("oldLink does not match " +
                        "link for activity " + activity.getIndex());
            }
            else{
                activity.setLink(link);
            }

            LinkedHashMap<Line, LinkedHashSet<Activity>> driveActivitiesAtLink =
                linkDriveActivityMap.get(link);
            if(driveActivitiesAtLink == null){
                driveActivitiesAtLink =
                    new LinkedHashMap<Line, LinkedHashSet<Activity>>();
            }
            LinkedHashSet<Activity> driveActivitiesAtLine =
                driveActivitiesAtLink.get(fromLine);
            if(driveActivitiesAtLine == null){
                driveActivitiesAtLine = new LinkedHashSet<Activity>();
            }
            driveActivitiesAtLine.add(activity);
            driveActivitiesAtLink.put(fromLine, driveActivitiesAtLine);
            linkDriveActivityMap.put(link, driveActivitiesAtLink);

            driveActivities.add(activity);
            break;

        case HEADWAY:
            if (networkInputState != NetworkInputState.CHANGE_HEADWAY){
                networkInputState = NetworkInputState.CHANGE_HEADWAY;
            }

            Link associatedFromLink = fromEvent.getAssociatedLink();
            Link associatedToLink = toEvent.getAssociatedLink();

            if(associatedFromLink == associatedToLink){
                activity.setLink(associatedFromLink);
            }

//            if (fromType != EventType.DEPARTURE || toType != EventType.DEPARTURE) {
//                throw new DataInconsistentException("headway activity index "
//                        + index + ": not between departures");
//            }

            headwayActivities.add(activity);
            break;

        case SYNC:
            if (networkInputState != NetworkInputState.DRIVE_WAIT_SYNC){
                throw new DataInconsistentException("did not expect " +
                        "sync activity " + index + " at input state " +
                        networkInputState);
            }

            if (fromType != EventType.DEPARTURE
                    || toType != EventType.DEPARTURE) {
                throw new DataInconsistentException("sync activity index "
                        + index + ": not between departure events");
            }

            if (fromStation != toStation) {
                throw new DataInconsistentException("sync activity index "
                        + index + ": from- and toStation different");
            }

//            if (fromLine != toLine){
//                throw new DataInconsistentException("sync activity index "
//                        + index + ": from- and toLine different");
//            }

            if (currentActivities != null) {
                throw new DataInconsistentException(
                        "a sync activity from event index "
                                + fromEvent.getIndex()
                                + " to event index " + toEvent.getIndex()
                                + " already exists, namely activity index "
                                + currentActivities.iterator().next()
                                        .getIndex());
            }

            break;
            case TURNAROUND:
            if (fromType != EventType.ARRIVAL || toType != EventType.DEPARTURE) {

                throw new DataInconsistentException("turn activity index "
                        + index + ": not between departure events");
            }

            // TODO check double activities here?
            if (currentActivities != null) {
                throw new DataInconsistentException(
                        "a turnaround activity from event index "
                                + fromEvent.getIndex()
                                + " to event index "
                                + toEvent.getIndex()
                                + " already exists, namely activity index "
                                + currentActivities.iterator().next()
                                        .getIndex());
            }
            break;

        case WAIT:
            if (networkInputState != NetworkInputState.DRIVE_WAIT_SYNC){
                throw new DataInconsistentException("did not expect " +
                        "wait activity " + index + " at input state " +
                        networkInputState);
            }

            if (fromType != EventType.ARRIVAL || toType != EventType.DEPARTURE) {
                throw new DataInconsistentException("wait activity index "
                        + index + ": not between arrival and departure");
            }

            if (fromStation != toStation) {
                throw new DataInconsistentException("wait activity index "
                        + index + ": from- and toStation different");
            }

            if (fromLine != toLine) {

                if ((fromLine == null || toLine == null)
                        && eventUndirectedLineMap.get(fromEvent) !=
                            eventUndirectedLineMap.get(toEvent)) {

                    throw new DataInconsistentException("wait activity "
                            + "index " + index + ": line changed in between");

                }

            }

            if (currentActivities != null) {
                throw new DataInconsistentException(
                        "a change activity from event index "
                                + fromEvent.getIndex()
                                + " to event index "
                                + toEvent.getIndex()
                                + " already exists, namely activity index "
                                + currentActivities.iterator().next()
                                        .getIndex());
            }

            Activity arrivalWaitActivity = fromEvent.getAssociatedWaitActivity();
            if(arrivalWaitActivity == null){
                fromEvent.setAssociatedWaitActivity(activity);
            }
            else if (arrivalWaitActivity != activity) {
                throw new DataInconsistentException("drive activity "
                        + "index " + index + ": drive activity from "
                        + "event index " + fromEvent.getIndex()
                        + " was already assigned different index "
                        + arrivalWaitActivity.getIndex());
            }

            Activity departureWaitActivity = toEvent.getAssociatedWaitActivity();
            if(departureWaitActivity == null){
                toEvent.setAssociatedWaitActivity(activity);
            }
            else if (departureWaitActivity != activity) {
                throw new DataInconsistentException("drive activity "
                        + "index " + index + ": drive activity from "
                        + "event index " + fromEvent.getIndex()
                        + " was already assigned different index "
                        + departureWaitActivity.getIndex());
            }

            waitActivities.add(activity);
            break;

        }

        if(activity.isPassengerUsable()){
            passengerUsableActivities.add(activity);
        }

        fromEvent.addOutgoingActivity(activity);
        toEvent.addIncomingActivity(activity);
        activities.add(activity);
        indexActivityMap.put(index, activity);

        invalidateCache();
    }

    protected void invalidateCache(){
        eventIdentificationMapAvailable = false;
    }

    /**
     * Checks whether passenger data is complete and if not, throws an
     * exception with a list of the respective activities.
     *
     * @throws DataInconsistentException
     */
    public void checkPassengerDataCompleteness()
            throws DataInconsistentException {
        LinkedHashSet<Event> eventsWithoutPassengerData =
            new LinkedHashSet<Event>();
        LinkedHashSet<Activity> activitiesWithoutPassengerData =
            new LinkedHashSet<Activity>();

        for (Event event : events) {
            if (event.getPassengers() == null) {
                eventsWithoutPassengerData.add(event);
            }
        }

        for (Activity activity : activitiesWithoutPassengerData) {
            if (activity.getPassengers() == null) {
                activitiesWithoutPassengerData.add(activity);
            }
        }

        Boolean badEvents = (eventsWithoutPassengerData.size() != 0);
        Boolean badActivities = (activitiesWithoutPassengerData.size() != 0);

        if (badEvents || badActivities) {

            StringBuffer errormsg = new StringBuffer("event activity " +
                    "network's passenger distribution incomplete; ");

            if (badEvents) {

                errormsg.append("missing passenger data for events: ");

                Iterator<Event> itr = eventsWithoutPassengerData.iterator();

                errormsg.append("" + itr.next().getIndex());

                while (itr.hasNext()) {
                    errormsg.append(", " + itr.next().getIndex());
                }

                if (badActivities) {
                    errormsg.append("; ");
                }

            }

            if (badActivities) {

                errormsg.append("missing passenger data for activities: ");

                Iterator<Activity> itr = activitiesWithoutPassengerData
                        .iterator();

                errormsg.append("" + itr.next().getIndex());

                while (itr.hasNext()) {
                    errormsg.append(", " + itr.next().getIndex());
                }

            }

            throw new DataInconsistentException(errormsg.toString());
        }

    }

    /**
     * Checks whether timetable data is complete and if not, throws an
     * exception with a list of the respective activities.
     *
     * @throws DataInconsistentException
     */
    public void checkTimetableCompleteness() throws DataInconsistentException {

        LinkedHashSet<Event> eventsWithoutTimetableData = new LinkedHashSet<Event>();

        for (Event event : events) {
            if (event.getTime() == null) {
                eventsWithoutTimetableData.add(event);
            }
        }

        if (eventsWithoutTimetableData.size() > 0) {
            StringBuffer errormsg = new StringBuffer("missing timetable data " +
                    "for events: ");

            Iterator<Event> itr = eventsWithoutTimetableData.iterator();

            errormsg.append("" + itr.next().getIndex());

            while (itr.hasNext()) {
                errormsg.append(", " + itr.next().getIndex());
            }

            throw new DataInconsistentException(errormsg.toString());

        }

    }

    /**
     * Checks for events with lines that could not be determined during the
     * phase of adding activities, in case lines are undirected. Does not yet
     * check whether all lines are complete.
     *
     * @throws DataInconsistentException
     */
    public void checkStructuralCompleteness() throws DataInconsistentException {
        if (!unalignedEvents.isEmpty()) {
            StringBuffer errormsg = new StringBuffer("event activity network " +
                    "incomplete; missing drive activities for events: ");

            Boolean firstrun = true;

            for (Event event : unalignedEvents) {
                if (firstrun) {
                    firstrun = false;
                } else {
                    errormsg.append(", ");
                }

                errormsg.append("" + event.getIndex());
            }

            throw new DataInconsistentException(errormsg.toString());
        }

        // TODO more correctness checks, especially trace lines

    }

    /**
     * Computes the derived durations of a given timetable, shortest duration if
     * several are possible.
     *
     * @throws DataInconsistentException
     */
    public void computeDurationsFromTimetable() throws DataInconsistentException{
        checkTimetableCompleteness();

        Double epsilon = MathHelper.epsilon;

        for (Activity activity : activities) {
            Double oldDuration = activity.getDuration();

            Double duration = activity.getToEvent().getTime()
            - activity.getFromEvent().getTime();

            Double lowerBound = activity.getLowerBound();
            Double upperBound = activity.getUpperBound();

            if (periodLength == null){
                if (duration - lowerBound < -epsilon ||
                        duration - upperBound > epsilon) {

                    throw new DataInconsistentException("duration "
                            + "of activity " + activity.getIndex()
                            + " not within bounds");
                }
            }

            else if(activity.getType() == ActivityType.CHANGE &&
                    modelChange == ModelChange.LCM_SIMPLIFICATION){

                if(Math.abs(upperBound-lowerBound-(periodLength-1)) > 0.1){
                    throw new DataInconsistentException("for change " +
                            "activity " + activity.getIndex() + " it must hold " +
                            "(upper-lower bound) = T-1 to be compatible " +
                            "with the " + ModelChange.LCM_SIMPLIFICATION);
                }

                double tau = periodLength/MathUtils.lcm(
                        activity.getFromEvent().getLine().getFrequency(),
                        activity.getToEvent().getLine().getFrequency());

                duration -= Math.floor((duration -
                        activity.getLowerBound())/tau)*tau;

            }

            else{

                duration -= Math.floor((duration - lowerBound)/periodLength)
                *periodLength;

                if (duration - lowerBound < -epsilon ||
                        duration - upperBound > epsilon) {

                    throw new DataInconsistentException("duration "
                            + "of activity " + activity.getIndex()
                            + " not within bounds; lower bound "
                            + activity.getLowerBound() + ", upper bound "
                            + activity.getUpperBound()
                            + ", actual duration " + duration);

                }

            }

            if(oldDuration != null){
                Double delta = oldDuration - duration;
                if(Math.abs(delta) > epsilon){
                    Double deltaDivPeriod = delta/periodLength;
                    Double passengers = activity.getPassengers();
                    if(Math.abs(deltaDivPeriod-Math.round(deltaDivPeriod)) > epsilon){
                        throw new DataInconsistentException("durations do not " +
                                "fit; old duration " + oldDuration +
                                " new duration " + duration);
                    }
                    else if(Math.abs(passengers) > epsilon){
                        throw new DataInconsistentException("durations differ " +
                                "an integral multiple of the period length and " +
                                "the number of passengers does not vanish; " +
                                "your timetabling objective function value may " +
                                "be wrong; old duration " + oldDuration +
                                " new duration " + duration + " passengers " +
                                        "number " + passengers);
                    }
                }
            }

            activity.setDuration(duration);
        }

        timetableGiven = true;

    }

    /**
     * Computes a timetable derived from durations.
     *
     * @throws DataInconsistentException
     */
    public void computeTimetableFromDurations() throws DataInconsistentException{

        LinkedHashSet<Event> unreached = new LinkedHashSet<Event>(events);

        // process forest
        while(!unreached.isEmpty()){
            LinkedHashSet<Event> reached = new LinkedHashSet<Event>();
            LinkedHashSet<Event> oldLeaves = new LinkedHashSet<Event>();
            Event firstNode = unreached.iterator().next();
            oldLeaves.add(firstNode);
            reached.add(firstNode);
            firstNode.setTime(0.0);

            Boolean treeComplete;

            if(firstNode.getOutgoingActivities().isEmpty() &&
                    firstNode.getIncomingActivities().isEmpty()){
                treeComplete = true;
            }
            else {
                treeComplete = false;
            }

            // process tree
            while(!treeComplete){
                treeComplete = true;

                LinkedHashSet<Event> currentLeaves =
                    new LinkedHashSet<Event>(oldLeaves);
                oldLeaves.clear();

                for(Event currentNode : currentLeaves){
                    for(Activity outgoingEdge : currentNode.getOutgoingActivities()){
                        if(outgoingEdge.getDuration() == null){
                            continue;
                        }

                        Event targetNode = outgoingEdge.getToEvent();
                        if(!reached.contains(targetNode)){
                            treeComplete = false;
                            reached.add(targetNode);
                            oldLeaves.add(targetNode);
                            Double t = currentNode.getTime()+
                                outgoingEdge.getDuration();
                            targetNode.setTime(t-Math.floor(t/periodLength)*
                                    periodLength);
                        }
                    }
                    for(Activity incomingEdge : currentNode.getIncomingActivities()){
                        if(incomingEdge.getDuration() == null){
                            continue;
                        }

                        Event targetNode = incomingEdge.getFromEvent();
                        if(!reached.contains(targetNode)){
                            treeComplete = false;
                            reached.add(targetNode);
                            oldLeaves.add(targetNode);
                            Double t = currentNode.getTime()-
                                incomingEdge.getDuration();
                            targetNode.setTime(t-Math.floor(t/periodLength)*
                                    periodLength);
                        }
                    }
                }
            }

            unreached.removeAll(reached);
            reached.clear();
        }

        checkTimetableCompleteness();
        if(modelChange == ModelChange.LCM_SIMPLIFICATION){
            clearDurations();
        }
        // TODO remove this!
        computeDurationsFromTimetable();

        timetableGiven = true;

    }

    /**
     * Computes a passenger distribution from the activity paths.
     *
     * @param od Origin destination matrix which provides path coefficients.
     */
    public void computePassengersFromActivityPaths(OriginDestinationMatrix od){
        resetPassengers();

        for(Map.Entry<Station, LinkedHashMap<Station,
                LinkedHashSet<Activity>>> e1 : odPathMap.entrySet()){

            Station s1 = e1.getKey();

            for(Map.Entry<Station, LinkedHashSet<Activity>> e2 :
                e1.getValue().entrySet()){

                Station s2 = e2.getKey();

                for(Activity a : e2.getValue()){
                    a.setPassengers(a.getPassengers()+od.get(s1, s2));
                }

            }
        }

        activityPathsGiven = true;
    }

    /**
     * Ensures that the event identification map is accessible. It allows to
     * map a (type, station, line) triple to an actual event and thus to
     * compare two event activity networks that have the same underlying line
     * concept.
     *
     * @throws DataInconsistentException
     */
    public void provideEventIdentificationMap() throws DataInconsistentException{
        if(eventIdentificationMapAvailable){
            return;
        }

        eventIdentificationMap.clear();

        for(Event event : events){
            eventIdentificationMap.put(event.getType(), event.getStation(),
                    event.getLine(), event);
        }

        eventIdentificationMapAvailable = true;
    }

    /**
     * Sets all durations to null.
     */
    public void clearDurations(){
        for(Activity a : activities){
            a.setDuration(null);
        }
    }

    /**
     * Sets all passengers to zero.
     */
    public void resetPassengers(){
        for(Activity a : activities){
            a.setPassengers(0.0);
        }
        for(Event e : events){
            e.setPassengers(0.0);
        }
    }

    // =========================================================================
    // === Single request comfort functions ====================================
    // =========================================================================
    public Event getEventByIndex(Integer index) {
        return indexEventMap.get(index);
    }

    public Activity getActivityByIndex(Integer index) {
        return indexActivityMap.get(index);
    }

    public Boolean isPeriodic() {
        return periodLength != null;
    }

    public LinkedHashSet<Event> getDepartureEventsByStation(Station station) {
        return stationDepartureEventMap.get(station);
    }

    public LinkedHashSet<Event> getArrivalEventsByStation(Station station) {
        return stationArrivalEventMap.get(station);
    }

    public Event identifyEvent(Event reference) throws DataInconsistentException{
        provideEventIdentificationMap();
        return eventIdentificationMap.get(reference.getType(),
                reference.getStation(), reference.getLine());
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setPeriodLength(Double periodLength) {
        this.periodLength = periodLength;
    }

    public void setModelChange(ModelChange modelChange) {
        this.modelChange = modelChange;
    }

    public void setModelFrequency(ModelFrequency modelFrequency) {
        this.modelFrequency = modelFrequency;
    }

    public void setModelHeadway(ModelHeadway modelHeadway) {
        this.modelHeadway = modelHeadway;
    }
    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public LinkedHashSet<Event> getEvents() {
        return events;
    }

    public LinkedHashSet<Activity> getActivities() {
        return activities;
    }

    public Double getPeriodLength() {
        return periodLength;
    }

    public Boolean timetableGiven() {
        return timetableGiven;
    }

    public LinkedHashSet<Activity> getDriveActivities() {
        return driveActivities;
    }

    public LinkedHashSet<Activity> getWaitActivities() {
        return waitActivities;
    }

    public LinkedHashSet<Activity> getChangeActivities() {
        return changeActivities;
    }

    public LinkedHashSet<Event> getDepartureEvents() {
        return departureEvents;
    }

    public LinkedHashSet<Event> getArrivalEvents() {
        return arrivalEvents;
    }

    public LinkedHashMap<Station, LinkedHashSet<Event>>
    getStationDepartureEventMap() {
        return stationDepartureEventMap;
    }

    public LinkedHashMap<Station, LinkedHashSet<Event>>
    getStationArrivalEventMap() {
        return stationArrivalEventMap;
    }

    public Integer getSmallestFreeEventIndex() {
        if (smallestFreeEventIndex == null) {
            return 1;
        }
        return smallestFreeEventIndex;
    }

    public Integer getSmallestFreeActivityIndex() {
        if (smallestFreeActivityIndex == null) {
            return 1;
        }
        return smallestFreeActivityIndex;
    }

    public BiLinkedHashMap<Station, Station, LinkedHashSet<Activity>>
    getOriginDestinationPathMap() {
        return odPathMap;
    }

    public void addOriginDestinationPath(Station s1, Station s2,
            LinkedHashSet<Activity> activityPath) {

        this.odPathMap.put(s1, s2, activityPath);

    }

    public TriLinkedHashMap<EventType, Station, Line, Event> getEventIdentificationMap() {
        return eventIdentificationMap;
    }

    public Boolean activityPathsGiven() {
        return activityPathsGiven;
    }

    public LinkedHashMap<Link, LinkedHashMap<Line, LinkedHashSet<Activity>>>
    getLinkDriveActivityMap() {
        return linkDriveActivityMap;
    }

    public LinkedHashSet<Activity> getPassengerUsableActivities() {
        return passengerUsableActivities;
    }

    public LinkedHashSet<Activity> getHeadwayActivities() {
        return headwayActivities;
    }

    public ModelChange getModelChange() {
        return modelChange;
    }

    public ModelFrequency getModelFrequency() {
        return modelFrequency;
    }

    public ModelHeadway getModelHeadway() {
        return modelHeadway;
    }

    public PublicTransportationNetwork getPublicTransportationNetwork() {
        return ptn;
    }

    public LineCollection getLineConcept() {
        return lc;
    }

}
