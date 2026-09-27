package net.lintim.debug;

import net.lintim.csv.OriginDestinationPathActivityCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.ReroutePassengers;
import net.lintim.main.ptn2ean;
import net.lintim.model.Configuration;
import net.lintim.model.EventActivityNetwork;
import net.lintim.model.OriginDestinationMatrix;

import java.io.File;
import java.io.IOException;

/**
 * Helper class that extracts passenger paths in terms of activities in from the
 * {@link EventActivityNetwork} if requested by the user. See main function of
 * {@link ptn2ean} or {@link ReroutePassengers} for an implementation.
 */
public class DebugOriginDestinationActivityPaths {

    private File odPathsFile;

    /**
     * Informs the user that debugging was enabled and initializes a file object
     * to dump the passenger paths.
     *
     * @param config must contain the key "default_debug_od_activity_paths_file"
     * @throws DataInconsistentException thrown if the key
     * "default_debug_od_activity_paths_file" does not exist.
     */
    public DebugOriginDestinationActivityPaths(Configuration config) throws DataInconsistentException {
        if (config.getBooleanValue("debug_paths_in_ean")) {
            odPathsFile = new File(config
                    .getStringValue("default_debug_od_activity_paths_file"));
            config.setValue("ean_remember_od_paths", "true");
            System.err
                    .println("Debugging OD-Paths in EAN enabled!");
        }
    }

    /**
     * Informs the user and saves the passenger paths to the file with the
     * config key "default_debug_od_activity_paths_file".
     */
    public void finish(Configuration config, EventActivityNetwork ean,
            OriginDestinationMatrix od) throws DataInconsistentException,
            IOException {
        if (config.getBooleanValue("debug_paths_in_ean")) {
            System.err
                    .print("Dumping OD-Paths in EAN to file... ");
            OriginDestinationPathActivityCSV.toFile(ean, od, odPathsFile);
            System.err.println("done!");
        }
    }
}
