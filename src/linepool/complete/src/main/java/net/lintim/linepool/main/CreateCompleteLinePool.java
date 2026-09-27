package net.lintim.linepool.main;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.io.LineWriter;
import net.lintim.io.PTNReader;
import net.lintim.io.TerminalReader;
import net.lintim.linepool.algorithm.CompleteTerminalLinePool;
import net.lintim.linepool.util.Parameters;
import net.lintim.model.Graph;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.Set;

public class CreateCompleteLinePool {

    private static Logger logger = new Logger(CreateCompleteLinePool.class.getCanonicalName());

    public static void main(String[] args) {
        logger.info("Begin reading configuration");
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().setConfig(config).build().read();
        Set<Integer> terminals = new TerminalReader.Builder().setConfig(config).build().read();
        logger.info("Finished reading input data");

        logger.info("Begin computing complete terminal based line pool");
        LinePool linePool = CompleteTerminalLinePool.computeCompleteLinePool(ptn, terminals, parameters);
        logger.info("Finished computation");

        logger.info("Begin writing output data");
        new LineWriter.Builder(linePool).writeLineConcept(false).writePool(true).writeCosts(true).setConfig(config)
            .build().write();
        logger.info("Finished writing output data");
    }
}
