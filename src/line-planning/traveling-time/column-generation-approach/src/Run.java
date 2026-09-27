import java.util.*;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.*;


/**
 * main class for the column generation algorithm solving the LP relaxation
 * of the line planning problem with minimal travelling time
 */
public class Run {

    private static final Logger logger = new Logger(Run.class);

    public static void main(String[] args) {

        long totalStartTime = System.currentTimeMillis();

        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin computing traveling time column generation line concept");


        ColumnGeneration colGen = new ColumnGeneration(parameters);
        logger.debug("Begin solving Column Generation problem");
        colGen.solveRelaxation();

        //solveIP?
        if (config.getBooleanValue("lc_traveling_time_cg_solve_ip")) {
            colGen.solveIP();
        }

        logger.info("Finished computing line concept");

        logger.info("Begin writing output data");

        //Path output file?
        if (config.getBooleanValue("lc_traveling_time_cg_print_paths")) {
            colGen.writePaths(colGen.getRLPM().getPaths(), "line-planning", "resulting_path_" + config.getStringValue("lc_traveling_time_cg_constraint_type") + ".giv");
        }


        //write detailed output file of column generation
        colGen.writeHistory();
        logger.info("Finished writing output data");
        logger.debug("Total time: " + (System.currentTimeMillis() - totalStartTime) + "\n");
    }
}
