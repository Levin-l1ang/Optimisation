package net.lintim.util.od;

import net.lintim.exception.LinTimException;
import net.lintim.model.Link;
import net.lintim.util.Config;

public class EdgeWeightProcessor {

    public static double getWaitingTime(Config config) {
        int minimalWaitingTime = config.getIntegerValue("ean_default_minimal_waiting_time");
        int maximalWaitingTime = config.getIntegerValue("ean_default_maximal_waiting_time");
        String waitingTimeModel = config.getStringValue("ean_model_weight_wait");
        switch (waitingTimeModel) {
            case "MINIMAL_WAITING_TIME":
                return minimalWaitingTime;
            case "MAXIMAL_WAITING_TIME":
                return maximalWaitingTime;
            case "AVERAGE_WAITING_TIME":
                return (minimalWaitingTime + maximalWaitingTime) / 2.0;
            case "ZERO_COST":
                return 0;
            default:
                throw new LinTimException("Unknown waiting time model " + waitingTimeModel);
        }
    }

    public enum DRIVE_WEIGHT {
        AVERAGE_DRIVING_TIME, MINIMAL_DRIVING_TIME, MAXIMAL_DRIVING_TIME, EDGE_LENGTH
    }

    public static DRIVE_WEIGHT getDriveWeight(Config config) {
        String driveTimeModel = config.getStringValue("ean_model_weight_drive");
        switch (driveTimeModel) {
            case "AVERAGE_DRIVING_TIME":
                return DRIVE_WEIGHT.AVERAGE_DRIVING_TIME;
            case "MINIMAL_DRIVING_TIME":
                return DRIVE_WEIGHT.MINIMAL_DRIVING_TIME;
            case "MAXIMAL_DRIVING_TIME":
                return DRIVE_WEIGHT.MAXIMAL_DRIVING_TIME;
            case "EDGE_LENGTH":
                return DRIVE_WEIGHT.EDGE_LENGTH;
            default:
                throw new LinTimException("Unknown drive time model: " + driveTimeModel);
        }
    }

    public static double getDriveTime(Link link, DRIVE_WEIGHT model) {
        switch (model) {
            case MAXIMAL_DRIVING_TIME:
                return link.getUpperBound();
            case MINIMAL_DRIVING_TIME:
                return link.getLowerBound();
            case AVERAGE_DRIVING_TIME:
                return (link.getLowerBound() + link.getUpperBound()) / 2.0;
            case EDGE_LENGTH:
                return link.getLength();
            default:
                throw new LinTimException("Unknown drive time model: " + model);
        }
    }

}
