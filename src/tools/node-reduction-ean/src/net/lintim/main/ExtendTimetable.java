package net.lintim.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import net.lintim.csv.ConfigurationCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;
import net.lintim.nodeExtension.ExtendNodes;

public class ExtendTimetable {

	private static File activitiesPeriodicFullFile;
	private static File eventsPeriodicFullFile;
	private static File activitiesPeriodicReducedFile;
	private static File eventsPeriodicReducedFile;
	private static File periodicTimetableFile;
	
	public static void main(String args[]){
		File configFile = new File(args[0]);
		loadConfig(configFile);
		ExtendNodes extendNodes = new ExtendNodes();
		startProcess("mv timetabling/Activities-periodic.giv timetabling/Activities-periodicReduced.giv");
		startProcess("mv timetabling/Events-periodic.giv timetabling/Events-periodicReduced.giv");
		startProcess("mv timetabling/Activities-periodicFull.giv timetabling/Activities-periodic.giv");
		startProcess("mv timetabling/Events-periodicFull.giv timetabling/Events-periodic.giv");
		extendNodes.loadReducedEAN(activitiesPeriodicReducedFile, eventsPeriodicReducedFile);
		extendNodes.extendNodes(activitiesPeriodicFullFile, eventsPeriodicFullFile, periodicTimetableFile);
	}
	
	private static void startProcess(String processString) {
		try {
			// Execute process
			Process process = Runtime.getRuntime().exec(processString);

			String line = null;

			// Write output of process to console
			BufferedReader inputReCo = new BufferedReader(new InputStreamReader(process.getInputStream()));
			BufferedReader inputErCo = new BufferedReader(new InputStreamReader(process.getErrorStream()));

			while ((line = inputReCo.readLine()) != null) {
				System.out.println(line);
			}
			while ((line = inputErCo.readLine()) != null) {
				System.out.println(line);
			}
			inputReCo.close();
			inputErCo.close();
			process.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void loadConfig(File configFile) {
		try {
			Configuration config = new Configuration();
			ConfigurationCSV.fromFile(config, configFile);
			activitiesPeriodicFullFile = new File(config.getStringValue("default_activities_periodic_file"));
			eventsPeriodicFullFile = new File(config.getStringValue("default_events_periodic_file"));
			periodicTimetableFile = new File(config.getStringValue("default_timetable_periodic_file"));
			activitiesPeriodicReducedFile = new File(activitiesPeriodicFullFile.getPath().substring(0, activitiesPeriodicFullFile.getPath().lastIndexOf('.')) + "Reduced.giv");
			eventsPeriodicReducedFile = new File(eventsPeriodicFullFile.getPath().substring(0, eventsPeriodicFullFile.getPath().lastIndexOf('.')) + "Reduced.giv");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (DataInconsistentException e) {
			e.printStackTrace();
		}
	}
	
}
