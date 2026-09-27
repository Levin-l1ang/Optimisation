package net.lintim.io.tools;

import net.lintim.exception.OutputFileException;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GTFSWriter {

    private static final Logger logger = new Logger(GTFSWriter.class);
    private final String outputPath;
    private final String datasetName;
    private final Graph<Stop, Link> ptn;
    private final LinePool lines;
    private final ArrayList<Trip> trips;
    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;


    private GTFSWriter(Builder builder) {
        outputPath = "".equals(builder.outputPath) ? builder.config.getStringValue("gtfs_output_path") :
            builder.outputPath;
        datasetName = "".equals(builder.datasetName) ? builder.config.getStringValue("ptn_name") :
            builder.datasetName;
        ptn = builder.ptn;
        lines = builder.lines;
        trips = new ArrayList<>(builder.trips);
        this.aperiodicEan = builder.aperiodicEan;
    }

    public void write() {
        try {
            Files.createDirectories(Paths.get(outputPath));
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath).toString());
        }
        writeAgency();
        writeStops();
        writeRoutes();
        writeTrips();
        writeStopTimes();
        writeCalendar();
        createZip();
    }

    private void writeAgency() {
        logger.debug("Begin writing file agency.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(outputPath, "agency.txt").toString()));
            writer.write("agency_name,agency_url,agency_timezone\n");
            writer.write("LinTim_" + datasetName + ",https://lintim.net,Europe/Berlin\n");
            writer.close();
        } catch (IOException e){
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, "agency.txt").toString());
        }
        logger.debug("Finished writing file agency.txt");
    }

    private void writeStops() {
        logger.debug("Begin writing file stops.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(outputPath, "stops.txt").toString()));
            writer.write("stop_id,stop_code,stop_name,stop_lat,stop_lon\n");
            for (Stop stop: ptn.getNodes()) {
                writer.write(stop.getId() + "," + stop.getShortName() + "," + stop.getLongName() + "," +
                    stop.getyCoordinate() + "," + stop.getxCoordinate() + "\n");
            }
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, "stops.txt").toString());
        }
        logger.debug("Finished writing file stops.txt");
    }

    private void writeRoutes() {
        logger.debug("Begin writing file routes.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(outputPath, "routes.txt").toString()));
            writer.write("route_id,route_short_name,route_long_name,route_type\n");
            for (Line line: lines.getLineConcept()) {
                writer.write(line.getId() + "," + line.getId() + "," + line.getId() + ",0\n");
            }
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, "routes.txt").toString());
        }
        logger.debug("Finished writing file routes.txt");
    }

    private void writeTrips() {
        logger.debug("Begin writing file trips.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(outputPath, "trips.txt").toString()));
            writer.write("route_id,service_id,trip_id\n");
            for (int tripId = 0; tripId < trips.size(); tripId++) {
                writer.write(trips.get(tripId).getLineId() + ",1," + tripId + "\n");
            }
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, "trips.txt").toString());
        }
        logger.debug("Finished writing file trips.txt");
    }

    private void writeStopTimes() {
        logger.debug("Begin writing file stop_times.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(outputPath, "stop_times.txt").toString()));
            writer.write("trip_id,arrival_time,departure_time,stop_id,stop_sequence\n");
            for (int tripId = 0; tripId < trips.size(); tripId++) {
                int stopSequence = 1;
                AperiodicEvent currentEvent = aperiodicEan.getNode(trips.get(tripId).getStartAperiodicEventId());
                while (true) {
                    int arrivalTime = currentEvent.getTime();
                    int departureTime = currentEvent.getTime();
                    if (stopSequence != 1) {
                        AperiodicActivity waitActivity = aperiodicEan.getOutgoingEdges(currentEvent).stream().filter(a -> a.getType() == ActivityType.WAIT).findAny().orElse(null);
                        if (waitActivity != null) {
                            // We have an outgoing activity, get the departure time
                            currentEvent = waitActivity.getRightNode();
                            departureTime = currentEvent.getTime();
                        }
                    }
                    // Write the current stop
                    writer.write(tripId + "," + convertSecondsToTimestamp(arrivalTime) + "," + convertSecondsToTimestamp(departureTime) + "," + currentEvent.getStopId() + "," + stopSequence + "\n");
                    stopSequence += 1;
                    if (currentEvent.getType() == EventType.DEPARTURE) {
                        // There is always a next event after an departure
                        currentEvent = aperiodicEan.getOutgoingEdges(currentEvent).stream().filter(a -> a.getType() == ActivityType.DRIVE).findAny().orElseThrow(() -> new RuntimeException("Did not find outgoing drive activity for an departure event!")).getRightNode();
                    }
                    else {
                        // We did not find the next departure earlier, therefore we found the end stop
                        break;
                    }
                }
            }
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, "stop_times.txt").toString());
        }
        logger.debug("Finished writing file stop_times.txt");
    }

    private void writeCalendar() {
        logger.debug("Begin writing file calendar.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(outputPath, "calendar.txt").toString()));
            writer.write("service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n");
            writer.write("1,1,1,1,1,1,1,1,20210101,20211231");
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, "calendar.txt").toString());
        }
        logger.debug("Finished writing file calendar.txt");
    }

    private void createZip() {
        logger.debug("Begin creating zip archive");
        List<String> gtfsFiles = Arrays.asList("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt",
        "calendar.txt");
        try {
            FileOutputStream outputFile = new FileOutputStream(Paths.get(outputPath, datasetName + ".zip").toString());
            ZipOutputStream zipFile = new ZipOutputStream(outputFile);
            for (String fileName: gtfsFiles) {
                File file = new File(Paths.get(outputPath, fileName).toString());
                FileInputStream fileStream = new FileInputStream(file);
                ZipEntry zipEntry = new ZipEntry(file.getName());
                zipFile.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];
                int length;
                while ((length = fileStream.read(bytes)) >= 0) {
                    zipFile.write(bytes, 0, length);
                }
                fileStream.close();
            }
            zipFile.close();
            outputFile.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e);
            throw new OutputFileException(Paths.get(outputPath, datasetName + ".zip").toString());
        }
        logger.debug("Finished creating zip archive");
    }

    private String convertSecondsToTimestamp(int secondsAfterMidnight) {
        int hours = secondsAfterMidnight / 3600;
        int minutes = (secondsAfterMidnight % 3600) / 60;
        int seconds = secondsAfterMidnight % 60;
        return String.format("%02d", hours) + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
    }

    public static class Builder {
        private final Graph<Stop, Link> ptn;
        private final LinePool lines;
        private final Collection<Trip> trips;
        private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
        private String outputPath = "";
        private String datasetName = "";
        private Config config = Config.getDefaultConfig();


        public Builder(Graph<Stop, Link> ptn, LinePool lines, Collection<Trip> trips, Graph<AperiodicEvent, AperiodicActivity> aperiodicEan) {
            this.ptn = ptn;
            this.lines = lines;
            this.trips = trips;
            this.aperiodicEan = aperiodicEan;
        }

        public Builder setOutputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder setDatasetName(String datasetName) {
            this.datasetName = datasetName;
            return this;
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        public GTFSWriter build() {
            return new GTFSWriter(this);
        }
    }
}
