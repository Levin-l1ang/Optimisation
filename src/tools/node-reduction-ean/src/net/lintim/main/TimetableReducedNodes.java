package net.lintim.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import net.lintim.csv.ConfigurationCSV;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;
import net.lintim.nodeReduction.ReduceNodes;

public class TimetableReducedNodes {

	private static File activitiesPeriodicFile;
	private static File eventsPeriodicFile;

	public static void main(String args[]) {
		File configFile = new File(args[0]);
		loadConfig(configFile);
		ReduceNodes reduceNodes = new ReduceNodes();
		reduceNodes.reduceNodes(activitiesPeriodicFile, eventsPeriodicFile);
		startProcess("mv timetabling/Activities-periodic.giv timetabling/Activities-periodicFull.giv");
		startProcess("mv timetabling/Events-periodic.giv timetabling/Events-periodicFull.giv");
		startProcess("mv timetabling/Activities-periodicReduced.giv timetabling/Activities-periodic.giv");
		startProcess("mv timetabling/Events-periodicReduced.giv timetabling/Events-periodic.giv");
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
			activitiesPeriodicFile = new File(config.getStringValue("default_activities_periodic_file"));
			eventsPeriodicFile = new File(config.getStringValue("default_events_periodic_file"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (DataInconsistentException e) {
			e.printStackTrace();
		}
	}
}
