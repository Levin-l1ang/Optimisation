import net.lintim.util.Config;

public class ParametersTree extends Parameters {
    private final boolean addShortestPaths;
    private final double ratioSp;
    private final boolean restrictTerminals;
    private final boolean appendSingleEdges;
    private final boolean restrictForbiddenEdges;
    private final boolean restrictTurns;

    public ParametersTree(Config config) {
        super(config);
        addShortestPaths = config.getBooleanValue("lpool_add_shortest_paths");
        if(addShortestPaths){
            ratioSp = config.getDoubleValue("lpool_ratio_shortest_paths");
        }
        else{
            ratioSp=0;
        }
        restrictTerminals = config.getBooleanValue("lpool_restrict_terminals");
        appendSingleEdges = config.getBooleanValue("lpool_append_single_edges");
        restrictForbiddenEdges = config.getBooleanValue("lpool_restrict_forbidden_edges");
        restrictTurns = config.getBooleanValue("lpool_restrict_turns");
    }

    public void setParametersInClasses() {
        super.setParametersInClasses();
        Stop.setCoordinateFactorConversion(getConversionFactorCoordinates());
        Line.setMinimumEdges(getMinEdges());
        Line.setMinimumDistance(getMinDistanceLeaves());
        LinePool.setNodeDegreeRatio(getNodeDegreeRatio());
        LinePool.setMinCoverFactor(getMinCoverFactor());
        LinePool.setMaxCoverFactor(getMaxCoverFactor());
        LinePool.setRestrictTerminals(shouldRestrictTerminals());
    }

    @Override
    public boolean shouldAddShortestPaths() {
        return addShortestPaths;
    }

    @Override
    public double getRatioSp() {
        return ratioSp;
    }

    public boolean shouldRestrictTerminals() {
        return restrictTerminals;
    }

    public boolean shouldAppendSingleEdges() {
        return appendSingleEdges;
    }

    public boolean shouldRestrictTurns() {
        return restrictTurns;
    }

    public boolean shouldRestrictForbiddenEdges() {
        return restrictForbiddenEdges;
    }
}
