import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.*;

public class SolveGreedy {

    private static final Logger logger = new Logger(SolveGreedy.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        try {

            logger.info("Begin reading configuration");
            Config config = new ConfigReader.Builder(args[0]).build().read();
            GreedyParameters parameters = new GreedyParameters(config);
            logger.info("Finished reading configuration");

            logger.debug("Set variables... ");
            Edge.setHeader(config.getStringValue("edges_header"));
            Stop.setHeader(config.getStringValue("stops_header"));


            Candidate.setDefault_name(config.getStringValue("sl_new_stop_default_name"));

            Stop.setHeader(config.getStringValue("stops_header"));
            Edge.setHeader(config.getStringValue("edges_header"));

            Distance distance;
            if (parameters.getDistance().equals("euclidean_norm")) {
                distance = new EuclideanNorm();
            } else {
                throw new IOException("Distance not defined.");
            }
            logger.debug("done!");

            logger.info("Begin reading input data");
            File existing_stop_file = new File(config.getStringValue("default_existing_stop_file"));
            File existing_edge_file = new File(config.getStringValue("default_existing_edge_file"));
            File demand_file = new File(config.getStringValue("default_demand_file"));

            PTN ptn = new PTN(parameters.isDirectedPtn());
            Demand demand = new Demand();

            PTNCSV.fromFile(ptn, existing_stop_file, existing_edge_file);
            DemandCSV.fromFile(demand, demand_file);
            logger.info("Finished reading input data");

            logger.info("Begin computing greed stop location");
            if (parameters.isDestructionAllowed())
                ptn.removeDestructableStations();

            logger.debug("Calculate fds...");
            FiniteDominatingSet fds = new FiniteDominatingSet(ptn, demand, distance, parameters.getRadius(),
                parameters.isDestructionAllowed(), false);
            logger.debug("done!");

            logger.debug("The number of candidates is: ");
            logger.debug(String.valueOf(fds.getNumberOfCandidates()));

            logger.debug("Calculating Covering Matrix...");
            CoveringMatrix matrix = new CoveringMatrix(demand, fds);
            logger.debug("done!");


            logger.debug("Solve SL1 via Greedy...");
            FiniteDominatingSet solution = matrix.solveSL1Greedy();
            logger.debug("done!");

            logger.debug("Check if there was a valid solution...");
            if (solution == null) {
                logger.error("\nNo feasible solution found!\n");
                File stop_file = new File(config.getStringValue("default_stops_file"));
                File edge_file = new File(config.getStringValue("default_edges_file"));
                PTNCSV.toFileInfeasible(stop_file, edge_file);
                return;
            } else {
                logger.debug("done!");
            }

            logger.debug("Calculating new ptn...");
            for (Candidate cand : solution.getCandidates()) {
                if (!cand.isVertex())
                    ptn.insertCandidate(cand);
            }
            logger.info("Finished computation of stop location");

            logger.info("Begin writing output data");
            File stop_file = new File(config.getStringValue("default_stops_file"));
            File edge_file = new File(config.getStringValue("default_edges_file"));
            PTNCSV.toFile(ptn, stop_file, edge_file);
            logger.info("Finished writing output data");
        } catch (IOException e) {
            logger.error("An error occurred while reading a file.");
            throw new RuntimeException(e);
        }
    }

}
