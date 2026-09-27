package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.exception.DataInconsistentException;
import net.lintim.graphviz.EventActivityNetworkGraphviz;
import net.lintim.model.Configuration;
import net.lintim.model.EventActivityNetwork;
import net.lintim.model.LineCollection;
import net.lintim.model.PublicTransportationNetwork;

import java.io.File;
import java.io.IOException;

/**
 * Generates GraphViz data for event activity network resp. timetable
 * visualization.
 *
 */
public class PlotEventActivityNetwork {

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

            System.err.print("Loading Initial Headway Data... ");
            HeadwayCSV.fromFile(ptn, headwayFile);
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
            File eventsFile =
                new File(config.getStringValue("default_events_periodic_file"));
            File activitiesFile =
                new File(config.getStringValue("default_activities_periodic_file"));

            System.err.print("Loading Event Activity Network... ");
            EventActivityNetwork ean = new EventActivityNetwork(lc, config);
            PeriodicEventActivityNetworkCSV.fromFile(ean, eventsFile,
                    activitiesFile);
            ean.setPeriodLength(config.getDoubleValue("period_length"));
            System.err.println("done!");

            // TODO: modify this as soon as config interface ready
            // -----------------------------------------------------------------
            // --- Load Timetable (if exists and should be used) ---------------
            // -----------------------------------------------------------------
            File fromTimetableFile =
                new File(config.getStringValue("default_timetable_periodic_file"));

            if(fromTimetableFile.exists()){
                System.err.print("Loading Timetable... ");
                TimetableCSV.fromFile(ean, fromTimetableFile);
                System.err.println("done!");
            }

            // -----------------------------------------------------------------
            // --- Plot Timetable ----------------------------------------------
            // -----------------------------------------------------------------
            File graphvizFile =
                new File(config.getStringValue("default_ean_graphviz_file"));
            System.err.print("Creating Event Activity Network Graphviz File...");
            EventActivityNetworkGraphviz.toFile(ean, graphvizFile);
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
        }

    }

}
