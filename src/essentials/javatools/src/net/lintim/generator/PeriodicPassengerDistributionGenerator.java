package net.lintim.generator;

import net.lintim.exception.DataInconsistentException;
import net.lintim.graph.GraphMalformedException;
import net.lintim.graph.ShortestPathsGraph;
import net.lintim.model.*;
import net.lintim.model.Activity.ActivityType;
import net.lintim.util.IterationProgressCounter;
import net.lintim.util.MathHelper;
import net.lintim.util.NullIterationProgressCounter;

import java.util.*;

/**
 * Generates a periodic passenger distribution by either routing passengers
 * w.r.t. timetable shortest paths or an initial duration assumption.
 */
public class PeriodicPassengerDistributionGenerator {

    public enum InitialWeightChange {
        FORMULA_1, FORMULA_2, FORMULA_3, MINIMAL_CHANGING_TIME
    }

    public enum InitialWeightDrive {
        MINIMAL_DRIVING_TIME, AVERAGE_DRIVING_TIME, MAXIMAL_DRIVING_TIME, EDGE_LENGTH
    }

    public enum InitialWeightWait {
        MINIMAL_WAITING_TIME, AVERAGE_WAITING_TIME, MAXIMAL_WAITING_TIME, ZERO_COST
    }

    public enum ModelInitialDurationAssumption {
        AUTOMATIC, SEMI_AUTOMATIC
    }

    protected EventActivityNetwork ean;
    protected PublicTransportationNetwork ptn;
    protected OriginDestinationMatrix od;
    protected Boolean useTimetable;
    protected Random random = null;             // works only with Tree Map Queue method!

    protected Boolean silent = false;
    protected Boolean rememberOdLinkPaths;
    protected Boolean rememberOdActivityPaths;
    protected Double changePenalty;

    protected InitialWeightChange initialWeightChange;
    protected InitialWeightDrive initialWeightDrive;
    protected InitialWeightWait initialWeightWait;
    protected ModelInitialDurationAssumption modelInitialDurationAssumption;
    protected ShortestPathsGraph.ShortestPathsMethod shortestPathsMethod;

    protected IterationProgressCounter iterationProgressCounter =
        new NullIterationProgressCounter();

    /**
     * Constructor.
     *
     * @param ean The event activity network.
     * @param od The origin destination matrix.
     * @param useTimetable Whether or not to route passengers on shortest paths
     * w.r.t. a timetable.
     * @param rememberOdLinkPaths Whether or not to remember link paths in the
     * public transportation network.
     * @param rememberOdActivityPaths Whether or not to remember activity paths
     * in the event activity network.
     * @param changePenalty Change penalty. Applied in
     * <code>InitialWeightChange.FORMULA_1</code>.
     * @param initialWeightChange The initial duration assumption for change
     * activities.
     * @param initialWeightDrive The initial duration assumption for drive
     * activities.
     * @param initialWeightWait The initial duration assumption for wait
     * activities.
     * @param modelInitialDurationAssumption The initial duration assumption
     * model.
     * @param shortestPathsMethod The shortest paths method.
     * @throws DataInconsistentException
     */
    public PeriodicPassengerDistributionGenerator(EventActivityNetwork ean,
            OriginDestinationMatrix od, Boolean useTimetable,
            Boolean rememberOdLinkPaths, Boolean rememberOdActivityPaths,
            Double changePenalty,
            InitialWeightChange initialWeightChange,
            InitialWeightDrive initialWeightDrive,
            InitialWeightWait initialWeightWait,
            ModelInitialDurationAssumption modelInitialDurationAssumption,
            ShortestPathsGraph.ShortestPathsMethod shortestPathsMethod)
            throws DataInconsistentException {

        setEventActivityNetwork(ean);
        this.ptn = ean.getPublicTransportationNetwork();
        setOriginDestinationMatrix(od);
        setUseTimetable(useTimetable);
        setRememberOrginDestinationLinkPaths(rememberOdLinkPaths);
        setRememberOrginDestinationActivityPaths(rememberOdActivityPaths);
        setChangePenalty(changePenalty);
        setInitialWeightChange(initialWeightChange);
        setInitialWeightDrive(initialWeightDrive);
        setInitialWeightWait(initialWeightWait);
        setModelInitialDurationAssumption(modelInitialDurationAssumption);
        this.shortestPathsMethod = shortestPathsMethod;
    }

    /**
     * Constructor. Redirects to {@link
     * #PeriodicPassengerDistributionGenerator(EventActivityNetwork,
     * OriginDestinationMatrix, Boolean, Boolean, Boolean, Double,
     * InitialWeightChange, InitialWeightDrive, InitialWeightWait,
     * ModelInitialDurationAssumption,
     * net.lintim.graph.ShortestPathsGraph.ShortestPathsMethod)
     *
     * @param ean The event activity network.
     * @param od The origin destination matrix.
     * @param config The configuration.
     * @throws DataInconsistentException
     */
    public PeriodicPassengerDistributionGenerator(EventActivityNetwork ean,
            OriginDestinationMatrix od, Configuration config)
            throws DataInconsistentException {
        this(ean, od, config.getBooleanValue("ean_use_timetable"),
                config.getBooleanValue("ptn_remember_od_paths"),
                config.getBooleanValue("ean_remember_od_paths"),
                config.getDoubleValue("ean_change_penalty"),
                InitialWeightChange.valueOf(config.getStringValue(
                        "ean_model_weight_change").toUpperCase()),
                InitialWeightDrive.valueOf(config.getStringValue(
                        "ean_model_weight_drive").toUpperCase()),
                InitialWeightWait.valueOf(config.getStringValue(
                        "ean_model_weight_wait").toUpperCase()),
                ModelInitialDurationAssumption.valueOf(config.getStringValue(
                        "ean_initial_duration_assumption_model").toUpperCase()),
                        ShortestPathsGraph.ShortestPathsMethod.valueOf(
                                config.getStringValue("ean_algorithm_shortest_paths")));
    }

    protected Double getWeight(Activity activity)
            throws DataInconsistentException {

        if(modelInitialDurationAssumption == ModelInitialDurationAssumption.
                SEMI_AUTOMATIC){
            Double initialDurationAssumption = activity.getInitialDurationAssumption();
            if(initialDurationAssumption != null){
                return initialDurationAssumption;
            }
        }

        if (useTimetable) {
            Double retval = activity.getDuration();
            if(activity.getType().equals(ActivityType.CHANGE)){
                retval += getChangePenalty();
            }
            activity.setInitialDurationAssumption(retval);
            return retval;
        } else {
            Double retval = null;
            switch (activity.getType()) {
            case DRIVE:
                switch (initialWeightDrive) {
                case MINIMAL_DRIVING_TIME:
                    retval = activity.getLowerBound();
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case AVERAGE_DRIVING_TIME:
                    retval = (activity.getLowerBound() +
                            activity.getUpperBound()) / 2;
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case MAXIMAL_DRIVING_TIME:
                    retval = activity.getUpperBound();
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case EDGE_LENGTH:
                    retval = activity.getAssociatedLink().getLength();
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                }
            case WAIT:
                switch (initialWeightWait) {
                case MINIMAL_WAITING_TIME:
                    retval = activity.getLowerBound();
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case AVERAGE_WAITING_TIME:
                    retval = (activity.getLowerBound() +
                            activity.getUpperBound()) / 2;
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case MAXIMAL_WAITING_TIME:
                    retval = activity.getUpperBound();
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case ZERO_COST:
                    retval = 0.0;
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                }
            case CHANGE:
                Double f_1 = (double) activity.getFromEvent().getLine()
                        .getFrequency();
                Double f_2 = (double) activity.getToEvent().getLine()
                        .getFrequency();
                Double T = ean.getPeriodLength();
                retval = changePenalty;

                switch (initialWeightChange) {
                case FORMULA_1:
                    retval += T / f_1 + T / f_2;
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case FORMULA_2:
                    retval += T / (2 * f_1 * f_2);
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case FORMULA_3:
                    retval += T / (2 * f_2);
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                case MINIMAL_CHANGING_TIME:
                    retval += activity.getLowerBound();
                    activity.setInitialDurationAssumption(retval);
                    return retval;
                }
            default:
                throw new DataInconsistentException("activity type not "
                        + "supported for passengers");
            }
        }

    }

    /**
     * Does the actual computation.
     *
     * @throws DataInconsistentException
     */
    public void computePassengerDistribution() throws DataInconsistentException {

        LinkedHashSet<Event> events = ean.getEvents();

        ShortestPathsGraph<Event, Activity> sp =
            new ShortestPathsGraph<Event, Activity>();

        sp.setRandom(random);
        sp.setMethod(shortestPathsMethod);

        for (Event event : events) {
            sp.addVertex(event);
            event.setPassengers(0.0);
        }

        for (Activity activity : ean.getActivities()) {
            if (activity.isPassengerUsable()) {

                Double weight = getWeight(activity);

                sp.addEdge(activity, activity.getFromEvent(),
                        activity.getToEvent(), weight);

                activity.setPassengers(0.0);
            }
        }

        LinkedHashMap<Station, Event> sourceEvents =
            new LinkedHashMap<Station, Event>();
        LinkedHashMap<Station, Event> sinkEvents =
            new LinkedHashMap<Station, Event>();

        for (Map.Entry<Station, LinkedHashSet<Event>> e1 : ean
                .getStationDepartureEventMap().entrySet()) {

            Station s1 = e1.getKey();
            LinkedHashSet<Event> departures = e1.getValue();

            Event sourceEvent = new Event(null, null, s1, null, null, null,
                    null, null, null, null, -1);

            sourceEvents.put(s1, sourceEvent);
            sp.addVertex(sourceEvent);

            for (Event departure : departures) {
                sp.addEdge(null, sourceEvent, departure, 0.0);
            }
        }

        for (Map.Entry<Station, LinkedHashSet<Event>> e1 : ean
                .getStationArrivalEventMap().entrySet()) {

            Station s1 = e1.getKey();
            LinkedHashSet<Event> arrivals = e1.getValue();

            Event sinkEvent = new Event(null, null, s1, null, null, null,
                    null, null, null, null, -1);

            sinkEvents.put(s1, sinkEvent);
            sp.addVertex(sinkEvent);

            for (Event arrival : arrivals) {
                sp.addEdge(null, arrival, sinkEvent, 0.0);
            }
        }

        iterationProgressCounter.setTotalNumberOfIterations(
                ptn.getStations().size());

        for (Map.Entry<Station, Event> e1 : sourceEvents.entrySet()) {
            iterationProgressCounter.reportIteration();

            Station s1 = e1.getKey();
            Event source = e1.getValue();

            try {
                sp.compute(source);
            } catch (GraphMalformedException e) {
                throw new DataInconsistentException(
                        "shortest paths computation: " + e.getMessage());
            }

            for (Map.Entry<Station, Event> e2 : sinkEvents.entrySet()) {

                Station s2 = e2.getKey();
                Event sink = e2.getValue();
                Double passengers = od.get(s1, s2);

                if (s1 == s2 || passengers.doubleValue() < MathHelper.epsilon) {
                    continue;
                }

                LinkedList<Activity> path = sp.trackPath(sink);

                if (path.size() == 0) {
                    throw new DataInconsistentException("there is no "
                            + "path from station " + s1.getIndex() + " to "
                            + s2.getIndex());
                }

                Iterator<Activity> itr = path.descendingIterator();

                // Skip last activity, it is null anyway.
                itr.next();

                Activity activity = itr.next();
                // Set number of alighting passengers
                Event arrival = activity.getToEvent();
                arrival.setPassengers(arrival.getPassengers() + passengers);

                LinkedHashSet<Link> linkPath = rememberOdLinkPaths ?
                    linkPath = new LinkedHashSet<Link>() : null;

                LinkedHashSet<Activity> activityPath = rememberOdActivityPaths ?
                    activityPath = new LinkedHashSet<Activity>() : null;

                while (itr.hasNext() && activity != null) {
                    activity.setPassengers(activity.getPassengers()
                            + passengers);
                    if (rememberOdLinkPaths && activity.getType() ==
                        ActivityType.DRIVE) {
                        linkPath.add(activity.getAssociatedLink());
                    }
                    if (rememberOdActivityPaths && activity.getType() != null) {
                        activityPath.add(activity);
                    }
                    activity = itr.next();
                }

                if (rememberOdLinkPaths) {
                    ptn.addOriginDestinationPath(s1, s2, linkPath);
                }

                if (rememberOdActivityPaths) {
                    ean.addOriginDestinationPath(s1, s2, activityPath);
                }

            }
        }

        if(rememberOdLinkPaths){
            ptn.completeOriginDestinationPathMap();
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
    }

    public void setOriginDestinationMatrix(OriginDestinationMatrix od)
            throws DataInconsistentException {
        if (od.getPublicTransportationNetwork() != ptn) {
            throw new DataInconsistentException("origin destination matrix "
                    + "does not belong to event activity network");
        }
        this.od = od;
    }

    public void setUseTimetable(Boolean useTimetable)
            throws DataInconsistentException {
        if (useTimetable && !ean.timetableGiven()) {
            throw new DataInconsistentException("timetable usage requested "
                    + "but no timetable available");
        }
        this.useTimetable = useTimetable;
    }

    public void setChangePenalty(Double changePenalty) {
        this.changePenalty = changePenalty;
    }

    public void setInitialWeightChange(InitialWeightChange initialWeightChange)
            throws DataInconsistentException {
        this.initialWeightChange = initialWeightChange;
    }

    public void setInitialWeightDrive(InitialWeightDrive initialWeightDrive) {
        this.initialWeightDrive = initialWeightDrive;
    }

    public void setInitialWeightWait(InitialWeightWait initialWeightWait) {
        this.initialWeightWait = initialWeightWait;
    }

    public void setRememberOrginDestinationLinkPaths(Boolean rememberOdPaths) {
        this.rememberOdLinkPaths = rememberOdPaths;
    }

    public void setModelInitialDurationAssumption(
            ModelInitialDurationAssumption modelInitialDurationAssumption) {
        this.modelInitialDurationAssumption = modelInitialDurationAssumption;
    }

    public void setSilent(Boolean silent) {
        this.silent = silent;
    }

    public void setRandom(Random random) {
        this.random = random;
    }

    public void setIterationProgressCounter(
            IterationProgressCounter iterationProgressCounter) {
        this.iterationProgressCounter = iterationProgressCounter;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public EventActivityNetwork getEventActivityNetwork() {
        return ean;
    }

    public Boolean getUseTimetable() {
        return useTimetable;
    }

    public Double getChangePenalty() {
        return changePenalty;
    }

    public InitialWeightChange getInitialWeightChange() {
        return initialWeightChange;
    }

    public InitialWeightDrive getInitialWeightDrive() {
        return initialWeightDrive;
    }

    public InitialWeightWait getInitialWeightWait() {
        return initialWeightWait;
    }

    public OriginDestinationMatrix getOriginDestinationMatrix() {
        return od;
    }

    public Boolean getRememberOrginDestinationPaths() {
        return rememberOdLinkPaths;
    }

    public void setRememberOrginDestinationActivityPaths(
            Boolean rememberOdActivityPaths) {
        this.rememberOdActivityPaths = rememberOdActivityPaths;

    }

    public ModelInitialDurationAssumption getModelInitialDurationAssumption() {
        return modelInitialDurationAssumption;
    }

    public Boolean getSilent() {
        return silent;
    }

}
