package net.lintim.timetabling.main;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.timetabling.algorithm.WalkingEvaluator;
import net.lintim.timetabling.algorithm.WalkingRerouter;
import net.lintim.timetabling.util.WalkingParameters;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;

import java.io.IOException;

public class WalkingRerouterMain {
    public static void main(String[] args) throws IOException {
        Logger logger = new Logger(WalkingEvaluatorMain.class.getCanonicalName());
        boolean extendEan = false;
        if (args.length == 0) {
            throw new ConfigNoFileNameGivenException();
        }
        else if (args.length == 2) {
            extendEan = args[1].toLowerCase().equals("true");
        }
        logger.info("Start reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        WalkingParameters parameters = new WalkingParameters(config);
        logger.info("Finished reading configuration");
        logger.info("Start reading input data");
        Graph<InfrastructureNode, WalkingEdge> walkingGraph = new InfrastructureReader.Builder().readInfrastructureEdges(false).build().read().getSecondElement();
        Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
        OD demand = new ODReader.Builder(new MapOD()).setFileName(Config.getStringValueStatic("filename_od_nodes_file")).build().read();
        Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder().readTimetable(true).build().read().getFirstElement();
        logger.info("Finished reading input data");

        logger.info("Start evaluating the timetable with walking");
        WalkingRerouter.reroutePassengers(ean, demand, walkingGraph, ptn, parameters, extendEan);
        logger.info("Finished evaluting the timetable with walking");
        logger.info("Start writing output data");
        new PeriodicEANWriter.Builder(ean).writeEvents(false).writeTimetable(false).build().write();
        logger.info("Finished writing output data");
    }
}
