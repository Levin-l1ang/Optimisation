package net.lintim.main.timetabling;

import net.lintim.algorithm.timetabling.EanPreProcessor;
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
 * Preprocessing class for timetabling with fixed times
 */
public class PreprocessForFixedTimes {
	private static Logger logger = new Logger(PreprocessForFixedTimes.class.getCanonicalName());

	public static void main(String[] args) {
		if (args.length < 1) {
			throw new ConfigNoFileNameGivenException();
		}
		logger.info("Begin reading configuration");
		new ConfigReader.Builder(args[0]).build().read();
		logger.info("Finished reading configuration");
		logger.info("Begin reading input files");
		int periodLength = Config.getIntegerValueStatic("period_length");
		Graph<PeriodicEvent, PeriodicActivity> ean =  new
				PeriodicEANReader.Builder().build().read().getFirstElement();
		Map<PeriodicEvent, Pair<Integer, Integer>> timeBounds = new FixedTimesReader.Builder(ean).build().read();
		logger.info("Finished reading input files");
		logger.info("Begin preprocessing ean for fixed times");
		EanPreProcessor.preprocessEan(ean, timeBounds, periodLength);
		logger.info("Finished preprocessing ean for fixed times");
		logger.info("Begin writing output files");
		new PeriodicEANWriter.Builder(ean).writeTimetable(false).build().write();
		logger.info("Finished writing output files");
	}
}
