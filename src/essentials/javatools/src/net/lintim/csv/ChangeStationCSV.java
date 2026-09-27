package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ChangeStationCSV {
    public static Set<Integer> fromFile(File changeStationFile) throws IOException, DataInconsistentException {
        CsvReader reader = new CsvReader(changeStationFile);

        List<String> line;
        Set<Integer> changeStationIds = new HashSet<>();

        while ((line = reader.read()) != null) {
            try {
                int size = line.size();
                if(size != 1){
                    throw new DataInconsistentException("1 column needed, " +
                        size + " given");
                }
                try{
                    Iterator<String> itr = line.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    changeStationIds.add(index);

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("index should be a " +
                        "number, but it is not");
                }

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " +
                    reader.getLineNumber() + " in file " +
                    changeStationFile.getAbsolutePath() + " invalid: " +
                    e.getMessage());
            }
        }
        reader.close();
        return changeStationIds;
    }
}
