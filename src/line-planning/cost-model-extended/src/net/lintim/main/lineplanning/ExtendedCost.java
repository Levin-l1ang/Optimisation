package net.lintim.main.lineplanning;

import net.lintim.algorithm.lineplanning.ExtendedCostSolver;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.LineCapacityReader;
import net.lintim.io.LineReader;
import net.lintim.io.LineWriter;
import net.lintim.io.PTNReader;
import net.lintim.model.*;
import net.lintim.util.*;
import net.lintim.util.lineplanning.Parameters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.lintim.util.LinePlanningHelper.setFrequencies;

/**
 * Main class to solve the cost model of line planning.
 */
public class ExtendedCost {

    private static final Logger logger = new Logger(ExtendedCost.class);

    public static void main(String[] args) {

        logger.info("Begin reading configuration");
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().readLoads(true).build().read();
        LinePool linePool = new LineReader.Builder(ptn).build().read();
        LinePool fixedLines = null;
        Map<Line, Integer> fixedLineCapacities = null;
        if (parameters.respectFixedLines()) {
            fixedLines = new LineReader.Builder(ptn).setLineFileName(parameters.getFixedLinesFileName())
                .readFrequencies(true)
                .readCosts(false).build().read();
            fixedLineCapacities = new LineCapacityReader.Builder(fixedLines).build().read();
            LinePlanningHelper.preprocessForFixedLines(linePool, fixedLineCapacities, parameters.getPassengerPerVehicle());
        }
        if (parameters.respectForbiddenLinks()) {
            Collection<Link> forbiddenLinks = new PTNReader.Builder()
                .setLinkFileName(parameters.getForbiddenLinksFileName())
                .build()
                .read()
                .getEdges();
            int infeasibleEdges = 0;
            for (Link forbiddenLink : forbiddenLinks) {
                logger.debug("Forbidding link " + forbiddenLink);
                Link correspondingPtnLink = ptn.getEdge(forbiddenLink.getId());
                if (correspondingPtnLink == null) {
                    continue;
                }
                correspondingPtnLink.setUpperFrequencyBound(0);
                if (ptn.getEdge(forbiddenLink.getId()).getLowerFrequencyBound() > 0) {
                    ptn.getEdge(forbiddenLink.getId()).setLowerFrequencyBound(0);
                    logger.warn("Forbidden edge " + forbiddenLink.getId() + " is not covered!");
                    infeasibleEdges += 1;
                }
            }
            logger.warn("Found " + infeasibleEdges + " forbidden Edges that were not covered! Will ignore those for the computation");
        }
        logger.info("Finished reading input data");

        logger.info("Begin execution of line planning extended cost model");
        ExtendedCostSolver solver = ExtendedCostSolver.getExtendedCostSolver(parameters.getSolverType());
        // Solve the problem for all possible system frequencies and determine the best
        Set<Integer> possibleSystemFrequencies = LinePlanningHelper.determinePossibleSystemFrequencies
            (parameters.getCommonFrequencyDivisor(), parameters.getPeriodLength());
        boolean optimalSolutionFound = false;
        double bestObjective = Double.POSITIVE_INFINITY;
        int bestCommonFrequency = -1;
        int originalCommonFrequencyDivisor = parameters.getCommonFrequencyDivisor();
        double objective;
        HashMap<Integer, Integer> bestFrequencies = null;
        for (int commonFrequency : possibleSystemFrequencies) {
            parameters.setCommonFrequencyDivisor(commonFrequency);
            if (solver.solveLinePlanningCost(ptn, linePool, parameters)) {
                optimalSolutionFound = true;
                objective = getCost(linePool);
                if (objective < bestObjective) {
                    bestObjective = objective;
                    bestFrequencies = LinePlanningHelper.getFrequencies(linePool);
                    bestCommonFrequency = commonFrequency;
                }
            }
        }
        // Reset frequencies in linepool
        for (Line line : linePool.getLines()) {
            line.setFrequency(0);
        }
        logger.info("Finished execution of line planning extended cost model");

        if (!optimalSolutionFound) {
            throw new AlgorithmStoppingCriterionException("cost model line planning");
        }

        setFrequencies(linePool, bestFrequencies);
        if (originalCommonFrequencyDivisor == -1) {
            logger.debug("Found optimal system frequency " + bestCommonFrequency);
        }
        if (parameters.respectFixedLines()) {
            LinePlanningHelper.postProcessForFixedLines(linePool, fixedLineCapacities.keySet());
        }

        logger.info("Begin writing output data");
        new LineWriter.Builder(linePool).build().write();
        logger.info("Finished writing output data");
    }

    /**
     * Get the cost of the line concept. The cost of a line is multiplied by its frequency and then summed up.
     *
     * @param lineConcept the line concept to calculate the costs of.
     * @return the costs of the line concept.
     */
    private static double getCost(LinePool lineConcept) {
        return lineConcept.getLines().stream().mapToDouble(line -> line.getFrequency() * line.getCost()).sum();
    }
}
