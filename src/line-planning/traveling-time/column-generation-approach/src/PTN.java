import java.io.*;
import java.util.*;

import org.jgrapht.graph.*;

/**
 * class representing a PTN
 */
public class PTN {

    /**
     * list containing the edges
     */
    protected ArrayList<Edge> edges;

    /**
     * list containing the nodes
     */
    protected ArrayList<Node> nodes;

    /**
     * graph with the same nodes and edges
     * used for computing shortest paths
     */
    protected SimpleWeightedGraph<Node, DefaultWeightedEdge> graph;

    /**
     * ArrayList with the length of the number of od pairs
     * each entry contains an array with the origin, the destination and
     * the number of passengers
     */
    protected ArrayList<int[]> od;

    /**
     * odPos[i][j] gives the position of (i,j) in od
     */
    protected int[][] odPos;

//----------------------Constructor-------------------------------------------------

    /**
     * reads the nodes edges and od matrix from corresponding files
     * initializes graph with this data
     *
     * @param nodeFile file containing the nodes
     * @param edgeFile file containing the edges
     * @param odFile   file containing the od matrix
     */
    public PTN(String nodeFile, String edgeFile, String odFile) {
        graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        //initialize nodes of the PTN
        Node node;
        try {
            nodes = new ArrayList<>(0);
            BufferedReader in = new BufferedReader(new FileReader(nodeFile));
            Scanner scan = new Scanner(in);
            String line;
            String[] values;
            while (scan.hasNext()) {
                line = scan.nextLine().trim();
                if (line.indexOf("#") == 0)
                    continue;
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#") - 1);
                }
                if (line.contains(";")) {
                    values = line.split(";");
                    node = new Node(values[1].trim(), Integer.parseInt(values[0].trim()));
                    nodes.add(node);
                    graph.addVertex(node);
                }

            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //initialize the edges of the PTN
        Edge edge;
        try {
            edges = new ArrayList<>(0);
            BufferedReader in = new BufferedReader(new FileReader(edgeFile));
            Scanner scan = new Scanner(in);
            String line;
            String[] values;
            while (scan.hasNext()) {
                line = scan.nextLine().trim();
                if (line.indexOf("#") == 0)
                    continue;
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#") - 1);
                }
                if (line.contains(";")) {
                    values = line.split(";");
                    edge = new Edge(Integer.parseInt(values[0].trim()),
                        nodes.get(Integer.parseInt(values[1].trim()) - 1),
                        nodes.get(Integer.parseInt(values[2].trim()) - 1),
                        Double.parseDouble(values[3].trim()));
                    edges.add(edge);
                    graph.addEdge(edge.getLeftNode(), edge.getRightNode());
                    graph.setEdgeWeight(graph.getEdge(edge.getLeftNode(),
                        edge.getRightNode()), edge.getWeight());
                }
            }
            in.close();
            edges.sort(Comparator.comparing(Edge::getId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        //initalize od and odPos
        int[][] odMatrix;

        try {
            odMatrix = new int[nodes.size()][nodes.size()];
            BufferedReader in = new BufferedReader(new FileReader(odFile));
            Scanner scan = new Scanner(in);
            String line;
            String[] values;
            while (scan.hasNext()) {
                line = scan.nextLine().trim();
                if (line.indexOf("#") == 0)
                    continue;
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#") - 1);
                }
                if (line.contains(";")) {
                    values = line.split(";");
                    odMatrix[Integer.parseInt(values[0].trim()) - 1]
                        [Integer.parseInt(values[1].trim()) - 1]
                        = Integer.parseInt(values[2].trim());
                }
            }
            in.close();
            this.od = new ArrayList<>(0);
            int[] odPair;
            odPos = new int[nodes.size()][nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (odMatrix[i][j] > 0) {
                        odPair = new int[3];
                        odPair[0] = i;
                        odPair[1] = j;
                        odPair[2] = odMatrix[i][j];
                        od.add(odPair);
                    }
                    odPos[i][j] = od.size() - 1;
                    odPos[j][i] = odPos[i][j];
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);

        }

    }

//-----------------------Setter/Getter---------------------------

    public void setNodes(ArrayList<Node> nodes) {
        this.nodes = nodes;
    }

    public void setEdges(ArrayList<Edge> edges) {
        this.edges = edges;
    }

    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public ArrayList<Edge> getEdges() {
        return this.edges;
    }

    public SimpleWeightedGraph<Node, DefaultWeightedEdge> getGraph() {
        return graph;
    }

    public ArrayList<int[]> getOd() {
        return this.od;
    }

    public int[][] getOdPos() {
        return this.odPos;
    }

//-----------------------ToString--------------------------------

    public String toString() {
        String s = "";
        s = s + "Nodes: \n";
        for (int i = 0; i < nodes.size(); i++) {
            s = s + nodes.get(i) + "\n";
        }

        s = s + "Edges: \n";
        for (int i = 0; i < edges.size(); i++) {
            s = s + edges.get(i) + "\n";
        }
        return s;
    }

}
