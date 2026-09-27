package net.lintim.main.tools;

import net.lintim.algorithm.tools.PTNLoadGenerator;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.GraphHelper;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.tools.LoadGenerationParameters;

import java.util.Map;

/**
 */
public class PTNLoadGeneratorMain {

    private static final Logger logger = new Logger(PTNLoadGeneratorMain.class);

    public static void main(String[] args) {
        logger.info("Begin reading configuration");
        if (args.length != 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        LoadGenerationParameters parameters = new LoadGenerationParameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
        OD od = new ODReader.Builder(GraphHelper.getMaxNodeId(ptn)).build().read();
        LinePool linePool;
        if(parameters.useCg()) {
            linePool = new LineReader.Builder(ptn).build().read();
        }
        else {
            linePool = null;
        }
        Map<Integer, Map<Pair<Integer, Integer>, Double>> additionalLoad;
        if (parameters.addAdditionalLoad()) {
            additionalLoad = new AdditionalLoadReader.Builder().build().read();
        }
        else {
            additionalLoad = null;
        }
        logger.info("Finished reading input data");

        logger.info("Begin computing load distribution");
        PTNLoadGenerator loadGenerator = new PTNLoadGenerator(ptn, od, linePool, additionalLoad, parameters);
        loadGenerator.computeLoad();
        logger.info("Finished computing load distribution");

        logger.info("Begin writing output data");
        new PTNWriter.Builder(ptn).writeStops(false).writeLinks(false).writeLoads(true).build().write();
        logger.info("Finished writing output data");
    }

}
