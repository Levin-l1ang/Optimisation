package net.lintim.graph;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Computes a minimal spanning forest. Contains a copy of the graph to calculate
 * the minimal spanning tree on, but is not intended to be used as a container,
 * therefore there are no vertex/edge getters and there should not be in future.
 *
 * @param <V> Vertex type.
 * @param <E> Edge type.
 */
public class MinimumSpanningForestGraphUndirected<V, E> {

    LinkedHashMap<V, NodeStruct> graph = new LinkedHashMap<V, NodeStruct>();
    LinkedHashMap<E, EdgeStruct> edges = new LinkedHashMap<E, EdgeStruct>();
    LinkedHashSet<E> forestEdges = null;
    LinkedHashSet<E> coForestEdges = null;
    LinkedHashSet<V> treeStartNodes = null;

    private class EdgeStruct{
        E edge;
        NodeStruct left;
        NodeStruct right;
        Double weight = Double.POSITIVE_INFINITY;
    }

    private class NodeStruct{
        V node;
        Boolean seen;
        Boolean inQueue;
        EdgeStruct predecessor;
        LinkedHashSet<NodeStruct> treeMapQueueBucket;
        LinkedHashSet<EdgeStruct> attachedEdges = new LinkedHashSet<EdgeStruct>();
    }

    Boolean computed = false;

    /**
     * Adds a vertex/node to the graph.
     *
     * @param node The node to add.
     */
    public void addVertex(V node){
        NodeStruct nodeStruct = new NodeStruct();
        nodeStruct.node = node;
        graph.put(node, nodeStruct);

        computed = false;
    }

    /**
     * Adds an edge to the graph.
     *
     * @param edge The edge to add.
     * @param leftNode The edges left node.
     * @param rightNode The edges right node.
     * @param weight The edges shortest paths weight.
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

    /**
     * Computes the minimum spanning forest; accessible via
     * {@link #getForestEdges()} resp. {@link #getCoForestEdges()}.
     */
    public void computeMinimumSpanningForest() {

        forestEdges = new LinkedHashSet<E>();
        coForestEdges = new LinkedHashSet<E>(edges.keySet());
        treeStartNodes = new LinkedHashSet<V>();

        LinkedHashSet<NodeStruct> unseenNodes =
            new LinkedHashSet<NodeStruct>(graph.values());

        for(NodeStruct nodeStruct : unseenNodes){
            nodeStruct.predecessor = null;
            nodeStruct.treeMapQueueBucket = null;
            nodeStruct.seen = false;
        }

        while(!unseenNodes.isEmpty()){

            Iterator<NodeStruct> unseenItr = unseenNodes.iterator();
            NodeStruct initialStruct = unseenItr.next();
            treeStartNodes.add(initialStruct.node);
            unseenItr.remove();

            TreeMap<Double, LinkedHashSet<NodeStruct>> treeMapQueue =
                new TreeMap<Double, LinkedHashSet<NodeStruct>>();

            LinkedHashSet<NodeStruct> initialBucket =
                new LinkedHashSet<NodeStruct>();
            initialBucket.add(initialStruct);
            initialStruct.treeMapQueueBucket = initialBucket;
            treeMapQueue.put(0.0, initialBucket);

            while(!treeMapQueue.isEmpty()){

                Iterator<Entry<Double, LinkedHashSet<NodeStruct>>> queueItr =
                    treeMapQueue.entrySet().iterator();

                Entry<Double, LinkedHashSet<NodeStruct>> queueEntry =
                    queueItr.next();

                LinkedHashSet<NodeStruct> nodesWithSameDistance =
                    queueEntry.getValue();

                Iterator<NodeStruct> itr = queueEntry.getValue().iterator();
                NodeStruct minimumStruct = itr.next();
                itr.remove();

                minimumStruct.seen = true;

                if(minimumStruct.predecessor != null){
                    forestEdges.add(minimumStruct.predecessor.edge);
                }

                if(nodesWithSameDistance.isEmpty()){
                    queueItr.remove();
                }

                for(EdgeStruct e : minimumStruct.attachedEdges){

                    double newDistance = e.weight;
                    NodeStruct targetStruct =
                        e.left == minimumStruct ? e.right: e.left;
                    double oldDistance =
                        targetStruct.predecessor == null ?
                                Double.POSITIVE_INFINITY :
                                    targetStruct.predecessor.weight;

                    if(!targetStruct.seen && newDistance < oldDistance){
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
                        targetStruct.predecessor = e;
                        entries.add(targetStruct);
                    }
                }
            }

            while(unseenItr.hasNext()){
                NodeStruct nodeStruct = unseenItr.next();
                if(nodeStruct.seen){
                    unseenItr.remove();
                }
            }

        }

        coForestEdges.removeAll(forestEdges);

        computed = true;
    }

    /**
     * Computes an arbitrary, not necessary minimal spanning forest; accessible
     * via {@link #getForestEdges()} resp. {@link #getCoForestEdges()}.
     */
    public void computeSpanningForest() {

        forestEdges = new LinkedHashSet<E>();
        coForestEdges = new LinkedHashSet<E>(edges.keySet());
        treeStartNodes = new LinkedHashSet<V>();

        LinkedHashSet<NodeStruct> unseenNodes =
            new LinkedHashSet<NodeStruct>(graph.values());

        for(NodeStruct nodeStruct : unseenNodes){
            nodeStruct.predecessor = null;
            nodeStruct.inQueue = false;
            nodeStruct.seen = false;
        }

        while(!unseenNodes.isEmpty()){

            Iterator<NodeStruct> unseenItr = unseenNodes.iterator();
            NodeStruct initialStruct = unseenItr.next();
            treeStartNodes.add(initialStruct.node);
            unseenItr.remove();

            LinkedHashSet<NodeStruct> queue = new LinkedHashSet<NodeStruct>();

            initialStruct.inQueue = true;
            queue.add(initialStruct);

            while(!queue.isEmpty()){

                Iterator<NodeStruct> itr = queue.iterator();
                NodeStruct minimumStruct = itr.next();
                itr.remove();

                minimumStruct.seen = true;

                if(minimumStruct.predecessor != null){
                    forestEdges.add(minimumStruct.predecessor.edge);
                }

                for(EdgeStruct e : minimumStruct.attachedEdges){

                    NodeStruct targetStruct =
                        e.left == minimumStruct ? e.right: e.left;

                    if(!targetStruct.seen && !targetStruct.inQueue){
                        targetStruct.predecessor = e;
                        targetStruct.inQueue = true;
                        queue.add(targetStruct);
                    }
                }
            }

            while(unseenItr.hasNext()){
                NodeStruct nodeStruct = unseenItr.next();
                if(nodeStruct.seen){
                    unseenItr.remove();
                }
            }

        }

        coForestEdges.removeAll(forestEdges);

        computed = true;
    }

    // =========================================================================
    // === GETTERS =============================================================
    // =========================================================================
    public LinkedHashSet<E> getForestEdges(){
        if(!computed){
            throw new RuntimeException("need to compute minimal spanning " +
                    "forest first");
        }
        return forestEdges;
    }

    public LinkedHashSet<E> getCoForestEdges(){
        if(!computed){
            throw new RuntimeException("need to compute minimal spanning " +
                    "forest first");
        }
        return coForestEdges;
    }

    public LinkedHashSet<V> getTreeStartNodes(){
        if(!computed){
            throw new RuntimeException("need to compute minimal spanning " +
                    "forest first");
        }
        return treeStartNodes;
    }

}
