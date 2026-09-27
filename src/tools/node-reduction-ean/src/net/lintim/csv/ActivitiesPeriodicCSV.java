package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.Activity;
import net.lintim.model.Event;

public class ActivitiesPeriodicCSV {
	/*
	 * Reads the periodic Activities from Activities-periodic.giv
	 */
	public ActivitiesPeriodicCSV() {
	}

	public static ArrayList<Activity> fromFile(File activitiesPeriodicFile, ArrayList<Event> listOfEvents) throws IOException {
		CsvReader csvReader = new CsvReader(activitiesPeriodicFile);
		List<String> line;
		ArrayList<Activity> activity = new ArrayList<Activity>();
		while ((line = csvReader.read()) != null) {
			Iterator<String> itr = line.iterator();
			Integer activityNO = Integer.parseInt(itr.next());
			String type = new String(itr.next());
			Integer fromEvent = Integer.parseInt(itr.next());
			Integer toEvent = Integer.parseInt(itr.next());
			Integer lowerBound = Integer.parseInt(itr.next());
			Integer upperBound = Integer.parseInt(itr.next());
			Integer passenger = Integer.parseInt(itr.next());
			if (!type.equals("headway")) {
				activity.add(new Activity(activityNO, type, listOfEvents.get(fromEvent - 1), listOfEvents.get(toEvent - 1), lowerBound, upperBound, passenger));
			}
		}
		csvReader.close();

		return activity;
	}

	public static void toNodeReducedFile(File activitiesPeriodicFile, ArrayList<Activity> listOfActivities) throws IOException {
		FileWriter fw = new FileWriter(activitiesPeriodicFile);

		fw.write("# activity_index; type; from_event; to_event; lower_bound; upper_bound; passengers\n");

		Iterator<Activity> itr = listOfActivities.iterator();
		int activityCounter = 1;
		while (itr.hasNext()) {
			Activity activity = itr.next();
			activity.setIndex(activityCounter);
			activityCounter++;
			fw.write(activity.getIndex() + "; \"" + activity.getType() + "\"; " + activity.getFromEvent().getIndex() + "; " + activity.getToEvent().getIndex() + "; " + activity.getLowerBound() + "; "
					+ activity.getUpperBound() + "; " + activity.getPassenger() + "\n");
		}
		fw.close();
	}

	public static void toNodeFullFile(File activitiesPeriodicFile, ArrayList<Activity> listOfActivitys) throws IOException {

		FileWriter fw = new FileWriter(activitiesPeriodicFile);

		fw.write("# activity_index; type; from_event; to_event; lower_bound; upper_bound; passengers\n");

		Iterator<Activity> itr = listOfActivitys.iterator();
		while (itr.hasNext()) {
			Activity activity = itr.next();
			fw.write(activity.getIndex() + "; \"" + activity.getType() + "\"; " + activity.getFromEvent().getIndex() + "; " + activity.getToEvent().getIndex() + "; " + activity.getLowerBound() + "; "
					+ activity.getUpperBound() + "; " + activity.getPassenger() + "\n");
		}
		fw.close();
	}

}
