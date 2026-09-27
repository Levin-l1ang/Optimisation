package net.lintim.main.lineplanning;

import net.lintim.algorithm.lineplanning.LinePlanningDirectSolver;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.util.lineplanning.DirectParameters;
import net.lintim.util.lineplanning.DirectSolutionDescriptor;
import net.lintim.model.*;
import net.lintim.util.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.lintim.util.lineplanning.DirectHelper.computeShortestPaths;

/**
 * Implementation of initialization code for the direct traveller model in line planning. Will use
 * {@link net.lintim.algorithm.lineplanning.DirectGurobi#solveLinePlanningDirect(Graph, OD, LinePool, DirectParameters)} for
 * the actual computation. This class is only the entry point and responsible for all the setup, e.g. reading and
 * writing.
 */
public class Direct {
    private static final Logger logger = new Logger(Direct.class);

    /**
     * Start the computation of a solution to the direct travellers model for line planning. The only command line
     * argument should be the name of the config file to read
     *
     * @param args the command line arguments. Will only works if this only contains one element, the path to the
     *             config file to read
     */
    public static void main(String[] args) {
        logger.info("Begin reading configuration");
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        DirectParameters parameters = new DirectParameters(config);
        logger.info("Finished reading configuration");
        logger.info("Begin reading input data");

        Graph<Stop, Link> ptn = new PTNReader.Builder().readLoads(true).build().read();
        LinePool linePool = new LineReader.Builder(ptn).build().read();
        OD od = new ODReader.Builder(ptn.getNodes().size()).build().read();
        LinePool fixedLines = null;
        Map<Line, Integer> fixedLineCapacities = null;
        if(parameters.respectFixedLines()) {
            fixedLines =
                new LineReader.Builder(ptn)
                    .setLineFileName(parameters.getFixedLinesFileName())
                    .readFrequencies(true)
                    .readCosts(false).build().read();
            fixedLineCapacities = new LineCapacityReader.Builder(fixedLines).build().read();
            LinePlanningHelper.preprocessForFixedLines(linePool, fixedLineCapacities, parameters.getCapacity());
        }
        logger.info("Finished reading input data");
        logger.info("Begin execution of line planning direct model");
        Map<Pair<Integer, Integer>, Collection<Path<Stop, Link>>> preferablePaths = computeShortestPaths(ptn, od,
            parameters);
        LinePlanningDirectSolver solver = LinePlanningDirectSolver.getLinePlanningDirectSolver(parameters.getSolverType());
        if (parameters.respectFixedLines()) {
            LinePlanningHelper.preprocessForFixedLines(linePool, fixedLineCapacities,
                parameters.getCapacity());
            // See which passengers can already be served by the existing lines
            double numberOfPassengers = od.computeNumberOfPassengers();
            boolean success = solver.preprocessPassengersForFixedLines(od, preferablePaths, ptn,
                fixedLines, fixedLineCapacities, parameters);
            if (!success) {
                logger.error("Was unable to preprocess passengers for fixed lines, abort");
                return;
            }
            logger.debug("Reduced number of passengers from " + numberOfPassengers + " to " + od.computeNumberOfPassengers());
        }
        // Solve the problem for all possible system frequencies and determine the best
        Set<Integer> possibleSystemFrequencies = LinePlanningHelper.determinePossibleSystemFrequencies
            (parameters.getCommonFrequencyDivisor(), parameters.getPeriodLength());
        boolean solutionFound = false;
        double bestObjective = Double.NEGATIVE_INFINITY;
        HashMap<Integer, Integer> bestFrequencies = null;
        for (int commonFrequency : possibleSystemFrequencies) {
            parameters.setCommonFrequencyDivisor(commonFrequency);
            DirectSolutionDescriptor solutionWrapper = solver.solveLinePlanningDirect(ptn, od, linePool, parameters);
            if (solutionWrapper.isFeasible()) {
                solutionFound = true;
                if (solutionWrapper.getObjectiveValue() > bestObjective) {
                    bestFrequencies = LinePlanningHelper.getFrequencies(linePool);
                    bestObjective = solutionWrapper.getObjectiveValue();
                }
            }
        }
        // Reset frequencies in linepool
        for (Line line: linePool.getLines()) {
            line.setFrequency(0);
        }
        if (parameters.respectFixedLines()) {
            LinePlanningHelper.postProcessForFixedLines(linePool, fixedLineCapacities.keySet());
        }

        logger.info("Finished execution of line planning direct model");
        if (!solutionFound) {
            throw new AlgorithmStoppingCriterionException("direct model line planning");
        }
        LinePlanningHelper.setFrequencies(linePool, bestFrequencies);
        logger.info("Begin writing output data");
        new LineWriter.Builder(linePool).build().write();
        logger.info("Finished writing output data");
    }
}
