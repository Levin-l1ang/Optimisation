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
 * Handles line pool CSV files and allows reading and writing.
 *
 * Syntax:
 * index; link order; link index
 */

public class LinePoolCSV {

    /**
     * Reads a line pool from file.
     *
     * @param lc The line collection to write to.
     * @param lcFile The line pool file to read from.
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
                    if(size != 3){
                        throw new DataInconsistentException("3 columns needed, " +
                                size + " given");
                    }

                    Iterator<String> itr = csvLine.iterator();
                    index = Integer.parseInt(itr.next());
                    Integer linkOrder = Integer.parseInt(itr.next());
                    Integer linkIndex = Integer.parseInt(itr.next());

                    if(lastLineIndex == null){
                        lastLineIndex = index;
                        lastLinkOrder = linkOrder-1;
                        line = new Line(undirected, index);
                    }

                    if(lastLineIndex - index != 0){
                        lc.addLine(line);
                        lastLinkOrder = linkOrder-1;
                        line = new Line(undirected, index);
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
     * Writes a line pool to file.
     *
     * @param lpool The line collection to read from.
     * @param lpoolFile The line pool file to write to.
     * @param undirected Whether or not the line pool should be written to file
     * undirectedly. May only be true if <code>lpool</code> already is undirected.
     * It may be used to directify <code>lpool</code>, as can be seen in
     * {@link LineConceptDirectification}.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(LineCollection lpool, File lpoolFile,
            Boolean undirected) throws IOException, DataInconsistentException{

        PublicTransportationNetwork ptn = lpool.getPublicTransportationNetwork();

        lpoolFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(lpoolFile);

        fw.write("# line_index; link_order; link_index\n");

        if(undirected){
            if(!lpool.isUndirected()){
                throw new DataInconsistentException("line concept is not " +
                        "undirected");
            }

            for(Map.Entry<Integer, Line> e :
                lpool.getUndirectedIndexLineMap().entrySet()){

                Integer index = e.getKey();
                Line line = e.getValue();
                Integer linkOrderCount = 1;
                for(Link link : line.getLinks()){
                    fw.write(index + "; " + linkOrderCount + "; "
                            + ptn.getUndirectedIndexByLink(link) + "\n");
                    linkOrderCount++;
                }
            }
        }

        else{

            for(Entry<Line, Integer> e1 :
                lpool.getDirectedLineIndexMap().entrySet()){

                Line line = e1.getKey();
                Integer index = e1.getValue();
                Integer linkOrderCount = 1;

                for(Link link : line.getLinks()){
                    fw.write(index + "; " + linkOrderCount + "; "
                            + ptn.getDirectedIndexByLink(link) + "\n");
                    linkOrderCount++;
                }

            }

        }

        fw.close();

    }

}
