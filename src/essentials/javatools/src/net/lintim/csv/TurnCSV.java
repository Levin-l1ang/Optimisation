package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Station;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Handles the turn CSV format and allows reading and writing.
 *
 * Syntax:
 * index; 1 (vehicles can turn), 0 (otherwise)
 */
public class TurnCSV {

    /**
     * Reads turns from file.
     *
     * @param ptn The public transportation network to write to.
     * @param turnFile The turn file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(PublicTransportationNetwork ptn,
            File turnFile) throws IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(turnFile);

        List<String> line;

        while ((line = reader.read()) != null) {

            try{
                int size = line.size();
                if(size != 2){
                    throw new DataInconsistentException("2 columns needed, " +
                            size + " given");
                }

                try{
                    Iterator<String> itr = line.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    Integer vehiclesCanTurn = Integer.parseInt(itr.next());

                    ptn.getStationByIndex(index).setVehicleCanTurn(
                            vehiclesCanTurn == 1 ? true : false);

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("index should be a " +
                    "number, but it is not");
                }

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        turnFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

    }

    /**
     * Writes turns to file.
     *
     * @param ptn The public transportation network to read from.
     * @param turnFile The turn file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(PublicTransportationNetwork ptn, File turnFile)
    throws IOException, DataInconsistentException{

        turnFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(turnFile);

        fw.write("# stop_index; vehicle_can_turn\n");

        for(Station station : ptn.getStations()){

            fw.write(station.getIndex() + "; " +
                    (station.getVehicleCanTurn() ? 1 : 0) + "\n");

        }

        fw.close();

    }

}
