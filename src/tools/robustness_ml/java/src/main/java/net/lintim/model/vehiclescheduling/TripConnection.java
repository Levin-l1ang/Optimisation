package net.lintim.model.vehiclescheduling;

import net.lintim.model.Edge;

/**
 * Class representing an edge in the trip graph, i.e., connecting to trips.
 */
public class TripConnection implements Edge<TripNode> {

    private int id;
    private final TripNode sourceTrip;
    private final TripNode targetTrip;
    private final double cost;

    /**
     * Create a new trip connection.
     *
     * @param id         the id of the connection
     * @param sourceTrip the source trip, i.e., the first trip in time
     * @param targetTrip the target trip, i.e., the second trip in time
     * @param cost       the cost of the connection, i.e., the costs that occur when this two trips are served by the same
     *                   vehicle directly after each other
     */
    public TripConnection(int id, TripNode sourceTrip, TripNode targetTrip, double cost) {
        this.id = id;
        this.sourceTrip = sourceTrip;
        this.targetTrip = targetTrip;
        this.cost = cost;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public TripNode getLeftNode() {
        return sourceTrip;
    }

    @Override
    public TripNode getRightNode() {
        return targetTrip;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    /**
     * Get the cost of the connection, i.e., the costs that occur when this two trips are served by the same
     * vehicle directly after each other
     *
     * @return the cost of the connection
     */
    public double getCost() {
        return cost;
    }
}
