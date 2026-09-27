package net.lintim.util;

import net.lintim.model.OD;

/**
 */
public class ODProcessor implements CanProcessCsv{

	/**
	 * The od matrix to add the entries to
	 */
	private OD od;

	/**
	 * Create a new class to process csv contents
	 * @param od the od to add the od entries to
	 */
	public ODProcessor(OD od){
		this.od = od;
	}

	@Override
	public void processCsv(String[] args){
		if(args.length != 3){
			throw new IllegalArgumentException("The given arguments are not a valid od string, since args.length!=2");
		}
		int origin = Integer.parseInt(args[0].trim());
		int destination = Integer.parseInt(args[1].trim());
		int value = Integer.parseInt(args[2].trim());
		od.setOdEntry(origin, destination, value);
	}
}
