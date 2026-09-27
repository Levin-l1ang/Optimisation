package net.lintim.model;

import net.lintim.exception.DataInconsistentException;
import net.lintim.util.BiLinkedHashMap;

import java.util.*;

/**
 * Implementation of a public transportation network (PTN). It is a graph that
 * consists of {@link Station}s as nodes and {@link Link}s as edges.
 */

public class PublicTransportationNetwork{

    protected LinkedHashSet<Station> stations = new LinkedHashSet<Station>();
    protected LinkedHashSet<Link> directedLinks = new LinkedHashSet<Link>();
    protected LinkedHashSet<Link> undirectedLinks = new LinkedHashSet<Link>();

    protected Integer smallestFreeDirectedLinkIndex = null;
    protected Integer smallestFreeUndirectedLinkIndex = null;
    protected Boolean isUndirected;

    protected BiLinkedHashMap<Station, Station, LinkedHashSet<Link>> adjacencyMatrix =
        new BiLinkedHashMap<Station, Station, LinkedHashSet<Link>>();
    protected LinkedHashMap<Integer, Station> indexStationMap =
        new LinkedHashMap<Integer, Station>();

    protected LinkedHashMap<Integer, Link> indexLinkMap =
        new LinkedHashMap<Integer, Link>();
    protected LinkedHashMap<Integer, Link> undirectedIndexLinkMap =
        new LinkedHashMap<Integer, Link>();
    protected LinkedHashMap<Link, Integer> undirectedLinkIndexMap =
        new LinkedHashMap<Link, Integer>();
    protected LinkedHashMap<Integer, Link> directedIndexLinkMap =
        new LinkedHashMap<Integer, Link>();
    protected LinkedHashMap<Link, Integer> directedLinkIndexMap =
        new LinkedHashMap<Link, Integer>();

    protected BiLinkedHashMap<Station, Station, LinkedHashSet<Link>> odPathMap =
        new BiLinkedHashMap<Station, Station, LinkedHashSet<Link>>();

    public PublicTransportationNetwork(Boolean isUndirected){
        this.isUndirected = isUndirected;
    }

    public PublicTransportationNetwork(Configuration config)
    throws DataInconsistentException{

        this(config.getBooleanValue("ptn_is_undirected"));
    }

    // =========================================================================
    // === Station Operations ==================================================
    // =========================================================================
    /**
     * Adds a station.
     */
    public void addStation(Station station) throws DataInconsistentException {
        if (station == null) {
            throw new DataInconsistentException("station is null");
        }

        Integer index = station.getIndex();

        if (index == null) {
            throw new DataInconsistentException("station index is null");
        }

        if (indexStationMap.keySet().contains(index)) {
            throw new DataInconsistentException("station with index " + index
                    + " already exists");
        }
        stations.add(station);
        indexStationMap.put(index, station);
    }

    protected void removeStationUnchecked(Station station,
            LinkedHashSet<Station> affectedStations,
            LinkedHashSet<Link> affectedIncomingLinks,
            LinkedHashSet<Link> affectedOutgoingLinks){

        stations.remove(station);
        LinkedHashSet<Link> linksToRemove = new LinkedHashSet<Link>();

        for(Link outgoingLink : station.getOutgoingLinks()){
            linksToRemove.add(outgoingLink);
            if(affectedOutgoingLinks != null){
                affectedOutgoingLinks.add(outgoingLink);
            }
        }
        for(Link incomingLink : station.getIncomingLinks()){
            linksToRemove.add(incomingLink);
            if(affectedIncomingLinks != null){
                affectedIncomingLinks.add(incomingLink);
            }
        }

        indexStationMap.remove(station.getIndex());

        if(affectedStations != null){
            for(Link link : linksToRemove){
                Station fromStation = link.getFromStation();
                if(station == fromStation){
                    affectedStations.add(link.getToStation());
                }
                else{
                    affectedStations.add(fromStation);
                }
            }
        }

        while(!linksToRemove.isEmpty()){
            Link link = linksToRemove.iterator().next();
            removeLinkUnchecked(link);
            if(link.isUndirected()){
                linksToRemove.remove(link);
                linksToRemove.remove(link.getUndirectedCounterpart());
            }
            else{
                linksToRemove.remove(link);
            }

        }

        adjacencyMatrix.remove(station);
        odPathMap.remove(station);

    }

    /**
     * Removes a station.
     */
    public void removeStation(Station station,
            LinkedHashSet<Station> affectedStations,
            LinkedHashSet<Link> affectedIncomingLinks,
            LinkedHashSet<Link> affectedOutgoingLinks) throws
            DataInconsistentException{

        if(!stations.contains(station)){
            throw new DataInconsistentException("station not contained in " +
                    "this public transportation network");
        }

        removeStationUnchecked(station, affectedStations, affectedIncomingLinks,
                affectedOutgoingLinks);
    }

    /**
     * Renumber station indices.
     */
    public void compactifyStations(){
        Integer stationCount = 1;

        Comparator<Station> cmp = new Comparator<Station>(){

            @Override
            public int compare(Station o1, Station o2) {
                return o1.getIndex() - o2.getIndex();
            }

        };

        TreeSet<Station> orderedStations = new TreeSet<Station>(cmp);
        orderedStations.addAll(stations);

        for(Station station : orderedStations){
            station.setIndex(stationCount);
            stationCount++;
        }
    }

    // =========================================================================
    // === Link Operations =====================================================
    // =========================================================================
    protected void addDirectedLinkUnchecked(Link link, Integer directedIndex)
    throws DataInconsistentException {
        Station fromStation = link.getFromStation();
        Station toStation = link.getToStation();

        if (smallestFreeDirectedLinkIndex == null ||
                directedIndex >= smallestFreeDirectedLinkIndex) {
            smallestFreeDirectedLinkIndex = directedIndex + 1;
        }

        directedLinks.add(link);
        directedIndexLinkMap.put(directedIndex, link);
        directedLinkIndexMap.put(link, directedIndex);

        LinkedHashSet<Link> linkSet;
        if (!adjacencyMatrix.containsKey(fromStation, toStation)) {
            linkSet = new LinkedHashSet<Link>();
        } else {
            linkSet = adjacencyMatrix.get(fromStation, toStation);
        }
        linkSet.add(link);
        adjacencyMatrix.put(fromStation, toStation, linkSet);
        fromStation.addOutgoingLink(link);
        toStation.addIncomingLink(link);

    }

    protected void removeDirectedLinkUnchecked(Link link) {
        Integer directedIndex = directedLinkIndexMap.get(link);
        Station fromStation = link.getFromStation();
        Station toStation = link.getToStation();

        directedLinks.remove(link);
        directedIndexLinkMap.remove(directedIndex);
        directedLinkIndexMap.remove(link);

        LinkedHashSet<Link> linkSet = adjacencyMatrix.get(fromStation,
                toStation);
        linkSet.remove(link);
        adjacencyMatrix.put(fromStation, toStation, linkSet);
        fromStation.removeOutgoingLink(link);
        toStation.removeIncomingLink(link);

    }

    protected void addLinkUnchecked(Link link) throws DataInconsistentException{
        Integer index = link.getIndex();

        indexLinkMap.put(index, link);

        if(link.isUndirected()){
            if (smallestFreeUndirectedLinkIndex == null ||
                    index >= smallestFreeUndirectedLinkIndex) {

                smallestFreeUndirectedLinkIndex = index + 1;
            }

            Link counterpart = link.getUndirectedCounterpart();

            addDirectedLinkUnchecked(link, getSmallestFreeDirectedLinkIndex());
            addDirectedLinkUnchecked(counterpart, getSmallestFreeDirectedLinkIndex());

            undirectedIndexLinkMap.put(index, link);
            undirectedLinks.add(link);
            undirectedLinkIndexMap.put(link, index);
            undirectedLinkIndexMap.put(counterpart, index);

        }
        else{

            addDirectedLinkUnchecked(link, index);

        }

        isUndirected &= link.isUndirected();
    }

    protected void removeLinkUnchecked(Link link){
        Integer index = link.getIndex();

        indexLinkMap.remove(index);

        if(link.isUndirected()){
            Link counterpart = link.getUndirectedCounterpart();

            removeDirectedLinkUnchecked(link);
            removeDirectedLinkUnchecked(counterpart);

            undirectedIndexLinkMap.remove(index);
            undirectedLinks.remove(link);
            undirectedLinks.remove(counterpart);
            undirectedLinkIndexMap.remove(link);
            undirectedLinkIndexMap.remove(counterpart);

        }
        else{

            removeDirectedLinkUnchecked(link);

        }

    }

    /**
     * Adds a link.
     *
     * @param link The link to add.
     * @throws DataInconsistentException
     */
    public void addLink(Link link) throws DataInconsistentException {

        if (link == null) {
            throw new DataInconsistentException("link is null");
        }

        Integer index = link.getIndex();

        if (index == null) {
            throw new DataInconsistentException("index is null");
        }

        Boolean linkUndirected = link.isUndirected();

        if (linkUndirected && undirectedLinkIndexMap.keySet().contains(index) ||
                !linkUndirected && indexLinkMap.keySet().contains(index)) {
            throw new DataInconsistentException("link with index " + index
                    + " already exists");
        }

        Station fromStation = link.getFromStation();
        if (fromStation == null) {
            throw new DataInconsistentException("link index " + index
                    + ": fromStation is null");
        }
        if (!stations.contains(fromStation)) {
            throw new DataInconsistentException("station set does not contain "
                    + "fromStation; link index is " + index);
        }

        Station toStation = link.getToStation();
        if (toStation == null) {
            throw new DataInconsistentException("link index " + index
                    + ": toStation is null");
        }
        if (!stations.contains(toStation)) {
            throw new DataInconsistentException("station set does not contain "
                    + "toStation; link index is " + index);
        }

        if (fromStation == toStation) {
            throw new DataInconsistentException("link is a loop; index is "
                    + index);
        }

        addLinkUnchecked(link);

    }

    /**
     * Removes a link.
     *
     * @param link The link to remove.
     * @throws DataInconsistentException
     */
    public void removeLink(Link link) throws DataInconsistentException{
        if(!directedLinks.contains(link)){
            throw new DataInconsistentException("link not contained in this " +
                    "public transportation network");
        }

        removeLinkUnchecked(link);
    }

    /**
     * Renumber link indices.
     *
     * @throws DataInconsistentException
     */
    public void compactifyLinks() throws DataInconsistentException{
        smallestFreeUndirectedLinkIndex = 1;
        smallestFreeDirectedLinkIndex = 1;
        final LinkedHashMap<Link, Integer> directedIndexLinkMap =
            this.directedLinkIndexMap;

        Comparator<Link> cmp = new Comparator<Link>(){

            @Override
            public int compare(Link o1, Link o2) {
                return directedIndexLinkMap.get(o1) -
                directedIndexLinkMap.get(o2);
            }

        };

        TreeSet<Link> orderedLinksInitial = new TreeSet<Link>(cmp);
        orderedLinksInitial.addAll(directedLinks);
        LinkedHashSet<Link> orderedLinks = new LinkedHashSet<Link>(
                orderedLinksInitial);

        while(!orderedLinks.isEmpty()){
            Link link = orderedLinks.iterator().next();
            orderedLinks.remove(link);

            removeLinkUnchecked(link);
            if(link.isUndirected()){
                link.setIndex(getSmallestFreeUndirectedLinkIndex());
                Link undirectedCounterpart = link.getUndirectedCounterpart();
                undirectedCounterpart.setIndex(
                        getSmallestFreeUndirectedLinkIndex());

                orderedLinks.remove(undirectedCounterpart);
            }
            else{
                link.setIndex(getSmallestFreeDirectedLinkIndex());
            }

            addLinkUnchecked(link);
        }

    }

    // =========================================================================
    // === Completeness ========================================================
    // =========================================================================
    /**
     * Throws an exception if link load data is missing together with a list
     * where it happened.
     */
    public void checkLoadCompleteness() throws DataInconsistentException {
        LinkedHashSet<Integer> linksWithoutLoad = new LinkedHashSet<Integer>();

        if (isUndirected) {
            for (Map.Entry<Integer, Link> e : undirectedIndexLinkMap.entrySet()) {
                Link link = e.getValue();
                Integer index = e.getKey();

                if (link.getLoad() == null || link.getLowerFrequency() == null
                        || link.getUpperFrequency() == null) {

                    linksWithoutLoad.add(index);

                }
            }
        }

        else {
            for (Link link : directedLinks) {
                if (link.getLoad() == null || link.getLowerFrequency() == null
                        || link.getUpperFrequency() == null) {

                    linksWithoutLoad.add(link.getIndex());

                }
            }
        }

        if (linksWithoutLoad.size() != 0) {

            String error = "load data missing for ";

            if (isUndirected) {
                error += "undirected links ";
            } else {
                error += "links ";
            }

            Iterator<Integer> itr = linksWithoutLoad.iterator();

            error += itr.next();

            while (itr.hasNext()) {

                error += ", " + itr.next();

            }

            throw new DataInconsistentException(error);
        }
    }

    /**
     * Throws an exception if link headway data is missing together with a list
     * where it happened.
     */
    public void checkHeadwayCompleteness() throws DataInconsistentException {
        LinkedHashSet<Integer> linksWithoutHeadway = new LinkedHashSet<Integer>();

        if (isUndirected) {
            for (Map.Entry<Integer, Link> e : undirectedIndexLinkMap.entrySet()) {
                Link link = e.getValue();
                Integer index = e.getKey();

                if (link.getHeadway() == null) {

                    linksWithoutHeadway.add(index);

                }
            }
        }

        else {
            for (Link link : directedLinks) {
                if (link.getHeadway() == null) {

                    linksWithoutHeadway.add(link.getIndex());

                }
            }
        }

        if (linksWithoutHeadway.size() != 0) {

            String error = "headway data missing for ";

            if (isUndirected) {
                error += "undirected links ";
            } else {
                error += "links ";
            }

            Iterator<Integer> itr = linksWithoutHeadway.iterator();

            error += itr.next();

            while (itr.hasNext()) {

                error += ", " + itr.next();

            }

            throw new DataInconsistentException(error);
        }
    }

    /**
     * Adds empty paths where they are missing in the origin destination path
     * map {@link #odPathMap}.
     */
    public void completeOriginDestinationPathMap(){

        for(Station s1 : stations){
            for(Station s2 : stations){
                LinkedHashSet<Link> odLinkList = odPathMap.get(s1, s2);
                if(odLinkList == null){
                    odLinkList = new LinkedHashSet<Link>();
                }
                odPathMap.put(s1, s2, odLinkList);
            }
        }

    }

    // =========================================================================
    // === Single request comfort functions ====================================
    // =========================================================================
    public Link getUndirectedLinkByIndex(Integer index) {
        return undirectedIndexLinkMap.get(index);
    }

    public Integer getUndirectedIndexByLink(Link link) {
        return undirectedLinkIndexMap.get(link);
    }

    public Link getDirectedLinkByIndex(Integer index) {
        return directedIndexLinkMap.get(index);
    }

    public Integer getDirectedIndexByLink(Link link) {
        return directedLinkIndexMap.get(link);
    }

    public Station getStationByIndex(Integer index) {
        return indexStationMap.get(index);
    }

    public Link getLinkByIndex(Integer index) {
        return indexLinkMap.get(index);
    }

    public Boolean isUndirected() {
        return isUndirected;
    }

    public void addOriginDestinationPath(Station s1, Station s2,
            LinkedHashSet<Link> path) {
        odPathMap.put(s1, s2, path);
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public LinkedHashSet<Station> getStations() {
        return stations;
    }

    public LinkedHashSet<Link> getDirectedLinks() {
        return directedLinks;
    }

    public LinkedHashSet<Link> getUndirectedLinks() {
        return undirectedLinks;
    }

    public BiLinkedHashMap<Station, Station, LinkedHashSet<Link>>
    getAdjacencyMatrix() {
        return adjacencyMatrix;
    }

    public LinkedHashMap<Integer, Link> getUndirectedIndexLinkMap() {
        return undirectedIndexLinkMap;
    }

    public Integer getSmallestFreeLinkIndex() {
        return isUndirected ? getSmallestFreeUndirectedLinkIndex() :
            getSmallestFreeDirectedLinkIndex();
    }

    public Integer getSmallestFreeDirectedLinkIndex() {
        if (smallestFreeDirectedLinkIndex == null) {
            return 1;
        }
        return smallestFreeDirectedLinkIndex;
    }

    public Integer getSmallestFreeUndirectedLinkIndex() {
        if (smallestFreeUndirectedLinkIndex == null) {
            return 1;
        }
        return smallestFreeUndirectedLinkIndex;
    }

    public LinkedHashMap<Integer, Link> getIndexLinkMap() {
        return indexLinkMap;
    }

    public BiLinkedHashMap<Station, Station, LinkedHashSet<Link>>
    getOriginDestinationPathMap() {
        return odPathMap;
    }

    public LinkedHashMap<Integer, Link> getDirectedIndexLinkMap() {
        return directedIndexLinkMap;
    }

    public LinkedHashMap<Link, Integer> getDirectedLinkIndexMap() {
        return directedLinkIndexMap;
    }

}