package net.lintim.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * A class to represent the ridepool.
 */
public class RidepoolingPool {
    private HashMap<Integer, RidepoolingArea> rpool;
    private double costs;

    /**
     * Constructor of a new empty ridepool.
     * @param cost costs of a single vehicle operating in one area of the ridepool
     */
    public RidepoolingPool(double cost) {
        this.rpool = new HashMap<>();
        this.costs = cost;
    }

    /**
     * Method to add a ridepooling area, if not already an area with the same id is in the
     * pool. Areas with the same edge set as an already existing area can only be included,
     * if they differ in the vehicle frequency values.
     * @param area the ridepooling area to add
     * @return whether the area could be added.
     */
    public boolean addArea(RidepoolingArea area) {
        if (rpool.containsKey(area.getId())) {
            return false;
        }
        for (RidepoolingArea other : rpool.values()) {
            if (new HashSet<>(Arrays.asList(area.getEdges())).equals(new HashSet<>(Arrays.asList(other.getEdges()))) &&
            area.getVehicleFrequencies().equals(other.getVehicleFrequencies())) {
                return false;
            }
        }
        rpool.put(area.getId(), area);
        return true;
    }

    /**
     * Method to remove area with given id, if it exists in pool.
     * @param areaId id of the area to remove
     * @return whether an area was removed
     */
    public boolean removeArea(int areaId) {
        return rpool.remove(areaId) != null;
    }

    /**
     * Gets a list of the ridepooling areas.
     * @return the areas in the pool
     */
    public List<RidepoolingArea> getAreas() {
        return new ArrayList<>(rpool.values());
    }

    /**
     * Gets the ridepooling area for a given id.
     * @param areaId id of the area to get
     * @return the area with the given id
     */
    public RidepoolingArea getArea(int areaId) {
        return rpool.get(areaId);
    }

    /**
     * Method to get a list of all ridepooling areas with number of vehicles > 0.
     * @return a list of all ridepooling areas with number of vehicles > 0
     */
    public List<RidepoolingArea> getRideConcept() {
        List<RidepoolingArea> result = new ArrayList<>();
        for (RidepoolingArea area : rpool.values()) {
            if (area.getNumberOfVehicles() > 0) {
                result.add(area);
            }
        }
        return result;
    }

    /**
     * Gets the cost of one vehicle operating in an area in the ridepool
     * @return cost of one vehicle operating in the area
     */
    public double getCost() {
        return costs;
    }

    /**
     * Sets the cost of one vehicle operating in an area in the ridepool
     * @param cost cost of one vehicle operating in the area
     */
    public void setCost(double cost) {
        this.costs = cost;
    }

    /**
     * Method to test if all areas in the ridepooling pool are strongly connected.
     * @return the id of the area which is not strongly connected, or -1 if all areas are strongly connected.
     */
    public int testConnected() {
        for (RidepoolingArea area : rpool.values()) {
            if (!area.isConnected()) {
                return area.getId();
            }
        }
        return -1;
    }

    /**
     * Method to compute the vehicle frequency for each edge in every ridepooling area.
     * @param periodLength the period length to consider
     * @param vehicleCapacity capacity of one ridepooling vehicle
     */
    public void computeVehicleFrequenciesByEulerCircle(int periodLength, int vehicleCapacity) {
        for (RidepoolingArea area : getAreas()) {
            area.computeVehicleFrequenciesByEulerCircle(periodLength, vehicleCapacity);
        }
    }

    /**
     * Gets the vehicle frequency in a specific area on a specific edge.
     * @param areaId the id of the area
     * @param edgeId id of the edge in this area
     * @return the vehicle frequency
     */
    public double getVehicleFrequency(int areaId, int edgeId) {
        return getArea(areaId).getVehicleFrequency(edgeId);
    }

    /**
     * Gets the vehicle frequency in a specific area of all edges.
     * @param areaId the id of the area
     * @return hash map of the vehicle frequency
     */
    public Map<Integer, Double> getVehicleFrequencies(int areaId) {
        return getArea(areaId).getVehicleFrequencies();
    }

    /**
     * Gets the factor for the length of the specified edge in the specified area.
     * @param areaId the id of the area
     * @param edgeId the Id of the edge to get the stretch factor
     * @return the stretch factor of the edge
     */
    public double getStretchFactor(int areaId, int edgeId) {
        return getArea(areaId).getStretchFactor(edgeId);
    }

    /**
     * Gets the stretch factors for the lengths of all edges of a specific area.
     * @param areaId the id of the area
     * @return hash map of the stretch factors
     */
    public Map<Integer, Double> getAreaStretchFactors(int areaId) {
        return getArea(areaId).getAreaStretchFactors();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RidepoolingPool:\n");
        for (RidepoolingArea area : getAreas()) {
            sb.append(String.format("%d: %d\n", area.getId(), area.getNumberOfVehicles()));
        }
        return sb.toString();
    }
}