package net.lintim.main.vehiclescheduling;

import net.lintim.algorithm.vehiclescheduling.AddToEan;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.vehiclescheduling.Parameters;

/**
 * Main class for adding simple vehicle routes to a periodic ean. Will connect every repetition of a line with the
 * corresponding reverse direction
 */
public class AddSimpleVehicleRoutesToEanMain {
	/**
	 * Start the computation of the simple vehicle routes and add them to the existing ean.
	 * @param args One argument needed, the path to the LinTim config file to read
	 */
	public static void main(String[] args) {
		Logger logger = new Logger(AddSimpleVehicleRoutesToEanMain.class.getCanonicalName());
		logger.info("Begin reading configuration");
		if(args.length < 1){
			throw new ConfigNoFileNameGivenException();
		}
		Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
		logger.info("Finished reading configuration");
		logger.info("Begin reading input data");
		Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
		LinePool lineConcept = new LineReader.Builder(ptn).readFrequencies(true).readCosts(false).build().read();
		Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder().build().read().getFirstElement();
		logger.info("Finished reading input data");
		logger.info("Begin vehicle schedule computation");
		AddToEan.addSimpleVehicleSchedulesToEan(ean, lineConcept, parameters);
		logger.info("Finished vehicle schedule computation");
		logger.info("Writing output data");
		new PeriodicEANWriter.Builder(ean).writeTimetable(false).build().write();
		logger.info("Finished writing output data");
	}
}
