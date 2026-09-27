import net.lintim.util.Config;

public class GreedyParameters {

    private final boolean directedPtn;
    private final boolean destructionAllowed;
    private final double radius;
    private final String distance;

    public GreedyParameters(Config config) {
        directedPtn = !config.getBooleanValue("ptn_is_undirected");
        destructionAllowed = config.getBooleanValue("sl_destruction_allowed");
        radius = config.getDoubleValue("sl_radius");
        distance = config.getStringValue("sl_distance");
    }

    public boolean isDirectedPtn() {
        return directedPtn;
    }

    public boolean isDestructionAllowed() {
        return destructionAllowed;
    }

    public double getRadius() {
        return radius;
    }

    public String getDistance() {
        return distance;
    }
}
