package net.lintim.csv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.Event;

public class TimetablePeriodicCSV {

	public static ArrayList<Event> fromFile(File periodicTimetableFile, ArrayList<Event> listOfEvents) {
		try {
			CsvReader csvReader = new CsvReader(periodicTimetableFile);
			List<String> line;
			while ((line = csvReader.read()) != null) {
				Iterator<String> itr = line.iterator();
				listOfEvents.get(Integer.parseInt(itr.next()) -1).setTime(Integer.parseInt(itr.next()));
			}
			csvReader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return listOfEvents;
	}

	public static void toFile(File periodicTimetableFile, ArrayList<Event> listOfEvents){

		try {
			FileWriter fw = new FileWriter(periodicTimetableFile);

			fw.write("# event_index; time\n");

			Iterator<Event> itr = listOfEvents.iterator();
			while (itr.hasNext()) {
				Event event = itr.next();
				fw.write(event.getIndex() + "; " + event.getTime() + "\n");
			}
			fw.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
