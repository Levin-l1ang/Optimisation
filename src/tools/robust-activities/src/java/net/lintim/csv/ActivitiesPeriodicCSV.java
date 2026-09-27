package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.Activity;

public class ActivitiesPeriodicCSV {
    /*
     * Reads the periodic Activities from Activities-periodic.giv
     */
    public ActivitiesPeriodicCSV() {
    }

    public static ArrayList<Activity> fromFile(File activitiesPeriodicFile) throws IOException {
        CsvReader csvReader = new CsvReader(activitiesPeriodicFile);
        List<String> line;
        ArrayList<Activity> activity = new ArrayList<Activity>();
        while ((line = csvReader.read()) != null) {
            Iterator<String> itr = line.iterator();
            int activityNO = Integer.parseInt(itr.next().trim());
            String type = itr.next().trim();
            int fromEvent = Integer.parseInt(itr.next().trim());
            int toEvent = Integer.parseInt(itr.next().trim());
            int lowerBound = Integer.parseInt(itr.next().trim());
            int upperBound = Integer.parseInt(itr.next().trim());
            int buffer = 0;
            double bufferWeight = 0.0;
            double passenger = Double.parseDouble(itr.next().trim());
            activity.add(new Activity(activityNO, type, fromEvent, toEvent, lowerBound, upperBound, buffer, bufferWeight, passenger));
        }
        csvReader.close();

        return activity;
    }

    public static void toFile(File activitiesPeriodicFile, ArrayList<Activity> listOfActivitys) throws IOException {

        FileWriter fw = new FileWriter(activitiesPeriodicFile);

        fw.write("# activity_index; type; from_event; to_event; lower_bound; upper_bound; passengers\n");

        Iterator<Activity> itr = listOfActivitys.iterator();

        while (itr.hasNext()) {
            Activity activity = itr.next();
            fw.write(activity.getIndex() + "; \"" + activity.getType() + "\"; " + activity.getFromEvent() + "; " + activity.getToEvent() + "; " + (activity.getLowerBound() + activity.getBuffer())
                + "; " + activity.getUpperBound() + "; " + activity.getPassenger() + "\n");
        }
        fw.close();
    }

}
