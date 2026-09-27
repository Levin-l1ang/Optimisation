import java.util.ArrayList;

public class Line {

    public boolean directed;
    public int index;
    public ArrayList<Edge> edges;
    public ArrayList<Stop> stops;
    public int frequency;
    public double length;
    public double costs;

    public Line(boolean directed, int index) {
        this.directed = directed;
        this.index = index;
        this.edges = new ArrayList<>();
        this.stops = new ArrayList<>();
        this.frequency = 0;
        this.length = 0.0;
        this.costs = 0.0;
    }

    public ArrayList<Stop> computeStops(ArrayList<Edge> edges) {
        ArrayList<Stop> stops = new ArrayList<>();
        if (edges == null || edges.isEmpty()) {
            return stops;
        } else if (edges.size() == 1) {
            stops.add(edges.get(0).getLeft_stop());
            stops.add(edges.get(0).getRight_stop());
        } else {
            boolean turnedEdge = (edges.get(0).getLeft_stop() == edges.get(1).getLeft_stop() || edges.get(0).getLeft_stop() == edges.get(1).getRight_stop());
            if (turnedEdge)
                stops.add(edges.get(0).getRight_stop());
            else
                stops.add(edges.get(0).getLeft_stop());
            for (Edge edge : edges) {
                if (turnedEdge)
                    stops.add(edge.getLeft_stop());
                else
                    stops.add(edge.getRight_stop());
                if (edges.size() >= edges.indexOf(edge))
                    if (turnedEdge)
                        turnedEdge = (edge.getLeft_stop() == edges.get(edges.indexOf(edge) + 1).getLeft_stop() || edge.getLeft_stop() == edges.get(edges.indexOf(edge) + 1).getRight_stop());
                    else
                        turnedEdge = (edge.getRight_stop() == edges.get(edges.indexOf(edge) + 1).getLeft_stop() || edge.getRight_stop() == edges.get(edges.indexOf(edge) + 1).getRight_stop());
            }
        }
        return stops;
    }

    public void addEdge(int position, Edge edge) {
        if (position != this.edges.size() + 1) {
            System.out.println("Data Inconsistency: Position of edge " + edge.getIndex() + " exceeds array size.");
            System.exit(1);
        }
        this.edges.add(position - 1, edge);
        if (this.edges.size() == 1) {
            this.stops.add(edge.getLeft_stop());
            this.stops.add(edge.getRight_stop());
        } else if (this.edges.size() == 2) {
            if (this.stops.get(1) == edge.getLeft_stop())
                this.stops.add(edge.getRight_stop());
            else if (this.stops.get(1) == edge.getRight_stop())
                this.stops.add(edge.getLeft_stop());
            else if (this.stops.get(0) == edge.getLeft_stop()) {
                this.stops.remove(0);
                this.stops.add(edge.getLeft_stop());
                this.stops.add(edge.getRight_stop());
            } else if (this.stops.get(0) == edge.getRight_stop()) {
                this.stops.remove(0);
                this.stops.add(edge.getRight_stop());
                this.stops.add(edge.getLeft_stop());
            }
        } else if (this.edges.size() > 2) {
            if (this.edges.get(position - 2).getRight_stop() == edge.getLeft_stop() || this.edges.get(position - 2).getLeft_stop() == edge.getLeft_stop())
                this.stops.add(edge.getRight_stop());
            else
                this.stops.add(edge.getLeft_stop());
        }
    }

    public int getIndex() {
        return this.index;
    }

    public ArrayList<Edge> getEdges() {
        return this.edges;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setEdges(ArrayList<Edge> edges) {
        this.edges = edges;
        this.stops = computeStops(edges);
    }

    public ArrayList<Stop> getStops() {
        return this.stops;
    }

    public void setFrequency(int freq) {
        this.frequency = freq;
    }

    public int getFrequency() {
        return this.frequency;
    }

    public void setCosts(double costs) {
        this.costs = costs;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getLength() {
        return this.length;
    }

    @Override
    public String toString() {
        return "" + this.index;
    }
}
