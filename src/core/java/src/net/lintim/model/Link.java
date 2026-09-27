package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;

/**
 * A class representing an edge in a public transportation network (PTN).
 *
 * This class will contain all information that normally associated with a PTN edge in the LinTim context, i.e.,
 * structural information as well as some passenger data.
 */
public class Link implements Edge<Stop> {

    protected int linkId;
    protected Stop leftStop;
    protected Stop rightStop;
    protected double length;
    protected int lowerBound;
    protected int upperBound;
    protected double load;
    protected int lowerFrequencyBound;
    protected int upperFrequencyBound;
    protected int headway;
    protected boolean directed;

    /**
     * Create a new Link, i.e., an edge in a Public Transportation Network.
     *
     * @param linkId     the id of the link, i.e., the id to reference the link. Needs to be unique for a given graph
     * @param leftStop   the left stop of the edge. This is the source of the link, if the edge is directed
     * @param rightStop  the right stop of the edge. This is the target of the link if the edge is directed
     * @param length     the length of the link, given in kilometers
     * @param lowerBound the lowerBound of the link, i.e., the minimal time in minutes, a vehicle needs to
     *                   traverse the edge
     * @param upperBound the upperBound of the link, i.e., the maxmimal time in minutes, a vehicle needs to
     *                   traverse the edge
     * @param directed   whether the link is directed
     */
    public Link(int linkId, Stop leftStop, Stop rightStop, double length, int lowerBound, int upperBound, boolean
        directed) {
        this.linkId = linkId;
        this.leftStop = leftStop;
        this.rightStop = rightStop;
        this.length = length;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.headway = 0;
        this.directed = directed;
    }

    @Override
    public int getId() {
        return linkId;
    }

    @Override
    public void setId(int id) {
        this.linkId = id;
    }

    @Override
    public Stop getLeftNode() {
        return leftStop;
    }

    @Override
    public Stop getRightNode() {
        return rightStop;
    }

    @Override
    public boolean isDirected() {
        return directed;
    }

    /**
     * Get the length of the link, given in kilometers.
     *
     * @return the length
     */
    public double getLength() {
        return length;
    }

    /**
     * Get the lowerBound of the link, i.e., the minimal time in time units, a vehicle needs to  traverse the edge.
     *
     * @return the lower bound
     */
    public int getLowerBound() {
        return lowerBound;
    }

    /**
     * Get the upperBound of the link, i.e., the maximal time in time units, a vehicle needs to traverse the edge.
     *
     * @return the upper bound
     */
    public int getUpperBound() {
        return upperBound;
    }

    /**
     * Get the load of the link, i.e., how many passengers traverse this link in the given period.
     *
     * @return the load
     */
    public double getLoad() {
        return load;
    }


    /**
     * Set the load of the link, i.e., how many passengers traverse this link in the given period.
     *
     * @param load the new load
     */
    public void setLoad(double load) {
        this.load = load;
    }

    /**
     * Get the lower frequency bound on the link, i.e., the minimal number of times a vehicle needs to traverse this
     * link in a given period to serve the load.
     *
     * @return the lower frequency bound
     */
    public int getLowerFrequencyBound() {
        return lowerFrequencyBound;
    }

    /**
     * Set the lower frequency bound on the link, i.e., the minimal number of times a vehicle needs to traverse this
     * link in a given period to serve the load.
     *
     * @param lowerFrequencyBound the lower frequency bound
     */
    public void setLowerFrequencyBound(int lowerFrequencyBound) {
        this.lowerFrequencyBound = lowerFrequencyBound;
    }

    /**
     * Get the upper frequency bound on the link, i.e., the maximal number of times a vehicle may traverse this link
     * in a given period.
     *
     * @return the upper frequency bound
     */
    public int getUpperFrequencyBound() {
        return upperFrequencyBound;
    }

    /**
     * Set the upper frequency bound on the link, i.e., the maximal number of times a vehicle may traverse this link
     * in a given period.
     *
     * @param upperFrequencyBound the upper frequency bound
     */
    public void setUpperFrequencyBound(int upperFrequencyBound) {
        this.upperFrequencyBound = upperFrequencyBound;
    }

    /**
     * Set all information regarding the passenger load for the link.
     *
     * @param load                the new load of the link, i.e., how many passengers traverse this link in the given
     *                            period
     * @param lowerFrequencyBound the lower frequency bound on the link, i.e., the minimal number of times a vehicle
     *                            needs to traverse this link in a given period to serve the load
     * @param upperFrequencyBound the upper frequency bound on the link, i.e., the maximal number of times a vehicle
     *                            may traverse this link in a given period
     */
    public void setLoadInformation(double load, int lowerFrequencyBound, int upperFrequencyBound) {
        setLoad(load);
        setLowerFrequencyBound(lowerFrequencyBound);
        setUpperFrequencyBound(upperFrequencyBound);
    }

    /**
     * Get the headway of the stop. This is the minimal time needed between two vehicle that serve this stop. Given
     * in minutes. Is initially set to 0.
     *
     * @return the headway of the stop
     */
    public int getHeadway() {
        return headway;
    }

    /**
     * Set the headway of the stop. This is the minimal time needed between two vehicle that serve this stop. Should
     * be given in minutes.
     *
     * @param headway the new headway
     */
    public void setHeadway(int headway) {
        this.headway = headway;
    }

    /**
     * Return a string array, representing the link for a LinTim csv file.
     *
     * @return the csv representation of this link
     */
    public String[] toCsvStrings() {
        return toCsvStrings(1);
    }

    /**
     * Return a string array representing the link for a LinTim csv file.
     * @param conversionFactorLength the length is divided by this number
     * @return the csv representation of this link
     */
    public String[] toCsvStrings(double conversionFactorLength) {
        return new String[]{
            String.valueOf(getId()),
            String.valueOf(getLeftNode().getId()),
            String.valueOf(getRightNode().getId()),
            CsvWriter.shortenDecimalValueForOutput(getLength() / conversionFactorLength),
            String.valueOf(getLowerBound()),
            String.valueOf(getUpperBound())
        };
    }

    /**
     * Return a string array, representing the link for a LinTim csv load file
     *
     * @return the csv load representation of this link
     */
    public String[] toCsvLoadStrings() {
        return new String[]{
            String.valueOf(getId()),
            CsvWriter.shortenDecimalValueForOutput(getLoad()),
            String.valueOf(getLowerFrequencyBound()),
            String.valueOf(getUpperFrequencyBound())
        };
    }

    /**
     * Return a string array, representing the link for a LinTim csv headway file.
     *
     * @return the csv headway representation of this link
     */
    public String[] toCsvHeadwayStrings() {
        return new String[]{
            String.valueOf(getId()),
            String.valueOf(getHeadway())
        };
    }

    @Override
    public String toString(){
        return "Link " + Arrays.toString(toCsvStrings());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Link link = (Link) o;

        if (linkId != link.linkId) return false;
        if (Double.compare(link.length, length) != 0) return false;
        if (lowerBound != link.lowerBound) return false;
        if (upperBound != link.upperBound) return false;
        if (Double.compare(link.load, load) != 0) return false;
        if (lowerFrequencyBound != link.lowerFrequencyBound) return false;
        if (upperFrequencyBound != link.upperFrequencyBound) return false;
        if (headway != link.headway) return false;
        if (directed != link.directed) return false;
        //The equality check w.r.t the stops is dependent on the directed attribute
        if (directed) {
            if (leftStop == null && rightStop == null) {
                return link.leftStop == null && link.rightStop == null;
            } else if (leftStop == null) {
                return link.leftStop == null && rightStop.equals(link.rightStop);
            } else if (rightStop == null) {
                return leftStop.equals(link.leftStop) && link.rightStop == null;
            } else {
                return leftStop.equals(link.leftStop) && rightStop.equals(link.rightStop);
            }
        } else {
            if (leftStop == null && rightStop == null) {
                return link.leftStop == null && link.rightStop == null;
            } else if (leftStop == null) {
                return (link.leftStop == null && rightStop.equals(link.rightStop))
                    || (link.rightStop == null && rightStop.equals(link.leftStop));
            } else if (getRightNode() == null) {
                return (leftStop.equals(link.leftStop) && link.rightStop == null)
                    || (leftStop.equals(link.rightStop) && link.leftStop == null);
            } else {
                return (leftStop.equals(link.leftStop) && rightStop.equals(link.rightStop))
                    || (leftStop.equals(link.rightStop) && rightStop.equals(link.leftStop));
            }
        }

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = linkId;
        result = 31 * result + (leftStop != null ? leftStop.hashCode() : 0);
        result = 31 * result + (rightStop != null ? rightStop.hashCode() : 0);
        temp = Double.doubleToLongBits(getLength());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + getLowerBound();
        result = 31 * result + getUpperBound();
        result = 31 * result + getHeadway();
        result = 31 * result + (isDirected() ? 1 : 0);
        return result;
    }
}
