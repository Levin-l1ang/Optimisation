import org.jgrapht.graph.*;

import java.util.Objects;

/**
 * edge class for the edges in the PTN and CAG
 */
public class Edge {

    protected int id;
    protected int lineId = -1;
    protected Node leftNode;
    protected Node rightNode;
    protected double weight;

//------------Constructor------------------------------------------------

    public Edge(int id, Node leftNode, Node rightNode, double weight) {
        this.id = id;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.weight = weight;
    }

    public Edge(int id, Node leftNode, Node rightNode, double weight, int lineId) {
        this.id = id;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.weight = weight;
        this.lineId = lineId;
    }


//-----------Setter/Getter-----------------------------------------------

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public Node getLeftNode() {
        return this.leftNode;
    }

    public Node getRightNode() {
        return this.rightNode;
    }

    public double getWeight() {
        return this.weight;
    }

    public int getId() {
        return this.id;
    }

    public int getLineId() {
        return this.lineId;
    }

//---------------Equals----------------------------------------------------------


    @Override
    public int hashCode() {
        return Objects.hash(id, lineId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return id == edge.id && lineId == edge.lineId;
    }

    //----------------ToString------------------------------------------------------
    public String toString() {
        return "(" + this.leftNode + "," + this.rightNode + ")";
    }

}
