import java.util.*;

/**
 * class representing a line
 */
public class Line {

    protected int id;
    protected double cost;
    protected double length;
    protected ArrayList<Edge> edges;
    protected ArrayList<Node> nodes;


//---------------Constructor-----------------------------

    /**
     * Constructor
     * initializes a line
     * throws a NoPathException if the edges do not fit
     *
     * @param id    id of the line
     * @param edges ArrayList of the edges of the graph
     **/
    public Line(int id, ArrayList<Edge> edges) throws NoPathException {
        this.id = id;
        this.edges = edges;
        this.nodes = new ArrayList<>(0);
        if (edges.size() == 0) {
        } else if (edges.size() == 1) {
            nodes.add(edges.get(0).getLeftNode());
            nodes.add(edges.get(0).getRightNode());
        } else {
            Edge edge_1 = edges.get(0);
            Edge edge_2 = edges.get(1);
            if (edge_1.getLeftNode() == edge_2.getLeftNode()) {
                nodes.add(edge_1.getRightNode());
                nodes.add(edge_1.getLeftNode());
                nodes.add(edge_2.getRightNode());
            } else if (edge_1.getRightNode() == edge_2.getLeftNode()) {
                nodes.add(edge_1.getLeftNode());
                nodes.add(edge_2.getLeftNode());
                nodes.add(edge_2.getRightNode());
            } else if (edge_1.getLeftNode() == edge_2.getRightNode()) {
                nodes.add(edge_1.getRightNode());
                nodes.add(edge_1.getLeftNode());
                nodes.add(edge_2.getLeftNode());
            } else if (edge_1.getRightNode() == edge_2.getRightNode()) {
                nodes.add(edge_1.getLeftNode());
                nodes.add(edge_1.getRightNode());
                nodes.add(edge_2.getLeftNode());
            } else {
                throw new NoPathException("No connected Path. The first and second edge doesn't fit.");
            }
        }
        for (int i = 2; i < edges.size(); i++) {
            if (edges.get(i).getLeftNode() == nodes.get(i)) {
                nodes.add(edges.get(i).getRightNode());
            } else if (edges.get(i).getRightNode() == nodes.get(i)) {
                nodes.add(edges.get(i).getLeftNode());
            } else {
                throw new NoPathException("No connected Path. The " + i + 1 + "th edge doesn't fit.");
            }
        }
    }

//----------------Setter/Getter---------------------------

    public void setCost(double cost) {
        this.cost = cost;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public int getId() {
        return this.id;
    }

    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public ArrayList<Edge> getEdges() {
        return this.edges;
    }

    public double getCost() {
        return this.cost;
    }

    public double getLength() {
        return this.length;
    }

//-----------------------ToString--------------------------------

    public String toString() {
        if (nodes.size() == 0) {
            return "empty path";
        }
        String s = "Line " + this.id + ":(" + nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            s = s + "," + nodes.get(i);
        }
        s = s + ")";
        s = s + "\n Cost:" + this.cost;
        s = s + "\n Length:" + this.length;
        return s;
    }

//--------------------Equals--------------------------------------


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Line line = (Line) o;
        return id == line.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
