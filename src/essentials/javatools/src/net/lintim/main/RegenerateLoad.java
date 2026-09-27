package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.debug.DebugOriginDestinationLinkPaths;
import net.lintim.dump.IterationProgressCounterDump;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.LoadGenerator;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Wraps {@link LoadGenerator} to generate a load from either a passenger
 * distribution in a public transportation network or an event activity network.
 */

public class RegenerateLoad {

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
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Enable Relevant Debugging -----------------------------------
            // -----------------------------------------------------------------
            DebugOriginDestinationLinkPaths debugOdPaths =
                new DebugOriginDestinationLinkPaths(config);

            // -----------------------------------------------------------------
            // --- Load Public Transportation Network --------------------------
            // -----------------------------------------------------------------
            File stationsFile = new File(config.getStringValue("default_stops_file"));
            File linksFile = new File(config.getStringValue("default_edges_file"));

            System.err.print("Loading Public Transportation Network... ");
            Boolean ptnIsUndirected = config.getBooleanValue("ptn_is_undirected");
            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile,
                    linksFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Optional Files -----------------------------------------
            // -----------------------------------------------------------------
            LoadGenerator.LoadGeneratorModel generatorModel =
                LoadGenerator.LoadGeneratorModel
                    .valueOf(config.getStringValue("load_generator_model"));

            File loadsFile = new File(config
                    .getStringValue("default_loads_file"));
            File linesFile = new File(config
                    .getStringValue("default_lines_file"));

            EventActivityNetwork ean = null;
            OriginDestinationMatrix od = null;

            if (generatorModel == LoadGenerator.LoadGeneratorModel.LOAD_FROM_PTN) {
                File odFile = new File(config.getStringValue("default_od_file"));

                System.err.print("Loading Origin Destination Matrix... ");
                od = new OriginDestinationMatrix(ptn);
                OriginDestinationMatrixCSV.fromFile(od, odFile);
                System.err.println("done!");

            } else if (generatorModel == LoadGenerator.LoadGeneratorModel.LOAD_FROM_EAN) {
                File eventsFile = new File(config
                        .getStringValue("default_events_periodic_file"));
                File activitiesFile = new File(config
                        .getStringValue("default_activities_periodic_file"));

                System.err.print("Loading Line Concept... ");
                LineCollection lc = new LineCollection(ptn);
                LineConceptCSV.fromFile(lc, linesFile);
                System.err.println("done!");

                System.err.print("Loading Event Activity Network... ");
                ean = new EventActivityNetwork(lc, config);
                PeriodicEventActivityNetworkCSV.fromFile(ean, eventsFile,
                        activitiesFile);
                ean.setPeriodLength(config.getDoubleValue("period_length"));
                System.err.println("done!");

            } else {
                throw new DataInconsistentException(
                        "load_generator_model invalid");
            }

            // -----------------------------------------------------------------
            // --- Generate Load -----------------------------------------------
            // -----------------------------------------------------------------
            LoadGenerator loadGen = new LoadGenerator(ptn, od, ean, config);
            loadGen.setIterationProgressCounter(new IterationProgressCounterDump());
            System.err.print("Computing and saving Load..."
                + (generatorModel == LoadGenerator.LoadGeneratorModel.LOAD_FROM_PTN ? "\n"
                                    : " "));
            loadGen.computeLinkLoad();

            // -----------------------------------------------------------------
            // --- Save Load ---------------------------------------------------
            // -----------------------------------------------------------------
            LoadCSV.toFile(ptn, loadsFile, ptnIsUndirected);
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
