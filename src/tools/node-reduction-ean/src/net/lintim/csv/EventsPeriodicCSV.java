package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Event;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EventsPeriodicCSV {
	public static ArrayList<Event> fromFile(File eventsPeriodicFile) throws IOException, DataInconsistentException {
		CsvReader csvReader = new CsvReader(eventsPeriodicFile);
		List<String> textLine;
		ArrayList<Event> listOfEvents = new ArrayList<Event>();
		while ((textLine = csvReader.read()) != null) {
			Iterator<String> itr = textLine.iterator();
			Integer eventIndex = Integer.parseInt(itr.next());
			String type = new String(itr.next());
			Integer station = Integer.parseInt(itr.next());
			Integer line = Integer.parseInt(itr.next());
			Integer passenger = Integer.parseInt(itr.next());
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
			listOfEvents.add(eventIndex - 1, new Event(eventIndex, type, station, line, passenger, direction, lineFrequencyRepetition));
		}
		csvReader.close();

		return listOfEvents;
	}

	public static void toNodeReducedFile(File eventsPeriodicFile, ArrayList<Event> listOfEvents, ArrayList<Integer> listOfIndexes) throws IOException {

		FileWriter fw = new FileWriter(eventsPeriodicFile);

		fw.write("# event_id; type; stop-id; line-id; passengers; line-direction; line-freq-repetition\n");
		int eventCounter = 0;
		for (Event event : listOfEvents) {
			if (!listOfIndexes.contains(event.getIndex())) {
				eventCounter++;
				event.setIndex(eventCounter);
				String direction = event.getLineDirection().equals(Event.LineDirection.FORWARDS) ? ">" : "<";
				fw.write(event.getIndex() + "; \"" + event.getType() + "\"; " + event.getStation() + "; " +
						event.getLine() + "; " + event.getPassengers() + "; " + direction + "; " + event
						.getFrequencyRepetition() + "\n");
			}
		}
		fw.close();
	}

	public static void toNodeFullFile(File eventsPeriodicFile, ArrayList<Event> listOfEvents) throws IOException {

		FileWriter fw = new FileWriter(eventsPeriodicFile);

		fw.write("# event_index; type; station; line; passenger\n");

		Iterator<Event> itr = listOfEvents.iterator();

		while (itr.hasNext()) {
			Event event = itr.next();
			String direction = event.getLineDirection().equals(Event.LineDirection.FORWARDS) ? ">" : "<";
			fw.write(event.getIndex() + "; \"" + event.getType() + "\"; " + event.getStation() + "; " +
					event.getLine() + "; " + event.getPassengers() + "; " + direction + "; " + event
					.getFrequencyRepetition() + "\n");
		}
		fw.close();
	}
}
