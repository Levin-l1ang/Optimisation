import net.lintim.exception.LinTimException;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.io.File;
import java.io.IOException;

public class IO {

    private static final Logger logger = new Logger(IO.class);

    public static Pair<PTN, OD> readInputData(Parameters parameters) {
        try {
            File stop_file = new File(parameters.getStopFileName());
            File edge_file = new File(parameters.getEdgeFileName());
            File load_file = new File(parameters.getLoadFileName());
            File od_file = new File(parameters.getOdFileName());

            PTN ptn = new PTN(parameters.isDirected());

            PTNCSV.fromFile(ptn, stop_file, edge_file, load_file, parameters.getEanModelWeightDrive(),
                parameters.getConversionFactorLength());

            OD od = new OD(ptn, parameters.getPtnSpeed(), parameters.getWaitingTime());

            ODCSV.fromFile(ptn, od, od_file);

            return new Pair<>(ptn, od);
        }
        catch (IOException e) {
            logger.error("There was an error reading an input file");
            throw new LinTimException(e.getMessage());
        }
    }
}
