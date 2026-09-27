package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Handles the periodic event activity network CSV format and allows reading and
 * writing.
 *
 * Syntax events:
 * index; type; station index; line index; passengers
 *
 * Syntax activities:
 * index; type; from event; to event; lower bound; upper bound; passengers
 */

public class PeriodicEventActivityNetworkCSV {

    /**
     * Reads events and activities from file.
     *
     * @param ean The event activity network to write to.
     * @param eventsFile The events file to read from.
     * @param activitiesFile The activities file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(EventActivityNetwork ean, File eventsFile,
            File activitiesFile) throws
            IOException, DataInconsistentException {

        PublicTransportationNetwork ptn = ean.getPublicTransportationNetwork();
        LineCollection lc = ean.getLineConcept();

        CsvReader reader = new CsvReader(eventsFile);

        List<String> line;

        Boolean linesUndirected = ean.getLineConcept().isUndirected();

        while ((line = reader.read()) != null) {

            try{

                try{

                    int size = line.size();

                    if(size != 7){
                        throw new DataInconsistentException("7 columns needed, " +
                                size + " given");
                    }

                    Iterator<String> itr = line.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    Event.EventType type = Event.EventType.valueOf(itr.next().
                            trim().toUpperCase());
                    Integer stationIndex = Integer.parseInt(itr.next());
                    Integer lineIndex = Integer.parseInt(itr.next());
                    Double passengers = Double.parseDouble(itr.next());
                    Event.LineDirection direction;
                    String value = itr.next();
                    switch (value){
                        case ">":
                            direction = Event.LineDirection.FORWARDS;
                            break;
                        case "<":
                            direction = Event.LineDirection.BACKWARDS;
                            break;
                        default:
                            throw new DataInconsistentException("Line direction needs to be > or < but is " + value);
                    }
                    int lineFrequencyRepetition = Integer.parseInt(itr.next());

                    Station station = ptn.getStationByIndex(stationIndex);
                    if(station == null){
                        throw new DataInconsistentException("station index " +
                                stationIndex + " does not exist");
                    }

                    if(linesUndirected){
                        ean.addEventLineUndirected(new Event(index, type,
                                station, null, null, null, null, passengers,
                                null, direction, lineFrequencyRepetition),
                                lineIndex);
                    }
                    else{
                        ean.addEvent(new Event(index, type, station,
                                lc.getLineByIndex(lineIndex), null, null, null,
                                passengers, null, direction, lineFrequencyRepetition));
                    }

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        eventsFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        reader = new CsvReader(activitiesFile);

        while ((line = reader.read()) != null) {

            try{

                try{

                    int size = line.size();
                    if(size != 7){
                        throw new DataInconsistentException("7 columns " +
                                "needed, " + size + " given");
                    }

                    Iterator<String> itr = line.iterator();
                    Integer index = Integer.parseInt(itr.next());
                    Activity.ActivityType type = Activity.ActivityType.valueOf(
                            itr.next().trim().toUpperCase());
                    Integer fromEventIndex = Integer.parseInt(itr.next());
                    Integer toEventIndex = Integer.parseInt(itr.next());
                    Double lowerBound = Double.parseDouble(itr.next());
                    Double upperBound = Double.parseDouble(itr.next());
                    Double passengers = Double.parseDouble(itr.next());

                    Event fromEvent = ean.getEventByIndex(fromEventIndex);
                    if(fromEvent == null){
                        throw new DataInconsistentException("fromEvent " +
                                "index " + fromEventIndex + " not found; " +
                                        "activity index is " + index);
                    }

                    Event toEvent = ean.getEventByIndex(toEventIndex);
                    if(toEvent == null){
                        throw new DataInconsistentException("toEvent " +
                                "index " + toEventIndex + " not found; " +
                                        "activity index is " + index);
                    }

                    ean.addActivity(new Activity(index, type, fromEvent, toEvent,
                            null, lowerBound, upperBound, passengers, null, null));


                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        activitiesFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        ean.checkStructuralCompleteness();

    }

    /**
     * Writes a periodic event activity network to file.
     *
     * @param ean The event activity network to read from.
     * @param eventsFile The events file to write to.
     * @param activitiesFile The activities file to write to.
     * @param discardUnusedChangeActivities Whether or not to discard change
     * activities with zero passengers.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(EventActivityNetwork ean, File eventsFile,
            File activitiesFile, Boolean discardUnusedChangeActivities)
    throws IOException, DataInconsistentException{

        LinkedHashSet<Event> events = ean.getEvents();
        LinkedHashSet<Activity> activities = ean.getActivities();

        eventsFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(eventsFile);

        fw.write("# event_id; type; stop-id; line-id; passengers; line-direction; line-freq-repetition\n");

        for(Event event : events){

            Line line = event.getLine();

            String directionLabel = event.getLineDirection().equals(Event.LineDirection.FORWARDS) ? ">" : "<";
            fw.write(event.getIndex() + "; \""
                    + event.getType().name().toLowerCase() + "\"; "
                    + event.getStation().getIndex() + "; " + line.getIndex() + "; "
                    + Formatter.format(event.getPassengers()) + "; " + directionLabel + "; " + event
                    .getFrequencyRepetition() + "\n");

        }

        fw.close();

        activitiesFile.getParentFile().mkdirs();
        fw = new FileWriter(activitiesFile);

        fw.write("# activity_index; type; from_event; to_event; lower_bound; " +
        "upper_bound; passengers\n");

        int counter = 1;

        for(Activity activity : activities){

            if(!discardUnusedChangeActivities ||
                    activity.getType() != Activity.ActivityType.CHANGE ||
                    activity.getPassengers() > 0.0){

                fw.write((discardUnusedChangeActivities ?
                        counter : activity.getIndex()) + "; \""
                        + activity.getType().name().toLowerCase() + "\"; "
                        + activity.getFromEvent().getIndex() + "; "
                        + activity.getToEvent().getIndex() + "; "
                        + Formatter.format(activity.getLowerBound()) + "; "
                        + Formatter.format(activity.getUpperBound()) + "; "
                        + Formatter.format(activity.getPassengers()) + "\n");

                ++counter;
            }

        }

        fw.close();

    }

}
