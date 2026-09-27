package net.lintim.linepool.util;

import net.lintim.util.Config;

public class Parameters {
    private final double fixedLineCosts;
    private final double costPerEdge;
    private final double costPerLength;

    public Parameters(Config config) {
        fixedLineCosts = config.getDoubleValue("lpool_costs_fixed");
        costPerEdge = config.getDoubleValue("lpool_costs_edges");
        costPerLength = config.getDoubleValue("lpool_costs_length");
    }

    public double getFixedLineCosts() {
        return fixedLineCosts;
    }

    public double getCostPerEdge() {
        return costPerEdge;
    }

    public double getCostPerLength() {
        return costPerLength;
    }
}
