package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity;
import net.lintim.model.EventActivityNetwork;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Handles initial duration assumption CSV files and allows reading and writing.
 *
 * Syntax:
 * activity index; initial duration assumption
 */

public class InitialDurationAssumptionCSV {

    /**
     * Reads an initial duration assumption from file.
     *
     * @param ean The event activity network to write the initial duration
     * assumption to.
     * @param initialDurationAssumptionPeriodicFile The file to read the
     * initial duration assumption from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(EventActivityNetwork ean,
            File initialDurationAssumptionPeriodicFile) throws
            IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(initialDurationAssumptionPeriodicFile);

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
                    Double initialDurationAssumption = Double.parseDouble(itr.next());

                    Activity activity = ean.getActivityByIndex(index);
                    if(activity == null){
                        throw new DataInconsistentException("activity " +
                                "index " + index + " not found; " +
                                        "activity index is " + index);
                    }

                    activity.setInitialDurationAssumption(initialDurationAssumption);

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        initialDurationAssumptionPeriodicFile.getAbsolutePath()
                        + " invalid: " + e.getMessage());
            }
        }

        reader.close();

        ean.checkStructuralCompleteness();

    }

    /**
     * Writes an initial duration assumption to file.
     *
     * @param ean The event activity network to read the initial duration
     * assumption from.
     * @param initialDurationAssumptionPeriodicFile The file to write the
     * initial duration assumption to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(EventActivityNetwork ean,
            File initialDurationAssumptionPeriodicFile) throws
            IOException, DataInconsistentException{

        LinkedHashSet<Activity> activities = ean.getActivities();

        initialDurationAssumptionPeriodicFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(initialDurationAssumptionPeriodicFile);

        fw.write("# activity_index; initial_duration_assumption\n");

        for(Activity activity : activities){
            if (activity.getInitialDurationAssumption() == null) {
                continue;
            }
            fw.write(activity.getIndex() + "; "
                    + Formatter.format(activity.getInitialDurationAssumption())
                    + "\n");

        }

        fw.close();

    }

}
