package net.lintim.util;

import net.lintim.model.PTN;
import net.lintim.model.PTNEdge;

/**
 * Class to process loads by csv-line contents and add them to a given ptn
 */
public class PTNLoadProcessor implements CanProcessCsv{
	/**
	 * The ptn to add the created edges to
	 */
	private final PTN ptn;

	/**
	 * Create a new builder for the given ptn
	 * @param ptn the ptn to add the read load to
	 */
	public PTNLoadProcessor(PTN ptn){
		this.ptn = ptn;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 4){
			throw new IllegalArgumentException("The given arguments are not a valid load string, since args.length!=4");
		}
		int edgeId = Integer.parseInt(args[0].trim());
		int load = Integer.parseInt(args[1].trim());
		int minFreq = Integer.parseInt(args[2].trim());
		int maxFreq = Integer.parseInt(args[3].trim());
		PTNEdge edge = ptn.getEdge(edgeId);
		edge.setLoadFileContent(load, minFreq, maxFreq);
	}
}
