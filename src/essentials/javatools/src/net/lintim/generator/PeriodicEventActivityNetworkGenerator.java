package net.lintim.generator;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConcept;
import net.lintim.model.*;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.Event.EventType;
import net.lintim.model.EventActivityNetwork.ModelChange;
import net.lintim.model.EventActivityNetwork.ModelFrequency;
import net.lintim.model.EventActivityNetwork.ModelHeadway;
import org.apache.commons.math.util.MathUtils;

import java.util.*;

/**
 * Constructs an {@link EventActivityNetwork} from a given
 * {@link PublicTransportationNetwork}, {@link LineConcept} and a
 * {@link Configuration}. Can be used to perform a periodic rollout.
 */
public class PeriodicEventActivityNetworkGenerator {

    protected EventActivityNetwork ean;
    protected LineCollection lc;
    protected PublicTransportationNetwork ptn;
    protected Double periodLength;
    protected Graph<InfrastructureNode, WalkingEdge> walkingGraph;

    protected Double minimalChangeTime;
    protected Double maximalChangeTime;

    protected Double minimalWaitTime;
    protected Double maximalWaitTime;

    protected Double maxWalkingTime;

    protected EventActivityNetwork referenceEan;
    protected LineCollection referenceLc;
    protected Boolean timetableGiven = false;

    protected ModelFrequency modelFrequency;
    protected ModelChange modelChange;
    protected ModelHeadway modelHeadway;

    protected Boolean forceAllFrequenciesOne = false;
    protected Boolean discardStationLoopChanges = true;
    protected Set<Integer> changeStationIds;
    protected Map<Integer, StationLimit> stationLimits;

    /**
     * Primary Constructor.
     *
     * @param ean {@link EventActivityNetwork} to be constructed.
     * @param referenceEan passenger numbers and timetable may be copied from
     *                     this {@link EventActivityNetwork}.
     * @param minimalChangeTime global lower bound template for
     *                          {@link Activity.ActivityType#CHANGE}.
     * @param maximalChangeTime global upper bound template for
     *                          {@link Activity.ActivityType#CHANGE}.
     * @param minimalWaitTime global lower bound template for
     *                          {@link Activity.ActivityType#WAIT}.
     * @param maximalWaitTime global upper bound template for
     *                          {@link Activity.ActivityType#WAIT}.
     * @throws DataInconsistentException
     */
    public PeriodicEventActivityNetworkGenerator(EventActivityNetwork ean,
            EventActivityNetwork referenceEan, Double minimalChangeTime,
            Double maximalChangeTime, Double minimalWaitTime,
            Double maximalWaitTime)
            throws DataInconsistentException {

        setEventActivityNetwork(ean);
        setReferenceEventActivityNetwork(referenceEan);
        setLineConcept(ean.getLineConcept());
        setPublicTransportationNetwork(ean.getPublicTransportationNetwork());
        this.modelFrequency = ean.getModelFrequency();
        this.modelChange = ean.getModelChange();
        this.modelHeadway = ean.getModelHeadway();
        setMinimalChangeTime(minimalChangeTime);
        setMaximalChangeTime(maximalChangeTime);
        setMinimalWaitTime(minimalWaitTime);
        setMaximalWaitTime(maximalWaitTime);

    }

    /**
     * Redirects to {@link #PeriodicEventActivityNetworkGenerator(
     * EventActivityNetwork, EventActivityNetwork, Double, Double, Double,
     * Double)} by reading parameters from a {@link Configuration}:
     *
     * <ul>
     *  <li>minimalChangeTime:
     *  config.getDoubleValue("ean_default_minimal_change_time")</li>
     *  <li>maximalChangeTime:
     *  config.getDoubleValue("ean_default_maximal_change_time")</li>
     *  <li>minimalWaitTime:
     *  config.getDoubleValue("ean_default_minimal_waiting_time")</li>
     *  <li>maximalWaitTime:
     *  config.getDoubleValue("ean_default_maximal_waiting_time")</li>
     * </ul>
     * @param ean redirected to other constructor, same argument name.
     * @param referenceEan redirected to other constructor, same argument name.
     * @param config redirected as in main description.
     * @throws DataInconsistentException
     */
    public PeriodicEventActivityNetworkGenerator(EventActivityNetwork ean,
                                                 EventActivityNetwork referenceEan, Configuration config)
        throws DataInconsistentException {

        this(ean, referenceEan,
            config.getDoubleValue("ean_default_minimal_change_time"),
            config.getDoubleValue("ean_default_maximal_change_time"),
            config.getDoubleValue("ean_default_minimal_waiting_time"),
            config.getDoubleValue("ean_default_maximal_waiting_time"));

    }

    /**
     * Redirects to {@link #PeriodicEventActivityNetworkGenerator(
     * EventActivityNetwork, EventActivityNetwork, Double, Double, Double,
     * Double)} by reading parameters from a {@link Configuration}:
     *
     * <ul>
     *  <li>minimalChangeTime:
     *  config.getDoubleValue("ean_default_minimal_change_time")</li>
     *  <li>maximalChangeTime:
     *  config.getDoubleValue("ean_default_maximal_change_time")</li>
     *  <li>minimalWaitTime:
     *  config.getDoubleValue("ean_default_minimal_waiting_time")</li>
     *  <li>maximalWaitTime:
     *  config.getDoubleValue("ean_default_maximal_waiting_time")</li>
     * </ul>
     * @param ean redirected to other constructor, same argument name.
     * @param referenceEan redirected to other constructor, same argument name.
     * @param walkingGraph undocumented
     * @param changeStationIds undocumented
     * @param stationLimits undocumented
     * @param config redirected as in main description.
     * @throws DataInconsistentException
     */
    public PeriodicEventActivityNetworkGenerator(EventActivityNetwork ean,
                                                 EventActivityNetwork referenceEan,
                                                 Graph<InfrastructureNode, WalkingEdge> walkingGraph,
                                                 Set<Integer> changeStationIds,
                                                 Map<Integer, StationLimit> stationLimits,
                                                 Configuration config)
        throws DataInconsistentException {

        this(ean, referenceEan,
            config);
        this.walkingGraph = walkingGraph;
        if (walkingGraph != null) {
            maxWalkingTime = config.getDoubleValue("sl_max_walking_time");
        }
        this.changeStationIds = changeStationIds;
        this.stationLimits = stationLimits;
    }
    /**
     * Generates events, drive and wait activities.
     *
     * @throws DataInconsistentException
     */
    public void generateLineConceptRepresentation()
    throws DataInconsistentException {

        Double periodLength = ean.getPeriodLength();

        if(timetableGiven){
            referenceEan.provideEventIdentificationMap();
        }

        for (Line line : lc.getDirectedLines()) {
            Integer realFrequency = line.getFrequency();
            if(realFrequency == 0 && !forceAllFrequenciesOne){

                continue;
            }

            Integer frequency;
            if (modelFrequency == ModelFrequency.FREQUENCY_AS_MULTIPLICITY) {
                frequency = realFrequency;
            } else {
                frequency = 1;
            }

            LinkedList<Link> links = line.getLinks();

            Event[] oldDepartureEvents = new Event[links.size()];
            Event[] oldArrivalEvents = new Event[links.size()];
            int currentLinkIndex = 0;
            Event arrival = null;

            Event.LineDirection direction = line.isUndirectedRepresentative() ? Event.LineDirection.FORWARDS :
                    Event.LineDirection.BACKWARDS;

            Iterator<Link> linkItr = links.iterator();

            if (linkItr.hasNext()) {
                Link link = linkItr.next();

                Station fromStation = link.getFromStation();
                Station toStation = link.getToStation();

                Integer smallestIndex = ean.getSmallestFreeEventIndex();


                Event departure = new Event(smallestIndex,
                        Event.EventType.DEPARTURE, fromStation, line, 0, null,
                        null, null, null, direction, 1);
                oldDepartureEvents[currentLinkIndex] = departure;

                arrival = new Event(smallestIndex+1,
                        Event.EventType.ARRIVAL, toStation, line, 0,
                        null, null, null, null, direction, 1);

                Activity drive = new Activity(ean
                        .getSmallestFreeActivityIndex(), ActivityType.DRIVE,
                        departure, arrival, link, link.getLowerBound(), link
                                .getUpperBound(), null, null, null);
                oldArrivalEvents[currentLinkIndex] = arrival;

                departure.setAssociatedDriveActivity(drive);
                arrival.setAssociatedDriveActivity(drive);

                ean.addEvent(departure);
                ean.addEvent(arrival);
                ean.addActivity(drive);

                if(timetableGiven){
                    departure.setTime(referenceEan.identifyEvent(departure).getTime());
                    arrival.setTime(referenceEan.identifyEvent(arrival).getTime());
                }

            }
            double localMinimalWaitTime, localMaximalWaitTime;
            while (linkItr.hasNext()) {
                Link link = linkItr.next();
                currentLinkIndex++;

                Station fromStation = link.getFromStation();
                Station toStation = link.getToStation();

                Integer smallestEventIndex = ean.getSmallestFreeEventIndex();
                Integer smallestActivityIndex = ean.getSmallestFreeActivityIndex();

                Event departure = new Event(smallestEventIndex,
                        Event.EventType.DEPARTURE, fromStation, line,
                        0, null, null, null, null, direction, 1);
                oldDepartureEvents[currentLinkIndex] = departure;

                localMinimalWaitTime = -1;
                localMaximalWaitTime = -1;

                if (stationLimits != null && stationLimits.containsKey(fromStation.getIndex())) {
                    localMinimalWaitTime = stationLimits.get(fromStation.getIndex()).getMinWaitTime();
                    localMaximalWaitTime = stationLimits.get(fromStation.getIndex()).getMaxWaitTime();
                }
                if (localMinimalWaitTime == -1) {
                    localMinimalWaitTime = minimalWaitTime;

                }
                if (localMaximalWaitTime == -1) {
                    localMaximalWaitTime = maximalWaitTime;

                }

                Activity wait = new Activity(
                        smallestActivityIndex, ActivityType.WAIT,
                        arrival, departure, null, localMinimalWaitTime, localMaximalWaitTime,
                        null, null, null);

                arrival.setAssociatedWaitActivity(wait);
                departure.setAssociatedWaitActivity(wait);

                ean.addEvent(departure);
                ean.addActivity(wait);

                arrival = new Event(smallestEventIndex+1,
                        Event.EventType.ARRIVAL, toStation, line, 0,
                        null, null, null, null, direction, 1);
                oldArrivalEvents[currentLinkIndex] = arrival;

                Activity drive = new Activity(smallestActivityIndex+1,
                        ActivityType.DRIVE, departure, arrival, link,
                        link.getLowerBound(), link
                                .getUpperBound(), null, null, null);

                departure.setAssociatedDriveActivity(drive);
                arrival.setAssociatedDriveActivity(drive);

                ean.addEvent(arrival);
                ean.addActivity(drive);

                if(timetableGiven){
                    departure.setTime(referenceEan.identifyEvent(departure).getTime());
                    arrival.setTime(referenceEan.identifyEvent(arrival).getTime());
                }

            }

            // from here on no passenger mapping is necessary anymore, since
            // frequency_as_attribute model ends here

            int minimalTimeshift = (int) Math.floor(periodLength / frequency);
            int bufferTime = (int) (periodLength - minimalTimeshift * frequency);
            double bufferTimePerFrequency = (double) bufferTime / frequency;
            double currentBufferTimer = 0;
            int currentBuffer;

            for (Integer curFreq = 1; curFreq < frequency; curFreq++) {
                currentBufferTimer += bufferTimePerFrequency;
                currentBuffer = 0;
                if (currentBufferTimer >= 1) {
                    currentBufferTimer -= 1;
                    currentBuffer = 1;
                }
                currentLinkIndex = 0;

                linkItr = links.iterator();

                if (linkItr.hasNext()) {
                    Link link = linkItr.next();

                    Integer smallestEventIndex = ean.getSmallestFreeEventIndex();
                    Integer smallestActivityIndex = ean.getSmallestFreeActivityIndex();

                    Event departure = new Event(smallestEventIndex,
                            Event.EventType.DEPARTURE, link.getFromStation(),
                            line, curFreq, null, null, null, timetableGiven ?
                                    oldDepartureEvents[currentLinkIndex].
                                    getTime() + minimalTimeshift + currentBuffer : null, direction, curFreq+1);

                    Activity sync = new Activity(smallestActivityIndex,
                            ActivityType.SYNC,
                            oldDepartureEvents[currentLinkIndex], departure,
                            null, (double) minimalTimeshift + currentBuffer, (double) minimalTimeshift + currentBuffer, 0.0,
                            null, null);
                    oldDepartureEvents[currentLinkIndex] = departure;

                    arrival = new Event(smallestEventIndex+1,
                            Event.EventType.ARRIVAL, link.getToStation(), line,
                            curFreq, null, null, null, timetableGiven ?
                                    oldArrivalEvents[currentLinkIndex].
                                    getTime() + minimalTimeshift + currentBuffer : null, direction, curFreq+1);
                    // Possible other sync activity here
                    oldArrivalEvents[currentLinkIndex] = arrival;

                    Activity drive = new Activity(smallestActivityIndex+1,
                            ActivityType.DRIVE, departure, arrival, link, link
                                    .getLowerBound(), link.getUpperBound(),
                                    null, null, null);

                    departure.setAssociatedDriveActivity(drive);
                    arrival.setAssociatedDriveActivity(drive);

                    ean.addEvent(departure);
                    ean.addActivity(sync);
                    ean.addEvent(arrival);
                    ean.addActivity(drive);
                }

                while (linkItr.hasNext()) {
                    Link link = linkItr.next();
                    currentLinkIndex++;

                    Integer smallestEventIndex = ean.getSmallestFreeEventIndex();
                    Integer smallestActivityIndex = ean.getSmallestFreeActivityIndex();

                    Event departure = new Event(
                            smallestEventIndex,
                            Event.EventType.DEPARTURE, link.getFromStation(),
                            line, curFreq, null, null, null, timetableGiven ?
                                    oldDepartureEvents[currentLinkIndex].
                                    getTime() + minimalTimeshift + currentBuffer : null, direction, curFreq+1);

                    Activity sync = new Activity(smallestActivityIndex,
                            ActivityType.SYNC,
                            oldDepartureEvents[currentLinkIndex], departure,
                            null, (double) minimalTimeshift + currentBuffer, (double) minimalTimeshift + currentBuffer, 0.0, null, null);
                    oldDepartureEvents[currentLinkIndex] = departure;

                    localMinimalWaitTime = -1;
                    localMaximalWaitTime = -1;

                    if (stationLimits != null && stationLimits.containsKey(departure.getStation().getIndex())) {
                        localMinimalWaitTime = stationLimits.get(departure.getStation().getIndex()).getMinWaitTime();
                        localMaximalWaitTime = stationLimits.get(departure.getStation().getIndex()).getMaxWaitTime();
                    }
                    if (localMinimalWaitTime == -1) {
                        localMinimalWaitTime = minimalWaitTime;

                    }
                    if (localMaximalWaitTime == -1) {
                        localMaximalWaitTime = maximalWaitTime;

                    }

                    Activity wait = new Activity(smallestActivityIndex+1,
                            ActivityType.WAIT,
                            arrival, departure, null, localMinimalWaitTime,
                        localMaximalWaitTime, null, null, null);

                    departure.setAssociatedWaitActivity(wait);
                    arrival.setAssociatedWaitActivity(wait);

                    arrival = new Event(smallestEventIndex+1,
                            Event.EventType.ARRIVAL, link.getToStation(), line,
                            curFreq, null, null, null, timetableGiven ?
                                    oldArrivalEvents[currentLinkIndex].
                                    getTime() + minimalTimeshift + currentBuffer : null, direction, curFreq+1);
                    // Possible other sync activity here
                    oldArrivalEvents[currentLinkIndex] = arrival;

                    Activity drive = new Activity(smallestActivityIndex+2,
                            ActivityType.DRIVE, departure, arrival, link, link
                                    .getLowerBound(), link.getUpperBound(),
                                    null, null, null);

                    departure.setAssociatedDriveActivity(drive);
                    arrival.setAssociatedDriveActivity(drive);

                    ean.addEvent(departure);
                    ean.addActivity(sync);
                    ean.addActivity(wait);
                    ean.addEvent(arrival);
                    ean.addActivity(drive);

                }
            }
        }

    }

    /**
     * Generates the headway activities.
     *
     * @throws DataInconsistentException
     */
    public void generateHeadways() throws DataInconsistentException {
        if(modelHeadway == ModelHeadway.NO_HEADWAYS){
            return;
        }

        LinkedHashSet<Event> events = ean.getDepartureEvents();

        for (Event departure1 : events){

            for(Event departure2 : events){

                if(departure1.getType() != EventType.DEPARTURE ||
                        departure2.getType() != EventType.DEPARTURE
                        || departure1.getIndex() >= departure2.getIndex()
                        || departure1.getLine() == departure2.getLine()) {
                    continue;
                }

                Link link = departure1.getAssociatedLink();

                if (departure2.getAssociatedLink() == link) {

                    Double headway = link.getHeadway();

                    if (modelHeadway == ModelHeadway.SIMPLE ||
                            modelHeadway == ModelHeadway.LCM_REPRESENTATION){
                        ean.addActivity(new Activity(ean
                                .getSmallestFreeActivityIndex(),
                                ActivityType.HEADWAY, departure1,
                                departure2, link, headway,
                                periodLength - headway, 0.0,
                                null, null));
                    }

                    else if (modelHeadway == ModelHeadway.PRODUCT_OF_FREQUENCIES) {

                        double f1 = modelFrequency ==
                            ModelFrequency.FREQUENCY_AS_MULTIPLICITY ? 1.0 :
                                (double) departure1.getLine().getFrequency();
                        double f2 = modelFrequency ==
                            ModelFrequency.FREQUENCY_AS_MULTIPLICITY ? 1.0 :
                                (double) departure2.getLine().getFrequency();

                        for (double i = 0.0; i < f1;++i) {

                            for (double j = 0.0; j < f2; ++j) {

                                double delta = i * periodLength / f1
                                + j * periodLength / f2;

                                ean.addActivity(new Activity(ean
                                    .getSmallestFreeActivityIndex(),
                                    ActivityType.HEADWAY, departure1,
                                    departure2, link, headway + delta,
                                    periodLength - headway + delta, 0.0,
                                    null, null));
                            }
                        }
                    }

                    else if (modelHeadway == ModelHeadway.LCM_OF_FREQUENCIES) {
                        Integer lcm = MathUtils.lcm(
                                departure1.getLine().getFrequency(),
                                departure2.getLine().getFrequency());

                        double delta = periodLength / (double) lcm;

                        for(double k=0; k < lcm; k++){
                          ean.addActivity(new Activity(ean
                          .getSmallestFreeActivityIndex(),
                          ActivityType.HEADWAY, departure1,
                          departure2, link, headway + k*delta,
                          periodLength - headway + k*delta, 0.0,
                          null, null));

                        }

                    }

                    else {
                        throw new DataInconsistentException("headway " +
                            "model " + modelHeadway + " not supported");
                    }
                }
            }
        }

    }

    /**
     * Generates changes.
     *
     * @throws DataInconsistentException
     */
    public void generateChanges() throws DataInconsistentException {
        double localMinimalChangeTime, localMaximalChangeTime;
        for(Event arrival : ean.getArrivalEvents()){
            if (changeStationIds != null && !changeStationIds.contains(arrival.getStation().getIndex())) {
                continue;
            }
            localMinimalChangeTime = -1;
            localMaximalChangeTime = -1;
            if (stationLimits != null && stationLimits.containsKey(arrival.getStation().getIndex())) {
                localMinimalChangeTime = stationLimits.get(arrival.getStation().getIndex()).getMinChangeTime();
                localMaximalChangeTime = stationLimits.get(arrival.getStation().getIndex()).getMaxChangeTime();
            }
            if (localMinimalChangeTime == -1) {
                localMinimalChangeTime = minimalChangeTime;
            }
            if (localMaximalChangeTime == -1) {
                localMaximalChangeTime = maximalChangeTime;
            }
            for(Event departure : ean.getDepartureEvents()){
//                Activity arrivalAssociatedWait = arrival.getAssociatedWaitActivity();
//                Activity departureAssociatedWait = departure.getAssociatedWaitActivity();
                if(arrival.getStation() == departure.getStation() &&
                    // For networks that contain loops this is problematic,
                    // since some changes that could be interesting are
                    // invisible to passengers. However ...
                    arrival.getLine() != departure.getLine() &&
                    // ... the code below only helps for frequency as attribute
                    // networks. For frequency as multiplicity, one would need
                    // to check the waits of other frequency instances as well.
//                    (arrivalAssociatedWait == null ||
//                    departureAssociatedWait == null ||
//                    arrivalAssociatedWait != departureAssociatedWait) &&
                    (!discardStationLoopChanges ||
                    arrival.getAssociatedDriveActivity().
                    getFromEvent().getStation() !=
                    departure.getAssociatedDriveActivity().
                    getToEvent().getStation())){

                    if(arrival.getOutgoingEventActivitiesMap().containsKey(departure)){
                        continue;
                    }

                    ean.addActivity(new Activity(ean
                        .getSmallestFreeActivityIndex(),
                        ActivityType.CHANGE, arrival, departure, null,
                        localMinimalChangeTime, localMaximalChangeTime, 0.0, null,
                        null));
                }
            }
        }
        if (this.walkingGraph != null) {
            this.generateWalkingChanges();
        }
    }

    private void generateWalkingChanges() throws DataInconsistentException {
        Map<Integer, List<Event>> arrivalEvents = new HashMap<>();
        Map<Integer, List<Event>> departureEvents = new HashMap<>();
        for (Event event: ean.getEvents()) {
            int stopId = event.getStation().getIndex();
            if(changeStationIds != null && !changeStationIds.contains(stopId)){
                continue;
            }
            if (event.getType() == EventType.ARRIVAL) {
                arrivalEvents.computeIfAbsent(stopId, ArrayList::new).add(event);
            }
            else {
                departureEvents.computeIfAbsent(stopId, ArrayList::new).add(event);
            }
        }
        for (Station walkingStartStop: ptn.getStations()) {
            if (arrivalEvents.get(walkingStartStop.getIndex()) == null) {
                continue;
            }
            int startNodeId = Integer.parseInt(walkingStartStop.getLongName());
            InfrastructureNode startNode = walkingGraph.getNode(startNodeId);
            Dijkstra<InfrastructureNode, WalkingEdge, Graph<InfrastructureNode, WalkingEdge>> dijkstra = new Dijkstra<>(walkingGraph, startNode, WalkingEdge::getLength);
            for (Station walkingEndStop: ptn.getStations()) {
                if (departureEvents.get(walkingEndStop.getIndex()) == null || walkingStartStop.equals(walkingEndStop)) {
                    continue;
                }
                int endNodeId = Integer.parseInt(walkingEndStop.getLongName());
                double distance = dijkstra.computeShortestPath(walkingGraph.getNode(endNodeId));
                if (distance <= maxWalkingTime) {
                    // Now iterate all events at the two stops and build the change activities when appropriate
                    System.out.println("Building walking changes between " + walkingStartStop + " and " + walkingEndStop);
                    for (Event arrivalEvent: arrivalEvents.get(walkingStartStop.getIndex())) {
                        for (Event departureEvent: departureEvents.get(walkingEndStop.getIndex())) {
                            if(arrivalEvent.getOutgoingEventActivitiesMap().containsKey(departureEvent)){
                                continue;
                            }
                            ean.addActivity(new Activity(ean.getSmallestFreeActivityIndex(), ActivityType.CHANGE,
                                arrivalEvent, departureEvent, null, distance,
                                periodLength + distance - 1, 0., null, null));
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setEventActivityNetwork(EventActivityNetwork ean)
            throws DataInconsistentException {
        if (!ean.isPeriodic()) {
            throw new DataInconsistentException("event activity network "
                    + "not periodic");
        }
        this.ean = ean;
        this.periodLength = ean.getPeriodLength();
    }

    public void setLineConcept(LineCollection lc) {
        this.lc = lc;
    }

    public void setMinimalChangeTime(Double minimalChangeTime) {
        this.minimalChangeTime = minimalChangeTime;
    }

    public void setMaximalChangeTime(Double maximalChangeTime) {
        this.maximalChangeTime = maximalChangeTime;
    }

    public void setMinimalWaitTime(Double minimalWaitTime) {
        this.minimalWaitTime = minimalWaitTime;
    }

    public void setMaximalWaitTime(Double maximalWaitTime) {
        this.maximalWaitTime = maximalWaitTime;
    }

    public void setReferenceEventActivityNetwork(
            EventActivityNetwork referenceEan) {

        this.referenceEan = referenceEan;
        if(referenceEan != null){
            timetableGiven = referenceEan.timetableGiven();
            referenceLc = referenceEan.getLineConcept();
        }
        else{
            timetableGiven = false;
            referenceLc = null;
        }
    }

    public void setPublicTransportationNetwork(PublicTransportationNetwork ptn) {
        this.ptn = ptn;
    }

    public void setForceAllFrequenciesOne(Boolean forceAllFrequenciesOne) {
        this.forceAllFrequenciesOne = forceAllFrequenciesOne;
    }

    public void setFilterStationLoopChanges(Boolean filterStationLoopChanges) {
        this.discardStationLoopChanges = filterStationLoopChanges;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public EventActivityNetwork getEventActivityNetwork() {
        return ean;
    }

    public Double getMinimalChangeTime() {
        return minimalChangeTime;
    }

    public Double getMaximalChangeTime() {
        return maximalChangeTime;
    }

    public ModelFrequency getModelFrequency() {
        return modelFrequency;
    }

    public ModelHeadway getModelHeadway() {
        return modelHeadway;
    }

    public Double getMinimalWaitTime() {
        return minimalWaitTime;
    }

    public Double getMaximalWaitTime() {
        return maximalWaitTime;
    }

    public EventActivityNetwork getReferenceEventActivityNetwork() {
        return referenceEan;
    }

    public PublicTransportationNetwork getPublicTransportationNetwork() {
        return ptn;
    }

    public Boolean getForceAllFrequenciesOne() {
        return forceAllFrequenciesOne;
    }

    public Boolean getFilterStationLoopChanges() {
        return discardStationLoopChanges;
    }

    public void setDiscardStationLoopChanges(Boolean discardStationLoopChanges) {
        this.discardStationLoopChanges = discardStationLoopChanges;
    }

}
