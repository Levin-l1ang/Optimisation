package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.debug.DebugOriginDestinationLinkPaths;
import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Runs the timetable evaluation, i.e. basically all methods of
 * {@link PeriodicTimetableEvaluator} once and writes results to both console
 * as well as statistic.
 */

public class EvaluateTimetable {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Error: number of arguments invalid; first "
                    + "argument must be the path to the configuration file.");
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
            config.setValue("ean_use_timetable", "false");
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Initialize Statistic ----------------------------------------
            // -----------------------------------------------------------------
            File statisticFile = new File(
                    config.getStringValue("default_statistic_file"));
            System.err.print("Initializing Statistic... ");
            Statistic statistic = new Statistic();
            if (statisticFile.exists()) {
                StatisticCSV.fromFile(statistic, statisticFile);
            }
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Enable Relevant Debugging -----------------------------------
            // -----------------------------------------------------------------
            DebugOriginDestinationLinkPaths debugOdPaths =
                new DebugOriginDestinationLinkPaths(config);

            // -----------------------------------------------------------------
            // --- Load Public Transportation Network --------------------------
            // -----------------------------------------------------------------
            File stationsFile = new File(
                    config.getStringValue("default_stops_file"));
            File linksFile = new File(
                    config.getStringValue("default_edges_file"));
            File headwayFile = new File(
                    config.getStringValue("default_headways_file"));

            System.err.print("Loading Public Transportation Network... ");
            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile,
                    linksFile);
            System.err.println("done!");

            System.err.print("Loading Initial Headway Data... ");
            HeadwayCSV.fromFile(ptn, headwayFile);
            System.err.println("done!");

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
            File linesFile = new File(
                    config.getStringValue("default_lines_file"));

            System.err.print("Loading Line Concept... ");
            LineCollection lc = new LineCollection(ptn);
            LineConceptCSV.fromFile(lc, linesFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Event Activity Network ---------------------------------
            // -----------------------------------------------------------------
            File eventsFile = new File(
                    config.getStringValue("default_events_periodic_file"));
            File activitiesFile = new File(
                    config.getStringValue("default_activities_periodic_file"));
            File timetableFile = new File(
                    config.getStringValue("default_timetable_periodic_file"));

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
            // --- Evaluate Timetable ------------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Evaluating Event Activity Network...");
            System.err.println("  Computing Statistic...");

            {
                System.err.print("    Average Traveling Time: ");
                Double averageTravelingTime = PeriodicTimetableEvaluator
                .averageTravelingTime(ean);
                statistic.setDoubleValue("tim_average_traveling_time",
                        averageTravelingTime);
                System.err.println(averageTravelingTime);
            }

            {
                System.err.print("    Average Drive Time: ");
                Double averageDriveTime = PeriodicTimetableEvaluator
                .averageDriveTime(ean);
                statistic.setDoubleValue("tim_average_drive_time",
                        averageDriveTime);
                System.err.println(averageDriveTime);
            }

            {
                System.err.print("    Average Wait Time: ");
                Double averageWaitTime = PeriodicTimetableEvaluator
                .averageWaitTime(ean);
                statistic.setDoubleValue("tim_average_wait_time",
                        averageWaitTime);
                System.err.println(averageWaitTime);
            }

            {
                System.err.print("    Average Change Time: ");
                Double averageChangeTime = PeriodicTimetableEvaluator
                .averageChangeTime(ean);
                statistic.setDoubleValue("tim_average_change_time",
                        averageChangeTime);
                System.err.println(averageChangeTime);
            }

            {
                System.err.print("    Weighted Slack Time: ");
                Double weightedSlackTime = PeriodicTimetableEvaluator
                .weightedSlackTime(ean);
                statistic.setDoubleValue("tim_weighted_slack_time",
                        weightedSlackTime);
                System.err.println(weightedSlackTime);
            }

            {
                System.err.print("    Minimal used change duration: ");
                Double minimalUsedChangeDuration = PeriodicTimetableEvaluator
                .minimalUsedChangeDuration(ean);
                statistic.setDoubleValue("tim_minimal_used_change_duration",
                        minimalUsedChangeDuration);
                System.err.println(minimalUsedChangeDuration);
            }

            {
                System.err.print("    Maximal used change duration: ");
                Double maximalUsedChangeDuration = PeriodicTimetableEvaluator
                .maximalUsedChangeDuration(ean);
                statistic.setDoubleValue("tim_maximal_used_change_duration",
                        maximalUsedChangeDuration);
                System.err.println(maximalUsedChangeDuration);
            }
            // -----------------------------------------------------------------
            // --- Save Statistic ----------------------------------------------
            // -----------------------------------------------------------------
            System.err.print("  Saving Statistic... ");
            StatisticCSV.toFile(statistic, statisticFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Finish Relevant Debugging -----------------------------------
            // -----------------------------------------------------------------
            debugOdPaths.finish(config, ptn, od);

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
