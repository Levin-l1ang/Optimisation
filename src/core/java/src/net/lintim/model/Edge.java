package net.lintim.model;

import net.lintim.exception.LinTimException;

/**
 * The template for an edge object for a graph structure. Used in the directed and undirected case.
 */
public interface Edge<N extends Node> {
    /**
     * Get the id of an edge. This is a representation of the edge in a graph and needs to be unique.
     *
     * @return the id of the edge
     */
    int getId();

    /**
     * Set the id of this edge. THIS METHOD MUST ONLY BE USED IN GRAPH IMPLEMENTATIONS. Changing the id of a edge in
     * a graph may break the graph structure, depending on implementation
     *
     * @param id the new id of the edge
     */
    void setId(int id);

    /**
     * Get the left node of the edge. If the edge is directed, this is the source of the edge.
     *
     * @return the left node
     */
    N getLeftNode();

    /**
     * Get the right node of the edge. If the edge is directed, this is the target of the edge
     *
     * @return the right node
     */
    N getRightNode();

    /**
     * Get whether this edge is directed. If the edge is directed, it "flows" from left to right node, i.e., the left
     * node is the source and the right node is the target of the edge
     *
     * @return whether the edge is directed
     */
    boolean isDirected();

    default N getOtherSide(N oneSide) {
        if (getLeftNode().equals(oneSide)) {
            return getRightNode();
        }
        else if (getRightNode().equals(oneSide)) {
            return getLeftNode();
        }
        else {
            throw new LinTimException("Found invalid method call, " + oneSide + " is not part of " + this);
        }
    }
}
