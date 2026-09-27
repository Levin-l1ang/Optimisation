import java.util.ArrayList;

public class ChangeGo {

    public ArrayList<Vertex> vertices;
    public ArrayList<Arc> arcs;
    public Vertex rootVertex;
    public Vertex destinationVertex;

    public ChangeGo() {
        this.vertices = new ArrayList<>();
        this.arcs = new ArrayList<>();
    }

    public void addVertex(Vertex vertex) {
        vertices.add(vertex);
    }

    public void addArc(Arc arc) {
        arcs.add(arc);
    }

    public ArrayList<Vertex> getVertices() {
        return vertices;
    }

    public ArrayList<Arc> getArcs() {
        return arcs;
    }

    public void setRootVertex(Vertex vertex) {
        this.rootVertex = vertex;
    }

    public void setDestinationVertex(Vertex vertex) {
        this.destinationVertex = vertex;
    }

    public Vertex getRootVertex() {
        return this.rootVertex;
    }

    public Vertex getDestinationVertex() {
        return this.destinationVertex;
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        for (Vertex vertex : this.vertices) {
            s.append(vertex.toString()).append("\n");
        }
        for (Arc arc : this.arcs) {
            s.append(arc.toString()).append("\n");
        }
        return s.toString();
    }
}
