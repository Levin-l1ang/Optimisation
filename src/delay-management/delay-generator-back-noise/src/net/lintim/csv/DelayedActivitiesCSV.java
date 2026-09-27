package net.lintim.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lintim.model.PeriodicActivity;

public class DelayedActivitiesCSV {
	
	public static ArrayList<PeriodicActivity> fromFile(File activitiesDelayedFile, ArrayList<PeriodicActivity> listOfActivities) throws IOException {
		CsvReader csvReader = new CsvReader(activitiesDelayedFile);
		List<String> line;
		while ((line = csvReader.read()) != null) {
				Iterator<String> itr = line.iterator();
				Integer activity_index = Integer.parseInt(itr.next());
				Integer delay = Integer.parseInt(itr.next());
				listOfActivities.get(activity_index-1).setDelay(delay);
		}
				
		csvReader.close();
		return listOfActivities;
	}

	public static void toFile(File activitiesDelayedFile, ArrayList<PeriodicActivity> listOfActivities, boolean append_delays) throws IOException {
		FileWriter fw = new FileWriter(activitiesDelayedFile, append_delays);
		if(!append_delays) 
			fw.write("# ID; delay\n");

		for (PeriodicActivity activity : listOfActivities) {
			fw.write(activity.getIndex() + "; " +activity.getDelay() + "\n");
		}
		fw.close();
	}
}
