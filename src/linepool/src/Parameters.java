import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public abstract class Parameters extends SolverParameters {

    private final boolean directed;
    private final double ptnSpeed;
    private final double waitingTime;
    private final double conversionFactorLength;
    private final double ratio;
    private final int maxIterations;
    private final String eanModelWeightDrive;
    private final String eanModelWeightWait;
    private final int minWaitTime;
    private final int maxWaitTime;
    private final double conversionFactorCoordinates;
    private final double costFactorFixed;
    private final double costFactorLength;
    private final double costFactorEdges;
    private final double costVehicles;
    private final int periodLength;
    private final int minTurnoverTime;
    private final int minEdges;
    private final int minDistanceLeaves;
    private final double nodeDegreeRatio;
    private final int minCoverFactor;
    private final double maxCoverFactor;
    private final String poolHeader;
    private final String poolCostHeader;
    private final String stopFileName;
    private final String edgeFileName;
    private final String loadFileName;
    private final String odFileName;
    private final String poolFileName;
    private final String poolCostFileName;

    public Parameters(Config config) {
        super(config, "lc_");
        directed = !config.getBooleanValue("ptn_is_undirected");
        ptnSpeed = config.getDoubleValue("gen_vehicle_speed");
        waitingTime = config.getDoubleValue("ptn_stop_waiting_time");
        conversionFactorLength = config.getDoubleValue("gen_conversion_length");
        ratio = config.getDoubleValue("lpool_ratio_od");
        maxIterations = config.getIntegerValue("lpool_max_iterations");
        eanModelWeightDrive = config.getStringValue("ean_model_weight_drive");
        eanModelWeightWait = config.getStringValue("ean_model_weight_wait");
        minWaitTime = config.getIntegerValue("ean_default_minimal_waiting_time");
        maxWaitTime = config.getIntegerValue("ean_default_maximal_waiting_time");
        conversionFactorCoordinates = config.getDoubleValue("gen_conversion_coordinates");
        costFactorFixed = config.getDoubleValue("lpool_costs_fixed");
        costFactorLength = config.getDoubleValue("lpool_costs_length");
        costFactorEdges = config.getDoubleValue("lpool_costs_edges");
        costVehicles = config.getDoubleValue("lpool_costs_vehicles");
        periodLength = config.getIntegerValue("period_length");
        minTurnoverTime = config.getIntegerValue("vs_turn_over_time");
        minEdges = config.getIntegerValue("lpool_min_edges");
        minDistanceLeaves = config.getIntegerValue("lpool_min_distance_leaves");
        nodeDegreeRatio = config.getDoubleValue("lpool_node_degree_ratio");
        minCoverFactor = config.getIntegerValue("lpool_min_cover_factor");
        maxCoverFactor = config.getDoubleValue("lpool_max_cover_factor");
        poolHeader = config.getStringValue("lpool_header");
        poolCostHeader = config.getStringValue("lpool_cost_header");
        stopFileName = config.getStringValue("default_stops_file");
        edgeFileName = config.getStringValue("default_edges_file");
        loadFileName = config.getStringValue("default_loads_file");
        odFileName = config.getStringValue("default_od_file");
        poolFileName = config.getStringValue("default_pool_file");
        poolCostFileName = config.getStringValue("default_pool_cost_file");
    }

    void setParametersInClasses() {
        Line.setDirected(isDirected());
        Line.setCostsFixed(getCostFactorFixed());
        Line.setCostsLength(getCostFactorLength());
        Line.setCostsEdges(getCostFactorEdges());
        Line.setCostsVehicles(getCostVehicles());
        Line.setPeriodLength(getPeriodLength());
        Line.setMinTurnaroundTime(getMinTurnoverTime());
        Line.setWaitingTimeInStation(getEanModelWeightWait(), getMinWaitTime(), getMaxWaitTime());
        LinePoolCSV.setPoolHeader(getPoolHeader());
        LinePoolCSV.setPoolCostHeader(getPoolCostHeader());
    }

    public boolean isDirected() {
        return directed;
    }

    public double getPtnSpeed() {
        return ptnSpeed;
    }

    public double getWaitingTime() {
        return waitingTime;
    }

    public double getConversionFactorLength() {
        return conversionFactorLength;
    }

    public double getRatio() {
        return ratio;
    }

    public abstract boolean shouldAddShortestPaths();

    public abstract double getRatioSp();

    public int getMaxIterations() {
        return maxIterations;
    }

    public String getEanModelWeightDrive() {
        return eanModelWeightDrive;
    }

    public String getEanModelWeightWait() {
        return eanModelWeightWait;
    }

    public int getMinWaitTime() {
        return minWaitTime;
    }

    public int getMaxWaitTime() {
        return maxWaitTime;
    }

    public double getConversionFactorCoordinates() {
        return conversionFactorCoordinates;
    }

    public double getCostFactorFixed() {
        return costFactorFixed;
    }

    public double getCostFactorLength() {
        return costFactorLength;
    }

    public double getCostFactorEdges() {
        return costFactorEdges;
    }

    public double getCostVehicles() {
        return costVehicles;
    }

    public int getPeriodLength() {
        return periodLength;
    }

    public int getMinTurnoverTime() {
        return minTurnoverTime;
    }

    public int getMinEdges() {
        return minEdges;
    }

    public int getMinDistanceLeaves() {
        return minDistanceLeaves;
    }

    public double getNodeDegreeRatio() {
        return nodeDegreeRatio;
    }

    public int getMinCoverFactor() {
        return minCoverFactor;
    }

    public double getMaxCoverFactor() {
        return maxCoverFactor;
    }

    public String getPoolHeader() {
        return poolHeader;
    }

    public String getPoolCostHeader() {
        return poolCostHeader;
    }

    public String getStopFileName() {
        return stopFileName;
    }

    public String getEdgeFileName() {
        return edgeFileName;
    }

    public String getLoadFileName() {
        return loadFileName;
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
}
