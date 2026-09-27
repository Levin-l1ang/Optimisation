package net.lintim.model;

import net.lintim.exception.DataInconsistentException;
import net.lintim.util.SinglePair;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;

/**
 * Container class for a line in a {@link PublicTransportationNetwork}.
 */

// TODO complete property change stuff

public class Line {
    // =========================================================================
    // === JavaBeans ===========================================================
    // =========================================================================
    public static final String PROPERTY_ADD_STATION = "PropertyAddStation";
    public static final String PROPERTY_REMOVE_STATION = "PropertyRemoveStation";

    public static final String PROPERTY_ADD_LINK = "PropertyAddLink";
    public static final String PROPERTY_REMOVE_LINK = "PropertyRemoveLink";

    protected PropertyChangeSupport changes = new PropertyChangeSupport(this);

    // =========================================================================
    // === Fields ==============================================================
    // =========================================================================

    // --- Properties ----------------------------------------------------------
    private Integer index = null;
    private LinkedList<Station> stations = new LinkedList<Station>();
    private LinkedHashSet<Station> stationSet = new LinkedHashSet<Station>();
    private LinkedList<Link> links = new LinkedList<Link>();
    private Integer frequency = 1;
    private Double cost = null;
    private Double length = 0.0;

    private Boolean undirected;
    private Line undirectedCounterpart;
    private Line undirectedRepresentative;

    private LinkedHashMap<Station, LinkedList<SinglePair<Link>>> stationLinkMap =
        new LinkedHashMap<Station, LinkedList<SinglePair<Link>>>();

    // =========================================================================
    // === Constructors ========================================================
    // =========================================================================
    /**
     * Redirects to {@link #Line(Boolean, Integer, Integer)}, where the last
     * parameter, i.e. the frequency is one.
     *
     * @param undirected Whether or not the line is undirected.
     * @param index The line index.
     */
    public Line(Boolean undirected, Integer index) {
        this(undirected, index, 1);
    }

    /**
     * Constructor.
     *
     * @param undirected Whether or not the line is undirected.
     * @param index The line index.
     * @param frequency The line frequency.
     */
    public Line(Boolean undirected, Integer index, Integer frequency) {
        this(undirected, null, index, frequency);
    }

    private Line(Boolean undirected, Line undirectedCounterpart, Integer index,
            Integer frequency) {

        this.index = index;
        this.undirected = undirected;

        if (undirected) {
            if (undirectedCounterpart == null) {
                this.undirectedCounterpart = new Line(undirected, this, index,
                        frequency);
                this.undirectedRepresentative = this;
            } else {
                this.undirectedCounterpart = undirectedCounterpart;
                this.undirectedRepresentative = undirectedCounterpart;
            }
        }
        else {
            this.undirectedCounterpart = null;
            this.undirectedRepresentative = this;
        }

        this.frequency = frequency;
    }

    // =========================================================================
    // === Add and Remove ======================================================
    // =========================================================================
    /**
     * Redirects to {@link #addLinkEnd(Link)}.
     *
     * @param link The link to add at the end of the line.
     * @throws DataInconsistentException
     */
    public void addLink(Link link) throws DataInconsistentException {
        addLinkEnd(link, undirected);
    }

    /**
     * Adds a link at the end of the line.
     *
     * @param link The link to add.
     * @throws DataInconsistentException
     */
    public void addLinkEnd(Link link) throws DataInconsistentException {
        addLinkEnd(link, undirected);
    }

    private void addLinkEnd(Link link, Boolean mirror)
            throws DataInconsistentException {

        if (mirror) {

            if (links.size() >= 1) {
                if (link.getFromStation() != links.getLast().getToStation()) {
                    link = link.getUndirectedCounterpart();
                }

                if (links.size() == 1) {
                    if (link.getFromStation() != links.getLast().getToStation()) {
                        singleLinkFlip();
                    }

                    if (link.getFromStation() != links.getLast().getToStation()) {
                        link = link.getUndirectedCounterpart();
                    }

                }

            }

            this.undirectedCounterpart.addLinkBegin(link
                    .getUndirectedCounterpart(), false);
        }

        if (links.size() == 0) {
            initializeLine(link);
        } else {
            links.addLast(link);
            Station lastStation = stations.getLast();
            LinkedList<SinglePair<Link>> lastStationStationAdjacentLinks =
                stationLinkMap.get(lastStation);
            if(lastStationStationAdjacentLinks == null){
                throw new DataInconsistentException("last station has no " +
                        "adjacent links. This should never happen.");
            }
            SinglePair<Link> lastStationAdjacentLinks =
                lastStationStationAdjacentLinks.getLast();
            if (link.getFromStation() != lastStation) {
                throw new DataInconsistentException("line connection broken");
            }
            lastStationAdjacentLinks.second = link;
            Station toStation = link.getToStation();
            LinkedList<SinglePair<Link>> newLastStationStationAdjacentLinks =
                stationLinkMap.get(toStation);
            if(newLastStationStationAdjacentLinks == null){
                newLastStationStationAdjacentLinks = new LinkedList<SinglePair<Link>>();
                stationLinkMap.put(toStation, newLastStationStationAdjacentLinks);
            }
            newLastStationStationAdjacentLinks.addLast(new SinglePair<Link>(link, null));
            stations.addLast(toStation);
            stationSet.add(toStation);

//			if (stationSet.size() < stations.size()) {
//				throw new DataInconsistentException("loop created");
//			}
            changes.firePropertyChange(PROPERTY_ADD_STATION, null, toStation);
        }
        length += link.getLength();
    }

    /**
     * Adds a link at line begin.
     *
     * @param link The link to add.
     * @throws DataInconsistentException
     */
    public void addLinkBegin(Link link) throws DataInconsistentException {
        addLinkBegin(link, undirected);
    }

    private void addLinkBegin(Link link, Boolean mirror)
            throws DataInconsistentException {

        if (mirror) {
            if (links.size() >= 1) {
                if (link.getToStation() != links.getFirst().getFromStation()) {
                    link = link.getUndirectedCounterpart();
                }

                if (links.size() == 1) {
                    if (link.getToStation() != links.getFirst()
                            .getFromStation()) {
                        singleLinkFlip();
                    }

                    if (link.getToStation() != links.getFirst()
                            .getFromStation()) {
                        link = link.getUndirectedCounterpart();
                    }

                }

            }

            this.undirectedCounterpart.addLinkEnd(link
                    .getUndirectedCounterpart(), false);
        }

        if (links.size() == 0) {
            initializeLine(link);
        } else {
            links.addFirst(link);
            Station firstStation = stations.getFirst();
            LinkedList<SinglePair<Link>> firstStationStationAdjacentLinks =
                stationLinkMap.get(firstStation);
            if(firstStationStationAdjacentLinks == null){
                throw new DataInconsistentException("first station has no " +
                "adjacent links. This should never happen.");
            }
            if (link.getToStation() != firstStation) {
                throw new DataInconsistentException("line connection broken");
            }
            SinglePair<Link> firstStationAdjacentLinks =
                firstStationStationAdjacentLinks.getFirst();
            firstStationAdjacentLinks.first = link;
            Station fromStation = link.getFromStation();
            LinkedList<SinglePair<Link>> newFirstStationStationAdjacentLinks =
                stationLinkMap.get(fromStation);
            if(newFirstStationStationAdjacentLinks == null){
                newFirstStationStationAdjacentLinks = new LinkedList<SinglePair<Link>>();
                stationLinkMap.put(fromStation, newFirstStationStationAdjacentLinks);
            }
            newFirstStationStationAdjacentLinks.addFirst(new SinglePair<Link>(null, link));
            stations.addFirst(fromStation);
            stationSet.add(fromStation);
//            if (stationSet.size() < stations.size()) {
//                throw new DataInconsistentException("loop created");
//            }
            changes.firePropertyChange(PROPERTY_ADD_STATION, null, fromStation);
        }
        length += link.getLength();
    }

    protected Boolean removeStationInternal(Station toRemove, Link replacementLink,
            Boolean mirror) throws DataInconsistentException{

        throw new UnsupportedOperationException("removal of stations " +
                "currently not supported due to model changes. Code is " +
                "commented out, need fixiation and testing");

//        Boolean retval;
//
//        if(mirror){
//            Link mirroredLink = replacementLink == null ? null :
//                replacementLink.getUndirectedCounterpart();
//            retval = undirectedCounterpart.removeStationInternal(toRemove,
//                    mirroredLink, false);
//        }
//        else{
//            retval = false;
//        }
//
//        Pair<Link> attachedLinks = stationLinkMap.remove(toRemove);
//        Link l1 = attachedLinks.first;
//        Link l2 = attachedLinks.second;
//
//        if(l1 != null && l2 != null){
//            if(replacementLink == null){
//                throw new DataInconsistentException("replacement link is " +
//                        "null, but links must be replaced");
//            }
//            if(l1.getFromStation() != replacementLink.getFromStation()){
//                throw new DataInconsistentException("fromStation of " +
//                        "replacementLink does not match original fromStation");
//            }
//            if(l2.getToStation() != replacementLink.getToStation()){
//                throw new DataInconsistentException("toStation of " +
//                "replacementLink does not match original toStation");
//            }
//            ListIterator<Link> itr = links.listIterator();
//            // Do nothing, just forward to l1
//            while(itr.next() != l1){}
//            changes.firePropertyChange(PROPERTY_REMOVE_LINK, l1, null);
//            changes.firePropertyChange(PROPERTY_ADD_LINK, null, replacementLink);
//            itr.set(replacementLink);
//            length -= l1.getLength();
//            length += replacementLink.getLength();
//            itr.next();
//            changes.firePropertyChange(PROPERTY_REMOVE_LINK, l2, null);
//            length -= l2.getLength();
//            itr.remove();
//
//            stationLinkMap.get(l1.getFromStation()).second = replacementLink;
//            stationLinkMap.get(l2.getToStation()).first = replacementLink;
//
//            retval = true;
//
//        }
//        else{
//            // they can't be null at the same time, since else
//            // stations.size() == 2, which is forbidden in removeStation()
//            if(l1 == null){
//                stationLinkMap.get(l2.getToStation()).first = null;
//                changes.firePropertyChange(PROPERTY_REMOVE_LINK, l2, null);
//                links.remove(l2);
//                length -= l2.getLength();
//            }
//            else if(l2 == null){
//                stationLinkMap.get(l1.getFromStation()).second = null;
//                changes.firePropertyChange(PROPERTY_REMOVE_LINK, l1, null);
//                links.remove(l1);
//                length -= l1.getLength();
//            }
//
//        }
//
//        changes.firePropertyChange(PROPERTY_REMOVE_STATION, toRemove, null);
//        stations.remove(toRemove);
//        stationSet.remove(toRemove);
//
//        return retval;

    }

    /**
     * Returns whether this line is subline of <code>line</code>.
     *
     * @param line
     * @return
     */
    public boolean isSublineOf(Line line){
        Iterator<Link> itr1 = links.iterator();
        Iterator<Link> itr2 = line.getLinks().iterator();

        if(!itr1.hasNext()){
            return true;
        }

        Link toCompare = itr1.next();

        boolean startFound = false;

        while(itr2.hasNext()){
            if(itr2.next() == toCompare){
                startFound = true;
                if(!itr1.hasNext()){
                    return true;
                }

                toCompare = itr1.next();

            }
            else if(startFound){
                return false;
            }
        }

        return false;
    }

    public void removeStation(Station toRemove, Link replacementLink)
    throws DataInconsistentException{
        if(!stationSet.contains(toRemove)){
            throw new DataInconsistentException("station not contained in line");
        }

        if(stations.size() == 2){
            throw new DataInconsistentException("illegal line: one station, zero links");
        }

        removeStationInternal(toRemove, replacementLink, true);
    }

    // =========================================================================
    // === JavaBeans ===========================================================
    // =========================================================================
    public void addPropertyChangeListener(PropertyChangeListener listener){
        changes.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName,
            PropertyChangeListener listener) throws DataInconsistentException {

        if(propertyName == PROPERTY_ADD_STATION
                || propertyName == PROPERTY_REMOVE_STATION
                || propertyName == PROPERTY_ADD_LINK
                || propertyName == PROPERTY_REMOVE_LINK){
            changes.addPropertyChangeListener(propertyName, listener);
        }
        else{
            throw new DataInconsistentException("propertyName " + propertyName
                    + " not available");
        }

    }
    // =========================================================================
    // === Generic helper functions ============================================
    // =========================================================================
    private void initializeLine(Link link) {
        links.add(link);
        Station fromStation = link.getFromStation();
        Station toStation = link.getToStation();
        stations.add(fromStation);
        stations.add(toStation);
        stationSet.add(fromStation);
        stationSet.add(toStation);

        LinkedList<SinglePair<Link>> fromStationStationAdjacentLinks =
            new LinkedList<SinglePair<Link>>();
        fromStationStationAdjacentLinks.add(new SinglePair<Link>(null, link));
        stationLinkMap.put(fromStation, fromStationStationAdjacentLinks);

        LinkedList<SinglePair<Link>> toStationStationAdjacentLinks =
            new LinkedList<SinglePair<Link>>();
        toStationStationAdjacentLinks.add(new SinglePair<Link>(link, null));
        stationLinkMap.put(toStation, toStationStationAdjacentLinks);
    }

    private void clear() {
        stations.clear();
        stationSet.clear();
        links.clear();
        stationLinkMap.clear();
        length = 0.0;
    }

    private void singleLinkFlip() throws DataInconsistentException {
        Link link = links.getFirst();

        this.clear();
        undirectedCounterpart.clear();

        this.addLink(link.getUndirectedCounterpart());
    }

    // =========================================================================
    // === Single request comfort functions ====================================
    // =========================================================================
    public Boolean isUndirected() {
        return undirected;
    }

    public Boolean isUndirectedRepresentative(){
        return this == undirectedRepresentative;
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    public void setUndirectedCounterpart(Line undirectedCounterpart) {
        this.undirectedCounterpart = undirectedCounterpart;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Integer getIndex() {
        return index;
    }

    public LinkedList<Station> getStations() {
        return stations;
    }

    public LinkedList<Link> getLinks() {
        return links;
    }

    public LinkedList<SinglePair<Link>> getAdjacentLinks(Station station) {
        return stationLinkMap.get(station);
    }

    public LinkedHashSet<Station> getStationSet() {
        return stationSet;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public Line getUndirectedCounterpart() {
        return undirectedCounterpart;
    }

    public Double getCost() {
        return cost;
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double newLength){
        this.length = newLength;
    }

    public Line getUndirectedRepresentative() {
        return undirectedRepresentative;
    }

}
