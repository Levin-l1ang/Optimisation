package net.lintim.main.stop_location;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.stop_location.SLHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleSLMain {

    private static final Logger logger = new Logger(SimpleSLMain.class.getCanonicalName());

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        double conversionFactorCoordinates = Config.getDoubleValueStatic("gen_conversion_coordinates");
        boolean readRestrictedTurns = config.getBooleanValue("sl_restricted_turns");
        boolean readForbiddenEdges = config.getBooleanValue("sl_forbidden_edges");
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph =
            new InfrastructureReader.Builder().readWalkingEdges(false).build().read().getFirstElement();
        Set<Pair<Integer, Integer>> restrictedTurns = readRestrictedTurns ? new RestrictedTurnReader.Builder().isInfrastructure(true).build().read() : new HashSet<>();
        Graph<InfrastructureNode, InfrastructureEdge> forbiddenEdges = readForbiddenEdges ?
            new InfrastructureReader.Builder().readWalkingEdges(false).setInfrastructureEdgeFileName(config.getStringValue("filename_forbidden_infrastructure_edges_file")).build().read().getFirstElement()
            : null;
        logger.info("Finished reading input data");

        logger.info("Begin creating stops");
        List<InfrastructureNode> stopsToBuild = infrastructureGraph.getNodes().stream().filter(InfrastructureNode::isStopPossible).collect(Collectors.toList());
        SLHelper.PTNResult result = SLHelper.createPTN(infrastructureGraph, stopsToBuild, conversionFactorCoordinates, restrictedTurns, forbiddenEdges);
        logger.info("Finished creating stops");

        logger.info("Begin writing output data");
        new PTNWriter.Builder(result.getPtn()).build().write();
        if (readRestrictedTurns) {
            new RestrictedTurnWriter.Builder(result.getRestrictedTurns()).build().write();
        }
        if (readForbiddenEdges) {
            new PTNWriter.Builder(result.getForbiddenEdges()).writeStops(false).setLinkFileName(config.getStringValue("filename_forbidden_links_file")).build().write();
        }
        logger.info("Finished writing output data");
    }
}
