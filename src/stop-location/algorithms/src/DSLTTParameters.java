import net.lintim.util.Config;

public class DSLTTParameters extends DSLParameters {

    private final int waitingTime;
    private final double acceleration;
    private final double deceleration;
    private final double speed;


    public DSLTTParameters(Config config) {
        super(config);
        waitingTime = config.getIntegerValue("sl_waiting_time");
        acceleration = config.getDoubleValue("sl_acceleration");
        deceleration = config.getDoubleValue("sl_deceleration");
        speed = config.getDoubleValue("gen_vehicle_speed");
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public double getDeceleration() {
        return deceleration;
    }

    public double getSpeed() {
        return speed;
    }
}
