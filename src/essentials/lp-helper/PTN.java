import java.util.*;
import java.util.Map.Entry;

public class PTN {

    private final LinkedList<Stop> stops;
    private final LinkedList<Edge> edges;

    private final boolean directed;

    //constructor--------------------------------------------------
    public PTN(boolean directed) {
        this.directed = directed;
        stops = new LinkedList<>();
        edges = new LinkedList<>();
    }

    //Getter-------------------------------------------------------
    public boolean isDirected() {
        return directed;
    }

    public LinkedList<Stop> getStops() {
        return stops;
    }

    public LinkedList<Edge> getEdges() {
        return edges;
    }

    //In edge-file, stops are referred to by index but the edges need a
    //reference to the stop itself.
    public Stop getStop(int index) {
        int list_index = stops.indexOf(new Stop(index, "", "", 0., 0.));
        if (list_index == -1)
            return null;
        return stops.get(list_index);
    }

    public boolean hasPreferredEdge() {
        for (Edge e : edges) {
            if (e.isPreferred()) {
                return true;
            }
        }
        return false;
    }

    //Methods------------------------------------------------------
    public void addStop(Stop stop) {
        stops.add(stop);
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public void resetEdges() {
        for (Edge edge : edges) {
            edge.setPreferred(false);
        }
    }

    //degree ausrechnen nochmal geändert
    //vorher: nur left-stop
    public int stopsToTerminal(double variance_max_deg, boolean restrictTerminals, Set<Integer> terminals){
        int sum = 0;
        if (restrictTerminals) {
            for (Stop stop: stops) {
                if (terminals.contains(stop.getIndex())) {
                    stop.setIsTerminal(true);
                    sum += 1;
                }
            }
        }
        else {
            System.out.println("Dont restrict terminals");
            int max_deg = 0;
            Stop left_stop;
            Stop right_stop;
            HashMap<Stop, Integer> degree = new HashMap<Stop, Integer>();
            for (Stop stop : stops) {
                degree.put(stop, 0);
            }
            for (Edge edge : edges) {
                left_stop = edge.getLeftStop();
                right_stop = edge.getRightStop();
                degree.put(left_stop, degree.get(left_stop) + 1);
                degree.put(right_stop, degree.get(right_stop) + 1);
            }
            //Compute maximum degree
            for (Entry<Stop, Integer> entry : degree.entrySet()) {
                if (max_deg < entry.getValue()) {
                    max_deg = entry.getValue();
                }
            }
            //Set Terminals
            for (Entry<Stop, Integer> entry : degree.entrySet()) {
                if (entry.getValue() >= variance_max_deg * max_deg) {
                    entry.getKey().setIsTerminal(true);
                    sum++;
                }
            }
        }
        return sum;
    }


}
