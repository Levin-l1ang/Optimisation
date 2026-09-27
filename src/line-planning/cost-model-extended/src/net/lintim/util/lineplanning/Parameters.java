package net.lintim.util.lineplanning;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private int commonFrequencyDivisor;
    private final boolean respectFixedLines;
    private final boolean respectForbiddenLinks;
    private final int passengerPerVehicle;
    private final String fixedLinesFileName;
    private final String forbiddenLinksFileName;
    private final int periodLength;

    public Parameters(Config config) {
        super(config, "lc_");
        respectFixedLines = config.getBooleanValue("lc_respect_fixed_lines");
        respectForbiddenLinks = config.getBooleanValue("lc_respect_forbidden_edges");
        commonFrequencyDivisor = config.getIntegerValue("lc_common_frequency_divisor");
        if (commonFrequencyDivisor <= 0) {
            periodLength = config.getIntegerValue("period_length");
        } else {
            periodLength = -1;
        }
        if (respectFixedLines) {
            fixedLinesFileName = config.getStringValue("filename_lc_fixed_lines");
            passengerPerVehicle = config.getIntegerValue("gen_passengers_per_vehicle");
        } else {
            fixedLinesFileName = "";
            passengerPerVehicle = -1;
        }
        if (respectForbiddenLinks) {
            forbiddenLinksFileName = config.getStringValue("filename_forbidden_links_file");
        }
        else {
            forbiddenLinksFileName = "";
        }
    }

    public boolean respectForbiddenLinks() {
        return respectForbiddenLinks;
    }

    public String getForbiddenLinksFileName() {
        return forbiddenLinksFileName;
    }

    public int getCommonFrequencyDivisor() {
        return commonFrequencyDivisor;
    }

    public boolean respectFixedLines() {
        return respectFixedLines;
    }

    public int getPassengerPerVehicle() {
        return passengerPerVehicle;
    }

    public String getFixedLinesFileName() {
        return fixedLinesFileName;
    }

    public int getPeriodLength() {
        return periodLength;
    }

    public void setCommonFrequencyDivisor(int commonFrequencyDivisor) {
        this.commonFrequencyDivisor = commonFrequencyDivisor;
    }
}
