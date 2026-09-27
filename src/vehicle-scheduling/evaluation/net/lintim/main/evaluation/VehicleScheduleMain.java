package net.lintim.main.evaluation;

import net.lintim.evaluation.VehicleScheduleEvaluator;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.InputFileException;
import net.lintim.io.*;
import net.lintim.main.evaluation.util.evaluation.Parameters;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;


import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * Main class for evaluating an aperiodic vehicle schedule
 */
public class VehicleScheduleMain {
	public static void main(String[] args) {
		Logger logger = new Logger(VehicleScheduleMain.class.getCanonicalName());
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
		Collection<Trip> trips = new TripReader.Builder().build().read();
		VehicleSchedule vehicleSchedule = new VehicleScheduleReader.Builder().build().read();
		// We need the following if clause to determine if we need to read in the depot files or not
		Trip firstTrip = vehicleSchedule.iterator().next().iterator().next().getTripList().get(0);
		List<Depot> depots;
		if (firstTrip.getStartStopId() < 0) {
			parameters.setUseDepot(true);
			depots = new DepotReader.Builder().setDepotFileName(parameters.getDepotFileName()).setConversionFactorCoordinates(parameters.getConversionCoordinates()).build().read();
		} else {
			depots = new ArrayList<>();
		}
		logger.info("Finished reading input data");
		logger.info("Begin vehicle schedule evaluation");
		Statistic statistic = VehicleScheduleEvaluator.evaluateVehicleSchedule(vehicleSchedule, trips, ptn, lineConcept, parameters, depots);
		logger.info("Finished vehicle schedule evaluation");
		logger.info("Writing output data");
		new StatisticWriter.Builder().setStatistic(statistic).build().write();
		logger.info("Finished writing output data");

	}
}
