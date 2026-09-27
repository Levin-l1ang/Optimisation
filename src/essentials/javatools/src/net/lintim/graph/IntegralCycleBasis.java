package net.lintim.graph;

import net.lintim.util.IterationProgressCounter;
import net.lintim.util.NullIterationProgressCounter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;

/**
 * Computes an integral cycle base, optinally with the fundamental improvement
 * from the article "Christian Liebchen,
 * Finding Short Integral Cycle Bases for Cyclic timetabling, 2003."; contains
 * a copy of the graph to calculate the cyclebase on, but is not intended to
 * be used as a container, therefore there are no vertex/edge getters and there
 * should not be in future.
 *
 * @param <V> Vertex type.
 * @param <E> Edge type.
 */
public class IntegralCycleBasis<V, E> {

    class NodeStruct {
        V node;

        public NodeStruct(V node) {
            this.node = node;
        }

    }

    class EdgeStruct {
        E edge;
        NodeStruct from;
        NodeStruct to;
        Double weight;

        public EdgeStruct(E edge, NodeStruct from, NodeStruct to, Double weight) {
            this.edge = edge;
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

    }

    Boolean applyFundamentalImprovement = true;
    Boolean useMinimalSpanningForest = false;

    LinkedHashSet<LinkedHashMap<E, Boolean>> cycles =
        new LinkedHashSet<LinkedHashMap<E, Boolean>>();

    LinkedHashMap<V, NodeStruct> nodes = new LinkedHashMap<V, NodeStruct>();
    LinkedHashMap<E, EdgeStruct> edges = new LinkedHashMap<E, EdgeStruct>();

    IterationProgressCounter iterationProgressCounter =
        new NullIterationProgressCounter();

    Boolean computed = false;

    /**
     * Adds a vertex to the graph.
     *
     * @param node The node/vertex to add.
     */
    public void addVertex(V node){
        if(nodes.containsKey(node)){
            throw new RuntimeException("node \"" + node + "\" alread added");
        }

        NodeStruct nodeStruct = new NodeStruct(node);
        nodes.put(node, nodeStruct);

        computed = false;
    }

    /**
     * Adds an edge to the graph.
     *
     * @param edge The edge to add.
     * @param fromNode The edges from node.
     * @param toNode The edges to node.
     * @param weight The edges shortest paths weight.
     */
    public void addEdge(E edge, V fromNode, V toNode, Double weight){
        if(edges.containsKey(edge)){
            throw new RuntimeException("edge \"" + edge + "\" alread added");
        }

        EdgeStruct edgeStruct = new EdgeStruct(edge, nodes.get(fromNode),
                nodes.get(toNode), weight);
        edges.put(edge, edgeStruct);

        computed = false;
    }

    /**
     * Computes the the integral cycle base, which is accessible via
     * {@link #getCycles()} later. Applies fundamental improvement if
     * set by {@link #setApplyFundamentalImprovement(Boolean)}.
     */
    public void compute() {
        MinimumSpanningForestGraphUndirected<NodeStruct, EdgeStruct> msf =
            new MinimumSpanningForestGraphUndirected<NodeStruct, EdgeStruct>();

        for(Entry<V, NodeStruct> e1 : nodes.entrySet()){
            NodeStruct nodeStruct = e1.getValue();
            msf.addVertex(nodeStruct);
        }

        for(Entry<E, EdgeStruct> e1 : edges.entrySet()){
            EdgeStruct edgeStruct = e1.getValue();
            msf.addEdge(edgeStruct, edgeStruct.from, edgeStruct.to,
                    edgeStruct.weight);
        }

        msf.computeMinimumSpanningForest();

        if(applyFundamentalImprovement){
            applyFundamentalImproval(msf);
        }

        computed = true;

    }

    private void applyFundamentalImproval(
            MinimumSpanningForestGraphUndirected<NodeStruct, EdgeStruct> msf){

        ShortestPathsGraphUndirected<NodeStruct, EdgeStruct> sp =
            new ShortestPathsGraphUndirected<NodeStruct, EdgeStruct>();

        for(Entry<V, NodeStruct> e1 : nodes.entrySet()){
            NodeStruct nodeStruct = e1.getValue();
            sp.addVertex(nodeStruct);
        }

        for(EdgeStruct e1 : msf.getForestEdges()){
            sp.addEdge(e1, e1.from, e1.to, e1.weight);
        }

        LinkedHashSet<EdgeStruct> coForestEdges = msf.getCoForestEdges();

        Integer counter = 1;
        iterationProgressCounter.setTotalNumberOfIterations(coForestEdges.size());

        for(EdgeStruct edgeStruct : coForestEdges){
            iterationProgressCounter.reportIteration();

            LinkedHashMap<E, Boolean> cycle = new LinkedHashMap<E, Boolean>();

            sp.compute(edgeStruct.to, edgeStruct.from);
            EdgeStruct oldEdgeStruct = edgeStruct;
            Boolean orientation = true;
            cycle.put(edgeStruct.edge, orientation);

            LinkedList<EdgeStruct> path = sp.trackPath();

            if(path.size() == 0){
                throw new RuntimeException("something is wrong with the " +
                        "minimum spanning forest, cycle of size 1 detected");
            }

            for(EdgeStruct edgeStruct2 : path){
                orientation = orientation
                        && oldEdgeStruct.to.equals(edgeStruct2.from)
                        || !orientation
                        && oldEdgeStruct.from.equals(edgeStruct2.from);
                oldEdgeStruct = edgeStruct2;
                cycle.put(edgeStruct2.edge, orientation);
            }

            cycles.add(cycle);

            sp.addEdge(edgeStruct, edgeStruct.from, edgeStruct.to,
                    edgeStruct.weight);

            counter++;
        }

    }
    // =========================================================================
    // === SETTERS =============================================================
    // =========================================================================
    public void setIterationProgressCounter(
            IterationProgressCounter iterationProgressCounter) {
        this.iterationProgressCounter = iterationProgressCounter;
    }

    public void setApplyFundamentalImprovement(Boolean applyFundamentalImprovement) {
        this.applyFundamentalImprovement = applyFundamentalImprovement;
    }

    public void setUseMinimalSpanningTree(Boolean useMinimalSpanningTree) {
        this.useMinimalSpanningForest = useMinimalSpanningTree;
    }

    // =========================================================================
    // === GETTERS =============================================================
    // =========================================================================
    public LinkedHashSet<LinkedHashMap<E, Boolean>> getCycles(){
        if(!computed){
            throw new RuntimeException("need to compute cycles first");
        }
        return cycles;
    }

}
