package net.lintim.main.lineplanning;

import net.lintim.algorithm.lineplanning.DirectRestrictingFrequenciesSolver;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.lineplanning.Parameters;


/**
 * Implementation of initialization code for the direct traveller model in line planning. Will use
 * {@link DirectRestrictingFrequenciesSolver#solveLinePlanningDirect(Graph, OD, LinePool, Parameters)} for
 * the actual computation. This class is only the entry point and responsible for all the setup, e.g. reading and
 * writing.
 */
public class DirectRestrictingFrequencies {

    private static final Logger logger = new Logger(DirectRestrictingFrequencies.class);

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
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");
        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().readLoads(true).build().read();
        LinePool linePool = new LineReader.Builder(ptn).build().read();
        OD od = new ODReader.Builder(ptn.getNodes().size()).build().read();
        logger.info("Finished reading input data");
        logger.info("Begin execution of line planning direct model restricting frequencies");
        DirectRestrictingFrequenciesSolver solver = DirectRestrictingFrequenciesSolver.getDirectRestrictingFrequenciesSolver(parameters.getSolverType());
        boolean feasibleSolutionFound = solver.solveLinePlanningDirect(ptn, od, linePool, parameters);
        logger.info("Finished execution of line planning direct model restricting frequencies");
        if (!feasibleSolutionFound) {
            throw new AlgorithmStoppingCriterionException("direct model line planning restricting frequencies");
        }
        logger.info("Begin writing output data");
        new LineWriter.Builder(linePool).build().write();
        logger.info("Finished writing output data");
    }
}
