package net.lintim.model;

import java.util.Objects;

/**
 * Class representing a station limit, i.e., limits on change and wait time for one specific station. For details on the
 * different parameters, see the LinTim-documentation.
 */
public class StationLimit {
    private final int stopId;
    private final int minChangeTime;
    private final int maxChangeTime;
    private final int minWaitTime;
    private final int maxWaitTime;

    /**
     * Create a new station limit object.
     * @param stopId the id of the stop for which the limits should hold
     * @param minWaitTime the minimal wait time
     * @param maxWaitTime the maximal wait time
     * @param minChangeTime the minimal change time
     * @param maxChangeTime the maximal change time
     */
    public StationLimit(int stopId, int minWaitTime, int maxWaitTime, int minChangeTime, int maxChangeTime) {
        this.stopId = stopId;
        this.minChangeTime = minChangeTime;
        this.maxChangeTime = maxChangeTime;
        this.minWaitTime = minWaitTime;
        this.maxWaitTime = maxWaitTime;
    }

    public int getStopId() {
        return stopId;
    }

    public int getMinChangeTime() {
        return minChangeTime;
    }

    public int getMaxChangeTime() {
        return maxChangeTime;
    }

    public int getMinWaitTime() {
        return minWaitTime;
    }

    public int getMaxWaitTime() {
        return maxWaitTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StationLimit that = (StationLimit) o;
        return stopId == that.stopId &&
            minChangeTime == that.minChangeTime &&
            maxChangeTime == that.maxChangeTime &&
            minWaitTime == that.minWaitTime &&
            maxWaitTime == that.maxWaitTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, minChangeTime, maxChangeTime, minWaitTime, maxWaitTime);
    }
}
