import net.lintim.exception.LinTimException;
import net.lintim.io.ConfigReader;
import net.lintim.io.StatisticWriter;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class EvaluateSL {

    private static final Logger logger = new Logger(EvaluateSL.class);

    private static boolean ptn_only = true;
    private static boolean sl_extended = false;
    private static Config config;
    private static Statistic statistic = new Statistic();
    private static double radius;
    private static Distance distance;
    private static boolean directed;
    private static PTN existing_ptn;
    private static PTN current_ptn;
    private static Demand demand;
    private static OD od;
    private static boolean od_exists;
    private static int number_current_stops;
    private static int number_current_edges;
    private static double time_average;
    private static boolean feasible_od;
    private static int number_existing_stops;
    private static int number_existing_edges;
    private static int number_demand_points;
    private static int number_relevant_demand_points;
    private static boolean feasible_sl;
    private static TravelingTime tt_ptn;
    private static TravelingTime tt_sl;
    private static double acceleration;
    private static double deceleration;
    private static double speed;
    private static double conversion_factor_length;
    private static double travel_time_realistic;
    private static double travel_time_const;
    private static double waiting_time;
    private static double max_distance;
    private static int number_candidates;
    private static boolean ptn_evaluate_use_travel_time_model;

    public EvaluateSL() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2)
            throw new LinTimException("Invalid call of EvaluateSL! Need two arguments, first wether to evaluate " +
                "only the ptn, second the path to the config file to read");

        ptn_only = Boolean.parseBoolean(args[1]);

        logger.info("Begin reading configuration");

        readConfig(args[0]);

        logger.debug("Only evaluate ptn: " + ptn_only);

        logger.info("Finished reading configuration");

        logger.info("Begin reading input files");

        readPTN();

        readDemand();

        readOD();

        logger.info("Finished reading input files");

        logger.info("Begin evaluating PTN");

        evaluatePTN();

        if (!ptn_only) {
            evaluateSL();
        }

        logger.info("Finished evaluating PTN");


        logger.info("Begin writing output files");
        writeStatistic();
        logger.info("Finished writing output files");

    }

//Private methods----------------------------------------------------------------------------------------------------

    private static void readConfig(String configFileName) throws IOException {
        config = new ConfigReader.Builder(configFileName).build().read();

        logger.debug("Set variables...");

        sl_extended = config.getBooleanValue("sl_eval_extended");

        if (!ptn_only) {
            radius = config.getDoubleValue("sl_radius");
            if (config.getStringValue("sl_distance").equals("euclidean_norm")) {
                distance = new EuclideanNorm();
            } else {
                throw new IOException("Distance not defined.");
            }
        }

        directed = !config.getBooleanValue("ptn_is_undirected");
        acceleration = config.getDoubleValue("sl_acceleration");
        deceleration = config.getDoubleValue("sl_deceleration");
        speed = config.getDoubleValue("gen_vehicle_speed");
        conversion_factor_length = config.getDoubleValue("gen_conversion_length");
        ptn_evaluate_use_travel_time_model = config.getBooleanValue("ptn_evaluate_use_travel_time_model");
        tt_ptn = new TravelingTime(acceleration, speed, deceleration, ptn_evaluate_use_travel_time_model);
        tt_sl = new TravelingTime(acceleration, speed, deceleration, true);
        File od_file = new File(config.getStringValue("default_od_file"));
        od_exists = od_file.exists();
        if (od_exists)
            waiting_time = config.getDoubleValue("ptn_stop_waiting_time");

        logger.debug("done!");
    }

    private static void readPTN() throws IOException {
        if (!ptn_only) {
            existing_ptn = new PTN(directed);
            logger.debug("Read existing PTN...");
            File existing_stop_file = new File(config.getStringValue("default_existing_stop_file"));
            File existing_edge_file = new File(config.getStringValue("default_existing_edge_file"));
            PTNCSV.fromFile(existing_ptn, existing_stop_file, existing_edge_file);
            logger.debug("done!");
        }

        current_ptn = new PTN(directed);
        logger.debug("Read current PTN...");
        File current_stop_file = new File(config.getStringValue("default_stops_file"));
        File current_edge_file = new File(config.getStringValue("default_edges_file"));
        PTNCSV.fromFile(current_ptn, current_stop_file, current_edge_file);
        logger.debug("done!");
    }

    private static void readDemand() throws IOException {
        if (!ptn_only) {
            logger.debug("Read Demand...");
            File demand_file = new File(config.getStringValue("default_demand_file"));
            demand = new Demand();
            DemandCSV.fromFile(demand, demand_file);
            logger.debug("done!");
        }
    }

    private static void readOD() throws IOException {
        logger.debug("Read OD-matrix...");
        if (od_exists) {
            File od_file = new File(config.getStringValue("default_od_file"));
            od = new OD();
            ODCSV.fromFile(current_ptn, od, od_file);
            logger.debug("done!");
        } else {
            logger.debug("failed!");
            logger.debug("WARNING: No OD-data available! ptn_time_average and ptn_feasible_od will not be set.");
        }
    }

    private static void evaluatePTN() {
        number_current_stops = current_ptn.getStops().size();
        number_current_edges = current_ptn.getEdges().size();
        if (od_exists)
            calcPtnTimeAverage();
    }


    private static void evaluateSL() {

        travel_time_realistic = 0;
        travel_time_const = 0;
        for (Edge edge : current_ptn.getEdges()) {
            travel_time_realistic += tt_sl.calcTime(edge.getLength() * conversion_factor_length);
            travel_time_const += conversion_factor_length * edge.getLength() * 3.6 / speed;//convert speed from km/h to m/sec
        }

        number_existing_stops = existing_ptn.getStops().size();
        number_existing_edges = existing_ptn.getEdges().size();
        number_demand_points = demand.getDemand_points().size();
        number_relevant_demand_points = numberRelevantDemandPoints(existing_ptn, demand, radius, distance);
        feasible_sl = (number_relevant_demand_points == numberCoveredDemandPoints(current_ptn, demand, radius, distance));

        if (sl_extended) {

            max_distance = 0;
            double min_distance;
            for (DemandPoint demand_point : demand.getDemand_points()) {
                min_distance = Double.MAX_VALUE;
                for (Stop stop : current_ptn.getStops()) {
                    if (distance.calcDist(stop, demand_point) < min_distance)
                        min_distance = distance.calcDist(stop, demand_point);
                }
                if (min_distance > max_distance)
                    max_distance = min_distance;
            }

            FiniteDominatingSet fds = new FiniteDominatingSet(existing_ptn, demand, distance, radius, false, false);
            number_candidates = fds.getNumberOfCandidates();

        }

    }


    private static void writeStatistic() {
        logger.debug("Write statistic file...");

        //PTN
        if (od_exists) {
            statistic.put("ptn_time_average", time_average);
            statistic.put("ptn_feasible_od", feasible_od);
        }
        statistic.put("ptn_obj_stops", number_current_stops);
        statistic.put("ptn_prop_edges", number_current_edges);

        //SL
        if (!ptn_only) {
            statistic.put("ptn_prop_existing_stops", number_existing_stops);
            statistic.put("ptn_prop_existing_edges", number_existing_edges);
            statistic.put("ptn_prop_demand_points", number_demand_points);
            statistic.put("ptn_prop_relevant_demand_points", number_relevant_demand_points);
            statistic.put("ptn_feasible_sl", feasible_sl);
            statistic.put("ptn_travel_time_realistic", travel_time_realistic);
            statistic.put("ptn_travel_time_const", travel_time_const);

            //sl_extended
            if (sl_extended) {
                statistic.put("ptn_max_distance", max_distance);
                statistic.put("ptn_candidates", number_candidates);
            }
        }

        new StatisticWriter.Builder().setStatistic(statistic).build().write();
        logger.debug("done!");
    }


//helper-------------------------------------------------------------------------------------------------

    private static int numberRelevantDemandPoints(PTN ptn, Demand demand, double radius, Distance distance) {
        int count = 0;
        boolean can_be_covered;
        for (DemandPoint demand_point : demand.getDemand_points()) {
            can_be_covered = false;
            for (Stop stop : ptn.getStops()) {
                if (demand_point.isCoveredBy(stop, radius, distance)) {
                    can_be_covered = true;
                    break;
                }
            }
            if (!can_be_covered) {
                for (Edge edge : ptn.getEdges()) {
                    if (distance.candidateOnEdge(demand_point, edge, radius) != null
                        && !distance.candidateOnEdge(demand_point, edge, radius).isEmpty()) {
                        can_be_covered = true;
                        break;
                    }
                }
            }
            if (can_be_covered)
                count++;
        }
        return count;
    }

    private static int numberCoveredDemandPoints(PTN ptn, Demand demand, double radius, Distance distance) {
        int count = 0;
        for (DemandPoint demand_point : demand.getDemand_points()) {
            for (Stop stop : ptn.getStops()) {
                if (demand_point.isCoveredBy(stop, radius, distance)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static void calcPtnTimeAverage() {

        class SPNode {
            private Stop stop;

            SPNode(Stop stop) {
                this.stop = stop;
            }
        }

        class SPEdge {
            private SPNode start;
            private SPNode end;
            private double weight;

            SPEdge(SPNode start, SPNode end, double weight) {
                this.start = start;
                this.end = end;
                this.weight = weight;
            }
        }

        HashMap<Stop, SPNode> stop_begin = new HashMap<Stop, SPNode>();
        HashMap<Stop, SPNode> stop_end = new HashMap<Stop, SPNode>();
        LinkedList<SPEdge> edges = new LinkedList<SPEdge>();
        SPNode start;
        SPNode end;

        //waiting_edges + splitted stops
        for (Stop stop : current_ptn.getStops()) {
            start = new SPNode(stop);
            stop_begin.put(stop, start);
            end = new SPNode(stop);
            stop_end.put(stop, end);
            edges.add(new SPEdge(start, end, waiting_time));
        }

        //edges
        //length is calculated using TravelingTime
        for (Edge edge : current_ptn.getEdges()) {
            edges.add(new SPEdge(stop_end.get(edge.getLeft_stop()),
                stop_begin.get(edge.getRight_stop()),
                tt_ptn.calcTime(edge.getLength() * conversion_factor_length)));
            if (!directed) {
                edges.add(new SPEdge(stop_end.get(edge.getRight_stop()),
                    stop_begin.get(edge.getLeft_stop()),
                    tt_ptn.calcTime(edge.getLength() * conversion_factor_length)));
            }
        }

        //construct shortest path graph
        ShortestPathsGraph<SPNode, SPEdge> spg = new ShortestPathsGraph<SPNode, SPEdge>();
        for (SPNode stop : stop_begin.values())
            spg.addVertex(stop);
        for (SPNode stop : stop_end.values())
            spg.addVertex(stop);
        for (SPEdge edge : edges)
            spg.addEdge(edge, edge.start, edge.end, edge.weight);

        //compute shortest paths
        feasible_od = true;
        time_average = 0;
        double passengers;
        double total_passengers = 0;
        double length_shortest_path;
        for (SPNode origin : stop_begin.values()) {
            try {
                spg.compute(origin);
                for (SPNode destination : stop_end.values()) {
                    passengers = od.getPassengersAt(origin.stop, destination.stop);
                    total_passengers += passengers;
                    if (passengers > 0) {
                        length_shortest_path = spg.getDistance(destination);
                        if (length_shortest_path == Double.POSITIVE_INFINITY) {
                            feasible_od = false;
                            time_average = Double.POSITIVE_INFINITY;
                            return;
                        }
                        time_average += passengers * length_shortest_path;
                    }
                }
            } catch (Exception e) {
                feasible_od = false;
                time_average = Double.POSITIVE_INFINITY;
                return;
            }
        }
        if (time_average != Double.POSITIVE_INFINITY)
            time_average /= total_passengers;
    }
}
