package net.lintim.model;

import net.lintim.exception.DataInconsistentException;

import java.beans.PropertyChangeListener;
import java.util.LinkedHashSet;

/** Container class for a station in a public transportation network (PTN).
 */

public class Station implements Comparable<Station>{
    // =========================================================================
    // === Fields ==============================================================
    // =========================================================================
    // --- Properties ----------------------------------------------------------
    private Integer index;
    private String shortName;
    private String longName;
    private Double x_coordinate;
    private Double y_coordinate;
    private Boolean vehicleCanTurn;

    // --- Network Structure ---------------------------------------------------
    LinkedHashSet<Link> outgoingLinks = new LinkedHashSet<Link>();
    LinkedHashSet<Link> incomingLinks = new LinkedHashSet<Link>();

    LinkedHashSet<Station> reachableStations = new LinkedHashSet<Station>();

    // =========================================================================
    // === Constructors ========================================================
    // =========================================================================
    /**
     * Constructor.
     */
    public Station(Integer index, String shortName, String longName,
            Boolean vehiclesCanTurn){

        this.index = index;
        this.shortName = shortName;
        this.longName = longName;
        this.x_coordinate=0.;
        this.y_coordinate=0.;
        this.vehicleCanTurn = vehiclesCanTurn;
    }
    
    public Station(Integer index, String shortName, String longName,
        Boolean vehiclesCanTurn, double x_coordinate, double y_coordinate){

    this.index = index;
    this.shortName = shortName;
    this.longName = longName;
    this.x_coordinate=x_coordinate;
    this.y_coordinate=y_coordinate;
    this.vehicleCanTurn = vehiclesCanTurn;
}

    // =========================================================================
    // === Network Structure Operators =========================================
    // =========================================================================
    /** PLEASE DO NOT USE THIS METHOD! It is for internal use through the
     * {@link PublicTransportationNetwork} only. JavaBeans will not help us,
     * since {@link PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)}
     * cannot throw exceptions. We would need custom beans.
     *
     * @param link The outgoing link to add.
     * @return true if link could be added as new, false if it already existed.
     */
    public boolean addOutgoingLink(Link link) throws DataInconsistentException{
        if(link.getFromStation() != this){
            throw new DataInconsistentException("addOutgoingLink: from " +
                    "station does not match this station");
        }
        reachableStations.add(link.getToStation());
        return outgoingLinks.add(link);
    }

    /** PLEASE DO NOT USE THIS METHOD! It is for internal use through the
     * {@link PublicTransportationNetwork} only. JavaBeans will not help us,
     * since {@link PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)}
     * cannot throw exceptions. We would need custom beans.
     *
     * @param link The incoming link to add.
     * @return true if link could be added as new, false if it already existed.
     */
    public boolean addIncomingLink(Link link) throws DataInconsistentException{
        if(link.getToStation() != this){
            throw new DataInconsistentException("addIncomingLink: from " +
                    "station does not match this station");
        }
        reachableStations.add(link.getFromStation());
        return incomingLinks.add(link);
    }

    /** PLEASE DO NOT USE THIS METHOD! It is for internal use through the
     * {@link PublicTransportationNetwork} only. JavaBeans will not help us,
     * since {@link PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)}
     * cannot throw exceptions. We would need custom beans.
     *
     * @param link The outgoing link to remove.
     * @return true if link could be removed, false if it did not exist.
     */
    public boolean removeOutgoingLink(Link link){
        return outgoingLinks.remove(link);
    }

    /** PLEASE DO NOT USE THIS METHOD! It is for internal use through the
     * {@link PublicTransportationNetwork} only. JavaBeans will not help us,
     * since {@link PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)}
     * cannot throw exceptions. We would need custom beans.
     *
     * @param link The incoming link to remove.
     * @return true if link could be removed, false if it did not exist.
     */
    public boolean removeIncomingLink(Link link){
        return incomingLinks.remove(link);
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public void setVehicleCanTurn(Boolean vehiclesCanTurn) {
        this.vehicleCanTurn = vehiclesCanTurn;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Integer getIndex() {
        return index;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLongName() {
        return longName;
    }
    
    public double getXCoordinate(){
    	return x_coordinate;
    }
    
    public double getYCoordinate(){
    	return y_coordinate;
    }

    public Boolean getVehicleCanTurn() {
        return vehicleCanTurn;
    }

    public LinkedHashSet<Link> getOutgoingLinks() {
        return outgoingLinks;
    }

    public LinkedHashSet<Link> getIncomingLinks() {
        return incomingLinks;
    }

    public LinkedHashSet<Station> getReachableStations() {
        return reachableStations;
    }
    // =========================================================================
    // === Other ===============================================================
    // =========================================================================
    protected String javaId() {
        return super.toString();
    }

    @Override
    public String toString() {
        return "   javaId  index  sName  lName  vCanTurn\n"
        + String.format(" %8s", javaId().split("@")[1])
        + String.format("  %5d", index)
        + String.format("  %5s", shortName.substring(0, Math.min(5, shortName.length())))
        + String.format("  %5s", longName.substring(0, Math.min(5, longName.length())))
        + String.format("  %8b", vehicleCanTurn);
    }

    @Override
    public int compareTo(Station o) {
        return index-o.index;
    }
    
  //Distance-------------------------------------------------------------------------
    public static double distance(Station a, Station b){
    	double diff_x=(a.getXCoordinate()-b.getXCoordinate());
    	double diff_y=(a.getYCoordinate()-b.getYCoordinate());
    	return Math.sqrt(diff_x*diff_x+diff_y*diff_y);
    }

}
