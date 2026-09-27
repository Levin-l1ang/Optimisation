import org.jheaps.AddressableHeap;
import org.jheaps.tree.FibonacciHeap;

import java.util.*;
import java.util.Map.Entry;

/**
 * Efficient shortest path methods may reduce the calculation time for the all
 * pairs shortest paths problem from hours to seconds. This class provides both.
 * For a usage example, see @{link {@link ShortestPathsTest}.
 *
 * Handles directed graphs only. Especially, this is not a container class, i.e.
 * the graph has to be stored somewhere else and an instance of this class then
 * contains a copy. To not let it become a container class at all, there are no
 * getters for vertices/edges. Can handle graphs with multiple edges.
 *
 * @param <V> Class used for nodes.
 * @param <E> Class used for edges.
 */

public class ShortestPathsGraph<V, E> {

    public enum ShortestPathsMethod {
        BELLMAN_FORD,
        FIBONACCI_HEAP,
        TREE_MAP_QUEUE
    }

    LinkedHashMap<V, NodeStruct> graph = new LinkedHashMap<>();
    BiLinkedHashMap<NodeStruct, NodeStruct, EdgeStruct> nodeEdgeMap =
        new BiLinkedHashMap<>();

    Random random = null;

    private class EdgeStruct{
        E edge;
        NodeStruct source;
        NodeStruct target;
        Double weight = Double.POSITIVE_INFINITY;
    }

    private class NodeStruct implements Comparable<NodeStruct>{
        EdgeStruct predecessor;
        Double distance;
        TreeSet<NodeStruct> treeMapQueueBucket;
        AddressableHeap.Handle<Double, NodeStruct> fibonacciHeapNode;
        LinkedHashSet<EdgeStruct> outgoingEdges = new LinkedHashSet<>();
        LinkedHashSet<EdgeStruct> incomingEdges = new LinkedHashSet<>();

        @Override
        public int compareTo(NodeStruct o) {
            if(this == o){
                return 0;
            }
            return random == null ? 1 : random.nextBoolean() ? -1 : 1;
        }
    }

    ShortestPathsMethod method = ShortestPathsMethod.FIBONACCI_HEAP;
    Boolean negativeEdgeExists = false;

    /** Adds a vertex object to the shortest paths graph.
     *
     * @param vertex The vertex to add.
     */
    public void addVertex(V vertex){
        NodeStruct nodeStruct = new NodeStruct();
        graph.put(vertex, nodeStruct);
    }

    /** Adds an edge object to the shortest paths graph.
     *
     * @param edge The edge object to add.
     * @param sourceNode The edges source.
     * @param targetNode The edges target.
     * @param weight The edges weight for the shortest paths.
     */
    public void addEdge(E edge, V sourceNode, V targetNode, Double weight){
        NodeStruct sourceNodeStruct = graph.get(sourceNode);
        NodeStruct targetNodeStruct = graph.get(targetNode);

        EdgeStruct edgeStruct = nodeEdgeMap.get(sourceNodeStruct, targetNodeStruct);

        if(edgeStruct == null){
            edgeStruct = new EdgeStruct();
            edgeStruct.source = sourceNodeStruct;
            edgeStruct.target = targetNodeStruct;
            nodeEdgeMap.put(sourceNodeStruct, targetNodeStruct, edgeStruct);
        }

        if(weight < edgeStruct.weight){
            edgeStruct.edge = edge;
            edgeStruct.weight = weight;
        }

        sourceNodeStruct.outgoingEdges.add(edgeStruct);
        targetNodeStruct.incomingEdges.add(edgeStruct);

        if(edgeStruct.weight < 0){
            negativeEdgeExists = true;
        }
    }

    private void computeInternal(V node) throws GraphMalformedException{
        switch(method){
        case BELLMAN_FORD:
            computeBellmanFord(node);
            break;
        case FIBONACCI_HEAP:
            computeFibonacciHeap(node);
            break;
        case TREE_MAP_QUEUE:
            computeTreeMapQueue(node);
            break;
        }
    }

    /** Computes a shortest paths tree.
     *
     * @param sourceVertex Start vertex of the tree.
     * @throws GraphMalformedException
     */
    public void compute(V sourceVertex) throws GraphMalformedException{
        computeInternal(sourceVertex);
    }

    private void computeTreeMapQueue(V sourceVertex) throws GraphMalformedException{

        if(negativeEdgeExists){
            throw new GraphMalformedException("graph has negative edge weights");
        }

        TreeMap<Double, TreeSet<NodeStruct>> treeMapQueue =
            new TreeMap<>();

        TreeSet<NodeStruct> initialBucket = new TreeSet<>();
        NodeStruct initialStruct = graph.get(sourceVertex);
        initialBucket.add(initialStruct);
        treeMapQueue.put(0.0, initialBucket);

        for(Entry<V, NodeStruct> e1 : graph.entrySet()){
            V node = e1.getKey();
            NodeStruct nodeStruct = e1.getValue();
            nodeStruct.predecessor = null;

            if(sourceVertex.equals(node)){
                initialStruct.treeMapQueueBucket = initialBucket;
                initialStruct.distance = 0.0;
            }
            else {
                nodeStruct.treeMapQueueBucket = null;
                nodeStruct.distance = Double.POSITIVE_INFINITY;
            }

        }

        while(!treeMapQueue.isEmpty()){

            Iterator<Entry<Double, TreeSet<NodeStruct>>> calendarItr =
                treeMapQueue.entrySet().iterator();

            Entry<Double, TreeSet<NodeStruct>> calendarEntry =
                calendarItr.next();

            TreeSet<NodeStruct> nodesWithSameDistance =
                calendarEntry.getValue();

            Iterator<NodeStruct> itr = calendarEntry.getValue().iterator();
            NodeStruct minimumStruct = itr.next();
            Double distance = calendarEntry.getKey();
            itr.remove();

            if(nodesWithSameDistance.isEmpty()){
                calendarItr.remove();
            }

            for(EdgeStruct e : minimumStruct.outgoingEdges){
                NodeStruct targetStruct = e.target;

                double newDistance = distance + e.weight;
                double oldDistance = targetStruct.distance;

                if(newDistance < oldDistance){
                    Set<NodeStruct> oldNodesWithSameDistance =
                        targetStruct.treeMapQueueBucket;

                    if(oldNodesWithSameDistance != null){
                        oldNodesWithSameDistance.remove(targetStruct);

                        if(oldNodesWithSameDistance.isEmpty()){
                            treeMapQueue.remove(oldDistance);
                        }
                    }

                    TreeSet<NodeStruct> entries =
                        treeMapQueue.computeIfAbsent(newDistance, k -> new TreeSet<>());

                    targetStruct.treeMapQueueBucket = entries;
                    targetStruct.distance = newDistance;
                    targetStruct.predecessor = e;
                    entries.add(targetStruct);
                }
            }
        }

    }

    private void computeFibonacciHeap(V sourceVertex) throws GraphMalformedException{

        if(random != null){
            throw new RuntimeException("randomized shortest paths " +
                    "unsupported for fibonacci heaps");
        }

        if(negativeEdgeExists){
            throw new GraphMalformedException("graph has negative edge weights");
        }

        FibonacciHeap<Double, NodeStruct> heap = new FibonacciHeap<>();

        for(Entry<V, NodeStruct> e1 : graph.entrySet()){
            V node = e1.getKey();
            NodeStruct nodeStruct = e1.getValue();
            nodeStruct.predecessor = null;

            AddressableHeap.Handle<Double, NodeStruct> handle;

            if(sourceVertex.equals(node)){
                nodeStruct.distance = 0.0;
                handle = heap.insert(0.0, nodeStruct);
            }
            else{
                nodeStruct.distance = Double.POSITIVE_INFINITY;
                handle = heap.insert(Double.POSITIVE_INFINITY, nodeStruct);
            }

            nodeStruct.fibonacciHeapNode = handle;

        }

        while(!heap.isEmpty()){

            AddressableHeap.Handle<Double, NodeStruct> minimalHeapNode = heap.deleteMin();
            Double minimum = minimalHeapNode.getKey();
            NodeStruct minimumStruct = minimalHeapNode.getValue();

            for(EdgeStruct e : minimumStruct.outgoingEdges){

                Double newDistance = minimum + e.weight;
                NodeStruct targetNodeStruct = e.target;

                if(newDistance < targetNodeStruct.distance){
                    targetNodeStruct.distance = newDistance;
                    targetNodeStruct.predecessor = e;
                    targetNodeStruct.fibonacciHeapNode.decreaseKey(newDistance);
                }
            }
        }
    }

    private void computeBellmanFord(V sourceVertex) throws GraphMalformedException{
        if(random != null){
            throw new RuntimeException("randomized shortest paths " +
                    "unsupported for bellman ford");
        }

        int maxPathLength = graph.size();

        for(Entry<V, NodeStruct> e1 : graph.entrySet()){
            V node = e1.getKey();
            NodeStruct nodeStruct = e1.getValue();
            nodeStruct.predecessor = null;

            if(sourceVertex.equals(node)){
                nodeStruct.distance = 0.0;
            }
            else{
                nodeStruct.distance = Double.POSITIVE_INFINITY;
            }
        }

        for(int i=0; i<maxPathLength; ++i){
            for(Entry<V, NodeStruct> e1 : graph.entrySet()){
                NodeStruct nodeStruct = e1.getValue();
                for(EdgeStruct edgeStruct : nodeStruct.outgoingEdges){

                    NodeStruct targetNode = edgeStruct.target;
                    double oldDistance = targetNode.distance;
                    double newDistance = nodeStruct.distance + edgeStruct.weight;
                    if(newDistance < oldDistance){
                        targetNode.distance = newDistance;
                        targetNode.predecessor = edgeStruct;
                    }
                }
            }
        }

        for(Entry<V, NodeStruct> e1 : graph.entrySet()){
            NodeStruct nodeStruct = e1.getValue();
            for(EdgeStruct edgeStruct : nodeStruct.outgoingEdges){

                NodeStruct targetNode = edgeStruct.target;
                double oldDistance = targetNode.distance;
                double newDistance = nodeStruct.distance + edgeStruct.weight;
                if(newDistance < oldDistance){
                    throw new GraphMalformedException(
                            "graph contains negative cycle");
                }
            }
        }

    }

    /** Sets the shortest paths method.
     *
     * @param method The method to set.
     */
    public void setMethod(ShortestPathsMethod method){
        this.method = method;
    }

    /** Returns a shortest path in the shortest path tree computed by
     * {@link #compute(Object)}, which has to be run first.
     *
     * @param targetVertex The target vertex.
     * @return The path from th tree root to target vertex as a sequence of
     * edges.
     */
    public LinkedList<E> trackPath(V targetVertex){

        LinkedList<E> path = new LinkedList<>();

        EdgeStruct predecessorStruct = graph.get(targetVertex).predecessor;

        while(predecessorStruct != null){
            path.addFirst(predecessorStruct.edge);
            predecessorStruct = predecessorStruct.source.predecessor;
        }

        return path;
    }

    /** Returns the length of a shortest path in the shortest path tree computed
     * by {@link #compute(Object)}, which has to be run first.
     *
     * @param targetVertex The target vertex.
     * @return The distance from the tree root to the target vertex.
     */
    public double getDistance(V targetVertex){
        return graph.get(targetVertex).distance;
    }

    public Random getRandom() {
        return random;
    }

    public void setRandom(Random random) {
        this.random = random;
    }

}
