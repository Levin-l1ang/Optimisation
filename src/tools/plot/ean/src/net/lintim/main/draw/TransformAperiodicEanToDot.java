package net.lintim.main.draw;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.AperiodicEANReader;
import net.lintim.io.ConfigReader;
import net.lintim.model.AperiodicActivity;
import net.lintim.model.AperiodicEvent;
import net.lintim.model.Graph;
import net.lintim.model.Timetable;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Pair;
import net.lintim.util.draw.EanDotTransformer;

import java.io.File;
import java.util.logging.Logger;

/**
 * Class to draw an aperiodic ean, with or without timetable. It will produce a .dot-file which can afterwards be drawn
 * using e.g. neato.
 */
public class TransformAperiodicEanToDot {

	private static Logger logger = Logger.getLogger(TransformAperiodicEanToDot.class.getCanonicalName());

	/**
	 * Main method for drawing a periodic ean.
	 * @param args only one argument, the name of the config file to read.
	 */
	public static void main(String[] args) {
		logger.log(LogLevel.INFO, "Begin reading configuration");
		if (args.length != 1) {
			throw new ConfigNoFileNameGivenException();
		}
		new ConfigReader.Builder(args[0].trim()).build().read();
		String dotFile = Config.getStringValueStatic("filename_aperiodic_ean_dot_file");
		String timetableFileName = Config.getStringValueStatic("default_disposition_timetable_file");
		logger.log(LogLevel.INFO, "Finished reading configuration");

		logger.log(LogLevel.INFO, "Begin reading input data");
		// Check if a periodic timetable exists. If it exists, we want to read it as well
		File timetableFile = new File(timetableFileName);
		Graph<AperiodicEvent, AperiodicActivity> ean;
		Timetable<AperiodicEvent> dispositionTimetable = null;
		if (timetableFile.exists() && ! timetableFile.isDirectory()) {
			logger.log(LogLevel.INFO, "Will read disposition timetable");
			// Need to read two times, first for the original timetable, then for the disposition timetable
			Pair<Graph<AperiodicEvent, AperiodicActivity>, Timetable<AperiodicEvent>> eanAndTimetablePair = new
					AperiodicEANReader.Builder().build().read();
			ean = eanAndTimetablePair.getFirstElement();
			Timetable<AperiodicEvent> originalTimetable = eanAndTimetablePair.getSecondElement();
			// Now read the disposition timetable
			dispositionTimetable = new AperiodicEANReader.Builder().setEan(ean).readDispositionTimetable(true)
					.readEvents(false).readActivities(false).build().read().getSecondElement();
			// Reset the times in the ean to the original times, this was changed by the reader
			for (AperiodicEvent event : ean.getNodes()) {
				event.setTime(Math.toIntExact(originalTimetable.get(event)));
			}
		}
		else {
			logger.log(LogLevel.INFO, "Will not read disposition timetable");
			ean = new AperiodicEANReader.Builder().build().read().getFirstElement();
		}
		logger.log(LogLevel.INFO, "Finished reading input data");

		logger.log(LogLevel.INFO, "Begin writing output data");
		EanDotTransformer.writeAperiodicDotFile(ean, dispositionTimetable, dotFile);
		logger.log(LogLevel.INFO, "Finished writing output data");
	}
}
