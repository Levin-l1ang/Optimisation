import net.lintim.util.Pair;

import java.util.*;

public class Line implements Comparable<Line> {
    private final LinkedList<Edge> edges;
    // Maps each used edge to the boolean whether the direction is switched (in
    // undirected case)
    private final Hashtable<Integer, Boolean> edge_switch_direction;
    private final LinkedList<Stop> stops;
    public static Set<Pair<Integer, Integer>> restrictedTurns = null;
    private double cost;
    private double length;
    private double duration;
    private int line_index;
    private static double costs_fixed;
    private static double costs_length;
    private static double costs_edges;
    private static double costs_vehicles;
    private static boolean directed;
    private static int minimum_edges;
    private static double minimum_distance;
    private static boolean restrict_line_duration_periodically = false;
    private static int period_length;
    private static int min_turnaround_time;
    private static int periodic_restriction_lower_bound;
    private static int periodic_restriction_upper_bound;
    private static boolean allow_half_period_length;
    private static double half_period_restriction_lower_bound;
    private static double half_period_restriction_upper_bound;
    private static double waiting_time_in_station;

    // Constructor----------------------------------------------------------
    public Line() {
        edges = new LinkedList<>();
        edge_switch_direction = new Hashtable<>();
        stops = new LinkedList<>();
        line_index = Integer.MAX_VALUE;
        cost = costs_fixed;
        length = 0;
        duration = 0;
    }

    public Line(Line old_line) {
        edges = new LinkedList<>();
        edge_switch_direction = new Hashtable<>();
        stops = new LinkedList<>();
        line_index = Integer.MAX_VALUE;
        cost = old_line.computeCostWithoutVehicles();
        length = old_line.getLength();
        duration = old_line.getDuration();
        edges.addAll(old_line.getEdges());
        edge_switch_direction.putAll(old_line.getEdgeSwitchDirection());
        stops.addAll(old_line.getStops());
    }

    // Getter---------------------------------------------------------------
    public double getCostWithVehicles() {
        if (costs_vehicles == 0) {
            return cost;
        } else {
            // For directed lines, the line needs to be traversed once, for undirected lines twice
            int durationFactor = directed ? 1 : 2;
            return cost + costs_vehicles * Math.ceil(durationFactor * (duration + min_turnaround_time) / period_length);
        }
    }

    public double getLength() {
        return length;
    }

    public double getDuration() {
        return duration;
    }

    public LinkedList<Edge> getEdges() {
        return edges;
    }

    public Hashtable<Integer, Boolean> getEdgeSwitchDirection() {
        return edge_switch_direction;
    }

    public LinkedList<Stop> getStops() {
        return stops;
    }

    public boolean directionSwitched(Edge edge) {
        return edge_switch_direction.get(edge.getIndex());
    }

    public Stop getLastStop() {
        return stops.getLast();
    }

    public Stop getFirstStop() {
        return stops.getFirst();
    }

    public int getIndex() {
        return line_index;
    }

    public static boolean isDirected() {
        return directed;
    }

    public static int getMinimumEdges() {
        return minimum_edges;
    }

    // Setter--------------------------------------------------------------
    public void setLineIndex(int line_index) {
        this.line_index = line_index;
    }

    public static void setCostsFixed(double cf) {
        costs_fixed = cf;
    }

    public static void setCostsLength(double cl) {
        costs_length = cl;
    }

    public static void setCostsEdges(double ce) {
        costs_edges = ce;
    }

    public static void setCostsVehicles(double cv) {
        costs_vehicles = cv;
    }

    public static void setDirected(boolean d) {
        directed = d;
    }

    public static void setMinimumEdges(int m) {
        minimum_edges = m;
    }

    public static void setMinimumDistance(double m) {
        minimum_distance = m;
    }

    public static void setRestrictLineDurationPeriodically(boolean restrict) {
        restrict_line_duration_periodically = restrict;
    }

    public static void setPeriodLength(int period_length) {
        Line.period_length = period_length;
    }

    public static void setMinTurnaroundTime(int min_turnaround_time) {
        Line.min_turnaround_time = min_turnaround_time;
    }

    public static void setPeriodicRestrictions(int lower_bound, int upper_bound) {
        periodic_restriction_lower_bound = lower_bound;
        periodic_restriction_upper_bound = upper_bound;
    }

    public static void setHalfPeriodRestrictions(double lower_bound, double upper_bound) {
        half_period_restriction_lower_bound = lower_bound;
        half_period_restriction_upper_bound = upper_bound;
    }

    public static void setAllowHalfPeriodLength(boolean allow_half_period_length) {
        Line.allow_half_period_length = allow_half_period_length;
    }

    public static void setWaitingTimeInStation(String ean_model_weight_wait, double minimal_wait_time, double maximal_wait_time) {
        switch (ean_model_weight_wait.toUpperCase()) {
            case "MINIMAL_WAITING_TIME":
                waiting_time_in_station = minimal_wait_time;
                break;
            case "AVERAGE_WAITING_TIME":
                waiting_time_in_station = (minimal_wait_time + maximal_wait_time) / 2.;
                break;
            case "MAXIMAL_WAITING_TIME":
                waiting_time_in_station = maximal_wait_time;
                break;
            case "ZERO_COST":
                waiting_time_in_station = 0;
                break;
            default:
                throw new RuntimeException("Incorrect parameter for ean_model_weight_wait.");

        }
    }

    // Methods-Construction------------------------------------------------
    public boolean addFirstEdge(Edge edge, Stop stop) {
        if (!stop.isLeaf() && !stop.isTerminal())
            return false;
        if (!edges.isEmpty())
            return false;
        Stop left_stop = edge.getLeftStop();
        Stop right_stop = edge.getRightStop();
        if (left_stop.equals(stop)) {
            edge_switch_direction.put(edge.getIndex(), false);
            stops.add(left_stop);
            stops.add(right_stop);
        } else if (!directed && right_stop.equals(stop)) {
            edge_switch_direction.put(edge.getIndex(), true);
            stops.add(right_stop);
            stops.add(left_stop);
        } else {
            return false;
        }
        edges.add(edge);
        cost += costs_length * edge.getLength() + costs_edges;
        length += edge.getLength();
        duration += edge.getDuration();
        return true;
    }

    public boolean addEdge(Edge edge) {
        if (checkEdgeLeftToRight(edge)) {
            // Direction of edges does not need to be changed
            edge_switch_direction.put(edge.getIndex(), false);
            stops.add(edge.getRightStop());
        } else if (!directed && checkEdgeRightToLeft(edge)) {
            edge_switch_direction.put(edge.getIndex(), true);
            stops.add(edge.getLeftStop());
        } else {
            return false;
        }
        edges.add(edge);
        cost += costs_length * edge.getLength() + costs_edges;
        length += edge.getLength();
        // Waiting time in stations has to be added for all edges except the first
        duration += edge.getDuration() + waiting_time_in_station;
        return true;
    }

    private boolean checkEdgeLeftToRight(Edge edge) {
        Stop last_stop;
        Stop new_last_stop = edge.getRightStop();
        Edge last_edge = edges.getLast();
        if (edge_switch_direction.get(last_edge.getIndex())) {
            last_stop = last_edge.getLeftStop();
        } else {
            last_stop = last_edge.getRightStop();
        }
        if (last_stop.equals(edge.getLeftStop())) {
            // Check whether the edge is already contained in the line
            return true;
        }
        // Check if this is a restricted turn
        if (restrictedTurns != null && restrictedTurns.contains(new Pair<>(last_edge.getIndex(), edge.getIndex()))) {
            return false;
        }
        return false;
    }

    private boolean checkEdgeRightToLeft(Edge edge) {
        Stop last_stop;
        Edge last_edge = edges.getLast();
        if (edge_switch_direction.get(last_edge.getIndex())) {
            last_stop = last_edge.getLeftStop();
        } else {
            last_stop = last_edge.getRightStop();
        }
        if (last_stop.equals(edge.getRightStop())) {
            // Check whether the edge is already contained in the line
            return !edges.contains(edge);
        }
        return false;
    }

    public boolean isComplete(boolean restrict_terminals) {
        Stop first_stop = this.getFirstStop();
        Stop last_stop = this.getLastStop();
        // check if any restrictions have to be fulfilled but are not
        if(!checkRestrictions(duration)){
            return false;
        }
        // lines may begin and end at terminals
        if (first_stop.isTerminal() && last_stop.isTerminal()
            && edges.size() >= minimum_edges) {
            return true;
        }
        else if (restrict_terminals) {
            // Never all non-terminal lines when they are restricted
            return false;
        }
        // lines may begin at terminals and end at leaves
        else if (first_stop.isTerminal() && last_stop.isLeaf()
            && edges.size() >= minimum_edges) {
            return true;
        }
        // lines may begin at leaves and end at terminals
        else if (first_stop.isLeaf() && last_stop.isTerminal()
            && edges.size() >= minimum_edges) {
            return true;
        }
        return false;
    }

    public boolean isLeafToLeaf() {
        Stop first_stop = this.getFirstStop();
        Stop last_stop = this.getLastStop();
        // check if any restrictions have to be fulfilled but are not
        if (!checkRestrictions(duration)) {
            return false;
        }
        // lines may begin and end at leaves
        return first_stop.isLeaf() && last_stop.isLeaf()
            && Stop.distance(first_stop, last_stop) >= minimum_distance;
    }

    // private methods-------------------------------------------------------

    private boolean checkRestrictions(double duration) {
        double periodic_duration = duration % period_length;
        boolean half_period_feasible = allow_half_period_length && checkHalfPeriod(duration);
        boolean periodically_feasible = checkPeriodically(periodic_duration);
        return !restrict_line_duration_periodically || half_period_feasible || periodically_feasible;
    }

    // if restrict_line_duration_periodically is true, the line duration has to be in the bounds
    // modulo period_length
    private boolean checkPeriodically(double periodic_duration) {
        return periodic_restriction_lower_bound <= periodic_duration && periodic_duration <= periodic_restriction_upper_bound;
    }

    private boolean checkHalfPeriod(double duration) {
        return half_period_restriction_lower_bound <= duration && duration <= half_period_restriction_upper_bound;
    }

    private double computeCostWithoutVehicles() {
        double cost_without_vehicles = costs_fixed;
        for (Edge edge : edges) {
            cost_without_vehicles += costs_length * edge.getLength() + costs_edges;
        }
        return cost_without_vehicles;
    }

    // toString--------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder representation = new StringBuilder("(" + line_index + ",");
        for (Edge edge : edges) {
            representation.append(edge).append(",");
        }
        return representation + ")";
    }

    // CSV-------------------------------------------------------------------
    public String toCSV() {
        if (edges.isEmpty())
            return "!!!!!!EMPTY LINE!!!!!!\n";
        StringBuilder output = new StringBuilder();
        int edge_index = 0;
        for (Edge edge : edges) {
            edge_index++;
            output.append(line_index).append(";").append(edge_index).append(";").append(edge.getIndex()).append("\n");
        }
        return output.toString();
    }

    public String toCSVCost(double conversionFactorLength) {
        if (edges.isEmpty())
            return "!!!!!!EMPTY LINE!!!!!!\n";
        String cost_string = String.format(Locale.US, "%.6f", this.getCostWithVehicles());
        // the length of the line is stored in km. We want to transform it back to the units given in the
        // original ptn edge file
        return line_index + ";" + length/conversionFactorLength + ";" + cost_string + "\n";
    }

    // equals&sort---------------------------------------------------------------
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof Line))
            return false;
        Line other = (Line) o;
        if (other.getEdges().size() != edges.size())
            return false;
        Iterator<Edge> it_other = other.getEdges().iterator();
        Edge edge_other;
        for (Edge edge : edges) {
            edge_other = it_other.next();
            if (!edge.equals(edge_other)) {
                return false;
            }
        }
        return true;
    }


    public int compareTo(Line other) {
        // shorter lines are sorted as smaller
        if (other.getEdges().size() < edges.size()) {
            return -1;
        }
        // longer lines are sorted as bigger
        else if (other.getEdges().size() > edges.size()) {
            return 1;
        }
        // compare each edge
        else {
            Iterator<Edge> it_other = other.getEdges().iterator();
            Edge edge_other;
            for (Edge edge : edges) {
                edge_other = it_other.next();
                // consider first edge that does not coincide
                if (!edge.equals(edge_other)) {
                    // if the index is smaller it is sorted as smaller
                    if (edge_other.getIndex() < edge.getIndex()) {
                        return -1;
                    } else {
                        return +1;
                    }
                }
            }
            return 0;
        }
    }

}
