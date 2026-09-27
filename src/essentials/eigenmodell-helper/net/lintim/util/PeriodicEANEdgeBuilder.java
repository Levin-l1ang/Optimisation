package net.lintim.util;

import net.lintim.model.PeriodicEAN;
import net.lintim.model.PeriodicEANEdge;
import net.lintim.model.PeriodicEANVertex;

/**
 * Class for processing csv line contents and create new activities from them. These are added to the given periodic
 * ean.
 */
public class PeriodicEANEdgeBuilder implements CanProcessCsv{

	/**
	 * The ean to add the activities to
	 */
	private PeriodicEAN ean;

	/**
	 * Create a new builder to process csv lines for activities
	 * @param ean the ean to add the activities to
	 */
	public PeriodicEANEdgeBuilder(PeriodicEAN ean){
		this.ean = ean;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 7){
			throw new IllegalArgumentException("The given arguments are not a valid periodic event string, since args" +
					".length!=5");
		}
		int activityId = Integer.parseInt(args[0].trim());
		String activityType = args[1].trim();
		activityType = activityType.substring(1, activityType.length()-1);
		int sourceId = Integer.parseInt(args[2].trim());
		int targetId = Integer.parseInt(args[3].trim());
		int lowerBound = Integer.parseInt(args[4].trim());
		int upperBound = Integer.parseInt(args[5].trim());
		double numberOfPassengers = Double.parseDouble(args[6].trim());
		PeriodicEANVertex source = ean.getVertex(sourceId);
		PeriodicEANVertex target = ean.getVertex(targetId);
		PeriodicEANEdge edge = new PeriodicEANEdge(activityId, source, target, activityType, lowerBound, upperBound,
				numberOfPassengers);
		ean.addEdge(edge);
	}
}
