package net.lintim.csv;

import net.lintim.evaluator.PeriodicTimetableEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Event;
import net.lintim.model.EventActivityNetwork;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Handles the periodic timetable CSV format and allows reading and writing.
 *
 * Syntax:
 * activity index; time
 */

public class TimetableCSV {

    /**
     * Reads a timetable from file.
     *
     * @param ean The event activity network to write to.
     * @param timetableFile The timetable file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(EventActivityNetwork ean, File timetableFile)
    throws IOException, DataInconsistentException {

        CsvReader reader = new CsvReader(timetableFile);

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
                    Integer eventIndex = Integer.parseInt(itr.next());
                    Double time = Double.parseDouble(itr.next());

                    Event event = ean.getEventByIndex(eventIndex);

                    if(event == null){
                        throw new DataInconsistentException("event index " +
                                eventIndex + " does not exist");
                    }

                    else if(event.getTime() != null){
                        throw new DataInconsistentException("event index " +
                                eventIndex + " already has timetable data");
                    }

                    event.setTime(time);

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        timetableFile.getAbsolutePath()	+ " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        ean.computeDurationsFromTimetable();

    }

    /**
     * Writes a timetable to file.
     *
     * @param ean The event activity network to read from.
     * @param timetableFile The timetable file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(EventActivityNetwork ean, File timetableFile)
    throws IOException, DataInconsistentException{

        timetableFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(timetableFile);

        // TODO make proper annotation framework for all CSV writers
        fw.write("# objective: "
                + PeriodicTimetableEvaluator.averageTravelingTime(ean) + "\n");

        fw.write("# event_index; time\n");

        for(Event event : ean.getEvents()){

            fw.write(event.getIndex() + "; " +
                    Formatter.format(event.getTime()) + "\n");

        }

        fw.close();

    }
}
