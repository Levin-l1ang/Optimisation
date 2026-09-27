package net.lintim.util;

public class LocalSearchParameters extends Parameters{
    private final double changeWeight;
    private final double driveWeight;
    private final double waitWeight;
    private final double turnWeight;
    private final double allowedTravelTimeIncrease;
    private final int bufferIncreasePerStep;
    private final int numberOfCandidates;
    private final boolean reevaluateEverySolution;
    private final int propagateSlackMinTime;
    private final double propagateSlackPercentage;
    private final boolean propagateSlackUsePercentage;
    private final boolean usePeriodicTimetabling;
    private final boolean selectByRatio;
    private final int startSolutionNumber;

    public LocalSearchParameters(Config config) {
        super(config);
        this.changeWeight = config.getDoubleValue("rob_ls_change_weight");
        this.driveWeight = config.getDoubleValue("rob_ls_drive_weight");
        this.waitWeight = config.getDoubleValue("rob_ls_wait_weight");
        this.turnWeight = config.getDoubleValue("rob_ls_turn_weight");
        this.reevaluateEverySolution = false;
        this.bufferIncreasePerStep = config.getIntegerValue("rob_ls_buffer_increase_per_step");
        this.numberOfCandidates = config.getIntegerValue("rob_ls_candidates_per_type");
        int minTime = config.getIntegerValue("rob_ls_propagate_slack_min_time");
        this.allowedTravelTimeIncrease = config.getDoubleValue("rob_ls_allowed_travel_time_increase");
        this.propagateSlackMinTime = Math.max(minTime, 0); // only use if > 0
        double usePercentage = config.getDoubleValue("rob_ls_propagate_slack_percentage");
        this.propagateSlackPercentage = Math.max(Math.min(usePercentage, 1), 0); // Move to [0,1]
        this.propagateSlackUsePercentage = config.getBooleanValue("rob_ls_propagate_slack_use_percentage");
        this.usePeriodicTimetabling = config.getBooleanValue("rob_ls_use_periodic_timetabling");
        this.startSolutionNumber = config.getIntegerValue("rob_ls_start_solution");
        this.selectByRatio = config.getBooleanValue("rob_ls_select_by_ratio");

    }

    public int getStartSolutionNumber() {
        return startSolutionNumber;
    }

    public boolean usePeriodicTimetabling() {
        return usePeriodicTimetabling;
    }

    public int getPropagateSlackMinTime() {
        return propagateSlackMinTime;
    }

    public double getPropagateSlackPercentage() {
        return propagateSlackPercentage;
    }

    public boolean propagateSlackUsePercentage() {
        return propagateSlackUsePercentage;
    }

    public int getNumberOfCandidates() {
        return numberOfCandidates;
    }

    public double getChangeWeight() {
        return changeWeight;
    }

    public double getDriveWeight() {
        return driveWeight;
    }

    public double getWaitWeight() {
        return waitWeight;
    }

    public double getTurnWeight() {
        return turnWeight;
    }

    public int getBufferIncreasePerStep() {
        return bufferIncreasePerStep;
    }


    public boolean isReevaluateEverySolution() {
        return reevaluateEverySolution;
    }

    public boolean useSelectByRatio() {
        return selectByRatio;
    }

    public double getAllowedTravelTimeIncrease() {
        return allowedTravelTimeIncrease;
    }
}
