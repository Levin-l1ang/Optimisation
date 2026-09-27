import java.util.ArrayList;

public class ChangeGoGraph {

    public ArrayList<Vertex> vertices;
    public ArrayList<Arc> arcs;

    public ChangeGoGraph() {
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

    public Vertex getVertex(int index) {
        for (Vertex vertex : vertices)
            if (index == vertex.getIndex())
                return vertex;

        return null;
    }

    public Vertex getVertex(int stopID, Line line) {
        for (Vertex vertex : vertices) {
            if (vertex.getStopIndex() == stopID && ((line == null && vertex.getLine() == null) || line == vertex.getLine())) {
                return vertex;
            }
        }
        return null;
    }

    public Arc getArc(int index) {
        for (Arc arc : arcs)
            if (index == arc.getIndex())
                return arc;
        return null;
    }

    public ArrayList<Arc> getOutgoingArcs(Vertex vertex) {
        ArrayList<Arc> outgoingArcs = new ArrayList<>();
        for (Arc arc : this.arcs) {
            if (arc.getLeftVertex() == vertex)
                outgoingArcs.add(arc);
            else if (arc.getRightVertex() == vertex && !arc.isDirected())
                outgoingArcs.add(arc);
        }
        return outgoingArcs;
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
