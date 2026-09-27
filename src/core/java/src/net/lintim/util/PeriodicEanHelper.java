package net.lintim.util;

import net.lintim.model.*;

/**
 * Static helper classes to work with a periodic EAN.
 */
public class PeriodicEanHelper {
    /**
     * Get the start event of the line with the given properties in the ean, or null if there is none. A start event
     * is a periodic event without any incoming drive or wait activities.
     * @param ean the ean to check
     * @param lineId the line id to find
     * @param lineFrequencyRepetition the line frequency repetition to find
     * @param direction the line direction to find
     * @return the start event of the line with the given properties or null, if there is none
     */
    public static PeriodicEvent getStartOfLine(Graph<PeriodicEvent, PeriodicActivity> ean, int lineId, int
        lineFrequencyRepetition, LineDirection direction){
        return ean.getNodes().stream()
            .filter(periodicEvent -> periodicEvent.getLineId() == lineId)
            .filter(periodicEvent -> periodicEvent.getLineFrequencyRepetition() == lineFrequencyRepetition)
            .filter(periodicEvent -> periodicEvent.getDirection() == direction)
            .filter(periodicEvent ->
                ean.getIncomingEdges(periodicEvent).stream().noneMatch(periodicActivity -> periodicActivity.getType()
                    == ActivityType.DRIVE || periodicActivity
                    .getType() == ActivityType.WAIT)
            ).findAny().orElse(null);
    }

    /**
     * Get the end event of the line with the given properties in the ean, or null if there is none. An end event
     * is a periodic event without any outgoing drive or wait activities.
     * @param ean the ean to check
     * @param lineId the line id to find
     * @param lineFrequencyRepetition the line frequency repetition to find
     * @param direction the line direction to find
     * @return the end event of the line with the given properties or null, if there is none
     */
    public static PeriodicEvent getEndOfLine(Graph<PeriodicEvent, PeriodicActivity> ean, int lineId, int
        lineFrequencyRepetition, LineDirection direction){
        return ean.getNodes().stream()
            .filter(periodicEvent -> periodicEvent.getLineId() == lineId)
            .filter(periodicEvent -> periodicEvent.getLineFrequencyRepetition() == lineFrequencyRepetition)
            .filter(periodicEvent -> periodicEvent.getDirection() == direction)
            .filter(periodicEvent ->
                ean.getOutgoingEdges(periodicEvent).stream().noneMatch(periodicActivity -> periodicActivity.getType()
                    == ActivityType.DRIVE || periodicActivity
                    .getType() == ActivityType.WAIT)
            ).findAny().orElse(null);
    }

    public static Integer transformTimeToPeriodic(int time, int periodLength) {
        return ((time % periodLength) + periodLength ) % periodLength;
    }
}
