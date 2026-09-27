package net.lintim.algorithm.tools;

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
import net.lintim.util.Logger;
import net.lintim.util.tools.WalkingPreprocessorParameters;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WalkingPreprocessor {

    private final static Logger logger = new Logger(WalkingPreprocessor.class.getCanonicalName());

    public static Graph<InfrastructureNode, WalkingEdge> preprocess(Graph<InfrastructureNode, WalkingEdge> originalGraph,
                                  OD nodeOd, WalkingPreprocessorParameters parameters) {


        Graph<InfrastructureNode, WalkingEdge> preprocessedGraph = new SimpleMapGraph<>();
        for (InfrastructureNode node: originalGraph.getNodes()) {
            preprocessedGraph.addNode(node);
            // Is there any demand for this node, i.e., do we want to start or end walking here?
            boolean foundDemand = false;
            for (InfrastructureNode destination: originalGraph.getNodes()) {
                if (node.equals(destination)) {
                    continue;
                }
                if (nodeOd.getValue(node.getId(), destination.getId()) > 0) {
                    foundDemand = true;
                    break;
                }
            }
            if (!foundDemand) {
                // There was no demand starting at node, we therefore don't need any walking edges starting there
                continue;
            }
            // Now determine where we may go from here
            List<WalkingEdge> possibleEdges = originalGraph.getOutgoingEdges(node).stream()
                .filter(e -> e.getOtherSide(node).isStopPossible())
                .sorted(Comparator.comparingDouble(WalkingEdge::getLength))
                .collect(Collectors.toList());
            // Now add the valid edges into the new graph
            int numberOfEdgesAdded = 0;
            double minimalDistance = possibleEdges.get(0).getLength();
            for (WalkingEdge edge: possibleEdges) {
                if (parameters.getMaxWalkingAmount() > 0 && parameters.getMaxWalkingAmount() <= numberOfEdgesAdded ||
                parameters.getMaxWalkingRatio() > 0 && parameters.getMaxWalkingRatio() < edge.getLength() / minimalDistance ||
                parameters.getMaxWalkingTime() > 0 && parameters.getMaxWalkingTime() < edge.getLength()) {
                    break;
                }
                preprocessedGraph.addEdge(edge);
                numberOfEdgesAdded += 1;
            }
        }
        preprocessedGraph.orderEdges(Comparator.comparingInt(WalkingEdge::getId));
        return preprocessedGraph;
    }
}
