import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.LinTimException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.*;

public class WriteCostFile {

	private static final Logger logger = new Logger(WriteCostFile.class);

	public static void main(String[] args) {
		if (args.length < 1) {
			throw new ConfigNoFileNameGivenException();
		}

		try {

			logger.info("Begin reading configuration");
			Config config = new ConfigReader.Builder(args[0]).build().read();

			logger.debug("Set variables... ");
			boolean directed = !config.getBooleanValue("ptn_is_undirected");

			String ean_model_weight_drive = config.getStringValue("ean_model_weight_drive");
			String ean_model_weight_wait = config.getStringValue("ean_model_weight_wait");
			int minimal_wait_time = config.getIntegerValue("ean_default_minimal_waiting_time");
			int maximal_wait_time = config.getIntegerValue("ean_default_maximal_waiting_time");
			double conversionFactorLength = config.getDoubleValue("gen_conversion_length");

			Line.setCostsFixed(config.getDoubleValue("lpool_costs_fixed"));
			Line.setCostsLength(config.getDoubleValue("lpool_costs_length"));
			Line.setCostsEdges(config.getDoubleValue("lpool_costs_edges"));
			Line.setDirected(directed);
			Line.setWaitingTimeInStation(ean_model_weight_wait, minimal_wait_time, maximal_wait_time);

			LinePoolCSV.setPoolCostHeader(config.getStringValue("lpool_cost_header"));
			Line.setCostsVehicles(config.getDoubleValue("lpool_costs_vehicles"));
			Line.setPeriodLength(config.getIntegerValue("period_length"));
			Line.setMinTurnaroundTime(config.getIntegerValue("vs_turn_over_time"));

			logger.info("Finished reading configuration");

			logger.info("Begin reading input files");
			File stop_file = new File(config.getStringValue("default_stops_file"));
			File edge_file = new File(config.getStringValue("default_edges_file"));
			File pool_file = new File(config.getStringValue("default_pool_file"));
			File cost_file = new File(config.getStringValue("default_pool_cost_file"));

			PTN ptn = new PTN(directed);
			PTNCSV.fromFile(ptn, stop_file, edge_file, ean_model_weight_drive, conversionFactorLength);

			LinePool pool = new LinePool(ptn);
			LinePoolCSV.fromFile(pool, pool_file);
			logger.info("Finished reading input files");

			logger.info("Begin writing output files");
			LinePoolCSV.toCostFile(pool, cost_file, conversionFactorLength);
			logger.info("Finished writing output files");

		} catch (IOException e) {
			throw new LinTimException(e.getMessage());
		}
	}

}
