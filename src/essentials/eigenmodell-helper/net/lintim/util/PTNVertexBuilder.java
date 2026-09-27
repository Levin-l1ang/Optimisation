package net.lintim.util;

import net.lintim.model.PTN;
import net.lintim.model.PTNVertex;

/**
 * Class to create vertices by csv-line contents and add them to a given ptn
 */
public class PTNVertexBuilder implements CanProcessCsv{
	/**
	 * The ptn to add the created edges to
	 */
	PTN ptn;

	/**
	 * Create a new builder for the given ptn
	 * @param ptn the ptn to add the created vertices to
	 */
	public PTNVertexBuilder(PTN ptn){this.ptn = ptn;}

	@Override
	public void processCsv(String[] args){
		if(args.length != 5){
			throw new IllegalArgumentException("The given arguments are not a valid stop string, since args.length!=5");
		}
		int id = Integer.parseInt(args[0].trim());
		String name = args[2].trim();
		PTNVertex vertex = new PTNVertex(id, name);
		ptn.addVertex(vertex);
	}

}
