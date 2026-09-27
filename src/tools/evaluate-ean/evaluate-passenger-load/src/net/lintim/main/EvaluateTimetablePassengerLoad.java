package net.lintim.main;

import net.lintim.evaluate.TimetablePassengerLoadEvaluator;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.InputFileException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Pair;
import net.lintim.util.Statistic;

import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Evaluate the passenger load in the ean. Will write the maximal load factor to the statistic, all invalid loads to
 * a file and output the maximal value to the screen.
 */
public class EvaluateTimetablePassengerLoad {
	private static final Logger logger = Logger.getLogger(EvaluateTimetablePassengerLoad.class.getCanonicalName());

	public static void main(String[] args) {
		if (args.length != 1) {
			throw new ConfigNoFileNameGivenException();
		}
		logger.log(LogLevel.INFO, "Begin reading configuration");
		new ConfigReader.Builder(args[0].trim()).build().read();
		int capacity = Config.getIntegerValueStatic("gen_passengers_per_vehicle");
		logger.log(LogLevel.INFO, "Finished reading configuration");

		logger.log(LogLevel.INFO, "Begin reading input data");
		Graph<Stop, Link> ptn = new PTNReader.Builder().readLoads(true).build().read();
		Graph<PeriodicEvent, PeriodicActivity> periodicEan = new PeriodicEANReader.Builder().build().read().getFirstElement();
		LinePool lineConcept = new LineReader.Builder(ptn).readCosts(false).readFrequencies(true).build().read();
		logger.log(LogLevel.INFO, "Finished reading input data");

		logger.log(LogLevel.INFO, "Begin evaluating the timetable ptn loads");
		Pair<HashMap<Link, Pair<Double, Integer>>, Double> result = TimetablePassengerLoadEvaluator.evaluate(ptn,
				periodicEan, lineConcept, capacity);
		HashMap<Link, Pair<Double, Integer>> loads = result.getFirstElement();
		double returnValue = (double) Math.round(result.getSecondElement() * 100) / 100;
		Statistic.putStatic("ean_max_load_factor", returnValue);
		logger.log(LogLevel.INFO, "Finished evaluating the timetable ptn loads");

		logger.log(LogLevel.INFO, "Begin writing output data");
		if (loads.size() > 0) {
			new TimetablePassengerLoadWriter.Builder(loads).build().write();
		}
		try {
			new StatisticReader.Builder().build().read();
		} catch(InputFileException ignored) {
			logger.log(LogLevel.DEBUG, "No statistic file found");
		}
		new StatisticWriter.Builder().build().write();
		logger.log(LogLevel.INFO, "Finished writing output data");
	}
}
