package net.lintim.util.tools;

import net.lintim.exception.LinTimException;
import net.lintim.model.ActivityType;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;

/**
 * Helper class for a periodic ean, used for the transformation of a periodic timetable into lintim format
 */
public class PeriodicEanHelper {

	/**
	 * Get the start event of a line from an arbitrary event of said line.
	 * <p>
	 * Will search for the start event of the line corresponding to the given event and return it, or null if there
	 * is none
	 *
	 * @param ean         the ean to search in
	 * @param eventOfLine some arbitrary event of the line
	 * @return the corresponding start event or null if none can be found
	 */
	public static PeriodicEvent getStartEventOfLineWithFrequency(Graph<PeriodicEvent, PeriodicActivity> ean, PeriodicEvent
			eventOfLine) {
		return ean.getNodes().stream()
				.filter(periodicEvent -> periodicEvent.getLineId() == eventOfLine.getLineId())
				.filter(periodicEvent -> periodicEvent.getDirection() == eventOfLine.getDirection())
				.filter(periodicEvent -> periodicEvent.getLineFrequencyRepetition() == eventOfLine.getLineFrequencyRepetition())
				.filter(periodicEvent -> ean.getIncomingEdges(periodicEvent).stream().noneMatch(periodicActivity ->
						periodicActivity.getType() == ActivityType.DRIVE || periodicActivity.getType() ==
								ActivityType.WAIT))
				.findAny().orElseThrow(() -> new LinTimException("Could not find the start of the line for event " +
						eventOfLine));
	}

	/**
	 * Get the start event of a line from an arbitrary event of said line.
	 *
	 * Will search for the start event of the line corresponding to the given event and return it, or null if there
	 * is none
	 * @param ean the ean to search in
	 * @param eventOfLine some arbitrary event of the line
	 * @return the corresponding start event or null if none can be found
	 */
	public static PeriodicEvent getStartEventOfLineWithoutFrequency(Graph<PeriodicEvent, PeriodicActivity> ean, PeriodicEvent
			eventOfLine){
		return ean.getNodes().stream()
				.filter(periodicEvent -> periodicEvent.getLineId() == eventOfLine.getLineId())
				.filter(periodicEvent -> periodicEvent.getDirection() == eventOfLine.getDirection())
				.filter(periodicEvent -> ean.getIncomingEdges(periodicEvent).stream().noneMatch(periodicActivity ->
						periodicActivity.getType() == ActivityType.DRIVE || periodicActivity.getType() ==
								ActivityType.WAIT))
				.findAny().orElseThrow(() -> new LinTimException("Could not find the start of the line for event " +
						eventOfLine));
	}
}
