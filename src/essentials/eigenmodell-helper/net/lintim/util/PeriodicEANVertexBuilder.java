package net.lintim.util;

import net.lintim.model.PTNVertex;
import net.lintim.model.PeriodicEAN;
import net.lintim.model.PeriodicEANVertex;

/**
 * Class for processing csv line contents and creating periodic events from them. These are added to the given
 * periodic ean.
 */
public class PeriodicEANVertexBuilder implements CanProcessCsv {
	/**
	 * The ean to add the vertices to
	 */
	private PeriodicEAN ean;

	/**
	 * Create a new builder for the given ean
	 * @param ean the ean to add the vertices to
	 */
	public PeriodicEANVertexBuilder(PeriodicEAN ean){
		this.ean = ean;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 7){
			throw new IllegalArgumentException("The given arguments are not a valid periodic event string, since args" +
					".length!=7");
		}
		int eventId = Integer.parseInt(args[0].trim());
		String eventType = args[1].trim();
		eventType = eventType.substring(1, eventType.length()-1);
		int stopId = Integer.parseInt(args[2].trim());
		int lineId = Integer.parseInt(args[3].trim());
		double numberOfPassengers = Double.parseDouble(args[4].trim());
		PTNVertex stop = ean.getPTN().getVertex(stopId);
		PeriodicEANVertex vertex = new PeriodicEANVertex(eventId, eventType, stop, lineId, numberOfPassengers);
		ean.addVertex(vertex);
	}
}
