import com.dashoptimization.*;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.Logger;

import java.util.*;
import java.lang.*;

public class DSLTT2 {

    private static final Logger logger = new Logger(DSLTT2.class);

    private final FiniteDominatingSet fds;
    private final CandidateEdgeSet ecs;
    private XPRBprob p;
    private final TravelingTime travelingTime;
    private PTN ptn_new;


    public DSLTT2(FiniteDominatingSet fds, CandidateEdgeSet ecs, Demand demand, int waitingTime, TravelingTime travelingTime, Distance distance) {
        this.fds = fds;
        this.ecs = ecs;
        this.travelingTime = travelingTime;
        constructXpressIPFormulation(fds, ecs, demand, waitingTime, distance);
    }

    private void constructXpressIPFormulation(FiniteDominatingSet fds, CandidateEdgeSet ecs, Demand demand, int waitingTime, Distance distance) {
        XPRS.init();
        XPRB bcl = new XPRB();
        p = bcl.newProb("Traveling Time Minimization for Stop Location 2");

        int number_candidates = 0;
        int number_vertices = 0;
        for (Candidate candidate : fds.getCandidates()) {
            number_candidates += candidate.getEdges().size();
            if (candidate.isVertex())
                number_vertices += 1;
        }

        HashMap<String, Integer> indexMap = new HashMap<>();

        int number_candidate_edges = ecs.getCandidateEdges().size();
        int number_demand = demand.getDemand_points().size();

        XPRBvar[] y = new XPRBvar[number_candidate_edges];
        XPRBvar[] x = new XPRBvar[fds.getCandidates().size()];

        XPRBctr[] ctrDemand = new XPRBctr[number_demand];
        XPRBctr[] ctrBuildingLeftNeg = new XPRBctr[number_candidates];
        XPRBctr[] ctrBuildingRightNeg = new XPRBctr[number_candidates];
        XPRBctr[] ctrVertices = new XPRBctr[number_vertices];
        XPRBctr objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        logger.debug("Create x variables");
        int candidate_it = 0;
        int vertex_it = 0;
        for (Candidate candidate : fds.getCandidates()) {
            x[candidate.getId() - 1] = p.newVar("x_" + candidate.getId(), XPRB.BV, 0.0, 1.0);
            if (candidate.isVertex()) {
                ctrVertices[vertex_it] = p.newCtr("build_vertex_" + candidate.getId());
                ctrVertices[vertex_it].setType(XPRB.E);
                ctrVertices[vertex_it].setTerm(1);
                ctrVertices[vertex_it].setTerm(x[candidate.getId() - 1], 1.0);
            }
            for (Edge edge : candidate.getEdges()) {
                indexMap.put("" + candidate.getId() + "." + edge.getIndex(), candidate_it);
                candidate_it++;
            }
        }

        logger.debug("Create y variables");
        for (CandidateEdge candidateEdge : ecs.getCandidateEdges())
            y[candidateEdge.getIndex() - 1] = p.newVar("y_" + candidateEdge.getIndex(), XPRB.PL, 0.0, 1.0);

        logger.debug("Create constraints");
        for (DemandPoint demand_point : demand.getDemand_points()) {
            ctrDemand[demand.getDemand_points().indexOf(demand_point)] = p.newCtr("cover_" + (demand.getDemand_points().indexOf(demand_point) + 1));
            ctrDemand[demand.getDemand_points().indexOf(demand_point)].setType(XPRB.G); // <=
            ctrDemand[demand.getDemand_points().indexOf(demand_point)].setTerm(1);
            for (Candidate candidate : fds.getCandidates()) {
                if (fds.getDistance().calcDist(demand_point, candidate) <= fds.getRadius() + 2 * Distance.EPSILON)
                    for (Edge edge : candidate.getEdges())
                        ctrDemand[demand.getDemand_points().indexOf(demand_point)].setTerm(x[candidate.getId() - 1], 1.0);
            }
        }
        for (Candidate candidate : fds.getCandidates()) {
            objective.setTerm(x[candidate.getId() - 1], waitingTime);
        }

        for (Candidate candidate : fds.getCandidates()) {
            if (candidate.isVertex()) {
                for (Edge edge : candidate.getEdges()) {
                    if (distance.calcDist(edge.getLeft_stop(), candidate) < Distance.EPSILON) {
                        ctrBuildingLeftNeg[indexMap.get("" + candidate.getId() + "." + edge.getIndex())] = p.newCtr("building_left_neg_" + candidate.getId() + "." + edge.getIndex());
                        ctrBuildingLeftNeg[indexMap.get("" + candidate.getId() + "." + edge.getIndex())].setType(XPRB.E);
                        ctrBuildingLeftNeg[indexMap.get("" + candidate.getId() + "." + edge.getIndex())].setTerm(x[candidate.getId() - 1], -1.0);
                    } else {
                        ctrBuildingRightNeg[indexMap.get("" + candidate.getId() + "." + edge.getIndex())] = p.newCtr("building_right_neg_" + candidate.getId() + "." + edge.getIndex());
                        ctrBuildingRightNeg[indexMap.get("" + candidate.getId() + "." + edge.getIndex())].setType(XPRB.E);
                        ctrBuildingRightNeg[indexMap.get("" + candidate.getId() + "." + edge.getIndex())].setTerm(x[candidate.getId() - 1], -1.0);
                    }
                }
            } else {
                ctrBuildingLeftNeg[indexMap.get("" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex())] = p.newCtr("building_left_neg_" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex());
                ctrBuildingRightNeg[indexMap.get("" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex())] = p.newCtr("building_right_neg" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex());
                ctrBuildingLeftNeg[indexMap.get("" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex())].setType(XPRB.E);
                ctrBuildingLeftNeg[indexMap.get("" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex())].setTerm(x[candidate.getId() - 1], -1.0);
                ctrBuildingRightNeg[indexMap.get("" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex())].setType(XPRB.E);
                ctrBuildingRightNeg[indexMap.get("" + candidate.getId() + "." + candidate.getEdges().get(0).getIndex())].setTerm(x[candidate.getId() - 1], -1.0);
            }
        }

        for (CandidateEdge candEdge : ecs.getCandidateEdges()) {
            if (candEdge.getLeft_candidate().isVertex()) {
                for (Edge edge : candEdge.getLeft_candidate().getEdges())
                    if (candEdge.getOriginal_edge() == edge) {
                        ctrBuildingLeftNeg[indexMap.get("" + candEdge.getLeft_candidate().getId() + "." + edge.getIndex())].setTerm(y[candEdge.getIndex() - 1], 1.0);
                    }
            } else {
                ctrBuildingLeftNeg[indexMap.get("" + candEdge.getLeft_candidate().getId() + "." + candEdge.getLeft_candidate().getEdges().get(0).getIndex())].setTerm(y[candEdge.getIndex() - 1], 1.0);
            }
            if (candEdge.getRight_candidate().isVertex()) {
                for (Edge edge : candEdge.getRight_candidate().getEdges())
                    if (candEdge.getOriginal_edge() == edge) {
                        ctrBuildingRightNeg[indexMap.get("" + candEdge.getRight_candidate().getId() + "." + edge.getIndex())].setTerm(y[candEdge.getIndex() - 1], 1.0);
                    }
            } else {
                ctrBuildingRightNeg[indexMap.get("" + candEdge.getRight_candidate().getId() + "." + candEdge.getRight_candidate().getEdges().get(0).getIndex())].setTerm(y[candEdge.getIndex() - 1], 1.0);
            }
            objective.setTerm(y[candEdge.getIndex() - 1], travelingTime.calcTime(candEdge.getLength()));
        }

        p.setObj(objective);
    }

    public void solve(DSLTTParameters parameters) {
        XpressHelper.setXpressSolverParameters(p, parameters);
        if (parameters.writeLpFile()) {
            try {
                p.exportProb(XPRB.LP, "basis/dsltt2.lp");
            } catch (Exception e) {
                logger.debug("Error when writing lp file: " + e.getMessage());
            }
        }
        p.setSense(XPRB.MINIM);
        p.mipOptimise();

        int status = p.getMIPStat();

        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (status == XPRB.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }

            int number_candidates = fds.getCandidates().size();
            int number_candidate_edges = ecs.getCandidateEdges().size();

            Boolean[] y_sol = new Boolean[number_candidate_edges];
            Boolean[] x_sol = new Boolean[number_candidates];
            int built_stations = 0;
            LinkedList<Stop> stops = new LinkedList<>();

            for (Candidate candidate : fds.getCandidates()) {
                x_sol[candidate.getId() - 1] = Math.round(p.getVarByName("x_" + candidate.getId()).getSol()) == 1;
                if (x_sol[candidate.getId() - 1]) {
                    Stop stop = candidate.toStop(built_stations + 1);
                    stops.add(built_stations, stop);
                    built_stations++;
                }

            }
            int built_edges = 0;
            LinkedList<Edge> edges = new LinkedList<>();
            for (CandidateEdge candidateEdge : ecs.getCandidateEdges()) {
                y_sol[candidateEdge.getIndex() - 1] = Math.round(p.getVarByName("y_" + candidateEdge.getIndex()).getSol()) == 1;
                if (y_sol[candidateEdge.getIndex() - 1]) {
                    Edge edge = candidateEdge.toEdge(built_edges + 1, stops.get(candidateEdge.getLeft_candidate().getStopIndex() - 1), stops.get(candidateEdge.getRight_candidate().getStopIndex() - 1));
                    edges.add(built_edges, edge);
                    built_edges++;
                }
            }
            this.ptn_new = new PTN(false, stops, edges);
            return;
        }
        logger.error("No feasible solution found");
        if (p.getMIPStat() == XPRB.MIP_INFEAS) {
            logger.debug("Problem is infeasible");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "basis/dsltt2.ilp", 0);
            System.exit(1);
        }
    }

    public PTN getNewPTN() {
        return ptn_new;
    }
}
