package net.lintim.algorithm.vehiclescheduling;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverNotSupportedException;
import net.lintim.model.*;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.model.vehiclescheduling.TripConnection;
import net.lintim.model.vehiclescheduling.TripNode;
import net.lintim.util.GeneticAlgorithmParameters;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.SolverType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lintim.util.Parameters.SECONDS_PER_MINUTE;

public abstract class IPModelSolver {
    private static final Logger logger = new Logger(IPModelSolver.class.getCanonicalName());

    /**
     * Compute an optimal vehicle schedule for the given trip graph.
     *
     * @param tripGraph  the trip graph, i.e., a graph containing all trips as nodes and the connections as edges.
     * @param parameters the parameters to use
     * @return the found vehicle schedule.
     */
    public abstract VehicleSchedule solveVehicleSchedulingIPModel(Graph<TripNode, TripConnection> tripGraph,
                                                                  GeneticAlgorithmParameters parameters);

    /**
     * Compute an optimal vehicle schedule for the given trips.
     *
     * @param ptn        the underlying ptn
     * @param trips      the trips to cover
     * @param parameters the parameters to use
     * @return the found vehicle schedule
     */
    public VehicleSchedule solveVehicleSchedulingIPModel(Graph<Stop, Link> ptn, Collection<Trip> trips,
                                                         GeneticAlgorithmParameters parameters) {
        logger.debug("Computing trip graph");
        Graph<TripNode, TripConnection> tripGraph = computeTripGraph(ptn, trips, parameters);
        return solveVehicleSchedulingIPModel(tripGraph, parameters);
    }

    private static Graph<TripNode, TripConnection> computeTripGraph(Graph<Stop, Link> ptn, Collection<Trip> trips,
                                                                    GeneticAlgorithmParameters parameters) {
        // Check whether the depot index is present in the ptn
        if (parameters.useDepot() && ptn.getNode(parameters.getDepotIndex()) == null) {
            throw new LinTimException("The ptn node with id " + parameters.getDepotIndex() + " could not be found but should be the " +
                "depot!");
        }
        Function<Pair<Double, Double>, Double> connectionObjective = pair ->
            parameters.getCostFactorLength() * pair.getFirstElement() + parameters.getCostFactorTime() * pair.getSecondElement();
        // Compute the station distances, first entry in map is duration (in sec), second is length (in km)
        Map<Integer, Map<Integer, Pair<Double, Double>>> distanceTimeMap = computeStationDistances(ptn, parameters.getTimeUnitsPerMinute());
        // Now we can create the trip graph
        Graph<TripNode, TripConnection> tripGraph = new ArrayListGraph<>();
        TripNode depot = new TripNode(0, null, true);
        tripGraph.addNode(depot);
        int tripIndex = 1;
        int connectionIndex = 1;
        for (Trip trip : trips) {
            TripNode node = new TripNode(tripIndex, trip, false);
            tripIndex += 1;
            tripGraph.addNode(node);
            TripConnection fromDepot = new TripConnection(connectionIndex, depot, node, parameters.getVehicleCost() + (parameters.useDepot() ?
                connectionObjective.apply(distanceTimeMap.get(parameters.getDepotIndex()).get(trip.getStartStopId())) : 0));
            connectionIndex += 1;
            TripConnection toDepot = new TripConnection(connectionIndex, node, depot, parameters.useDepot() ?
                connectionObjective.apply(distanceTimeMap.get(trip.getEndStopId()).get(parameters.getDepotIndex())) : 0);
            connectionIndex += 1;
            tripGraph.addEdge(fromDepot);
            tripGraph.addEdge(toDepot);
        }
        // Now determine the compatibilities and add the respective edges
        for (TripNode origin : tripGraph.getNodes()) {
            if (origin.isDepot()) {
                continue;
            }
            Map<Integer, Pair<Double, Double>> originDistanceTimeMap = distanceTimeMap.get(origin.getTrip().getEndStopId());
            for (TripNode destination : tripGraph.getNodes()) {
                if (origin.equals(destination) || destination.isDepot()) {
                    continue;
                }
                double timeDistanceBetweenTrips = destination.getTrip().getStartTime() - origin.getTrip().getEndTime
                    ();
                double timeToDrive = originDistanceTimeMap.get(destination.getTrip().getStartStopId()).getSecondElement();
                if (timeDistanceBetweenTrips >= timeToDrive + parameters.getTurnoverTime()) {
                    double distanceBetweenTrips = originDistanceTimeMap.get(destination.getTrip().getStartStopId())
                        .getFirstElement();
                    tripGraph.addEdge(new TripConnection(connectionIndex, origin, destination, connectionObjective
                        .apply(new Pair<>(distanceBetweenTrips, timeDistanceBetweenTrips))));
                    connectionIndex += 1;
                }
            }
        }
        return tripGraph;
    }

    static VehicleSchedule computeSchedule(Collection<TripConnection> usedConnections) {
        VehicleSchedule schedule = new VehicleSchedule();
        int vehicleId = 1;
        int tripId = 1;
        // Look for all connections starting in the depot. These are the start of all vehicle tours
        List<TripConnection> outgoingDepotConnections = usedConnections.stream().filter(tripConnection ->
            tripConnection.getLeftNode().isDepot()).collect(Collectors.toList());
        while (!outgoingDepotConnections.isEmpty()) {
            Circulation circulation = new Circulation(vehicleId);
            VehicleTour tour = new VehicleTour(vehicleId);
            circulation.addVehicle(tour);
            vehicleId += 1;
            TripConnection currentEdge = outgoingDepotConnections.get(0);
            outgoingDepotConnections.remove(currentEdge);
            while (!currentEdge.getRightNode().isDepot()) {
                Trip sourceTrip = currentEdge.getLeftNode().getTrip();
                Trip targetTrip = currentEdge.getRightNode().getTrip();

                if (!currentEdge.getLeftNode().isDepot()) {
                    // Create the empty trip and add it
                    Trip emptyTrip = new Trip(sourceTrip.getEndAperiodicEventId(), sourceTrip
                        .getEndPeriodicEventId(), sourceTrip.getEndStopId(), sourceTrip.getEndTime(),
                        targetTrip.getStartAperiodicEventId(), targetTrip.getStartPeriodicEventId(), targetTrip
                        .getStartStopId(), targetTrip.getStartTime(), -1, TripType.EMPTY);
                    tour.addTrip(tripId, emptyTrip);
                    tripId += 1;
                }
                tour.addTrip(tripId, targetTrip);
                tripId += 1;
                TripNode lastTripNode = currentEdge.getRightNode();
                currentEdge = usedConnections.stream().filter(tripConnection -> tripConnection.getLeftNode().equals
                    (lastTripNode)).findAny().orElse(null);
                if (currentEdge == null) {
                    throw new LinTimException("Could not find next trip!");
                }
            }
            schedule.addCirculation(circulation);
        }
        return schedule;
    }

    public static Map<Integer, Map<Integer, Pair<Double, Double>>> computeStationDistances(Graph<Stop, Link>
                                                                                                ptn, int timeUnitsPerMinute) {
        // TODO: Should we use another approach, to model the objective function regarding empty kilometers and time
        // TODO: correctly? This will choose a longer path, that needs less time, even if it is worth in the objective
        // TODO: What we really need: The shortest path w.r.t the objective, that is not too long w.r.t the duration
        // TODO: For now, mimic the behavior of the canal-based vehicle schedules.
        // TODO: See #264 in gitlab.
        Function<Link, Double> lengthFunction = link -> (double) link.getLowerBound() * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        HashMap<Integer, Map<Integer, Pair<Double, Double>>> returnMap = new HashMap<>();
        for (Stop origin : ptn.getNodes()) {
            returnMap.put(origin.getId(), new HashMap<>());
            Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(ptn, origin, lengthFunction);
            dijkstra.computeShortestPaths();
            for (Stop destination : ptn.getNodes()) {
                // Compute the distance
                if (dijkstra.getDistance(destination) == 0) {
                    returnMap.get(origin.getId()).put(destination.getId(), new Pair<>(0., 0.));
                } else {
                    double distance = dijkstra.getPath(destination).getEdges().stream().mapToDouble(Link::getLength).sum();
                    double time = dijkstra.getDistance(destination);
                    returnMap.get(origin.getId()).put(destination.getId(), new Pair<>(distance, time));
                }
            }
        }
        return returnMap;
    }

}
