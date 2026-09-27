import java.util.*;

import org.jgrapht.*;
import org.jgrapht.graph.*;

public class Path {

    /**
     * list containing the nodes of the path
     */
    protected ArrayList<Node> nodes;

    /**
     * weight of the path
     */
    protected double weight;

    /**
     * corresponding graph path
     */
    GraphPath<Node, DefaultWeightedEdge> path;

    /**
     * corresponding graph, ie path is in this graph
     */
    protected Graph<Node, DefaultWeightedEdge> graph;


//------------Constructor---------------------------------

    /**
     * Constructor
     * initializes a path
     *
     * @param path graph path in graph
     **/

    public Path(GraphPath<Node, DefaultWeightedEdge> path) {
        this.path = path;
        this.weight = path.getWeight();
        this.graph = path.getGraph();
        this.nodes = new ArrayList<>(0);
        this.nodes.add(path.getStartVertex());
        List<DefaultWeightedEdge> edgeList = path.getEdgeList();
        for (int i = 0; i < edgeList.size(); i++) {
            if (nodes.get(i) != graph.getEdgeSource(edgeList.get(i))) {
                nodes.add(graph.getEdgeSource(edgeList.get(i)));
            } else {
                nodes.add(graph.getEdgeTarget(edgeList.get(i)));
            }
        }
    }


//-------------------Setter/Getter-----------------------------


    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public double getWeight() {
        return this.weight;
    }

    public GraphPath<Node, DefaultWeightedEdge> getPath() {
        return this.path;
    }

//-------------------getLineIds----------------------------

    /**
     * computes the lines used by this path
     *
     * @return list of line ids used by this paths
     */

    public ArrayList<Integer> getLineIds() {
        ArrayList<Integer> list = new ArrayList<>(0);
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getLineId() > 0 && !(list.contains(nodes.get(i).getLineId()))) {
                list.add(nodes.get(i).getLineId());
            }
        }
        return list;
    }

    /**
     * computes how many edges of each line are used
     *
     * @return list containing the number of edges in the intersection of the
     * path and each line
     */
    public ArrayList<Integer> getLineIds(int numberOfLines) {
        ArrayList<Integer> list = new ArrayList<>(0);
        for (int i = 0; i < numberOfLines; i++) {
            list.add(0);
        }
        int k;
        for (int i = 0; i < nodes.size() - 1; i++) {
            k = nodes.get(i).getLineId();
            if (k > 0 && k == nodes.get(i + 1).getLineId()) {
                list.set(k - 1, list.get(k - 1) + 1);
            }
        }
        return list;
    }

//----------------changeWeight--------------------------------

    /**
     * changes the weight of the path
     * when a shortest path in the updated cag is computed it might have
     * a higher weight since the cag is updated with the value of
     * the dual line constraint variables
     * sets the weight to the real length
     * used if the line constraint type is 1 or 3
     */
    public void changeWeight(double[] dual) {
        double sum = 0;
        for (int i = 0; i < nodes.size() - 1; i++) {
            if (nodes.get(i).getLineId() > 0 && nodes.get(i + 1).getLineId() ==
                nodes.get(i).getLineId()) {
                sum = sum + dual[nodes.get(i).getLineId() - 1];
            }
        }
        this.weight = this.weight + sum;
    }

    /**
     * changes the weight of the path
     * when a shortest path in the updated cag is computed it has
     * a higher weight since the cag is updated with the value of
     * the dual line constraint variables
     * sets the weight to the real length
     * used if the line constraint type is 2 or 4
     */
    public void changeWeightAllEdges(ArrayList<ArrayList<Double>> dual) {
        double sum = 0;
        for (int k = 1; k < nodes.size() - 1; k++) {
            if (nodes.get(k).getLineId() > 0 && nodes.get(k).getLineId() ==
                nodes.get(k + 1).getLineId()) {
                if (nodes.get(k).getPosition() < nodes.get(k + 1).getPosition()) {
                    sum = sum + dual.get(nodes.get(k).getLineId() - 1).get(
                        nodes.get(k).getPosition());
                } else {
                    sum = sum + dual.get(nodes.get(k).getLineId() - 1).get(
                        nodes.get(k + 1).getPosition());
                }
            }
        }
        this.weight = this.weight + sum;
    }
//--------------------toString------------------------------

    public String toString() {
        if (nodes.size() == 0) {
            return "empty path";
        }
        String s = "(" + nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            s = s + "," + nodes.get(i);
        }
        s = s + ")";
        s = s + "\n WEIGHTS=";
        List<DefaultWeightedEdge> edgeList = path.getEdgeList();
        for (int i = 0; i < edgeList.size(); i++) {
            s = s + graph.getEdgeWeight(edgeList.get(i)) + " ";
        }
        return s;
    }
}
