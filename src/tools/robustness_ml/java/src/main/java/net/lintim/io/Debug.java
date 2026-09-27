package net.lintim.io;

import net.lintim.algorithm.vehiclescheduling.IPModelSolver;
import net.lintim.exception.LinTimException;
import net.lintim.exception.OutputFileException;
import net.lintim.model.*;
import net.lintim.util.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class Debug {

    public static String debugIdentifier = "";

    private static final Logger logger = new Logger(Debug.class.getCanonicalName());

    public static void writeInitialRouting(List<Passenger> passengers) {
        logger.debug("Write initial routing");
        CsvWriter writer = new CsvWriter("initial_routing_java_" + debugIdentifier + ".route");
        for (Passenger passenger: passengers) {
            int startIndex = -1;
            for (int pathIndex = 0; pathIndex < passenger.getPath().size(); pathIndex++) {
                if (passenger.getPath().get(pathIndex).isStart()) {
                    startIndex = pathIndex - 1;
                    break;
                }
            }
            if (startIndex < 0) {
                throw new LinTimException("Invalid path in " + passenger + "! Has no start node!");
            }
            String eventList = passenger.getPath().stream().skip(startIndex).map(RoutingNode::getEvent).filter(Objects::nonNull).map(AperiodicEvent::getId).map(String::valueOf).collect(Collectors.joining(","));
            try {
                writer.writeLine(String.valueOf(passenger.getWeight()), String.valueOf(passenger.getStartStopId()), String.valueOf(passenger.getEndStopId()), eventList);
            } catch (IOException e) {
                throw new RuntimeException("Could not write initial routing! " + e);
            }
        }
    }

    public static PriorityQueue<Passenger> readInitialRouting(Map<Integer, RoutingNode> eventNodesById, Map<Integer, List<RoutingNode>> startNodesByStop, Map<Integer, RoutingNode> endNodesByStop) {
        logger.debug("Read initial routing");
        PriorityQueue<Passenger> passengers = new PriorityQueue<>();
        int index = 0;
        try {
            BufferedReader reader = new BufferedReader(new FileReader("initial_routing_java.route"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(";");
                int weight = Integer.parseInt(values[0]);
                int startStopId = Integer.parseInt(values[1]);
                int endStopId = Integer.parseInt(values[2]);
                String[] eventList = values[3].split(",");
                List<RoutingNode> path = Arrays.stream(values[3].split(",")).map(Integer::parseInt).map(eventNodesById::get).collect(Collectors.toList());
                int arrivalEventId = Integer.parseInt(eventList[eventList.length-1]);
                RoutingNode endNode = endNodesByStop.get(endStopId);
                RoutingNode startNode = startNodesByStop.get(startStopId).stream().filter(n -> n.getEvent().getId() == path.get(0).getEvent().getId()).findAny().orElseThrow(RuntimeException::new);
                path.add(0, startNode);
                path.add(endNode);
                Passenger passenger = new Passenger(index, weight, path, startStopId, endStopId, arrivalEventId, startNode);
                index += 1;
                passengers.add(passenger);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read initial routing! " + e);
        }
        return passengers;
    }

    public static void writeCapacitatedRouting(Passengers passengers, Map<Integer, Set<Integer>> passengersPerEvent,
                                               Map<Integer, Set<Integer>> passengersPerActivity) {
        logger.debug("Write capacitated routing");
        Collection<Passenger> passengerList = passengers.getSortedPassengersById();
        CsvWriter writer = new CsvWriter("capacitated_routing_results_java_" + debugIdentifier + ".sta");
        try {
            writer.writeLine(String.valueOf(passengerList.size()));
            for (Passenger passenger: passengerList) {
                writer.writeLine(String.valueOf(passenger.getWeight()), String.valueOf(passenger.getTravelTime()), String.valueOf(passenger.getChanges()), String.valueOf(passenger.getArrivalEventId()), String.valueOf(passenger.getStartStopId()), String.valueOf(passenger.getEndStopId()));
            }
            int numberOfUsedEvents = 0;
            for (Set<Integer> usingPassengers: passengersPerEvent.values()) {
                if (usingPassengers.size() > 0) {
                    numberOfUsedEvents += 1;
                }
            }
            writer.writeLine(String.valueOf(numberOfUsedEvents));
            for (Map.Entry<Integer, Set<Integer>> mapEntry: passengersPerEvent.entrySet()) {
                if (mapEntry.getValue().size() == 0) {
                    continue;
                }
                writer.writeLine(String.valueOf(mapEntry.getKey()), mapEntry.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
            int numberOfUsedActivities = 0;
            for (Set<Integer> usingPassengers: passengersPerActivity.values()) {
                if (usingPassengers.size() > 0) {
                    numberOfUsedActivities += 1;
                }
            }
            writer.writeLine(String.valueOf(numberOfUsedActivities));
            for (Map.Entry<Integer, Set<Integer>> mapEntry: passengersPerActivity.entrySet()) {
                if (mapEntry.getValue().size() == 0) {
                    continue;
                }
                writer.writeLine(String.valueOf(mapEntry.getKey()), mapEntry.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not write capacitated routing! " + e);
        }
    }

    public static RoutingResult readCapacitatedRouting() {
        logger.debug("Reading capacitated routing results");
        Passengers passengers;
        Map<Integer, Set<Integer>> passengersByEventId = new HashMap<>();
        Map<Integer, Set<Integer>> passengersByActivityId = new HashMap<>();
        String[] values;
        try {
            BufferedReader reader = new BufferedReader(new FileReader("capacitated_routing_results_java.route"));
            String line = reader.readLine();
            int numberOfPassengers = Integer.parseInt(line);
            passengers = new Passengers();
            for (int index = 0; index < numberOfPassengers; index++) {
                line = reader.readLine();
                values = line.split(";");
                Passenger passenger = new Passenger(index, Integer.parseInt(values[0]), null, Integer.parseInt(values[4]),
                    Integer.parseInt(values[5]), Integer.parseInt(values[3]));
                passenger.addTravelTime(Integer.parseInt(values[1]));
                passenger.setChanges(Integer.parseInt(values[2]));
                passengers.addPassenger(passenger);
            }
            int numberOfEvents = Integer.parseInt(reader.readLine());
            for (int i=0; i < numberOfEvents; i++) {
                line = reader.readLine();
                values = line.split(";");
                passengersByEventId.put(Integer.parseInt(values[0]), new HashSet<>(Arrays.stream(values[1].split(",")).map(Integer::parseInt).collect(Collectors.toSet())));
            }
            int numberOfActivities = Integer.parseInt(reader.readLine());
            for (int i = 0; i < numberOfActivities; i++) {
                line = reader.readLine();
                values = line.split(";");
                passengersByActivityId.put(Integer.parseInt(values[1]), new HashSet<>(Arrays.stream(values[1].split(",")).map(Integer::parseInt).collect(Collectors.toSet())));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read initial routing! " + e);
        }
        return new RoutingResult(passengers, passengersByEventId, passengersByActivityId);
    }

    public static String initializeSolutionFolder(Parameters parameters) {
        Instant instant = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());
        File solutionFolder = Paths.get(parameters.getSolutionFolderName(), formatter.format(instant)).toFile();
        if (!solutionFolder.mkdirs()) {
            throw new OutputFileException(solutionFolder.toString());
        }
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(
                Paths.get(solutionFolder.toString(), "values.csv").toFile()));
            writer.write("#iteration; machine learning value; average perceived traveling time\n");
            writer.close();
        } catch (IOException e) {
            throw new OutputFileException(Paths.get(solutionFolder.toString(), "values.csv").toString());
        }
        return solutionFolder.toString();
    }

    public static void appendToValuesFile(String solutionFolderName, IterationValue value) {
        String outputFile = Paths.get(solutionFolderName, "values.csv").toString();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true));
            writer.write(value.getIterationIndex() + "; " + value.getMlValue() + "; " + value.getTtValue() + "\n");
            writer.close();
        } catch (IOException e) {
            throw new OutputFileException(outputFile);
        }
    }

    public static void writeMLData(LocalSearchData data, double[] tensor, Parameters parameters) {
        writeArrayToFile(data.getAverageDwellingSlack(), parameters.getDwellingSlackFileName());
        writeArrayToFile(data.getAverageChangeSlack(), parameters.getChangeSlackFileName());
        writeArrayToFile(data.getChangeDistribution(), parameters.getChangeDistributionFileName());
        writeArrayToFile(data.getLinkLoadDistribution(), parameters.getLinkLoadDistributionFileName());
        writeArrayToFile(data.getEventDistribution(), parameters.getEventDistributionFileName());
        writeArrayToFile(data.getOdChangeDistribution(), parameters.getOdChangeDistributionFileName());
        writeArrayToFile(data.getOdTravelTimeDistribution(), parameters.getOdTravelTimeDistributionFileName());
        writeArrayToFile(data.getLineDistribution(), parameters.getLineDistributionFileName());
        writeArrayToFile(data.getTurnaroundDistribution(), parameters.getTurnaroundDistributionFileName());
        writeArrayToFile(tensor, parameters.getTensorFileName());
        MLModel model = MLModel.supplyMLModel(parameters);
        model.readModel(parameters.getModelFileName());
        double[] result = model.getResult(tensor);
        writeArrayToFile(result, parameters.getMlResultFileName());
    }

    public static void writeArrayToFile(double[] values, String filename) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(filename).toFile()));
            writer.write(String.join("; ", Arrays.stream(values).mapToObj(Double::toString).toArray(String[]::new)));
            writer.write("\n");
            writer.close();
        } catch (IOException e) {
            throw new OutputFileException(filename);
        }
    }

    public static void writeArrayTwiceToFile(double[] values, String filename) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(filename).toFile()));
            writer.write(String.join("; ", Arrays.stream(values).mapToObj(Double::toString).toArray(String[]::new)));
            writer.write("\n");
            writer.write(String.join("; ", Arrays.stream(values).mapToObj(Double::toString).toArray(String[]::new)));
            writer.write("\n");
            writer.close();
        } catch (IOException e) {
            throw new OutputFileException(filename);
        }
    }

    private static void writeArrayToFile(int[] values, String filename) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(filename).toFile()));
            writer.write(String.join(";", Arrays.stream(values).mapToObj(Integer::toString).toArray(String[]::new)));
            writer.write("\n");
            writer.close();
        } catch (IOException e) {
            throw new OutputFileException(filename);
        }
    }

    public static void outputSolution(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Parameters parameters, String folderName,
                                      int iteration, double[] ml_input, double[] result) {
        outputSolution(aperiodicEan, null, null, parameters, folderName, String.format("%04d", iteration), ml_input, result);
    }


    public static void outputSolution(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan,
                                      VehicleSchedule vs, Graph<Stop, Link> ptn, Parameters parameters,
                                      String folderName,
                                      String iteration, double[] ml_input, double[] result) {
        // First, add the turnaround edges to the aperiodic ean. We will remove them later
        if (vs != null) {
            Map<Integer, Map<Integer, Pair<Double, Double>>> distances = IPModelSolver.computeStationDistances(ptn,
                parameters.getTimeUnitsPerMinute());
            int nextActivityId = GraphHelper.getMaxEdgeId(aperiodicEan) + 1;
            AperiodicEvent source, target;
            int lowerBound;
            for (Circulation circulation : vs.getCirculations()) {
                for (VehicleTour tour : circulation.getVehicleTourList()) {
                    for (Trip trip : tour.getTripList()) {
                        if (trip.getTripType() == TripType.TRIP) {
                            continue;
                        }
                        source = aperiodicEan.getNode(trip.getStartAperiodicEventId());
                        target = aperiodicEan.getNode(trip.getEndAperiodicEventId());
                        lowerBound = (int) Math.ceil(distances.get(trip.getStartStopId()).get(trip.getEndStopId()).getSecondElement()) + parameters.getTurnoverTime();
                        if (target.getTime() - source.getTime() < lowerBound) {
                            throw new RuntimeException("Found invalid turnaround activity between " + source + " and target " + target + ", lowerbound is " + lowerBound);
                        }
                        aperiodicEan.addEdge(new AperiodicActivity(nextActivityId, -1,
                            ActivityType.TURNAROUND, source, target, lowerBound, lowerBound, 0));
                        nextActivityId += 1;
                    }
                }
            }
        }

        java.nio.file.Path[] FILES_TO_COPY = {
            Paths.get("basis","After-Config.cnf"),
            Paths.get("basis","Config.cnf"),
            Paths.get("basis","Edge.giv"),
            Paths.get("basis","Headway.giv"),
            Paths.get("basis","Load.giv"),
            Paths.get("basis","OD.giv"),
            Paths.get("basis","Pool.giv"),
            Paths.get("basis","Pool-Cost.giv"),
            Paths.get("basis","Private-Config.cnf"),
            Paths.get("basis","State-Config.cnf"),
            Paths.get("basis","Stop.giv"),
            Paths.get("line-planning", "Line-Concept.lin"),
            Paths.get("timetabling", "Activities-periodic.giv"),
            Paths.get("timetabling", "Events-periodic.giv"),
            Paths.get("timetabling", "Timetable-periodic.tim"),
            Paths.get("statistic", "line_distribution_with_frequencies.csv"),
            Paths.get("statistic", "data.tensor"),
        };
        String instanceName = "A_" + iteration;
        java.nio.file.Path instancePath = Paths.get(folderName, instanceName);
        try {
            Files.createDirectories(Paths.get(instancePath.toString(), "basis"));
            Files.createDirectory(Paths.get(instancePath.toString(), "line-planning"));
            Files.createDirectory(Paths.get(instancePath.toString(), "timetabling"));
            Files.createDirectory(Paths.get(instancePath.toString(), "statistic"));
            for (java.nio.file.Path path : FILES_TO_COPY) {
                if (Files.exists(path)) {
                    Files.copy(path, Paths.get(instancePath.toString(), path.toString()));
                }
            }
            new AperiodicEANWriter.Builder(aperiodicEan)
                .setEventFileName(Paths.get(instancePath.toString(), "delay-management", "Events-expanded.giv").toString())
                .setActivityFileName(Paths.get(instancePath.toString(), "delay-management", "Activities-expanded.giv").toString())
                .writeActivities(true).build().write();
            // Update the trips file to account for the new timetable
            BufferedReader reader = new BufferedReader(new FileReader(Paths.get("delay-management", "Trips.giv").toFile()));
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(instancePath.toString(), "delay-management", "Trips.giv").toFile()));
            String line;
            String[] values;
            int startId, endId, startTime;
            AperiodicEvent startEvent;
            AperiodicEvent endEvent;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    writer.write(line + "\n");
                    continue;
                }
                values = line.split(";");
                startId = Integer.parseInt(values[0].trim());
                endId = Integer.parseInt(values[4].trim());
                startTime = Integer.parseInt(values[3].trim());
                startEvent = aperiodicEan.getNode(startId);
                endEvent = aperiodicEan.getNode(endId);
                if (startEvent == null || endEvent == null) {
                    if (MLHelper.timeIsInRoutingInterval(parameters, startTime)) {
                        throw new RuntimeException("Could not find start event " + startEvent + "(id " + startId + ") or end event " + endEvent + "(id " + endId + ") in aperiodic ean!");
                    }
                    else {
                        // This is an empty trip outside of the routing interval where either the trip before or the
                        // trip afterwards was not present in all input timetables. Therefore it was delete from the
                        // ean and we don't need to handle it here
                        continue;
                    }
                }
                values[3] = " " + startEvent.getTime();
                values[7] = " " + endEvent.getTime();
                writer.write(String.join(";", values) + "\n");
            }
            reader.close();
            writer.close();
            reader = new BufferedReader(new FileReader(Paths.get("parameters.csv").toFile()));
            writer = new BufferedWriter(new FileWriter(Paths.get(instancePath.toString(), "parameters.csv").toFile()));
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    writer.write(line + "\n");
                    continue;
                }
                values = line.split(";");
                if (values.length != 2) {
                    throw new RuntimeException("Invalid formatted parameters file");
                }
                if (values[0].toLowerCase(Locale.ROOT).contains("evaluation_start_in_seconds")) {
                    values[1] = " " + parameters.getRoutingStartTime();
                }
                writer.write(String.join(";", values) + "\n");
            }
            reader.close();
            writer.close();
            writer = new BufferedWriter(new FileWriter(Paths.get(instancePath.toString(), "data.tensor").toFile()));
            for (int i = 0; i < ml_input.length; i++) {
                writer.write(String.valueOf(ml_input[i]));
                if (i < ml_input.length - 1) {
                    writer.write(";");
                }
            }
            writer.close();
            writer = new BufferedWriter(new FileWriter(Paths.get(instancePath.toString(), "ml_result.log").toFile()));
            for (int i = 0; i < result.length; i++) {
                writer.write(String.valueOf(result[i]));
                if (i < result.length - 1) {
                    writer.write(";");
                }
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException("Unable to write solution! " + e);
        }
        // Clean up the aperiodic ean if necessary
        if (vs != null) {
            aperiodicEan.getEdges().stream().filter(a -> a.getType() == ActivityType.TURNAROUND)
                .forEach(aperiodicEan::removeEdge);
        }
    }

    public static void writePassengerData(Passengers passengers, String identifier) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get("statistic", "perceived_times_java_" + identifier + ".sta").toFile()));
            List<Passenger> outputList = new ArrayList<>(passengers.getSortedPassengersById());
            outputList.sort(Comparator.comparingInt(Passenger::getStartStopId).thenComparingInt(Passenger::getEndStopId));
            for (Passenger passenger: outputList) {
                writer.write(passenger.getStartStopId() + "; " + passenger.getEndStopId() + "; " + passenger.getTravelTime() + "; " + passenger.getWeight() + "\n");
            }
            writer.close();
        } catch (IOException e) {
            throw new LinTimException("Unable to write perceived time: " + e.getMessage());
        }
    }

}
