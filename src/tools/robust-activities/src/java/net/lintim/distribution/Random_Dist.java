package net.lintim.distribution;

import java.io.File;
import java.io.IOException;
import java.util.*;

import net.lintim.csv.ActivitiesBufferCSV;
import net.lintim.csv.ActivitiesPeriodicCSV;
import net.lintim.model.Activity;

public class Random_Dist {

    public static void generateExponentialBuffer(String type, File activitiesPeriodicFile, File activitiesBufferFile, File activitiesRelaxFile, File activitiesBufferWeightFile, ArrayList<Activity> listOfActivities, double relaxUpperBound,
                                                 double maxBufferExcRand, double averageBufferOnActivity, long randomSeed, boolean bufferDrive, boolean bufferWait) throws IOException {
        double weight = 0;
        double amountOfWeight = 0;
        int numberOfBufferedActivities = 0;
        double amountOfBuffer = 0;
        Random r;
        if (randomSeed == 0)
            r = new Random();
        else
            r = new Random(randomSeed);
        for (Activity activity : listOfActivities) {
            activity.setUpperBound((int) (activity.getUpperBound() + relaxUpperBound));
            if ((activity.getType().equals("drive") && bufferDrive) || (activity.getType().equals("wait") && bufferWait)) {
                weight = r.nextDouble();
                activity.setBufferWeight(weight);
                amountOfWeight += weight;
                numberOfBufferedActivities++;
            }
        }
        amountOfBuffer = numberOfBufferedActivities * averageBufferOnActivity;
        ActivitiesPeriodicCSV.toFile(activitiesRelaxFile, listOfActivities);
        for (Activity activity : listOfActivities) {
            if (type.equals("uniform-random")) {
                activity.setBufferWeight(activity.getBufferWeight() / amountOfWeight);
                activity.setBuffer((int) Math.round(numberOfBufferedActivities * averageBufferOnActivity * activity.getBufferWeight()));
            } else if (type.equals("exceed-random") && numberOfBufferedActivities > 0) {
                if (activity.getBufferWeight() * maxBufferExcRand > amountOfBuffer) {
                    activity.setBuffer((int) Math.round(amountOfBuffer));
                    amountOfBuffer = 0;
                    activity.setBufferWeight(activity.getBuffer() / (numberOfBufferedActivities * averageBufferOnActivity));
                } else {
                    activity.setBuffer((int) Math.round(activity.getBufferWeight() * maxBufferExcRand));
                    amountOfBuffer -= activity.getBufferWeight() * maxBufferExcRand;
                    activity.setBufferWeight(activity.getBuffer() / (numberOfBufferedActivities * averageBufferOnActivity));
                }
            }
        }
        ActivitiesPeriodicCSV.toFile(activitiesBufferFile, listOfActivities);
        if (type.equals("uniform-random")) {
            System.out.println("# buffer executed with the following paramters: buffer-generator: uniform-random , amount of buffer distributed: " + (numberOfBufferedActivities * averageBufferOnActivity) + "\n");
            ActivitiesBufferCSV.toFile(activitiesBufferWeightFile, "# buffer executed with the following paramters: buffer-generator: uniform-random , amount of buffer distributed: "
                + (numberOfBufferedActivities * averageBufferOnActivity), listOfActivities);
        } else {
            System.out.println("# buffer executed with the following paramters: buffer-generator: exceed-random , amount of buffer distributed: " + (numberOfBufferedActivities * averageBufferOnActivity) + ", rob_max_puffer_exc_rand: " + maxBufferExcRand + "\n");
            ActivitiesBufferCSV.toFile(activitiesBufferWeightFile, "# buffer executed with the following paramters: buffer-generator: exceed-random , amount of buffer distributed: "
                    + (numberOfBufferedActivities * averageBufferOnActivity) + ", rob_max_puffer_exc_rand: " + maxBufferExcRand,
                listOfActivities);

        }
    }
}
