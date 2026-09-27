package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.evaluator.OriginDestinationMatrixEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.OriginDestinationMatrixGenerator;
import net.lintim.model.Configuration;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Statistic;

import java.io.File;
import java.io.IOException;

/**
 * Wraps {@link OriginDestinationMatrixGenerator} to derive an origin
 * destination matrix from a reference by randomization.
 *
 */
public class RandomizeOriginDestinationMatrix {

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
            File statisticFile = new File(config.getStringValue("default_statistic_file"));
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
            // --- Main Logic --------------------------------------------------
            // -----------------------------------------------------------------
            Double odOverallSum = OriginDestinationMatrixEvaluator.overallSum(od);

            System.err.print("Putting noise on Origin Destination Matrix... ");
            OriginDestinationMatrixGenerator.generateNoisyMatrix(od,
                    config.getDoubleValue("od_noise_level"));
            System.err.println("done!");

            System.err.print("Rescaling Origin Destination Matrix... ");
            OriginDestinationMatrixGenerator.rescale(od, odOverallSum);
            System.err.println("done!");

            if(config.getBooleanValue("od_values_integral")){
                System.err.print("Rounding Origin Destination Matrix... ");
                OriginDestinationMatrixGenerator.roundEntries(od);
                System.err.println("done!");
            }

            statistic.setDoubleValue("od_overall_sum",
                    OriginDestinationMatrixEvaluator.overallSum(od));

            // -----------------------------------------------------------------
            // --- Save Origin Destination Matrix ------------------------------
            // -----------------------------------------------------------------
            System.err.print("Saving Origin Destination Matrix... ");
            OriginDestinationMatrixCSV.toFile(od, odFile);
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
        }

    }

}
