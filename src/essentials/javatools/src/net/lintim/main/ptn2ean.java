package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.debug.DebugInitialDurationAssumption;
import net.lintim.debug.DebugOriginDestinationActivityPaths;
import net.lintim.debug.DebugOriginDestinationLinkPaths;
import net.lintim.dump.IterationProgressCounterDump;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicEventActivityNetworkGenerator;
import net.lintim.generator.PeriodicPassengerDistributionGenerator;
import net.lintim.generator.PeriodicPassengerDistributionGenerator.ModelInitialDurationAssumption;
import net.lintim.io.InfrastructureReader;
import net.lintim.io.StationLimitReader;
import net.lintim.model.*;
import net.lintim.model.EventActivityNetwork.ModelChange;
import net.lintim.model.EventActivityNetwork.ModelFrequency;
import net.lintim.model.EventActivityNetwork.ModelHeadway;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Wraps {@link PeriodicEventActivityNetworkGenerator} and
 * {@link PeriodicPassengerDistributionGenerator} to generate an event
 * activity network with initial passenger distribution.
 */
public class ptn2ean {

    public static void main(String[] args) {

        if(args.length != 1) {
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
            config.setValue("ean_use_timetable", "false");
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load State --------------------------------------------------
            // -----------------------------------------------------------------
            File stateFile = new File(config.getStringValue("filename_state_config"));
            Configuration state = new Configuration();

            if(stateFile.exists()) {
                System.err.print("Loading State... ");
                ConfigurationCSV.fromFile(state, stateFile);
                System.err.println("done!");
            }

            // -----------------------------------------------------------------
            // --- Initialize Statistic ----------------------------------------
            // -----------------------------------------------------------------
            // File statisticFile = new File(config.getStringValue(
            //         "default_statistic_file"));
            // System.err.print("Initializing Statistic... ");
            // Statistic statistic = new Statistic();
            // if(statisticFile.exists()){
            //     StatisticCSV.fromFile(statistic, statisticFile);
            // }
            // System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Enable Relevant Debugging -----------------------------------
            // -----------------------------------------------------------------
            DebugOriginDestinationLinkPaths debugOdPaths =
                new DebugOriginDestinationLinkPaths(config);
            DebugInitialDurationAssumption debugIda =
                new DebugInitialDurationAssumption(config);
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
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile,
                linksFile);
            boolean readWalking = config.getBooleanValue("ean_use_walking");
            Graph<InfrastructureNode, WalkingEdge> walkingGraph = null;
            if(readWalking) {
                walkingGraph = new InfrastructureReader.Builder().readInfrastructureEdges(false)
                    .setMaxWalkingTime(config.getDoubleValue("sl_max_walking_time"))
                    .setDirectedWalking(config.getBooleanValue("sl_walking_is_directed"))
                    .setNodeFileName(config.getStringValue("filename_node_file"))
                    .setWalkingEdgeFileName(config.getStringValue("filename_walking_edge_file"))
                    .setConversionFactorCoordinates(config.getDoubleValue("gen_conversion_coordinates"))
                    .setConversionFactorLength(config.getDoubleValue("gen_conversion_length"))
                    .build().read().getSecondElement();
            }
            System.err.println("done!");

            if(!config.getStringValue("ean_construction_target_model_headway").equals("NO_HEADWAYS")) {
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
            // --- Construct Event Activity Network ----------------------------
            // -----------------------------------------------------------------
            File eventsFile = new File(config.getStringValue(
                "default_events_periodic_file"));
            File activitiesFile = new File(config.getStringValue(
                "default_activities_periodic_file"));

            ModelFrequency targetModelFrequency = ModelFrequency.valueOf(config.
                getStringValue("ean_construction_target_model_frequency").
                trim().toUpperCase());

            ModelChange targetModelChange = ModelChange.valueOf(config.
                getStringValue("ean_construction_target_model_change").
                trim().toUpperCase());

            ModelHeadway targetModelHeadway = ModelHeadway.valueOf(config.
                getStringValue("ean_construction_target_model_headway").
                trim().toUpperCase());

            boolean respectChangeStations = config.getBooleanValue("ean_respect_change_stations");
            Set<Integer> changeStationIds = null;
            if(respectChangeStations) {
                changeStationIds = ChangeStationCSV.fromFile(new File(config.getStringValue(
                    "filename_change_station_file")));
            }

            System.err.println("Constructing Event Activity Network...");
            EventActivityNetwork ean = new EventActivityNetwork(lc,
                targetModelFrequency, targetModelChange, targetModelHeadway);
            ean.setPeriodLength(config.getDoubleValue("period_length"));

            Map<Integer, StationLimit> stationLimits = null;
            if(config.getBooleanValue("ean_individual_station_limits")) {
                stationLimits = new StationLimitReader.Builder().setFileName(config.getStringValue("filename_station_limit_file")).build().read();
            }

            state.setValue("ean_model_frequency", targetModelFrequency.toString());
            state.setValue("ean_model_change", targetModelChange.toString());
            state.setValue("ean_model_headway", targetModelHeadway.toString());
            PeriodicEventActivityNetworkGenerator peangen
                = new PeriodicEventActivityNetworkGenerator(ean, null, walkingGraph, changeStationIds, stationLimits, config);

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
            // --- Save Event Activity Network ---------------------------------
            // -----------------------------------------------------------------
            Boolean discardUnusedChangeActivities = config.getBooleanValue(
                "ean_discard_unused_change_activities");
            System.err.print("Saving Event Activity Network... ");
            PeriodicEventActivityNetworkCSV.toFile(ean, eventsFile, activitiesFile,
                discardUnusedChangeActivities);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Save Statistic ----------------------------------------------
            // -----------------------------------------------------------------
            //System.err.print("Saving Statistic... ");
            //StatisticCSV.toFile(statistic, statisticFile);
            //System.err.println("done!");

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
            debugOdPaths.finish(config, ptn, od);
            debugIda.finish(config, ean);
            debugOdActivityPaths.finish(config, ean, od);

        } catch (IOException | DataInconsistentException e) {
            System.err.println();
            System.err.println("An error occured. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        }

    }


}
