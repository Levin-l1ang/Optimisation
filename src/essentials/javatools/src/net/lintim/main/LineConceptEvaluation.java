package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.evaluator.LineCollectionEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Runs the line concept evaluation, i.e. basically all methods of
 * {@link LineConceptEvaluation} once and writes results to console, statistic
 * as well as diverse histogram files.
 */
public class LineConceptEvaluation {

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
            File stationsFile =
                new File(config.getStringValue("default_stops_file"));
            File linksFile =
                new File(config.getStringValue("default_edges_file"));
            File headwayFile =
                new File(config.getStringValue("default_headways_file"));
            File loadsFile =
                new File(config.getStringValue("default_loads_file"));

            System.err.print("Loading Public Transportation Network... ");
            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile,
                    linksFile);
            System.err.println("done!");

            System.err.print("Loading Initial Headway Data... ");
            HeadwayCSV.fromFile(ptn, headwayFile);
            System.err.println("done!");

            System.err.print("Loading Load Data... ");
            LoadCSV.fromFile(ptn, loadsFile);
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
            File lcFile = new File(config.getStringValue("default_lines_file"));
            File poolCostFile = new File(config.getStringValue(
                    "default_pool_cost_file"));

            System.err.print("Loading Line Pool... ");
            LineCollection lc = new LineCollection(ptn);
            LineConceptCSV.fromFile(lc, lcFile);
            LinePoolCostCSV.fromFile(lc, poolCostFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Evaluate ----------------------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Evaluating Line Concept...");
            System.err.println("  Computing Statistic...");
            // - - Line Concept - - - - - - - - - - - - - - - - - - - - - - - -
           /* System.err.print("    Cost: ");
            Double lcCost = LineCollectionEvaluator.cost(lc);
            statistic.setDoubleValue("lc_cost", lcCost);
            System.err.println(lcCost);

            System.err.print("    Used Undirected Lines: ");
            Integer lcUsedUndirectedLines =
                LineCollectionEvaluator.undirectedLinesUsedCount(lc);
            statistic.setIntegerValue("lc_used_undirected_lines",
                    lcUsedUndirectedLines);
            System.err.println(lcUsedUndirectedLines);

            System.err.print("    Used Directed Lines: ");
            Integer lcUsedDirectedLines =
                LineCollectionEvaluator.directedLinesUsedCount(lc);
            statistic.setIntegerValue("lc_used_directed_lines", lcUsedDirectedLines);
            System.err.println(lcUsedDirectedLines);

            System.err.print("    Lines Pair Intersection Connected: ");
            Boolean lcLinePairIntersectionConnected =
                LineCollectionEvaluator.linePairIntersectionsConnected(lc, true);
            statistic.setBooleanValue("lc_line_pair_intersections_connected",
                    lcLinePairIntersectionConnected);
            System.err.println(lcLinePairIntersectionConnected ? "true" : "false");

            System.err.print("    Average Traveling Time Lower Bound: ");
            Double lcAverageTravelingTimeLowerBound =
                LineCollectionEvaluator.averageTravelingTimeLowerBound(lc,
                        od, config.getDoubleValue(
                        "ean_default_minimal_waiting_time"),
                        config.getDoubleValue(
                        "ean_default_minimal_change_time"), true);
            statistic.setDoubleValue("lc_average_traveling_time_lower_bound",
                    lcAverageTravelingTimeLowerBound);
            System.err.println(lcAverageTravelingTimeLowerBound);*/
            
            
            System.err.print("    Line-Concept Average Time: ");
            Double lcTimeAverage =
                    LineCollectionEvaluator.lineCollectionTimeAverage(lc,
                            od, config.getDoubleValue(
                            "ptn_stop_waiting_time"));
                statistic.setDoubleValue("lc_time_average",
                        lcTimeAverage);
                System.err.println(lcTimeAverage);

            System.err.println("  ... done!");
            
            System.err.print("    Line-Concept Minimum Length: ");
            Double min_length =
                    LineCollectionEvaluator.minLength(lc);
                statistic.setDoubleValue("lc_min_length",
                        min_length);
            System.err.println(min_length);
            
            System.err.print("    Line-Concept Minimum Distance: ");
            Double min_dist =
                    LineCollectionEvaluator.minDistance(lc);
                statistic.setDoubleValue("lc_min_distance",
                        min_dist);
            System.err.println(min_dist);
            
            System.err.print("    Line-Concept Minimum Edges: ");
            Double min_edges =
                    LineCollectionEvaluator.minEdges(lc);
                statistic.setDoubleValue("lc_min_edges",
                        min_edges);
            System.err.println(min_edges);
                
            System.err.print("    Line-Concept Average Length: ");
            Double av_length =
                    LineCollectionEvaluator.averageLength(lc);
                statistic.setDoubleValue("lc_average_length",
                        av_length);
            System.err.println(av_length);
            
            System.err.print("    Line-Concept Average Distance: ");
            Double av_dist =
                    LineCollectionEvaluator.averageDistance(lc);
                statistic.setDoubleValue("lc_average_distance",
                        av_dist);
            System.err.println(av_dist);
            
            System.err.print("    Line-Concept Average Edges: ");
            Double av_edges =
                    LineCollectionEvaluator.averageEdges(lc);
                statistic.setDoubleValue("lc_average_edges",
                        av_edges);
            System.err.println(av_edges);
            
            System.err.print("    Line-Concept Variance Length: ");
            Double var_length =
                    LineCollectionEvaluator.varianceLength(lc);
                statistic.setDoubleValue("lc_var_length",
                        var_length);
            System.err.println(var_length);
            
            System.err.print("    Line-Concept Variance Distance: ");
            Double var_dist =
                    LineCollectionEvaluator.varianceDistance(lc);
                statistic.setDoubleValue("lc_var_distance",
                        var_dist);
            System.err.println(var_dist);
            
            System.err.print("    Line-Concept Variance Edges: ");
            Double var_edges =
                    LineCollectionEvaluator.varianceEdges(lc);
                statistic.setDoubleValue("lc_var_edges",
                        var_edges);
            System.err.println(var_edges);

          /*  {
                System.err.print("  Computing Undirected Line Length " +
                "Distribution...");

                LinkedHashMap<Integer, Integer> distribution =
                    LineCollectionEvaluator.undirectedLineLengthDistribution(
                            lc, true);

                Integer maxSize = Collections.max(
                        distribution.keySet());
                System.err.println(" done!");

                System.err.print("  Saving Undirected Line Length " +
                "Distribution...");

                FileWriter fw = new FileWriter(config.getStringValue(
                        "filename_lc_undirected_line_length_distribution"));

                if(distribution.get(0) != null){
                    throw new DataInconsistentException("some station is " +
                    "not linked to the network");
                }

                for(int i = 1; i <= maxSize; i++){
                    Integer occurances = distribution.get(i);
                    fw.write(i + " " + (occurances == null ? 0 : occurances) +
                            "\n");
                }

                fw.close();

                System.err.println(" done!");
            }

            {
                System.err.print("  Computing Undirected Link Undirected Line " +
                "Distribution...");

                LinkedHashMap<Integer, Integer> distribution =
                    LineCollectionEvaluator.
                    undirectedLinkUndirectedLineDistribution(lc, true);

                Integer maxSize = Collections.max(
                        distribution.keySet());
                System.err.println(" done!");

                System.err.print("  Saving Undirected Link Undirected Line " +
                "Distribution...");

                FileWriter fw = new FileWriter(config.getStringValue(
                    "filename_lc_undirected_link_undirected_line_distribution"));

                if(distribution.get(0) != null){
                    throw new DataInconsistentException("some edge has " +
                    "no line that passes over");
                }

                for(int i = 1; i <= maxSize; i++){
                    Integer occurances = distribution.get(i);
                    fw.write(i + " " + (occurances == null ? 0 : occurances) +
                            "\n");
                }

                fw.close();

                System.err.println(" done!");
            }

            {
                System.err.print("  Computing Station Undirected Line " +
                "Distribution...");

                LinkedHashMap<Integer, Integer> distribution =
                    LineCollectionEvaluator.
                    stationUndirectedLineDistribution(lc, true);

                Integer maxSize = Collections.max(
                        distribution.keySet());
                System.err.println(" done!");

                System.err.print("  Station Link Undirected Line " +
                "Distribution...");

                FileWriter fw = new FileWriter(config.getStringValue(
                    "filename_lc_station_undirected_line_distribution"));

                if(distribution.get(0) != null){
                    throw new DataInconsistentException("some station has " +
                    "no line that passes over");
                }

                for(int i = 1; i <= maxSize; i++){
                    Integer occurances = distribution.get(i);
                    fw.write(i + " " + (occurances == null ? 0 : occurances) +
                            "\n");
                }

                fw.close();

                System.err.println(" done!");
            }

            System.err.println("... done!");

            // - - Line Pool - - - - - - - - - - - - - - - - - - - - - - - - - -
            System.err.println("Evaluating Line Pool...");
            System.err.println("  Computing Statistic...");
            System.err.print("    Undirected Lines: ");
            Integer lpoolUndirectedLines =
                LineCollectionEvaluator.undirectedLinesCount(lc);
            statistic.setIntegerValue("lpool_undirected_lines",
                    lpoolUndirectedLines);
            System.err.println(lpoolUndirectedLines);

            System.err.print("    Directed Lines: ");
            Integer lpoolDirectedLines =
                LineCollectionEvaluator.directedLinesCount(lc);
            statistic.setIntegerValue("lpool_directed_lines", lpoolDirectedLines);
            System.err.println(lpoolDirectedLines);


            System.err.print("    Line Pair Intersections Connected: ");
            Boolean lpoolLinePairIntersectionsConnected =
                LineCollectionEvaluator.linePairIntersectionsConnected(lc, false);
            statistic.setBooleanValue("lpool_line_pair_intersections_connected",
                    lpoolLinePairIntersectionsConnected);
            System.err.println(lpoolLinePairIntersectionsConnected ?
                    "true" : "false");

            if(config.getBooleanValue(
                    "lc_evaluate_lpool_average_traveling_time_lower_bound")){

                System.err.print("    Average Traveling Time Lower Bound: ");

                Double lpoolAverageTravelingTimeLowerBound =
                    LineCollectionEvaluator.averageTravelingTimeLowerBound(lc,
                            od, config.getDoubleValue(
                            "ean_default_minimal_waiting_time"),
                            config.getDoubleValue(
                            "ean_default_minimal_change_time"), false);
                statistic.setDoubleValue(
                        "lpool_average_traveling_time_lower_bound",
                        lpoolAverageTravelingTimeLowerBound);
                System.err.println(lpoolAverageTravelingTimeLowerBound);

            } else {
                System.err.println("  Set " +
                "\"lc_evaluate_lpool_average_traveling_time_lower_bound\" to " +
                "\"true\" to calculate it.");
            }

            System.err.println("  ... done!");

            {
                System.err.print("  Computing Undirected Line Length " +
                "Distribution...");

                LinkedHashMap<Integer, Integer> distribution =
                    LineCollectionEvaluator.undirectedLineLengthDistribution(
                            lc, false);

                Integer maxSize = Collections.max(
                        distribution.keySet());
                System.err.println(" done!");

                System.err.print("  Saving Undirected Line Length " +
                "Distribution...");

                FileWriter fw = new FileWriter(config.getStringValue(
                        "filename_lpool_undirected_line_length_distribution"));

                if(distribution.get(0) != null){
                    throw new DataInconsistentException("some station is " +
                    "not linked to the network");
                }

                for(int i = 1; i <= maxSize; i++){
                    Integer occurances = distribution.get(i);
                    fw.write(i + " " + (occurances == null ? 0 : occurances) +
                            "\n");
                }

                fw.close();

                System.err.println(" done!");
            }

            {
                System.err.print("  Computing Undirected Link Undirected Line " +
                "Distribution...");

                LinkedHashMap<Integer, Integer> distribution =
                    LineCollectionEvaluator.
                    undirectedLinkUndirectedLineDistribution(lc, false);

                Integer maxSize = Collections.max(
                        distribution.keySet());
                System.err.println(" done!");

                System.err.print("  Saving Undirected Link Undirected Line " +
                "Distribution...");

                FileWriter fw = new FileWriter(config.getStringValue(
                        "filename_lpool_undirected_link_undirected_line_" +
                        "distribution"));

                if(distribution.get(0) != null){
                    throw new DataInconsistentException("some edge has " +
                    "no line that passes over at line concept level");
                }

                for(int i = 1; i <= maxSize; i++){
                    Integer occurances = distribution.get(i);
                    fw.write(i + " " + (occurances == null ? 0 : occurances) +
                            "\n");
                }

                fw.close();

                System.err.println(" done!");
            }

            {
                System.err.print("  Computing Station Undirected Line " +
                "Distribution...");

                LinkedHashMap<Integer, Integer> distribution =
                    LineCollectionEvaluator.
                    stationUndirectedLineDistribution(lc, false);

                Integer maxSize = Collections.max(
                        distribution.keySet());
                System.err.println(" done!");

                System.err.print("  Station Link Undirected Line " +
                "Distribution...");

                FileWriter fw = new FileWriter(config.getStringValue(
                        "filename_lpool_station_undirected_line_" +
                        "distribution"));

                if(distribution.get(0) != null){
                    throw new DataInconsistentException("some station has " +
                    "no line that passes over at line concept level");
                }

                for(int i = 1; i <= maxSize; i++){
                    Integer occurances = distribution.get(i);
                    fw.write(i + " " + (occurances == null ? 0 : occurances) +
                            "\n");
                }

                fw.close();

                System.err.println(" done!");
            }

            System.err.println("... done!");*/

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
