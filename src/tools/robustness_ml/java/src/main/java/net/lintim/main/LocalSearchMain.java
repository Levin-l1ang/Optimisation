package net.lintim.main;

import net.lintim.algorithm.LocalSearchAlgorithm;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalSearchMain {

    private static final Logger logger = new Logger(LocalSearchMain.class.getCanonicalName());

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        LocalSearchParameters parameter = new LocalSearchParameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().setConfig(config).build().read();
        OD od = new ODReader.Builder(new MapOD()).setConfig(config).build().read();
        Graph<PeriodicEvent, PeriodicActivity> periodicEan = new PeriodicEANReader.Builder().readTimetable(true).setConfig(config).build().read().getFirstElement();

        LinePool lines = new LineReader.Builder(ptn).readFrequencies(true).readCosts(false).setConfig(config).build().read();
        VehicleSchedule vehicleSchedule;
        Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
        if (parameter.getStartSolutionNumber() == -1) {
            vehicleSchedule = new VehicleScheduleReader.Builder().setConfig(config).build().read();
            aperiodicEan = new AperiodicEANReader.Builder().setConfig(config).build().read().getFirstElement();
        }
        else {
            vehicleSchedule = ZipHelper.getSpecificVehicleScheduleFromFile(parameter.getInputFile(), parameter.getStartSolutionNumber());
            aperiodicEan = ZipHelper.getSpecificEanFromFile(parameter.getInputFile(), parameter.getStartSolutionNumber(), 1);
            ZipHelper.copySpecificTripFile(parameter.getInputFile(), parameter.getStartSolutionNumber());
            parameter.setRoutingStartTime(ZipHelper.getHighestRoutingStartTime());
        }
        // Filter aperiodic Ean for turnaround activities that have a periodic id. These are the vs-first turnaround
        // activities that dont correspond to real turnarounds
        for (AperiodicActivity activity : aperiodicEan.getEdges()) {
            if (activity.getType().equals(ActivityType.TURNAROUND) && activity.getPeriodicActivityId() != -1) {
                aperiodicEan.removeEdge(activity);
            } else if (parameter.createMissingChanges() && activity.getType().equals(ActivityType.CHANGE)) {
                aperiodicEan.removeEdge(activity);
            }
        }
        logger.info("Finished reading input data");

        logger.info("Begin executing local search");
        LocalSearchAlgorithm.robustify(aperiodicEan, ptn, od, periodicEan, lines, vehicleSchedule, parameter);
        logger.info("Finished local search");

        logger.info("Begin writing output data");
        new AperiodicEANWriter.Builder(aperiodicEan)
            .setConfig(config)
            .setEventFileName("delay-management/Events-expanded-robustified.giv")
            .setActivityFileName("delay-management/Activities-expanded-robustified.giv")
            .build().write();
        logger.info("Finished writing output data");
    }
}
