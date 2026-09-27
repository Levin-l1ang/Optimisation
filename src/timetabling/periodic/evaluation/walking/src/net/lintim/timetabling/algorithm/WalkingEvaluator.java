package net.lintim.timetabling.algorithm;

import net.lintim.model.*;
import net.lintim.timetabling.model.RoutingEdge;
import net.lintim.timetabling.util.WalkingEvaluatorParameters;
import net.lintim.timetabling.util.WalkingParameters;
import net.lintim.util.Statistic;

import java.io.IOException;

public class WalkingEvaluator extends WalkingRouter {

    private final Statistic statistic;

    public WalkingEvaluator(Graph<PeriodicEvent, PeriodicActivity> ean, OD od, Graph<InfrastructureNode, WalkingEdge> walkingGraph, Graph<Stop, Link> ptn, WalkingParameters parameters) {
        super(ean, od, walkingGraph, ptn, parameters);
        this.statistic = new Statistic();
    }

    private void evaluateNetwork() {
        double numberOfPassengers = od.computeNumberOfPassengers();
        double sumWeightedDriveTime = 0;
        double sumWeightedWalkTime = 0;
        double sumWeightedChangeTime = 0;
        double sumWeightedWaitAtStartTime = 0;
        int numberOfTransfers = 0;                  // sum of the passengers using a changing activity over all changing activities
        double sumWeightedDomesticTravelTime = 0;
        double numberOfNonDomesticPassengers = numberOfPassengers;
        for (InfrastructureNode node: walkingGraph.getNodes()) {
            double domesticDemand = od.getValue(node.getId(), node.getId());
            if (domesticDemand > 0) {
                sumWeightedDomesticTravelTime += domesticDemand * 10;
                numberOfNonDomesticPassengers -= domesticDemand;
            }
        }
        for (RoutingEdge edge: routingGraph.getEdges()) {
            if (edge.getType() == RoutingEdge.EdgeType.WAIT_AT_START) {
                sumWeightedWaitAtStartTime += edge.getLength() * edge.getWeight();
            }
            else if (edge.getType() == RoutingEdge.EdgeType.WALK) {
                sumWeightedWalkTime += edge.getLength()  * edge.getWeight();
            }
            else if (edge.getType() == RoutingEdge.EdgeType.CHANGE) {
                sumWeightedChangeTime += edge.getLength() * edge.getWeight();
                numberOfTransfers += edge.getWeight();
            }
            else {
                sumWeightedDriveTime += edge.getLength() * edge.getWeight();
            }
        }
        double sumWeightedTravelTime = sumWeightedWalkTime * parameters.getWalkingUtility()
            + sumWeightedDriveTime + sumWeightedChangeTime * parameters.getChangeUtility()
            + numberOfTransfers * parameters.getChangePenalty()
            + sumWeightedWaitAtStartTime * parameters.getAdaptionUtility();
        // System.out.println("Number of passengers: " + numberOfPassengers);
        // System.out.println("Number of non-domestic passengers: " + numberOfNonDomesticPassengers);      // domestic = demand from and to the same node
        statistic.put("tim_walk_perceived_time_average", (sumWeightedDriveTime + sumWeightedChangeTime * parameters.getChangeUtility() + parameters.getChangePenalty() * numberOfTransfers) / numberOfNonDomesticPassengers);
        statistic.put("tim_walk_walking_time_average", sumWeightedWalkTime / numberOfNonDomesticPassengers);
        statistic.put("tim_walk_adaption_average", sumWeightedWaitAtStartTime / numberOfNonDomesticPassengers);
        statistic.put("tim_walk_perceived_time_average_with_walking_and_adaption", sumWeightedTravelTime / numberOfNonDomesticPassengers);
        // System.out.println("Weighted domestic travel time: " + sumWeightedDomesticTravelTime);
        // System.out.println("Including domestic: " + (sumWeightedTravelTime + sumWeightedDomesticTravelTime)/numberOfPassengers);
    }

    public static Statistic evaluateTimetable(Graph<PeriodicEvent, PeriodicActivity> ean, OD demand,
                                              Graph<InfrastructureNode, WalkingEdge> walkingGraph,
                                              Graph<Stop, Link> ptn, WalkingEvaluatorParameters parameters) throws IOException {
        WalkingEvaluator evaluator = new WalkingEvaluator(ean, demand, walkingGraph, ptn, parameters);
        if (parameters.addChanges()) {
            evaluator.extendEan();
        }
        evaluator.buildRoutingGraph();
        evaluator.routePassengers();
        evaluator.evaluateNetwork();
        return evaluator.statistic;
    }
}
