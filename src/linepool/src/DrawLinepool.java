import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.LinTimException;
import net.lintim.exception.OutputFileException;
import net.lintim.io.ConfigReader;
import net.lintim.io.LineReader;
import net.lintim.io.RidepoolingPoolReader;
import net.lintim.io.PTNReader;
import net.lintim.model.RidepoolingPool;
import net.lintim.model.RidepoolingArea;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.model.Graph;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.*;
import java.util.Map;

public class DrawLinepool {

    private static final Logger logger = new Logger(DrawLinepool.class);

	public static void main(String[] args) {
		if (args.length < 1) {
			throw new ConfigNoFileNameGivenException();
		}

		try {
			logger.info("Begin reading configuration");
			boolean draw_line_concept = false;
            draw_line_concept = args.length >= 2 && args[1].equals("lc");
			boolean draw_ride_concept = false;
            draw_ride_concept = args.length >= 2 && args[1].equals("rc");
			boolean draw_line_pool = false;
            draw_line_pool = args.length >= 2 && args[1].equals("lpool");
			boolean draw_ride_pool = false;
            draw_ride_pool = args.length >= 2 && args[1].equals("rpool");

			Config config = new ConfigReader.Builder(args[0]).build().read();

			logger.debug("Set variables... ");
			boolean directed = !config.getBooleanValue("ptn_is_undirected");
			boolean colored = false;
			double drawingFactor = 1.0;
			double correctionFactor = config.getDoubleValue("gen_conversion_coordinates");
			if (draw_ride_pool) {
				colored = config.getBooleanValue("rpool_draw_colored");
				drawingFactor = config.getDoubleValue("rpool_draw_coordinate_factor");
			}
			else if (draw_ride_concept){
				colored = config.getBooleanValue("rc_draw_colored");
				drawingFactor = config.getDoubleValue("rc_draw_coordinate_factor");
			}
			else if (draw_line_concept) {
				colored = config.getBooleanValue("lc_draw_colored");
				drawingFactor = config.getDoubleValue("lpool_coordinate_factor");
			}
			else if (draw_line_pool) {
				colored = config.getBooleanValue("lpool_draw_colored");
				drawingFactor = config.getDoubleValue("lpool_coordinate_factor");
			}



			logger.info("Finished reading configuration");

			logger.info("Begin reading input files");
			File stop_file = new File(config.getStringValue("default_stops_file"));
			File edge_file = new File(config.getStringValue("default_edges_file"));
			File pool_file = new File(config.getStringValue("default_lines_file"));
			String rpool_file = "";
			File dot_file = new File(config.getStringValue("default_line_graph_file"));
			PTN ptn = new PTN(directed);
			Graph<Stop, Link> ptnGraph = new SimpleMapGraph();
			if(draw_line_concept){
				pool_file = new File(config.getStringValue("default_lines_file"));
				dot_file = new File(config.getStringValue("default_line_graph_file"));
				PTNCSV.fromFile(ptn, stop_file, edge_file);
				Line.setDirected(directed);
			}
			else if (draw_line_pool){
				pool_file = new File(config.getStringValue("default_pool_file"));
				dot_file = new File(config.getStringValue("default_pool_graph_file"));
				PTNCSV.fromFile(ptn, stop_file, edge_file);
				Line.setDirected(directed);
			}
			else if (draw_ride_concept){
				rpool_file = config.getStringValue("filename_rc_file");
				dot_file = new File(config.getStringValue("filename_rc_graph"));
				ptnGraph = new PTNReader.Builder().build().read();
			}
			else if (draw_ride_pool){
				rpool_file = config.getStringValue("filename_rpool_file");
				dot_file = new File(config.getStringValue("filename_rpool_graph"));
				ptnGraph = new PTNReader.Builder().build().read();
			}


			LinePool pool = new LinePool(ptn);
			RidepoolingPool rpool = new RidepoolingPool(0);
			if (draw_line_pool || draw_line_concept) {
				LinePoolCSV.fromFile(pool, pool_file, draw_line_concept);
			}
			else if (draw_ride_concept) {
				rpool = new RidepoolingPoolReader.Builder(ptnGraph).setRidepoolFileName(rpool_file).readNumberVehicles(true).build().read();
			}
			else if (draw_ride_pool) {
				rpool = new RidepoolingPoolReader.Builder(ptnGraph).setRidepoolFileName(rpool_file).readNumberVehicles(false).build().read();
			}
			logger.info("Finished reading input files");

			logger.info("Begin writing output files");
			if (draw_line_concept || draw_line_pool) {
				LinePoolCSV.toDOTFile(pool, dot_file, drawingFactor, colored);
			}
			else if (draw_ride_concept || draw_ride_pool) {
				writeRpoolDotFile(ptnGraph, drawingFactor, dot_file, rpool, colored, correctionFactor, draw_ride_pool);
			}
			logger.info("Finished writing output files");

		} catch (IOException e) {
			throw new LinTimException(e.getMessage());
		}
	}

	/**
	 * Transform the given ridepooling pool into the dot format and write it to the given output file
	 *
	 * @param ptn               the ptn to draw
	 * @param conversionFactor  factor to multiply the x and y coordinates with
	 * @param outputFileName    the output file to write. Will be in dot format for further processing with
	 *                          the graphviz utility
	 * @param rpool             the ridepooling pool to draw
	 * @param colored           whether the areas are colored or not
	 * @param correctionFactor  needed to set position correctly as the PTN Reader always reads x and y coordinates and mulitplies with gen_conversion_coordintes
	 * @param draw_ride_pool    determines whether all areas or only those with positive number of vehicles are drawn
	 */
	private static void writeRpoolDotFile(Graph<Stop, Link> ptn, double conversionFactor, File outputFileName, RidepoolingPool rpool, boolean colored, double correctionFactor, boolean draw_ride_pool) {
		StringBuilder stringBuilder = new StringBuilder();
		if (ptn.isDirected()) {
			stringBuilder.append("di");
		}
		stringBuilder.append("graph G {\n");
		for (Stop stop : ptn.getNodes()) {
			stringBuilder.append(transformStop(stop, conversionFactor, correctionFactor));
		}
		int numberAreas = rpool.getAreas().size();
		if (!draw_ride_pool) {
			numberAreas = rpool.getRideConcept().size();
		}
		String color = "";
		if (draw_ride_pool) {
			for (RidepoolingArea area : rpool.getAreas()) {
				if (colored) {
					float hValue = (float) area.getId()/numberAreas;
					color = hValue + ", " + 1.0 + ", " +  1.0;
				}
				for (Link link : area.getEdges()) {
					stringBuilder.append(transformLink(link, ptn.isDirected(), color, area.getId()));
				}
			}
		}
		else {
			int counter = 1;
			for (RidepoolingArea area : rpool.getRideConcept()) {
				if (colored) {
					float hValue = (float) counter/numberAreas;
					color = hValue + ", " + 1.0 + ", " +  1.0;
					counter += 1;
				}
				for (Link link : area.getEdges()) {
					stringBuilder.append(transformLink(link, ptn.isDirected(), color, area.getId()));
				}
			}
		}
		stringBuilder.append("}");
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName));
			writer.write(stringBuilder.toString());
			writer.close();
		} catch (IOException e) {
			throw new OutputFileException(outputFileName.getName());
		}
	}

	private static String transformStop(Stop stop, double conversionFactor, double correctionFactor) {
		return "\ts" + stop.getId() + " [label=\"" + stop.getShortName() + "\", pos=\"" + stop.getxCoordinate() * conversionFactor / correctionFactor +
			"," + stop.getyCoordinate() * conversionFactor / correctionFactor + "\"];\n";
	}

	private static String transformLink(Link link, boolean ptnIsDirected, String color, int label) {
		String stopConnector = ptnIsDirected ? "->" : "--";
		if (color == "") {
			return "\ts" + link.getLeftNode().getId() + " " + stopConnector + " s" + link.getRightNode().getId()
					+ "[label=\"" + label + "\"];\n";
		}
		else {
			return "\ts" + link.getLeftNode().getId() + " " + stopConnector + " s" + link.getRightNode().getId()
					+ "[label=\"" + label + "\", color=\"" + color + "\"];\n";
		}
	}

}
