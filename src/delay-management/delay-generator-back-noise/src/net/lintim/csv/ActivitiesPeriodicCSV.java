package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;

public class ActivitiesPeriodicCSV {
	/*
	 * Reads the periodic Activities from Activities-periodic.giv
	 */
	public ActivitiesPeriodicCSV() {
	}

	public static ArrayList<PeriodicActivity> fromFile(File activitiesPeriodicFile, ArrayList<PeriodicEvent> listOfEvents) throws IOException {
		CsvReader csvReader = new CsvReader(activitiesPeriodicFile);
		List<String> line;
		ArrayList<PeriodicActivity> activity = new ArrayList<PeriodicActivity>();
		while ((line = csvReader.read()) != null) {
			Iterator<String> itr = line.iterator();
			Integer activityNO = Integer.parseInt(itr.next());
			Integer periodic_id = Integer.parseInt(itr.next());
			String type = new String(itr.next());
			Integer fromEvent = Integer.parseInt(itr.next());
			Integer toEvent = Integer.parseInt(itr.next());
			Integer lowerBound = Integer.parseInt(itr.next());
			Double passenger = Double.parseDouble(itr.next());
			Integer delay = 0;
			activity.add(activityNO-1,new PeriodicActivity(activityNO, periodic_id, type, listOfEvents.get(fromEvent - 1), listOfEvents.get(toEvent - 1), lowerBound, passenger, delay));
		}
		csvReader.close();

		return activity;
	}

	public static void toFile(File activitiesPeriodicFile, ArrayList<PeriodicActivity> listOfActivitys) throws IOException {

		FileWriter fw = new FileWriter(activitiesPeriodicFile);

		// TODO make proper annotation framework for all CSV writers
		fw.write("# activity_index; type; from_event; to_event; lower_bound; upper_bound; passengers\n");

		Iterator<PeriodicActivity> itr = listOfActivitys.iterator();

		while (itr.hasNext()) {
			PeriodicActivity activity = itr.next();
			fw.write(activity.getIndex() + "; " + activity.getPeriodic_index() + "; \"" + activity.getType() + "\"; " + activity.getFromEvent() + "; " + activity.getToEvent() + "; "
					+ activity.getLowerBound() + "; " + activity.getPassenger() + "\n");
		}
		fw.close();
	}

}
