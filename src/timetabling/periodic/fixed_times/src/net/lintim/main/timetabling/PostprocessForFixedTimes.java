package net.lintim.main.timetabling;

import net.lintim.algorithm.timetabling.EanPostprocessor;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.PeriodicEANReader;
import net.lintim.io.PeriodicEANWriter;
import net.lintim.io.timetabling.FixedTimesReader;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.util.Map;

/**
 * Main class for postprocessing a ean with fixed time bounds
 */
public class PostprocessForFixedTimes {
	private static Logger logger = new Logger(PostprocessForFixedTimes.class.getCanonicalName());

	public static void main(String[] args) {
		if (args.length < 1) {
			throw new ConfigNoFileNameGivenException();
		}
		logger.info("Begin reading configuration");
		new ConfigReader.Builder(args[0]).build().read();
		logger.info("Finished reading configuration");
		int periodLength = Config.getIntegerValueStatic("period_length");
		logger.info("Begin reading input files");
		Graph<PeriodicEvent, PeriodicActivity> ean =  new
				PeriodicEANReader.Builder().readTimetable(true).build().read().getFirstElement();
		Map<PeriodicEvent, Pair<Integer, Integer>> eventTimeBounds = new FixedTimesReader.Builder(ean).build().read();
		logger.info("Finished reading input files");
		logger.info("Begin postprocessing ean for fixed times");
		EanPostprocessor.postprocessEan(ean, periodLength);
		boolean feasible = EanPostprocessor.checkTimeBounds(eventTimeBounds, periodLength);
		logger.info("Finished postprocessing ean for fixed times");
		logger.info("Begin writing output files");
		if (!feasible) {
			logger.warn("Writing ean inconsistent with given time bounds");
		}
		new PeriodicEANWriter.Builder(ean).build().write();
		logger.info("Finished writing output files");
	}
}
