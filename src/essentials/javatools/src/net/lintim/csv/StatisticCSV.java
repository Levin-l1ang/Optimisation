package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Statistic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Handles the statistic CSV format and allows reading and writing.
 *
 * Syntax:
 * key; value
 */

public class StatisticCSV {

    /**
     * Reads a statistic from file.
     *
     * @param statistic The statistic to write to.
     * @param statisticFile The statistic file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     * @throws FileNotFoundException
     */
    public static void fromFile(Statistic statistic, File statisticFile)
    throws IOException, DataInconsistentException, FileNotFoundException {
        CsvReader reader = new CsvReader(statisticFile);

        List<String> line;

        while ((line = reader.read()) != null) {

            try{
                int size = line.size();
                if(size != 2){
                    throw new DataInconsistentException("2 columns needed, " +
                            size + " given");
                }

                Iterator<String> itr = line.iterator();
                String key = itr.next();
                String value = itr.next();
                statistic.setValue(key, value);

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        statisticFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();
    }

    /**
     * Writes a statistic to file.
     *
     * @param statistic The statistic to read from.
     * @param statisticFile The statistic file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     * @throws FileNotFoundException
     */
    // TODO integrate into CSV write framework
    public static void toFile(Statistic statistic, File statisticFile)
    throws IOException, DataInconsistentException, FileNotFoundException {

        statisticFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(statisticFile);

        TreeMap<String,String> sorted_map =
        		new TreeMap<String,String>(statistic.getData());

        for(Map.Entry<String, String> e : sorted_map.entrySet()){
            fw.write(e.getKey() +  "; " + e.getValue() + "\n");
        }

        /*for(Map.Entry<String, String> e : statistic.getData().entrySet()){
            fw.write(e.getKey() +  "; " + e.getValue() + "\n");
        }*/

        fw.close();
    }


}
