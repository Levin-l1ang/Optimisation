package net.lintim.main.od;

import net.lintim.algorithm.od.ODGenerator;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.od.EdgeWeightProcessor;
import net.lintim.util.SolverType;

public class ODFromNodesMain {

    private static Logger logger = new Logger(ODFromNodesMain.class.getCanonicalName());

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();

        double waitingTime = EdgeWeightProcessor.getWaitingTime(config);
        EdgeWeightProcessor.DRIVE_WEIGHT driveModel = EdgeWeightProcessor.getDriveWeight(config);
        double walkingUtility = config.getDoubleValue("gen_walking_utility");
        String odNodeFileName = config.getStringValue("filename_od_nodes_file");
        boolean writeAssignment = config.getBooleanValue("od_node_write_assignment");
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
        Graph<InfrastructureNode, WalkingEdge> walkingGraph = new InfrastructureReader.Builder()
            .readInfrastructureEdges(false).build().read().getSecondElement();

        logger.debug("Read walking graph with " + walkingGraph.getNodes().size() + " nodes and " + walkingGraph.getEdges().size() + " edges.");

        OD nodeOd = new ODReader.Builder(new MapOD()).setFileName(odNodeFileName).build().read();
        logger.info("Finished reading input data");

        logger.info("Begin computing od matrix");
        OD stopOd = ODGenerator.generateOD(ptn, walkingGraph, waitingTime, driveModel, walkingUtility, nodeOd, writeAssignment);
        logger.debug("Number of passengers: " + stopOd.computeNumberOfPassengers());
        logger.info("Finished computing od matrix");

        logger.info("Begin writing output data");
        new ODWriter.Builder(stopOd, ptn).build().write();
        logger.info("Finished writing output data");
    }
}
