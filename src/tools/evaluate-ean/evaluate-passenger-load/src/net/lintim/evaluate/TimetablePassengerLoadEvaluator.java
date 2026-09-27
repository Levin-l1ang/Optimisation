package net.lintim.evaluate;

import net.lintim.exception.LinTimException;
import net.lintim.model.*;
import net.lintim.util.LogLevel;
import net.lintim.util.Pair;

import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Class for computing the loads on the current ean
 */
public class TimetablePassengerLoadEvaluator {
	private static Logger logger = Logger.getLogger(TimetablePassengerLoadEvaluator.class.getCanonicalName());

	/**
	 * Evaluate the given ean and ptn, computing how many passengers are using specific ptn edges. This is compared
	 * to the capacity on the edge, i.e., the number of vehicles traversing the edge in each planning period. The
	 * invalid loads, i.e., edges that have more passengers than can be transported, will be added to the returned
	 * Map, stored by the corresponding edge and containing the load and the summed frequency on the edge.
	 * @param ptn the ptn
	 * @param ean the ean
	 * @param lineConcept the lines with their frequencies
	 * @param capacity the capacities of the vehicles
	 * @return all invalid loads
	 */
	public static Pair<HashMap<Link, Pair<Double, Integer>>, Double>
	evaluate(Graph<Stop, Link> ptn, Graph<PeriodicEvent, PeriodicActivity> ean, LinePool lineConcept, int capacity) {
		HashMap<Link, Double> forwardLoads = new HashMap<>();
		HashMap<Link, Double> backwardLoads = new HashMap<>();
		HashMap<Link, Pair<Double, Integer>> invalidLoads = new HashMap<>();
		for(Link link : ptn.getEdges()) {
			forwardLoads.put(link, 0.);
			backwardLoads.put(link, 0.);
		}
		// Read the loads from the ean
		int countActivities = 0;
		double countLoad = 0;
		for(PeriodicActivity activity : ean.getEdges()) {
			if (activity.getNumberOfPassengers() == 0 || activity.getType() != ActivityType.DRIVE) {
				continue;
			}
			countActivities += 1;
			countLoad += activity.getNumberOfPassengers();
			// Find the corresponding ptn edge
			Link link = ptn.getEdge((Link l) -> l.getLeftNode().getId() == activity.getLeftNode().getStopId() && l
					.getRightNode().getId() == activity.getRightNode().getStopId(), true);
			if (link != null) {
				forwardLoads.put(link, forwardLoads.get(link) + activity.getNumberOfPassengers());
			}
			else if (!ptn.isDirected()) {
				link = ptn.getEdge((Link l) -> l.getRightNode().getId() == activity.getLeftNode().getStopId() && l
						.getLeftNode().getId() == activity.getRightNode().getStopId(), true);
				if (link != null) {
					backwardLoads.put(link, backwardLoads.get(link) + activity.getNumberOfPassengers());
				}
				else {
					throw new LinTimException("Could not find ptn link to activity " + activity);
				}
			}
			else {
				throw new LinTimException("Could not find ptn link to activity " + activity);
			}
		}
		logger.log(LogLevel.INFO, "Processed " + countActivities + " activities with a total load of " + countLoad);
		double maxLoadFactor = 0;
		Link worstLink = null;
		for (Link link : ptn.getEdges()) {
			if (forwardLoads.get(link) == 0 && backwardLoads.get(link) == 0) {
				continue;
			}
			// Find the frequency on this link
			int frequencyOnLink = lineConcept.getLines().stream()
					.filter(l -> l.getLinePath().getEdges().contains(link))
					.mapToInt(Line::getFrequency)
					.sum();
			double loadOnLink = Math.max(forwardLoads.get(link), backwardLoads.get(link));
			int neededFrequency = (int) Math.ceil(loadOnLink / capacity);
			if (frequencyOnLink == 0 && loadOnLink > 0) {
				maxLoadFactor = Double.POSITIVE_INFINITY;
				worstLink = link;
			}
			else {
				if (loadOnLink / (frequencyOnLink * capacity) > maxLoadFactor) {
					worstLink = link;
				}
				maxLoadFactor = Math.max(maxLoadFactor, loadOnLink / (frequencyOnLink * capacity));
			}
			if (neededFrequency > frequencyOnLink) {
				logger.log(LogLevel.INFO, "Found invalid ptn edge " + link + " with load of " + loadOnLink + " " +
						"and a current frequency of " + frequencyOnLink);
				invalidLoads.put(link, new Pair<>(loadOnLink, frequencyOnLink));
			}
		}
		logger.log(LogLevel.INFO, "Maximal load factor found: " + maxLoadFactor + " for link " + worstLink);
		return new Pair<>(invalidLoads, maxLoadFactor);
	}
}
