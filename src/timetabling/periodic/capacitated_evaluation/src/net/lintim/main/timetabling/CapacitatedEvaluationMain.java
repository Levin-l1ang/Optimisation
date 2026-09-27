package net.lintim.main.timetabling;

import net.lintim.algorithm.timetabling.CapacitatedRoutingGurobi;
import net.lintim.algorithm.timetabling.CapacitatedRoutingCore;
import net.lintim.evaluation.timetabling.RoutingEvaluator;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.InputFileException;
import net.lintim.exception.LinTimException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;
import net.lintim.util.Statistic;
import net.lintim.util.timetabling.RoutingNetworkBuilder;

/**
 */
public class CapacitatedEvaluationMain {
	public static void main(String[] args) {
		Logger logger = new Logger(CapacitatedEvaluationMain.class.getCanonicalName());
		logger.info("Begin reading configuration");
		if (args.length != 1) {
			throw new ConfigNoFileNameGivenException();
		}
		new ConfigReader.Builder(args[0]).build().read();
		try {
			new StatisticReader.Builder().build().read();
		}
		catch (InputFileException exc){
			logger.debug("Could not read statistic file, maybe it does not exist");
		}
		int capacity = Config.getIntegerValueStatic("gen_passengers_per_vehicle");
		SolverParameters solverParameters = new SolverParameters(Config.getDefaultConfig(), "tim_");
		logger.info("Finished reading configuration");

		logger.info("Begin reading input files");
		Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
		OD od = new ODReader.Builder(ptn.getNodes().size()).build().read();
		boolean timetable = Config.getBooleanValueStatic("tim_cap_eval_tt");
		Graph<PeriodicEvent, PeriodicActivity> ean;
		if (timetable){
			ean = new PeriodicEANReader.Builder().readTimetable(true).build().read().getFirstElement();
		}
		else {
			ean = new PeriodicEANReader.Builder().readTimetable(false).build().read().getFirstElement();
		}

		logger.info("Finished reading input files");

		logger.info("Begin capacitated evaluation of periodic timetable");
		RoutingNetworkBuilder.enrichEan(ean, ptn);
        try {
            switch (solverParameters.getSolverType()) {
                case GUROBI:
                    logger.debug("Will use Gurobi for optimization");
                    CapacitatedRoutingGurobi.routePassengers(ean, ptn, od, Config.getDefaultConfig(), solverParameters);
                default:
                    logger.debug("Will use solver agnostic version for optimization");
                    CapacitatedRoutingCore.routePassengers(ean, ptn, od, Config.getDefaultConfig(), solverParameters);
            }
        } catch (Exception e) {
            logger.info("Could not load solver " + solverParameters.getSolverType() + ", can you use it on your system?");
            throw new LinTimException(e.getMessage());
        }
		RoutingEvaluator.evaluateRouting(ean, Statistic.getDefaultStatistic(), Config.getDefaultConfig(), od.computeNumberOfPassengers());
		logger.info("Finished capacitated evaluation of periodic timetable");

		logger.info("Begin writing output data");
		new StatisticWriter.Builder().build().write();
		logger.info("Finished writing output data");
	}
}
