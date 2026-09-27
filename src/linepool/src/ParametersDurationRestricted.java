import net.lintim.util.Config;

public class ParametersDurationRestricted extends ParametersTree{

    private final int maxBufferTime;
    private final boolean allowLinesHalfPeriod;

    public ParametersDurationRestricted(Config config) {
        super(config);
        maxBufferTime = config.getIntegerValue("lpool_restricted_maximum_buffer_time");
        allowLinesHalfPeriod = config.getBooleanValue("lpool_restricted_allow_half_period");
    }

    public void setParametersInClasses() {
        super.setParametersInClasses();
        Line.setRestrictLineDurationPeriodically(true);
        Line.setPeriodicRestrictions(getPeriodLength()-getMinTurnoverTime()-getMaxBufferTime(),
            getPeriodLength()-getMinTurnoverTime());
        Line.setHalfPeriodRestrictions(getPeriodLength()/2.-getMinTurnoverTime()-getMaxBufferTime(),
            getPeriodLength()/2.-getMinTurnoverTime());
        Line.setAllowHalfPeriodLength(shouldAllowLinesHalfPeriod());
    }

    @Override
    public boolean shouldAddShortestPaths() {
        return false;
    }

    @Override
    public double getRatioSp() {
        return 0;
    }

    public int getMaxBufferTime() {
        return maxBufferTime;
    }

    public boolean shouldAllowLinesHalfPeriod() {
        return allowLinesHalfPeriod;
    }
}
