package net.lintim.model;

import net.lintim.util.CsvReader;
import net.lintim.util.ODProcessor;

import java.util.Arrays;

/**
 * Class representing an origin destination (OD) matrix for a public transportation system.
 */
public class OD {

	/**
	 * The size of the matrix
	 */
	private int size;

	/**
	 * The actual od matrix
	 */
	private int[][] matrix;

	/**
	 * Read the OD matrix from the given data folder. The folder has to contain a LinTim-formatted OD file, named "OD.giv"
	 * @param odFileName the name of the of file
	 */
	public OD(String odFileName){
		readOD(odFileName);
	}

	/**
	 * Read the OD matrix from the given file. Has to be in the LinTim-format
	 * @param dataFileName the name of the data file
	 */
	private void readOD(String dataFileName){
		size = (int) Math.sqrt(CsvReader.determineNumberOfLines(dataFileName));
		matrix = new int[size][size];
		CsvReader.readCsv(dataFileName, new ODProcessor(this));
	}

	/**
	 * Return the entry of a specific od entry given by the two parameters, i.e., the number of passengers that want
	 * to travel from origin to destination in the given demand period
	 * @param origin the origin
	 * @param destination the destination
	 * @return the od entry
	 */
	public int getOdEntry(int origin, int destination){
		if(origin > matrix.length || 0 >= origin || destination > matrix.length || 0 >= destination){
			throw new IllegalArgumentException("Invalid OD entry request for (" + origin + "," + destination + ")");
		}
		return matrix[origin-1][destination-1];
	}

	/**
	 * Set the value of a specific od entry given by the first two parameters, i.e., the number of passengers that want
	 * to travel from origin to destination in the given demand period. A maybe existing entry is overwritten.
	 * @param origin the origin
	 * @param destination the destination
	 * @param value the value of the new entry
	 */
	public void setOdEntry(int origin, int destination, int value){
		if(origin > matrix.length || 0 >= origin || destination > matrix.length || 0 >= destination){
			throw new IllegalArgumentException("Invalid OD entry set request for (" + origin + "," + destination + ")");
		}
		matrix[origin-1][destination-1] = value;
	}

	@Override
	public String toString(){
		StringBuilder returnString = new StringBuilder();
		returnString.append("[");
		for(int[] matrixRow : matrix){
			for(int matrixEntry : matrixRow){
				returnString.append(" ").append(matrixEntry);
			}
			returnString.append("\n");
		}
		return returnString.toString();
	}

	/**
	 * Return the size of the od matrix
	 * @return the size
	 */
	public int getSize(){
		return size;
	}

	@Override
	public boolean equals(Object other){
		if(other == null){
			return false;
		}
		if(other == this){
			return true;
		}
		if(!(other instanceof OD)){
			return false;
		}
		OD otherOD = (OD) other;
		return Arrays.deepEquals(otherOD.matrix, this.matrix);
	}
}
