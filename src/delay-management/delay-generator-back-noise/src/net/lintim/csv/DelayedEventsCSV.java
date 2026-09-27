package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.PeriodicEvent;

public class DelayedEventsCSV {
	
		public static ArrayList<PeriodicEvent> fromFile(File eventsPeriodicFile, ArrayList<PeriodicEvent> listOfEvents) throws IOException {
		CsvReader csvReader = new CsvReader(eventsPeriodicFile);
		List<String> line;
		while ((line = csvReader.read()) != null) {
				Iterator<String> itr = line.iterator();
				Integer event_index = Integer.parseInt(itr.next());
				Integer delay = Integer.parseInt(itr.next());
				listOfEvents.get(event_index-1).setDelay(delay);
		}
				
		csvReader.close();
		return listOfEvents;
	}
	
	public static void toFile(File eventsDelayedFile, ArrayList<PeriodicEvent> listOfEvents, boolean append_delays) throws IOException {
		FileWriter fw = new FileWriter(eventsDelayedFile, append_delays);
		if(!append_delays) 
			fw.write("# ID; delay\n");

		for (PeriodicEvent event : listOfEvents) {
			fw.write(event.getIndex() + "; " +event.getDelay() + "\n");
		}
		fw.close();
	}
}
