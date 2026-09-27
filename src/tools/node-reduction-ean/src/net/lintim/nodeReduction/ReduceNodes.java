package net.lintim.nodeReduction;

import net.lintim.csv.ActivitiesPeriodicCSV;
import net.lintim.csv.EventsPeriodicCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity;
import net.lintim.model.Event;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ReduceNodes {

	public static ArrayList<Event> listOfEventsFull;
	public static ArrayList<Activity> listOfActivitiesFull;
	private ArrayList<Integer> listOfEventIdexes;
	private ArrayList<Integer> listOfActivityIndexes;

	public void reduceNodes(File activitiesPeriodicFile, File eventsPeriodicFile) {
		ArrayList<Event> listOfEvents = null;
		ArrayList<Activity> listOfActivities = null;
		listOfEventIdexes = new ArrayList<Integer>();
		listOfActivityIndexes = new ArrayList<Integer>();
		try {
			listOfEvents = EventsPeriodicCSV.fromFile(eventsPeriodicFile);
			listOfActivities = ActivitiesPeriodicCSV.fromFile(activitiesPeriodicFile, listOfEvents);
			listOfEventsFull = EventsPeriodicCSV.fromFile(eventsPeriodicFile);
			listOfActivitiesFull = ActivitiesPeriodicCSV.fromFile(activitiesPeriodicFile, listOfEventsFull);
		} catch (IOException | DataInconsistentException e) {
			e.printStackTrace();
		}
		int activityCounter = 0;
		int eventCounter = 0;
		for(Event event: listOfEvents){
			if(event.getType().equals("arrival") && event.getIncomingActivities(listOfActivities).size()== 1){
				eventCounter++;
				activityCounter++;
			}
		}
//		for (Event event : listOfEvents) {
//			ArrayList<Activity> incomingActivity = event.getIncomingActivities(listOfActivities);
//			ArrayList<Activity> outgoingActivity = event.getOutgoingActivities(listOfActivities);
//			if (incomingActivity.size() == 1 && outgoingActivity.size() == 1 && incomingActivity.get(0).getPassenger() == outgoingActivity.get(0).getPassenger()) {
//				incomingActivity.get(0).setToEvent(outgoingActivity.get(0).getToEvent());
//				incomingActivity.get(0).setLowerBound(incomingActivity.get(0).getLowerBound() + outgoingActivity.get(0).getLowerBound());
//				incomingActivity.get(0).setUpperBound(incomingActivity.get(0).getUpperBound() + outgoingActivity.get(0).getUpperBound());
//				listOfEventIdexes.add(event.getIndex());
//				listOfActivityIndexes.add(outgoingActivity.get(0).getIndex());
//				listOfActivities.remove(outgoingActivity.get(0));
//				activityCounter++;
//				eventCounter++;
//			} else if (incomingActivity.size() == 1 && outgoingActivity.size() == 0) {
//				listOfActivityIndexes.add(incomingActivity.get(0).getIndex());
//				listOfActivities.remove(incomingActivity.get(0));
//				listOfEventIdexes.add(event.getIndex());
//				activityCounter++;
//				eventCounter++;
//			} else if (incomingActivity.size() == 0 && outgoingActivity.size() == 1) {
//				listOfActivityIndexes.add(outgoingActivity.get(0).getIndex());
//				listOfActivities.remove(outgoingActivity.get(0));
//				listOfEventIdexes.add(event.getIndex());
//				activityCounter++;
//				eventCounter++;
//			} else if (incomingActivity.size() == 0 && outgoingActivity.size() == 0) {
//				listOfEventIdexes.add(event.getIndex());
//				eventCounter++;
//			}
//		}
		// NodeReductionIndexMapCSV.toFile(listOfEvents, listOfActivities,
		// listOfActivityIndexes, listOfEventIdexes);
		System.out.println("Network of " + listOfEventsFull.size() + " Nodes reduced by " + activityCounter + " Activities and " + eventCounter + " Events.");
		try {
			File acitiviesPeriodicReducedFile = new File(activitiesPeriodicFile.getPath().substring(0, activitiesPeriodicFile.getPath().lastIndexOf('.')) + "Reduced.giv");
			File eventsPeriodicReducedFile = new File(eventsPeriodicFile.getPath().substring(0, eventsPeriodicFile.getPath().lastIndexOf('.')) + "Reduced.giv");
			EventsPeriodicCSV.toNodeReducedFile(eventsPeriodicReducedFile, listOfEvents, listOfEventIdexes);
			ActivitiesPeriodicCSV.toNodeReducedFile(acitiviesPeriodicReducedFile, listOfActivities);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
