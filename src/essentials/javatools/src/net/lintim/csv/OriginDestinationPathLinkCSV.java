package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Link;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Station;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Handles origin destination link path CSV files, i.e. those that contain
 * paths between stations in the public transportation network and allows for
 * writing.
 *
 * Syntax:
 * start station; end station; link; passengers (optional)
 */

public class OriginDestinationPathLinkCSV {

    /**
     * Writes origin destination link paths to file.
     *
     * @param ptn The public transportation network to read from.
     * @param od The origin destination matrix to read from.
     * @param odPathsFile The origin destination paths file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(PublicTransportationNetwork ptn,
            OriginDestinationMatrix od, File odPathsFile) throws IOException,
            DataInconsistentException {

        odPathsFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(odPathsFile);

        Boolean odGiven = (od != null);

        fw.write("# from_station; to_station; link_index"
                + (odGiven ? "; passengers" : "") + "\n");

        for (Map.Entry<Station, LinkedHashMap<Station, LinkedHashSet<Link>>> e1 : ptn
                .getOriginDestinationPathMap().entrySet()) {

            Station s1 = e1.getKey();

            for (Map.Entry<Station, LinkedHashSet<Link>> e2 : e1.getValue()
                    .entrySet()) {

                Station s2 = e2.getKey();

                for (Link link : e2.getValue()) {

                    fw.write(s1.getIndex() + "; " + s2.getIndex() + "; "
                            + link.getIndex() +  (odGiven ? "; "
                            + od.getMatrix().get(s1, s2) : "") + "\n");

                }
            }

        }

        fw.close();

    }
}
