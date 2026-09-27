package net.lintim.main;

import net.lintim.callback.DefaultCallback;
import net.lintim.csv.*;
import net.lintim.dump.IterationProgressCounterDump;
import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicTimetableOdpespGenerator;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Wraps {@link PeriodicTimetableOdpespGenerator} to generate a periodic
 * timetable by solving the odpesp.
 *
 */

public class PeriodicTimetableOdpesp {

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

            System.err.print("Loading Event Activity Network... ");
            EventActivityNetwork ean = new EventActivityNetwork(lc, config);
            PeriodicEventActivityNetworkCSV.fromFile(ean, eventsFile,
                    activitiesFile);
            ean.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Timetable (if exists and should be used) ---------------
            // -----------------------------------------------------------------
            if(config.getBooleanValue("tim_odpesp_use_old_timetable")){
                File fromTimetableFile = new File(config.getStringValue(
                        "default_timetable_periodic_file"));

                if(fromTimetableFile.exists()){
                    System.err.print("Loading Timetable... ");
                    TimetableCSV.fromFile(ean, fromTimetableFile);
                    System.err.println("done!");
                }
                else{
                    throw new DataInconsistentException(
                            "tim_odpesp_use_old_timetable is true, but no " +
                            "timetable file available");
                }
            }

            // -----------------------------------------------------------------
            // --- Load Passenger Paths (if exists and should be used) ---------
            // -----------------------------------------------------------------
            if(config.getBooleanValue("tim_odpesp_use_old_passenger_paths")){
                File odPathsFile = new File(config.getStringValue(
                            "default_debug_od_activity_paths_file"));

                if(odPathsFile.exists()){
                    System.err.print("Loading Passenger Paths... ");
                    OriginDestinationPathActivityCSV.fromFile(ean, od, odPathsFile);
                    System.err.println("done!");
                }
                else{
                    throw new DataInconsistentException(
                            "tim_odpesp_use_old_passenger_paths is true, but no " +
                            "activity paths file available");
                }
            }

            // -----------------------------------------------------------------
            // --- Compute Topology --------------------------------------------
            // -----------------------------------------------------------------
//            System.err.print("Computing Public Transportation Network Topology... ");
//            PublicTransportationNetworkTopology ptnTop =
//                new PublicTransportationNetworkTopology(ptn, config);
//            System.err.println("done!");
//
//            System.err.print("Computing Line Concept Topology... ");
//            LineCollectionTopology lcTop = new LineCollectionTopology(ptnTop, lc);
//            System.err.println("done!");
//
//            System.err.print("Event Activity Network Topology... ");
//            EventActivityNetworkTopology eanTop =
//                new EventActivityNetworkTopology(ean, lcTop, ptnTop);
//            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Solver Bureaucracy ------------------------------------------
            // -----------------------------------------------------------------
            DefaultCallback.cleanup();

            ClassLoader classLoader = PeriodicTimetable.class.getClassLoader();

            StringBuffer buffer = new StringBuffer();
            String[] classNameParts =
                PeriodicTimetable.class.getName().split("\\.");

            for(int i=0; i<classNameParts.length-2; i++){
                buffer.append(classNameParts[i] + ".");
            }

            buffer.append("generator.PeriodicTimetableOdpespGenerator");

            String solver =
                config.getStringValue("tim_odpesp_solver").toLowerCase();

            if(solver.equals("cplex")){
                buffer.append("Cplex");
            } else {
                throw new DataInconsistentException("selected solver \""
                        + solver + "\" not supported");
            }

            Class<?> prtimgenClass = classLoader.loadClass(buffer.toString());

            // -----------------------------------------------------------------
            // --- Compute Timetable -------------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Computing Periodic Timetable...");
            PeriodicTimetableOdpespGenerator prtimgen =
                (PeriodicTimetableOdpespGenerator) prtimgenClass.newInstance();
            prtimgen.setIterationProgressCounter(new IterationProgressCounterDump());
            prtimgen.initialize(ean, od, // eanTop,
                    config);
            double logarithmicCyclebaseWidth = prtimgen.logarithmicBase10CyclebasisWidth();
            System.err.println("Logarithmic (base 10) cyclebase width: " +
                    logarithmicCyclebaseWidth);
            statistic.setDoubleValue("tim_logarithmic_base10_cyclebase_width",
                    logarithmicCyclebaseWidth);
            prtimgen.solve();

            Double averageTravelingTime = PeriodicTimetableEvaluator.
            averageTravelingTime(ean);
            Double weightedSlack = PeriodicTimetableEvaluator.
            weightedSlackTime(ean);

            System.err.println("Average traveling time: " + averageTravelingTime);
            System.err.println("Weighted Slack: " + weightedSlack);

            statistic.setDoubleValue("tim_weighted_slack_time", weightedSlack);
            statistic.setDoubleValue("tim_average_traveling_time",
                    averageTravelingTime);
            System.err.println("... done!");
            // -----------------------------------------------------------------
            // --- Save Timetable ----------------------------------------------
            // -----------------------------------------------------------------
            File toTimetableFile = new File(
                    config.getStringValue("default_timetable_periodic_file"));
            File toDurationsFile = new File(
                    config.getStringValue("default_durations_periodic_file"));

            System.err.print("Saving Timetable... ");
            TimetableCSV.toFile(ean, toTimetableFile);
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
        } catch (ClassNotFoundException e) {
            System.err.println();
            System.err.println("An error occured. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        } catch (InstantiationException e) {
            System.err.println();
            System.err.println("An error occured. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        } catch (IllegalAccessException e) {
            System.err.println();
            System.err.println("An error occured. See stacktrace below.");
            e.printStackTrace();
            System.exit(1);
        }

    }

}
