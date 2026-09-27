package net.lintim.util;

import net.lintim.exception.LinTimException;
import net.lintim.model.*;

import java.util.*;

/**
 * Class containing helper methods for the line planning.
 */
public class LinePlanningHelper {

    private static Logger logger = new Logger(LinePlanningHelper.class);

    /**
     * Determine the possible system frequencies based on the given config value. For config values smaller or equal
     * to 0, all possible system frequencies (without 1) are considered.
     * @param configCommonDivisor the common divisor given in the config
     * @return a list of all system frequencies to try
     */
    public static Set<Integer> determinePossibleSystemFrequencies(int configCommonDivisor, int periodLength) {
        Set<Integer> possibleSystemFrequencies = new HashSet<>();
        int temp = periodLength;
        if(configCommonDivisor <= 0) {
            for(int value : new int[]{2, 3, 5, 7, 11, 13}) {
                possibleSystemFrequencies.add(value);
            }
        }
        else {
            possibleSystemFrequencies.add(configCommonDivisor);
        }
        return possibleSystemFrequencies;
    }

    /**
     * Get the frequencies of the given line concept. Will all be 0 for a line pool without frequencies
     * @param lineConcept the line concept to read the frequencies from
     * @return the frequencies of the lines, stored by their line ids
     */
    public static HashMap<Integer, Integer> getFrequencies(LinePool lineConcept) {
        HashMap<Integer, Integer> frequencies = new HashMap<>();
        for(Line line : lineConcept.getLines()) {
            frequencies.put(line.getId(), line.getFrequency());
        }
        return frequencies;
    }

    /**
     * Set the frequencies of the given linepool to the provided frequencies. There needs to be an entry for each
     * line id in the frequency map, otherwise this methods will throw a {@link LinTimException}. Already existing
     * line frequencies will be overwritten.
     * @param lineConcept the line concept to write the lines to
     * @param frequencies the frequencies to set. Needs to contain an entry for each line id.
     */
    public static void setFrequencies(LinePool lineConcept, HashMap<Integer, Integer> frequencies) {
        for(Line line : lineConcept.getLines()) {
            // First check if we have a fixed line, i.e., a line that already has a frequency
            if (line.getFrequency() > 0) {
                continue;
            }
            try {
                line.setFrequency(frequencies.get(line.getId()));
            }
            catch (NullPointerException e) {
                logger.fatal("Should read frequency for line " + line.getId() + " but it is not " +
                    "present!");
                throw new LinTimException(e.getMessage());
            }
        }
    }

    public static void preprocessForFixedLines(LinePool allLines, Map<Line, Integer> capacities, int unfixedCapacity) {
        for (Line line : capacities.keySet()) {
            for (Link link : line.getLinePath().getEdges()) {
                int newBound = (link.getLowerFrequencyBound()*unfixedCapacity - line.getFrequency()*capacities.get(line))/unfixedCapacity;
                logger.debug("Set lower bound of " + link + " to max of 0 and " + newBound + ", old bound was " + link.getLowerFrequencyBound());
                link.setLowerFrequencyBound(Math.max(0, newBound));
                logger.debug("Its now " + link.getLowerFrequencyBound());
            }
            allLines.removeLine(line.getId());
        }
    }

    public static void postProcessForFixedLines(LinePool allLines, Collection<Line> fixedLines) {
        for (Line fixedLine : fixedLines) {
            allLines.addLine(fixedLine);
        }
    }
}
