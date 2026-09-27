package net.lintim.model.impl;

import net.lintim.model.Edge;
import net.lintim.model.Graph;
import net.lintim.model.Node;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An implementation of {@link net.lintim.model.Graph} based on {@link Map}. The inserted notes need to implement
 * {@link Object#hashCode()}. There cannot be multiple nodes or edges with the same value of {@link Node#getId()} or
 * {@link Edge#getId()}, respectively.
 * @param <N> the type of the nodes
 * @param <E> the type of the edges
 */
public class SimpleMapGraph<N extends Node, E extends Edge<N>> implements Graph<N, E> {

    private Map<Integer, N> nodes;
    private Map<Integer, E> edges;
    private Map<N, List<E>> incidentEdges;

    /**
     * Generate a new graph.
     */
    public SimpleMapGraph() {
        this.nodes = new HashMap<>();
        this.edges = new HashMap<>();
        this.incidentEdges = new HashMap<>();
    }

    @Override
    public N getNode(int id) {
        return nodes.get(id);
    }

    @Override
    public E getEdge(int id) {
        return edges.get(id);
    }

    @Override
    public <O> N getNode(Function<N, O> map, O value) {
        return nodes.values().stream().filter(n -> map.apply(n).equals(value)).findAny().orElse(null);
    }

    @Override
    public <O> E getEdge(Function<E, O> map, O value) {
        return edges.values().stream().filter(e -> map.apply(e).equals(value)).findAny().orElse(null);
    }

    @Override
    public boolean addEdge(E edge) {
        if (edge == null) {
            throw new IllegalArgumentException("null cannot be an edge");
        }
        N source = edge.getLeftNode();
        if (!nodes.containsKey(source.getId())) {
            throw new IllegalArgumentException("The edge's left node is not a member of the graph");
        }
        N target = edge.getRightNode();
        if (!nodes.containsKey(target.getId())) {
            throw new IllegalArgumentException("The edge's right node is not a member of the graph");
        }
        if (edges.containsKey(edge.getId())) {
            return false;
        }
        edges.put(edge.getId(), edge);
        incidentEdges.get(source).add(edge);
        incidentEdges.get(target).add(edge);
        return true;
    }

    @Override
    public boolean addNode(N node) {
        if (node == null) {
            throw new IllegalArgumentException("null cannot be a node");
        }
        if (nodes.containsKey(node.getId())) {
            return false;
        }
        nodes.put(node.getId(), node);
        incidentEdges.put(node, new ArrayList<>());
        return true;
    }

    @Override
    public boolean removeEdge(E edge) {
        if (edge == null || !edges.containsKey(edge.getId())) {
            return false;
        }
        edges.remove(edge.getId());
        incidentEdges.get(edge.getLeftNode()).remove(edge);
        incidentEdges.get(edge.getRightNode()).remove(edge);
        return true;
    }

    @Override
    public boolean removeNode(N node) {
        if (node == null || !nodes.containsKey(node.getId())) {
            return false;
        }
        List<E> edgesToRemove = new ArrayList<>(incidentEdges.get(node));
        for (E edge: edgesToRemove) {
            removeEdge(edge);
        }
        nodes.remove(node.getId());
        incidentEdges.remove(node);
        return true;
    }

    @Override
    public void orderNodes(Comparator<N> comparator) {
        List<N> orderedNodes = nodes.values().stream().sorted(comparator).collect(Collectors.toList());
        nodes.clear();
        // Renumbering is a little bit complicated. Changing the id will change the nodes map (accessed by id) and
        // the incident edges (since the id is part of the hashcode of a node). We therefore need to do some work
        // to stay consistent
        int newNodeId = 1;
        HashMap<N, List<E>> newIncidentEdges = new HashMap<>();
        for (N node: orderedNodes) {
            List<E> localIncidentEdges = incidentEdges.get(node);
            node.setId(newNodeId);
            newNodeId += 1;
            nodes.put(node.getId(), node);
            newIncidentEdges.put(node, localIncidentEdges);
        }
        incidentEdges = newIncidentEdges;
    }

    @Override
    public void orderEdges(Comparator<E> comparator) {
        List<E> orderedEdges = edges.values().stream().sorted(comparator).collect(Collectors.toList());
        edges.clear();
        int newEdgeId = 1;
        for (E edge: orderedEdges) {
            edge.setId(newEdgeId);
            newEdgeId += 1;
            edges.put(edge.getId(), edge);
        }
    }

    @Override
    public Collection<E> getOutgoingEdges(N node) {
        if (!nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("The node is not a member of the graph");
        }
        return incidentEdges.get(node).stream().filter(e -> !e.isDirected() || e.getLeftNode().equals(node))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<E> getIncomingEdges(N node) {
        if (!nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("The node is not a member of the graph");
        }
        return incidentEdges.get(node).stream().filter(e -> !e.isDirected() || e.getRightNode().equals(node))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<E> getIncidentEdges(N node) {
        if (!nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("The node is not a member of the graph");
        }
        return new ArrayList<>(incidentEdges.get(node));
    }

    @Override
    public ArrayList<N> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    @Override
    public ArrayList<E> getEdges() {
        return new ArrayList<>(edges.values());
    }

    @Override
    public boolean isDirected() {
        return edges.values().stream().anyMatch(Edge::isDirected);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Graph:\nNodes:\n");
        for(N node : nodes.values()){
            builder.append(node).append("\n");
        }
        builder.append("Edges:\n");
        for(E edge : edges.values()){
            builder.append(edge).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Graph<?, ?>)) return false;

        Graph<?, ?> that = (Graph<?, ?>) o;
        if (!Arrays.equals(that.getClass().getTypeParameters(), this.getClass().getTypeParameters())) return false;

        ArrayList<N> theseNodes = this.getNodes();
        theseNodes.sort(Comparator.comparingInt(N::getId).thenComparingInt(N::hashCode));
        ArrayList<E> theseEdges = this.getEdges();
        theseEdges.sort(Comparator.comparingInt(E::getId).thenComparingInt(E::hashCode));

        ArrayList<N> thoseNodes = new ArrayList<>((Collection<N>) that.getNodes());
        thoseNodes.sort(Comparator.comparingInt(N::getId).thenComparingInt(N::hashCode));
        ArrayList<E> thoseEdges = new ArrayList<>((Collection<E>) that.getEdges());
        thoseEdges.sort(Comparator.comparingInt(E::getId).thenComparingInt(E::hashCode));

        return theseNodes.equals(thoseNodes) && theseEdges.equals(thoseEdges);
    }

    @Override
    public int hashCode() {
        ArrayList<N> nodes = getNodes();
        nodes.sort(Comparator.comparingInt(N::getId).thenComparingInt(N::hashCode));
        ArrayList<E> edges = getEdges();
        edges.sort(Comparator.comparingInt(E::getId).thenComparingInt(E::hashCode));
        int result = nodes.hashCode();
        result = 31 * result + edges.hashCode();
        return result;
    }
}
