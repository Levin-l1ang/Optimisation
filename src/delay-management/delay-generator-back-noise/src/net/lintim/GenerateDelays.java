package net.lintim;

import net.lintim.csv.*;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class GenerateDelays {

	private static File eventFileName;
	private static File activityFileName;
	private static File eventDelaysFileName;
	private static File activityDelaysFileName;
	private static Boolean delay_events;
	private static Boolean delay_activities;
	private static Boolean append_delays;
	private static Long seed;
	private static Integer minTime;
	private static Integer maxTime;
	private static Integer minDelay;
	private static Integer maxDelay;
	private static Integer earliestTime;
	private static Integer latestTime;

	public static void main(String[] args) throws FileNotFoundException, IOException, DataInconsistentException {
		File configFile = new File(args[0]);
		readConfig(configFile);
		System.out.println("Generating delays with delay generator \"uniform_backround_noise\"!");
		ArrayList<PeriodicEvent> listOfEvents = EventsPeriodicCSV.fromFile(eventFileName);
		ArrayList<PeriodicActivity> listOfActivities = ActivitiesPeriodicCSV.fromFile(activityFileName, listOfEvents);
		
		if(append_delays){
			System.out.println("Delays are appended to already existing delays. Only events and activities that do not yet have a source delay can be delayed.");
			listOfEvents = DelayedEventsCSV.fromFile(eventDelaysFileName,listOfEvents);
			listOfActivities = DelayedActivitiesCSV.fromFile(activityDelaysFileName,listOfActivities);
		}
		
		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);
		if (delay_events) {
			listOfEvents = delayEvents(listOfEvents,r);
		}
		if (delay_activities) {
			listOfActivities = delayActivities(listOfActivities,r);
		}

	}

	private static ArrayList<PeriodicActivity> delayActivities(ArrayList<PeriodicActivity> listOfActivities, Random r) {
		System.out.println("Generating delays within [" + minDelay + "," + maxDelay + "] on all activites.");
		for (PeriodicActivity activity : listOfActivities) {
			if (activity.getFromEvent().getTime() > minTime && activity.getToEvent().getTime() < maxTime && activity.getDelay() == 0){
					activity.setDelay((int) (r.nextInt(maxDelay - minDelay+1) + minDelay));
			}
		}
		try {
			DelayedActivitiesCSV.toFile(activityDelaysFileName, listOfActivities, append_delays);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return listOfActivities;
	}

	private static ArrayList<PeriodicEvent> delayEvents(ArrayList<PeriodicEvent> listOfEvents, Random r) {
		System.out.println("Generating delays within [" + minDelay + "'" + maxDelay + "] on all events.");
		for (PeriodicEvent event : listOfEvents) {
			if (event.getTime() > minTime && event.getTime() < maxTime && event.getDelay() == 0) {
					event.setDelay((int) (r.nextInt(maxDelay - minDelay+1) + minDelay));
			}
		}
		try {
			DelayedEventsCSV.toFile(eventDelaysFileName, listOfEvents, append_delays);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return listOfEvents;
	}

	private static void readConfig(File configFile) {
		try {
			Configuration config = new Configuration();
			ConfigurationCSV.fromFile(config, configFile);
			eventFileName = new File(config.getStringValue("default_events_expanded_file"));
			activityFileName = new File(config.getStringValue("default_activities_expanded_file"));
			eventDelaysFileName = new File(config.getStringValue("default_event_delays_file"));
			activityDelaysFileName = new File(config.getStringValue("default_activity_delays_file"));
			delay_events = new Boolean(config.getStringValue("delays_events"));
			delay_activities = new Boolean(config.getStringValue("delays_activities"));
			earliestTime = config.getIntegerValue("DM_earliest_time");
			latestTime = config.getIntegerValue("DM_latest_time");
			minTime = config.getIntegerValue("delays_min_time");
			maxTime = config.getIntegerValue("delays_max_time");
			minDelay = config.getIntegerValue("delays_min_delay");
			maxDelay = config.getIntegerValue("delays_max_delay");
			append_delays = config.getBooleanValue("delays_append");
			seed = Long.parseLong(config.getStringValue("delays_seed"));
			if (earliestTime > latestTime) {
				throw new Exception("DelayGenerator: DM_latest_time must not " + "be smaller than DM_earliest_time");
			}
			if (minTime > maxTime) {
				throw new Exception("DelayGenerator: delays_max_time must not " + "be smaller than delays_min_time");
			}
			if (minTime < earliestTime || maxTime > latestTime) {
				throw new Exception("  DelayGenerator: [delays_min_time,delays_max_time]" + " must be contained in [DM_earliest_time,DM_latest_time]");
			}
			if (minDelay < 0)
				throw new Exception("DelayGenerator: delays_min_time must not be negative");
			if (minDelay > maxDelay) {
				throw new Exception("DelayGenerator: delays_max_delay must not " + "be smaller than delays_min_delay");
			}

			if (!delay_events && !delay_activities) {
				throw new DataInconsistentException("Both delays_events and delays_activities are set to false. Hence no delays are generated.");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (DataInconsistentException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
