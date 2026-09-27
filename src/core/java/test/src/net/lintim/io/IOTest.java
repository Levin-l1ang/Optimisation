package net.lintim.io;

import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Pair;
import net.lintim.util.Statistic;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 */
public class IOTest {
    private static final Path inputPath = Paths.get("test", "resources", "dataset");
    private static final Path outputPath = inputPath.getParent().getParent().resolve("output");
    private static final Path basePath = inputPath.resolve("basis");

    private static final double DELTA = 1e-15;

    private static Config config;

    private static boolean compareFiles(Path expected, Path found) {
        try {
            BufferedReader reader1 = new BufferedReader(new FileReader(expected.toFile()));
            BufferedReader reader2 = new BufferedReader(new FileReader(found.toFile()));
            String line1 = reader1.readLine();
            String line2 = reader2.readLine();
            try {
                while (line1 != null || line2 != null) {
                    // First check if both lines are present
                    if (line1 == null || line2 == null) {
                        return false;
                    }
                    if (!line1.equalsIgnoreCase(line2)) {
                        System.out.println("Found inequal lines for " + expected + " and " + found + ":");
                        System.out.println("Expected: " + line1);
                        System.out.println("Found:    " + line2);
                        return false;
                    }
                    line1 = reader1.readLine();
                    line2 = reader2.readLine();
                }
            }
            finally {
                reader1.close();
                reader2.close();
            }
        } catch (IOException e) {
            // If there was some IO exception the files are not equal!
            System.out.println("IO exception!");
            System.out.println(e);
            return false;
        }
        // The comparison was succesfull
        return true;
    }

    @BeforeClass
    public static void readConfig() {
        Path configPath = basePath.resolve("Config.cnf");
        config = new Config();
        new ConfigReader.Builder(configPath.toString()).setConfig(config).build().read();
    }

    @Before
    public void createOutputFolder() {
        assertTrue(outputPath.toFile().mkdirs());
    }

    @After
    public void removeOutputFolder() {
        // We make some assumptions here, the output folder should never have any subfolders. If this is not the
        // case, we need to adjust this method
        File toDelete = outputPath.toFile();
        assertTrue(toDelete.exists() && toDelete.isDirectory());
        String[] files = toDelete.list();
        for (String file: files) {
            assertTrue(outputPath.resolve(file).toFile().delete());
        }
        assertTrue(toDelete.delete());
    }

    @Test
    public void canReadConfig() {
        Path configPath = basePath.resolve("Config.cnf");
        Config config = new Config();
        new ConfigReader.Builder(configPath.toString()).setConfig(config).build().read();
        assertEquals(448, config.getData().size());
    }

    @Test
    public void canReadAndWritePtn() {
        Path stopPath = inputPath.resolve(config.getStringValue("default_stops_file"));
        Path linkPath = inputPath.resolve(config.getStringValue("default_edges_file"));
        Path loadPath = inputPath.resolve(config.getStringValue("default_loads_file"));
        Path headwayPath = inputPath.resolve(config.getStringValue("default_headways_file"));
        Path geoCoordinatesPath = inputPath.resolve(config.getStringValue("default_stops_coordinates_file"));
        Graph<Stop, Link> ptn= new PTNReader.Builder().readLoads(true).readHeadways(true).readGeoCoordinates(true)
            .setStopFileName(stopPath.toString()).setLinkFileName(linkPath.toString())
            .setLoadFileName(loadPath.toString()).setHeadwayFileName(headwayPath.toString())
            .setGeoCoordinateFileName(geoCoordinatesPath.toString()).setPtnIsDirected(false)
            .setConfig(config).build().read();
        assertEquals(8, ptn.getNodes().size());
        assertEquals(8, ptn.getEdges().size());
        assertEquals(3, ptn.getEdge(1).getLowerFrequencyBound());
        assertEquals(5, ptn.getEdge(2).getHeadway());
        assertEquals(3.5, ptn.getNode(3).getLatitude(), 0.01);
        Path outputStopPath = outputPath.resolve("Stop.giv");
        Path outputLinkPath = outputPath.resolve("Edge.giv");
        Path outputLoadPath = outputPath.resolve("Load.giv");
        Path outputHeadwayPath = outputPath.resolve("Headway.giv");
        new PTNWriter.Builder(ptn).writeLoads(true).writeHeadways(true).setStopFileName(outputStopPath.toString())
            .setLinkFileName(outputLinkPath.toString()).setLoadFileName(outputLoadPath.toString()).setHeadwayFileName
            (outputHeadwayPath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(stopPath, outputStopPath));
        assertTrue(compareFiles(linkPath, outputLinkPath));
        assertTrue(compareFiles(loadPath, outputLoadPath));
        assertTrue(compareFiles(headwayPath, outputHeadwayPath));
    }

    @Test
    public void canReadAdditionalLoad() {
        Path loadPath = inputPath.resolve(config.getStringValue("filename_additional_load_file"));
        Map<Integer, Map<Pair<Integer, Integer>, Double>> loads = new AdditionalLoadReader.Builder()
            .setFileName(loadPath.toString())
            .build().read();
        assertEquals(2, loads.size());
        assertEquals(3, loads.get(5).get(new Pair<>(5, 6)), DELTA);
    }

    @Test
    public void canReadStationLimits() {
        Path limitsPath = inputPath.resolve(config.getStringValue("filename_station_limit_file"));
        Map<Integer, StationLimit> limits = new StationLimitReader.Builder()
            .setFileName(limitsPath.toString())
            .build().read();
        assertEquals(2, limits.size());
        assertEquals(4, limits.get(1).getMinWaitTime());
        assertEquals(1, limits.get(6).getMinChangeTime());
    }

    @Test
    public void canReadAndWriteOD() {
        Path odPath = inputPath.resolve(config.getStringValue("default_od_file"));
        Path stopPath = inputPath.resolve(config.getStringValue("default_stops_file"));
        Graph<Stop, Link> ptn = new PTNReader.Builder().readLinks(false).setConversionFactorCoordinates(1.)
            .setPtnIsDirected(false).setStopFileName(stopPath.toString()).build().read();
        int numberOfStops = ptn.getNodes().size();
        OD od = new ODReader.Builder(numberOfStops).setFileName(odPath.toString()).build().read();
        assertEquals(2622, od.computeNumberOfPassengers(), DELTA);
        assertEquals(46, od.getODPairs().size());
        assertEquals(10, od.getValue(1, 2), DELTA);
        Path outputOdPath = outputPath.resolve("OD.giv");
        new ODWriter.Builder(od, ptn).setFileName(outputOdPath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(odPath, outputOdPath));
    }

    @Test
    public void canReadAndWriteLinePool() {
        Path stopPath = inputPath.resolve(config.getStringValue("default_stops_file"));
        Path linkPath = inputPath.resolve(config.getStringValue("default_edges_file"));
        Graph<Stop, Link> ptn= new PTNReader.Builder().setStopFileName(stopPath
            .toString()).setLinkFileName(linkPath.toString()).setPtnIsDirected(false).setConversionFactorCoordinates(1.)
            .setConversionFactorLength(1.).build().read();
        Path poolPath = inputPath.resolve(config.getStringValue("default_pool_file"));
        Path poolCostPath = inputPath.resolve(config.getStringValue("default_pool_cost_file"));
        LinePool pool = new LineReader.Builder(ptn).readFrequencies(false).setLineFileName(poolPath.toString())
            .setLineCostFileName(poolCostPath.toString()).setConversionFactorLength(2.).build().read();
        assertEquals(8, pool.getLines().size());
        assertEquals(ptn.getEdge(6), pool.getLine(1).getLinePath().getEdges().get(1));
        assertEquals(3.6, pool.getLine(4).getLength(), DELTA);
        assertEquals(0, pool.getLine(1).getFrequency());
        assertEquals(3.8, pool.getLine(7).getCost(), DELTA);
        Path outputPoolPath = outputPath.resolve("Pool.giv");
        Path outputPoolCostPath = outputPath.resolve("Pool-Cost.giv");
        new LineWriter.Builder(pool).writeLineConcept(false).writePool(true).writeCosts(true).setPoolFileName
            (outputPoolPath.toString()).setPoolCostFileName(outputPoolCostPath.toString()).setConversionFactorLength(2.).setConfig(config).build()
            .write();
        assertTrue(compareFiles(poolPath, outputPoolPath));
        assertTrue(compareFiles(poolCostPath, outputPoolCostPath));
    }

    @Test
    public void canReadAndWriteLineConcept() {
        Path stopPath = inputPath.resolve(config.getStringValue("default_stops_file"));
        Path linkPath = inputPath.resolve(config.getStringValue("default_edges_file"));
        Graph<Stop, Link> ptn= new PTNReader.Builder().setStopFileName(stopPath
            .toString()).setLinkFileName(linkPath.toString()).setPtnIsDirected(false).setConversionFactorCoordinates(1.)
            .setConversionFactorLength(1.).build().read();
        Path lcPath = inputPath.resolve(config.getStringValue("default_lines_file"));
        LinePool lc = new LineReader.Builder(ptn).readFrequencies(true).readCosts(false).setLineFileName(lcPath
            .toString()).setConversionFactorLength(1.).build().read();
        assertEquals(4, lc.getLine(2).getFrequency());
        assertEquals(4, lc.getLineConcept().size());
        Path outputLcPath = outputPath.resolve("Line-Concept.lin");
        new LineWriter.Builder(lc).setConceptFileName(outputLcPath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(lcPath, outputLcPath));
    }

    @Test
    public void canReadAndWritePeriodicEan() {
        Path eventPath = inputPath.resolve(config.getStringValue("default_events_periodic_file"));
        Path activityPath = inputPath.resolve(config.getStringValue("default_activities_periodic_file"));
        Path timetablePath = inputPath.resolve(config.getStringValue("default_timetable_periodic_file"));
        Pair<Graph<PeriodicEvent, PeriodicActivity>, PeriodicTimetable<PeriodicEvent>> pair = new PeriodicEANReader
            .Builder().readTimetable(true).setEventFileName(eventPath.toString()).setActivityFileName(activityPath
            .toString()).setTimetableFileName(timetablePath.toString()).setTimeUnitsPerMinute(1).setPeriodLength(60)
            .build().read();
        Graph<PeriodicEvent, PeriodicActivity> ean = pair.getFirstElement();
        PeriodicTimetable<PeriodicEvent> timetable = pair.getSecondElement();
        assertEquals(168, ean.getNodes().size());
        assertEquals(EventType.ARRIVAL, ean.getNode(2).getType());
        assertEquals(LineDirection.FORWARDS, ean.getNode(4).getDirection());
        assertEquals(7, ean.getNode(5).getTime());
        assertEquals(168, timetable.size());
        assertEquals(50, (long) timetable.get(ean.getNode(21)));
        assertEquals(60, timetable.getPeriod());
        assertEquals(858, ean.getEdges().size());
        assertEquals(ActivityType.CHANGE, ean.getEdge(850).getType());
        Path outputEventPath = outputPath.resolve("Events-periodic.giv");
        Path outputActivityPath = outputPath.resolve("Activity-periodic.giv");
        Path outputTimetablePath = outputPath.resolve("Timetable-periodic.giv");
        new PeriodicEANWriter.Builder(ean).setEventFileName(outputEventPath.toString()).setActivityFileName
            (outputActivityPath.toString()).setTimetableFileName(outputTimetablePath.toString()).setConfig(config)
            .build().write();
        assertTrue(compareFiles(eventPath, outputEventPath));
        assertTrue(compareFiles(activityPath, outputActivityPath));
        assertTrue(compareFiles(timetablePath, outputTimetablePath));
    }

    @Test
    public void canReadAndWriteAperiodicEan() {
        Path eventPath = inputPath.resolve(config.getStringValue("default_events_expanded_file"));
        Path activityPath = inputPath.resolve(config.getStringValue("default_activities_expanded_file"));
        Path timetablePath = inputPath.resolve(config.getStringValue("default_disposition_timetable_file"));
        Pair<Graph<AperiodicEvent, AperiodicActivity>, Timetable<AperiodicEvent>> pair = new AperiodicEANReader
            .Builder().setEventFileName(eventPath.toString()).setActivityFileName(activityPath
            .toString()).setTimeUnitsPerMinute(1).build().read();
        Graph<AperiodicEvent, AperiodicActivity> ean = pair.getFirstElement();
        Timetable<AperiodicEvent> timetable = pair.getSecondElement();
        assertEquals(652, ean.getNodes().size());
        assertEquals(EventType.DEPARTURE, ean.getNode(2).getType());
        assertEquals(42600, ean.getNode(84).getTime());
        assertEquals(652, timetable.size());
        assertEquals(31860, (long) timetable.get(ean.getNode(85)));
        assertEquals(578, ean.getEdges().size());
        assertEquals(ActivityType.CHANGE, ean.getEdge(576).getType());
        Path outputEventPath = outputPath.resolve("Events-expanded.giv");
        Path outputActivityPath = outputPath.resolve("Activity-expanded.giv");
        new AperiodicEANWriter.Builder(ean).writeActivities(true).setEventFileName(outputEventPath.toString())
            .setActivityFileName(outputActivityPath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(eventPath, outputEventPath));
        assertTrue(compareFiles(activityPath, outputActivityPath));
        pair = new AperiodicEANReader.Builder().setEventFileName(eventPath.toString()).setActivityFileName(activityPath
            .toString()).setTimeUnitsPerMinute(1).readDispositionTimetable(true).setTimetableFileName(timetablePath
            .toString()).build().read();
        ean = pair.getFirstElement();
        timetable = pair.getSecondElement();
        assertEquals(652, ean.getNodes().size());
        assertEquals(EventType.DEPARTURE, ean.getNode(2).getType());
        assertEquals(43345, ean.getNode(84).getTime());
        assertEquals(652, timetable.size());
        assertEquals(32460, (long) timetable.get(ean.getNode(85)));
        assertEquals(578, ean.getEdges().size());
        Path outputTimetablePath = outputPath.resolve("Timetable-disposition.tim");
        new AperiodicEANWriter.Builder(ean).writeEvents(false).writeDispositionTimetable(true).setTimetable
            (timetable).setTimetableFileName(outputTimetablePath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(timetablePath, outputTimetablePath));
    }

    @Test
    public void canReadAndWriteStatistic() {
        Path statisticPath = inputPath.resolve(config.getStringValue("default_statistic_file"));
        Statistic statistic = new Statistic();
        new StatisticReader.Builder().setFileName(statisticPath.toString()).setStatistic(statistic).build().read();
        assertEquals(10, statistic.getData().size());
        assertEquals(6.87, statistic.getDoubleValue("tim_time_average"), DELTA);
        Path outputStatisticPath = outputPath.resolve("statistic.sta");
        new StatisticWriter.Builder().setStatistic(statistic).setFileName(outputStatisticPath.toString()).build().write();
        assertTrue(compareFiles(statisticPath, outputStatisticPath));
    }

    @Test
    public void canReadAndWriteTrips() {
        Path tripPath = inputPath.resolve(config.getStringValue("default_trips_file"));
        ArrayList<Trip> trips = new ArrayList<>(new TripReader.Builder().setFileName(tripPath.toString()).build()
            .read());
        assertEquals(110, trips.size());
        assertEquals(36060, trips.get(2).getStartTime());
        Path outputTripPath = outputPath.resolve("Trips.giv");
        new TripWriter.Builder(trips).setFileName(outputTripPath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(tripPath, outputTripPath));
    }

    @Test
    public void canReadAndWriteVehicleSchedule() {
        Path vsPath = inputPath.resolve(config.getStringValue("default_vehicle_schedule_file"));
        VehicleSchedule vs = new VehicleScheduleReader.Builder().setFileName(vsPath.toString()).build().read();
        assertEquals(6, vs.getCirculations().size());
        assertEquals(1, vs.getCirculation(2).getVehicleTourList().size());
        assertEquals(32, vs.getCirculation(1).getVehicleTour(1).getTripList().size());
        assertEquals(TripType.TRIP, vs.getCirculation(2).getVehicleTour(2).getTrip(51).getTripType());
        Path outputVsPath = outputPath.resolve("Vehicle-Schedules.vs");
        new VehicleScheduleWriter.Builder(vs).setFileName(outputVsPath.toString()).setConfig(config).build().write();
        assertTrue(compareFiles(vsPath, outputVsPath));
    }
}
