package net.lintim.algorithm.vehiclescheduling;

import net.lintim.model.*;
import net.lintim.util.*;
import net.lintim.util.vehiclescheduling.Parameters;

/**
 * Class containing a static method to add vehicle scheduling activities to an existing periodic ean
 */
public class AddToEan {

	private static final Logger logger = new Logger(AddToEan.class.getCanonicalName());

	/**
	 * Add a simple vehicle schedule to the ean, i.e., connect both directions with the same line repetition number
	 * of a line with circulation activities. The lower bound of these activities will be the given turn over time.
	 * @param ean the ean to add the circulation activities to
	 * @param lineConcept the line concept, including frequencies
     * @param parameters the parameters to use
	 */
	public static void addSimpleVehicleSchedulesToEan(Graph<PeriodicEvent, PeriodicActivity> ean, LinePool lineConcept,
                                                      Parameters parameters){
		for(Line line : lineConcept.getLines()){
			if(line.getFrequency() == 0){
				continue;
			}
			int maxFrequency = parameters.eanContainsFrequencies() ? line.getFrequency() : 1;
			int maxActivityId = GraphHelper.getMaxEdgeId(ean)+1;
			for(int freq = 1; freq <= maxFrequency; freq++){
				PeriodicEvent startOfForwards = PeriodicEanHelper.getStartOfLine(ean, line.getId(), freq,
						LineDirection.FORWARDS);
				PeriodicEvent endOfForwards = PeriodicEanHelper.getEndOfLine(ean, line.getId(), freq, LineDirection
						.FORWARDS);
				PeriodicEvent startOfBackwards = PeriodicEanHelper.getStartOfLine(ean, line.getId(), freq,
						LineDirection.BACKWARDS);
				PeriodicEvent endOfBackwards = PeriodicEanHelper.getEndOfLine(ean, line.getId(), freq, LineDirection
						.BACKWARDS);
				//Error checking: Are there any circulation activities for current lines?
				if(ean.getOutgoingEdges(endOfForwards).stream().anyMatch(periodicActivity -> periodicActivity.getType()
						== ActivityType.TURNAROUND)){
					logger.warn("The forwards direction of line " + line.getId() + " in rep " + freq +
							" has already an outgoing circulation activity!");
				}
				if(ean.getOutgoingEdges(endOfBackwards).stream().anyMatch(periodicActivity -> periodicActivity.getType()
						== ActivityType.TURNAROUND)){
					logger.warn("The backwards direction of line " + line.getId() + " in rep " + freq +
							" has already an outgoing circulation activity!");
				}
				ean.addEdge(new PeriodicActivity(maxActivityId++, ActivityType.TURNAROUND, endOfForwards,
						startOfBackwards, parameters.getTurnoverTime(), parameters.getTurnoverTime() + 2 * parameters.getMaximalBufferTime(), 0));
				ean.addEdge(new PeriodicActivity(maxActivityId++, ActivityType.TURNAROUND, endOfBackwards,
						startOfForwards, parameters.getTurnoverTime(), parameters.getTurnoverTime() + 2 * parameters.getMaximalBufferTime(), 0));
			}
		}
	}
}
