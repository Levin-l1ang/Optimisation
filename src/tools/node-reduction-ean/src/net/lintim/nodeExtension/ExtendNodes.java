package net.lintim.nodeExtension;

import net.lintim.csv.ActivitiesPeriodicCSV;
import net.lintim.csv.EventsPeriodicCSV;
import net.lintim.csv.NodeReductionIndexMapCSV;
import net.lintim.csv.TimetablePeriodicCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity;
import net.lintim.model.Event;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ExtendNodes {

	private ArrayList<Event> listOfEventsReduced;
	private ArrayList<Event> listOfEventsFull;
	private ArrayList<Activity> listOfActivitiesFull;
	private HashMap<Integer, Integer> mapOfEventIndexes;
	private HashMap<Integer, Integer> mapOfActivityIndexes;

	
	public void extendNodes(File activitiesPeriodicFile, File eventsPeriodicFile, File periodicTimetableFile) {
		try {
			loadFullEAN(activitiesPeriodicFile, eventsPeriodicFile);
			this.listOfEventsReduced = TimetablePeriodicCSV.fromFile(periodicTimetableFile, this.listOfEventsReduced);
			mapOfEventIndexes = NodeReductionIndexMapCSV.fromEventIndexFile();
			mapOfActivityIndexes = NodeReductionIndexMapCSV.fromActivityIndexFile();
			for(Activity activity: listOfActivitiesFull){
				if(mapOfActivityIndexes.get(activity.getIndex()) == 0){
			
				}
			}
			for(Event event:listOfEventsFull){
				if(mapOfEventIndexes.get(event.getIndex()) == 0){
					event.setTime(listOfEventsReduced.get(mapOfEventIndexes.get(event.getOutgoingActivities(listOfActivitiesFull).get(0).getToEvent().getIndex())-1).getTime() - event.getOutgoingActivities(listOfActivitiesFull).get(0).getLowerBound());
				}
			}
			ActivitiesPeriodicCSV.toNodeFullFile(activitiesPeriodicFile, listOfActivitiesFull);
			EventsPeriodicCSV.toNodeFullFile(eventsPeriodicFile, listOfEventsFull);
			TimetablePeriodicCSV.toFile(periodicTimetableFile, listOfEventsFull);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void loadReducedEAN(File activitiesPeriodicFile, File eventsPeriodicFile) {
		this.listOfEventsReduced = null;
		try {
			this.listOfEventsReduced = EventsPeriodicCSV.fromFile(eventsPeriodicFile);
		} catch (IOException | DataInconsistentException e) {
			e.printStackTrace();
		}
	}

	public void loadFullEAN(File activitiesPeriodicFile, File eventsPeriodicFile) {
		try {
			listOfEventsFull = EventsPeriodicCSV.fromFile(eventsPeriodicFile);
			listOfActivitiesFull = ActivitiesPeriodicCSV.fromFile(activitiesPeriodicFile, listOfEventsFull);
		} catch (IOException | DataInconsistentException e) {
			e.printStackTrace();
		}
	}

}
