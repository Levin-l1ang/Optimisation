package net.lintim.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * File to process a csv file. Comments in the form of "#..." are trimmed from the end of the lines. Empty lines are
 * ignored. Other lines will be split by ";" and processed by a given Builder class.
 */
public class CsvReader {

	/**
	 * Read the file with the given name line by line and process each line with the given processor. The lines are
	 * trimmed for comments ("#...") and whitespaces before splitted by ";". The tokens are then given to the
	 * {@link CanProcessCsv#processCsv(String[])} method of the processor.
	 * @param fileName the name of the file to read
	 * @param processor the processor to process every line
	 */
	public static void readCsv(String fileName, CanProcessCsv processor){
		File file = new File(fileName);
		try{
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line;
			while((line = reader.readLine()) != null){
				//First trim the comments, if there are any
				int position = line.indexOf("#");
				if(position != -1){
					line = line.substring(0, position);
				}
				line = line.trim();
				//Empty lines can be ignored
				if(line.isEmpty()){
					continue;
				}
				String[] tokens = line.split(";");
				//Let the processor process the line
				processor.processCsv(tokens);
			}
		} catch (IOException e){
			System.err.println("Error while reading file " + fileName);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Read the given file and determine the number of lines, without empty lines, lines only containing whitespaces
	 * and lines only containing whitespaces and comments
	 * @param fileName the name of the file
	 * @return the number of lines with content of this file
	 */
	public static int determineNumberOfLines(String fileName){
		File file = new File(fileName);
		int numberOfLines = 0;
		try{
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line;
			while((line = reader.readLine()) != null){
				//First trim the comments, if there are any
				int position = line.indexOf("#");
				if(position != -1){
					line = line.substring(0, position);
				}
				line = line.trim();
				//Empty lines can be ignored
				if(line.isEmpty()){
					continue;
				}
				numberOfLines++;
			}
		} catch (IOException e){
			System.err.println("Error while reading file " + fileName);
			throw new RuntimeException(e);
		}
		return numberOfLines;
	}
}
