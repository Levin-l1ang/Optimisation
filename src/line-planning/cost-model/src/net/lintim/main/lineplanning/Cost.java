package net.lintim.main.lineplanning;

import net.lintim.algorithm.lineplanning.LinePlanningCostSolver;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.Graph;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;
import net.lintim.util.Logger;

/**
 * Main class to solve the cost model of line planning.
 */
public class Cost {

    private static final Logger logger = new Logger(Cost.class);

    public static void main(String[] args) {
        logger.info("Begin reading configuration");
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        SolverParameters parameters = new SolverParameters(config, "lc_");
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().readLoads(true).build().read();
        LinePool linePool = new LineReader.Builder(ptn).build().read();
        logger.info("Finished reading input data");

        logger.info("Begin execution of line planning cost model");
        LinePlanningCostSolver solver = LinePlanningCostSolver.getLinePlanningCostSolver(parameters.getSolverType());
        boolean optimalSolutionFound = solver.solveLinePlanningCost(ptn, linePool, parameters);
        logger.info("Finished execution of line planning cost model");

        if (!optimalSolutionFound) {
            throw new AlgorithmStoppingCriterionException("cost model line planning");
        }

        logger.info("Begin writing output data");
        new LineWriter.Builder(linePool).build().write();
        logger.info("Finished writing output data");
    }
}
