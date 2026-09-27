package net.lintim.model;


import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import net.lintim.util.Logger;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * A class for representing a ridepooling area.
 */
public class RidepoolingArea {
    private int area_id;
    private int nb_vehicles;
    private LinkedList<Link> edges;
    private HashMap<Integer, Double> stretch_factor;
    private HashMap<Integer, Double> vehicle_frequency;      /* -1=value not set */

    private static Logger logger = new Logger(RidepoolingArea.class);

    /**
     * Constructor for a ridepooling area with given information.
     * @param area_id the id of the area
     * @param nb_vehicles number of vehicles operating in the area
     * @param edges: list of edges contained in the area
     */
    public RidepoolingArea(int id) {
         this.area_id = id;
         this.nb_vehicles = 0;
         this.edges = new LinkedList<>();
         this.stretch_factor = new HashMap<>();
         this.vehicle_frequency = new HashMap<>();
    }

    /**
     * Method to add a new link to the ridepooling area
     * @param edge the link to add
     * @param stretch_factor factor to stretch the length (usually minimal duration) of this edge. Default is 1.
     * @param vehicle_frequency_value vehicle frequency of one vehicle on this edge. Default of -1 denotes value not set.
     * @return whether the link could be added
     */
public boolean addLink(Link edge, double stretchFactorValue, double vehicle_frequency_value) {
    int index = Collections.binarySearch(edges, edge, Comparator.comparingLong(Link::getId));

    if (index >= 0) {
        logger.warn(String.format("Link %d is already contained in ridepooling area %d!", edge.getId(), this.getId()));
        return false;
    } else {
        int insertionPoint = -(index + 1);
        edges.add(insertionPoint, edge);

        stretch_factor.put(edge.getId(), stretchFactorValue);
        vehicle_frequency.put(edge.getId(), vehicle_frequency_value);

        return true;
    }
}

    /**
     * Method to add a new link to the ridepooling area
     * @param edge the link to add
     * @return whether the link could be added
     */
    public boolean addLink(Link edge) {
        if (edges.contains(edge)) {
            logger.warn(String.format("Link %d is already contained in ridepooling area %d!", edge.getId(), this.getId()));
            return false;
        } else {
            int index = 0;
            while (index < edges.size() && edges.get(index).getId() < edge.getId()) {
                index++;
            }
            edges.add(index, edge);

            stretch_factor.put(edge.getId(), 1.0);
            vehicle_frequency.put(edge.getId(), -1.0);

            return true;
        }
    }

    /**
     * Method to add a new link with stretch factor to the ridepooling area
     * @param edge the link to add
     * @param stretch_factor factor to stretch the length (usually minimal duration) of this edge. Default is 1.
     * @return whether the link could be added
     */
    public boolean addLinkWithStretch(Link edge, double stretchFactorValue) {
        if (edges.contains(edge)) {
            logger.warn(String.format("Link %d is already contained in ridepooling area %d!", edge.getId(), this.getId()));
            return false;
        } else {
            int index = 0;
            while (index < edges.size() && edges.get(index).getId() < edge.getId()) {
                index++;
            }
            edges.add(index, edge);

            stretch_factor.put(edge.getId(), stretchFactorValue);
            vehicle_frequency.put(edge.getId(), -1.0);

            return true;
        }
    }

    /**
     * Method to add a new link with vehicle_frequency factor to the ridepooling area
     * @param edge the link to add
     * @param vehicle_frequency_value vehicle frequency of one vehicle on this edge. Default of -1 denotes value not set.
     * @return whether the link could be added
     */
    public boolean addLinkWithDistribution(Link edge, double vehicle_frequency_value) {
        if (edges.contains(edge)) {
            logger.warn(String.format("Link %d is already contained in ridepooling area %d!", edge.getId(), this.getId()));
            return false;
        } else {
            int index = 0;
            while (index < edges.size() && edges.get(index).getId() < edge.getId()) {
                index++;
            }
            edges.add(index, edge);

            stretch_factor.put(edge.getId(), 1.0);
            vehicle_frequency.put(edge.getId(), vehicle_frequency_value);

            return true;
        }
    }

    /**
     * Method to get the id of the area.
     * @return area id
     */
    public int getId() {
        return area_id;
    }

    /**
     * Method to get the number of vehicles operating in the area.
     * @return number of vehicles
     */
    public int getNumberOfVehicles() {
        return nb_vehicles;
    }

    /**
     * Method to get edges belonging to the area.
     * @return List of the edges
     */
    public LinkedList<Link> getEdges() {
        return edges;
    }

    /**
     * Gets the factor for the length (usually minimal duration) of the specified edge.
     * @param edge_id: the Id of the edge to get the stretch factor
     * @return the stretch factor of the edge
     */
    public Double getStretchFactor(int edge_id) {
        return stretch_factor.get(edge_id);
    }

    /**
     * Gets the stretch factors for the lengths (usually minimal duration) of all edges of the area.
     * @return hash map of the stretch factors
     */
    public HashMap<Integer, Double> getAreaStretchFactors() {
        return stretch_factor;
    }

    /**
     * Gets the vehicle frequency in this area on a specific edge.
     * @param edge_id: the Id of the edge in this area
     * @return the vehicle frequency
     */
    public Double getVehicleFrequency(int edge_id) {
        return vehicle_frequency.get(edge_id);
    }

    /**
     * Gets the vehicle frequency in this area of all edges.
     * @return hash map of the vehicle frequency
     */
    public HashMap<Integer, Double> getVehicleFrequencies() {
        return vehicle_frequency;
    }

    /**
     * Sets the number of vehicles operating in the area
     * @param number_vehicles: number of vehicles
     */
    public void setNumberOfVehicles(int number_vehicles) {
        nb_vehicles = number_vehicles;
    }

    /**
     * Set the stretch factor for the length (usually minimal duration) of a specific edge in this area to the given factor.
     * @param edge_id id of the edge in the area
     * @param stretch stretch factor to set
     * @return Whether the value was set succesfully or not
     */
    public boolean setStretchFactor(int edge_id, double stretch) {
        boolean edgeExists = false;
        for (Link edge : edges) {
            if (edge.getId() == edge_id) {
                edgeExists = true;
                break;
            }
        }

        if (!edgeExists) {
            return false;
        }

        stretch_factor.put(edge_id, stretch);
        return true;
    }

    /**
     * Set the vehicle frequency in this area on a specific edge to the given factor.
     * @param edge_id id of the edge in the area
     * @param vehicle_freq vehicle_frequency to set
     * @return Whether the value was set succesfully or not
     */
    public boolean setDistribution(int edge_id, double vehicle_freq) {
        boolean edgeExists = false;
        for (Link edge : edges) {
            if (edge.getId() == edge_id) {
                edgeExists = true;
                break;
            }
        }

        if (!edgeExists) {
            return false;
        }

        vehicle_frequency.put(edge_id, vehicle_freq);
        return true;
    }

    /**
     * Method to compare the edge sets of areas.
     * @param other area to compare
     * @return whether the edge sets are equal
     */
    public boolean compareEdges(RidepoolingArea other) {
        return edges == other.getEdges();
    }

    /**
     * Method to test if the area is strongly connected.
     * @return whether the area is strongly connected
     */
    public boolean isConnected() {
        if (edges.isEmpty()) {
            return true;
        }

        Graph<Integer, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (Link edge : edges) {
            int leftNodeId = edge.getLeftNode().getId();
            int rightNodeId = edge.getRightNode().getId();
            graph.addVertex(leftNodeId);
            graph.addVertex(rightNodeId);
            graph.addEdge(leftNodeId, rightNodeId);

            if (!edge.isDirected()) {
                graph.addEdge(rightNodeId, leftNodeId);
            }
        }

        KosarajuStrongConnectivityInspector<Integer, DefaultEdge> inspector = new KosarajuStrongConnectivityInspector<>(graph);
        return inspector.isStronglyConnected();

    }

    /**
     * Method to test if a given edge is yet included in the area.
     * @param edge edge to test
     * @return whether the edge is included or not
     */
    public boolean includesEdge(Link edge) {
        return edges.contains(edge);
    }

    /**
     * Method to compute the vehicle frequency for each edge in the area by constructing
    *  an Euler circle in a graph with multiple parallels.
     * @param period_length the period length to consider
     * @param vehicle_capacity capacity (or average occupancy) of one ridepooling vehicle
     */
    public void computeVehicleFrequenciesByEulerCircle(int period_length, int vehicle_capacity) {
        int sumOfWeightedDurations = 0;

        for (Link edge : edges) {
            int lowerBound = edge.getLowerBound();
            double load = edge.getLoad();
            int weightedDuration = (int) (lowerBound * 2 * (Math.ceil(load / vehicle_capacity) + 1) / 2);
            sumOfWeightedDurations += weightedDuration;
        }

        for (Link edge : edges) {
            double load = edge.getLoad();
            int edgeId = edge.getId();
            int intermediate = (int) (2 * (Math.ceil(load / vehicle_capacity) + 1) / 2);
            double vehicle_frequency_value = intermediate * period_length / sumOfWeightedDurations;
            vehicle_frequency.put(edgeId, vehicle_frequency_value);

        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Area ").append(this.getId())
        .append("\n").append("Edge; stretch-factor; vehicle-frequency-of-edge\n");

        for (Link edge : edges) {
            sb.append(edge.getId()).append("; ")
            .append(getStretchFactor(edge.getId())).append("; ")
            .append(getVehicleFrequency(edge.getId())).append("\n");
        }

        return sb.toString();
    }
}
