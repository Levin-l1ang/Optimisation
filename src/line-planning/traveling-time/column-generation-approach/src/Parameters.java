import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private final int maxIter;
    private final double terminationValue;
    private final boolean solveIp;
    private final int weightOdEdge;
    private final int constraintType;
    private final double budget;
    private final boolean cover;
    private final int numberOfShortestPaths;
    private final boolean relaxationConstraint;
    private final boolean printPathVar;
    private final boolean addSol1;
    private final boolean addSol2;
    private final boolean addSol3;
    private final String sol1FileName;
    private final String sol2FileName;
    private final String sol3FileName;
    private final String stopFileName;
    private final String edgeFileName;
    private final String odFileName;
    private final String poolFileName;
    private final String poolCostFileName;
    private final String lineConceptFileName;

    public Parameters(Config config) {
        super(config, "lc_");
        maxIter = config.getIntegerValue("lc_traveling_time_cg_max_iterations");
        weightOdEdge = config.getIntegerValue("lc_traveling_time_cg_weight_od_edge");
        budget = config.getDoubleValue("lc_budget");
        constraintType = config.getIntegerValue("lc_traveling_time_cg_constraint_type");
        cover = config.getBooleanValue("lc_traveling_time_cg_cover");
        numberOfShortestPaths = config.getIntegerValue("lc_traveling_time_cg_k_shortest_paths");
        terminationValue = config.getDoubleValue("lc_traveling_time_cg_termination_value");
        relaxationConstraint = config.getBooleanValue("lc_traveling_time_cg_relaxation_constraint");
        printPathVar = config.getBooleanValue("lc_traveling_time_cg_print_path_variables");
        addSol1 = config.getBooleanValue("lc_traveling_time_cg_add_sol_1");
        addSol2 = config.getBooleanValue("lc_traveling_time_cg_add_sol_2");
        addSol3 = config.getBooleanValue("lc_traveling_time_cg_add_sol_3");
        sol1FileName = config.getStringValue("lc_traveling_time_cg_add_sol_1_name");
        sol2FileName = config.getStringValue("lc_traveling_time_cg_add_sol_2_name");
        sol3FileName = config.getStringValue("lc_traveling_time_cg_add_sol_3_name");
        stopFileName = config.getStringValue("default_stops_file");
        edgeFileName = config.getStringValue("default_edges_file");
        odFileName = config.getStringValue("default_od_file");
        poolFileName = config.getStringValue("default_pool_file");
        poolCostFileName = config.getStringValue("default_pool_cost_file");
        solveIp = config.getBooleanValue("lc_traveling_time_cg_solve_ip");
        lineConceptFileName = config.getStringValue("default_lines_file");
    }

    public int getMaxIter() {
        return maxIter;
    }

    public double getTerminationValue() {
        return terminationValue;
    }

    public double getWeightOdEdge() {
        return weightOdEdge;
    }

    public double getBudget() {
        return budget;
    }

    public int getConstraintType() {
        return constraintType;
    }

    public boolean cover() {
        return cover;
    }

    public boolean relaxationConstraint() {
        return relaxationConstraint;
    }

    public String getStopFileName() {
        return stopFileName;
    }

    public String getEdgeFileName() {
        return edgeFileName;
    }

    public String getOdFileName() {
        return odFileName;
    }

    public String getPoolFileName() {
        return poolFileName;
    }

    public String getPoolCostFileName() {
        return poolCostFileName;
    }

    public String getLineConceptFileName() {
        return lineConceptFileName;
    }

    public int getNumberOfShortestPaths() {
        return numberOfShortestPaths;
    }

    public boolean addSol1() {
        return addSol1;
    }

    public boolean addSol2() {
        return addSol2;
    }

    public boolean addSol3() {
        return addSol3;
    }

    public String getSol1FileName() {
        return sol1FileName;
    }

    public String getSol2FileName() {
        return sol2FileName;
    }

    public String getSol3FileName() {
        return sol3FileName;
    }

    public boolean solveIp() {
        return solveIp;
    }

    public boolean printPathVar() {
        return printPathVar;
    }
}
