import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.LinTimException;
import net.lintim.io.ConfigReader;
import net.lintim.io.PTNReader;
import net.lintim.io.RestrictedTurnReader;
import net.lintim.io.TerminalReader;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.io.*;
import java.util.Set;

public class CreateLinepool {

    private static final Logger logger = new Logger(CreateLinepool.class.getCanonicalName());

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        // Determine whether we are using the duration restricted model or not
        ParametersTree parameters;
        if (config.getStringValue("lpool_model").equalsIgnoreCase("tree_based")) {
            parameters = new ParametersTree(config);
        }
        else if (config.getStringValue("lpool_model").equalsIgnoreCase("restricted_line_duration")) {
            parameters = new ParametersDurationRestricted(config);
        }
        else {
            throw new LinTimException("Did not recognize lpool_model " + config.getStringValue("lpool_model"));
        }
        parameters.setParametersInClasses();
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Pair<PTN, OD> input = IO.readInputData(parameters);
        PTN ptn = input.getFirstElement();
        OD od = input.getSecondElement();
        // Remove the forbidden edges if necessary
        if (parameters.shouldRestrictForbiddenEdges()) {
            Graph<net.lintim.model.Stop, Link> forbiddenEdges = new PTNReader.Builder()
                .setLinkFileName(config.getStringValue("filename_forbidden_links_file")).build().read();
            ptn.getEdges().removeIf(nextEdge -> forbiddenEdges.getEdge(nextEdge.getIndex()) != null);
        }

        Set<Integer> terminals = null;
        if (parameters.shouldRestrictTerminals()) {
            terminals = new TerminalReader.Builder().setConfig(config).build().read();
            LinePool.setTerminals(terminals);
        }

        Set<Pair<Integer, Integer>> restrictedTurns = null;
        if (parameters.shouldRestrictTurns()) {
            restrictedTurns = new RestrictedTurnReader.Builder().build().read();
            Line.restrictedTurns = restrictedTurns;
        }

        logger.info("Finished reading input data");

        logger.info("Begin generating line pool");
        LinePool pool = TreeBasedAlgo.generateLinePool(ptn, od, parameters);

        logger.info("Begin writing output data");
        try {
            LinePoolCSV.toFile(pool,
                new File(parameters.getPoolFileName()),
                new File(parameters.getPoolCostFileName()),
                parameters.getConversionFactorLength());
        } catch (IOException e) {
            logger.error("An error occurred while writing the final line pool.");
            throw new LinTimException(e.getMessage());
        }
        logger.info("Finished writing output data");
	}
}
