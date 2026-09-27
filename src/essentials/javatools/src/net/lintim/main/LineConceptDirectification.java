package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;
import net.lintim.model.LineCollection;
import net.lintim.model.PublicTransportationNetwork;

import java.io.File;
import java.io.IOException;

/**
 * Directifies a line concept with underlying public transportation network
 * and all associated maps, as described in documentation.pdf.
 *
 */
public class LineConceptDirectification {

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
            // --- Load Public Transportation Network --------------------------
            // -----------------------------------------------------------------
            File stationsFile =
                new File(config.getStringValue("default_stops_file"));
            File linksFile =
                new File(config.getStringValue("default_edges_file"));
            File headwayFile =
                new File(config.getStringValue("default_headways_file"));
            File loadsFile =
                new File(config.getStringValue("default_loads_file"));

            System.err.print("Loading Public Transportation Network... ");
            Boolean ptnIsUndirected = config.getBooleanValue("ptn_is_undirected");

            if(!ptnIsUndirected){
                throw new DataInconsistentException("cannot directify an" +
                        "already directified public transportation network");
            }

            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile, linksFile);
            System.err.println("done!");

            if (headwayFile.exists()) {
                System.err.print("Loading Initial Headway Data... ");
                HeadwayCSV.fromFile(ptn, headwayFile);
                System.err.println("done!");
            }

            System.err.print("Loading Load Data... ");
            LoadCSV.fromFile(ptn, loadsFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Line Concept -------------------------------------------
            // -----------------------------------------------------------------
            File lcFile = new File(config.getStringValue("default_lines_file"));
            File lpoolFile = new File(config.getStringValue("default_pool_file"));
            File poolCostFile = new File(config.getStringValue(
                    "default_pool_cost_file"));

            System.err.print("Loading Line Pool... ");

            if(!ptnIsUndirected){
                throw new DataInconsistentException("cannot directify an" +
                        "already directified line concept; by the way: " +
                        "something seems to be wrong with the " +
                        "data and/or configuration: the public " +
                        "transportation network is undirected, so it is " +
                        "impossible to have a directed line concept on top " +
                        "of it");
            }

            LineCollection lc = new LineCollection(ptn);
            LineCollection lpool = new LineCollection(ptn);
            if (lpoolFile.exists()) {
                LinePoolCSV.fromFile(lpool, lpoolFile);
            }
            if (poolCostFile.exists()) {
                LinePoolCostCSV.fromFile(lpool, poolCostFile);
            }
            if (lcFile.exists()) {
                LineConceptCSV.fromFile(lc, lcFile);
            }
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Directify ---------------------------------------------------
            // -----------------------------------------------------------------
            state.setValue("ptn_is_undirected", "false");

            System.err.println("Directifying Public Transportation Network...");
            System.err.print("  Saving directed Public Transportation Network...");
            PublicTransportationNetworkCSV.toFile(ptn, stationsFile,
                    linksFile, false, config);
            System.err.println(" done!");

            if (headwayFile.exists()) {
                System.err.print("  Saving directed Headways...");
                HeadwayCSV.toFile(ptn, headwayFile, false, config);
                System.err.println(" done!");
            }

            System.err.print("  Saving directed Load...");
            LoadCSV.toFile(ptn, loadsFile, false);
            System.err.println(" done!");

            if (lcFile.exists()) {
                System.err.print("  Saving directed Line Concept...");
                LineConceptCSV.toFile(lc, lcFile, false);
                System.err.println(" done!");
            }

            if (lpoolFile.exists()) {
                System.err.print("  Saving directed Line Pool...");
                LinePoolCSV.toFile(lpool, lpoolFile, false);
                System.err.println(" done!");
            }

            if (poolCostFile.exists()) {
                System.err.print("  Saving directed Pool Cost File...");
                LinePoolCostCSV.toFile(lpool, poolCostFile, false);
                System.err.println(" done!");
            }

            // -----------------------------------------------------------------
            // --- Save State --------------------------------------------------
            // -----------------------------------------------------------------
            System.err.print("Saving State Configuration... ");
            ConfigurationCSV.toFile(state, stateFile,
                    "This file is automatically generated. Do not edit!");
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
