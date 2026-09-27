package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConceptDirectification;
import net.lintim.model.Line;
import net.lintim.model.LineCollection;
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
 * Handles line concept CSV files and allows reading and writing.
 *
 * Syntax:
 * index; link order; link index; frequency
 */

public class LineConceptCSV {

    /**
     * Reads a line concept from file.
     *
     * @param lc The line collection to write to.
     * @param lcFile The line concept file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(LineCollection lc, File lcFile) throws
    IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(lcFile);

        List<String> csvLine;
        Integer lastLineIndex = null;

        Line line = null;
        Integer lastLinkOrder = null;
        Integer index = null;

        Boolean undirected = lc.isUndirected();
        PublicTransportationNetwork ptn = lc.getPublicTransportationNetwork();

        try{

            while ((csvLine = reader.read()) != null) {

                try{

                    int size = csvLine.size();
                    if(size != 4){
                        throw new DataInconsistentException("4 columns needed, " +
                                size + " given");
                    }

                    Iterator<String> itr = csvLine.iterator();
                    index = Integer.parseInt(itr.next());
                    Integer linkOrder = Integer.parseInt(itr.next());
                    Integer linkIndex = Integer.parseInt(itr.next());
                    Integer frequency = Integer.parseInt(itr.next());

                    if(lastLineIndex == null){
                        lastLineIndex = index;
                        lastLinkOrder = linkOrder-1;
                        line = new Line(undirected, index, frequency);
                    }

                    if(lastLineIndex - index != 0){
                        lc.addLine(line);
                        lastLinkOrder = linkOrder-1;
                        line = new Line(undirected, index, frequency);
                    }

                    if(linkOrder != lastLinkOrder+1){
                        throw new DataInconsistentException("link order not " +
                        "continuous");
                    }

                    Link link = ptn.getLinkByIndex(linkIndex);

                    if(link == null){
                        throw new DataInconsistentException("link with index "
                                + linkIndex + " does not exist");
                    }

                    line.addLink(link);

                    lastLinkOrder = linkOrder;
                    lastLineIndex = index;

                }
                catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }
            }

            if(lastLineIndex != null){
                lc.addLine(line);
            }


        } catch (DataInconsistentException e){
            throw new DataInconsistentException("line " + reader.getLineNumber()
                    + " in file " + lcFile.getAbsolutePath()
                    + " invalid: " + e.getMessage());
        }

        reader.close();

    }

    /**
     * Writes a line concept to file.
     *
     * @param lc The line collection to read from.
     * @param lcFile The line concept file to write to.
     * @param undirected Whether or not the line concept should be written to file
     * undirectedly. May only be true if <code>lc</code> already is undirected.
     * It may be used to directify <code>lc</code>, as can be seen in
     * {@link LineConceptDirectification}.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(LineCollection lc, File lcFile,
            Boolean undirected) throws IOException, DataInconsistentException{

        PublicTransportationNetwork ptn = lc.getPublicTransportationNetwork();

        lcFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(lcFile);

        fw.write("# line_index; link_order; link_index; frequency\n");

        if(undirected){
            if(!lc.isUndirected()){
                throw new DataInconsistentException("line concept is not " +
                        "undirected");
            }

            for(Map.Entry<Integer, Line> e : lc.getUndirectedIndexLineMap().entrySet()){
                Integer index = e.getKey();
                Line line = e.getValue();
                Integer linkOrderCount = 1;
                for(Link link : line.getLinks()){
                    fw.write(index + "; " + linkOrderCount + "; "
                            + ptn.getUndirectedIndexByLink(link) + "; "
                            + line.getFrequency() + "\n");
                    linkOrderCount++;
                }
            }
        }

        else{

            for(Entry<Line, Integer> e1 : lc.getDirectedLineIndexMap().entrySet()){
                Line line = e1.getKey();
                Integer index = e1.getValue();
                Integer linkOrderCount = 1;
                for(Link link : line.getLinks()){
                    fw.write(index + "; " + linkOrderCount + "; "
                            + ptn.getDirectedIndexByLink(link) + "; "
                            + line.getFrequency() + "\n");
                    linkOrderCount++;
                }
            }

        }

        fw.close();

    }

}
