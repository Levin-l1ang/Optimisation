package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConceptDirectification;
import net.lintim.model.Link;
import net.lintim.model.PublicTransportationNetwork;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handles load CSV files and allows reading and writing.
 *
 * Syntax:
 * link index; load; lower frequency; upper frequency
 */

public class LoadCSV {

    /**
     * Reads loads from file.
     *
     * @param ptn The public transportation network to write the loads to.
     * @param loadsFile The loads file to read the loads from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(PublicTransportationNetwork ptn,
            File loadsFile) throws
    IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(loadsFile);

        List<String> line;

        Boolean undirected = ptn.isUndirected();

        while ((line = reader.read()) != null) {

            try{

                int size = line.size();
                if(size != 4){
                    throw new DataInconsistentException("4 columns needed, " +
                            size + " given");
                }

                try{

                    Iterator<String> itr = line.iterator();
                    Integer linkIndex = Integer.parseInt(itr.next());
                    Double load = Double.parseDouble(itr.next());
                    Integer lowerFrequency = Integer.parseInt(itr.next());
                    Integer upperFrequency = Integer.parseInt(itr.next());

                    if(undirected){
                        Link link = ptn.getUndirectedLinkByIndex(linkIndex);
                        if(link == null){
                            throw new DataInconsistentException("undirected " +
                                    "link index " + linkIndex + " does not " +
                                            "exist");
                        }
                        Link counterpart = link.getUndirectedCounterpart();
                        if(link.getLoad() != null ||
                                link.getLowerFrequency() != null ||
                                link.getUpperFrequency() != null ||
                                counterpart.getLoad() != null ||
                                counterpart.getLowerFrequency() != null ||
                                counterpart.getUpperFrequency() != null){

                            throw new DataInconsistentException("undirected " +
                                    "link index " + linkIndex + " already " +
                                            "has load information");
                        }
                        link.setLoad(load);
                        link.setLowerFrequency(lowerFrequency);
                        link.setUpperFrequency(upperFrequency);

                        counterpart.setLoad(load);
                        counterpart.setLowerFrequency(lowerFrequency);
                        counterpart.setUpperFrequency(upperFrequency);
                    }
                    else{
                        Link link = ptn.getLinkByIndex(linkIndex);
                        if(link == null){
                            throw new DataInconsistentException("link index " +
                                    linkIndex + " does not exist");
                        }
                        if(link.getLoad() != null ||
                                link.getLowerFrequency() != null ||
                                link.getUpperFrequency() != null){

                            throw new DataInconsistentException("undirected " +
                                    "link index " + linkIndex + " already " +
                                            "has load information");
                        }
                        link.setLoad(load);
                        link.setLowerFrequency(lowerFrequency);
                        link.setUpperFrequency(upperFrequency);
                    }

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        loadsFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        ptn.checkLoadCompleteness();

    }

    /**
     * Writes loads to file.
     *
     * @param ptn The public transportation network to read from.
     * @param loadsFile The loads file to write to.
     * @param undirected Whether or not loads should be written to file
     * undirectedly. May only be true if <code>ptn</code> already is undirected.
     * It may be used to directify <code>ptn</code>, as can be seen in
     * {@link LineConceptDirectification}.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(PublicTransportationNetwork ptn, File loadsFile,
            Boolean undirected)	throws IOException, DataInconsistentException{

        loadsFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(loadsFile);

        fw.write("# link_index; load; minimal_frequency; maximal_frequency\n");

        if(undirected){
            if(!ptn.isUndirected()){
                throw new DataInconsistentException("public transportation " +
                        "network is not undirected");
            }

            for(Map.Entry<Integer, Link> e :
                ptn.getUndirectedIndexLinkMap().entrySet()){

                Integer index = e.getKey();
                Link link = e.getValue();

                Double load = link.getLoad();
                String loadString;

                if(load == null){
                    loadString = "0";
                }
                else if(load == Math.floor(load)){
                    loadString = "" + load.intValue();
                }
                else{
                    loadString = "" + load;
                }

                fw.write(index + "; " + loadString + "; "
                        + link.getLowerFrequency() + "; "
                        + link.getUpperFrequency() + "\n");


            }

        }

        else{

            for(Entry<Integer, Link> e1 : ptn.getDirectedIndexLinkMap().entrySet()){

                Integer index = e1.getKey();
                Link link = e1.getValue();
                Double load = link.getLoad();
                String loadString;

                if(load == Math.floor(load)){
                    loadString = "" + load.intValue();
                }
                else{
                    loadString = "" + load;
                }

                fw.write(index + "; " + loadString + "; "
                        + link.getLowerFrequency() + "; "
                        + link.getUpperFrequency() + "\n");

            }
        }

        fw.close();

    }
}
