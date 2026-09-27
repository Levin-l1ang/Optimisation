package net.lintim.util;

import net.lintim.model.AperiodicEAN;
import net.lintim.model.AperiodicEANEdge;
import net.lintim.model.AperiodicEANVertex;
import net.lintim.model.PeriodicEANEdge;

/**
 * Class for processing csv line contents and creating activities from them. These are added to the given aperiodic ean
 */
public class AperiodicEANEdgeBuilder implements CanProcessCsv{
	/**
	 * The aperiodic ean to add the vertices to
	 */
	private AperiodicEAN ean;

	/**
	 * Create a new builder on the given ean
	 * @param ean the ean to add the created activities to
	 */
	public AperiodicEANEdgeBuilder(AperiodicEAN ean){
		this.ean = ean;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 8){
			throw new IllegalArgumentException("The given arguments are not a valid aperiodic event string, since args" +
					".length!=8");
		}
		int activityId = Integer.parseInt(args[0].trim());
		int periodicId = Integer.parseInt(args[1].trim());
		String activityType = args[2].trim();
		activityType = activityType.substring(1, activityType.length()-1);
		int sourceEventId = Integer.parseInt(args[3].trim());
		int targetEventId = Integer.parseInt(args[4].trim());
		int lowerBound = Integer.parseInt(args[5].trim());
		int upperBound = Integer.parseInt(args[6].trim());
		double numberOfPassengers = Double.parseDouble(args[7].trim());
		AperiodicEANVertex source = ean.getVertex(sourceEventId);
		AperiodicEANVertex target = ean.getVertex(targetEventId);
		PeriodicEANEdge periodicEdge = ean.getPeriodicEAN().getEdge(periodicId);
		AperiodicEANEdge edge = new AperiodicEANEdge(activityId, source, target, activityType, lowerBound,
				upperBound, numberOfPassengers, periodicEdge);
		ean.addEdge(edge);
	}
}
