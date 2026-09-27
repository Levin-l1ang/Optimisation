package net.lintim.algorithm.vehiclescheduling;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverNotSupportedException;
import net.lintim.model.*;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.model.vehiclescheduling.TripConnection;
import net.lintim.model.vehiclescheduling.TripNode;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.SolverType;
import net.lintim.util.vehiclescheduling.Parameters;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;

import static net.lintim.util.vehiclescheduling.Constants.SECONDS_PER_MINUTE;

/**
 * @author Alexander Schiewe
 * Date: 12.09.17
 */
public abstract class IPModelSolver {
    private static final Logger logger = new Logger(IPModelSolver.class.getCanonicalName());

    /**
     * Compute an optimal vehicle schedule for the given trip graph.
     *
     * @param tripGraph  the trip graph, i.e., a graph containing all trips as nodes and the connections as edges.
     * @param parameters the parameters to use
     * @param depot the depot to use
     * @param ptn the ptn to use
     * @return the found vehicle schedule.
     */
    public abstract VehicleSchedule solveVehicleSchedulingIPModel(
        Graph<TripNode,TripConnection> tripGraph,
        Parameters parameters,
        Optional<Depot> depot,
        Graph<Stop, Link> ptn
        );

    /**
     * Compute an optimal vehicle schedule for the given trips.
     *
     * @param ptn        the underlying ptn
     * @param trips      the trips to cover
     * @param parameters the parameters to use
     * @param depots the depots to use
     * @return the found vehicle schedule
     */
    public VehicleSchedule solveVehicleSchedulingIPModel(
        Graph<Stop, Link> ptn,
        Collection<Trip> trips,
        Parameters parameters,
        List<Depot> depots
        ) {
            logger.debug("Computing trip graph");
            Graph<TripNode, TripConnection> tripGraph = computeTripGraph(
                ptn,
                trips,
                parameters,
                depots
                );
            Optional<Depot> depotOpt = (depots.size() !=0 ? Optional.of(depots.get(0)) : Optional.empty());
            return solveVehicleSchedulingIPModel(tripGraph, parameters, depotOpt, ptn);
            }

    /**
     * Get the ip model solver for the given solver type.
     *
     * @param solverType the solver type to use
     * @return the ip model class
     */
    public static IPModelSolver getVehicleSchedulingIpSolver(SolverType solverType) {
        try {
            String solverClassName;
            switch (solverType) {
                case GUROBI:
                    logger.debug("Will use Gurobi for optimization");
                    solverClassName = "net.lintim.algorithm.vehiclescheduling.IPModelGurobi";
                    break;
                default:
                    logger.debug("Will use Core for optimization");
                    solverClassName = "net.lintim.algorithm.vehiclescheduling.IPModelCore";
            }
            Class<?> solverClass = Class.forName(solverClassName);
            return (IPModelSolver) solverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Cannot initialize solver: " + e.getMessage());
            throw new LinTimException(e.getMessage());
        }
    }

    private static Graph<TripNode, TripConnection> computeTripGraph(
        Graph<Stop, Link> ptn,
        Collection<Trip> trips,
        Parameters parameters,
        List<Depot> depots
        ) {
            // Check wheter there are too many depots in the depot file
            if (depots.size()>1) {
                throw new LinTimException("There are too many depots in " + parameters.getDepotFileName() + ". The depot file should only contain one depot.");
            }
            // Define function which computes costs of empty trips as lenght*cost_length + duration*cost_duration. First argument must be length, second must be time
            Function<Pair<Double, Double>, Double> connectionObjective = pair ->
                parameters. getFactorLength() * pair.getFirstElement() + parameters.getFactorTime() * pair.getSecondElement();

            // Compute the station distances, first entry in distanceTimeMap is length (in km), second is duration (in sec)
            Map<Integer, Map<Integer, Pair<Double, Double>>> distanceTimeMap = computeStationDistances(
                ptn,
                parameters.getTimeUnitsPerMinute(),
                parameters.getSpeedLevel()
                );
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
                TripConnection fromDepot;
                TripConnection toDepot;
                if (depots.size()!=0){
                    Depot depot_data = depots.get(0);
                    Stop nearestStop = depot_data.getNearestStop(ptn);
                    fromDepot = new TripConnection(
                        connectionIndex,
                        depot,
                        node,
                        parameters.getVehicleCost()
                        + depot_data.getTimeDistanceToStop(nearestStop, parameters.getSpeed()) * parameters.getFactorTime()
                        + connectionObjective.apply(distanceTimeMap.get(depot_data.getNearestStop(ptn).getId()).get(trip.getStartStopId())));
                    connectionIndex += 1;
                    toDepot = new TripConnection(
                        connectionIndex,
                        node,
                        depot,
                        depot_data.getTimeDistanceToStop(nearestStop, parameters.getSpeed())
                        + connectionObjective.apply(distanceTimeMap.get(trip.getEndStopId()).get(depots.get(0).getNearestStop(ptn).getId())));
                } else {
                    fromDepot = new TripConnection(
                        connectionIndex,
                        depot,
                        node,
                        parameters.getVehicleCost());
                    connectionIndex += 1;
                    toDepot = new TripConnection(
                        connectionIndex,
                        node,
                        depot,
                        0);
                }


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

    static VehicleSchedule computeSchedule(Collection<TripConnection> usedConnections, Parameters parameters, Optional<Depot> depot, Graph<Stop,Link> ptn) {
        VehicleSchedule schedule = new VehicleSchedule();
        int vehicleId = 1;
        Map<Integer, Map<Integer, Pair<Double, Double>>> distanceTimeMap = computeStationDistances(ptn, parameters.getTimeUnitsPerMinute(), parameters.getSpeedLevel());
        // Look for all connections starting in the depot. These are the start of all vehicle tours
        List<TripConnection> outgoingDepotConnections = usedConnections.stream().filter(tripConnection ->
            tripConnection.getLeftNode().isDepot()).collect(Collectors.toList());
        while (!outgoingDepotConnections.isEmpty()) {
            Circulation circulation = new Circulation(vehicleId);
            VehicleTour tour = new VehicleTour(vehicleId);
            circulation.addVehicle(tour);
            vehicleId += 1;
            int tripId = 1;
            TripConnection currentEdge = outgoingDepotConnections.get(0);
            outgoingDepotConnections.remove(currentEdge);
            Trip sourceTrip = null;
            Trip targetTrip = null;
            while (!currentEdge.getRightNode().isDepot()) {
                sourceTrip = currentEdge.getLeftNode().getTrip();
                targetTrip = currentEdge.getRightNode().getTrip();
                if (!currentEdge.getLeftNode().isDepot()) {
                    // Create the empty trip and add it
                    Trip emptyTrip = new Trip(
                        sourceTrip.getEndAperiodicEventId(),
                        sourceTrip.getEndPeriodicEventId(),
                        sourceTrip.getEndStopId(),
                        sourceTrip.getEndTime(),
                        targetTrip.getStartAperiodicEventId(),
                        targetTrip.getStartPeriodicEventId(),
                        targetTrip.getStartStopId(),
                        targetTrip.getStartTime(),
                        -1,
                        TripType.EMPTY);
                    tour.addTrip(tripId, emptyTrip);
                    tripId += 1;
                } else if (depot.isPresent()) {
                    Trip emptyTrip = new Trip(
                        -1,
                        -1,
                        -depot.get().getDepotId(),
                        (int)(targetTrip.getStartTime()
                        - depot.get().getTimeDistanceToStop(depot.get().getNearestStop(ptn),parameters.getSpeed())
                        - distanceTimeMap.get(depot.get().getNearestStop(ptn).getId()).get(targetTrip.getStartStopId()).getSecondElement()
                        - parameters.getTurnoverTime()),
                        targetTrip.getStartAperiodicEventId(),
                        targetTrip.getStartPeriodicEventId(),
                        targetTrip.getStartStopId(),
                        targetTrip.getStartTime(),
                        -1,
                        TripType.EMPTY);
                    tour.addTrip(tripId, emptyTrip);
                    tripId += 1;
                }
                tour.addTrip(tripId, targetTrip);
                tripId += 1;
                TripNode lastTripNode = currentEdge.getRightNode();
                currentEdge = usedConnections.stream().filter(tripConnection -> tripConnection.getLeftNode().equals
                    (lastTripNode)).findAny().orElse(null);
                if (currentEdge == null) {
                    logger.error("Could not find next trip!");
                    throw new LinTimException("Could not find next trip!");
                }
            }
            if (depot.isPresent()) {
                Trip emptyTrip = new Trip(
                    targetTrip.getEndAperiodicEventId(),
                    targetTrip.getEndPeriodicEventId(),
                    targetTrip.getEndStopId(),
                    targetTrip.getEndTime(),
                    -1,
                    -1,
                    -depot.get().getDepotId(),
                    (int)(targetTrip.getEndTime()
                    + depot.get().getTimeDistanceToStop(depot.get().getNearestStop(ptn),parameters.getSpeed())
                    + distanceTimeMap.get(depot.get().getNearestStop(ptn).getId()).get(targetTrip.getEndStopId()).getSecondElement()
                    + parameters.getTurnoverTime()),
                    -1,
                    TripType.EMPTY);
                tour.addTrip(tripId, emptyTrip);
            }

            schedule.addCirculation(circulation);
        }
        return schedule;
    }

    private static Map<Integer, Map<Integer, Pair<Double, Double>>> computeStationDistances(
        Graph<Stop, Link> ptn,
        int timeUnitsPerMinute,
        String speedLevel
        ) {
        // TODO: Should we use another approach, to model the objective function regarding empty kilometers and time
        // TODO: correctly? This will choose a longer path, that needs less time, even if it is worth in the objective
        // TODO: What we really need: The shortest path w.r.t the objective, that is not too long w.r.t the duration
        // TODO: For now, mimic the behavior of the canal-based vehicle schedules.
        // TODO: See #264 in gitlab.
        Function<Link, Double> lengthFunction;
        if ((speedLevel.charAt(0) == 'f' || speedLevel.charAt(0) == 'F') && (speedLevel.charAt(1) == 'a' || speedLevel.charAt(1) == 'A') && (speedLevel.charAt(2) == 's' || speedLevel.charAt(2) == 'S') && (speedLevel.charAt(3) == 't' || speedLevel.charAt(3) == 'T'))  {
            lengthFunction = link -> (double) link.getLowerBound() * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        } else if ((speedLevel.charAt(0) == 's' || speedLevel.charAt(0) == 'S') && (speedLevel.charAt(1) == 'l' || speedLevel.charAt(1) == 'L') && (speedLevel.charAt(2) == 'o' || speedLevel.charAt(2) == 'O') && (speedLevel.charAt(3) == 'w' || speedLevel.charAt(3) == 'W')) {
            lengthFunction = link -> (double) link.getUpperBound() * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        } else {
            lengthFunction = link -> (double) (link.getLowerBound() + link.getUpperBound())/ 2 * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        }


        HashMap<Integer, Map<Integer, Pair<Double, Double>>> returnMap = new HashMap<>();
        for (Stop origin : ptn.getNodes()) {
            returnMap.put(origin.getId(), new HashMap<>());
            Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(ptn, origin, lengthFunction);
            dijkstra.computeShortestPaths();
            for (Stop destination : ptn.getNodes()) {
                if (origin.equals(destination)) {
                    returnMap.get(origin.getId()).put(destination.getId(), new Pair<>(0., 0.));
                } else {
                    var path = dijkstra.getPath(destination);
                    if (path == null) {
                        logger.debug("No path found from stop " + origin.getId()
                            + " to stop " + destination.getId()
                            + ". Setting distance and time to Infinity.");
                        returnMap.get(origin.getId()).put(
                            destination.getId(),
                            new Pair<>(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
                        );
                    } else {
                        double distance = path.getEdges().stream()
                            .mapToDouble(Link::getLength).sum();
                        double time = dijkstra.getDistance(destination);
                        returnMap.get(origin.getId()).put(
                            destination.getId(), new Pair<>(distance, time));
                    }
                }
            }
        }
        return returnMap;
    }

}