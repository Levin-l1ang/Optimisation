package net.lintim.model;

import java.util.Arrays;

/**
 * A class to represent demand point, i.e., the location and the number of people in a city that serves
 * as demand.
 */
public class DemandPoint {
    protected int id;
    protected String shortName;
    protected String longName;
    protected double xCoordinate;
    protected double yCoordinate;
    protected int demand;

    /**
     * Constructor of a demand point.
     *
     * @param id          id of the demand point
     * @param shortName   short name of the demand point
     * @param longName    long name of the demand point
     * @param xCoordinate x coordinate of the demand point
     * @param yCoordinate y coordinate of the demand point
     * @param demand      demand at the demand point
     */
    public DemandPoint(int id, String shortName, String longName, double xCoordinate, double yCoordinate, int demand) {
        this.id = id;
        this.shortName = shortName;
        this.longName = longName;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.demand = demand;
    }

    /**
     * Gets the id of the demand point.
     *
     * @return id of the demand point
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the short name of the demand point.
     *
     * @return the short name of the demand point
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * Gets the long name of the demand point.
     *
     * @return the long name of the demand point
     */
    public String getLongName() {
        return longName;
    }

    /**
     * Gets the x coordinate of the demand point (in some planar coordinate system with unit km).
     *
     * @return the x coordinate of the demand point.
     */
    public double getxCoordinate() {
        return xCoordinate;
    }

    /**
     * Gets the y coordinate of the demand point (in some planar coordinate system with unit km).
     *
     * @return the y coordinate of the demand point
     */
    public double getyCoordinate() {
        return yCoordinate;
    }

    /**
     * Gets the demand at the demand point.
     *
     * @return the demand
     */
    public int getDemand() {
        return demand;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DemandPoint that = (DemandPoint) o;

        if (getId() != that.getId()) return false;
        if (Double.compare(that.getxCoordinate(), getxCoordinate()) != 0) return false;
        if (Double.compare(that.getyCoordinate(), getyCoordinate()) != 0) return false;
        if (getDemand() != that.getDemand()) return false;
        if (getShortName() != null ? !getShortName().equals(that.getShortName()) : that.getShortName() != null)
            return false;
        return getLongName() != null ? getLongName().equals(that.getLongName()) : that.getLongName() == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = getId();
        result = 31 * result + (getShortName() != null ? getShortName().hashCode() : 0);
        result = 31 * result + (getLongName() != null ? getLongName().hashCode() : 0);
        temp = Double.doubleToLongBits(getxCoordinate());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getyCoordinate());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + getDemand();
        return result;
    }

    /**
     * Create a csv array containing the values needed for a demand file in LinTim format
     * @return a representation of this demand in LinTim format
     */
    public String[] toCsvString(){
        return new String[]{
            String.valueOf(id),
            shortName,
            longName,
            String.valueOf(xCoordinate),
            String.valueOf(yCoordinate),
            String.valueOf(demand)
        };
    }

    @Override
    public String toString() {
        return "Demand " + Arrays.toString(toCsvString());
    }
}
