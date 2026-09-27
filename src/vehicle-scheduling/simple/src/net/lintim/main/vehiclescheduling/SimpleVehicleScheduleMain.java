package net.lintim.main.vehiclescheduling;

import net.lintim.algorithms.vehiclescheduling.SimpleVehicleSchedule;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.TripReader;
import net.lintim.io.VehicleScheduleWriter;
import net.lintim.model.Trip;
import net.lintim.model.VehicleSchedule;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.Collection;

/**
 * Create a simple vehicle schedule, i.e., a vehicle schedule containing only circulations serving one line. A
 * vehicle will serve the next other direction of the same line after finishing a trip.
 */
public class SimpleVehicleScheduleMain {

    private static final Logger logger = new Logger(SimpleVehicleScheduleMain.class.getCanonicalName());

	public static void main(String[] args) {
		logger.info("Begin reading configuration");
		if(args.length < 1){
			throw new ConfigNoFileNameGivenException();
		}
		Config config = new ConfigReader.Builder(args[0]).build().read();
		// We assume the turn over time to be in seconds
		int turnOverTime = config.getIntegerValue("vs_turn_over_time");
		logger.info("Finished reading configuration");
		logger.info("Begin reading input data");
		Collection<Trip> trips = new TripReader.Builder().build().read();
		logger.info("Finished reading input data");
		logger.info("Begin simple vehicle schedule computation");
		VehicleSchedule vehicleSchedule = SimpleVehicleSchedule.createSimpleVehicleSchedule(trips, turnOverTime);
		logger.info("Finished simple vehicle schedule computation");
		logger.info("Writing output data");
		new VehicleScheduleWriter.Builder(vehicleSchedule).build().write();
		logger.info("Finished writing output data");
	}
}
