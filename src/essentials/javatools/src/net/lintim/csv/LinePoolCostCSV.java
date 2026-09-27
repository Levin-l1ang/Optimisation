package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConceptDirectification;
import net.lintim.model.Line;
import net.lintim.model.LineCollection;
import net.lintim.util.MathHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handles line pool cost CSV files and allows reading and writing.
 *
 * Syntax:
 * index; length; cost
 */

public class LinePoolCostCSV {

    protected static NumberFormat formatter = new DecimalFormat("#.000");

    /**
     * Reads line pool costs from file.
     *
     * @param lc The line collection to write to.
     * @param lcFile The line pool cost file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(LineCollection lc, File lcFile) throws
    IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(lcFile);
        Boolean undirected = lc.isUndirected();

        List<String> csvLine;

        try{

            while ((csvLine = reader.read()) != null) {

                try{

                    int size = csvLine.size();
                    if(size != 3){
                        throw new DataInconsistentException("3 columns needed, " +
                                size + " given");
                    }

                    Iterator<String> itr = csvLine.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    Double length = Double.parseDouble(itr.next());
                    Double cost = Double.parseDouble(itr.next());

                    Line line;

                    if(undirected){
                        line = lc.getLineByUndirectedIndex(index);

                        if(line == null){
                            throw new DataInconsistentException("line " +
                                    index + " does not exist");
                        }

                        Line counterpart = line.getUndirectedCounterpart();

                        line.setCost(cost);
                        counterpart.setCost(cost);
                    }
                    else{
                        line = lc.getLineByDirectedIndex(index);
                        line.setCost(cost);


                    }

                    if(Math.abs(line.getLength() - length) > MathHelper.epsilon){
                        System.err.println("Warning: line " + line.getIndex() +
                                " length differs by " + (length - line.getLength())
                                + " from sum of edge lengths");
                        line.setLength(length);
//						throw new DataInconsistentException("line length " +
//								"does not match");
                    }


                }
                catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }
            }

        } catch (DataInconsistentException e){
            throw new DataInconsistentException("line " + reader.getLineNumber()
                    + " in file " + lcFile.getAbsolutePath()
                    + " invalid: " + e.getMessage());
        }

        reader.close();

    }

    /**
     * Writes line pool costs to file.
     *
     * @param lc The line collection to read from.
     * @param lcFile The line pool cost file to write to.
     * @param undirected Whether or not line pool costs should be written to file
     * undirectedly. May only be true if <code>lc</code> already is undirected.
     * It may be used to directify <code>lc</code>, as can be seen in
     * {@link LineConceptDirectification}.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(LineCollection lc, File lcFile, Boolean undirected)
            throws IOException, DataInconsistentException {

        lcFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(lcFile);

        fw.write("# line_index; length; cost\n");

        if (undirected) {
            if (!lc.isUndirected()) {
                throw new DataInconsistentException("line concept is not "
                        + "undirected");
            }

            for (Map.Entry<Integer, Line> e : lc.getUndirectedIndexLineMap()
                    .entrySet()) {
                Integer index = e.getKey();
                Line line = e.getValue();

                fw.write(index + "; " + formatter.format(line.getLength())
                        + "; " + formatter.format(line.getCost()) + "\n");

            }
        }

        else {

            for (Entry<Line, Integer> e1 : lc.getDirectedLineIndexMap().entrySet()){
                Line line = e1.getKey();
                Integer index = e1.getValue();

                fw.write(index + "; "
                        + formatter.format(line.getLength()) + "; "
                        + formatter.format(line.getCost()) + "\n");

            }

        }

        fw.close();

    }

}
