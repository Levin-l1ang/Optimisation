package net.lintim.evaluation;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.LinTimException;
import net.lintim.main.evaluation.util.evaluation.Parameters;
import net.lintim.model.*;
import net.lintim.util.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Class for evaluating a vehicle schedule
 */
public class VehicleScheduleEvaluator {
	private static final Logger logger = new Logger(VehicleScheduleEvaluator.class.getCanonicalName());
	private static final int SECONDS_PER_MINUTE = 60;

	public static Statistic evaluateVehicleSchedule(
		VehicleSchedule vehicleSchedule,
		Collection<Trip> trips,
		Graph<Stop, Link> ptn,
		LinePool lineConcept,
		Parameters parameters,
		List<Depot> depots
		){
		logger.info("Begin evaluation");
		int numberOfUsedVehicles = 0;
		double emptyTripDistanceWithoutDepot = 0;
		double emptyTripDistanceDepot = 0;
		double fullTripDistance = 0;
		double emptyTripDurationWithoutDepot = 0;
		double emptyTripDurationDepot = 0;
		double fullTripDuration = 0;
		boolean feasibility = true;
		int numberOfEmptyTripsWithoutDepot = 0;
		int numberOfEmptyTripsDepot = 0;
		double minWaitingTimeInStation = Double.MAX_VALUE;
		double maxWaitingTimeInStation = -1;
		double sumWaitingTimeInStation = 0;
		int numberOfWaitingTimesInStation = 0;
		//First, calculate the distances between the stations and for the lines
		// Store by stop ids, first the duration (in seconds), second the length (in kilometers)
		Map<Pair<Integer, Integer>, Pair<Double, Double>> shortestPathLengths = computeStationDistances(ptn, parameters);
		Map<Integer, Double> lineLength = computeLineLengths(lineConcept);
		for(Circulation circulation : vehicleSchedule.getCirculations()){
			for(VehicleTour vehicleTour : circulation.getVehicleTourList()){
				numberOfUsedVehicles += 1;
				//Store the first and last trip for special consideration later on
				List<Trip> tripList = vehicleTour.getTripList();
				Trip firstTrip = tripList.get(0);
				Trip lastTrip = tripList.get(tripList.size()-1);
				int currentTime = Integer.MIN_VALUE;
				int currentStopId = -1;
				for(Trip trip : tripList){
					if (currentTime > trip.getStartTime()) {
						logger.warn("Moved backwards in time, trip " + trip + " starts before the last " + "trip ended!");
						feasibility = false;
					}
					if (currentStopId != -1 && trip.getStartStopId() != currentStopId) {
						logger.warn("The last trip ended at " + currentStopId + " but the current trip " + trip + " starts at a different stop!");
						feasibility = false;
					}
					if (trip.getTripType() == TripType.TRIP && !trips.remove(trip)) {
						logger.warn("Could not find trip " + trip + " from the vehicle schedule in the" + " list of read trips. Please check your Trip file!");
						feasibility = false;
					}
					boolean isEmptyTrip = (trip.getTripType() == TripType.EMPTY);
					double tripDuration;
					double tripLength;
					double minTripDuration;
					//First handle the case where we have a depot and the first and last trips are the trips from/to the depot
					if(parameters.useDepot()){
						if(trip.equals(firstTrip)){
						// hier noch checken ob die trips lang genug sind vom depot zu den stops
							Depot startDepot = null;
							int firstStopIdInVehicleTour = trip.getEndStopId();
							// get the correct Depot
							for(Depot depot : depots) {
								if(depot.getDepotId() == -trip.getStartStopId()) {
									startDepot = depot;
								}
							}
							numberOfEmptyTripsDepot += 1;
							emptyTripDistanceDepot += startDepot.getDistanceToStop(
								ptn.getNode(firstStopIdInVehicleTour),
								ptn
							);
							emptyTripDurationDepot += startDepot.getTimeDistanceToStop(
								ptn.getNode(firstStopIdInVehicleTour),
    								parameters.getVehicleSpeed(),
    								shortestPathLengths,
    								ptn
    							);

						}
						if(trip.equals(lastTrip)){
							Depot endDepot = null;
							int lastStopIdInVehicleTour = trip.getStartStopId();
							// get the correct Depot
							for(Depot depot : depots) {
								if(depot.getDepotId() == -trip.getEndStopId()) {
									endDepot = depot;
								}
							}
							numberOfEmptyTripsDepot += 1;
							emptyTripDistanceDepot += endDepot.getDistanceToStop(
								ptn.getNode(lastStopIdInVehicleTour),
								ptn
							);
							emptyTripDurationDepot += endDepot.getTimeDistanceToStop(
								ptn.getNode(lastStopIdInVehicleTour),
    								parameters.getVehicleSpeed(),
    								shortestPathLengths,
    								ptn
    							);
						}
					}
					//Now process an ordinary trip
					if ((parameters.useDepot() && !trip.equals(lastTrip) && !trip.equals(firstTrip)) || !parameters.useDepot()){
						if(isEmptyTrip){
							tripDuration = trip.getEndTime() - trip.getStartTime();
							tripLength = shortestPathLengths.get(new Pair<>(trip.getStartStopId(), trip.getEndStopId())).getSecondElement();
							minTripDuration = shortestPathLengths.get(new Pair<>(trip.getStartStopId(), trip.getEndStopId())).getFirstElement() + parameters.getTurnoverTime();

							//Check if the empty trip is possible, i.e., if the trip duration is enough to cover the distance of the trip
							if(tripDuration < minTripDuration){
								logger.warn("Found a trip with insufficient time, " + trip + " has a " + "duration of " + tripDuration + " seconds, but has a minimal duration of " + minTripDuration + "seconds.");
								logger.warn("Goes from " + trip.getStartStopId() + " to " + trip.getEndStopId());
								logger.warn("Distance is " + shortestPathLengths.get(new Pair<>(trip.getStartStopId(), trip.getEndStopId())).getFirstElement());
								logger.warn("Turnaround time is " + parameters.getTurnoverTime());
								feasibility = false;
							}
							if(trip.getEndStopId()!=trip.getStartStopId()){
								numberOfEmptyTripsWithoutDepot += 1;
								emptyTripDurationWithoutDepot += tripDuration;
								emptyTripDistanceWithoutDepot += tripLength;
							}
							else {
								numberOfWaitingTimesInStation += 1;
								sumWaitingTimeInStation += tripDuration;
								minWaitingTimeInStation = Math.min(minWaitingTimeInStation, tripDuration);
								maxWaitingTimeInStation = Math.max(maxWaitingTimeInStation, tripDuration);
							}
						}
						else {
							tripDuration = trip.getEndTime() - trip.getStartTime();
							minTripDuration = shortestPathLengths.get(new Pair<>(trip.getStartStopId(), trip.getEndStopId())).getFirstElement();
							try {
								tripLength = lineLength.get(trip.getLineId());
							} catch (NullPointerException e){
								throw new LinTimException("Used line " + trip.getLineId() + " in the vehicle schedule, " + "but this line has frequency 0!");
							}
							fullTripDuration += tripDuration;
							fullTripDistance += tripLength;
						}
					}
					currentTime = trip.getEndTime();
					currentStopId = trip.getEndStopId();
				}
			}
		}

		double emptyDuration = emptyTripDurationDepot + emptyTripDurationWithoutDepot + sumWaitingTimeInStation;
		double emptyLength = emptyTripDistanceDepot + emptyTripDistanceWithoutDepot;
		double emptyCosts = parameters.getCostPerVehicle() * numberOfUsedVehicles + parameters.getCostFactorEmptyDuration() * emptyDuration + parameters.getCostFactorEmptyLength() * emptyLength;
		double costs = emptyCosts + parameters.getCostFactorFullDuration() * fullTripDuration + parameters.getCostFactorFullLength() * fullTripDistance;

		// Check case that there were no waiting trips at all
		if (maxWaitingTimeInStation == -1) {
			minWaitingTimeInStation = 0;
			maxWaitingTimeInStation = 0;
			sumWaitingTimeInStation = 0;
			// to avoid dividing by zero
			numberOfWaitingTimesInStation = 1;
		}
		// Are there still uncovered trips left?
		if (trips.size() > 0) {
			logger.warn("There were uncovered trips:");
			for (Trip trip : trips) {
				logger.warn(trip.toString());
			}
			feasibility = false;
		}
		//Write the found values to the statistic
		Statistic statistic = new Statistic();
			statistic.put("vs_cost", costs);
			statistic.put("vs_empty_cost", emptyCosts);
			statistic.put("vs_circulations", vehicleSchedule.getCirculations().size());
			statistic.put("vs_vehicles", numberOfUsedVehicles);
			statistic.put("vs_empty_distance_without_depot", emptyTripDistanceWithoutDepot);
			statistic.put("vs_empty_distance_with_depot", emptyLength);
			statistic.put("vs_empty_duration_standing", sumWaitingTimeInStation);
			statistic.put("vs_empty_duration_driving_without_depot", emptyTripDurationWithoutDepot);
			statistic.put("vs_empty_duration_driving_with_depot", emptyTripDurationDepot + emptyTripDurationWithoutDepot);
			statistic.put("vs_empty_trips_without_depot", numberOfEmptyTripsWithoutDepot);
			statistic.put("vs_empty_trips_with_depot", numberOfEmptyTripsWithoutDepot + numberOfEmptyTripsDepot);
			statistic.put("vs_minimal_waiting_time", minWaitingTimeInStation);
			statistic.put("vs_maximal_waiting_time", maxWaitingTimeInStation);
			statistic.put("vs_average_waiting_time", sumWaitingTimeInStation / numberOfWaitingTimesInStation);
			statistic.put("vs_full_distance", fullTripDistance);
			statistic.put("vs_full_duration", fullTripDuration);
			statistic.put("vs_feasible", feasibility);
			return statistic;
	}

    private static HashMap<Integer, Double> computeLineLengths(LinePool lineConcept) {
        HashMap<Integer, Double> lineLength = new HashMap<>();
        for(Line line : lineConcept.getLines()){
            if(line.getFrequency() > 0){
                double sumOfLength = 0;
                for(Link link : line.getLinePath().getEdges()){
                    sumOfLength += link.getLength();
                }
                lineLength.put(line.getId(), sumOfLength);
            }
        }
        return lineLength;
    }

    private static HashMap<Pair<Integer, Integer>, Pair<Double, Double>> computeStationDistances(Graph<Stop, Link> ptn, Parameters parameters) {
        HashMap<Pair<Integer, Integer>, Pair<Double, Double>> shortestPathLengths = new HashMap<>();
	//Depending on a different speed level we choose a different length function which is then used in Dijkstra
	Function<Link, Double> lengthFunction;
	String speedLevel = parameters.getSpeedLevel();
        if ((speedLevel.charAt(0) == 'f' || speedLevel.charAt(0) == 'F') && (speedLevel.charAt(1) == 'a' || speedLevel.charAt(1) == 'A') && (speedLevel.charAt(2) == 's' || speedLevel.charAt(2) == 'S') && (speedLevel.charAt(3) == 't' || speedLevel.charAt(3) == 'T'))  {
            lengthFunction = link -> (double) link.getLowerBound() * SECONDS_PER_MINUTE / parameters.getTimeUnitsPerMinute();
        } else if ((speedLevel.charAt(0) == 's' || speedLevel.charAt(0) == 'S') && (speedLevel.charAt(1) == 'l' || speedLevel.charAt(1) == 'L') && (speedLevel.charAt(2) == 'o' || speedLevel.charAt(2) == 'O') && (speedLevel.charAt(3) == 'w' || speedLevel.charAt(3) == 'W')) {
            lengthFunction = link -> (double) link.getUpperBound() * SECONDS_PER_MINUTE / parameters.getTimeUnitsPerMinute();
        } else {
            lengthFunction = link -> (double) (link.getLowerBound() + link.getUpperBound())/ 2 * SECONDS_PER_MINUTE / parameters.getTimeUnitsPerMinute();
        }
        for(Stop startStop : ptn.getNodes()){
            Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(ptn, startStop, lengthFunction);
            dijkstra.computeShortestPaths();
            for(Stop endStop : ptn.getNodes()){
                if (startStop.equals(endStop)){
                    shortestPathLengths.put(new Pair<>(startStop.getId(), endStop.getId()), new Pair<>(0.0,0.0));
                    continue;
                }
                Path path = dijkstra.getPath(endStop);
                if (path == null) {
                     logger.debug("No path found from stop " + startStop.getId()
                            + " to stop " + endStop.getId()
                            + ". Setting distance and time to Infinity.");
                    shortestPathLengths.put(new Pair<>(startStop.getId(), endStop.getId()), new Pair<>(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
                    continue;
                }
                double pathLength = startStop.equals(endStop) ? 0 : dijkstra.getPath(endStop).getEdges().stream()
                        .mapToDouble(Link::getLength).sum();
                shortestPathLengths.put(new Pair<>(startStop.getId(), endStop.getId()), new Pair<>(dijkstra
                        .getDistance(endStop), pathLength));
            }
        }
        return shortestPathLengths;
    }
}
