package net.lintim.util;

import net.lintim.model.AperiodicEAN;
import net.lintim.model.AperiodicEANVertex;
import net.lintim.model.PeriodicEANVertex;


/**
 * Class for processing csv line contents and create events from them. These are added to the given aperiodic ean
 */
public class AperiodicEANVertexBuilder implements CanProcessCsv{
	/**
	 * The aperiodic ean to add the vertices to
	 */
	private AperiodicEAN ean;

	/**
	 * Create a new builder on the given ean
	 * @param ean the ean to add the created events to
	 */
	public AperiodicEANVertexBuilder(AperiodicEAN ean){
		this.ean = ean;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 6){
			throw new IllegalArgumentException("The given arguments are not a valid aperiodic event string, since args" +
					".length!=6");
		}
		int eventId = Integer.parseInt(args[0].trim());
		int periodicId = Integer.parseInt(args[1].trim());
		int eventTime = Integer.parseInt(args[3].trim());
		double numberOfPassengers = Double.parseDouble(args[4].trim());
		PeriodicEANVertex periodicEvent = ean.getPeriodicEAN().getVertex(periodicId);
		AperiodicEANVertex vertex = new AperiodicEANVertex(eventId, periodicEvent, eventTime, numberOfPassengers);
		ean.addVertex(vertex);
	}




}
