package net.lintim.main;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.util.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class ReevaluateSolution {
    private static final Logger logger = new Logger(ReevaluateSolution.class.getCanonicalName());

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().setConfig(config).build().read();
        OD od = new ODReader.Builder(new MapOD()).setConfig(config).build().read();
        Graph<PeriodicEvent, PeriodicActivity> periodicEan = new PeriodicEANReader.Builder().setConfig(config).build().read().getFirstElement();
        Graph<AperiodicEvent, AperiodicActivity> aperiodicEan = new AperiodicEANReader.Builder().setConfig(config).build().read().getFirstElement();
        LinePool lines = new LineReader.Builder(ptn).readFrequencies(true).readCosts(false).setConfig(config).build().read();
        VehicleSchedule vehicleSchedule = new VehicleScheduleReader.Builder().setConfig(config).build().read();
        // Filter aperiodic Ean for turnaround activities that have a periodic id. These are the vs-first turnaround
        // activities that dont correspond to real turnarounds
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            if (activity.getType().equals(ActivityType.TURNAROUND) && activity.getPeriodicActivityId() != -1) {
                aperiodicEan.removeEdge(activity);
            }
        }
        logger.info("Finished reading input data");

        logger.info("Begin computing ml data");
        LocalSearchData mlData = new LocalSearchData(aperiodicEan, ptn, od, periodicEan, lines, vehicleSchedule, parameters);
        mlData.initializeNetwork();
        logger.debug(mlData.toString());
        double[] tensor = mlData.generateModelInput();
        MLModel model = MLModel.supplyMLModel(parameters);
        model.readModel(parameters.getModelFileName());
        double mlValue = model.predict(tensor);
        double ttValue = mlData.computeAverageTravelTime();
        logger.info("Finished computing ml data");

        logger.info("Begin writing output data");
        BufferedWriter writer = new BufferedWriter(new FileWriter("reevaluated-values.csv"));
        writer.write(mlValue + "; " + ttValue);
        writer.close();
        logger.info("Finished writing output data");

    }
}
