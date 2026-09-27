package net.lintim.model.vehiclescheduling;

import net.lintim.model.Node;
import net.lintim.model.Trip;
import net.lintim.util.Logger;

/**
 * Representation of a node in the trip graph. A node represents a trip or the depot.
 */
public class TripNode implements Node {

    private static final Logger logger = new Logger(TripNode.class.getCanonicalName());

    private final Trip trip;
    private final boolean isDepot;
    private int id;

    /**
     * Create a new node for the trip graph. Must be either a depot or represent the depot.
     *
     * @param id      the id of the node.
     * @param trip    the trip to represent. Must be null if this is the depot
     * @param isDepot whether this node is the depot. Needs to be false if a trip is provided
     */
    public TripNode(int id, Trip trip, boolean isDepot) {
        this.id = id;
        this.trip = trip;
        this.isDepot = isDepot;
        if ((this.trip != null && isDepot) || (this.trip == null && !isDepot)) {
            logger.warn("Unfitting trip and isDepot!");
        }
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Get the corresponding trip of the node. Is null, if this node is the depot
     *
     * @return the trip of the node
     */
    public Trip getTrip() {
        return trip;
    }

    /**
     * Whether this node corresponds to the depot
     *
     * @return whether this node is the depot
     */
    public boolean isDepot() {
        return isDepot;
    }
}
