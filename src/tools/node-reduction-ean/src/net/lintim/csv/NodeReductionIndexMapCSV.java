package net.lintim.csv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.Activity;
import net.lintim.model.Event;

public class NodeReductionIndexMapCSV {

	public static HashMap<Integer, Integer> fromEventIndexFile() {
		HashMap<Integer, Integer> mapOfIndexes = null;
		try {
			File indexMapFile = new File("timetabling/NodeReductionEventIndexMap.giv");
			mapOfIndexes = new HashMap<Integer, Integer>();
			CsvReader csvReader = new CsvReader(indexMapFile);
			List<String> line;
			while ((line = csvReader.read()) != null) {
				Iterator<String> itr = line.iterator();
				mapOfIndexes.put(Integer.parseInt(itr.next()), Integer.parseInt(itr.next()));
			}
			csvReader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return mapOfIndexes;
	}
	
	public static HashMap<Integer, Integer> fromActivityIndexFile() {
		HashMap<Integer, Integer> mapOfIndexes = null;
		try {
			File indexMapFile = new File("timetabling/NodeReductionActivityIndexMap.giv");
			mapOfIndexes = new HashMap<Integer, Integer>();
			CsvReader csvReader = new CsvReader(indexMapFile);
			List<String> line;
			while ((line = csvReader.read()) != null) {
				Iterator<String> itr = line.iterator();
				mapOfIndexes.put(Integer.parseInt(itr.next()), Integer.parseInt(itr.next()));
			}
			csvReader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return mapOfIndexes;
	}

	public static void toFile(ArrayList<Event> listOfEvents, ArrayList<Activity> listOfActivities, ArrayList<Integer> listOfActivityIndexes, ArrayList<Integer> listOfEventIndexes) {

		try {
			File file = new File("timetabling/NodeReductionActivityIndexMap.giv");
			FileWriter fw = new FileWriter(file, false);

			fw.write("# old_event_index; new_event_index\n");
			int activityCounter = 0;
			for (Activity activity : listOfActivities) {
				if (listOfEventIndexes.contains(activity.getIndex())) {
					activityCounter++;
					fw.write(activity.getIndex() + "; " + activityCounter + "\n");
				} else {
					fw.write(activity.getIndex() + "; 0" + "\n");
				}
			}
			file = new File("timetabling/NodeReductionEventIndexMap.giv");
			fw = new FileWriter(file, false);

			fw.write("# old_event_index; new_event_index\n");
			int eventcounter = 0;
			for (Event event : listOfEvents) {
				if (listOfEventIndexes.contains(event.getIndex())) {
					eventcounter++;
					fw.write(event.getIndex() + "; " + eventcounter + "\n");
				} else {
					fw.write(event.getIndex() + "; 0" + "\n");
				}
			}
			fw.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
