package net.lintim.csv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.Activity;

public class ActivitiesBufferCSV {

    public static HashMap<Activity, Double> fromFile(File activitiesBufferFile, ArrayList<Activity> listOfActivities) {
        HashMap<Activity, Double> mapOfActivitiesBuffer = new HashMap<Activity, Double>();
        try {
            CsvReader csvReader = new CsvReader(activitiesBufferFile);
            List<String> line;
            while ((line = csvReader.read()) != null) {
                Iterator<String> itr = line.iterator();
                mapOfActivitiesBuffer.put(listOfActivities.get(Integer.parseInt(itr.next())), Double.parseDouble(itr.next()));
            }
            csvReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapOfActivitiesBuffer;
    }

    public static void toFile(File activitiesBufferFile, String firstLine, ArrayList<Activity> listOfActivities) {
        try {
            FileWriter fw = new FileWriter(activitiesBufferFile);

            fw.write(firstLine + "\n");
            fw.write("# activity_index; buffer\n");

            for (Activity activity : listOfActivities) {
                fw.write(activity.getIndex() + "; " + activity.getBufferWeight() + "\n");
            }
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
