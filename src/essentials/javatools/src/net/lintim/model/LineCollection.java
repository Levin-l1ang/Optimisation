package net.lintim.model;

import net.lintim.exception.DataInconsistentException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.TreeSet;

/**
 * Implementation of a line collection, i.e. either a line concept or line pool.
 */
public class LineCollection implements PropertyChangeListener{
    // =========================================================================
    // === Fields ==============================================================
    // =========================================================================

    // --- Properties ----------------------------------------------------------
    protected PublicTransportationNetwork ptn;

    protected LinkedHashSet<Line> directedLines = new LinkedHashSet<Line>();
    protected LinkedHashSet<Line> undirectedLines = new LinkedHashSet<Line>();

    protected LinkedHashMap<Integer, Line> indexLineMap =
        new LinkedHashMap<Integer, Line>();
    protected LinkedHashMap<Integer, Line> directedIndexLineMap =
        new LinkedHashMap<Integer, Line>();
    protected LinkedHashMap<Line, Integer> directedLineIndexMap =
        new LinkedHashMap<Line, Integer>();
    protected LinkedHashMap<Integer, Line> undirectedIndexLineMap =
        new LinkedHashMap<Integer, Line>();
    protected LinkedHashMap<Line, Integer> undirectedLineIndexMap =
        new LinkedHashMap<Line, Integer>();

    protected Integer smallestFreeDirectedLineIndex = null;
    protected Integer smallestFreeUndirectedLineIndex = null;
    protected Boolean isUndirected;

    protected LinkedHashMap<Link, LinkedHashSet<Line>> directedLinkDirectedLineMap =
        new LinkedHashMap<Link, LinkedHashSet<Line>>();
    protected LinkedHashMap<Link, LinkedHashSet<Line>> directedLinkUndirectedLineMap =
        new LinkedHashMap<Link, LinkedHashSet<Line>>();
    protected LinkedHashMap<Link, LinkedHashSet<Line>> undirectedLinkUndirectedLineMap =
        new LinkedHashMap<Link, LinkedHashSet<Line>>();
    protected LinkedHashMap<Station, LinkedHashSet<Line>> stationDirectedLineMap =
        new LinkedHashMap<Station, LinkedHashSet<Line>>();
    protected LinkedHashMap<Station, LinkedHashSet<Line>> stationUndirectedLineMap =
        new LinkedHashMap<Station, LinkedHashSet<Line>>();

    /**
     * Constructor.
     *
     * @param ptn The underlying public transportation network.
     */
    public LineCollection(PublicTransportationNetwork ptn) {
        this.ptn = ptn;
        this.isUndirected = ptn.isUndirected();
    }

    // =========================================================================
    // === Add and Remove ======================================================
    // =========================================================================
    protected void addDirectedLineUnchecked(Line line, Integer directedIndex) {

        if (smallestFreeDirectedLineIndex == null ||
                directedIndex >= smallestFreeDirectedLineIndex) {

            smallestFreeDirectedLineIndex = directedIndex + 1;
        }

        for(Link link : line.getLinks()){
            LinkedHashSet<Line> lineSet = directedLinkDirectedLineMap.get(link);
            if(lineSet == null){
                lineSet = new LinkedHashSet<Line>();
            }
            lineSet.add(line);
            directedLinkDirectedLineMap.put(link, lineSet);
        }

        for(Station station : line.getStations()){
            LinkedHashSet<Line> lineSet = stationDirectedLineMap.get(station);
            if(lineSet == null){
                lineSet = new LinkedHashSet<Line>();
            }
            lineSet.add(line);
            stationDirectedLineMap.put(station, lineSet);
        }

        directedLines.add(line);
        line.addPropertyChangeListener(this);
        directedIndexLineMap.put(directedIndex, line);
        directedLineIndexMap.put(line, directedIndex);

    }

    protected void removeDirectedLineUnchecked(Line line) {

        Integer directedIndex = directedLineIndexMap.get(line);

        for(Link link : line.getLinks()){
            LinkedHashSet<Line> lineSet = directedLinkDirectedLineMap.get(link);
            lineSet.remove(line);
            if(lineSet.isEmpty()){
                directedLinkDirectedLineMap.remove(link);
            }
            else{
                directedLinkDirectedLineMap.put(link, lineSet);
            }
        }

        for(Station station : line.getStations()){
            LinkedHashSet<Line> lineSet = stationDirectedLineMap.get(station);
            lineSet.remove(line);
            if(lineSet.isEmpty()){
                stationDirectedLineMap.remove(station);
            }
            else{
                stationDirectedLineMap.put(station, lineSet);
            }
        }

        directedLines.remove(line);
        directedIndexLineMap.remove(directedIndex);
        directedLineIndexMap.remove(line);

    }


    protected void addLineUnchecked(Line line){
        Integer index = line.getIndex();

        indexLineMap.put(line.getIndex(), line);

        if(line.isUndirected()){
            if (smallestFreeUndirectedLineIndex == null ||
                    index >= smallestFreeUndirectedLineIndex) {

                smallestFreeUndirectedLineIndex = index + 1;
            }

            Line counterpart = line.getUndirectedCounterpart();

            undirectedLines.add(line);

            addDirectedLineUnchecked(line, getSmallestFreeDirectedLineIndex());
            addDirectedLineUnchecked(counterpart,
                    getSmallestFreeDirectedLineIndex());

            undirectedIndexLineMap.put(index, line);
            undirectedLineIndexMap.put(line, index);
            undirectedLineIndexMap.put(counterpart, index);

            for(Link link : line.getLinks()){
                LinkedHashSet<Line> lineSet =
                    directedLinkUndirectedLineMap.get(link);
                if(lineSet == null){
                    lineSet = new LinkedHashSet<Line>();
                }
                lineSet.add(line);
                directedLinkUndirectedLineMap.put(link, lineSet);
            }

            for(Link prelink : line.getLinks()){
                Link link = prelink.getUndirectedRepresentative();
                LinkedHashSet<Line> lineSet =
                    undirectedLinkUndirectedLineMap.get(link);
                if(lineSet == null){
                    lineSet = new LinkedHashSet<Line>();
                }
                lineSet.add(line);
                undirectedLinkUndirectedLineMap.put(link, lineSet);
            }

            for(Station station : line.getStations()){
                LinkedHashSet<Line> lineSet =
                    stationUndirectedLineMap.get(station);
                if(lineSet == null){
                    lineSet = new LinkedHashSet<Line>();
                }
                lineSet.add(line);
                stationUndirectedLineMap.put(station, lineSet);
            }

        }
        else{

            addDirectedLineUnchecked(line, index);

        }

        isUndirected &= line.isUndirected();
    }

    protected void removeLineUnchecked(Line line){
        Integer index = line.getIndex();

        indexLineMap.remove(line.getIndex());

        if(line.isUndirected()){
            Line counterpart = line.getUndirectedCounterpart();

            undirectedLines.remove(line.getUndirectedRepresentative());

            removeDirectedLineUnchecked(line);
            removeDirectedLineUnchecked(counterpart);

            undirectedIndexLineMap.remove(index);
            undirectedLineIndexMap.remove(line.getUndirectedRepresentative());

            for(Link link : line.getLinks()){
                LinkedHashSet<Line> lineSet =
                    directedLinkUndirectedLineMap.get(link);
                lineSet.remove(line);
                if(lineSet.isEmpty()){
                    directedLinkUndirectedLineMap.remove(link);
                }
                else{
                    directedLinkUndirectedLineMap.put(link, lineSet);
                }
            }

            for(Link prelink : line.getLinks()){
                Link link = prelink.getUndirectedRepresentative();
                LinkedHashSet<Line> lineSet =
                    undirectedLinkUndirectedLineMap.get(link);
                lineSet.remove(line);
                if(lineSet.isEmpty()){
                    undirectedLinkUndirectedLineMap.remove(link);
                }
                else{
                    undirectedLinkUndirectedLineMap.put(link, lineSet);
                }
            }

            for(Station station : line.getStations()){
                LinkedHashSet<Line> lineSet =
                    stationUndirectedLineMap.get(station);
                lineSet.remove(line);
                if(lineSet.isEmpty()){
                    stationUndirectedLineMap.remove(station);
                }
                else{
                    stationUndirectedLineMap.put(station, lineSet);
                }
            }

        }
        else{

            removeDirectedLineUnchecked(line);

        }
    }

    /**
     * Adds a line to the line collection.
     *
     * @param line The line to add.
     * @throws DataInconsistentException
     */
    public void addLine(Line line) throws DataInconsistentException{
        if(line == null){
            throw new DataInconsistentException("line is null");
        }

        Integer index = line.getIndex();

        if(index == null){
            throw new DataInconsistentException("line index is null");
        }

        if(indexLineMap.containsKey(index)){
            throw new DataInconsistentException("line with index " + index +
                    " already exists");
        }

        addLineUnchecked(line);

    }

    /**
     * Removes a line from the line collection.
     *
     * @param line The line to remove.
     * @throws DataInconsistentException
     */
    public void removeLine(Line line) throws DataInconsistentException{
        if(!directedLines.contains(line)){
            throw new DataInconsistentException("line not contained in lines");
        }

        removeLineUnchecked(line);
    }

    /**
     * Renumbers the line indices.
     */
    public void compactifyLines(){
        smallestFreeUndirectedLineIndex = 1;
        smallestFreeDirectedLineIndex = 1;

        final LinkedHashMap<Line, Integer> directedIndexLineMap =
            this.directedLineIndexMap;

        Comparator<Line> cmp = new Comparator<Line>(){

            @Override
            public int compare(Line o1, Line o2) {
                return directedIndexLineMap.get(o1) - directedIndexLineMap.get(o2);
            }

        };

        TreeSet<Line> orderedLinesInitial = new TreeSet<Line>(cmp);
        orderedLinesInitial.addAll(directedLines);
        LinkedHashSet<Line> orderedLines =
            new LinkedHashSet<Line>(orderedLinesInitial);

        while(!orderedLines.isEmpty()){
            Line line = orderedLines.iterator().next();
            orderedLines.remove(line);

            removeLineUnchecked(line);
            if(line.isUndirected()){
                line.setIndex(getSmallestFreeUndirectedLineIndex());

                Line undirectedCounterpart = line.getUndirectedCounterpart();
                undirectedCounterpart.setIndex(
                        getSmallestFreeUndirectedLineIndex());

                orderedLines.remove(undirectedCounterpart);
            }
            else{
                line.setIndex(getSmallestFreeDirectedLineIndex());
            }

            addLineUnchecked(line);
        }
    }

    // =========================================================================
    // === Property Changes ====================================================
    // =========================================================================
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Line line = (Line) evt.getSource();
        String type = evt.getPropertyName();

        if(type == Line.PROPERTY_ADD_STATION){
            Station station = (Station) evt.getNewValue();
            LinkedHashSet<Line> lineSet = stationDirectedLineMap.get(station);
            if(lineSet == null){
                lineSet = new LinkedHashSet<Line>();
            }
            lineSet.add(line);
            stationDirectedLineMap.put(station, lineSet);
        }
        else if(type == Line.PROPERTY_REMOVE_STATION){
            Station station = (Station) evt.getOldValue();
            LinkedHashSet<Line> lineSet = stationDirectedLineMap.get(station);
            lineSet.remove(line);
            if(lineSet.isEmpty()){
                stationDirectedLineMap.remove(station);
            }
            else{
                stationDirectedLineMap.put(station, lineSet);
            }
        }
        else if(type == Line.PROPERTY_ADD_LINK){
            Link link = (Link) evt.getNewValue();

            LinkedHashMap<Link, LinkedHashSet<Line>> linkLineMap =
                line.isUndirectedRepresentative() ? directedLinkUndirectedLineMap :
                    directedLinkDirectedLineMap;

            LinkedHashSet<Line> lineSet = linkLineMap.get(link);
            if(lineSet == null){
                lineSet = new LinkedHashSet<Line>();
            }
            lineSet.add(line);
            linkLineMap.put(link, lineSet);
        }
        else if(type == Line.PROPERTY_REMOVE_LINK){
            Link link = (Link) evt.getOldValue();

            LinkedHashMap<Link, LinkedHashSet<Line>> linkLineMap =
                line.isUndirectedRepresentative() ? directedLinkUndirectedLineMap :
                    directedLinkDirectedLineMap;

            LinkedHashSet<Line> lineSet = linkLineMap.get(link);
            lineSet.remove(line);
            if(lineSet.isEmpty()){
                linkLineMap.remove(link);
            }
            else{
                linkLineMap.put(link, lineSet);
            }
        }

    }

    // =========================================================================
    // === Single request comfort functions ====================================
    // =========================================================================
    public Line getLineByIndex(Integer index){
        return indexLineMap.get(index);
    }

    public Integer getUndirectedIndexByLine(Line line){
        return undirectedLineIndexMap.get(line);
    }

    public Line getLineByUndirectedIndex(Integer index){
        return undirectedIndexLineMap.get(index);
    }

    public Line getLineByDirectedIndex(Integer index){
        return directedIndexLineMap.get(index);
    }

    public Boolean isUndirected() {
        return isUndirected;
    }

    public LinkedHashSet<Line> getLinesByStation(Station station){
        return stationDirectedLineMap.get(station);
    }

    public LinkedHashSet<Line> getDirectedLinesByLink(Link link){
        return directedLinkDirectedLineMap.get(link);
    }

    public LinkedHashSet<Line> getUndirectedLinesByDirectedLink(Link link){
        return directedLinkUndirectedLineMap.get(link);
    }

    public LinkedHashSet<Line> getUndirectedLinesByUndirectedLink(Link link){
        return undirectedLinkUndirectedLineMap.get(link);
    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================


    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public LinkedHashSet<Line> getDirectedLines() {
        return directedLines;
    }

    public LinkedHashSet<Line> getUndirectedLines() {
        return undirectedLines;
    }

    public LinkedHashMap<Integer, Line> getIndexLineMap() {
        return indexLineMap;
    }

    public LinkedHashMap<Integer, Line> getUndirectedIndexLineMap() {
        return undirectedIndexLineMap;
    }

    public LinkedHashMap<Line, Integer> getUndirectedLineIndexMap() {
        return undirectedLineIndexMap;
    }

    public PublicTransportationNetwork getPublicTransportationNetwork() {
        return ptn;
    }

    public Integer getSmallestFreeLineIndex() {
        return isUndirected ? getSmallestFreeUndirectedLineIndex() :
            getSmallestFreeDirectedLineIndex();
    }

    public Integer getSmallestFreeUndirectedLineIndex() {
        if(smallestFreeUndirectedLineIndex == null){
            smallestFreeUndirectedLineIndex = 1;
        }

        return smallestFreeUndirectedLineIndex;
    }

    public Integer getSmallestFreeDirectedLineIndex() {
        if(smallestFreeDirectedLineIndex == null){
            smallestFreeDirectedLineIndex = 1;
        }

        return smallestFreeDirectedLineIndex;
    }

    public LinkedHashMap<Link, LinkedHashSet<Line>> getLinkDirectedLineMap() {
        return directedLinkDirectedLineMap;
    }

    public LinkedHashMap<Line, Integer> getDirectedLineIndexMap() {
        return directedLineIndexMap;
    }

    public LinkedHashMap<Link, LinkedHashSet<Line>>
    getDirectedLinkUndirectedLineMap() {
        return directedLinkUndirectedLineMap;
    }

    public LinkedHashMap<Link, LinkedHashSet<Line>>
    getUndirectedLinkUndirectedLineMap() {
        return undirectedLinkUndirectedLineMap;
    }

    public LinkedHashMap<Station, LinkedHashSet<Line>>
    getStationDirectedLineMap() {
        return stationDirectedLineMap;
    }

    public LinkedHashMap<Station, LinkedHashSet<Line>>
    getStationUndirectedLineMap() {
        return stationUndirectedLineMap;
    }
    
    public LinkedHashSet<Station> getStations(){
    	return (LinkedHashSet<Station>) stationDirectedLineMap.keySet();
    }
    
    public LinkedHashSet<Link> getDirectedLinks(){
    	return (LinkedHashSet<Link>) directedLinkDirectedLineMap.keySet();
    }
    
    public LinkedHashSet<Station> getUsedStations(){
    	LinkedHashSet<Station> usedStations = new LinkedHashSet<Station>();
    	for(Entry<Station, LinkedHashSet<Line>> e1: 
    		stationDirectedLineMap.entrySet()){
    		
    		for(Line line: e1.getValue()){
    			if(line.getFrequency() > 0){
    				usedStations.add(e1.getKey());
    				continue;
    			}
    		}
    		
    	}
    	
    	return usedStations;
    }
    
    public LinkedHashSet<Link> getUsedDirectedLinks(){
    	LinkedHashSet<Link> usedLinks = new LinkedHashSet<Link>();
    	for(Entry<Link, LinkedHashSet<Line>> e1: 
    		directedLinkDirectedLineMap.entrySet()){
    		
    		for(Line line: e1.getValue()){
    			if(line.getFrequency() > 0){
    				usedLinks.add(e1.getKey());
    				continue;
    			}
    		}
    		
    	}
    	
    	return usedLinks;
    }
}
