package net.lintim.util;

import java.nio.file.Paths;

public class Parameters {

    private final int maxTravelTime;
    private final int maxChanges;
    private final int maxTurnAroundTime;
    private final double capacity;
    private final int periodLength;
    private final int minChangeTime;
    private final int maxChangeTime;
    private final double changePenalty;
    private int routingStartTime;
    private int routingEndTime;
    private final int timeUnitsPerMinute;
    private final String modelFileName;
    private final int maxGroupSize;
    private final boolean writeInitialRouting;
    private final boolean readInitialRouting;
    private final boolean writeCapacitatedRouting;
    private final boolean readCapacitatedRouting;
    private final boolean outputMLData;
    private final boolean createMissingChanges;
    private final boolean useGreedyRouting;
    private final boolean debugWritingFiles = false;
    private final boolean outputEverySolution;
    private final int maxIteration;
    private final int rerouteInterval;
    private final String dwellingSlackFileName;
    private final String changeSlackFileName;
    private final String changeDistributionFileName;
    private final String linkLoadDistributionFileName;
    private final String eventDistributionFileName;
    private final String odChangeDistributionFileName;
    private final String odTravelTimeDistributionFileName;
    private final String lineDistributionFileName;
    private final String turnaroundDistributionFileName;
    private final String tensorFileName;
    private final String mlResultFileName;
    private final String solutionFolderName;
    private final String inputFile;
    private final int turnoverTime;
    private final boolean useSVR;
    private final boolean useSingleANNModels;
    private final int svrPort;

    public final static int SECONDS_PER_MINUTE = 60;
    public final static int SECONDS_PER_HOUR = 3600;

    private static final Logger logger = new Logger(Parameters.class.getCanonicalName());

    public Parameters(Config config) {
        this.maxTravelTime = config.getIntegerValue("rob_max_travel_time");
        this.maxChanges = config.getIntegerValue("rob_max_changes");
        this.maxTurnAroundTime = config.getIntegerValue("rob_max_turnaround_time");
        this.capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        this.timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
        this.periodLength = config.getIntegerValue("period_length") * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        this.minChangeTime = config.getIntegerValue("ean_default_minimal_change_time") * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        this.maxChangeTime = config.getIntegerValue("ean_default_maximal_change_time") * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        this.changePenalty = config.getDoubleValue("ean_change_penalty") * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        // TODO: We may want to add the computation here instead of setting it in config
        this.routingStartTime = config.getIntegerValue("rob_routing_start_time");
        this.routingEndTime = Math.min(config.getIntegerValue("rob_routing_end_time"),
            this.routingStartTime + 4 * SECONDS_PER_HOUR);
        this.modelFileName = config.getStringValue("filename_robustness_ml_model");
        this.maxGroupSize = config.getIntegerValue("rob_max_group_size");
        this.maxIteration = config.getIntegerValue("rob_max_iteration");
        this.rerouteInterval = config.getIntegerValue("rob_reroute_interval");
        this.outputEverySolution = config.getBooleanValue("rob_output_every_solution");
        this.useSVR = config.getBooleanValue("rob_use_api_for_prediction");
        this.useSingleANNModels = config.getBooleanValue("rob_use_single_ann_models");
        if (useSVR) {
            svrPort = config.getIntegerValue("rob_api_port");
        }
        else {
            svrPort = -1;
        }
        this.writeInitialRouting = false;
        this.readInitialRouting = false;
        this.writeCapacitatedRouting = false;
        this.readCapacitatedRouting = false;
        this.outputMLData = false;
        this.useGreedyRouting = true;
        this.createMissingChanges = config.getBooleanValue("rob_create_missing_changes");
        this.solutionFolderName = config.getStringValue("rob_debug_output_path");
        // File names for ml output
        this.dwellingSlackFileName = Paths.get("statistic", "average_dwelling_slack.csv").toString();
        this.changeSlackFileName = Paths.get("statistic", "average_interchange_slack.csv").toString();
        this.changeDistributionFileName = Paths.get("statistic", "change_activity_distribution.csv").toString();
        this.linkLoadDistributionFileName = Paths.get("statistic", "edge_occupation_distribution.csv").toString();
        this.eventDistributionFileName = Paths.get("statistic", "evetn_distribution.csv").toString();
        this.odChangeDistributionFileName = Paths.get("statistic", "group_interchange_distribution.csv").toString();
        this.odTravelTimeDistributionFileName = Paths.get("statistic", "group_traveltime_distribution.csv").toString();
        this.lineDistributionFileName = Paths.get("statistic", "line_distribution_with_frequencies.csv").toString();
        this.turnaroundDistributionFileName = Paths.get("statistic", "turnaround_slack_histogram.csv").toString();
        this.tensorFileName = config.getStringValue("filename_robustness_tensor_file_name");
        this.mlResultFileName = Paths.get("statistic", "model.result").toString();
        inputFile = config.getStringValue("rob_start_solutions_file");
        turnoverTime = config.getIntegerValue("vs_turn_over_time") * SECONDS_PER_MINUTE / this.getTimeUnitsPerMinute();
    }

    public void setRoutingStartTime(int routingStartTime) {
        logger.debug("Set routing start time to " + routingStartTime);
        this.routingStartTime = routingStartTime;
        logger.debug("Routing start time is now " + getRoutingStartTime());
        this.routingEndTime = this.routingStartTime + 4 * SECONDS_PER_HOUR;
    }

    public boolean shouldUseSVR() {
        return useSVR;
    }

    public int getSvrPort() {
        return svrPort;
    }

    public int getTurnoverTime() {
        return turnoverTime;
    }

    public String getInputFile() {
        return inputFile;
    }

    public int getTimeUnitsPerMinute() {
        return timeUnitsPerMinute;
    }

    public boolean outputEverySolution() {
        return outputEverySolution;
    }

    public String getSolutionFolderName() {
        return solutionFolderName;
    }

    public String getDwellingSlackFileName() {
        return dwellingSlackFileName;
    }

    public String getChangeSlackFileName() {
        return changeSlackFileName;
    }

    public String getChangeDistributionFileName() {
        return changeDistributionFileName;
    }

    public String getLinkLoadDistributionFileName() {
        return linkLoadDistributionFileName;
    }

    public String getEventDistributionFileName() {
        return eventDistributionFileName;
    }

    public String getOdChangeDistributionFileName() {
        return odChangeDistributionFileName;
    }

    public String getOdTravelTimeDistributionFileName() {
        return odTravelTimeDistributionFileName;
    }

    public String getLineDistributionFileName() {
        return lineDistributionFileName;
    }

    public String getTurnaroundDistributionFileName() {
        return turnaroundDistributionFileName;
    }

    public String getTensorFileName() {
        return tensorFileName;
    }

    public String getMlResultFileName() {
        return mlResultFileName;
    }

    public int getMaxTravelTime() {
        return maxTravelTime;
    }

    public int getMaxChanges() {
        return maxChanges;
    }

    public int getMaxTurnAroundTime() {
        return maxTurnAroundTime;
    }

    public double getCapacity() {
        return capacity;
    }

    public int getMinChangeTime() {
        return minChangeTime;
    }

    public int getMaxChangeTime() {
        return maxChangeTime;
    }

    public double getChangePenalty() {
        return changePenalty;
    }

    public int getRoutingStartTime() {
        return routingStartTime;
    }

    public int getRoutingEndTime() {
        return routingEndTime;
    }

    public String getModelFileName() {
        return modelFileName;
    }

    public int getMaxGroupSize() {
        return maxGroupSize;
    }

    public boolean writeInitialRouting() {
        return writeInitialRouting;
    }

    public boolean readInitialRouting() {
        return readInitialRouting;
    }

    public boolean writeCapacitatedRouting() {
        return writeCapacitatedRouting;
    }

    public boolean readCapacitatedRouting() {
        return readCapacitatedRouting;
    }

    public boolean outputMLData() {
        return outputMLData;
    }

    public boolean createMissingChanges() {
        return createMissingChanges;
    }

    public int getPeriodLength() {
        return periodLength;
    }

    public boolean isUseGreedyRouting() {
        return useGreedyRouting;
    }

    public boolean writeDebugFiles() {
        return debugWritingFiles;
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public int getRerouteInterval() {
        return rerouteInterval;
    }

    public boolean useSingleANNModels() {
        return useSingleANNModels;
    }
}
