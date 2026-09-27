package net.lintim.generator;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.TreeSet;

import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.graph.IntegralCycleBasis;
import net.lintim.model.Activity;
import net.lintim.model.Configuration;
import net.lintim.model.Event;
import net.lintim.model.EventActivityNetwork;
import net.lintim.model.EventActivityNetwork.ModelChange;
import net.lintim.model.EventActivityNetwork.ModelFrequency;
import net.lintim.model.EventActivityNetwork.ModelHeadway;
import net.lintim.model.LineCollection;
import net.lintim.util.BiLinkedHashMap;
import net.lintim.util.IterationProgressCounter;
import net.lintim.util.MathHelper;
import net.lintim.util.NullIterationProgressCounter;

/**
 * Generates a periodic timetable by solving the PESP.
 */
public abstract class PeriodicTimetableGenerator {

    public enum Solver{
        GUROBI,
        CPLEX,
        XPRESS
    }

    public enum LinearModel {
        EPESP, // Extended Periodic Event Scheduling Problem
        CPF // Cycle Periodicity Formulation
    }

    public enum ObjectiveFunctionModel {
        TRAVELING_TIME,
        SLACK
    }

    public enum CyclebaseModel {
        UNEXPLORED_VERTICES,
        MSF_FUNDAMENTAL_IMPROVEMENT
    }

    EventActivityNetwork ean;
    LinkedHashSet<Event> events;
    LinkedHashSet<Activity> activities;
    ModelHeadway modelHeadway;
    ModelChange modelChange;
    ModelFrequency modelFrequency;
    Boolean useInitialTimetable;
    Double slackDifference = 0.0;
    Double periodLength;
    Boolean forceEqualities = false;
    Boolean fixModuloFromInitialTimetable = false;
    Boolean timetableGiven;
    Double threshold;

    Solver solver;
    LinearModel linearModel;
    ObjectiveFunctionModel objectiveFunctionModel = ObjectiveFunctionModel.SLACK;
    CyclebaseModel cyclebaseModel;
    Double objectiveFunction = null;

    OutputStream stream = System.err;

    LinkedHashSet<Activity> forestEdges = new LinkedHashSet<Activity>();
    LinkedHashSet<Event> forestNodes = new LinkedHashSet<Event>();
    LinkedHashMap<Integer, Activity> nonForestEdges =
        new LinkedHashMap<Integer, Activity>();
    BiLinkedHashMap<Integer, Activity, Boolean> cycles =
        new BiLinkedHashMap<Integer, Activity, Boolean>();
    LinkedHashSet<Activity> edgeInSomeCycle = new LinkedHashSet<Activity>();
    LinkedHashMap<Integer, Double> a_C = new LinkedHashMap<Integer, Double>();
    LinkedHashMap<Integer, Double> b_C = new LinkedHashMap<Integer, Double>();

    Boolean cyclebaseReady = false;
    protected Boolean initialized = false;

    IterationProgressCounter iterationProgressCounter =
        new NullIterationProgressCounter();

    /**
     * To be called after instantiation. Not a constructor due to the necessity
     * of a class loader for different solvers.
     *
     * @param ean The event activity network.
     * @param solver The solver to use.
     * @param linearModel The linear model.
     * @param cyclebaseModel The cyclebase model.
     * @param useInitialTimetable <code>true</code> if an initial timetable
     * should be used, <code>false</code> otherwise.
     * @param fixModuloFromInitialTimetable <code>true</code> if modulo
     * parameters should be taken and fixed from the initial timetable,
     * <code>false</code> otherwise.
     * @param threshold Indicator of which amount of optinal activities should
     * be taken into account when calculating the cyclebase.
     */
    public void initialize(EventActivityNetwork ean,
            Solver solver, LinearModel linearModel,
            CyclebaseModel cyclebaseModel, Boolean useInitialTimetable,
            Boolean fixModuloFromInitialTimetable, Double threshold) {

        if(initialized){
            return;
        }

        this.periodLength = ean.getPeriodLength();
        this.threshold = threshold;
        this.ean = ean;
        this.events = ean.getEvents();

        TreeSet<Activity> sortedActivities =
            new TreeSet<Activity>(new Comparator<Activity>() {
                @Override
                public int compare(Activity o1, Activity o2) {
                    int delta=(int)Math.signum(o2.getPassengers()
                            - o1.getPassengers());
                    if(delta == 0){
                        return 1;
                    }
                    else{
                        return delta;
                    }
                }
            });

        this.activities = new LinkedHashSet<Activity>();
        double passengerOverallSum = 0.0;

        for(Activity a : ean.getActivities()){
            if(Math.round(a.getUpperBound()-a.getLowerBound())
                    >= Math.round(periodLength-1)){
                double passengers = a.getPassengers();

                if(Math.abs(passengers) < MathHelper.epsilon){
                    continue;
                }
                sortedActivities.add(a);
                passengerOverallSum += passengers;
            }
            else{
                activities.add(a);
            }
        }

        double currentSum = 0.0;
        for(Activity a : sortedActivities){
            double passengers = a.getPassengers();
            if(currentSum/passengerOverallSum >= threshold){
                break;
            }
            else{
                activities.add(a);
            }
            currentSum += passengers;
        }

        for(Activity a : activities){
            slackDifference += a.getLowerBound()*a.getPassengers();
        }

        setSolver(solver);
        setLinearModel(linearModel);
        setModelHeadway(ean.getModelHeadway());
        setModelChange(ean.getModelChange());
        setModelFrequency(ean.getModelFrequency());
        setCyclebaseModel(cyclebaseModel);
        setUseInitialTimetable(useInitialTimetable);
        setFixModuloFromInitialTimetable(fixModuloFromInitialTimetable);

        makeCyclebase();

        initialized=true;
    }

    /**
     * To be called after instantiation, redirects to {@link #initialize(
     * EventActivityNetwork, Solver, LinearModel, CyclebaseModel, Boolean,
     * Boolean, Double)}.
     *
     * @param ean The event activity network.
     * @param config The configuration.
     * @throws DataInconsistentException
     */
    public void initialize(EventActivityNetwork ean,
            Configuration config) throws DataInconsistentException{

        initialize(ean, Solver.valueOf(config.getStringValue("tim_solver").
                toUpperCase()), LinearModel.valueOf(
                    config.getStringValue("tim_linear_model").toUpperCase()),
                CyclebaseModel.valueOf(config.getStringValue(
                "tim_cyclebase_model").trim().toUpperCase()),
                config.getBooleanValue("tim_use_old_solution"),
                config.getBooleanValue("tim_fix_old_modulo"),
                config.getDoubleValue("tim_passenger_threshold"));

    }

    /**
     * Generates the cyclebase depending on the {@link #cyclebaseModel} choice.
     */
    public void makeCyclebase(){

        if(cyclebaseReady){
            return;
        }

        switch(cyclebaseModel){
        case UNEXPLORED_VERTICES:
            makeCyclebaseUnexploredVertices();
            break;
        case MSF_FUNDAMENTAL_IMPROVEMENT:
            makeCyclebaseMsfFundamentalImprovement();
            break;
        }

        cyclebaseReady = true;

    }

    /**
     * Generates a cycle base with a depth first search.
     */
    protected void makeCyclebaseUnexploredVertices(){

        if(events.isEmpty()){
            return;
        }

        LinkedHashMap<Event, Event> predecessorNodes =
            new LinkedHashMap<Event, Event>();
        LinkedHashMap<Event, Activity> predecessorEdges =
            new LinkedHashMap<Event, Activity>();
        LinkedHashMap<Activity, Boolean> orientations =
            new LinkedHashMap<Activity, Boolean>();

        LinkedHashSet<Event> unreached = new LinkedHashSet<Event>(events);

        // build forest
        while(!unreached.isEmpty()){
            LinkedHashSet<Event> reached = new LinkedHashSet<Event>();
            LinkedHashSet<Event> oldLeaves = new LinkedHashSet<Event>();
            Event firstNode = unreached.iterator().next();
            forestNodes.add(firstNode);
            oldLeaves.add(firstNode);
            reached.add(firstNode);
            predecessorNodes.put(firstNode, null);
            predecessorEdges.put(firstNode, null);

            Boolean treeComplete;

            if(firstNode.getOutgoingActivities().isEmpty() &&
                    firstNode.getIncomingActivities().isEmpty()){
                treeComplete = true;
            }
            else {
                treeComplete = false;
            }

            // build tree
            while(!treeComplete){
                treeComplete = true;

                LinkedHashSet<Event> currentLeaves =
                    new LinkedHashSet<Event>(oldLeaves);
                oldLeaves.clear();

                for(Event currentNode : currentLeaves){

                    LinkedHashSet<Activity> outgoingSubset =
                        new LinkedHashSet<Activity>(
                                currentNode.getOutgoingActivities());
                    // Make sure we work on our proper subgraph
                    // TODO make a proper copy of the event activity network
                    outgoingSubset.retainAll(activities);

                    for(Activity outgoingEdge : outgoingSubset){
                        Event targetNode = outgoingEdge.getToEvent();
                        if(!reached.contains(targetNode)){
                            treeComplete = false;
                            reached.add(targetNode);
                            oldLeaves.add(targetNode);
                            predecessorNodes.put(targetNode, currentNode);
                            predecessorEdges.put(targetNode, outgoingEdge);
                            orientations.put(outgoingEdge, true);
                        }
                    }

                    LinkedHashSet<Activity> incomingSubset =
                        new LinkedHashSet<Activity>(
                                currentNode.getIncomingActivities());
                    incomingSubset.retainAll(activities);

                    for(Activity incomingEdge : incomingSubset){
                        Event targetNode = incomingEdge.getFromEvent();
                        if(!reached.contains(targetNode)){
                            treeComplete = false;
                            reached.add(targetNode);
                            oldLeaves.add(targetNode);
                            predecessorNodes.put(targetNode, currentNode);
                            predecessorEdges.put(targetNode, incomingEdge);
                            orientations.put(incomingEdge, false);
                        }
                    }

                }
            }

            unreached.removeAll(reached);
            reached.clear();
        }

        // build cycles
        LinkedHashSet<Activity> cycleBaseEdges =
            new LinkedHashSet<Activity>(activities);
        forestEdges.clear();
        forestEdges.addAll(predecessorEdges.values());
        forestEdges.remove(null);
        cycleBaseEdges.removeAll(forestEdges);
        Integer cyclecount = 1;

        for(Activity cycleBaseEdge : cycleBaseEdges){
            Event fromNode = cycleBaseEdge.getFromEvent();
            LinkedHashSet<Activity> fromPath = new LinkedHashSet<Activity>();

            Activity predecessorEdge = predecessorEdges.get(fromNode);
            Event predecessorNode = predecessorNodes.get(fromNode);

            while(predecessorEdge != null){
                fromPath.add(predecessorEdge);
                predecessorEdge = predecessorEdges.get(predecessorNode);
                predecessorNode = predecessorNodes.get(predecessorNode);
            }

            Event toNode = cycleBaseEdge.getToEvent();
            LinkedHashSet<Activity> toPath = new LinkedHashSet<Activity>();

            predecessorEdge = predecessorEdges.get(toNode);
            predecessorNode = predecessorNodes.get(toNode);

            while(predecessorEdge != null){
                toPath.add(predecessorEdge);
                predecessorEdge = predecessorEdges.get(predecessorNode);
                predecessorNode = predecessorNodes.get(predecessorNode);
            }

            LinkedHashSet<Activity> commonPart =
                new LinkedHashSet<Activity>(fromPath);
            commonPart.retainAll(toPath);

            fromPath.removeAll(commonPart);
            toPath.removeAll(commonPart);

            LinkedHashMap<Activity, Boolean> cycle =
                new LinkedHashMap<Activity, Boolean>();

            cycle.put(cycleBaseEdge, true);
            edgeInSomeCycle.add(cycleBaseEdge);

            for(Activity edge : fromPath){
                cycle.put(edge, orientations.get(edge));
                edgeInSomeCycle.add(edge);
            }

            for(Activity edge : toPath){
                cycle.put(edge, !orientations.get(edge));
                edgeInSomeCycle.add(edge);
            }

            cycles.put(cyclecount, cycle);

            Double a = 0.0;
            Double b = 0.0;
            for(Entry<Activity, Boolean> e : cycle.entrySet()){
                Activity activity = e.getKey();
                Boolean orientation = e.getValue();

                if(orientation){
                    a += activity.getLowerBound();
                    b += activity.getUpperBound();
                }
                else{
                    a -= activity.getUpperBound();
                    b -= activity.getLowerBound();
                }

            }
            a /= periodLength;
            b /= periodLength;

            nonForestEdges.put(cyclecount, cycleBaseEdge);
            a_C.put(cyclecount, a);
            b_C.put(cyclecount, b);

            cyclecount++;
        }

    }

    /**
     * Computes the cyclebase with {@link IntegralCycleBasis}.
     */
    protected void makeCyclebaseMsfFundamentalImprovement(){

        if(events.isEmpty()){
            return;
        }

        IntegralCycleBasis<Event, Activity> icb =
            new IntegralCycleBasis<Event, Activity>();

        icb.setIterationProgressCounter(iterationProgressCounter);

        for(Event event : events){
            icb.addVertex(event);
        }

        for(Activity activity : activities){
            icb.addEdge(activity, activity.getFromEvent(),
                    activity.getToEvent(), activity.getUpperBound()
                    -activity.getLowerBound());
        }

        icb.compute();
        Integer cycleCount = 1;

        for(LinkedHashMap<Activity, Boolean> cycle : icb.getCycles()){
            cycles.put(cycleCount, cycle);
            Double a = 0.0;
            Double b = 0.0;

            for(Entry<Activity, Boolean> e : cycle.entrySet()){
                Activity activity = e.getKey();
                Boolean orientation = e.getValue();

                edgeInSomeCycle.add(activity);

                if(orientation){
                    a += activity.getLowerBound();
                    b += activity.getUpperBound();
                }
                else{
                    a -= activity.getUpperBound();
                    b -= activity.getLowerBound();
                }

            }
            a = Math.ceil(a / periodLength);
            b = Math.floor(b / periodLength);

            a_C.put(cycleCount, a);
            b_C.put(cycleCount, b);

            cycleCount++;
        }

    }

    /**
     * Solves the actual problem with {@link #solveInternal()} after checking
     * whether {@link #initialize(LineCollection, Configuration)} has been run
     * and verifies objective function integrity.
     *
     * @throws DataInconsistentException
     */
    public void solve() throws DataInconsistentException{
        if(!initialized){
            throw new DataInconsistentException("please run the initialize " +
                    "method before solve");
        }

        if(useInitialTimetable && !ean.timetableGiven()){
            throw new DataInconsistentException("requested to use " +
            "initial timetable, but no timetable given");
        }

        if(fixModuloFromInitialTimetable && !ean.timetableGiven()){
            throw new DataInconsistentException("requested to use " +
            "initial modulo, but no timetable given");
        }

        timetableGiven = ean.timetableGiven();

        solveInternal();

        if(modelChange == ModelChange.LCM_SIMPLIFICATION){
            ean.computeDurationsFromTimetable();
        }
        else if(linearModel == LinearModel.CPF){
            ean.computeTimetableFromDurations();
        }

        Double referenceObjectiveFunction = null;

        switch(objectiveFunctionModel){
        case TRAVELING_TIME:
            referenceObjectiveFunction =
                PeriodicTimetableEvaluator.averageTravelingTime(activities);
            break;
        case SLACK:
            referenceObjectiveFunction =
                PeriodicTimetableEvaluator.weightedSlackTime(activities);
            break;
        default:
            throw new DataInconsistentException("objective function model unknown");
        }

        if(Math.abs(referenceObjectiveFunction - objectiveFunction) >
        MathHelper.epsilon){
            throw new DataInconsistentException("timetabling objective " +
                    "function invalid: is " + objectiveFunction +
                    " but should be " + referenceObjectiveFunction);
        }
    }

    protected abstract void solveInternal() throws DataInconsistentException;

    // =========================================================================
    // === Helpers =============================================================
    // =========================================================================
    public Double circularSlack(Collection<Activity> activities){
        Double retval = 0.0;
        for(Activity a : activities){
            retval += a.getPassengers()*(a.getDuration()-a.getLowerBound());
        }
        return retval;
    }

    public Double circularLowerBoundsSum(
            LinkedHashMap<Activity, Boolean> circleActivities){

        Double retval = 0.0;
        for(Entry<Activity, Boolean> e1 : circleActivities.entrySet()){
            retval += (e1.getValue() ? 1 : -1)*e1.getKey().getLowerBound();
        }
        return retval;
    }

    public Double logarithmicBase10CyclebasisWidth(){

        if(!cyclebaseReady){
            makeCyclebase();
        }

        Double retval = 0.0;

        for(Entry<Integer, LinkedHashMap<Activity, Boolean>> cycle :
            cycles.entrySet()){
            Integer index = cycle.getKey();
            retval += Math.log10(b_C.get(index)-a_C.get(index)+1);
        }

        return retval;
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setSolver(Solver solver) {
        this.solver = solver;
    }

    public void setCyclebaseModel(CyclebaseModel cyclebaseModel) {
        this.cyclebaseModel = cyclebaseModel;
    }

    public void setLinearModel(LinearModel linearModel) {
        this.linearModel = linearModel;
    }

    public void setModelHeadway(ModelHeadway modelHeadway) {
        this.modelHeadway = modelHeadway;
    }

    public void setModelChange(ModelChange modelChange) {
        this.modelChange = modelChange;
    }

    public void setUseInitialTimetable(Boolean useInitialTimetable) {
        this.useInitialTimetable = useInitialTimetable;
    }

    public void setFixModuloFromInitialTimetable(
            Boolean fixModuloFromInitialTimetable) {
        this.fixModuloFromInitialTimetable = fixModuloFromInitialTimetable;
    }

    public void setTreshold(Double treshold) {
        this.threshold = treshold;
    }

    public void setIterationProgressCounter(
            IterationProgressCounter iterationProgressCounter) {
        this.iterationProgressCounter = iterationProgressCounter;
    }

    public void setModelFrequency(ModelFrequency modelFrequency) {
        this.modelFrequency = modelFrequency;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Solver getSolver() {
        return solver;
    }

    public LinearModel getLinearModel() {
        return linearModel;
    }

    public Double getObjectiveFunction() {
        return objectiveFunction;
    }

    public ModelHeadway getModelHeadway() {
        return modelHeadway;
    }

    public Boolean getUseInitialTimetable() {
        return useInitialTimetable;
    }

    public Boolean getFixModuloFromInitialTimetable() {
        return fixModuloFromInitialTimetable;
    }

    public Double getThreshold() {
        return threshold;
    }

    public ModelChange getModelChange() {
        return modelChange;
    }

    public ModelFrequency getModelFrequency() {
        return modelFrequency;
    }

}
