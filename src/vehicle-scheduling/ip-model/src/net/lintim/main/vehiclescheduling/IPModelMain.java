package net.lintim.main.vehiclescheduling;

import net.lintim.algorithm.vehiclescheduling.IPModelSolver;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.InputFileException;
import net.lintim.io.ConfigReader;
import net.lintim.io.PTNReader;
import net.lintim.io.TripReader;
import net.lintim.io.VehicleScheduleWriter;
import net.lintim.io.DepotReader;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.vehiclescheduling.Parameters;

import java.util.Collection;
import java.util.List;
import java.io.File;
import java.io.IOException;


/**
 * Main class for computing a vehicle schedule using an IP solver
 */
public class IPModelMain {
    private static final Logger logger = new Logger(IPModelMain.class.getCanonicalName());

    public static void main(String[] args) {
        logger.info("Begin reading configuration");
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");
        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
        Collection<Trip> trips = new TripReader.Builder().build().read(); 
        
        // In the case that there is no depot file, we create one 
        List<Depot> depots;
        try {
            depots = new DepotReader.Builder().setDepotFileName(parameters.getDepotFileName()).setConversionFactorCoordinates(parameters.getConversionCoordinates()).build().read();
        } catch (InputFileException e) {
            logger.info("The depot file could not be found, we now create one out of the config parameters ");
            ProcessBuilder pb = new ProcessBuilder("make", "vs-depot-file");
            try {
                Process process = pb.start();
                int exitCode = process.waitFor();
                logger.info("Depot File was created");
                } catch (IOException | InterruptedException a) {
                    a.printStackTrace(); // in case error occures during the depot file creation bash script
                }
            depots = new DepotReader.Builder().setDepotFileName(parameters.getDepotFileName()).setConversionFactorCoordinates(parameters.getConversionCoordinates()).build().read();
        }

        logger.info("Finished reading input data");
        logger.info("Begin ip vehicle schedule computation");
        IPModelSolver solver = IPModelSolver.getVehicleSchedulingIpSolver(parameters.getSolverType());
        VehicleSchedule vehicleSchedule = solver.solveVehicleSchedulingIPModel(ptn, trips, parameters, depots);
        logger.info("Finished ip vehicle schedule computation");
        if (vehicleSchedule != null) {
            logger.info("Writing output data");
            new VehicleScheduleWriter.Builder(vehicleSchedule).build().write();
            logger.info("Finished writing output data");
        } else {
            logger.info("Finish program without writing output");
        }
    }
}
