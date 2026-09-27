package net.lintim.main.tools;

import net.lintim.exception.ConfigKeyNotFoundException;
import net.lintim.io.ConfigReader;
import net.lintim.io.LineReader;
import net.lintim.io.PTNReader;
import net.lintim.io.PeriodicEANReader;
import net.lintim.io.tools.VisumTimetableWriter;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Exporter to provide a VISUM compatible timetable format.
 *
 * Will read the current periodic timetable and write a VISUM-compatible timetable. For this, the timetable will
 * be transformed to a stop-based line concept, including the arrival and departure times of the stops
 */
public class TransformTimetableToVisumMain {
	private static Logger logger = Logger.getLogger("net.lintim.main.tools.TransformTimetableToVisumMain");
	/**
	 * Main method to transform the periodic timetable into the visum import format
	 * @param args args[0] Should be the path to the config file to read
	 */
	public static void main(String[] args) throws IOException {
		logger.log(LogLevel.INFO, "Begin reading configuration");
		if (args.length != 1){
			System.out.println("Usage: Use one parameters, the path to the config file to use");
		}
		new ConfigReader.Builder(args[0]).build().read();
		//Now we can read in the data
		String outputFile = Config.getStringValueStatic("default_timetable_visum_file");
		String outputHeader = Config.getStringValueStatic("timetable_header_visum");
		// Fixed lines should not be in the timetable
		boolean respectFixedLines = Config.getBooleanValueStatic("lc_respect_fixed_lines");
		logger.log(LogLevel.INFO, "Finished reading configuration");

		logger.log(LogLevel.INFO, "Begin reading input data");
		Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
		Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder().readTimetable(true).build().read()
				.getFirstElement();
		LinePool lineConcept = new LineReader.Builder(ptn).readFrequencies(true).build().read();
		LinePool fixedLines = new LinePool();
		if (respectFixedLines) {
			String fixedLinesFileName = Config.getStringValueStatic("filename_lc_fixed_lines");
			new LineReader.Builder(ptn).setLineFileName(fixedLinesFileName).setLinePool(fixedLines).readCosts(false).build().read();
		}
		logger.log(LogLevel.INFO, "Finished reading input data");

		logger.log(LogLevel.INFO, "Begin writing output data");
        VisumTimetableWriter.writeTimetable(ean, lineConcept, outputFile, outputHeader, fixedLines);
		logger.log(LogLevel.INFO, "Finished writing output data");
	}
}
