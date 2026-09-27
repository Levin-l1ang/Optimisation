package net.lintim.main.tools;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.io.tools.GTFSWriter;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.Collection;

public class GTFSExporterMain {

    private static final Logger logger = new Logger(GTFSExporterMain.class);
    public static void main(String[] args) {
        logger.info("Begin reading configuration");
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().readLinks(true).build().read();
        LinePool lines = new LineReader.Builder(ptn).readCosts(false).readFrequencies(true).build().read();
        Graph<AperiodicEvent, AperiodicActivity> aperiodicEan = new AperiodicEANReader.Builder().build().read()
            .getFirstElement();
        Collection<Trip> trips = new TripReader.Builder().build().read();
        logger.info("Finished reading input data");

        logger.info("Begin writing GTFS data");
        new GTFSWriter.Builder(ptn, lines, trips, aperiodicEan).build().write();
        logger.info("Finished writing GTFS data");
    }
}
