import java.util.*;

public class ChangeGoPath implements Comparable<ChangeGoPath> {

    public int index;
    public ArrayList<Vertex> vertices;
    public Double dualCosts;
    public int ptnPathIndex;

    public ChangeGoPath() {
        this.index = 0;
        this.dualCosts = 0.0;
        this.vertices = new ArrayList<>();
    }

    public ChangeGoPath(ArrayList<Vertex> vertices) {
        this.index = 0;
        this.dualCosts = 0.0;
        this.vertices = vertices;
    }

    public void addVertex(Vertex vertex) {
        this.vertices.add(vertex);
    }

    public ArrayList<Vertex> getVertices() {
        return this.vertices;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }

    public boolean contains(Vertex vertex) {
        return this.vertices.contains(vertex);
    }

    public int getLength() {
        return vertices.size();
    }

    public Vertex getFirst() {
        return this.vertices.get(0);
    }

    public Vertex getLast() {
        return this.vertices.get(this.vertices.size() - 1);
    }

    public Double getDualCosts() {
        return this.dualCosts;
    }

    public void setDualCosts(Double dualCosts) {
        this.dualCosts = dualCosts;
    }

    public int getPTNPathIndex() {
        return this.ptnPathIndex;
    }

    public void setPTNPathIndex(int ptnPathIndex) {
        this.ptnPathIndex = ptnPathIndex;
    }

    public boolean usesLineOnEdge(Line line, Edge edge) {
        for (Vertex vertex : vertices) {
            if (vertex.getLine() != null && vertex.getLine() == line) {
                if (vertices.get(vertices.indexOf(vertex) + 1).getLine() != null && ((vertex.getStopIndex() == edge.getLeft_stop().getIndex() && vertices.get(vertices.indexOf(vertex) + 1).getStopIndex() == edge.getRight_stop().getIndex()) || (vertex.getStopIndex() == edge.getRight_stop().getIndex() && vertices.get(vertices.indexOf(vertex) + 1).getStopIndex() == edge.getLeft_stop().getIndex()))) {
                    return true;
                } else if (vertices.get(vertices.indexOf(vertex) - 1).getLine() != null && ((vertex.getStopIndex() == edge.getLeft_stop().getIndex() && vertices.get(vertices.indexOf(vertex) - 1).getStopIndex() == edge.getRight_stop().getIndex()) || (vertex.getStopIndex() == edge.getRight_stop().getIndex() && vertices.get(vertices.indexOf(vertex) - 1).getStopIndex() == edge.getLeft_stop().getIndex()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getNumberChanges() {
        int count = 0;
        for (Vertex vertex : vertices) {
            if (vertex.getLine() == null)
                count++;
        }
        return count - 1;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (Vertex vertex : vertices) {
            s.append("-(").append(vertex.getStopIndex()).append(",").append((vertex.getLine() == null) ? "" : vertex.getLine().getIndex()).append(")");
        }
        return s.toString();
    }

    @Override
    public int compareTo(ChangeGoPath p) {
        if (p.getVertices() == null && this.getVertices() == null) {
            return 0;
        } else if (this.getVertices() == null) {
            return 1;
        } else if (p.getVertices() == null) {
            return -1;
        } else if (p.hashCode() == this.hashCode()) {
            return 0;
        } else if (this.getNumberChanges() > p.getNumberChanges()) {
            return 1;
        } else {
            return -1;
        }
    }

    public boolean usesAVertexTwice() {
        for (Vertex vertex : this.vertices) {
            if (Collections.frequency(this.vertices, vertex) > 1)
                return true;
        }
        return false;
    }

    public boolean usesALineTwice() {
        int lineIndex = 0;
        for (Vertex vertex1 : this.vertices) {
            if (vertex1.getLine() != null) {
                lineIndex = vertex1.getLine().getIndex();
            } else if (lineIndex != 0) {
                for (Vertex vertex2 : this.vertices) {
                    if (this.vertices.indexOf(vertex2) > this.vertices.indexOf(vertex1) && vertex2.getLine() != null && vertex2.getLine().getIndex() == lineIndex) {
                        return true;
                    }
                }
                lineIndex = 0;
            }
        }
        return false;
    }

    public boolean passesSameStops(ChangeGoPath p) {
        if (p.getVertices().size() != this.getVertices().size())
            return false;
        if (p.getVertices().get(0).equalsStopLine(this.getVertices().get(0))) {
            for (Vertex vertex : this.getVertices())
                if (vertex.getLine() == null && vertex.getStopIndex() != p.getVertices().get(this.getVertices().indexOf(vertex)).getStopIndex())
                    return false;
                else if (vertex.getLine() != null && vertex.getLine().getIndex() != p.getVertices().get(this.getVertices().indexOf(vertex)).getLine().getIndex())
                    return false;
        } else if (p.getVertices().get(p.getVertices().size() - 1).equalsStopLine(this.getVertices().get(0))) {
            for (Vertex vertex : this.getVertices())
                if (vertex.getLine() == null && vertex.getStopIndex() != p.getVertices().get(p.getVertices().size() - this.getVertices().indexOf(vertex) - 1).getStopIndex())
                    return false;
                else if (vertex.getLine() != null && vertex.getLine().getIndex() != p.getVertices().get(p.getVertices().size() - this.getVertices().indexOf(vertex) - 1).getLine().getIndex())
                    return false;
        } else {
            return false;
        }
        return true;
    }

    public boolean equals(ChangeGoPath p) {
        if (this.hashCode() == p.hashCode()) {
            return true;
        }
        return false;
    }
}
