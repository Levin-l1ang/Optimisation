package net.lintim.model;

/**
 * The template for a node object for a graph structure.
 */
public interface Node {

    /**
     * Get the id of the node. This is a representation of the node in a graph and needs to be unique.
     *
     * @return the id of the node
     */
    int getId();

    /**
     * Set the id of this node. THIS METHOD MUST ONLY BE USED IN GRAPH IMPLEMENTATIONS. Changing the id of a node in
     * a graph may break the graph structure, depending on implementation
     *
     * @param id the new id of the node
     */
    void setId(int id);
}
