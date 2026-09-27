package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConceptDirectification;
import net.lintim.model.Link;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handles headway CSV files and allows reading and writing.
 *
 * Syntax:
 * edge index; headway
 */

public class HeadwayCSV {

    /**
     * Reads headways from file.
     *
     * @param ptn The public transportation network to write edge headways to.
     * @param headwayFile The file to read headways from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(PublicTransportationNetwork ptn,
            File headwayFile) throws IOException, DataInconsistentException {

        Boolean undirected = ptn.isUndirected();

        CsvReader reader = new CsvReader(headwayFile);

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

                    Integer linkIndex = Integer.parseInt(itr.next());
                    Double headway = Double.parseDouble(itr.next());

                    if(undirected){
                        Link link = ptn.getUndirectedLinkByIndex(linkIndex);
                        if(link == null){
                            throw new DataInconsistentException("undirected " +
                                    "link index " + linkIndex + " does not " +
                                            "exist");
                        }
                        Link counterpart = link.getUndirectedCounterpart();
                        if(link.getHeadway() != null ||
                                counterpart.getHeadway() != null){

                            throw new DataInconsistentException("undirected " +
                                    "link index " + linkIndex + " already " +
                                            "has headway information");
                        }
                        link.setHeadway(headway);
                        counterpart.setHeadway(headway);
                    }
                    else{
                        Link link = ptn.getLinkByIndex(linkIndex);
                        if(link == null){
                            throw new DataInconsistentException("link index " +
                                    linkIndex + " does not exist");
                        }
                        if(link.getHeadway() != null){

                            throw new DataInconsistentException("directed " +
                                    "link index " + linkIndex + " already " +
                                            "has headway information");
                        }
                        link.setHeadway(headway);
                    }

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        headwayFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();
        ptn.checkHeadwayCompleteness();

    }

    /**
     * Writes headways to file.
     *
     * @param ptn The public transportation network to read from.
     * @param headwayFile The headway file to write to.
     * @param undirected Whether or not headways should be written to file
     * undirectedly. May only be true if <code>ptn</code> already is undirected.
     * It may be used to directify <code>ptn</code>, as can be seen in
     * {@link LineConceptDirectification}.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void toFile(PublicTransportationNetwork ptn, File headwayFile,
            Boolean undirected, Configuration config)	throws IOException, DataInconsistentException{

        headwayFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(headwayFile);

        fw.write("# " + config.getStringValue("headways_header") +"\n");

        if(undirected){
            if(!ptn.isUndirected()){
                throw new DataInconsistentException("public transportation " +
                        "network is not undirected");
            }

            for(Map.Entry<Integer, Link> e :
                ptn.getUndirectedIndexLinkMap().entrySet()){

                Integer index = e.getKey();
                Link link = e.getValue();

                Double headway = link.getHeadway();
                String headwayString;

                if(headway == Math.floor(headway)){
                    headwayString = "" + headway.intValue();
                }
                else{
                    headwayString = "" + headway;
                }

                fw.write(index + "; " + headwayString + "\n");

            }

        }

        else{

            for(Entry<Integer, Link> e1 : ptn.getDirectedIndexLinkMap().entrySet()){

                Integer index = e1.getKey();
                Link link = e1.getValue();

                Double headway = link.getHeadway();
                String headwayString;

                if(headway == Math.floor(headway)){
                    headwayString = "" + headway.intValue();
                }
                else{
                    headwayString = "" + headway;
                }

                fw.write(index + "; " + headwayString + "\n");

            }
        }

        fw.close();

    }
}
