package net.lintim.main.timetabling.periodic;

import net.lintim.algorithm.timetabling.periodic.PespSolver;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.timetabling.periodic.Parameters;

/**
 * Main class for computing a solution for the periodic timetabling problem using a PESP IP solver. This will use a
 * very straight forward ip formulation that is not very efficient!
 */
public class PespIp {
    public static void main(String[] args) {
        Logger logger = new Logger(PespIp.class);
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");


        logger.info("Begin reading input data");
        Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder()
            .readTimetable(parameters.shouldUseOldSolution()).build().read().getFirstElement();
        logger.info("Finished reading input data");
        logger.info("Begin execution of the periodic timetabling pesp ip solver");
        PespSolver solver = PespSolver.getSolver(parameters.getSolverType());
        boolean optimalSolutionFound = solver.solveTimetablingPespModel(ean, parameters);

        if (!optimalSolutionFound) {
            throw new AlgorithmStoppingCriterionException("pesp ip periodic timetabling");
        }

        logger.info("Finished computation of periodic timetable");

        logger.info("Begin writing output data");
        new PeriodicEANWriter.Builder(ean).writeEvents(false).writeActivities(false).build().write();
        new StatisticWriter.Builder().build().write();
        logger.info("Finished writing output data");
    }
}
