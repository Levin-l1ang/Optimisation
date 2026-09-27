package net.lintim.main;

import net.lintim.csv.ConfigurationCSV;
import net.lintim.csv.OriginDestinationMatrixCSV;
import net.lintim.csv.PublicTransportationNetworkCSV;
import net.lintim.csv.StatisticCSV;
import net.lintim.evaluator.OriginDestinationMatrixEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Statistic;

import java.io.File;
import java.io.IOException;

/**
 * Runs the origin destination matrix evaluation, i.e. basically all methods of
 * {@link OriginDestinationMatrixEvaluator} once and writes results to
 * console, statistic as well as diverse histogram files.
 */
public class OriginDestinationMatrixEvaluation {

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
            File statisticFile = new File(config.
                    getStringValue("default_statistic_file"));
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

            System.err.print("Loading Public Transportation Network... ");
            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile, linksFile);
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
            // --- Evaluate ----------------------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Evaluating Origin Destination Matrix...");
            System.err.println("  Computing Statistic...");
            System.err.print("    Sum over all entries: ");

            Double overallSum = OriginDestinationMatrixEvaluator.overallSum(od);
            System.err.println(overallSum);
            statistic.setDoubleValue("od_prop_overall_sum", overallSum);
            {
                Integer entriesGreaterZero =
                    OriginDestinationMatrixEvaluator.entriesGreaterZero(od);
                System.err.print("    Entries greater zero: ");
                System.err.println(entriesGreaterZero);
                statistic.setIntegerValue("od_prop_entries_greater_zero",
                        entriesGreaterZero);
            }
            /**
            String filenameIncrements = config.getStringValue(
            "filename_od_relative_partial_sum");

            System.err.print("  Computing Ordered Relative Increments...");
            double[] orderedIncrements =
                OriginDestinationMatrixEvaluator.orderedEntries(od);
            System.err.println(" done!");

            System.err.print("  Saving Ordered Relative Increments...");
            FileWriter fw = new FileWriter(new File(filenameIncrements));

            Double currentSum = 0.0;
            fw.write("0.0\n");

            for(int i=orderedIncrements.length-1; i>=0; i--){
                currentSum += orderedIncrements[i];
                fw.write(currentSum/overallSum + "\n");
            }

            fw.close();
            System.err.println(" done!");
            **/

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
        }

    }

}
