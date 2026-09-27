package net.lintim.csv;

import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity;
import net.lintim.model.EventActivityNetwork;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Handles periodic event activity network duration CSV files and allows
 * reading and writing.
 *
 * Syntax:
 * activity index; duration.
 */
public class DurationsCSV {

    /**
     * Reads durations from file.
     *
     * @param ean The event activity network to write to.
     * @param durationsFile The durations file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(EventActivityNetwork ean, File durationsFile)
    throws IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(durationsFile);

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

                    Integer activityIndex = Integer.parseInt(itr.next());
                    Double duration = Double.parseDouble(itr.next());

                    Activity activity = ean.getActivityByIndex(activityIndex);

                    if(activity == null){
                        throw new DataInconsistentException("activity index " +
                                activityIndex + " does not exist");
                    }

                    else if(activity.getDuration() != null){
                        throw new DataInconsistentException("activity index " +
                                activityIndex + " already has duration");
                    }

                    activity.setDuration(duration);

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        durationsFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        ean.computeTimetableFromDurations();

    }

    /**
     * Writes durations to file.
     *
     * @param ean The event activity network to read from.
     * @param durationsFile The durations file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(EventActivityNetwork ean, File durationsFile)
    throws IOException, DataInconsistentException{

        durationsFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(durationsFile);

        // TODO make proper annotation framework for all CSV writers
        fw.write("# objective: "
                + PeriodicTimetableEvaluator.averageTravelingTime(ean) + "\n");

        fw.write("# activity-id; time\n");

        for(Activity activity : ean.getActivities()){

            fw.write(activity.getIndex() + "; " +
                    Formatter.format(activity.getDuration()) + "\n");

        }

        fw.close();

    }
}
