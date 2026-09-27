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
import net.lintim.model.EventActivityNetwork.ModelFrequency;
import net.lintim.model.EventActivityNetwork.ModelHeadway;

import java.io.File;
import java.io.IOException;

/**
 * Wraps {@link PeriodicEventActivityNetworkGenerator} to perform a periodic
 * rollout, i.e. an expansion of the timetable from frequency as attribute to
 * frequency as multiplicity.
 */

public class PeriodicRollout {

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

            if(EventActivityNetwork.ModelFrequency.valueOf(
                    config.getStringValue("ean_model_frequency").trim().
                    toUpperCase()) == ModelFrequency.FREQUENCY_AS_MULTIPLICITY){
                throw new DataInconsistentException("ean_model_frequency " +
                        "must not be "+ModelFrequency.FREQUENCY_AS_MULTIPLICITY+
                        "; event activity network already periodically " +
                        "rolled out");
            }

            // -----------------------------------------------------------------
            // --- Load State --------------------------------------------------
            // -----------------------------------------------------------------
            File stateFile = new File(config.getStringValue("filename_state_config"));
            Configuration state = new Configuration();

            if(stateFile.exists()){
                System.err.print("Loading State... ");
                ConfigurationCSV.fromFile(state, stateFile);
                System.err.println("done!");
            }

            // -----------------------------------------------------------------
            // --- Initialize Statistic ----------------------------------------
            // -----------------------------------------------------------------
            File statisticFile = new File(config.getStringValue(
                    "default_statistic_file"));
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

            if (!config.getStringValue("ean_model_headway").equals("NO_HEADWAYS")) {
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
            EventActivityNetwork referenceEan = new EventActivityNetwork(lc, config);
            PeriodicEventActivityNetworkCSV.fromFile(referenceEan, eventsFile,
                    activitiesFile);
            referenceEan.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            System.err.print("Loading Timetable... ");
            TimetableCSV.fromFile(referenceEan, timetableFile);
            referenceEan.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Construct Periodic Rolled Out Activity Network --------------
            // -----------------------------------------------------------------
            ModelFrequency targetModelFrequency =
                ModelFrequency.FREQUENCY_AS_MULTIPLICITY;

            ModelChange targetModelChange = ModelChange.SIMPLE;
            ModelHeadway targetModelHeadway;

            if (!config.getStringValue("ean_model_headway").equals("NO_HEADWAYS")){
                targetModelHeadway = ModelHeadway.SIMPLE;
            }
            else {
                targetModelHeadway = ModelHeadway.NO_HEADWAYS;
            }

            System.err.println("Constructing Event Activity Network...");

            state.setValue("ean_model_frequency", targetModelFrequency.toString());
            state.setValue("ean_model_change", targetModelChange.toString());
            state.setValue("ean_model_headway", targetModelHeadway.toString());

            System.err.println("Constructing Periodic Rolled Out Event " +
                    "Activity Network...");
            EventActivityNetwork ean = new EventActivityNetwork(lc,
                    targetModelFrequency, targetModelChange,
                    targetModelHeadway);
            ean.setPeriodLength(config.getDoubleValue("period_length"));

            PeriodicEventActivityNetworkGenerator peangen
            = new PeriodicEventActivityNetworkGenerator(
                    ean, referenceEan, config);
            System.err.print("  Line Concept Representation... ");
            peangen.generateLineConceptRepresentation();
            System.err.println("done!");
            System.err.print("  Change Activities... ");
            peangen.generateChanges();
            System.err.println("done!");
            System.err.print("  Headways... ");
            peangen.generateHeadways();
            System.err.println("done!");
            System.err.println("... done!");

            ean.computeDurationsFromTimetable();

            // -----------------------------------------------------------------
            // --- Route Passengers --------------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Computing Passenger Distribution...");
            config.setValue("ean_use_timetable", "true");
            PeriodicPassengerDistributionGenerator peandist = new
            PeriodicPassengerDistributionGenerator(ean, od, config);
            peandist.setIterationProgressCounter(new IterationProgressCounterDump());
            peandist.computePassengerDistribution();
            System.err.println("... done!");

            Double averageTravelingTime =
                PeriodicTimetableEvaluator.averageTravelingTime(ean);
            Double weightedSlackTime =
                PeriodicTimetableEvaluator.weightedSlackTime(ean);
            System.err.println("Average traveling time: " + averageTravelingTime);
            System.err.println("Weighted slack time: " + weightedSlackTime);
            statistic.setDoubleValue("tim_average_traveling_time",
                    averageTravelingTime);
            statistic.setDoubleValue("tim_weighted_slack_time",
                    weightedSlackTime);

            // -----------------------------------------------------------------
            // --- Save Event Activity Network ---------------------------------
            // -----------------------------------------------------------------
            Boolean discardUnusedChangeActivities=config.getBooleanValue(
                    "ean_discard_unused_change_activities");
            System.err.print("Saving Event Activity Network... ");
            PeriodicEventActivityNetworkCSV.toFile(ean, eventsFile,
                    activitiesFile, discardUnusedChangeActivities);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Save Timetable ----------------------------------------------
            // -----------------------------------------------------------------
            File toDurationsFile = new File(config.getStringValue(
                    "default_durations_periodic_file"));

            System.err.print("Saving Timetable... ");
            TimetableCSV.toFile(ean, timetableFile);
            System.err.println("done!");

            System.err.print("Saving Durations... ");
            DurationsCSV.toFile(ean, toDurationsFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Save Statistic ----------------------------------------------
            // -----------------------------------------------------------------
            System.err.print("Saving Statistic... ");
            StatisticCSV.toFile(statistic, statisticFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Save State --------------------------------------------------
            // -----------------------------------------------------------------
            System.err.print("Saving State Configuration... ");
            ConfigurationCSV.toFile(state, stateFile,
                    "This file is automatically generated. Do not edit!");
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
