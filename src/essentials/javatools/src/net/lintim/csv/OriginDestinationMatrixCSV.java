package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Station;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Handles origin destination matrix CSV files and allows reading and writing.
 *
 * Syntax:
 * from station; to station; number of passengers
 */

public class OriginDestinationMatrixCSV {
    /**
     * Reads an origin destination matrix from file.
     *
     * @param od The origin destination matrix to write to.
     * @param odFile The origin destination matrix file to read from.
     * @param symmetric Whether or not only the upper triangle of the origin
     * destination matrix is saved in the file.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(OriginDestinationMatrix od, File odFile,
            Boolean symmetric)
    throws IOException, DataInconsistentException {

        PublicTransportationNetwork ptn = od.getPublicTransportationNetwork();

        CsvReader reader = new CsvReader(odFile);

        List<String> line;

        while ((line = reader.read()) != null) {

            try{

                int size = line.size();
                if(size != 3){
                    throw new DataInconsistentException("3 columns needed, " +
                            size + " given");
                }

                try{
                    Iterator<String> itr = line.iterator();
                    Integer fromIndex = Integer.parseInt(itr.next());
                    Integer toIndex = Integer.parseInt(itr.next());
                    Double amount = Double.parseDouble(itr.next());

                    if(symmetric){
                        od.addSymmetric(fromIndex, toIndex, amount);
                    }
                    else{
                        od.add(fromIndex, toIndex, amount);
                    }
                }

                catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " + reader.getLineNumber()
                        + " in file " + odFile.getAbsolutePath()
                        + " invalid: " + e.getMessage());
            }

        }

        reader.close();

        if(symmetric){
            for(Station station : ptn.getStations()){
                od.addSymmetric(station, station, 0.0);
            }
        }

        od.checkCompleteness();

    }
    
    //Version to be used, as all Origin-Destination-Matrices are assumed to be asymmetric 
    public static void fromFile(OriginDestinationMatrix od, File odFile)
    throws IOException, DataInconsistentException {
    	fromFile(od, odFile, false);
    }

    /**
     * Writes an origin destination matrix to file.
     *
     * @param od The origin destination matrix to read from.
     * @param odFile The origin destination matrix file to write to.
     * @throws IOException
     */
    // TODO integrate into CSV write framework
    public static void toFile(OriginDestinationMatrix od, File odFile) throws IOException{

        PublicTransportationNetwork ptn = od.getPublicTransportationNetwork();

        odFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(odFile);

        fw.write("# from; to; customers\n");

        LinkedHashSet<Station> stations = ptn.getStations();
        LinkedHashSet<Station> stillToPass = new LinkedHashSet<Station>(stations);

        for(Station station1 : stations){

            for(Station station2 : stillToPass){

                fw.write(station1.getIndex() + "; " + station2.getIndex()
                        + "; " + od.get(station1, station2) + "\n");

            }

        }

        fw.close();

    }

}
