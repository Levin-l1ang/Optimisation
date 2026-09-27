package net.lintim.util;

import net.lintim.io.AperiodicEANReader;
import net.lintim.io.ConfigReader;
import net.lintim.io.VehicleScheduleReader;
import net.lintim.model.AperiodicActivity;
import net.lintim.model.AperiodicEvent;
import net.lintim.model.Graph;
import net.lintim.model.VehicleSchedule;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipHelper {

    private static int highestRoutingStartTime = -1;

    private static final Logger logger = new Logger(ZipHelper.class.getCanonicalName());

    public static int getHighestRoutingStartTime() {
        return highestRoutingStartTime;
    }

    public static Graph<AperiodicEvent, AperiodicActivity> getSpecificEanFromFile(String inputFile, int instanceNumber,
                                                                                  int solutionNumber) throws IOException {
        String eventTargetName = "delay-management/Events-expanded-zip-" + solutionNumber + ".giv";
        String activityTargetName = "delay-management/Activities-expanded-zip-" + solutionNumber + ".giv";
        String parametersTargetName = "paramters-zip.csv";
        logger.debug("Reading zip file " + inputFile);
        ZipFile startSolutionsFile = new ZipFile(inputFile);
        Enumeration<? extends ZipEntry> entries = startSolutionsFile.entries();
        ZipEntry nextEntry;
        while (entries.hasMoreElements()) {
            nextEntry = entries.nextElement();
            if (!nextEntry.getName().contains("A_" + instanceNumber + "/") && !nextEntry.isDirectory()) {
                continue;
            }
            if (nextEntry.getName().contains("Events-expanded.giv")) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(inputFile), (ClassLoader) null)) {
                    java.nio.file.Path toExtract = fileSystem.getPath(nextEntry.getName());
                    Files.copy(toExtract, Paths.get(eventTargetName), StandardCopyOption.REPLACE_EXISTING);
                }
            } else if (nextEntry.getName().contains("Activities-expanded.giv")) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(inputFile), (ClassLoader) null)) {
                    java.nio.file.Path toExtract = fileSystem.getPath(nextEntry.getName());
                    Files.copy(toExtract, Paths.get(activityTargetName), StandardCopyOption.REPLACE_EXISTING) ;
                }
            }else if (nextEntry.getName().contains("parameters.csv")) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(inputFile), (ClassLoader) null)) {
                    java.nio.file.Path toExtract = fileSystem.getPath(nextEntry.getName());
                    Files.copy(toExtract, Paths.get(parametersTargetName), StandardCopyOption.REPLACE_EXISTING) ;
                }
            }
        }
        Graph<AperiodicEvent, AperiodicActivity> result = new AperiodicEANReader.Builder()
            .setEventFileName(eventTargetName)
            .setActivityFileName(activityTargetName)
            .build().read().getFirstElement();
        Config config = new ConfigReader.Builder(parametersTargetName).setConfig(new Config()).build().read();
        highestRoutingStartTime = Math.max(highestRoutingStartTime, config.getIntegerValue("evaluation_start_in_seconds"));
//        Files.delete(Paths.get(eventTargetName));
//        Files.delete(Paths.get(activityTargetName));
        return result;
    }

    public static VehicleSchedule getSpecificVehicleScheduleFromFile(String inputFile, int instanceNumber)
        throws IOException {
        String vsTargetName = "vehicle-scheduling/Vehicle_Schedules-zip.vs";
        logger.debug("Reading zip file " + inputFile);
        ZipFile startSolutionsFile = new ZipFile(inputFile);
        Enumeration<? extends ZipEntry> entries = startSolutionsFile.entries();
        ZipEntry nextEntry;
        while (entries.hasMoreElements()) {
            nextEntry = entries.nextElement();
            if (!nextEntry.getName().contains("A_" + instanceNumber) && !nextEntry.isDirectory()) {
                continue;
            }
            if (nextEntry.getName().contains("Vehicle_Schedules.vs")) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(inputFile), (ClassLoader) null)) {
                    java.nio.file.Path toExtract = fileSystem.getPath(nextEntry.getName());
                    Files.copy(toExtract, Paths.get(vsTargetName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        VehicleSchedule vs = new VehicleScheduleReader.Builder().setFileName(vsTargetName).build().read();
//        Files.delete(Paths.get(vsTargetName));
        return vs;
    }

    public static void copySpecificTripFile(String inputFile, int instanceNumber)
        throws IOException {
        String tripTargetName = "delay-management/Trips.giv";
        logger.debug("Reading zip file " + inputFile);
        ZipFile startSolutionsFile = new ZipFile(inputFile);
        Enumeration<? extends ZipEntry> entries = startSolutionsFile.entries();
        ZipEntry nextEntry;
        while (entries.hasMoreElements()) {
            nextEntry = entries.nextElement();
            if (!nextEntry.getName().contains("A_" + instanceNumber) && !nextEntry.isDirectory()) {
                continue;
            }
            if (nextEntry.getName().contains("Trips.giv")) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(inputFile), (ClassLoader) null)) {
                    java.nio.file.Path toExtract = fileSystem.getPath(nextEntry.getName());
                    Files.copy(toExtract, Paths.get(tripTargetName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static List<Graph<AperiodicEvent, AperiodicActivity>> getRandomEans(String inputFile, int numberOfInstances,
                                                                               int randomSeed) throws IOException {
        logger.debug("Reading zip file " + inputFile);
        ZipFile startSolutionsFile = new ZipFile(inputFile);
        Enumeration<? extends ZipEntry> entries = startSolutionsFile.entries();
        int directories = 0;
        ZipEntry nextEntry;
        ArrayList<Integer> directoryNames = new ArrayList<>();
        while (entries.hasMoreElements()) {
            nextEntry = entries.nextElement();
            if (nextEntry.isDirectory() && nextEntry.getName().chars().filter(ch -> ch == '/').count() == 2) {
                directories += 1;
                directoryNames.add(Integer.parseInt(nextEntry.getName().split("/")[1].split("_")[1]));
            }
        }
        if (directories < numberOfInstances) {
            throw new RuntimeException("Did not find enough possible start solutions! Got " + directories + ", need " + numberOfInstances);
        }
        // Now we can choose the solutions
        Set<Integer> chosenSolutions = new HashSet<>();
        Random random;
        if (randomSeed >= 0) {
            random = new Random(randomSeed);
        }
        else {
            random = new Random();
        }
        while (chosenSolutions.size() < numberOfInstances) {
            chosenSolutions.add(directoryNames.get(random.nextInt(directoryNames.size())));
        }
        logger.debug("Chose solutions: " + chosenSolutions);
        ArrayList<Integer> solutionList = new ArrayList<>(chosenSolutions);
        return getSpecificEansFromFile(inputFile, solutionList);
    }

    public static List<Graph<AperiodicEvent, AperiodicActivity>> getSpecificEansFromFile(String inputFile,
                                                                                         List<Integer> solutionList) throws IOException {
        ArrayList<Graph<AperiodicEvent, AperiodicActivity>> result = new ArrayList<>();
        for (int index = 0; index < solutionList.size(); index++) {
            result.add(getSpecificEanFromFile(inputFile, solutionList.get(index), index));
        }
        return result;
    }

}
