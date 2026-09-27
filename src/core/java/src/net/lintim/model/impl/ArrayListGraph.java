package net.lintim.model.impl;

import net.lintim.model.Edge;
import net.lintim.model.Graph;
import net.lintim.model.Node;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Graph implementation using java.util.ArrayList and java.util.HashMap for forward/backward mapping of nodes and edges,
 * respectively, and one java.util.LinkedList per node for incident edges (mapped, again, by a java.util.HashMap)
 */
public class ArrayListGraph<N extends Node, E extends Edge<N>> implements Graph<N, E> {

    private List<N> nodes = new ArrayList<>();
    private List<E> edges = new ArrayList<>();
    private Map<N, Integer> nodeIndices = new HashMap<>();
    private Map<E, Integer> edgeIndices = new HashMap<>();

    private Map<N, List<E>> incidentEdges = new HashMap<>();

    @Override
    public N getNode(int id) {
        return getNode(N::getId, id);
    }

    @Override
    public E getEdge(int id) {
        return getEdge(E::getId, id);
    }

    public <O> N getNode(Function<N, O> map, O value) {
        return nodes.stream().filter(n -> n != null && map.apply(n).equals(value)).findAny().orElse(null);
    }

    public <O> E getEdge(Function<E, O> map, O value) {
        return edges.stream().filter(e -> e != null && map.apply(e).equals(value)).findAny().orElse(null);
    }

    @Override
    public boolean addEdge(E edge) throws IllegalArgumentException {
        if (edge == null) throw new IllegalArgumentException("null cannot be an edge");
        N n1 = edge.getLeftNode();
        if (!nodeIndices.containsKey(n1))
            throw new IllegalArgumentException("The edge's left node is not a member of the graph");
        N n2 = edge.getRightNode();
        if (!nodeIndices.containsKey(n2))
            throw new IllegalArgumentException("The edge's right node is not a member of the graph");
        if (!addElement(edges, edgeIndices, edge)) return false;
        incidentEdges.get(n1).add(edge);
        incidentEdges.get(n2).add(edge);
        return true;
    }

    private <T> boolean addElement(List<T> list, Map<T, Integer> map, T element) {
        if (map.containsKey(element)) return false;
        int newIndex = list.size();
        if (list.add(element)) {
            map.put(element, newIndex);
            return true;
        }
        // I don't know why we would ever end up here, but anyway
        return false;
    }

    @Override
    public boolean addNode(N node) {
        if (node == null) throw new IllegalArgumentException("null cannot be a node");
        if (!addElement(nodes, nodeIndices, node)) return false;
        incidentEdges.put(node, new LinkedList<>());
        return true;
    }

    @Override
    public boolean removeEdge(E edge) {
        if (!removeElement(edges, edgeIndices, edge)) return false;
        incidentEdges.get(edge.getLeftNode()).remove(edge);
        incidentEdges.get(edge.getRightNode()).remove(edge);
        return true;
    }

    private <T> boolean removeElement(List<T> list, Map<T, Integer> map, T element) {
        Integer index = map.get(element);
        if (index == null) return false;
        list.set(index, null); // we don't want to List::remove because this would entail re-indexing!
        map.remove(element);
        return true;
    }

    @Override
    public boolean removeNode(N node) {
        List<E> edges = new ArrayList<>(incidentEdges.get(node));
        for (E edge : edges){
            removeEdge(edge);
        }
        if (!removeElement(nodes, nodeIndices, node)) {
            return false;
        }
        incidentEdges.remove(node);
        return true;
    }

    @Override
    public void orderNodes(Comparator<N> comparator) {
        orderElements(nodes, nodeIndices, comparator);
    }

    @Override
    public void orderEdges(Comparator<E> comparator) {
        orderElements(edges, edgeIndices, comparator);
    }

    private <T> void orderElements(List<T> list, Map<T, Integer> map, Comparator<T> comparator) {
        map.clear();
        list.removeIf(Objects::isNull);
        if (comparator != null) list.sort(comparator);
        int i = 0;
        for (T element : list) map.put(element, i++);
    }

    @Override
    public List<E> getOutgoingEdges(N node) {
        if (!nodeIndices.containsKey(node))
            throw new IllegalArgumentException("The node is not a member of the graph");
        return incidentEdges.get(node).stream().filter(e -> !e.isDirected() || e.getLeftNode().equals(node))
                .collect(Collectors.toList());
    }

    @Override
    public List<E> getIncomingEdges(N node) {
        if (!nodeIndices.containsKey(node))
            throw new IllegalArgumentException("The node is not a member of the graph");
        return incidentEdges.get(node).stream().filter(e -> !e.isDirected() || e.getRightNode().equals(node))
                .collect(Collectors.toList());
    }

    @Override
    public ArrayList<E> getIncidentEdges(N node) {
        if (!nodeIndices.containsKey(node))
            throw new IllegalArgumentException("The node is not a member of the graph");
        return new ArrayList<>(incidentEdges.get(node));
    }

    @Override
    public ArrayList<N> getNodes() {
        return nodes.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public ArrayList<E> getEdges() {
        return edges.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public boolean isDirected() {
        return edges.stream().anyMatch(Edge::isDirected);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Graph:\nNodes:\n");
        for(N node : nodes){
            builder.append(node).append("\n");
        }
        builder.append("Edges:\n");
        for(E edge : edges){
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
