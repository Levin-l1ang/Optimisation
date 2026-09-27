package net.lintim.util;

import net.lintim.model.PTN;
import net.lintim.model.PTNEdge;
import net.lintim.model.PTNVertex;


/**
 * Class to create edges by csv-line contents and add them to a given ptn
 */
public class PTNEdgeBuilder implements CanProcessCsv{

	/**
	 * The ptn to add the created edges to
	 */
	private PTN ptn;

	/**
	 * Create a new builder for the given ptn
	 * @param ptn the ptn to add the created edges to
	 */
	public PTNEdgeBuilder(PTN ptn){
		this.ptn = ptn;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 6){
			throw new IllegalArgumentException("The given arguments are not a valid edge string, since args.length!=6");
		}
		int edgeId = Integer.parseInt(args[0].trim());
		int sourceId = Integer.parseInt(args[1].trim());
		int targetId = Integer.parseInt(args[2].trim());
		double length = Double.parseDouble(args[3].trim());
		int minTravelTime = Integer.parseInt(args[4].trim()) * 60;
		int maxTravelTime = Integer.parseInt(args[5].trim()) * 60;
		PTNVertex source = ptn.getVertex(sourceId);
		PTNVertex target = ptn.getVertex(targetId);
		PTNEdge edge = new PTNEdge(edgeId, source, target, length, minTravelTime, maxTravelTime);
		ptn.addEdge(edge);
	}
}
