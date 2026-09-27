package net.lintim.main.stop_location;

import net.lintim.algorithm.stop_location.TravelTimeModel;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.InfrastructureReader;
import net.lintim.io.ODReader;
import net.lintim.io.PTNWriter;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.SolverType;
import net.lintim.util.stop_location.Parameters;

public class TravelTimeMain {

    private static Logger logger = new Logger(TravelTimeMain.class.getCanonicalName());

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);

        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Pair<Graph<InfrastructureNode, InfrastructureEdge>, Graph<InfrastructureNode, WalkingEdge>> infrastructure =
            new InfrastructureReader.Builder().build().read();
        Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph = infrastructure.getFirstElement();
        Graph<InfrastructureNode, WalkingEdge> walkingGraph = infrastructure.getSecondElement();
        OD od = new ODReader.Builder(new MapOD()).setFileName(config.getStringValue("filename_od_nodes_file")).build().read();
        logger.info("Finished reading input data");

        logger.info("Begin computation of travel time stop location model");
        Graph<Stop, Link> ptn = TravelTimeModel.findSolution(infrastructureGraph, walkingGraph, od, parameters);
        logger.info("Finished computation");

        logger.info("Begin writing output data");
        new PTNWriter.Builder(ptn).build().write();
        logger.info("Finished writing output data");
    }
}
