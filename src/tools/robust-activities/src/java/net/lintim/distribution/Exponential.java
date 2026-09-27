package net.lintim.distribution;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import net.lintim.csv.ActivitiesBufferCSV;
import net.lintim.csv.ActivitiesPeriodicCSV;
import net.lintim.model.Activity;
import net.lintim.model.Trip;

public class Exponential {

    public static void generateExponentialBuffer(String type, File activitiesPeriodicFile, File activitiesBufferFile, File activitiesRelaxFile, File activitiesBufferWeightFile, ArrayList<Activity> listOfActivities, ArrayList<Trip> listOfTrips,
                                                 double relaxUpperBound, double lambda, double averageBufferOnActivity, boolean bufferDrive, boolean bufferWait) throws IOException {
        int numberOfBufferedActivites = 0;
        double sumOfWeights = 0;
        double exponentialFaktor = 0;
        double maxWeight = 0;
        for (Trip trip : listOfTrips) {
            ArrayList<Activity> listOfActivitiesForTrip = trip.getListOfActivities();
            for (Activity activity : listOfActivitiesForTrip) {
                activity.setUpperBound((int) (activity.getUpperBound() + relaxUpperBound));
                if ((activity.getType().equals("drive") && bufferDrive) || (activity.getType().equals("wait") && bufferWait)) {
                    numberOfBufferedActivites++;
                    exponentialFaktor = (1 - Math.exp(-lambda * (listOfActivitiesForTrip.indexOf(activity) + 1))) * (listOfActivitiesForTrip.size() - listOfActivitiesForTrip.indexOf(activity) - 1);
                    activity.setBufferWeight(exponentialFaktor);
                    sumOfWeights += exponentialFaktor;
                    if (exponentialFaktor > maxWeight && type.equals("reverse-exponential")) {
                        maxWeight = exponentialFaktor;
                    }
                }
            }
        }
        ActivitiesPeriodicCSV.toFile(activitiesRelaxFile, listOfActivities);
        for (Trip trip : listOfTrips) {
            ArrayList<Activity> listOfActivitiesForTrip = trip.getListOfActivities();
            for (Activity activity : listOfActivitiesForTrip) {
                if (type.equals("exponential")) {
                    activity.setBufferWeight(activity.getBufferWeight() / sumOfWeights);
                    activity.setBuffer((int) Math.round((numberOfBufferedActivites * averageBufferOnActivity * activity.getBufferWeight())));
                } else {
                    activity.setBufferWeight((maxWeight - activity.getBufferWeight()) / (Double.valueOf(numberOfBufferedActivites) * maxWeight - sumOfWeights));
                    activity.setBuffer((int) Math.round((numberOfBufferedActivites * averageBufferOnActivity * activity.getBufferWeight())));
                }
            }
        }
        ActivitiesPeriodicCSV.toFile(activitiesBufferFile, listOfActivities);
        System.out.println("# buffer executed with the following paramters: buffer-generator: " + type + ", amount of buffer distributed: "
            + (numberOfBufferedActivites * averageBufferOnActivity) + ", lambda: " + lambda + "\n");
        ActivitiesBufferCSV.toFile(activitiesBufferWeightFile, "# buffer executed with the following paramters: buffer-generator: " + type + ", amount of buffer distributed: "
            + (numberOfBufferedActivites * averageBufferOnActivity) + ", lambda: " + lambda, listOfActivities);
    }
}
