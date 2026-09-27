package net.lintim.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import net.lintim.csv.ActivitiesPeriodicCSV;
import net.lintim.csv.ConfigurationCSV;
import net.lintim.csv.EventsPeriodicCSV;
import net.lintim.distribution.Exponential;
import net.lintim.distribution.Proportional;
import net.lintim.distribution.Random_Dist;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity;
import net.lintim.model.Configuration;
import net.lintim.model.Event;
import net.lintim.model.Trip;

public class BufferActivities {

    private static File activitiesPeriodicFile;
    private static File eventsPeriodicFile;
    private static File activitiesBufferWeightFile;
    private static File activitiesBufferFile;
    private static File activitiesRelaxFile;
    private static String bufferGenerator;
    private static Double lambda;
    private static Double relaxUpperBound;
    private static Double averageBufferOnActivity;
    private static Integer maxBufferExcRand;
    private static Long randomSeed;
    private static Boolean bufferDrive;
    private static Boolean bufferWait;

    public static void main(String args[]) throws IOException {
        File configFile = new File(args[0]);
        loadConfiguration(configFile);

        ArrayList<Activity> listOfActivities = null;
        ArrayList<Event> listOfEvents = null;
        try {
            listOfActivities = ActivitiesPeriodicCSV.fromFile(activitiesPeriodicFile);
            listOfEvents = EventsPeriodicCSV.fromFile(eventsPeriodicFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ArrayList<Trip> listOfTrips = fromActivitiesToTrips(listOfActivities, listOfEvents);
        if (bufferGenerator.equals("exponential") || bufferGenerator.equals("reverse-exponential")) {
            Exponential.generateExponentialBuffer(bufferGenerator, activitiesPeriodicFile, activitiesBufferFile, activitiesRelaxFile, activitiesBufferWeightFile, listOfActivities, listOfTrips, relaxUpperBound, lambda, averageBufferOnActivity, bufferDrive, bufferWait);
        } else if (bufferGenerator.equals("uniform-random") || bufferGenerator.equals("exceed-random")) {
            Random_Dist.generateExponentialBuffer(bufferGenerator, activitiesPeriodicFile, activitiesBufferFile, activitiesRelaxFile, activitiesBufferWeightFile, listOfActivities, relaxUpperBound, maxBufferExcRand, averageBufferOnActivity, randomSeed, bufferDrive, bufferWait);
        } else if (bufferGenerator.equals("proportional")) {
            Proportional.generateProportionalBuffer(activitiesPeriodicFile, activitiesBufferFile, activitiesRelaxFile, activitiesBufferWeightFile, listOfActivities, relaxUpperBound, averageBufferOnActivity, bufferDrive, bufferWait);
        } else {
            try {
                throw new Exception("The setting of \"buffer-generator\" is not valid.\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static ArrayList<Trip> fromActivitiesToTrips(ArrayList<Activity> listOfActivities, ArrayList<Event> listOfEvents) {
        ArrayList<Trip> listOfTrips = new ArrayList<Trip>();
        boolean hasIncomingTrainEdge;
        for (Event start : listOfEvents) {
            hasIncomingTrainEdge = false;
            for (Activity activity : start.getIncomingActivities(listOfActivities)) {
                if (activity.getType().equals("drive") || activity.getType().equals("wait")) {
                    hasIncomingTrainEdge = true;
                    break;
                }
            }
            if (hasIncomingTrainEdge) // not start event of a trip
                continue;
            listOfTrips.add(findTrip(start, listOfActivities, listOfEvents));
        }
        return listOfTrips;
    }

    private static Trip findTrip(Event start, ArrayList<Activity> listOfActivities, ArrayList<Event> listOfEvents) {
        boolean endEventFound = false;
        Trip trip = new Trip();
        Event end = start;
        while (!endEventFound) {
            endEventFound = true;
            for (Activity activity : end.getOutgoingActivities(listOfActivities)) {
                if (activity.getType().equals("drive") || activity.getType().equals("wait")) {
                    trip.addActivity(activity);
                    for (Event event : listOfEvents) {
                        if (event.getIndex() == activity.getToEvent()) {
                            end = event;
                        }
                    }
                    endEventFound = false;
                    break;
                }
            }
        }
        return trip;
    }

    private static void loadConfiguration(File configFile) {
        Configuration config = new Configuration();
        try {
            ConfigurationCSV.fromFile(config, configFile);
            activitiesPeriodicFile = new File(config.getStringValue("default_activities_periodic_file"));
            eventsPeriodicFile = new File(config.getStringValue("default_events_periodic_file"));
            activitiesBufferWeightFile = new File(config.getStringValue("default_activity_buffer_weight_file"));
            activitiesBufferFile = new File(config.getStringValue("default_activity_buffer_file"));
            activitiesRelaxFile = new File(config.getStringValue("default_activity_relax_file"));
            randomSeed = Long.parseLong(config.getStringValue("rob_buffer_seed"));
            bufferGenerator = config.getStringValue("rob_buffer_generator");
            lambda = config.getDoubleValue("rob_lambda");
            relaxUpperBound = config.getDoubleValue("rob_relax_upper_bound");
            averageBufferOnActivity = config.getDoubleValue("rob_average_buffer_on_activity");
            maxBufferExcRand = config.getIntegerValue("rob_max_puffer_exc_rand");
            bufferDrive = config.getBooleanValue("rob_buffer_drive");
            bufferWait = config.getBooleanValue("rob_buffer_wait");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (DataInconsistentException e) {
            e.printStackTrace();
        }

    }

}
