package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.PeriodicEvent;

public class EventsPeriodicCSV {
	public static ArrayList<PeriodicEvent> fromFile(File eventsPeriodicFile) throws IOException {
		CsvReader csvReader = new CsvReader(eventsPeriodicFile);
		List<String> textLine;
		ArrayList<PeriodicEvent> listOfEvents = new ArrayList<PeriodicEvent>();
		while ((textLine = csvReader.read()) != null) {
			Iterator<String> itr = textLine.iterator();
			Integer eventIndex = Integer.parseInt(itr.next());
			Integer periodic_id = Integer.parseInt(itr.next());
			String type = new String(itr.next());
			Long time = new Long(itr.next());
			double passenger = Double.parseDouble(itr.next());
			Integer delay = 0;
			listOfEvents.add(eventIndex - 1, new PeriodicEvent(eventIndex, periodic_id, type, time, passenger, delay));
		}
		csvReader.close();

		return listOfEvents;
	}

	public static void toFile(File eventsPeriodicFile, ArrayList<PeriodicEvent> listOfEvents) throws IOException {

		FileWriter fw = new FileWriter(eventsPeriodicFile);

		// TODO make proper annotation framework for all CSV writers
		fw.write("# event_index; type; station; line; passenger\n");

		Iterator<PeriodicEvent> itr = listOfEvents.iterator();

		while (itr.hasNext()) {
			PeriodicEvent event = itr.next();
			fw.write(event.getIndex() + "; " + event.getPeriodic_index() + "; \"" + event.getType() + "\"; " + event.getTime() + "; " + event.getPassengers() + "\n");
		}
		fw.close();
	}
}
