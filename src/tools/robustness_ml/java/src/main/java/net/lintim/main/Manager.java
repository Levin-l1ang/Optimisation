package net.lintim.main;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.IOException;

public class Manager {

    private static final Logger logger = new Logger(Manager.class);

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        if (args.length < 2) {
            throw new RuntimeException("Need at least two arguments, the config file to read and which method to run (algo or eval)");
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        // Call the correct main class
        if (args[1].equals("eval")) {
            ComputeKeyFeatures.main(args);
        }
        else {
            if (config.getBooleanValue("rob_use_genetic_algorithm")) {
                GeneticAlgorithmMain.main(args);
            }
            else {
                LocalSearchMain.main(args);
            }
        }
    }
}
