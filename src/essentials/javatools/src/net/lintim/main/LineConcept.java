package net.lintim.main;

import net.lintim.callback.DefaultCallback;
import net.lintim.csv.*;
import net.lintim.evaluator.LineCollectionEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.LineConceptGenerator;
import net.lintim.model.Configuration;
import net.lintim.model.LineCollection;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Statistic;

import java.io.File;
import java.io.IOException;

/**
 * Wraps {@link LineConceptGenerator} to generate a line concept.
 *
 */
public class LineConcept {

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
            File loadsFile = new File(config.getStringValue("default_loads_file"));

            System.err.print("Loading Public Transportation Network... ");
            Boolean ptnIsUndirected = config.getBooleanValue("ptn_is_undirected");
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
            // --- Load Line Pool ----------------------------------------------
            // -----------------------------------------------------------------
            File poolFile = new File(config.getStringValue("default_pool_file"));
            File poolCostFile = new File(config.getStringValue("default_pool_cost_file"));

            System.err.print("Loading Line Pool... ");
            LineCollection lpool = new LineCollection(ptn);
            LinePoolCSV.fromFile(lpool, poolFile);
            LinePoolCostCSV.fromFile(lpool, poolCostFile);
            System.err.println("done!");

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

            buffer.append("generator.LineConceptGenerator");

//            String solver = config.getStringValue("lc_solver").toLowerCase();

//            if(solver.equals("cplex")){
            buffer.append("Cplex");
//            } else {
//                throw new DataInconsistentException("selected solver \""
//                        + solver + "\" not supported");
//            }

            Class<?> lcgenClass = classLoader.loadClass(buffer.toString());

            // -----------------------------------------------------------------
            // --- Compute Line Concept ----------------------------------------
            // -----------------------------------------------------------------
            System.err.println("Computing Line Concept...");
            LineConceptGenerator lcgen = (LineConceptGenerator)lcgenClass.newInstance();
            lcgen.initialize(lpool, config);
            lcgen.solve();
//			LinkedHashSet<Line> toRemove = new LinkedHashSet<Line>();
//			toRemove.addAll(ptnIsUndirected ?
//					lpool.getUndirectedLines() : lpool.getDirectedLines());
//			for(Line line : toRemove){
//				if(line.getFrequency() == 0){
//					lpool.removeLine(line);
//				}
//			}
//			lpool.compactifyLines();
            statistic.setDoubleValue("lc_cost", LineCollectionEvaluator.cost(lpool));

            // -----------------------------------------------------------------
            // --- Save Line Concept -------------------------------------------
            // -----------------------------------------------------------------
            File lcFile = new File(config.getStringValue("default_lines_file"));

            System.err.print("Saving Line Concept... ");
            LineConceptCSV.toFile(lpool, lcFile, ptnIsUndirected);
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
