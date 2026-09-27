package net.lintim.linepool.algorithm;

import net.lintim.linepool.util.Parameters;
import net.lintim.model.*;
import net.lintim.util.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompleteTerminalLinePool {

    private static final Logger logger = new Logger(CompleteTerminalLinePool.class.getCanonicalName());

    private final Graph<Stop, Link> ptn;
    private final Set<Integer> terminals;
    private final Parameters parameters;

    public CompleteTerminalLinePool(Graph<Stop, Link> ptn, Set<Integer> terminals, Parameters parameters) {
        this.ptn = ptn;
        this.terminals = terminals;
        this.parameters = parameters;
    }

    private LinePool createLinePool() {
        // TODO: Use the Stop/Link types from the beginning? Or create a graph based on ints and
        // only convert in the end?

        // Idea: Create directed graph (even for undirected ptn) and use ALlDirectedPaths to compute all
        // directed paths between terminals/all stops. Convert these paths to lines and make them undirected, when the
        // corresponding ptn was undirected. For undirected ptn, we only need to compute one direction for all terminals,
        // e.g. only when terminal_1 < terminal_2
        org.jgrapht.Graph<Integer, Integer> graph = new SimpleDirectedGraph<>(Integer.class);
        for (Stop stop: ptn.getNodes()) {
            graph.addVertex(stop.getId());
        }
        for (Link link: ptn.getEdges()) {
            graph.addEdge(link.getLeftNode().getId(), link.getRightNode().getId(), link.getId());
            if (!link.isDirected()) {
                graph.addEdge(link.getRightNode().getId(), link.getLeftNode().getId(), -1 * link.getId());
            }
        }
        AllDirectedPaths<Integer, Integer> algo = new AllDirectedPaths<>(graph);
        Set<GraphPath<Integer, Integer>> allPaths = new HashSet<>();
        for (int origin: terminals) {
            logger.debug("Processing paths starting at " + origin);
            for (int destination: terminals) {
                if (origin == destination) {
                    continue;
                }
                if (!ptn.isDirected() && origin > destination) {
                    continue;
                }
                logger.debug("Destination " + destination);
                List<GraphPath<Integer, Integer>> paths = algo.getAllPaths(origin, destination, true, null);
                for (GraphPath<Integer, Integer> path: paths) {
                    logger.debug("Found path " + path);
                }
                allPaths.addAll(paths);
            }
        }
        LinePool completeLinePool = new LinePool();
        int nextLineId = 1;
        logger.debug("Processing for lines");
        for (GraphPath<Integer, Integer> path: allPaths) {
            logger.debug("Found path " + path);
            Line line = new Line(nextLineId, parameters.getFixedLineCosts(), ptn.isDirected());
            nextLineId += 1;
            for (Integer linkId: path.getEdgeList()) {
                line.addLink(ptn.getEdge(Math.abs(linkId)), parameters.getCostPerLength(), parameters.getCostPerEdge());
            }
            completeLinePool.addLine(line);
        }
        return completeLinePool;
    }

    public static LinePool computeCompleteLinePool(Graph<Stop, Link> ptn, Set<Integer> terminals, Parameters parameters) {
        CompleteTerminalLinePool algo = new CompleteTerminalLinePool(ptn, terminals, parameters);
        return algo.createLinePool();
    }
}
