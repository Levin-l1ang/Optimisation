package net.lintim.timetabling.algorithm;

import net.lintim.model.*;
import net.lintim.timetabling.model.RoutingEdge;
import net.lintim.timetabling.util.WalkingParameters;
import net.lintim.util.Logger;

import java.io.IOException;

public class WalkingRerouter extends WalkingRouter {
    private static Logger logger = new Logger(WalkingRerouter.class.getCanonicalName());

    public WalkingRerouter(Graph<PeriodicEvent, PeriodicActivity> ean, OD od, Graph<InfrastructureNode, WalkingEdge> walkingGraph, Graph<Stop, Link> ptn, WalkingParameters parameters) {
        super(ean, od, walkingGraph, ptn, parameters);
    }

    private void setEanWeights() {
        logger.debug("Start setting ean weights");
        ean.getEdges().forEach(a -> a.setNumberOfPassengers(0));
        for (RoutingEdge edge: routingGraph.getEdges()) {
            if (edge.getActivity() != null && edge.getWeight() > 0) {
                edge.getActivity().setNumberOfPassengers(edge.getActivity().getNumberOfPassengers() + edge.getWeight());
            }
        }
    }

    public static void reroutePassengers(Graph<PeriodicEvent, PeriodicActivity> ean, OD od, Graph<InfrastructureNode, WalkingEdge> walkingGraph, Graph<Stop, Link> ptn, WalkingParameters parameters, boolean extendEan) throws IOException {
        WalkingRerouter rerouter = new WalkingRerouter(ean, od, walkingGraph, ptn, parameters);
        if (extendEan) {
            rerouter.extendEan();
        }
        rerouter.buildRoutingGraph();
        rerouter.routePassengers();
        rerouter.setEanWeights();
    }
}
