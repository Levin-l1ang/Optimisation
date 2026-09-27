import net.lintim.exception.LinTimException;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private final String method;
    private final int percentage;
    private final int maxWait;
    private final boolean swapHeadways;
    private final String optMethod;
    private final boolean writeBestOfAllObjectives;
    private final boolean checkConsistency;
    private final int earliestTime;
    private final int latestTime;
    private final int maxDelay;
    private final String dispoFile;
    private final String dispoFileHeader;
    private final String bestOfAllObjectivesFile;
    private final String pathsFileName;

    public Parameters(Config config) {
        super(config, "DM_");
        method = config.getStringValue("DM_method");
        dispoFile = config.getStringValue("default_disposition_timetable_file");
        dispoFileHeader = config.getStringValue("timetable_header_disposition");
        percentage = config.getIntegerValue("DM_method_prio_percentage");
        if (percentage < 0)
            throw new LinTimException("SolveDM: DM_method_prio_percentage must not be negative");
        maxWait = config.getIntegerValue("DM_propagate_maxwait");
        swapHeadways = config.getBooleanValue("DM_propagate_swapHeadways");
        optMethod = config.getStringValue("DM_opt_method_for_heuristic");
        writeBestOfAllObjectives = config.getBooleanValue("DM_best_of_all_write_objectives");
        if (writeBestOfAllObjectives) {
            bestOfAllObjectivesFile = config.getStringValue("filename_dm_best_of_all_objectives");
        } else {
            bestOfAllObjectivesFile = "";
        }
        earliestTime = config.getIntegerValue("DM_earliest_time");
        latestTime = config.getIntegerValue("DM_latest_time");
        maxDelay = config.getIntegerValue("delays_max_delay");
        checkConsistency = config.getBooleanValue("DM_enable_consistency_checks");
        pathsFileName = config.getStringValue("default_passenger_paths_file");
    }

    public String getMethod() {
        return method;
    }

    public int getPercentage() {
        return percentage;
    }

    public int getMaxWait() {
        return maxWait;
    }

    public boolean shouldSwapHeadways() {
        return swapHeadways;
    }

    public String getOptMethod() {
        return optMethod;
    }

    public boolean shouldWriteBestOfAllObjectives() {
        return writeBestOfAllObjectives;
    }

    public String getDispoFile() {
        return dispoFile;
    }

    public String getDispoFileHeader() {
        return dispoFileHeader;
    }

    public String getBestOfAllObjectivesFile() {
        return bestOfAllObjectivesFile;
    }

    public boolean shouldCheckConsistency() {
        return checkConsistency;
    }

    public int getEarliestTime() {
        return earliestTime;
    }

    public int getLatestTime() {
        return latestTime;
    }

    public int getMaxDelay() {
        return maxDelay;
    }

    public String getPathsFileName() {
        return pathsFileName;
    }
}
