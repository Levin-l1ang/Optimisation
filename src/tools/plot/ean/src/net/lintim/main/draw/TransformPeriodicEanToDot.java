package net.lintim.main.draw;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.PeriodicEANReader;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.draw.EanDotTransformer;

import java.io.File;
import java.util.logging.Logger;

/**
 * Class to draw a periodic ean, with or without timetable. It will produce a .dot-file which can afterwards be drawn
 * using e.g. neato.
 */
public class TransformPeriodicEanToDot {

	private static Logger logger = Logger.getLogger(TransformPeriodicEanToDot.class.getCanonicalName());

	/**
	 * Main method for drawing a periodic ean.
	 * @param args only one argument, the name of the config file to read.
	 */
	public static void main(String[] args) {
		logger.log(LogLevel.INFO, "Begin reading configuration");
		if (args.length != 1) {
			throw new ConfigNoFileNameGivenException();
		}
		new ConfigReader.Builder(args[0]).build().read();
		String dotFile = Config.getStringValueStatic("filename_periodic_ean_dot_file");
		String timetableFileName = Config.getStringValueStatic("default_timetable_periodic_file");
		int periodLength = Config.getIntegerValueStatic("period_length");
		logger.log(LogLevel.INFO, "Finished reading configuration");

		logger.log(LogLevel.INFO, "Begin reading input data");
		// Check if a periodic timetable exists. If it exists, we want to read it as well
		File timetableFile = new File(timetableFileName);
		Graph<PeriodicEvent, PeriodicActivity> ean;
		boolean readTimetable = timetableFile.exists() && ! timetableFile.isDirectory();
		logger.log(LogLevel.DEBUG, "Read timetable: " + readTimetable);
		ean = new PeriodicEANReader.Builder().readTimetable(readTimetable).build().read().getFirstElement();
		logger.log(LogLevel.INFO, "Finished reading input data");

		logger.log(LogLevel.INFO, "Begin writing output data");
		EanDotTransformer.writePeriodicDotFile(ean, readTimetable, periodLength, dotFile);
		logger.log(LogLevel.INFO, "Finished writing output data");
	}
}
