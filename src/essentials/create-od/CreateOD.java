import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.File;
import java.io.IOException;


public class CreateOD {

    private static final Logger logger = new Logger(CreateOD.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        try {

            logger.info("Begin reading configuration");
            Config config = new ConfigReader.Builder(args[0]).build().read();
            boolean directed = !config.getBooleanValue("ptn_is_undirected");
            double radius = config.getDoubleValue("sl_radius");

            Distance distance;
            if (config.getStringValue("sl_distance").equals("euclidean_norm")) {
                distance = new EuclideanNorm();
            } else {
                throw new IOException("Distance not defined.");
            }

            double ptn_speed = config.getDoubleValue("gen_vehicle_speed");
            double conversion_factor_length = config.getDoubleValue("gen_conversion_length");
            double conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates");
            double waiting_time = config.getDoubleValue("ptn_stop_waiting_time");
            double od_network_acceleration = config.getDoubleValue("od_network_acceleration");
            boolean use_network_distance = config.getBooleanValue("od_use_network_distance");
            boolean remove_uncovered_demand_points = config.getBooleanValue("od_remove_uncovered_demand_points");

            ODCSV.setHeader(config.getStringValue("od_header"));
            logger.info("Finished reading configuration");

            logger.info("Begin reading input data");
            File stop_file = new File(config.getStringValue("default_stops_file"));
            File edge_file = new File(config.getStringValue("default_edges_file"));
            File demand_file = new File(config.getStringValue("default_demand_file"));

            PTN ptn = new PTN(directed);
            Demand demand = new Demand();

            PTNCSV.fromFile(ptn, stop_file, edge_file);
            DemandCSV.fromFile(demand, demand_file);
            logger.info("Finished reading input data");

            logger.info("Begin computing od matrix");
            if (remove_uncovered_demand_points)
                demand.removeUncoveredDemandPoint(ptn, distance, radius);
            PassengerDistribution passenger_distribution =
                new PassengerDistribution(demand, ptn, distance, od_network_acceleration, waiting_time,
                    conversion_factor_length, conversion_factor_coordinates, ptn_speed, use_network_distance);
            OD od_matrix = passenger_distribution.createOD();
            logger.info("Finished computing od matrix");

            logger.info("Begin writing output data");
            ODCSV.toFile(od_matrix, ptn, new File(config.getStringValue("default_od_file")));
            logger.info("Finished writing output data");

        } catch (IOException e) {
            logger.error("An error occurred while reading a file.");
            throw new RuntimeException(e);
        }
    }

}
