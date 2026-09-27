package net.lintim.main.lineplanning;

import net.lintim.algorithm.lineplanning.CostRestrictingFrequenciesSolver;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.LineReader;
import net.lintim.io.LineWriter;
import net.lintim.io.PTNReader;
import net.lintim.model.Graph;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;
import net.lintim.util.lineplanning.Parameters;


/**
 * Main class to solve the cost model with restricted frequencies of line planning.
 */
public class CostRestrictingFrequencies {

    private static final Logger logger = new Logger(CostRestrictingFrequencies.class);

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
        logger.info("Finished reading input data");

        logger.info("Begin execution of line planning cost model restricting frequencies");
        CostRestrictingFrequenciesSolver solver = CostRestrictingFrequenciesSolver.getSolver(parameters.getSolverType());
        boolean feasibleSolutionFound = solver.solveLinePlanningCost(ptn, linePool, parameters);

        logger.info("Finished execution of line planning cost model restricting frequencies");

        if (!feasibleSolutionFound) {
            throw new AlgorithmStoppingCriterionException("cost model line planning");
        }

        logger.info("Begin writing output data");
        new LineWriter.Builder(linePool).build().write();
        logger.info("Finished writing output data");
    }
}
