package net.lintim.distribution;

import net.lintim.csv.ActivitiesBufferCSV;
import net.lintim.csv.ActivitiesPeriodicCSV;
import net.lintim.model.Activity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Proportional {

    public static void generateProportionalBuffer(File activitiesPeriodicFile, File activitiesBufferFile, File activitiesRelaxFile, File activitiesBufferWeightFile, ArrayList<Activity> listOfActivities, double relaxUpperBound, double averageBufferOnActivity, boolean bufferDrive, boolean bufferWait)
        throws IOException {
        int numberOfBufferedActivities = 0;
        for (Activity activity : listOfActivities) {
            if ((activity.getType().equals("drive") && bufferDrive) || (activity.getType().equals("wait") && bufferWait)) {
                activity.setUpperBound((int) (activity.getUpperBound() + relaxUpperBound));
                numberOfBufferedActivities++;
            }
        }
        ActivitiesPeriodicCSV.toFile(activitiesRelaxFile, listOfActivities);
        for (Activity activity : listOfActivities) {
            if ((activity.getType().equals("drive") && bufferDrive) || (activity.getType().equals("wait") && bufferWait)) {
                activity.setBufferWeight(averageBufferOnActivity / numberOfBufferedActivities);
                activity.setBuffer((int) averageBufferOnActivity);
            }
        }
        ActivitiesPeriodicCSV.toFile(activitiesBufferFile, listOfActivities);
        System.out.println("# buffer executed with the following paramters: buffer-generator: proportional , amount of buffer distributed: "
            + (numberOfBufferedActivities * averageBufferOnActivity) + "\n");
        ActivitiesBufferCSV.toFile(activitiesBufferWeightFile, "# buffer executed with the following paramters: buffer-generator: proportional , amount of buffer distributed: "
            + (numberOfBufferedActivities * averageBufferOnActivity), listOfActivities);
    }
}
