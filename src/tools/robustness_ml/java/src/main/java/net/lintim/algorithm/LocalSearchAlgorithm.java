package net.lintim.algorithm;

import it.unimi.dsi.fastutil.Hash;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.LinTimException;
import net.lintim.io.Debug;
import net.lintim.model.*;
import net.lintim.util.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LocalSearchAlgorithm {

    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
    private final Graph<Stop, Link> ptn;
    private final OD od;
    private final Graph<PeriodicEvent, PeriodicActivity> periodicEan;
    private final LinePool lines;
    private final VehicleSchedule vehicleSchedule;
    private final LocalSearchParameters parameters;
    private final List<AperiodicActivity> allChanges;
    private final List<AperiodicActivity> allDrives;
    private final List<AperiodicActivity> allWaits;
    private final List<AperiodicActivity> allTurns;
    private final LocalSearchData mlData;
    private final MLModel mlModel;
    private Passengers passengers;
    private final Map<Passenger, Integer> lastChangedPassengers;
    private final Map<AperiodicEvent, Integer> lastChangedEvents;
    private final Map<PeriodicEvent, Integer> lastChangedPeriodicEvents;

    private static final Logger logger = new Logger(LocalSearchAlgorithm.class.getCanonicalName());

    private LocalSearchAlgorithm(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Graph<Stop, Link> ptn, OD od,
                                 Graph<PeriodicEvent, PeriodicActivity> periodicEan, LinePool lines,
                                 VehicleSchedule vehicleSchedule, LocalSearchParameters parameters) {
        this.aperiodicEan = aperiodicEan;
        this.ptn = ptn;
        this.od = od;
        this.periodicEan = periodicEan;
        this.lines = lines;
        this.vehicleSchedule = vehicleSchedule;
        this.parameters = parameters;
        this.mlData = new LocalSearchData(aperiodicEan, ptn, od, periodicEan, lines, vehicleSchedule, parameters);
        this.mlModel = MLModel.supplyMLModel(parameters);
        this.allChanges = new ArrayList<>();
        this.allDrives = new ArrayList<>();
        this.allWaits = new ArrayList<>();
        this.allTurns = new ArrayList<>();
        lastChangedPassengers = new HashMap<>();
        lastChangedEvents = new HashMap<>();
        this.lastChangedPeriodicEvents = new HashMap<>();
    }

    private void initializeData() {
        passengers = mlData.initializeNetwork();
        if (parameters.outputMLData()) {
            logger.debug(mlData.toString());
        }
        mlModel.readModel(parameters.getModelFileName());
        classifyActivities();
    }

    private void classifyActivities() {
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            switch (activity.getType()) {
                case DRIVE:
                    allDrives.add(activity);
                    break;
                case WAIT:
                    allWaits.add(activity);
                    break;
                case CHANGE:
                    allChanges.add(activity);
                    break;
                case TURNAROUND:
                    allTurns.add(activity);
                    break;
            }
        }
    }

    private double evaluateCurrentNetwork() {
        double[] input = mlData.generateModelInput();
        return mlModel.predict(input);
    }

    private void localSearch() {
        List<IterationValue> values = new ArrayList<>();
        double currentMLValue = evaluateCurrentNetwork();
        double currentTravelTime = mlData.computeAverageTravelTime();
        double originalTravelTime = currentTravelTime;
        double originalMLValue = currentMLValue;
        double maxTravelTime = originalTravelTime * parameters.getAllowedTravelTimeIncrease();
        BufferedWriter reevaluateWriter = null;
        int iteration = 1;
        String solutionFolderName = "";
        if (parameters.outputEverySolution()) {
            solutionFolderName = Debug.initializeSolutionFolder(parameters);
        }
        while (true) {
            if (parameters.getRerouteInterval() > 0 && iteration % parameters.getRerouteInterval() == 0) {
                logger.debug("Reroute passengers in iteration " + iteration);
                reroutePassengers();
                currentMLValue = evaluateCurrentNetwork();
                currentTravelTime = mlData.computeAverageTravelTime();
                if (parameters.outputMLData()) {
                    logger.debug(mlData.toString());
                }
            }
            values.add(new IterationValue(iteration, currentMLValue, currentTravelTime));
            if (parameters.outputEverySolution()) {
                Debug.appendToValuesFile(solutionFolderName, new IterationValue(iteration, currentMLValue, currentTravelTime));
                double[] input = mlData.generateModelInput();
                double[] result = mlModel.getResult(input);
                Debug.writePassengerData(passengers, "ls");
                Debug.writeMLData(mlData, input, parameters);
                Debug.outputSolution(aperiodicEan, parameters, solutionFolderName, iteration, input, result);
            }
            if (parameters.isReevaluateEverySolution()) {
                logger.debug("Reevaluate");
                // Create a new ml data object of the current data
                LocalSearchData newData = new LocalSearchData(aperiodicEan, ptn, od, periodicEan, lines, vehicleSchedule, parameters);
                Passengers newPassengers = newData.initializeNetwork();
                try {
                    if (reevaluateWriter == null) {
                        reevaluateWriter = new BufferedWriter(new FileWriter(new File("reevaluate.csv")));
                        reevaluateWriter.write("# iteration; currentMLValue; currentTTValue; recomputedMLValue; recomputedTTValue\n");
                    }
                    reevaluateWriter.write(iteration + "; " + currentMLValue + "; " + currentTravelTime + "; " +
                        mlModel.predict(newData.generateModelInput()) + "; " + newData.computeAverageTravelTime() + "\n");
                    reevaluateWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                logger.debug("Done");
            }
            logger.info("Iteration " + iteration + ", current Value " + currentMLValue + "( of " + originalMLValue +
                "), average travel time " + currentTravelTime + " (allowed " + maxTravelTime + ", original " +
                originalTravelTime + ")");
            if (iteration >= parameters.getMaxIteration()) {
                logger.debug("Iteration limit reached");
                break;
            }
            iteration += 1;
            List<AperiodicActivity> candidates = determineLocalNeighboorhood();
            AperiodicActivity bestCandidate = null;
            IterationResult bestResult = new IterationResult();
            for (AperiodicActivity candidate: candidates) {
                //logger.debug("Evaluating activity " + candidate.getId());
                IterationResult result = evaluateBufferIncrease(candidate);
                if (parameters.useSelectByRatio()) {
                    // Compare everything to currentValue
                    double currentBestObjective = (currentMLValue - bestResult.getMlValue())/currentMLValue + (currentTravelTime - bestResult.getTtValue())/currentTravelTime;
                    double newObjective = (currentMLValue - result.getMlValue()) / currentMLValue + (currentTravelTime - result.getTtValue()) / currentTravelTime;
                    if (newObjective > currentBestObjective && result.getTtValue() <= maxTravelTime) {
                        bestResult = result;
                        bestCandidate = candidate;
                    }
                }
                else {
                    if (result.getMlValue() < bestResult.getMlValue() && result.getTtValue() <= maxTravelTime) {
                        bestResult = result;
                        bestCandidate = candidate;
                    }
                }
            }
            if (bestResult.getMlValue() < currentMLValue) {
                logger.debug("Found best value " + bestResult + " for activity " + bestCandidate + " with ml value " + bestResult.getMlValue() + " and travel time " + bestResult.getTtValue());
                try {
                    implementChange(bestCandidate);
                }
                catch (AlgorithmStoppingCriterionException e) {
                    // This should not happen
                    throw new RuntimeException("Found infeasible timetable after deeming it feasible. This should never happen. Abort!");
                }
                currentMLValue = bestResult.getMlValue();
                currentTravelTime = bestResult.getTtValue();

            }
            else if (bestResult.getMlValue() == Double.POSITIVE_INFINITY) {
                logger.info("Reached local optimum after " + iteration + " iterations! No feasible move found!");
                break;
            }
            else {
                logger.info("Reached local optimum after " + iteration + " iterations! No improving move found!");
                break;
            }
        }
        logger.debug("Final solution: ML Value " + currentMLValue + "( of " + originalMLValue +
            "), average travel time " + currentTravelTime + " (allowed " + maxTravelTime + ", original " +
            originalTravelTime + ")");
        if (reevaluateWriter != null) {
            try {
                reevaluateWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private IterationResult evaluateBufferIncrease(AperiodicActivity candidate) {
        try {
            implementChange(candidate);
        } catch (AlgorithmStoppingCriterionException e) {
            // Implementing this change lead to an infeasible solution, we don't need to validate, only roll back
            logger.debug("Found invalid timetable, roll back");
            revertLastChange();
            return new IterationResult(Double.POSITIVE_INFINITY, -1);
        }
        mlData.computeVariableNetworkData();
        double mlValue = evaluateCurrentNetwork();
        double ttValue = mlData.computeAverageTravelTime();
        revertLastChange();
        return new IterationResult(mlValue, ttValue);
    }

    private void implementChange(AperiodicActivity activity) {
        lastChangedEvents.clear();
        lastChangedPassengers.clear();
        lastChangedPeriodicEvents.clear();
        if (parameters.usePeriodicTimetabling()) {
            // First, store the current slack for each activity
            HashMap<PeriodicActivity, Double> originalSlack = new HashMap<>();
            double slack;
            for (PeriodicActivity act: periodicEan.getEdges()) {
                slack = act.getRightNode().getTime() - act.getLeftNode().getTime() - act.getLowerBound();
                while (slack < 0) {
                    slack += parameters.getPeriodLength();
                }
                if (slack > act.getUpperBound() - act.getLowerBound()) {
                    logger.debug("Invalid original slack for " + act + ": " + slack);
                    //throw new RuntimeException("Invalid activity " + act + ", the current duration [" + act.getLeftNode().getTime() + "," + act.getRightNode().getTime() + "] implies a slack of " + slack + " which is infeasible");
                }
                originalSlack.put(act, slack);
            }
            HashMap<AperiodicActivity, Integer> originalAperiodicSlack = new HashMap<>();
            int aperiodicSlack;
            for (AperiodicActivity act: aperiodicEan.getEdges()) {
                aperiodicSlack = act.getRightNode().getTime() - act.getLeftNode().getTime() - act.getLowerBound();
                originalAperiodicSlack.put(act, aperiodicSlack);
            }
            Queue<PeriodicEvent> eventsToProcess = new LinkedList<>();
            eventsToProcess.add(periodicEan.getNode(activity.getRightNode().getPeriodicEventId()));
            Map<PeriodicEvent, Integer> bufferToAddMap = new HashMap<>();
            Map<Integer, Collection<AperiodicEvent>> aperiodicEventMap = new HashMap<>();
            for (AperiodicEvent event: aperiodicEan.getNodes()) {
                aperiodicEventMap.computeIfAbsent(event.getPeriodicEventId(), ArrayList::new).add(event);
            }
            // TODO: Maybe store all `real` aperiodic activities here beforehand?
            bufferToAddMap.put(periodicEan.getNode(activity.getRightNode().getPeriodicEventId()), (parameters.getBufferIncreasePerStep() * parameters.getTimeUnitsPerMinute()) / Parameters.SECONDS_PER_MINUTE);
            PeriodicEvent currentEvent, nextEvent;
            AperiodicEvent nextAperiodicEvent;
            int bufferToAdd, bufferToAddInSeconds;
            double remainingSlack, minimalSlack;
            while (eventsToProcess.size() > 0) {
                currentEvent = eventsToProcess.poll();
                bufferToAdd = bufferToAddMap.get(currentEvent);
                bufferToAddInSeconds = (bufferToAdd * Parameters.SECONDS_PER_MINUTE) / parameters.getTimeUnitsPerMinute();
                logger.debug("Add buffer of " + bufferToAdd + " to periodic event " + currentEvent);
                currentEvent.setTime((int) (Math.ceil((currentEvent.getTime() + bufferToAdd)) % parameters.getPeriodLength()));
                logger.debug("New time: " + currentEvent.getTime());
                if (!lastChangedPeriodicEvents.containsKey(currentEvent)) {
                    lastChangedPeriodicEvents.put(currentEvent, bufferToAdd);
                }
                else {
                    lastChangedPeriodicEvents.put(currentEvent, lastChangedPeriodicEvents.get(currentEvent) + bufferToAdd);
                }
                // Find all corresponding aperiodic events and add them to changed events
                for (AperiodicEvent correspondingEvent: aperiodicEventMap.get(currentEvent.getId())) {
                    logger.debug("Add buffer of " + bufferToAddInSeconds + " to aperiodic event " + correspondingEvent);
                    correspondingEvent.setTime(correspondingEvent.getTime() + bufferToAddInSeconds);
                    logger.debug("New time: " + correspondingEvent.getTime());
                    if (!lastChangedEvents.containsKey(correspondingEvent)) {
                        lastChangedEvents.put(correspondingEvent, bufferToAddInSeconds);
                    }
                    else {
                        lastChangedEvents.put(correspondingEvent, lastChangedEvents.get(correspondingEvent) + bufferToAddInSeconds);
                        // Failure check: Have we added more than a period?
                        // We recognize a failure, if we reach an accumulated buffer of the initial buffer increase squared
                        // For each cycle, we should at least decrease the buffer by 1, therefore reaching the event at must `initial buffer` times,
                        // every time with a smaller buffer, i.e., the sum of 1 to `initial buffer`
                        if (lastChangedEvents.get(correspondingEvent) > (parameters.getBufferIncreasePerStep() * (parameters.getBufferIncreasePerStep() + 1))/2) {
                            throw new AlgorithmStoppingCriterionException("Detected infinite loop in slack propagation: Robust periodic timetable is not possible");
                        }
                    }
                }
                for (PeriodicActivity outgoingActivity: periodicEan.getOutgoingEdges(currentEvent)) {
                    remainingSlack = outgoingActivity.getRightNode().getTime() - currentEvent.getTime() - outgoingActivity.getLowerBound();
                    while (remainingSlack < 0) {
                        remainingSlack += parameters.getPeriodLength();
                    }
                    if (remainingSlack > outgoingActivity.getUpperBound()) {
                        remainingSlack -= parameters.getPeriodLength();
                    }
                    if (parameters.propagateSlackUsePercentage()) {
                        minimalSlack = (int) Math.ceil(originalSlack.get(outgoingActivity) * (1 - parameters.getPropagateSlackPercentage()));
                    }
                    else {
                        minimalSlack = Math.min(originalSlack.get(outgoingActivity), parameters.getPropagateSlackMinTime()/ (double)Parameters.SECONDS_PER_MINUTE * parameters.getTimeUnitsPerMinute());
                    }
                    if (remainingSlack >= minimalSlack) {
                        // We have enough remaining slack, continue
                        continue;
                    }
                    // We don't have enough remaining slack, propagate some slack
                    bufferToAdd = (int) Math.ceil(minimalSlack - remainingSlack);
                    nextEvent = outgoingActivity.getRightNode();
                    if (!eventsToProcess.contains(nextEvent)) {
                        eventsToProcess.add(nextEvent);
                        bufferToAddMap.put(nextEvent, bufferToAdd);
                    }
                    else {
                        bufferToAddMap.put(nextEvent, Math.max(bufferToAddMap.get(nextEvent), bufferToAdd));
                    }
                }
                // Now check all outgoing aperiodic activities. There may be activities without a corresponding periodic
                // activity or aperiodic activities that need to be shifted by a whole period (which we would not see in
                // the periodic case).
                for (AperiodicEvent event: aperiodicEventMap.get(currentEvent.getId())) {
                    for (AperiodicActivity outgoingActivity: aperiodicEan.getOutgoingEdges(event)) {
                        remainingSlack = outgoingActivity.getRightNode().getTime() - event.getTime() - outgoingActivity.getLowerBound();
                        if (parameters.propagateSlackUsePercentage()) {
                            minimalSlack = (int) Math.ceil(originalAperiodicSlack.get(outgoingActivity) * (1 - parameters.getPropagateSlackPercentage()));
                        }
                        else {
                            minimalSlack = Math.min(originalAperiodicSlack.get(outgoingActivity), parameters.getPropagateSlackMinTime());
                        }
                        if (remainingSlack >= minimalSlack) {
                            // We have enough remaining slack, continue
                            continue;
                        }
                        // We don't have enough remaining slack, propagate some slack
                        // cast to int does nothing since aperiodic slack is already int
                        bufferToAddInSeconds = (int) (minimalSlack - remainingSlack);
                        bufferToAdd = bufferToAddInSeconds * parameters.getTimeUnitsPerMinute() / Parameters.SECONDS_PER_MINUTE;
                        nextAperiodicEvent = outgoingActivity.getRightNode();
                        // Now transfer the buffer back to the corresponding periodic event
                        nextEvent = periodicEan.getNode(nextAperiodicEvent.getPeriodicEventId());
                        if (!eventsToProcess.contains(nextEvent)) {
                            eventsToProcess.add(nextEvent);
                            bufferToAddMap.put(nextEvent, bufferToAdd);
                        }
                        else {
                            bufferToAddMap.put(nextEvent, Math.max(bufferToAddMap.get(nextEvent), bufferToAdd));
                        }
                    }
                }
            }
        }
        else {
            // First, store the current slack for each activity
            HashMap<AperiodicActivity, Integer> originalSlack = new HashMap<>();
            for (AperiodicActivity act : aperiodicEan.getEdges()) {
                originalSlack.put(act, act.getRightNode().getTime() - act.getLeftNode().getTime() - act.getLowerBound());
            }
            Queue<AperiodicEvent> eventsToProcess = new LinkedList<>();
            eventsToProcess.add(activity.getRightNode());
            Map<AperiodicEvent, Integer> bufferToAddMap = new HashMap<>();
            bufferToAddMap.put(activity.getRightNode(), parameters.getBufferIncreasePerStep());
            AperiodicEvent currentEvent, nextEvent;
            int bufferToAdd, remainingSlack, minimalSlack;
            while (eventsToProcess.size() > 0) {
                currentEvent = eventsToProcess.poll();
                // First, process the current event
                bufferToAdd = bufferToAddMap.get(currentEvent);
                bufferToAddMap.remove(currentEvent);
                currentEvent.setTime(currentEvent.getTime() + bufferToAdd);
                if (!lastChangedEvents.containsKey(currentEvent)) {
                    lastChangedEvents.put(currentEvent, bufferToAdd);
                } else {
                    lastChangedEvents.put(currentEvent, lastChangedEvents.get(currentEvent) + bufferToAdd);
                }
                // Now, find all adjacent events
                for (AperiodicActivity outgoingActivity : aperiodicEan.getOutgoingEdges(currentEvent)) {
                    remainingSlack = outgoingActivity.getRightNode().getTime() - currentEvent.getTime() - outgoingActivity.getLowerBound();
                    if (parameters.propagateSlackUsePercentage()) {
                        minimalSlack = (int) Math.ceil(originalSlack.get(outgoingActivity) * (1 - parameters.getPropagateSlackPercentage()));
                    }
                    else {
                        minimalSlack = Math.min(originalSlack.get(outgoingActivity), parameters.getPropagateSlackMinTime());
                    }
                    if (remainingSlack >= minimalSlack) {
                        // We have enough remaining slack, continue
                        continue;
                    }
                    // We don't have enough remaining slack, propagate some slack
                    bufferToAdd = minimalSlack - remainingSlack;
//                bufferToAdd = -1 * slack;
                    nextEvent = outgoingActivity.getRightNode();
                    if (!eventsToProcess.contains(nextEvent)) {
                        eventsToProcess.add(nextEvent);
                        bufferToAddMap.put(nextEvent, bufferToAdd);
                    } else {
                        bufferToAddMap.put(nextEvent, Math.max(bufferToAddMap.get(nextEvent), bufferToAdd));
                    }
                }
            }
        }
        // We processed all events. Find all affected passengers and update their travel time
        AperiodicEvent currentEvent;
        for (Map.Entry<AperiodicEvent, Integer> changedEvent: lastChangedEvents.entrySet()) {
            currentEvent = changedEvent.getKey();
            if (currentEvent.getType() == EventType.ARRIVAL) {
                if (mlData.getPassengersByEventId().containsKey(currentEvent.getId())) {
                    for (int passengerIndex: mlData.getPassengersByEventId().get(currentEvent.getId())) {
                        if (passengers.getPassenger(passengerIndex).getArrivalEventId() == currentEvent.getId()) {
                            lastChangedPassengers.put(passengers.getPassenger(passengerIndex), changedEvent.getValue());
                            passengers.getPassenger(passengerIndex).setTravelTime(
                                passengers.getPassenger(passengerIndex).getTravelTime() + changedEvent.getValue());
                        }
                    }
                }
            }
        }
    }

    private void revertLastChange() {
        for (Map.Entry<Passenger, Integer> passengerChange: lastChangedPassengers.entrySet()) {
            passengerChange.getKey().setTravelTime(passengerChange.getKey().getTravelTime() - passengerChange.getValue());
        }
        for (Map.Entry<AperiodicEvent, Integer> eventChange: lastChangedEvents.entrySet()) {
            eventChange.getKey().setTime(eventChange.getKey().getTime() - eventChange.getValue());
        }
        int newTime;
        for (Map.Entry<PeriodicEvent, Integer> eventChange: lastChangedPeriodicEvents.entrySet()) {
            newTime = eventChange.getKey().getTime() - eventChange.getValue();
            // Need to shift it back into the periodic time window
            while (newTime < 0) {
                newTime += parameters.getPeriodLength();
            }
            eventChange.getKey().setTime(newTime);
        }
    }

    private List<AperiodicActivity> determineLocalNeighboorhood() {
        // Greedy search on where to perform a change. Look for the change/drive/wait/circulation activities with the
        // least weighted buffer
        List<AperiodicActivity> candidates = allChanges.stream().sorted(Comparator.comparingDouble(this::weightActivity).reversed()).limit(parameters.getNumberOfCandidates()).collect(Collectors.toList());
        candidates.addAll(allDrives.stream().sorted(Comparator.comparingDouble(this::weightActivity).reversed()).limit(parameters.getNumberOfCandidates()).collect(Collectors.toList()));
        candidates.addAll(allWaits.stream().sorted(Comparator.comparingDouble(this::weightActivity).reversed()).limit(parameters.getNumberOfCandidates()).collect(Collectors.toList()));
        candidates.addAll(allTurns.stream().sorted(Comparator.comparingDouble(this::weightActivity).reversed()).limit(parameters.getNumberOfCandidates()).collect(Collectors.toList()));
        return candidates;
    }

    private double weightActivity(AperiodicActivity activity) {
        double slack = activity.getRightNode().getTime() - activity.getLeftNode().getTime() - activity.getLowerBound();
        if (slack == 0) {
            slack = 0.5;
        }
        double passengers = activity.getNumberOfPassengers();
        switch (activity.getType()) {
            case DRIVE:
                return parameters.getDriveWeight() * passengers / slack;
            case WAIT:
                return parameters.getWaitWeight() * passengers / slack;
            case CHANGE:
                return parameters.getChangeWeight() * passengers / slack;
            case TURNAROUND:
                return parameters.getTurnWeight() / slack;
            default:
                throw new LinTimException("Invalid activity class " + activity.getType() + " to weight in local search");
        }
    }

    private void reroutePassengers() {
        passengers = mlData.reroutePassengers();
        mlData.recomputeAllNetworkData();
    }

    public static void robustify(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Graph<Stop, Link> ptn, OD od,
                                 Graph<PeriodicEvent, PeriodicActivity> periodicEan, LinePool lines,
                                 VehicleSchedule vehicleSchedule, LocalSearchParameters parameters) {
        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(aperiodicEan, ptn, od, periodicEan, lines, vehicleSchedule,
            parameters);
        algo.initializeData();
        algo.localSearch();
    }
}
