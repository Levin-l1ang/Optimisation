import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.LinTimException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.io.File;
import java.io.IOException;


public class CreateLinepoolSP {

    private static final Logger logger = new Logger(CreateLinepoolSP.class);

	public static void main(String[] args) {
		if (args.length < 1) {
			throw new ConfigNoFileNameGivenException();
		}

        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        ParametersSP parameters = new ParametersSP(config);
        parameters.setParametersInClasses();
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Pair<PTN, OD> input = IO.readInputData(parameters);
        PTN ptn = input.getFirstElement();
        OD od = input.getSecondElement();
        logger.info("Finished reading input files");

        logger.info("Create line-pool by shortest paths...");
        LinePool pool = new LinePool(ptn);
        pool.poolFromKSP(od, parameters.getNumberShortestPaths());

        logger.debug("Try to finalize pool...");
        pool.finalizeSP();
        logger.debug("done!");

        logger.info("Finished creating line pool");

        //Output Pool
        logger.info("Begin writing output files");
        try {
            LinePoolCSV.toFile(pool,
                new File(config.getStringValue("default_pool_file")),
                new File(config.getStringValue("default_pool_cost_file")),
                parameters.getConversionFactorLength());
        }
        catch (IOException e) {
            logger.error("Could not write line pool to file");
            throw new LinTimException(e.getMessage());
        }
        logger.info("Finished writing output files");
	}
}
