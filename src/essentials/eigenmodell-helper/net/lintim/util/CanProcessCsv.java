package net.lintim.util;

/**
 * Interface to process csv-lines
 */
public interface CanProcessCsv {
	/**
	 * Take the given csv line contents and process them in an appropriate way
	 * @param args the contents of a csv line
	 */
	void processCsv(String[] args);
}
