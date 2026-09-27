package net.lintim.model;

import net.lintim.io.CsvReader;
import net.lintim.exception.LinTimException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.Stop;
import net.lintim.model.Link;
import net.lintim.model.Graph;
import net.lintim.util.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import net.lintim.algorithm.Dijkstra;



/**
 * A class to represent a depot, i.e., a location where vehicles are stored and from which
 * they start and end their operations.
 */
public class Depot {
    private int depotId;
    private double xCoordinate;
    private double yCoordinate;
    private int numberOfVehicles;

    /**
     * Constructor of a depot.
     *
     * @param depotId           the id of the depot
     * @param xCoordinate       the x coordinate of the depot
     * @param yCoordinate       the y coordinate of the depot
     * @param numberOfVehicles  the number of vehicles stored at this depot
     * @throws LinTimException  if depotId or numberOfVehicles is negative
     */
    public Depot(int depotId, double xCoordinate, double yCoordinate, int numberOfVehicles) {
        if (depotId < 0) {
            throw new LinTimException("Id of depot is not allowed to be negative: " + depotId);
        }
        if (numberOfVehicles < 0) {
            throw new LinTimException("Number of vehicles is not allowed to be negative: " + numberOfVehicles);
        }
        this.depotId = depotId;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.numberOfVehicles = numberOfVehicles;
    }

    /**
     * Gets the id of the depot.
     *
     * @return the id of the depot
     */
    public int getDepotId() {
        return depotId;
    }

    /**
     * Sets the id of the depot.
     *
     * @param depotId           the new id of the depot
     * @throws LinTimException  if depotId is negative
     */
    public void setDepotId(int depotId) {
        if (depotId < 0) {
            throw new LinTimException("Id of depot is not allowed to be negative: " + depotId);
        }
        this.depotId = depotId;
    }

    /**
     * Gets the x coordinate of the depot.
     *
     * @return the x coordinate of the depot
     */
    public double getXCoordinate() {
        return xCoordinate;
    }

    /**
     * Sets the x coordinate of the depot.
     *
     * @param xCoordinate the new x coordinate of the depot
     */
    public void setXCoordinate(double xCoordinate) {
        this.xCoordinate = xCoordinate;
    }

    /**
     * Gets the y coordinate of the depot.
     *
     * @return the y coordinate of the depot
     */
    public double getYCoordinate() {
        return yCoordinate;
    }

    /**
     * Sets the y coordinate of the depot.
     *
     * @param yCoordinate the new y coordinate of the depot
     */
    public void setYCoordinate(double yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    /**
     * Gets the number of vehicles stored at this depot.
     *
     * @return the number of vehicles at the depot
     */
    public int getNumberOfVehicles() {
        return numberOfVehicles;
    }

    /**
     * Sets the number of vehicles stored at this depot.
     *
     * @param numberOfVehicles  the new number of vehicles at the depot
     * @throws LinTimException  if numberOfVehicles is negative
     */
    public void setNumberOfVehicles(int numberOfVehicles) {
        if (numberOfVehicles < 0) {
            throw new LinTimException("Number of vehicles is not allowed to be negative: " + numberOfVehicles);
        }
        this.numberOfVehicles = numberOfVehicles;
    }

    /**
     * Indicates whether some other object is "equal to" this depot.
     * Two depots are considered equal if they have the same id, coordinates,
     * and number of vehicles.
     *
     * @param obj the reference object with which to compare
     * @return true if this object is the same as the obj argument; false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Depot)) {
            return false;
        }
        Depot other = (Depot) obj;
        return depotId == other.depotId &&
            Double.compare(xCoordinate, other.xCoordinate) == 0 &&
            Double.compare(yCoordinate, other.yCoordinate) == 0 &&
            numberOfVehicles == other.numberOfVehicles;
    }

    /**
     * Calculates the Euclidean distance from this depot to a given stop.
     *
     * @param stop the stop to calculate the distance to
     * @return the Euclidean distance from this depot to the stop
     */
    public double getDistanceToStop(Stop stop) {
        double dx = stop.getxCoordinate() - this.xCoordinate;
        double dy = stop.getyCoordinate() - this.yCoordinate;
        return Math.sqrt(dx * dx + dy * dy);
    }

     /**
     * Calculates the Euclidean distance from this depot to the nearest stop and from there uses the ptn to calculate the distance to a given stop.
     *
     * @param stop the stop to calculate the distance to
     * @param ptn the ptn to use
     * @return the Euclidean distance from this depot to the stop
     */
    public double getDistanceToStop(Stop stop, Graph<Stop, Link> ptn) {
    	Stop nearestStop = this.getNearestStop(ptn);
        double dx = nearestStop.getxCoordinate() - this.xCoordinate;
        double dy = nearestStop.getyCoordinate() - this.yCoordinate;
        double nearestStopDistance = Math.sqrt(dx * dx + dy * dy);
       	Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(ptn, nearestStop, Link::getLength);
	dijkstra.computeShortestPaths();
	double distanceNearestStopToStop = nearestStop.equals(stop) ? 0 : dijkstra.getPath(stop).getEdges().stream().mapToDouble(Link::getLength).sum();
	return distanceNearestStopToStop + nearestStopDistance;
	}

    /**
     * Calculates the time distance from this depot to a given stop based on vehicle speed using the euclidean distance only.
     *
     * @param stop         the stop to calculate the time distance to
     * @param vehicleSpeed the speed of the vehicle
     * @return the time distance from this depot to the stop
     */
    public double getTimeDistanceToStop(Stop stop, double vehicleSpeed) {
        double dx = stop.getxCoordinate() - this.xCoordinate;
        double dy = stop.getyCoordinate() - this.yCoordinate;
        return Math.sqrt(dx * dx + dy * dy)/ vehicleSpeed;
    }

       /**
     * Calculates the time distance from this depot to a given stop based on the ptn. We calculate the time distance to the nearest ptn stop using the euclidean distance and afterwards add the time we then need traversing through the ptn until we reach our destiantion stop.
     *
     * @param stop         the stop to calculate the time distance to
     * @param vehicleSpeed the speed of the vehicle
     * @param ptn_distances	a mapping that maps two ptn nodes on a tuple containing the shortest path duration and distance between those two nodes
     * @param ptn	the ptn to use
     * @return the time distance from this depot to the stop
     */
    public double getTimeDistanceToStop(
    	Stop stop,
    	double vehicleSpeed,
    	Map<Pair<Integer, Integer>, Pair<Double, Double>> ptn_distances,
    	Graph<Stop, Link> ptn
    	) {
    	Stop nearestStop = this.getNearestStop(ptn);
    	double timeDistanceToNearestStop = this.getDistanceToStop(nearestStop)/ vehicleSpeed;
    	double timeDistanceNearestStopToFinalStop = ptn_distances.get(
    		new Pair<>(
    			nearestStop.getId(),
    			stop.getId()
    		)
    	).getFirstElement();
    	return timeDistanceToNearestStop + timeDistanceNearestStopToFinalStop;
    }

    /**
     * Finds the nearest stop to this depot from a given public transport network.
     *
     * @param ptn the public transport network graph containing stops and links
     * @return the stop that is closest to this depot
     */
    public Stop getNearestStop(Graph<Stop, Link> ptn) {
        List<Pair<Double, Stop>> distanceList = new ArrayList<>();

        for (Stop stop : ptn.getNodes()) {
            double depotDistance = Math.pow(stop.getxCoordinate() - this.xCoordinate, 2)
                                 + Math.pow(stop.getyCoordinate() - this.yCoordinate, 2);
            distanceList.add(new Pair<>(depotDistance, stop));
        }

        Collections.sort(distanceList, Comparator.comparingDouble(Pair::getFirstElement));
        return distanceList.get(0).getSecondElement();
    }
}
