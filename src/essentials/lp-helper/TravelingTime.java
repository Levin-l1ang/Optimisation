import java.lang.Math;

public class TravelingTime {

    private double a;
    private double b;
    private final double v;
    private double dmax;
    private final boolean use_traveling_time;

    public TravelingTime(double acceleration, double speed, double deceleration, boolean use_traveling_time) {
        this.a = acceleration;
        this.b = deceleration;
        this.use_traveling_time = use_traveling_time;
        if (use_traveling_time) {
            this.v = speed * 1000.0 / 3600.0;
        } else {
            this.v = speed;
        }
        this.dmax = Math.pow(v, 2.0) / (2.0 * a) + Math.pow(v, 2.0) / (2.0 * b);
    }

    public TravelingTime(double speed) {
        this.use_traveling_time = false;
        this.v = speed;
    }

    /**
     * Computes travel time for fixed speed in minutes. Needs speed in km/h.
     *
     * @param distance in km
     * @return travel time for fixed speed in minutes
     */
    public double calcTimeInMinutes(double distance) {
        if (use_traveling_time) {
            throw new RuntimeException("Method calcTimeInMinutes can only be used for fixed speed!");
        }
        return distance / v * 60;
    }

    public double calcTime(double d) {
        if (use_traveling_time) {
            if (d < dmax)
                return Math.pow(d * 2.0 * (a + b) / (a * b), 0.5);
            else
                return d / v + v / (2.0 * a) + v / (2.0 * b);
        } else {
            return d / v;
        }

    }


}
