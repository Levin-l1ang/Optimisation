package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.debug.DebugOriginDestinationActivityPaths;
import net.lintim.debug.DebugOriginDestinationLinkPaths;
import net.lintim.dump.IterationProgressCounterDump;
import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicEventActivityNetworkGenerator;
import net.lintim.generator.PeriodicPassengerDistributionGenerator;
import net.lintim.model.*;
import net.lintim.model.EventActivityNetwork.ModelChange;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Wraps {@link PeriodicPassengerDistributionGenerator} to generate a
 * passenger distribution by shortest paths w.r.t. a timetable.
 */

public class ReroutePassengers {

    public static void main(String[] args){

        if(args.length != 1){
            System.err.println("Error: number of arguments invalid; first " +
                    "argument must be the path to the configuration file.");
            System.exit(1);
        }

        try {
            // -----------------------------------------------------------------
            // --- Load Configuration ------------------------------------------
            // -----------------------------------------------------------------
            File configFile = new File(args[0]);

            System.err.print("Loading Configuration... ");
            Configuration config = new Configuration();
            ConfigurationCSV.fromFile(config, configFile);
            System.err.println("done!");

            ModelChange modelChange = ModelChange.valueOf(config.
                    getStringValue("ean_model_change").trim().toUpperCase());

            if(modelChange != ModelChange.SIMPLE &&
                    modelChange != ModelChange.LCM_SIMPLIFICATION){

                throw new DataInconsistentException("rerouting of passengers " +
                        "is limited to change activity model " +
                        ModelChange.SIMPLE + ", but " + modelChange + " given");
            }

            // -----------------------------------------------------------------
            // --- Initialize Statistic ----------------------------------------
            // -----------------------------------------------------------------
            File statisticFile = new File(config.getStringValue("default_statistic_file"));
            System.err.print("Initializing Statistic... ");
            Statistic statistic = new Statistic();
            if(statisticFile.exists()){
                StatisticCSV.fromFile(statistic, statisticFile);
            }
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Enable Relevant Debugging -----------------------------------
            // -----------------------------------------------------------------
            DebugOriginDestinationLinkPaths debugOdLinkPaths =
                new DebugOriginDestinationLinkPaths(config);
            DebugOriginDestinationActivityPaths debugOdActivityPaths =
                new DebugOriginDestinationActivityPaths(config);

            // -----------------------------------------------------------------
            // --- Load Public Transportation Network --------------------------
            // -----------------------------------------------------------------
            File stationsFile = new File(config.getStringValue("default_stops_file"));
            File linksFile = new File(config.getStringValue("default_edges_file"));
            File headwayFile = new File(config.getStringValue("default_headways_file"));

            System.err.print("Loading Public Transportation Network... ");
            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile, linksFile);
            System.err.println("done!");

            if (headwayFile.exists()){
                System.err.print("Loading Initial Headway Data... ");
                HeadwayCSV.fromFile(ptn, headwayFile);
                System.err.println("done!");
            }

            // -----------------------------------------------------------------
            // --- Load Origin Destination Matrix ------------------------------
            // -----------------------------------------------------------------
            File odFile = new File(config.getStringValue("default_od_file"));

            System.err.print("Loading Origin Destination Matrix... ");
            OriginDestinationMatrix od = new OriginDestinationMatrix(ptn);
            OriginDestinationMatrixCSV.fromFile(od, odFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Line Concept -------------------------------------------
            // -----------------------------------------------------------------
            File linesFile = new File(config.getStringValue("default_lines_file"));

            System.err.print("Loading Line Concept... ");
            LineCollection lc = new LineCollection(ptn);
            LineConceptCSV.fromFile(lc, linesFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Event Activity Network ---------------------------------
            // -----------------------------------------------------------------
            File eventsFile = new File(config.getStringValue(
                    "default_events_periodic_file"));
            File activitiesFile = new File(config.getStringValue(
                    "default_activities_periodic_file"));
            File timetableFile = new File(config.getStringValue(
                    "default_timetable_periodic_file"));

            System.err.print("Loading Event Activity Network... ");
            EventActivityNetwork ean = new EventActivityNetwork(lc, config);
            PeriodicEventActivityNetworkCSV.fromFile(ean, eventsFile, activitiesFile);
            ean.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            System.err.print("Loading Timetable... ");
            TimetableCSV.fromFile(ean, timetableFile);
            ean.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Reroute Passengers ------------------------------------------
            // -----------------------------------------------------------------
            Double averageTravelingTime =
                PeriodicTimetableEvaluator.averageTravelingTime(ean);
            Double weightedSlackTime =
                PeriodicTimetableEvaluator.weightedSlackTime(ean);
            System.err.println("Old weighted traveling time: " + averageTravelingTime);
            System.err.println("Old weighted slack time: " + weightedSlackTime);
            statistic.setDoubleValue("tim_weighted_traveling_time_no_rerouting",
                    averageTravelingTime);
            statistic.setDoubleValue("tim_weighted_slack_time_no_rerouting",
                    weightedSlackTime);

            if(config.getBooleanValue("ean_complete_change_activities_before_reroute")){
                System.err.print("Completing change activities...");
                PeriodicEventActivityNetworkGenerator peangen =
                    new PeriodicEventActivityNetworkGenerator(ean, null, config);
                peangen.generateChanges();
                ean.computeDurationsFromTimetable();
                System.err.println(" done!");
            }

            System.err.println("Computing Passenger Distribution...");
            config.setValue("ean_use_timetable", "true");
            PeriodicPassengerDistributionGenerator peandist = new
            PeriodicPassengerDistributionGenerator(ean, od, config);
            peandist.setIterationProgressCounter(new IterationProgressCounterDump());
            peandist.computePassengerDistribution();
            System.err.println("... done!");

            averageTravelingTime = PeriodicTimetableEvaluator.averageTravelingTime(ean);
            weightedSlackTime = PeriodicTimetableEvaluator.weightedSlackTime(ean);
            System.err.println("New average traveling time: " + averageTravelingTime);
            System.err.println("New weighted slack time: " + weightedSlackTime);
            statistic.setDoubleValue("tim_weighted_traveling_time", averageTravelingTime);
            statistic.setDoubleValue("tim_weighted_slack_time", weightedSlackTime);

            Boolean discardUnusedChangeActivities=config.getBooleanValue(
            "ean_discard_unused_change_activities");
            System.err.print("Saving Event Activity Network... ");
            PeriodicEventActivityNetworkCSV.toFile(ean, eventsFile,	activitiesFile,
                    discardUnusedChangeActivities);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Save Statistic ----------------------------------------------
            // -----------------------------------------------------------------
            System.err.print("Saving Statistic... ");
            StatisticCSV.toFile(statistic, statisticFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Finish Relevant Debugging -----------------------------------
            // -----------------------------------------------------------------
            debugOdLinkPaths.finish(config, ptn, od);
            debugOdActivityPaths.finish(config, ean, od);

        } catch (IOException e) {
            System.err.println();
            System.err.println("An error occured. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        } catch (DataInconsistentException e) {
            System.err.println();
            System.err.println("An error occured. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        }

    }


}
