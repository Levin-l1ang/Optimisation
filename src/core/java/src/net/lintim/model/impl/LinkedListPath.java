package net.lintim.model.impl;

import net.lintim.model.Edge;
import net.lintim.model.Node;
import net.lintim.model.Path;
import net.lintim.util.Logger;

import java.util.*;

/**
 * Path implementation using two instances of java.util.LinkedList (one for edges, one for nodes)
 */
public class LinkedListPath<N extends Node, E extends Edge<N>> implements Path<N, E> {

    private static Logger logger = new Logger(LinkedListPath.class);

    private LinkedList<E> edgeList = new LinkedList<>();
    private LinkedList<N> nodeList = new LinkedList<>();
    private boolean directed;

    public LinkedListPath(boolean directed) {
        this.directed = directed;
    }

    @Override
    public List<N> getNodes() {
        return new LinkedList<>(nodeList);
    }

    @Override
    public List<E> getEdges() {
        return new LinkedList<>(edgeList);
    }

    @Override
    public boolean isDirected() {
        return directed;
    }

    @Override
    public boolean addFirst(E edge) {
        if (edge == null) throw new IllegalArgumentException("null cannot be an edge");
        //Different cases:
        //Case 1: Nodelist could be empty
        if(nodeList.isEmpty()){
            //We add the nodes in the "directed" direction. If the edge is undirected, we may need to resort the list
            //when we add the second edge
            nodeList.add(edge.getLeftNode());
            nodeList.add(edge.getRightNode());
        }
        //Case 2: The list has a size of at least 1 and the order we used in Case 1 was correct
        else if (edge.getLeftNode().equals(nodeList.getFirst()) && !directed)
            nodeList.addFirst(edge.getRightNode());
        else if (edge.getRightNode().equals(nodeList.getFirst()))
            nodeList.addFirst(edge.getLeftNode());
        //Case 3: The list has exactly size 2 (i.e., only one edge was added yet), is undirected and the order we used
        // in Case 1 was wrong (otherwise we will end in case 1 or 2)
        else if (nodeList.size() == 2 && !directed){
            Collections.reverse(nodeList);
            //Now we can handle case 2 again
            if (edge.getLeftNode().equals(nodeList.getFirst()) && !directed)
                nodeList.addFirst(edge.getRightNode());
            else if (edge.getRightNode().equals(nodeList.getFirst()))
                nodeList.addFirst(edge.getLeftNode());
            else {
                logger.debug("edge " + edge.getId() + " cannot be prepended to path (nodes don't match)");
                return false;
            }
        }
        else {
            logger.debug("edge " + edge.getId() + " cannot be prepended to path (nodes don't match)");
            return false;
        }
        edgeList.addFirst(edge);
        return true;
    }

    @Override
    public boolean addFirst(List<E> edges) {
        LinkedList<E> list = new LinkedList<>(edges);
        Collections.reverse(list);
        boolean succeeded = true;
        E failedElement = null;
        for (E edge : list) {
            succeeded = addFirst(edge);
            if (!succeeded) {
                failedElement = edge;
                break;
            }
        }
        if (!succeeded) {
            resetPath(list, failedElement);
        }
        return succeeded;
    }

    /**
     * Helper method to reset the path
     * @param insertList the list that was tried to add to the path
     * @param failedElement the failed element
     */
    private void resetPath(List<E> insertList, E failedElement) {
        List<E> listToRemove = new ArrayList<>();
        for (E edge : insertList) {
            if (edge.equals(failedElement)) {
                break;
            }
            listToRemove.add(edge);
        }
        Collections.reverse(listToRemove);
        this.remove(listToRemove);
    }

    @Override
    public boolean addLast(E edge) {
        if (edge == null) throw new IllegalArgumentException("null cannot be an edge");
        //Different cases:
        //Case 1: Nodelist could be empty
        if(nodeList.isEmpty()){
            //We add the nodes in the "directed" direction. If the edge is undirected, we may need to resort the list
            //when we add the second edge
            nodeList.add(edge.getLeftNode());
            nodeList.add(edge.getRightNode());
        }
        //Case 2: The list has a size of at least 1 and the order we used in Case 1 was correct
        else if (edge.getRightNode().equals(nodeList.getLast()) && !directed)
            nodeList.addLast(edge.getLeftNode());
        else if (edge.getLeftNode().equals(nodeList.getLast()))
            nodeList.addLast(edge.getRightNode());
        //Case 3: The list has exactly size 2 (i.e., only one edge was added yet), is undirected and the order we used
        // in Case 1 was wrong (otherwise we will end in case 1 or 2)
        else if (nodeList.size() == 2 && !directed){
            Collections.reverse(nodeList);
            //Now we can handle case 2 again
            if (edge.getRightNode().equals(nodeList.getLast()) && !directed)
                nodeList.addLast(edge.getLeftNode());
            else if (edge.getLeftNode().equals(nodeList.getLast()))
                nodeList.addLast(edge.getRightNode());
            else {
                logger.debug("edge " + edge.getId() + " cannot be appended to path (nodes don't match)");
                return false;
            }
        }
        else {
            logger.debug("edge " + edge.getId() + " cannot be appended to path (nodes don't match)");
            return false;
        }
        edgeList.addLast(edge);
        return true;
    }

    @Override
    public boolean addLast(List<E> edges) {
        boolean succeeded = true;
        E failedElement = null;
        for (E edge : edges) {
            succeeded = addLast(edge);
            if (!succeeded) {
                failedElement = edge;
                break;
            }
        }
        if (!succeeded) {
            resetPath(edges, failedElement);
        }
        return succeeded;
    }

    @Override
    public boolean remove(E edge) {
        if (edge == null) throw new IllegalArgumentException("null cannot be an edge");
        if (edgeList.isEmpty()) {
            return false;
        }
        if (edge.equals(edgeList.getFirst())) {
            edgeList.removeFirst();
            nodeList.removeFirst();
            handleEmptyCase();
            return true;
        }
        if (edge.equals(edgeList.getLast())) {
            edgeList.removeLast();
            nodeList.removeLast();
            handleEmptyCase();
            return true;
        }
        if (!edge.getLeftNode().equals(edge.getRightNode()))
            throw new IllegalArgumentException("edge to be removed is neither in the beginning nor in the end");
        ListIterator<N> nodeIterator = nodeList.listIterator();
        ListIterator<E> edgeIterator = edgeList.listIterator();
        while (edgeIterator.hasNext()) {
            E e = edgeIterator.next();
            nodeIterator.next();
            if (e.equals(edge)) {
                edgeIterator.remove();
                nodeIterator.remove();
                return true;
            }
        }
        // The element was not in the list
        return false;
    }

    private void handleEmptyCase() {
        if (edgeList.size() == 0 && nodeList.size() == 1) {
            nodeList.clear();
        }
    }

    @Override
    public boolean remove(List<E> edges) {
        LinkedList<E> toRemove  = new LinkedList<>(edges);
        Iterator<E> it =
            toRemove.getFirst().equals(edgeList.getFirst()) || toRemove.getFirst().equals(edgeList.getLast()) ?
                toRemove.iterator() :
                toRemove.getLast().equals(edgeList.getFirst()) || toRemove.getLast().equals(edgeList.getLast()) ?
                    toRemove.descendingIterator() : null;
        if (it == null)
            throw new RuntimeException("removing of interior edge sequences from a path is not yet implemented");
        while (it.hasNext()) this.remove(it.next());
        return true;
    }

    @Override
    public Path<N, E> getSubPath(N start, N end) {
        Path<N, E> subPath = new LinkedListPath<>(isDirected());
        if (!this.getNodes().contains(start) || !this.getNodes().contains(end)) {
            throw new IllegalArgumentException("Range from " + start + " to " + end + " is not part of this path!");
        }
        if (start.equals(end)) {
            return subPath;
        }
        boolean insidePath = false;
        // First the directed case
        if (isDirected()) {
            for (E edge : this.getEdges()) {
                if (edge.getLeftNode().equals(start)) {
                    insidePath = true;
                }
                if (insidePath) {
                    subPath.addLast(edge);
                }
                if (insidePath && edge.getRightNode().equals(end)) {
                    return subPath;
                }
            }
        }
        else {
            // Now for the undirected case, first create the node list
            List<N> nodeList = new ArrayList<>();
            for (N node : this.getNodes()) {
                boolean isStartOrEnd = node.equals(start) || node.equals(end);
                if (!insidePath && isStartOrEnd) {
                    insidePath = true;
                    nodeList.add(node);
                    continue;
                }
                if (insidePath) {
                    nodeList.add(node);
                }
                if (insidePath && isStartOrEnd) {
                    break;
                }
            }
            if (!(nodeList.contains(start) && nodeList.contains(end))) {
                // Did not find end of path!
                throw new IllegalArgumentException("Range from " + start + " to " + end + " is not part of this path!");
            }
            insidePath = false;
            // Now lets search for the path based on the node list
            subPath = new LinkedListPath<>(isDirected());
            N startOfPath = nodeList.get(0);
            N endOfPath = nodeList.get(nodeList.size()-1);
            for (E edge : this.getEdges()) {
                // First check if we find the beginning of the undirected path, i.e., the first element of the node list
                if (!insidePath && (edge.getLeftNode().equals(startOfPath) || edge.getRightNode().equals(startOfPath))) {
                    insidePath = true;
                    // Special case: Do we search for the first edge in the path? Then add the edge directly. Otherwise
                    // the first occurence of startOfPath will be in the edge BEFORE the subpath and we want to skip it
                    if (startOfPath.equals(getNodes().get(0))) {
                        subPath.addFirst(edge);
                        // Next special case: Do we only search for one edge? Then we need to quit immediately, to
                        // avoid adding the next edge after the subpath as well
                        if (edge.getLeftNode().equals(endOfPath) || edge.getRightNode().equals(endOfPath)) {
                            return subPath;
                        }
                    }
                    continue;
                }
                // Add every edge inside the path. Afterwards check if we found the end and quit accordingly
                if (insidePath) {
                    subPath.addLast(edge);
                }
                if (insidePath && (edge.getLeftNode().equals(endOfPath) || edge.getRightNode().equals(endOfPath))) {
                    return subPath;
                }
            }
        }
        // Did not find end of path!
        throw new IllegalArgumentException("Range from " + start + " to " + end + " is not part of this path!");
    }

    @Override
    public boolean canAppendToStart(E edge) {
        if (edge == null) throw new IllegalArgumentException("null cannot be an edge");
        //We need to handle different cases, depending of the size of the current path
        //Case 1: The path could be empty, then we can always add an edge
        if(nodeList.isEmpty()){
            return true;
        }
        //Case 2: We already have added a list. Can we add the edge directly, without changing the path?
        else if (edge.getLeftNode().equals(nodeList.getFirst()) && !directed)
            return true;
        else if (edge.getRightNode().equals(nodeList.getFirst()))
            return true;
        //Case 3: The list has exactly size 2 (i.e., only one edge was added yet) and is undirected. Then adding to
        // the start could be done by reversing the path and adding to the front afterwards. We need to check for that.
        else if (nodeList.size() == 2 && !directed){
            //Check if we can add the edge to the start of the reversed path
            if (edge.getLeftNode().equals(nodeList.getLast()) && !directed)
                return true;
            else if (edge.getRightNode().equals(nodeList.getLast()))
                return true;
        }
        return false;
    }

    @Override
    public boolean canAppendToEnd(E edge) {
        if (edge == null) throw new IllegalArgumentException("null cannot be an edge");
        //We need to handle different cases, depending of the size of the current path
        //Case 1: The path could be empty, then we can always add an edge
        if(nodeList.isEmpty()){
            return true;
        }
        //Case 2: We already have added a list. Can we add the edge directly, without changing the path?
        else if (edge.getRightNode().equals(nodeList.getLast()) && !directed)
            return true;
        else if (edge.getLeftNode().equals(nodeList.getLast()))
            return true;
        //Case 3: The list has exactly size 2 (i.e., only one edge was added yet) and is undirected. Then adding to
        // the end could be done by reversing the path and adding to the end afterwards. We need to check for that.
        else if (nodeList.size() == 2 && !directed) {
            //Check if we can add the edge to the end of the reversed path
            if (edge.getRightNode().equals(nodeList.getFirst()) && !directed)
                return true;
            else if (edge.getLeftNode().equals(nodeList.getFirst()))
                return true;
        }
        return false;
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("LinkedListPath (directed ").append(directed).append("):\nNodes:\n");
        for(N node : nodeList){
            builder.append(node).append("\n");
        }
        for(E edge : edgeList){
            builder.append(edge).append("\n");
        }

        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinkedListPath<?, ?> that = (LinkedListPath<?, ?>) o;

        return edgeList != null ? edgeList.equals(that.edgeList) : that.edgeList == null;
    }
}
