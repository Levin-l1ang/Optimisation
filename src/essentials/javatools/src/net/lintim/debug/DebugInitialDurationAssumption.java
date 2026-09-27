package net.lintim.debug;

import net.lintim.csv.InitialDurationAssumptionCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.ptn2ean;
import net.lintim.model.Configuration;
import net.lintim.model.EventActivityNetwork;

import java.io.File;
import java.io.IOException;

/**
 * Helper class that extracts the initial duration assumption from the
 * {@link EventActivityNetwork} if requested by the user. See main function of
 * {@link ptn2ean} for an implementation.
 */
public class DebugInitialDurationAssumption {

    private File initialDurationAssumptionPeriodicFile;

    /**
     * Informs the user that debugging was enabled and initializes a file object
     * to dump the initial duration assumption.
     *
     * @param config must contain the boolean key
     * "ean_dump_initial_duration_assumption"
     * @throws DataInconsistentException thrown if the key
     * "ean_dump_initial_duration_assumption" does not exist.
     */
    public DebugInitialDurationAssumption(Configuration config) throws DataInconsistentException {
        if (config.getBooleanValue("ean_dump_initial_duration_assumption")) {
            initialDurationAssumptionPeriodicFile = new File(config
                    .getStringValue("filename_initial_duration_assumption"));
            System.err.println("Debugging Initial Duration Assumption enabled!");
        }
    }

    /**
     * Informs the user and saves the initial duration assumption to a file.
     */
    public void finish(Configuration config, EventActivityNetwork ean)
    throws DataInconsistentException,
            IOException {
        if (config.getBooleanValue("ean_dump_initial_duration_assumption")) {
            System.err
                    .print("Dumping Initial Duration Assumption to file... ");
            InitialDurationAssumptionCSV.toFile(ean, initialDurationAssumptionPeriodicFile);
            System.err.println("done!");
        }
    }
}
