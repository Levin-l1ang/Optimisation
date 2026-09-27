package net.lintim.main.tools;

import net.lintim.algorithm.tools.WalkingPreprocessor;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.InfrastructureReader;
import net.lintim.io.InfrastructureWriter;
import net.lintim.io.ODReader;
import net.lintim.model.Graph;
import net.lintim.model.InfrastructureNode;
import net.lintim.model.OD;
import net.lintim.model.WalkingEdge;
import net.lintim.model.impl.MapOD;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;
import net.lintim.util.GraphHelper;
import net.lintim.util.Logger;
import net.lintim.util.tools.WalkingPreprocessorParameters;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WalkingPreprocessorMain {

    private static final Logger logger = new Logger(WalkingPreprocessorMain.class.getCanonicalName());

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        WalkingPreprocessorParameters parameters = new WalkingPreprocessorParameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<InfrastructureNode, WalkingEdge> walkingGraph = new InfrastructureReader.Builder()
            .readInfrastructureEdges(false).build().read().getSecondElement();
        OD nodeOD = new ODReader.Builder(new MapOD()).readNodeOd(true).build().read();
        logger.debug("Read walking graph, has " + walkingGraph.getNodes().size() + " nodes and " + walkingGraph.getEdges().size() + " edges.");
        logger.info("Finished reading input data");

        logger.info("Begin preprocessing walking graph");
        Graph<InfrastructureNode, WalkingEdge> preprocessedGraph = WalkingPreprocessor.preprocess(walkingGraph, nodeOD,
            parameters);
        logger.debug("Preprocessed graph, has " + preprocessedGraph.getNodes().size() + " nodes and " + preprocessedGraph.getEdges().size() + " edges.");
        logger.info("Finished preprocessing walking graph");

        logger.info("Begin writing output data");
        new InfrastructureWriter.Builder(null, preprocessedGraph).writeInfrastructureEdges(false).build().write();
        logger.info("Finished writing output data");


    }
}
