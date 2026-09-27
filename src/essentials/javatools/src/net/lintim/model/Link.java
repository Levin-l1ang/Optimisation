package net.lintim.model;


/**
 * Container class for a link between two {@link Station}s in a public
 * transportation network (PTN).
 */

public class Link {
    private Integer index;
    private Station fromStation;
    private Station toStation;

    private Double length;
    private Double lowerBound;
    private Double upperBound;
    private Double headway;
    private Double load;
    private Integer lowerFrequency;
    private Integer upperFrequency;

    private Boolean undirected;
    private Link undirectedCounterpart;
    private Link undirectedRepresentative;

    /**
     * Redirects to {@link #Link(Boolean, Integer, Station, Station, Double,
     * Double, Double, Double, Integer, Integer, Double)} and sets the last four
     * arguments, i.e. load, lower frequency, upper frequency and headway to
     * null.
     *
     * @param undirected Whether or not the link is undirected.
     * @param index The link index.
     * @param fromStation The link from station.
     * @param toStation The link to station.
     * @param length The link length in meters.
     * @param lowerBound The upper bound for drive durations on the link.
     * @param upperBound The lower bound for drive durations on the link.
     */
    public Link(Boolean undirected, Integer index, Station fromStation,
            Station toStation, Double length, Double lowerBound,
            Double upperBound) {

        this(undirected, index, fromStation, toStation, length, lowerBound,
                upperBound, null, null, null, null);
    }

    /**
     * Constructs a new link.
     *
     * @param undirected Whether or not the link is undirected.
     * @param index The link index.
     * @param fromStation The link from station.
     * @param toStation The link to station.
     * @param length The link length in meters.
     * @param lowerBound The upper bound for drive durations on the link.
     * @param upperBound The lower bound for drive durations on the link.
     * @param load The number of passengers traveling on the link.
     * @param lowerFrequency The link lower frequency,
     * i.e. the minimal number of vehicles.
     * @param upperFrequency The link upper frequency,
     * i.e. the maximal number of vehicles.
     * @param headway The link headway, i.e. the
     * minimal time distance two vehicles using link must have.
     */
    public Link(Boolean undirected, Integer index, Station fromStation,
            Station toStation, Double length, Double lowerBound,
            Double upperBound, Double load, Integer lowerFrequency,
            Integer upperFrequency, Double headway) {

        this(undirected, null, index, fromStation, toStation, length,
                lowerBound, upperBound, load, lowerFrequency, upperFrequency,
                headway);
    }

    private Link(Boolean undirected, Link undirectedCounterpart, Integer index,
            Station fromStation, Station toStation, Double length,
            Double lowerBound, Double upperBound, Double load,
            Integer lowerFrequency, Integer upperFrequency, Double headway) {

        this.index = index;
        this.undirected = undirected;

        if (undirected) {
            if (undirectedCounterpart == null) {
                this.undirectedCounterpart = new Link(true, this, index,
                        toStation, fromStation, length, lowerBound, upperBound,
                        load, lowerFrequency, upperFrequency, headway);
                this.undirectedRepresentative = this;
            } else {
                this.undirectedCounterpart = undirectedCounterpart;
                this.undirectedRepresentative = undirectedCounterpart;
            }
        }
        else{
            this.undirectedRepresentative = this;
        }

        this.fromStation = fromStation;
        this.toStation = toStation;

        this.length = length;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.headway = headway;
        this.load = load;
        this.lowerFrequency = lowerFrequency;
        this.upperFrequency = upperFrequency;

    }

    // =========================================================================
    // === Single request comfort functions ====================================
    // =========================================================================
    public Boolean isUndirected() {
        return undirected;
    }

    public Boolean isUndirectedRepresentative() {
        return this == undirectedRepresentative;
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setLoad(Double load) {
        this.load = load;
    }

    public void setLowerFrequency(Integer lowerFrequency) {
        this.lowerFrequency = lowerFrequency;
    }

    public void setUpperFrequency(Integer upperFrequency) {
        this.upperFrequency = upperFrequency;
    }

    public void setHeadway(Double headway) {
        this.headway = headway;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setLowerBound(Double lowerBound) {
        this.lowerBound = lowerBound;
    }

    public void setUpperBound(Double upperBound) {
        this.upperBound = upperBound;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Integer getIndex() {
        return index;
    }

    public Station getFromStation() {
        return fromStation;
    }

    public Station getToStation() {
        return toStation;
    }

    public Double getLength() {
        return length;
    }

    public Double getLowerBound() {
        return lowerBound;
    }

    public Double getUpperBound() {
        return upperBound;
    }

    public Double getLoad() {
        return load;
    }

    public Integer getLowerFrequency() {
        return lowerFrequency;
    }

    public Integer getUpperFrequency() {
        return upperFrequency;
    }

    public Double getHeadway() {
        return headway;
    }

    public Link getUndirectedCounterpart() {
        return undirectedCounterpart;
    }

    public Link getUndirectedRepresentative() {
        return undirectedRepresentative;
    }

    // =========================================================================
    // === Other ===============================================================
    // =========================================================================

    protected String javaId() {
        return super.toString();
    }

    @Override
    public String toString() {
        return "   javaId index fStId tStId length lBound"
                + " uBound hway load lFreq uFreq uDir uDirCJId\n"
                + String.format(" %8s", javaId().split("@")[1])
                + String.format(" %5d", index)
                + String.format(" %5d", fromStation.getIndex())
                + String.format(" %5d", toStation.getIndex())
                + String.format(" %4.1f", length)
                + String.format(" %4.1f", lowerBound)
                + String.format(" %4.1f", upperBound)
                + String.format(" %4.1f", headway)
                + String.format(" %4.1f", load)
                + String.format(" %5d", lowerFrequency)
                + String.format(" %5d", upperFrequency)
                + String.format(" %5b", undirected)
                + String.format(" %8s", undirected ?
                        undirectedCounterpart.javaId().split("@")[1] : "---");
    }

}
