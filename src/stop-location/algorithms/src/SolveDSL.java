import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.*;
import java.lang.*;
import java.util.*;

public class SolveDSL {

    private static final Logger logger = new Logger(SolveDSL.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        try {

            logger.info("Begin reading configuration");
            Config config = new ConfigReader.Builder(args[0]).build().read();
            DSLParameters parameters = new DSLParameters(config);
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

            logger.info("Begin reading input data");
            File existing_stop_file = new File(config.getStringValue("default_existing_stop_file"));
            File existing_edge_file = new File(config.getStringValue("default_existing_edge_file"));
            File demand_file = new File(config.getStringValue("default_demand_file"));

            PTN ptn = new PTN(parameters.isDirectedPtn());
            Demand demand = new Demand();

            PTNCSV.fromFile(ptn, existing_stop_file, existing_edge_file);
            DemandCSV.fromFile(demand, demand_file);
            logger.info("Finished reading input data");

            logger.info("Begin computing dsl stop location solution");

            if (parameters.isDestructionAllowed())
                ptn.removeDestructableStations();

            logger.debug("Calculate fds...");
            FiniteDominatingSet fds = new FiniteDominatingSet(ptn, demand, distance, parameters.getRadius(),
                parameters.isDestructionAllowed(), false);
            logger.debug("done!");

            logger.debug("The number of candidates is: ");
            logger.debug(String.valueOf(fds.getNumberOfCandidates()));

            logger.debug("Setting up IP-Formulation...");
            DSL dsl = new DSL(fds, demand);
            logger.debug("done!");

            logger.debug("Solving IP-Formulation...");
            long time = System.currentTimeMillis();
            dsl.solve(parameters);
            logger.debug("Done in " + (System.currentTimeMillis() - time) + " milliseconds!");


            LinkedList<Stop> stopsToRemove = new LinkedList<>();
            LinkedList<Edge> edgesToRemove = new LinkedList<>();
            boolean isRemove;
            for (Stop stop : ptn.getStops()) {
                isRemove = true;
                for (Candidate candidate : dsl.getBuiltCandidates()) {
                    if (candidate.isVertex() && distance.calcDist(stop, candidate) < Distance.EPSILON) {
                        isRemove = false;
                        break;
                    }
                }
                if (isRemove) {
                    stopsToRemove.add(stop);
                }
            }

            if (dsl.getBuiltCandidates() != null) {
                for (Candidate candidate : dsl.getBuiltCandidates()) {
                    if (!candidate.isVertex())
                        ptn.insertCandidate(candidate);
                }
            }
            for (Stop stop : stopsToRemove)
                for (Edge edge : ptn.getEdges())
                    if (edge.getLeft_stop() == stop || edge.getRight_stop() == stop)
                        edgesToRemove.add(edge);

            ptn.getStops().removeAll(stopsToRemove);
            ptn.getEdges().removeAll(edgesToRemove);

            logger.info("Finished computation of stops");

            logger.info("Begin writing output data");
            PTNCSV.toFile(ptn, new File(config.getStringValue("default_stops_file")), new File(config.getStringValue("default_edges_file")));
            logger.info("Finished writing output data");

        } catch (IOException e) {
            logger.error("An error occurred while reading a file.");
            throw new RuntimeException(e);
        }
    }

}
