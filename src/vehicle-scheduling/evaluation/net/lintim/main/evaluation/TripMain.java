package net.lintim.main.evaluation;

import net.lintim.evaluation.TripEvaluator;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.InputFileException;
import net.lintim.io.*;
import net.lintim.model.AperiodicActivity;
import net.lintim.model.AperiodicEvent;
import net.lintim.model.Graph;
import net.lintim.model.Trip;
import net.lintim.util.LogLevel;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Main class for evaluating the current trips
 */
public class TripMain {
	/**
	 * Evaluate the trips in the current LinTim dataset directory.
	 * @param args Only one parameter is read, args[0]. This should be the relative path to the config file to read.
	 */
	public static void main(String[] args) {
		Logger logger = Logger.getLogger("net.lintim.main.vehiclescheduling");
		logger.log(LogLevel.INFO, "Begin reading configuration");
		if(args.length < 1){
			throw new ConfigNoFileNameGivenException();
		}
		new ConfigReader.Builder(args[0]).build().read();
		logger.log(LogLevel.INFO, "Finished reading configuration");
		logger.log(LogLevel.INFO, "Begin reading input data");
		Graph<AperiodicEvent, AperiodicActivity> ean = new AperiodicEANReader.Builder().build().read().getFirstElement();
		Collection<Trip> trips = new TripReader.Builder().build().read();
        try{
            new StatisticReader.Builder().build().read();
        }
        catch (InputFileException e){
            //When there is no statistic file, ignore it. We will write a new one
        }
		logger.log(LogLevel.INFO, "Finished reading input data");
		logger.log(LogLevel.INFO, "Begin rollout evaluation");
		TripEvaluator.evaluateTrips(trips, ean);
		logger.log(LogLevel.INFO, "Finished rollout evaluation");
		logger.log(LogLevel.INFO, "Writing output data");
		new StatisticWriter.Builder().build().write();
		logger.log(LogLevel.INFO, "Finished writing output data");
	}
}
