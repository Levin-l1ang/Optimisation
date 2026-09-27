import com.dashoptimization.*;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.Logger;

import java.util.*;
import java.lang.*;

public class DSL {

    private static final Logger logger = new Logger(DSL.class);

    private final FiniteDominatingSet fds;
    private XPRBprob p;
    private LinkedList<Candidate> candidates;


    public DSL(FiniteDominatingSet fds, Demand demand) {
        this.fds = fds;
        constructXpressIPFormulation(fds, demand);
    }

    private void constructXpressIPFormulation(FiniteDominatingSet fds, Demand demand) {
        XPRS.init();
        XPRB bcl = new XPRB();
        p = bcl.newProb("Minimizing Number of Stops for Stop Location");

        int number_vertices = 0;
        for (Candidate candidate : fds.getCandidates()) {
            if (candidate.isVertex())
                number_vertices++;
        }
        int number_candidates = fds.getCandidates().size();
        int number_demand = demand.getDemand_points().size();

        XPRBvar[] x = new XPRBvar[number_candidates];


        XPRBctr[] ctrDemand = new XPRBctr[number_demand];
        XPRBctr[] ctrVertices = new XPRBctr[number_vertices];
        XPRBctr objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        logger.debug("Create x variables");
        int vertex_count = 0;
        for (Candidate candidate : fds.getCandidates()) {
            x[candidate.getId() - 1] = p.newVar("x_" + candidate.getId(), XPRB.BV, 0.0, 1.0);
            if (candidate.isVertex()) {
                ctrVertices[vertex_count] = p.newCtr("build_vertex_" + candidate.getId());
                ctrVertices[vertex_count].setType(XPRB.E);
                ctrVertices[vertex_count].setTerm(1);
                ctrVertices[vertex_count].setTerm(x[candidate.getId() - 1], 1.0);
                vertex_count++;
            }
        }

        logger.debug("Create constraints");
        for (DemandPoint demand_point : demand.getDemand_points()) {
            ctrDemand[demand.getDemand_points().indexOf(demand_point)] = p.newCtr("cover_" + demand.getDemand_points().indexOf(demand_point));
            ctrDemand[demand.getDemand_points().indexOf(demand_point)].setType(XPRB.G); // <=
            ctrDemand[demand.getDemand_points().indexOf(demand_point)].setTerm(1);
            for (Candidate candidate : fds.getCandidates()) {
                if (fds.getDistance().calcDist(demand_point, candidate) <= fds.getRadius() + Distance.EPSILON) {
                    ctrDemand[demand.getDemand_points().indexOf(demand_point)].setTerm(x[candidate.getId() - 1], 1.0);
                }
            }
        }
        for (Candidate candidate : fds.getCandidates()) {
            objective.setTerm(x[candidate.getId() - 1], 1.0);
        }

        p.setObj(objective);
    }

    public void solve(DSLParameters parameters) {
        XpressHelper.setXpressSolverParameters(p, parameters);
        if (parameters.writeLpFile()) {
            try {
                p.exportProb(XPRB.LP, "basis/dsl.lp");
            } catch (Exception e) {
                logger.debug("Unable to write lp file: " + e.getMessage());
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
            Boolean[] x_sol = new Boolean[number_candidates];
            this.candidates = new LinkedList<>();
            for (Candidate candidate : fds.getCandidates()) {
                x_sol[candidate.getId() - 1] = p.getVarByName("x_" + candidate.getId()).getSol() == 1.0;
                if (x_sol[candidate.getId() - 1]) {
                    this.candidates.add(candidate);
                }
            }
            return;
        }
        logger.error("No feasible solution found");
        if (p.getMIPStat() == XPRB.MIP_INFEAS) {
            logger.debug("Mip infeasible!");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "basis/dsl.ilp", 0);
        }
        System.exit(1);


    }

    public LinkedList<Candidate> getBuiltCandidates() {
        return this.candidates;
    }

}
