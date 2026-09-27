package net.lintim.debug;

import net.lintim.csv.OriginDestinationPathLinkCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.ReroutePassengers;
import net.lintim.main.ptn2ean;
import net.lintim.model.*;

import java.io.File;
import java.io.IOException;

/**
 * Helper class that extracts passenger paths in terms of {@link Link}s in from
 * the {@link EventActivityNetwork} if requested by the user. See main function
 * of {@link ptn2ean} or {@link ReroutePassengers} for an implementation.
 */
public class DebugOriginDestinationLinkPaths {

    private File odPathsFile;

    /**
     * Informs the user that debugging was enabled and initializes a file object
     * to dump the passenger paths.
     *
     * @param config must contain the key "default_debug_od_link_paths_file"
     * @throws DataInconsistentException thrown if the key
     * "default_debug_od_link_paths_file" does not exist.
     */
    public DebugOriginDestinationLinkPaths(Configuration config) throws DataInconsistentException {
        if (config.getBooleanValue("debug_paths_in_ptn")) {
            odPathsFile = new File(config
                    .getStringValue("default_debug_od_link_paths_file"));
            config.setValue("ptn_remember_od_paths", "true");
            System.err
                    .println("Debugging OD-Paths in PTN enabled!");
        }
    }

    /**
     * Informs the user and saves the passenger paths to the file with the
     * config key "default_debug_od_link_paths_file".
     */
    public void finish(Configuration config, PublicTransportationNetwork ptn,
            OriginDestinationMatrix od) throws DataInconsistentException,
            IOException {
        if (config.getBooleanValue("debug_paths_in_ptn")) {
            System.err
                    .print("Dumping OD-Paths in PTN to file... ");
            OriginDestinationPathLinkCSV.toFile(ptn, od, odPathsFile);
            System.err.println("done!");
        }
    }
}
