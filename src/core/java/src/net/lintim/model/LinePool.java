package net.lintim.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * A class to represent the line pool.
 */
public class LinePool {
    private HashMap<Integer, Line> pool;

    /**
     * Constructor of a new empty line pool.
     */
    public LinePool() {
        pool = new HashMap<>();
    }

    /**
     * Method to add a line, if not already a line with the same id is in the pool.
     *
     * @param line line to add
     * @return whether the line was added
     */
    public boolean addLine(Line line) {
        if (pool.containsKey(line.getId())) {
            return false;
        } else {
            pool.put(line.getId(), line);
            return true;
        }
    }

    /**
     * Method to remove line with given id, if it exists in pool.
     *
     * @param lineId id of the line to remove
     * @return whether a line was removed
     */
    public boolean removeLine(int lineId) {
        if (!pool.containsKey(lineId)) {
            return false;
        } else {
            pool.remove(lineId);
            return true;
        }
    }

    /**
     * Gets a collection of the lines. This is not a copy!
     *
     * @return a collection of the lines
     */
    public Collection<Line> getLines() {
        return pool.values();
    }

    /**
     * Gets the line for a given id or null if it is not in the pool.
     *
     * @param id id of the line to get
     * @return the line with id id or null if not in the pool
     */
    public Line getLine(int id) {
        return pool.get(id);
    }

    /**
     * Method to get a list of all lines with frequency > 0.
     *
     * @return a linked list of all lines with frequency > 0.
     */
    public LinkedList<Line> getLineConcept() {
        LinkedList<Line> lineConcept = new LinkedList<>();
        for (Line line : pool.values()) {
            if (line.getFrequency() > 0) {
                lineConcept.add(line);
            }
        }
        return lineConcept;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinePool linePool = (LinePool) o;

        return pool != null ? pool.equals(linePool.pool) : linePool.pool == null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("LineConcept: \n");
        for(Map.Entry<Integer, Line> lineEntry : pool.entrySet()){
            builder.append(lineEntry.getKey()).append(":").append(lineEntry.getValue());
        }
        return builder.toString();
    }
}
