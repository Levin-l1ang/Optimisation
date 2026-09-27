package net.lintim.graph;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Computes undirected shortest paths on a directed graph, needed to apply the
 * fundamental improvement from the article
 * "Christian Liebchen, Finding Short Integral Cycle Bases for Cyclic
 * timetabling, 2003."
 *
 * @param <V> Vertex type.
 * @param <E> Edge type.
 */
public class ShortestPathsGraphUndirected<V, E> {

    LinkedHashMap<V, NodeStruct> graph = new LinkedHashMap<V, NodeStruct>();
    LinkedHashMap<E, EdgeStruct> edges = new LinkedHashMap<E, EdgeStruct>();

    private class EdgeStruct{
        E edge;
        NodeStruct left;
        NodeStruct right;
        Double weight = Double.POSITIVE_INFINITY;
    }

    private class NodeStruct{
        EdgeStruct predecessor;
        Double distance;
        LinkedHashSet<NodeStruct> treeMapQueueBucket;
        LinkedHashSet<EdgeStruct> attachedEdges = new LinkedHashSet<EdgeStruct>();
    }

    Boolean negativeEdgeExists = false;
    Boolean computed = false;

    /**
     * The left vertex in the shortest path calculation.
     */
    V leftVertex = null;
    /**
     * The right vertex in the shortest path calculation.
     */
    V rightVertex = null;

    /**
     * Computes the shortest path from left vertex to right vertex.
     *
     * @param leftVertex The left vertex.
     * @param rightVertex The right vertex.
     */
    public void compute(V leftVertex, V rightVertex) {

        this.leftVertex = leftVertex;
        this.rightVertex = rightVertex;

        if(negativeEdgeExists){
            throw new RuntimeException("graph has negative edge weights");
        }

        TreeMap<Double, LinkedHashSet<NodeStruct>> treeMapQueue =
            new TreeMap<Double, LinkedHashSet<NodeStruct>>();

        LinkedHashSet<NodeStruct> initialBucket = new LinkedHashSet<NodeStruct>();
        NodeStruct initialStruct = graph.get(leftVertex);
        initialBucket.add(initialStruct);
        treeMapQueue.put(0.0, initialBucket);

        for(Entry<V, NodeStruct> e1 : graph.entrySet()){
            V node = e1.getKey();
            NodeStruct nodeStruct = e1.getValue();
            nodeStruct.predecessor = null;

            if(leftVertex.equals(node)){
                initialStruct.treeMapQueueBucket = initialBucket;
                initialStruct.distance = 0.0;
            }
            else{
                nodeStruct.treeMapQueueBucket = null;
                nodeStruct.distance = Double.POSITIVE_INFINITY;
            }
        }

        while(!treeMapQueue.isEmpty()){

            Iterator<Entry<Double, LinkedHashSet<NodeStruct>>> queueItr =
                treeMapQueue.entrySet().iterator();

            Entry<Double, LinkedHashSet<NodeStruct>> queueEntry =
                queueItr.next();

            LinkedHashSet<NodeStruct> nodesWithSameDistance =
                queueEntry.getValue();

            if(nodesWithSameDistance.contains(rightVertex)){
                break;
            }

            Iterator<NodeStruct> itr = queueEntry.getValue().iterator();
            NodeStruct minimumStruct = itr.next();
            Double distance = queueEntry.getKey();
            itr.remove();

            if(nodesWithSameDistance.isEmpty()){
                queueItr.remove();
            }

            for(EdgeStruct e : minimumStruct.attachedEdges){

                double newDistance = distance + e.weight;
                NodeStruct targetStruct = e.left == minimumStruct ?
                        e.right : e.left;
                double oldDistance = targetStruct.distance;

                if(newDistance < oldDistance){
                    LinkedHashSet<NodeStruct> oldNodesWithSameDistance =
                        targetStruct.treeMapQueueBucket;

                    if(oldNodesWithSameDistance != null){
                        oldNodesWithSameDistance.remove(targetStruct);

                        if(oldNodesWithSameDistance.isEmpty()){
                            treeMapQueue.remove(oldDistance);
                        }
                    }

                    LinkedHashSet<NodeStruct> entries =
                        treeMapQueue.get(newDistance);

                    if(entries == null){
                        entries = new LinkedHashSet<NodeStruct>();
                        treeMapQueue.put(newDistance, entries);
                    }

                    targetStruct.treeMapQueueBucket = entries;
                    targetStruct.distance = newDistance;
                    targetStruct.predecessor = e;
                    entries.add(targetStruct);
                }
            }
        }

        computed = true;
    }

    /**
     * Adds a vertex to the graph.
     *
     * @param node The node/vertex to add.
     */
    public void addVertex(V node){
        NodeStruct nodeStruct = new NodeStruct();
        graph.put(node, nodeStruct);

        computed = false;
    }

    /**
     * Adds an edge to the graph.
     *
     * @param edge The edge to add.
     * @param leftNode The edges left node.
     * @param rightNode The edges right node.
     * @param weight The edges shortest path weight.
     */
    public void addEdge(E edge, V leftNode, V rightNode, Double weight){
        NodeStruct leftNodeStruct = graph.get(leftNode);
        NodeStruct rightNodeStruct = graph.get(rightNode);

        if(edges.containsKey(edge)){
            throw new RuntimeException("edge \"" + edge + "\" inserted twice");
        }

        EdgeStruct edgeStruct = new EdgeStruct();
        edgeStruct.edge = edge;
        edgeStruct.weight = weight;
        edgeStruct.left = leftNodeStruct;
        edgeStruct.right = rightNodeStruct;

        edges.put(edge, edgeStruct);

        leftNodeStruct.attachedEdges.add(edgeStruct);
        rightNodeStruct.attachedEdges.add(edgeStruct);

        computed = false;
    }

    public LinkedList<E> trackPath(){

        LinkedList<E> path = new LinkedList<E>();

        if(computed == false){
            throw new UnsupportedOperationException("need to compute shortest " +
                    "paths first before running trackPath");
        }

        NodeStruct nodeStruct = graph.get(rightVertex);
        EdgeStruct predecessorStruct = nodeStruct.predecessor;

        while(predecessorStruct != null){

                path.addFirst(predecessorStruct.edge);
                nodeStruct = predecessorStruct.left == nodeStruct ?
                        predecessorStruct.right : predecessorStruct.left;
                predecessorStruct = nodeStruct.predecessor;
        }

        return path;
    }

    public double getDistance(){
        return graph.get(rightVertex).distance;
    }

    public LinkedHashMap<V, E> getPredecessorMap(){
        LinkedHashMap<V, E> retval = new LinkedHashMap<V,E>();
        for(Entry<V, NodeStruct> e1 : graph.entrySet()){
            E predecessor = null;
            EdgeStruct edgeStruct = e1.getValue().predecessor;
            if(edgeStruct != null){
                predecessor = edgeStruct.edge;
            }
            retval.put(e1.getKey(), predecessor);
        }
        return retval;
    }

}
