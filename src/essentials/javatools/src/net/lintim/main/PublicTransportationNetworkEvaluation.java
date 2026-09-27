package net.lintim.main;

import net.lintim.csv.*;
import net.lintim.evaluator.EventActivityNetworkEvaluator;
import net.lintim.evaluator.PublicTransportationNetworkEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Statistic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Runs the public transportation network evaluation, i.e. basically all methods
 * of {@link EventActivityNetworkEvaluator} once and writes results to console,
 * statistic and diverse histogram files.
 */
public class PublicTransportationNetworkEvaluation {

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
            File stationsFile = new File(config.getStringValue("default_stops_file"));
            File linksFile = new File(config.getStringValue("default_edges_file"));
            File headwayFile = new File(config.getStringValue("default_headways_file"));
            File loadsFile = new File(config.getStringValue("default_loads_file"));

            System.err.print("Loading Public Transportation Network... ");
            PublicTransportationNetwork ptn =
                new PublicTransportationNetwork(config);
            PublicTransportationNetworkCSV.fromFile(ptn, stationsFile, linksFile);
            System.err.println("done!");

            System.err.print("Loading Initial Headway Data... ");
            HeadwayCSV.fromFile(ptn, headwayFile);
            System.err.println("done!");

            System.err.print("Loading Load Data... ");
            LoadCSV.fromFile(ptn, loadsFile);
            System.err.println("done!");

            // -----------------------------------------------------------------
            // --- Load Traveling Time Information -----------------------------
            // -----------------------------------------------------------------
			// Evaluation Routine for PTN is implemented sparately. Thus, this file is obsolete.
			// Especially this part is commented since from modeling view this should not be here
			/*
            System.err.print("Loading Traveling Time Information... ");
			int waiting_time = config.getIntegerValue("sl_waiting_time");
			double acceleration = config.getDoubleValue("sl_acceleration");
			double deceleration = config.getDoubleValue("sl_deceleration");
			double speed = config.getDoubleValue("gen_vehicle_speed");
			TravelingTime tt = new TravelingTime(acceleration,speed,deceleration);
			*/
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
            Double minimalWaitingTime = config.getDoubleValue(
                    "sl_waiting_time");

            System.err.println("Evaluating Origin Destination Matrix...");
            System.err.println("  Computing Statistic...");
            
            //ptn_prop_edges
            if(ptn.isUndirected()){
            	System.err.print("    Undirected Edges: ");
                Integer ptnUndirectedEdges = ptn.getUndirectedLinks().size();
                statistic.setIntegerValue("ptn_prop_edges",
                        ptnUndirectedEdges);
                System.err.println(ptnUndirectedEdges);
            }
            else{
            	System.err.print("    Directed Edges: ");
                Integer ptnDirectedEdges = ptn.getDirectedLinks().size();
                statistic.setIntegerValue("ptn_prop_edges",
                        ptnDirectedEdges);
                System.err.println(ptnDirectedEdges);
            }
            
            //ptn_obj_stops
            System.err.print("    Stops: ");
            Integer ptnStops = ptn.getStations().size();
            statistic.setIntegerValue("ptn_obj_stops", ptnStops);
            System.err.println(ptnStops);
            
            //ptn_time_average
            System.err.print("    PTN time average: ");
            Double ptnTimeAverage =
                PublicTransportationNetworkEvaluator.ptnTimeAverage(ptn, od, minimalWaitingTime);
            statistic.setDoubleValue(
                    "ptn_time_average",ptnTimeAverage);
            System.err.println(ptnTimeAverage);
            
            //ptn_feasible_od
            System.err.print("    PTN feasible OD: ");
            Boolean ptnFeasibleOd = 
            		PublicTransportationNetworkEvaluator.ptnFeasibleOd(ptn, od, minimalWaitingTime);
            statistic.setBooleanValue(
            		"ptn_feasible_od", ptnFeasibleOd);
            System.err.println(ptnFeasibleOd);
           

           /* System.err.print("    Directed Links: ");
            Integer ptnDirectedLinks = ptn.getDirectedLinks().size();
            statistic.setIntegerValue("ptn_directed_links",
                    ptnDirectedLinks);
            System.err.println(ptnDirectedLinks);

            System.err.print("    Undirected Links: ");
            Integer ptnUndirectedLinks = ptn.getUndirectedLinks().size();
            statistic.setIntegerValue("ptn_undirected_links",
                    ptnUndirectedLinks);
            System.err.println(ptnUndirectedLinks);

            System.err.print("    Stations: ");
            Integer ptnStations = ptn.getStations().size();
            statistic.setIntegerValue("ptn_stations", ptnStations);
            System.err.println(ptnStations);

            System.err.print("    Passthrough Stations: ");
            Integer passthroughStations =
                PublicTransportationNetworkEvaluator.passthroughStations(ptn);
            statistic.setIntegerValue("ptn_passthrough_stations",
                    passthroughStations);
            System.err.println(passthroughStations);

            System.err.print("    Dead End Stations: ");
            Integer deadEndStations =
                PublicTransportationNetworkEvaluator.deadEndStations(ptn);
            statistic.setIntegerValue("ptn_dead_end_stations",
                    deadEndStations);
            System.err.println(deadEndStations);

            {
                System.err.print("    General Average Traveling Time Lower Bound: ");
                Double generalAverageTravelingTimeLowerBound =
                    PublicTransportationNetworkEvaluator.
                    averageTravelingTimeLowerBound(ptn, od, 0.0);
                statistic.setDoubleValue(
                        "ptn_general_average_traveling_time_lower_bound",
                        generalAverageTravelingTimeLowerBound);
                System.err.println(generalAverageTravelingTimeLowerBound);
            }*/
	     
    	 /*    {
		System.err.print("     Traveling Time on All Edges: ");
		Double travelingTime = PublicTransportationNetworkEvaluator.calculateTravelingTime(ptn,tt,minimalWaitingTime);
		statistic.setDoubleValue("ptn_sl_traveling_time", travelingTime);
		System.err.println(travelingTime);
	     }*/
/*
            {
                System.err.print("    Wait aware Average Traveling Time Lower Bound: ");
                Double waitAwareAverageTravelingTimeLowerBound =
                    PublicTransportationNetworkEvaluator.
                    averageTravelingTimeLowerBound(ptn, od, minimalWaitingTime);
                statistic.setDoubleValue(
                        "ptn_wait_aware_average_traveling_time_lower_bound",
                        waitAwareAverageTravelingTimeLowerBound);
                System.err.println(waitAwareAverageTravelingTimeLowerBound);
            }*/

            System.err.println("  ...done!");

            System.err.print("  Computing Station Degree Distribution...");
            LinkedHashMap<Integer, Integer> distribution =
                PublicTransportationNetworkEvaluator.
                stationDegreeDistribution(ptn);
            Integer maxDegree = Collections.max(distribution.keySet());
            System.err.println(" done!");

            System.err.print("  Saving Station Degree Distribution...");

            FileWriter fw = new FileWriter(config.getStringValue(
            "default_ptn_station_degree_distribution_file"));

            if(distribution.get(0) != null){
                throw new DataInconsistentException("some station is " +
                "not linked to the network");
            }

            for(int i = 1; i <= maxDegree; i++){
                Integer occurances = distribution.get(i);
                fw.write(i + " " + (occurances == null ? 0 : occurances) + "\n");
            }

            fw.close();

            System.err.println(" done!");

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
