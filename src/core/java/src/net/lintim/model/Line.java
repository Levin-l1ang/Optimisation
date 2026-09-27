package net.lintim.model;

import net.lintim.io.CsvWriter;
import net.lintim.model.impl.LinkedListPath;
import net.lintim.util.Logger;

import java.util.Arrays;

/**
 * A class for representing a line as path of Stop and Link .
 */
public class Line {
    protected int id;
    protected double length;
    protected double cost;
    protected int frequency;
    protected Path<Stop, Link> linePath;

    private static Logger logger = new Logger(Line.class);

    /**
     * Constructor for a new, empty line.
     *
     * @param id       id of the line
     * @param directed whether the links are directed
     */
    public Line(int id, boolean directed) {
        this.id = id;
        length = 0;
        cost = 0;
        frequency = 0;
        linePath = new LinkedListPath<>(directed);
    }

    /**
     * Constructor for a new line with fixed costs. Use this constructor to create a line and compute its length
     * during the process.
     *
     * @param id        line id
     * @param fixedCost fixed costs of creating a line
     * @param directed  whether the links are directed
     */
    public Line(int id, double fixedCost, boolean directed) {
        this.id = id;
        this.cost = fixedCost;
        linePath = new LinkedListPath<>(directed);
    }

    /**
     * Constructor for a line with given information.
     *
     * @param id        line id
     * @param length    length of the line
     * @param cost      cost of the line
     * @param frequency frequency of the line
     * @param linePath  line path belonging to the line
     */
    public Line(int id, double length, double cost, int frequency, Path<Stop, Link> linePath) {
        this.id = id;
        this.length = length;
        this.cost = cost;
        this.frequency = frequency;
        this.linePath = linePath;
    }

    /**
     * Private function to add a new link to a line.
     *
     * @param link                 link to add
     * @param computeCost whether the cost of the line should be adapted
     * @param factorCostLength     factor of the cost depending on the length of the line
     * @param factorCostLink       factor of the cost depending on the number of links in the line
     * @return whether the link could be added to the line
     */
    private boolean addLink(Link link, boolean computeCost, double factorCostLength, double factorCostLink) {
        if (linePath.getNodes().contains(link.getLeftNode()) && linePath.getNodes().contains(link.getRightNode())) {
            logger.warn("Line " + getId() + " now contains a loop, closed by link " + link.getId() + ". " +
                "This may " +
                "create problems in the LinTim algorithms!");
        }
        boolean returnValue = linePath.addLast(link);
        if (returnValue) {
            length += link.getLength();
            if (computeCost) {
                cost += link.getLength()*factorCostLength + factorCostLink;
            }
        }
        return returnValue;
    }

    /**
     * Method to add a new link to the line without changing the length or the cost of the line.
     *
     * @param link link to add
     * @return whether the link could be added to the line
     */
    public boolean addLink(Link link) {
        return addLink(link, false, 0, 0);
    }

    /**
     * Method to add a new link to the line and factorCostLink and factorCostLength*length to the line cost.
     *
     * @param link             link to add
     * @param factorCostLength factor of the cost depending on the length of the line
     * @param factorCostLink   factor of the cost depending on the number of links in the line
     * @return whether the link could be added to the line
     */
    public boolean addLink(Link link, double factorCostLength, double factorCostLink) {
        return addLink(link, true, factorCostLength, factorCostLink);
    }

    /**
     * Gets the id of the line
     *
     * @return line id
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the length of the line.
     *
     * @return length of the line
     */
    public double getLength() {
        return length;
    }

    /**
     * Gets the cost of the line.
     *
     * @return line cost
     */
    public double getCost() {
        return cost;
    }

    /**
     * Gets the frequency of the line.
     *
     * @return frequency of the line
     */
    public int getFrequency() {
        return frequency;
    }

    /**
     * Gets the path belonging to the line.
     *
     * @return the path belonging to the line
     */
    public Path<Stop, Link> getLinePath() {
        return linePath;
    }

    /**
     * Sets the frequency of the line.
     *
     * @param frequency line frequency
     */
    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    /**
     * Sets the length of the line.
     *
     * @param length length of the line
     */
    public void setLength(double length) {
        this.length = length;
    }

    /**
     * Sets the cost of a line
     *
     * @param cost cost of the line
     */
    public void setCost(double cost) {
        this.cost = cost;
    }

    /**
     * Return a string array, representing the line for a LinTim pool cost csv file
     * @return the csv cost representation of this line
     */
    public String[] toLineCostCsvStrings(double conversionFactorLength){
        return new String[] {
            String.valueOf(getId()),
            CsvWriter.shortenDecimalValueForOutput(getLength()/conversionFactorLength),
            CsvWriter.shortenDecimalValueForOutput(getCost())
        };
    }
    /**
     * Return a string array, representing the line for a LinTim pool cost csv file
     * @return the csv cost representation of this line
     */
    public String[] toLineCostCsvStrings(){
        return toLineCostCsvStrings(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Line line = (Line) o;

        if (getId() != line.getId()) return false;
        if (Double.compare(line.getLength(), getLength()) != 0) return false;
        if (Double.compare(line.getCost(), getCost()) != 0) return false;
        return getLinePath() != null ? getLinePath().equals(line.getLinePath()) : line.getLinePath() == null;
    }

    @Override
    public int hashCode() {
        return getId();
    }

    @Override
    public String toString() {
        return "Line " + Arrays.toString(toLineCostCsvStrings()) + ", Path " + linePath;
    }
}
