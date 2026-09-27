package net.lintim.timetabling.main;

import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.timetabling.algorithm.WalkingEvaluator;
import net.lintim.timetabling.algorithm.WalkingRouter;
import net.lintim.timetabling.util.WalkingEvaluatorParameters;
import net.lintim.timetabling.util.WalkingParameters;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;

import java.io.IOException;

public class WalkingEvaluatorMain {

    public static void main(String[] args) throws IOException {
        Logger logger = new Logger(WalkingEvaluatorMain.class.getCanonicalName());
        if (args.length == 0) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Start reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        WalkingEvaluatorParameters parameters = new WalkingEvaluatorParameters(config);
        logger.info("Finished reading configuration");
        logger.info("Start reading input data");
        Graph<InfrastructureNode, WalkingEdge> walkingGraph = new InfrastructureReader.Builder().readInfrastructureEdges(false).build().read().getSecondElement();
        Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
        OD demand = new ODReader.Builder(new MapOD()).setFileName(Config.getStringValueStatic("filename_od_nodes_file")).build().read();
        Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder().readTimetable(true).build().read().getFirstElement();
        logger.info("Finished reading input data");

        logger.info("Start evaluating the timetable with walking");
        Statistic statistic = WalkingEvaluator.evaluateTimetable(ean, demand, walkingGraph, ptn, parameters);
        logger.info("Finished evaluting the timetable with walking");
        logger.info("Start writing output data");
        new StatisticWriter.Builder().setStatistic(statistic).build().write();
        logger.info("Finished writing output data");
    }
}
