import java.util.ArrayList;

public class PTNPath {

    private int index;
    private ArrayList<Edge> listOfEdges;
    private final ArrayList<Stop> listOfStops;
    private double length;

    // Constructor for Path on PTN. Note that listOfStops is maintained by changing listOfEdges.
    // Should not change listOfStops information directly but rather by accessing listOfEdges.
    public PTNPath(int index, ArrayList<Edge> listOfEdges) {
        this.index = index;
        this.listOfEdges = listOfEdges;
        this.listOfStops = calculateListOfStops(this.listOfEdges);
        this.length = 0.0;
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public ArrayList<Edge> getEdges() {
        return this.listOfEdges;
    }

    public void setEdges(ArrayList<Edge> listOfEdges) {
        this.listOfEdges = listOfEdges;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getLength() {
        return this.length;
    }

    public void addEdge(Edge edge, boolean reversed) {
        if (this.listOfEdges == null) {
            this.listOfEdges = new ArrayList<>();
        }
        if (this.listOfEdges.size() == 0 && reversed) {
            this.listOfStops.add(edge.getRight_stop());
            this.listOfStops.add(edge.getLeft_stop());
        } else if (this.listOfEdges.size() == 0) {
            this.listOfStops.add(edge.getLeft_stop());
            this.listOfStops.add(edge.getRight_stop());
        } else if (edge.getLeft_stop() == this.listOfEdges.get(this.listOfEdges.size() - 1).getRight_stop() || edge.getLeft_stop() == this.listOfEdges.get(this.listOfEdges.size() - 1).getLeft_stop())
            listOfStops.add(edge.getRight_stop());
        else if (edge.getRight_stop() == this.listOfEdges.get(this.listOfEdges.size() - 1).getRight_stop() || edge.getRight_stop() == this.listOfEdges.get(this.listOfEdges.size() - 1).getLeft_stop())
            listOfStops.add(edge.getLeft_stop());
        else
            System.err.println("Edge does not follow up last edge in Path!");
        this.listOfEdges.add(edge);

    }

    private ArrayList<Stop> calculateListOfStops(ArrayList<Edge> listOfEdges) {
        ArrayList<Stop> listOfStops = new ArrayList<>();
        if (listOfEdges.isEmpty())
            return listOfStops;
        if (listOfEdges.size() == 1) {
            listOfStops.add(listOfEdges.get(0).getLeft_stop());
        } else if (listOfEdges.get(0).getLeft_stop() == listOfEdges.get(1).getLeft_stop() || listOfEdges.get(0).getLeft_stop() == listOfEdges.get(1).getRight_stop())
            listOfStops.add(listOfEdges.get(0).getRight_stop());
        else
            listOfStops.add(listOfEdges.get(0).getLeft_stop());
        for (Edge edge : listOfEdges)
            if (listOfStops.get(listOfStops.size() - 1) == edge.getLeft_stop())
                listOfStops.add(edge.getRight_stop());
            else
                listOfStops.add(edge.getLeft_stop());
        return listOfStops;
    }

    public ArrayList<Stop> getStops() {
        return this.listOfStops;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (Stop stop : listOfStops) {
            s.append("; ").append(stop.getIndex());
        }
        return s.toString();
    }

}
