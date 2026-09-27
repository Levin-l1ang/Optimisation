package net.lintim.algorithm.timetabling;

import net.lintim.evaluation.timetabling.RoutingEvaluator;
import net.lintim.io.ConfigReader;
import net.lintim.io.ODReader;
import net.lintim.io.PTNReader;
import net.lintim.io.PeriodicEANReader;
import net.lintim.model.*;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Statistic;
import net.lintim.util.timetabling.RoutingNetworkBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class CapacitatedRoutingGurobiTest {

    private static final double DELTA = 1e-15;

    @Test
    public void enrichEANAndRoutePassengers(){
        new ConfigReader.Builder("test/dataset/Config.cnf").build().read();
        Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder()
            .setEventFileName("test/dataset/Events.giv")
            .setActivityFileName("test/dataset/Activities.giv")
            .setTimetableFileName("test/dataset/Timetable.tim").readTimetable(true).build().read()
            .getFirstElement();
        Graph<Stop, Link> ptn = new PTNReader.Builder()
            .setPtnIsDirected(false)
            .setStopFileName("test/dataset/Stop.giv")
            .setLinkFileName("test/dataset/Edge.giv").build().read();
        OD od = new ODReader.Builder(ptn.getNodes().size())
            .setFileName("test/dataset/OD.giv").build().read();
            RoutingNetworkBuilder.enrichEan(ean, ptn);
        Config config = new Config();
        config.put("tim_solver", "GUROBI");
        config.put("tim_threads", -1);
        config.put("tim_timelimit", -1);
        config.put("tim_mip_gap", -1.0);
        config.put("tim_write_lp_file", false);
        config.put("console_log_level", "INFO");
        SolverParameters solverParameters = new SolverParameters(config, "tim_");
        CapacitatedRoutingGurobi.routePassengers(ean, ptn, od, Config.getDefaultConfig(), solverParameters);
        assertEquals(2, ean.getEdge(7).getNumberOfPassengers(), DELTA);
        assertEquals(2, ean.getEdge(10).getNumberOfPassengers(), DELTA);
    }
    
    @Test
    public void evaluateTravelTime(){
        new ConfigReader.Builder("test/dataset/Config.cnf").build().read();
        Graph<PeriodicEvent, PeriodicActivity> ean = new PeriodicEANReader.Builder()
        .setEventFileName("test/dataset/Events.giv")
        .setActivityFileName("test/dataset/Activities.giv")
        .setTimetableFileName("test/dataset/Timetable.tim").readTimetable(true).build().read()
        .getFirstElement();
        Graph<Stop, Link> ptn = new PTNReader.Builder()
        .setPtnIsDirected(false)
        .setStopFileName("test/dataset/Stop.giv")
        .setLinkFileName("test/dataset/Edge.giv").build().read();
        OD od = new ODReader.Builder(ptn.getNodes().size())
        .setFileName("test/dataset/OD.giv").build().read();
        RoutingNetworkBuilder.enrichEan(ean, ptn);
        Config config = new Config();
        config.put("tim_solver", "GUROBI");
        config.put("tim_threads", -1);
        config.put("tim_timelimit", -1);
        config.put("tim_mip_gap", -1.0);
        config.put("tim_write_lp_file", false);
        config.put("console_log_level", "INFO");
        SolverParameters solverParameters = new SolverParameters(config, "tim_");
        CapacitatedRoutingGurobi.routePassengers(ean, ptn, od, Config.getDefaultConfig(), solverParameters);
        Statistic statistic = new Statistic();
        RoutingEvaluator.evaluateRouting(ean, statistic, Config.getDefaultConfig(), od.computeNumberOfPassengers());
        assertEquals(44, statistic.getDoubleValue("tim_capacitated_perceived_travel_time"), DELTA);
    }


}