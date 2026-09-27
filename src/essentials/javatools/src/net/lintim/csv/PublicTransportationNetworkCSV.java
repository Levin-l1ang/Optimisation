package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConceptDirectification;
import net.lintim.model.Link;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Station;
import net.lintim.model.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handles the public transportation network CSV format and allows reading and
 * writing.
 *
 * Syntax stations:
 * index; short name; long name
 *
 * Syntax links:
 * index; from/left station; to/right station; length; lower bound; upper bound
 */

public class PublicTransportationNetworkCSV {

    /**
     * Reads a public transportation network from file.
     *
     * @param ptn The public transportation network to write to.
     * @param stationsFile The stations file to read from.
     * @param linksFile The links file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(PublicTransportationNetwork ptn,
            File stationsFile, File linksFile) throws
    IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(stationsFile);

        List<String> line;

        while ((line = reader.read()) != null) {

            try{

                // TODO standardize stops file
                int size = line.size();
                if(size < 3){
                    throw new DataInconsistentException("3 columns needed, " +
                            size + " given");
                }

                try{
                    Iterator<String> itr = line.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    String shortName = itr.next();
                    String longName = itr.next();

                    if(size>=5){
                    	double x_coordinate=Double.parseDouble(itr.next());
                    	double y_coordinate=Double.parseDouble(itr.next());
                    	ptn.addStation(new Station(index, shortName, longName, null, x_coordinate, y_coordinate));
                    }
                    else{
                    	ptn.addStation(new Station(index, shortName, longName, null));
                    }

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("index should be a " +
                    "number, but it is not");
                }

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        stationsFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        Boolean undirected = ptn.isUndirected();

        reader = new CsvReader(linksFile);

        while ((line = reader.read()) != null) {

            try{

                int size = line.size();
                if(size != 6){
                    throw new DataInconsistentException("6 columns needed, " +
                            size + " given");
                }

                try{

                    Iterator<String> itr = line.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    Integer leftStationIndex = Integer.parseInt(itr.next());
                    Integer rightStationIndex = Integer.parseInt(itr.next());
                    Double length = Double.parseDouble(itr.next());
                    Double lowerBound = Double.parseDouble(itr.next());
                    Double upperBound = Double.parseDouble(itr.next());

                    Station leftStation = ptn.getStationByIndex(leftStationIndex);
                    if(leftStation == null){
                        throw new DataInconsistentException("left station " +
                                "resp. fromStation index not found; link " +
                                "index is " + index);
                    }

                    Station rightStation = ptn.getStationByIndex(rightStationIndex);
                    if(rightStation == null){
                        throw new DataInconsistentException("right station " +
                                "resp. toStation index not found; link " +
                                "index is " + index);
                    }

                    ptn.addLink(new Link(undirected, index, leftStation, rightStation,
                            length, lowerBound, upperBound));

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        linksFile.getAbsolutePath()	+ " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

    }

    /**
     * Writes a public transportation network to file.
     *
     * @param ptn The public transportation network to read from.
     * @param stationsFile The stations file to write to.
     * @param linksFile The links file to write to.
     * @param undirected Whether or not the public transportation network should
     * be written to file undirectedly. May only be true if <code>ptn</code>
     * already is undirected. It may be used to directify <code>ptn</code>, as
     * can be seen in {@link LineConceptDirectification}.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(PublicTransportationNetwork ptn, File stationsFile,
            File linksFile, Boolean undirected, Configuration config)	throws IOException, DataInconsistentException{

        stationsFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(stationsFile);

        fw.write("# " + config.getStringValue("stops_header") + "\n");

        for(Station station : ptn.getStations()){

            fw.write(station.getIndex() + "; " + station.getShortName()
                + "; " + station.getLongName() + "; " + station.getXCoordinate() + "; " + station.getYCoordinate() +"\n");

        }

        fw.close();

        linksFile.getParentFile().mkdirs();
        fw = new FileWriter(linksFile);

        if(undirected){
            if(!ptn.isUndirected()){
                throw new DataInconsistentException("public transportation " +
                        "network is not undirected");
            }

            fw.write("# " + config.getStringValue("edges_header") + "\n");

            for(Map.Entry<Integer, Link> e : ptn.getIndexLinkMap().entrySet()){

                Integer index = e.getKey();
                Link link = e.getValue();

                fw.write(index + "; " + link.getFromStation().getIndex()
                        + "; " + link.getToStation().getIndex() + "; " +
                        Formatter.format(link.getLength()) + "; " +
                        Formatter.format(link.getLowerBound()) + "; " +
                        Formatter.format(link.getUpperBound()) + "\n");

            }

        }

        else{

            fw.write("# " + config.getStringValue("edges_header") + "\n");

            for(Map.Entry<Integer, Link> e : ptn.getDirectedIndexLinkMap().entrySet()){

                Integer index = e.getKey();
                Link link = e.getValue();

                fw.write(index + "; " + link.getFromStation().getIndex()
                        + "; " + link.getToStation().getIndex() + "; " +
                        Formatter.format(link.getLength()) + "; " +
                        Formatter.format(link.getLowerBound()) + "; " +
                        Formatter.format(link.getUpperBound()) + "\n");

            }

        }

        fw.close();

    }

}
