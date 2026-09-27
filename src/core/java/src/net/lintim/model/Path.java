package net.lintim.model;

import java.util.Iterator;
import java.util.List;

/**
 * Interface for a possibly directed Path in a Graph
 */
public interface Path<N extends Node, E extends Edge<N>> extends Iterable<N>{

    /**
     * Get a list of the nodes of this path. Note that this returns a copy, i.e., appending or deleting nodes to the
     * returned list will not be reflected in the path.
     * @return a copy of the nodes
     */
    List<N> getNodes();

    /**
     * Get a list of the edges of this path. Note that this returns a copy, i.e., appending or deleting edges to the
     * returned list will not be reflected in the path.
     * @return a copy of the edges
     */
    List<E> getEdges();

    /**
     * Return whether the path is directed, i.e., whether all the edges are traversed in "forward" direction or not
     * @return whether the path is directed
     */
    boolean isDirected();

    /**
     * Add an edge to the beginning of the path.
     * @param edge the edge to add
     * @return whether the edge could be added to the path
     */
    boolean addFirst(E edge);

    /**
     * Add a list of edges to the beginning of the path. Note, that the first element in the given list will be the
     * first element of the path after the addition.
     * @param edges a list of edges to add to the beginning of the path
     * @return whether the edges could be added.
     */
    boolean addFirst(List<E> edges);


    /**
     * Add an edge to the end of the path.
     * @param edge the edge to add
     * @return whether the edge could be added to the path
     */
    boolean addLast(E edge);

    /**
     * Add a list of edges to the end of the path. Note, that the last element in the given list will be the
     * last element of the path after the addition.
     * @param edges a list of edges to add to the end of the path
     * @return whether the edges could be added.
     */
    boolean addLast(List<E> edges);

    /**
     * Determine, whether the given edge can be added to the start of the path, i.e., if the method
     * {@link #addFirst(Edge)} can be called safely. The path will not be changed when using this method!
     * @param edge the edge to add.
     * @return whether the edge can be added to the start of the path
     */
    boolean canAppendToStart(E edge);

    /**
     * Determine, whether the given edge can be added to the end of the path, i.e., if the method
     * {@link #addLast(Edge)} can be called safely. The path will not be changed when using this method!
     * @param edge the edge to add.
     * @return whether the edge can be added to the end of the path
     */
    boolean canAppendToEnd(E edge);

    /**
     * Will remove the given edge from the path, if possible. The path needs to be valid afterwards, i.e., the edge
     * will not be removed if this is not the case.
     * @param edge the edge to remove
     * @return whether the edge could be removed
     */
    boolean remove(E edge);

    /**
     * Will remove the given edges from the path, if possible. Currently only supported for the beginning or the end
     * of a path. Will throw otherwise.
     * @param edges the edges to remove
     * @return whether the edges could be removed
     */
    boolean remove(List<E> edges);

    /**
     * Check if the given path is contained in this path. Considers whether this path is directed, i.e., if this path
     * is directed, the given subPath must be contained in forward direction.
     * @param subPath the path to check
     * @return whether subPath is contained in this path
     */
    default boolean contains(Path<N, E> subPath) {
        //First, check the forward direction
        for (int superIndex = 0; superIndex < this.getEdges().size(); superIndex++) {
            if (this.getEdges().get(superIndex).equals(subPath.getEdges().get(0))) {
                //Found the first edge. Now we have to check the whole subset
                boolean foundWholePath = true;
                for (int subIndex = 1; subIndex < subPath.getEdges().size(); subIndex++) {
                    if(superIndex + subIndex >= this.getEdges().size()){
                        foundWholePath = false;
                        break;
                    }
                    if (!this.getEdges().get(superIndex + subIndex).equals(subPath.getEdges().get(subIndex))) {
                        foundWholePath = false;
                    }
                }
                if (foundWholePath) {
                    return true;
                }
            }
        }
        if (!isDirected()) {
            for (int superIndex = this.getEdges().size() - 1; superIndex >= 0; superIndex--) {
                if (this.getEdges().get(superIndex).equals(subPath.getEdges().get(0))) {
                    //Found the first edge. Now we have to check the whole subset
                    boolean foundWholePath = true;
                    for (int subIndex = 1; subIndex < subPath.getEdges().size(); subIndex++) {
                        if(superIndex - subIndex < 0){
                            foundWholePath = false;
                            break;
                        }
                        if (!this.getEdges().get(superIndex - subIndex).equals(subPath.getEdges().get(subIndex))) {
                            foundWholePath = false;
                        }
                    }
                    if (foundWholePath) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    Path<N,E> getSubPath(N start, N end);

    /**
     * Check, whether this path contains the given edge.
     * @param edge the edge to check
     * @return whether this path contains the given edge
     */
    default boolean contains(E edge) {
        return getEdges().contains(edge);
    }

    /**
     * Check, whether this path contains the given node.
     * @param node the node to check
     * @return whether this path contains the given node
     */
    default boolean contains(N node){
        return getNodes().contains(node);
    }

    @Override
    default Iterator<N> iterator() {
        return getNodes().iterator();
    }

    /**
     * Get an iterator for the edges.
     * @return an edge iterator
     */
    default Iterator<E> edgeIterator() {
        return getEdges().iterator();
    }

}
