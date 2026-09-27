import org.jgrapht.graph.DefaultEdge;

// Arc is a directed edge
public class Arc extends DefaultEdge {

    public int index;
    public int edgeIndex;
    public boolean directed;
    public Vertex leftVertex;
    public Vertex rightVertex;
    public int lineIndex;
    public double length;

    public Arc(int index, boolean directed, Vertex leftVertex, Vertex rightVertex, double length) {
        this.index = index;
        this.edgeIndex = 0;
        this.directed = directed;
        this.leftVertex = leftVertex;
        this.rightVertex = rightVertex;
        this.lineIndex = 0;
        this.length = length;
    }

    public Arc(int index, int edgeIndex, boolean directed, Vertex leftVertex, Vertex rightVertex, int lineIndex, double length) {
        this.index = index;
        this.edgeIndex = edgeIndex;
        this.directed = directed;
        this.leftVertex = leftVertex;
        this.rightVertex = rightVertex;
        this.lineIndex = lineIndex;
        this.length = length;
    }

    public int getIndex() {
        return this.index;
    }

    public int getEdgeIndex() {
        return this.edgeIndex;
    }

    public boolean isDirected() {
        return this.directed;
    }

    public Vertex getLeftVertex() {
        return this.leftVertex;
    }

    public Vertex getRightVertex() {
        return this.rightVertex;
    }

    public int getLineIndex() {
        return this.lineIndex;
    }

    public double getLength() {
        return this.length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public void setLeftVertex(Vertex vertex) {
        this.leftVertex = vertex;
    }

    public void setRightVertex(Vertex vertex) {
        this.rightVertex = vertex;
    }

    @Override
    public String toString() {
        return "Arc " + this.index + ", edgeIndex: " + this.edgeIndex + ", isDirected: " + this.directed + ", from " + this.leftVertex.getIndex() + ", to " + this.rightVertex.getIndex() + ", lineIndex " + this.lineIndex + ", length: " + this.length;
    }

    @Override
    public Vertex getSource() {
        return this.leftVertex;
    }

    @Override
    public Vertex getTarget() {
        return this.rightVertex;
    }
}
