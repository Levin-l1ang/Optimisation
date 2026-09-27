import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.*;
import java.lang.*;

public class SolveDSLTT {

    private static final Logger logger = new Logger(SolveDSLTT.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        try {
            logger.info("Begin reading configuration");
            Config config = new ConfigReader.Builder(args[0]).build().read();
            DSLTTParameters parameters = new DSLTTParameters(config);
            logger.info("Finished reading configuration");

            logger.debug("Set variables... ");

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

            TravelingTime tt = new TravelingTime(parameters.getAcceleration(), parameters.getSpeed(),
                parameters.getDeceleration(), true);

            logger.info("Begin reading input data");
            File existing_stop_file = new File(config.getStringValue("default_existing_stop_file"));
            File existing_edge_file = new File(config.getStringValue("default_existing_edge_file"));
            File demand_file = new File(config.getStringValue("default_demand_file"));

            PTN ptn = new PTN(parameters.isDirectedPtn());
            Demand demand = new Demand();

            PTNCSV.fromFile(ptn, existing_stop_file, existing_edge_file);
            DemandCSV.fromFile(demand, demand_file);
            logger.info("Finished reading input data");

            logger.info("Begin computing of dsl tt stop location");

            if (parameters.isDestructionAllowed())
                ptn.removeDestructableStations();

            logger.debug("Calculate fds...");
            FiniteDominatingSet fds = new FiniteDominatingSet(ptn, demand, distance, parameters.getRadius(),
                parameters.isDestructionAllowed(), false);
            logger.debug("done!");

            logger.debug("The number of candidates is: ");
            logger.debug(String.valueOf(fds.getNumberOfCandidates()));

            logger.debug("Calculate Candidate Edges...");
            CandidateEdgeSet ces = new CandidateEdgeSet(ptn, fds);
            logger.debug("done!");

            logger.debug("The number of candidate edges is: ");
            logger.debug(String.valueOf(ces.getNumberOfCandidateEdges()));

            logger.debug("Setting up IP-Formulation...");
            DSLTT dsltt = new DSLTT(fds, ces, demand, parameters.getWaitingTime(), tt);
            logger.debug("done!");

            logger.debug("Solving IP-Formulation...");
            long time = System.currentTimeMillis();
            dsltt.solve(parameters);
            logger.debug("Done in " + (System.currentTimeMillis() - time) + " milliseconds!");
            logger.info("Begin computing stop location");

            logger.info("Begin writing output data");
            PTNCSV.toFile(dsltt.getNewPTN(), new File(config.getStringValue("default_stops_file")), new File(config.getStringValue("default_edges_file")));
            logger.info("Finished writing output data");

        } catch (IOException e) {
            logger.error("An error occurred while reading a file.");
            throw new RuntimeException(e);
        }
    }

}
