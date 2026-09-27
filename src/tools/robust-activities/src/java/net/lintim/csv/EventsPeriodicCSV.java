package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.Event;

public class EventsPeriodicCSV {
    public static ArrayList<Event> fromFile(File eventsPeriodicFile) throws IOException {
        CsvReader csvReader = new CsvReader(eventsPeriodicFile);
        List<String> textLine;
        ArrayList<Event> listOfEvents = new ArrayList<Event>();
        while ((textLine = csvReader.read()) != null) {
            Iterator<String> itr = textLine.iterator();
            Integer eventIndex = Integer.parseInt(itr.next().trim());
            String type = itr.next().trim();
            Integer station = Integer.parseInt(itr.next().trim());
            Integer line = Integer.parseInt(itr.next().trim());
            double passenger = Double.parseDouble(itr.next().trim());
            listOfEvents.add(eventIndex - 1, new Event(eventIndex, type, station, line, passenger));
        }
        csvReader.close();

        return listOfEvents;
    }

    public static void toFile(File eventsPeriodicFile, ArrayList<Event> listOfEvents) throws IOException {

        FileWriter fw = new FileWriter(eventsPeriodicFile);

        fw.write("# event_index; type; station; line; passenger\n");

        Iterator<Event> itr = listOfEvents.iterator();

        while (itr.hasNext()) {
            Event event = itr.next();
            fw.write(event.getIndex() + "; \"" + event.getType() + "\"; " + event.getStation() + "; " + event.getLine() + "; " + event.getPassengers() + "\n");
        }
        fw.close();
    }
}
