package net.lintim.main;

import net.lintim.callback.DefaultCallback;
import net.lintim.csv.*;
import net.lintim.dump.IterationProgressCounterDump;
import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicTimetableGenerator;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Wraps {@link PeriodicTimetableGenerator} to generate a periodic timetable.
 *
 */
public class PeriodicTimetable {

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
            config.setValue("ean_use_timetable", "false");
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
            File stationsFile = new File(config.getStringValue(
                    "default_stops_file"));
            File linksFile = new File(config.getStringValue(
                    "default_edges_file"));
            File headwayFile = new File(config.getStringValue(
                    "default_headways_file"));

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
            if(config.getBooleanValue("tim_use_old_solution") ||
                    config.getBooleanValue("tim_fix_old_modulo")){
                File fromTimetableFile = new File(config.getStringValue(
                        "default_timetable_periodic_file"));

                if(fromTimetableFile.exists()){
                    System.err.print("Loading Timetable... ");
                    TimetableCSV.fromFile(ean, fromTimetableFile);
                    System.err.println("done!");
                }
                else{
                    throw new DataInconsistentException(
                            "time_use_old_solution is true, but no timetable " +
                            "file available");
                }
            }

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

            buffer.append("generator.PeriodicTimetableGenerator");

            String solver = config.getStringValue("tim_solver").toLowerCase();

            if(solver.equals("gurobi")){
                buffer.append("Gurobi");
            } else if(solver.equals("cplex")){
                buffer.append("Cplex");
            } else if(solver.equals("xpress")){
                buffer.append("Xpress");
            } else {
                throw new DataInconsistentException("selected solver \""
                        + solver + "\" not supported");
            }

            Class<?> ptimgenClass = classLoader.loadClass(buffer.toString());

            // -----------------------------------------------------------------
            // --- Compute Timetable -------------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Preparing Periodic Timetable Calculation...");
            PeriodicTimetableGenerator ptimgen =
                (PeriodicTimetableGenerator)ptimgenClass.newInstance();
            ptimgen.setIterationProgressCounter(new IterationProgressCounterDump());
            ptimgen.initialize(ean, config);
            double logarithmicCyclebaseWidth = ptimgen.logarithmicBase10CyclebasisWidth();
            System.err.println("Logarithmic (base 10) cyclebase width: " +
                    logarithmicCyclebaseWidth);
            statistic.setDoubleValue("ean_logarithmic_base10_cyclebase_width",
                    logarithmicCyclebaseWidth);
            System.err.println("... preparation done!");
            System.err.println("Computing Periodic Timetable...");
            ptimgen.solve();
            System.err.println("... computing done!");

            // -----------------------------------------------------------------
            // --- Evalute Timetable -------------------------------------------
            // -----------------------------------------------------------------
            Double averageTravelingTime =
                PeriodicTimetableEvaluator.averageTravelingTime(ean);
            Double weightedSlack =
                PeriodicTimetableEvaluator.weightedSlackTime(ean);

            System.err.println("Average traveling time: " + averageTravelingTime);
            System.err.println("Weighted Slack: " + weightedSlack);

            statistic.setDoubleValue("tim_weighted_slack_time", weightedSlack);
            statistic.setDoubleValue("tim_average_traveling_time",
                    averageTravelingTime);

            // -----------------------------------------------------------------
            // --- Save Timetable ----------------------------------------------
            // -----------------------------------------------------------------
            File toTimetableFile = new File(config.getStringValue(
            "default_timetable_periodic_file"));
            File toDurationsFile = new File(config.getStringValue(
            "default_durations_periodic_file"));

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
