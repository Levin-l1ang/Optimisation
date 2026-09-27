package net.lintim.util.draw;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.OutputFileException;
import net.lintim.io.ConfigReader;
import net.lintim.io.PTNReader;
import net.lintim.io.ZoneReader;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.model.Zone;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.*;

/**
 * Utility class for drawing a ptn. Reads the ptn with the core library and write a dot file for further processing with
 * graphviz.
 */
public class TransformPtnToDot {
	private static Logger logger = Logger.getLogger("net.lintim.util.draw.TransformPtnToDot");

	public static void main(String[] args) {
		logger.log(LogLevel.INFO, "Begin reading configuration");
		if (args.length < 1) {
			throw new ConfigNoFileNameGivenException();
		}
		new ConfigReader.Builder(args[0]).build().read();
		boolean drawZones = args[1].equals("zone");
		boolean readExistingPtn = Config.getBooleanValueStatic("ptn_draw_existing_ptn");
		boolean useCoordinates = Config.getBooleanValueStatic("ptn_draw_use_coordinates");
		String stopFileName;
		String linkFileName;
		if (!readExistingPtn) {
			stopFileName = Config.getStringValueStatic("default_stops_file");
			linkFileName = Config.getStringValueStatic("default_edges_file");
		}
		else {
			stopFileName = Config.getStringValueStatic("default_existing_stop_file");
			linkFileName = Config.getStringValueStatic("default_existing_edge_file");
		}
		String ptnDotFileName = "";
		double conversionFactor = Config.getDoubleValueStatic("ptn_draw_conversion_factor");
		String zoneFileName = "";
		if (drawZones) {
			zoneFileName = Config.getStringValueStatic("filename_tariff_zone_file");
			ptnDotFileName = Config.getStringValueStatic("filename_tariff_ptn_zone_graph");
		}
		else{
			ptnDotFileName = Config.getStringValueStatic("default_ptn_graph_file");
		}
		logger.log(LogLevel.INFO, "Finished reading configuration");

		logger.log(LogLevel.INFO, "Begin reading input data");
		Graph<Stop, Link> ptn = new PTNReader.Builder().setStopFileName(stopFileName).setLinkFileName(linkFileName)
				.build().read();
		Map<Integer, Zone> zones = new HashMap<>();;
		if (drawZones) {
			zones = new ZoneReader.Builder().setZoneFileName(zoneFileName).setPtn(ptn).build().read();
		}
		logger.log(LogLevel.INFO, "Finished reading input data");

		logger.log(LogLevel.INFO, "Begin writing output data");
		writeDotFile(ptn, conversionFactor, ptnDotFileName, zones, useCoordinates);
		logger.log(LogLevel.INFO, "Finished writing output data");
	}

	/**
	 * Transform the given ptn into the dot format and write it to the given output file
	 *
	 * @param ptn            the ptn to draw
	 * @param outputFileName the name of the output file to write. Will be in dot format for further processing with
	 *                       the graphviz utility
	 */
	private static void writeDotFile(Graph<Stop, Link> ptn, double conversionFactor, String outputFileName, Map<Integer, Zone> zones, boolean useCoordinates) {
		StringBuilder stringBuilder = new StringBuilder();
		if (ptn.isDirected()) {
			stringBuilder.append("di");
		}
		stringBuilder.append("graph G {\n");
		int numberZones = zones.size();
		String fillcolor = "";
		for (Stop stop : ptn.getNodes()) {
			if (!zones.isEmpty()) {
				for (Zone zone : zones.values()) {
					if (zone.containsStop(stop)) {
						Zone correspondingZone = zone;
						float hValue = (float) correspondingZone.getZoneId()/numberZones;
						fillcolor = hValue + ", " + 1.0 + ", " +  1.0;
						break;
					}
				}
			}
			stringBuilder.append(transformStop(stop, conversionFactor, fillcolor, useCoordinates));
		}
		for (Link link : ptn.getEdges()) {
			stringBuilder.append(transformLink(link, ptn.isDirected()));
		}
		stringBuilder.append("graph [bgcolor=\"white\"] }");
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFileName)));
			writer.write(stringBuilder.toString());
			writer.close();
		} catch (IOException e) {
			throw new OutputFileException(outputFileName);
		}
	}

	private static String transformStop(Stop stop, double conversionFactor, String fillcolor, boolean useCoordinates) {
		if (fillcolor == "") {
			if (useCoordinates) {
				return "\ts" + stop.getId() + " [label=\"" + stop.getShortName() + "\", pos=\"" + stop.getxCoordinate() / conversionFactor +
					"," + stop.getyCoordinate() / conversionFactor + "\"];\n";
			}
			else{
				return "\ts" + stop.getId() + " [label=\"" + stop.getShortName() + "\"];\n";
			}
		}
		else{
			if (useCoordinates) {
				return "\ts" + stop.getId() + " [label=\"" + stop.getShortName() + "\", pos=\"" + stop.getxCoordinate() / conversionFactor +
						"," + stop.getyCoordinate() / conversionFactor + "\", style=\"filled\", fillcolor=\"" + fillcolor + "\"];\n";
			}
			else{
				return "\ts" + stop.getId() + " [label=\"" + stop.getShortName() + "\", style=\"filled\", fillcolor=\"" + fillcolor + "\"];\n";
			}
		}
	}

	private static String transformLink(Link link, boolean ptnIsDirected) {
		String stopConnector = ptnIsDirected ? "->" : "--";
		return "\ts" + link.getLeftNode().getId() + " " + stopConnector + " s" + link.getRightNode().getId()
				+ "[label=\"" + link.getId() + "\"];\n";
	}
}
