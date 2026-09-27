package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.debug.DebugOriginDestinationLinkPaths;
import net.lintim.dump.IterationProgressCounterDump;
import net.lintim.evaluator.EventActivityNetworkEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicPassengerDistributionGenerator;
import net.lintim.generator.PeriodicPassengerDistributionGenerator.ModelInitialDurationAssumption;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Runs the event activity network evaluation, i.e. basically all methods of
 * {@link EventActivityNetworkEvaluator} once and writes results to both console
 * as well as statistic.
 */

public class EvaluateEventActivityNetwork {

    public static void main(String[] args) {

        if(args.length != 1) {
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
            if(statisticFile.exists()) {
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

            if (headwayFile.exists()) {
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

            System.err.print("Loading Event Activity Network... ");
            EventActivityNetwork ean = new EventActivityNetwork(lc, config);
            PeriodicEventActivityNetworkCSV.fromFile(ean, eventsFile, activitiesFile);
            ean.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Construct Passenger Distribution ----------------------------
            // -----------------------------------------------------------------
            if(config.getBooleanValue("ean_construction_skip_passenger_distribution")) {
                System.err.println("Skipping Passenger Distribution Calculation...");
                ean.resetPassengers();
            } else {
                System.err.println("Computing Passenger Distribution...");
                PeriodicPassengerDistributionGenerator peandist = new
                    PeriodicPassengerDistributionGenerator(ean, od, config);
                peandist.setIterationProgressCounter(new IterationProgressCounterDump());
                if(peandist.getModelInitialDurationAssumption() ==
                    ModelInitialDurationAssumption.SEMI_AUTOMATIC) {

                    System.err.print("  Loading initial duration assumption... ");
                    InitialDurationAssumptionCSV.fromFile(ean, new File(config
                        .getStringValue("filename_initial_duration_assumption")));
                    System.err.println("done!");
                }
                peandist.computePassengerDistribution();

                System.err.println("... done!");
            }
            // -----------------------------------------------------------------
            // --- Evaluate EventActivityNetwork -------------------------------
            // -----------------------------------------------------------------
            Boolean ean_extended_statistic = config.getBooleanValue("ean_eval_extended");
            System.err.println("Evaluating Event Activity Network...");
            System.err.println("  Computing Statistic...");

            {
                System.err.print("    Events: ");
                Integer numberOfEvents = ean.getEvents().size();
                statistic.setIntegerValue("ean_prop_events", numberOfEvents);
                System.err.println(numberOfEvents);
            }

            {
                System.err.print("    Departure Events: ");
                Integer numberOfDepartureEvents = ean.getDepartureEvents().size();
                statistic.setIntegerValue("ean_prop_events_departure", numberOfDepartureEvents);
                System.err.println(numberOfDepartureEvents);
            }

            {
                System.err.print("    Arrival Events: ");
                Integer numberOfArrivalEvents = ean.getArrivalEvents().size();
                statistic.setIntegerValue("ean_prop_events_arrival", numberOfArrivalEvents);
                System.err.println(numberOfArrivalEvents);
            }

            {
                System.err.print("    Activities: ");
                Integer numberOfActivities = ean.getActivities().size();
                statistic.setIntegerValue("ean_prop_activities", numberOfActivities);
                System.err.println(numberOfActivities);
            }

            {
                System.err.print("    Drive Activities: ");
                Integer numberOfDriveActivities = ean.getDriveActivities().size();
                statistic.setIntegerValue("ean_prop_activities_drive",
                    numberOfDriveActivities);
                System.err.println(numberOfDriveActivities);
            }

            {
                System.err.print("    Wait Activities: ");
                Integer numberOfWaitActivities = ean.getWaitActivities().size();
                statistic.setIntegerValue("ean_prop_activities_wait",
                    numberOfWaitActivities);
                System.err.println(numberOfWaitActivities);
            }

            {
                System.err.print("    Change Activities: ");
                Integer numberOfChangeActivities = ean.getChangeActivities().size();
                statistic.setIntegerValue("ean_prop_activities_change",
                    numberOfChangeActivities);
                System.err.println(numberOfChangeActivities);
            }

            {
                System.err.print("    Headway Activities: ");
                Integer numberOfHeadwayActivities = ean.getHeadwayActivities().size();
                statistic.setIntegerValue("ean_prop_activities_headway",
                    numberOfHeadwayActivities);
                System.err.println(numberOfHeadwayActivities);
            }

            {
                System.err.print("    Used Activities: ");
                Integer numberOfUsedActivities =
                    EventActivityNetworkEvaluator.numberOfUsedActivites(ean);
                statistic.setIntegerValue("ean_prop_activities_od",
                    numberOfUsedActivities);
                System.err.println(numberOfUsedActivities);
            }

            if(ean_extended_statistic) {
                System.err.print("    Objective Activities: ");
                Integer numberOfObjectiveActivities =
                    EventActivityNetworkEvaluator.numberOfObjectiveActivities(ean);
                statistic.setIntegerValue("ean_prop_activities_objective",
                    numberOfObjectiveActivities);
                System.err.println(numberOfObjectiveActivities);
            }

            if(ean_extended_statistic) {
                System.err.print("    Feasibility Activities: ");
                Integer numberOfFeasibilityActivities =
                    EventActivityNetworkEvaluator.numberOfFeasibilityActivities(ean);
                statistic.setIntegerValue("ean_prop_activities_feas",
                    numberOfFeasibilityActivities);
                System.err.println(numberOfFeasibilityActivities);
            }

            {
                System.err.print("    Used Change Activities: ");
                Integer numberOfUsedChangeActivities =
                    EventActivityNetworkEvaluator.numberOfUsedChangeActivites(ean);
                statistic.setIntegerValue("ean_prop_activities_od_change",
                    numberOfUsedChangeActivities);
                System.err.println(numberOfUsedChangeActivities);
            }

            {
                System.err.print("    Used Drive Activities: ");
                Integer numberOfUsedDriveActivities =
                    EventActivityNetworkEvaluator.numberOfUsedDriveActivites(ean);
                statistic.setIntegerValue("ean_prop_activities_od_drive",
                    numberOfUsedDriveActivities);
                System.err.println(numberOfUsedDriveActivities);
            }

            {
                System.err.print("    Used Wait Activities: ");
                Integer numberOfUsedWaitActivities =
                    EventActivityNetworkEvaluator.numberOfUsedWaitActivites(ean);
                statistic.setIntegerValue("ean_prop_activities_od_wait",
                    numberOfUsedWaitActivities);
                System.err.println(numberOfUsedWaitActivities);
            }

            if(ean_extended_statistic) {
                System.err.print("    Headways Between Departures Only: ");
                Boolean headwaysBetweenDeparturesOnly =
                    EventActivityNetworkEvaluator.headwaysBetweenDeparturesOnly(ean);
                statistic.setBooleanValue("ean_prop_headways_dep",
                    headwaysBetweenDeparturesOnly);
                System.err.println(headwaysBetweenDeparturesOnly);
            }

            if(ean_extended_statistic) {
                System.err.print("    Interstation Headways Exist: ");
                Boolean interstationHeadwaysExist =
                    EventActivityNetworkEvaluator.interstationHeadwaysExist(ean);
                statistic.setBooleanValue("ean_prop_headways_interstation",
                    interstationHeadwaysExist);
                System.err.println(interstationHeadwaysExist);
            }

            {
                System.err.print("    Average Traveling Time: ");
                Double initialAverageTravelingTime =
                    EventActivityNetworkEvaluator.initialAverageTravelingTime(ean);
                statistic.setDoubleValue("ean_time_average", initialAverageTravelingTime);
                System.err.println(initialAverageTravelingTime);
            }

            if(ean_extended_statistic) {
                System.err.print("    Minimal Used Change Duration:     ");
                Double initialMinimalUsedChangeDuration =
                    EventActivityNetworkEvaluator.initialMinimalUsedChangeDuration(ean);
                statistic.setDoubleValue("ean_prop_change_od_min", initialMinimalUsedChangeDuration);
                System.err.println(initialMinimalUsedChangeDuration);
            }

            if(ean_extended_statistic) {
                System.err.print("    Maximal Used Change Duration:     ");
                Double initialMaximalUsedChangeDuration =
                    EventActivityNetworkEvaluator.initialMaximalUsedChangeDuration(ean);
                statistic.setDoubleValue("ean_prop_change_od_max", initialMaximalUsedChangeDuration);
                System.err.println(initialMaximalUsedChangeDuration);
            }

            /**
             {
             if(config.getBooleanValue(
             "ean_evaluate_logarithmic_base10_cyclebase_width")){
             System.err.print("    Logarithmic Base 10 Cyclebase Width Objective: ");
             Double eanLogarithmicBase10CyclebaseLengthObjective =
             EventActivityNetworkEvaluator.logarithmicBase10CyclebaseWidthObjective(ean);
             statistic.setDoubleValue("ean_logarithmic_base10_cyclebase_width_objective",
             eanLogarithmicBase10CyclebaseLengthObjective);
             System.err.println(eanLogarithmicBase10CyclebaseLengthObjective);
             System.err.print("    Logarithmic Base 10 Cyclebase Width Feasibility: ");
             Double eanLogarithmicBase10CyclebaseLengthFeasibility =
             EventActivityNetworkEvaluator.logarithmicBase10CyclebaseWidthFeasibility(ean);
             statistic.setDoubleValue("ean_logarithmic_base10_cyclebase_width_feasibility",
             eanLogarithmicBase10CyclebaseLengthFeasibility);
             System.err.println(eanLogarithmicBase10CyclebaseLengthFeasibility);
             }
             else {
             System.err.println("  Set " +
             "\"ean_evaluate_logarithmic_base10_cyclebase_width\" " +
             "to \"true\" to calculate it.");
             }
             }
             **/
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
            System.err.println("An error occurred. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        } catch (DataInconsistentException e) {
            System.err.println();
            System.err.println("An error occurred. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        }

    }

}
