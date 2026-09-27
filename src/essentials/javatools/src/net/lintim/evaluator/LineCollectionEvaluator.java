package net.lintim.evaluator;

import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicEventActivityNetworkGenerator;
import net.lintim.graph.GraphMalformedException;
import net.lintim.graph.ShortestPathsGraph;
import net.lintim.main.LineConceptEvaluation;
import net.lintim.model.*;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.EventActivityNetwork.ModelChange;
import net.lintim.model.EventActivityNetwork.ModelFrequency;
import net.lintim.model.EventActivityNetwork.ModelHeadway;
import net.lintim.util.BiLinkedHashMap;
import net.lintim.util.MathHelper;
import net.lintim.util.SinglePair;

import java.util.*;
import java.util.Map.Entry;

//import PublicTransportationNetworkEvaluator.DirectedStation;


/**
 * Evaluates different properties of an {@link LineCollection}.
 */
public class LineCollectionEvaluator {
	private static boolean feasible_od = true;
	private static boolean feasible_od_yet_checked= false;

	private static class DirectedStation {

		public Station station;

		public DirectedStation(Station station) {
			this.station = station;
		}
	}

	public static Double minLength(LineCollection lc){
		double retval=Double.MAX_VALUE;
		double length;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			length=line.getLength();
			if(line.getFrequency()>0 && length<retval){
				retval=length;
			}
		}
		return retval;
	}

	public static Double minDistance(LineCollection lc){
		double retval=Double.MAX_VALUE;
		double distance;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			distance=Station.distance(line.getStations().getFirst(),line.getStations().getLast());
			if(line.getFrequency()>0 && distance<retval){
				retval=distance;
			}
		}
		return retval;
	}

	public static Double minEdges(LineCollection lc){
		double retval=Double.MAX_VALUE;
		int edges;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			edges=line.getLinks().size();
			if(line.getFrequency()>0 && edges<retval){
				retval=edges;
			}
		}
		return retval;
	}

	public static Double averageLength(LineCollection lc){
		double retval=0;
		int count=0;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			if(line.getFrequency()>0){
				count++;
				retval+=line.getLength();
			}
		}
		return retval/count;
	}

	public static Double averageDistance(LineCollection lc){
		double retval=0;
		double distance;
		int count=0;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			distance=Station.distance(line.getStations().getFirst(),line.getStations().getLast());
			if(line.getFrequency()>0){
				retval+=distance;
				count++;
			}
		}
		return retval/count;
	}

	public static Double averageEdges(LineCollection lc){
		double retval=0;
		int count=0;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			if(line.getFrequency()>0){
				retval+=line.getLinks().size();
				count++;
			}
		}
		return retval/count;
	}

	public static Double varianceLength(LineCollection lc){
		double sum=0;
		double squared_sum=0;
		int count=0;
		double length;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			if(line.getFrequency()>0){
				count++;
				length=line.getLength();
				sum+=length;
				squared_sum+=length*length;
			}
		}
		sum/=count;
		sum=sum*sum;
		squared_sum/=count;
		return squared_sum-sum;
	}

	public static Double varianceDistance(LineCollection lc){
		double sum=0;
		double squared_sum=0;
		double distance;
		int count=0;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			if(line.getFrequency()>0){
				distance=Station.distance(line.getStations().getFirst(),line.getStations().getLast());
				sum+=distance;
				squared_sum+=distance*distance;
				count++;
			}
		}
		sum/=count;
		sum=sum*sum;
		squared_sum/=count;
		return squared_sum-sum;
	}

	public static Double varianceEdges(LineCollection lc){
		double sum=0;
		double squared_sum=0;
		int count=0;
		int edges;
		LinkedHashSet<Line> lines;
		if(lc.isUndirected()){
			lines=lc.getUndirectedLines();
		}
		else{
			lines=lc.getDirectedLines();
		}
		for(Line line:lines){
			if(line.getFrequency()>0){
				edges=line.getLinks().size();
				sum+=edges;
				squared_sum+=edges*edges;
				count++;
			}
		}
		sum/=count;
		sum=sum*sum;
		squared_sum/=count;
		return squared_sum-sum;
	}



    /**
     * Computes the cost of a line concept, i.e. the sum of lineCost*frequency
     * over all lines for a given {@link LineCollection}. Will consider
     * undirected lines only if the line concept is undirected.
     * @param lc the given {@link LineCollection}.
     * @return the sum of lineCost*frequency over all lines.
     */
    public static Double cost(LineCollection lc){
        double retval = 0.0;

        if(lc.isUndirected()){
            for(Line line : lc.getUndirectedLines()){
                retval += line.getCost()*line.getFrequency();
            }
        }
        else {
            for(Line line : lc.getDirectedLines()){
                retval += line.getCost()*line.getFrequency();
            }
        }

        return retval;
    }

    /**
     * Computes the number of undirected lines for a given {@link LineCollection}.
     * @param lc the given {@link LineCollection}.
     * @return the number of undirected lines.
     */
    public static Integer undirectedLinesCount(LineCollection lc){
        if(!lc.isUndirected()){
            throw new UnsupportedOperationException(
                    "line collection not undirected");
        }

        return lc.getUndirectedLines().size();
    }

    /**
     * Computes the number of directed lines for a given {@link LineCollection}.
     * @param lc the given {@link LineCollection}.
     * @return the number of directed lines.
     */
    public static Integer directedLinesCount(LineCollection lc){
        return lc.getDirectedLines().size();
    }

    /**
     * Computes the number of undirected lines with frequency greater zero for
     * a the given {@link LineCollection}.
     * @param lc the given {@link LineCollection}.
     * @return the number of undirected lines with frequency greater zero.
     */
    public static Integer undirectedLinesUsedCount(LineCollection lc){
        if(!lc.isUndirected()){
            throw new UnsupportedOperationException(
                    "line collection not undirected");
        }

        Integer counter = 0;

        for(Line line : lc.getUndirectedLines()){
            if(line.getFrequency() != 0){
                counter++;
            }
        }

        return counter;
    }

    /**
     * Computes the number of directed lines with frequency greater zero for a
     * the given {@link LineCollection}.
     * @param lc the given {@link LineCollection}.
     * @return the number of directed lines with frequency greater zero.
     */
    public static Integer directedLinesUsedCount(LineCollection lc){

        Integer counter = 0;

        for(Line line : lc.getDirectedLines()){
            if(line.getFrequency() != 0){
                counter++;
            }
        }

        return counter;
    }

    /**
     * Checks whether the intersection of the {@link Link}s of two lines are
     * connected in the {@link PublicTransportationNetwork} graph or not.
     * @param line1 first line to consider
     * @param line2 second line to consider.
     * @return true if the intersection of the {@link Link}s of line1 and line2
     * is connected; false otherwise.
     */
    public static Boolean linePairIntersectionConnected(Line line1, Line line2){
        LinkedHashSet<Link> commonLinePart = new LinkedHashSet<Link>(
                line1.getLinks());
        commonLinePart.retainAll(line2.getLinks());

        if(commonLinePart.size() > 0){

            Iterator<Link> itr = commonLinePart.iterator();

            Link oldLink = itr.next();

            Integer segmentCount = 1;
            Integer positionCount = 1;

            while(itr.hasNext()){
                Link link = itr.next();
                if(oldLink.getToStation() != link.getFromStation()){
                    segmentCount++;
                    return false;
                }
                oldLink = link;
                positionCount++;
            }
        }

        return true;
    }

    /**
     * Checks whether the intersection of the {@link Link}s of every pair of
     * lines is connected in the {@link PublicTransportationNetwork} graph or
     * not.
     * @param lc the given {@link LineCollection}.
     * @return true if the intersection of the {@link Link}s of every pair of
     * lines in the given {@link LineCollection} is connected; false otherwise.
     */
    public static Boolean linePairIntersectionsConnected(LineCollection lc,
            Boolean skipFrequencyZero){

        LinkedHashSet<Line> lines = lc.getDirectedLines();

        for(Line line1 : lines){

            if(skipFrequencyZero && line1.getFrequency().equals(0)){
                continue;
            }

            for(Line line2 : lines){

                if(skipFrequencyZero && line2.getFrequency().equals(0)){
                    continue;
                }

                if(line1 == line2){
                    break;
                }

                if(!linePairIntersectionConnected(line1, line2)){
                    return false;
                }

            }
        }

        return true;
    }

    /**
     * Method to determine the average time a passengers spends in the PTN with the
     * lines used in the LineCollection to reach their destination.
     * @param lc The LineCollection considered.
     * @param od The OD-Matrix used.
     * @param minimalWaitingTime The time a passengers spend approximatly at any station.
     * @return The average traveling time.
     */
    public static Double lineCollectionTimeAverage(LineCollection lc,
    		OriginDestinationMatrix od, Double minimalWaitingTime)
    		throws DataInconsistentException {

    	Double retval = 0.0;
		Double passengers = 0.0;
		Double passengers_od = 0.0;

		BiLinkedHashMap<Station, Station, Double> lcTime =
				computeLcTime(lc, od, minimalWaitingTime);

		BiLinkedHashMap<Station, Station, Double> odData = od.getMatrix();

		for (Entry<Station, LinkedHashMap<Station, Double>> e1 :
				lcTime.entrySet()) {

			for (Entry<Station, Double> e2 : e1.getValue().entrySet()) {
				if (e1.getKey().getIndex() == e2.getKey().getIndex()) {
					continue;
				}
				passengers_od = odData.get(e1.getKey(), e2.getKey());
				retval += e2.getValue() * passengers_od;
				passengers += passengers_od;
			}
		}

		return retval/passengers;

    }

    private static BiLinkedHashMap<Station, Station, Double> computeLcTime(
			LineCollection lc, OriginDestinationMatrix od,
			Double minimalWaitingTime) throws DataInconsistentException {

		BiLinkedHashMap<Station, Station, Double> retval =
				new BiLinkedHashMap<Station, Station, Double>();

		ShortestPathsGraph<DirectedStation, Link> sp =
				new ShortestPathsGraph<DirectedStation, Link>();

		LinkedHashMap<Station, SinglePair<DirectedStation>> directedStationMap =
				new LinkedHashMap<Station, SinglePair<DirectedStation>>();

		LinkedHashSet<Station> stations = lc.getUsedStations();

		for (Station station : stations) {
			DirectedStation incomingLinks = new DirectedStation(station);
			DirectedStation outgoingLinks = new DirectedStation(station);
			directedStationMap.put(station, new SinglePair<DirectedStation>(
					incomingLinks, outgoingLinks));

			sp.addVertex(incomingLinks);
			sp.addVertex(outgoingLinks);
			sp.addEdge(null, incomingLinks, outgoingLinks, minimalWaitingTime);
		}

		for (Link link : lc.getUsedDirectedLinks()) {
			sp.addEdge(link,
					directedStationMap.get(link.getFromStation()).second,
					directedStationMap.get(link.getToStation()).first,
					link.getLowerBound());
		}

		for (Station s1 : stations) {
			try {
				sp.compute(directedStationMap.get(s1).second);
			} catch (GraphMalformedException e) {
				throw new DataInconsistentException(
						"shortest paths computation: " + e.getMessage());
			}

			for (Station s2 : stations) {
				if (s1 == s2) {
					retval.put(s1, s2, 0.0);
				} else {
					if (Math.abs(od.get(s1, s2)) < MathHelper.epsilon) {
						continue;
					}

					Double distance = sp
							.getDistance(directedStationMap.get(s2).first);

					if (distance == Double.POSITIVE_INFINITY) {
						/*throw new DataInconsistentException("there is no "
								+ "path from station " + s1.getIndex() + " to "
								+ s2.getIndex());*/
						feasible_od = false;
					}

					retval.put(s1, s2, distance);

				}

			}

		}

		feasible_od_yet_checked=true;
		return retval;

	}

    /**
	 * Returns whether there is a way for each OD-pair through the PTN with the
	 * given lines.
	 * @param lc the given {@link LineCollection}.
	 * @param od the given {@link OriginDestinationMatrix}.
	 * @return Whether all OD-pair can travel.
	 */
	public static Boolean lcFeasibleOd(LineCollection lc,
			OriginDestinationMatrix od, Double minimalWaitingTime)
			throws DataInconsistentException {
		if(lc.getPublicTransportationNetwork().getStations().size() != lc.getUsedStations().size())
			return false;
		if(!feasible_od_yet_checked)
			lineCollectionTimeAverage(lc, od, minimalWaitingTime);
		return feasible_od;
	}


    /**
     * A Method to determine whether the given LineCollection  does not contain
     * lines with circles.
     * @param lc The LineCollection to consider.
     * @return Whether no line in the LineCollection contains a circle.
     */
    public static boolean lcFeasibleCircles(LineCollection lc){
    	LinkedHashSet<Line> lines = new LinkedHashSet<Line>();
    	LinkedList<Station> stations = new LinkedList<Station>();
    	LinkedHashSet<Station> stationsSet = new LinkedHashSet<Station>();
    	if(lc.getPublicTransportationNetwork().isUndirected()){
    		lines = lc.getUndirectedLines();
    	}else{
    		lines = lc.getDirectedLines();
    	}
    	for(Line line: lines){
    		stations=line.getStations();
    		stationsSet= line.getStationSet();
    		if(stations.size() != stationsSet.size())
    			return false;
    	}
    	return true;
    }



    /**
     * Computes a lower bound for the sum of duration*passengers, i.e. a lower
     * bound for the average traveling time for a given {@link LineCollection}
     * and {@link OriginDestinationMatrix} by setting all durations to lower
     * bounds, distributing the passengers along the shortest paths in time and
     * calculating sum of duration*passengers for these assumptions.
     * @param lc the given {@link LineCollection}
     * @param od the given {@link OriginDestinationMatrix} to calculate the
     * passenger distribution
     * @param minimalWaitTime the lower bound for every wait activity
     * @param minimalChangeTime the lower bound for every change activity
     * @param skipFrequencyZero true if lines with frequency zero should be
     * excluded; false otherwise. This parameter controls whether the lower
     * bound is computed for the line concept (false) or the whole line pool
     * (true).
     * @return a lower bound for the average traveling time.
     * @throws DataInconsistentException if something goes wrong in the shortest
     * paths calculation.
     */
 /*   public static Double averageTravelingTimeLowerBound(LineCollection lc,
            OriginDestinationMatrix od, Double minimalWaitTime,
            Double minimalChangeTime, Boolean skipFrequencyZero)
    throws DataInconsistentException{

        double retval = 0.0;

        PublicTransportationNetwork ptn = lc.getPublicTransportationNetwork();
        boolean isUndirected = ptn.isUndirected();

        LineCollection tempLc = new LineCollection(ptn);

        LinkedHashSet<Line> lines =
            new LinkedHashSet<Line>(lc.getDirectedLines());

        LinkedHashSet<Integer> indicesUsed = new LinkedHashSet<Integer>();

        // Filter sublines, should reduce the memory consumption enormously.
        // Why doing so, this seems to have quite an impact on the shortest paths in the EAN
        // Two equal lines are possible, watch out to not filter out both lines with respect to eachother
        for(Line line1 : lc.getDirectedLines()){
            Iterator<Line> itr = lines.iterator();
            boolean isSomeSubline = false;
            while(itr.hasNext()){
                Line line2 = itr.next();
                if(line2==line1){
                    continue;
                }
                if(line1.isSublineOf(line2) && (
                        !skipFrequencyZero ||
                        line2.getFrequency() > 0)){
                    isSomeSubline = true;
                    if(line2.isSublineOf(line1) && (!skipFrequencyZero ||line1.getFrequency() > 0)){
						lines.remove(line1);
					}
                    break;
                }
            }
            if(!isSomeSubline){
                if(isUndirected){
                    if(!indicesUsed.contains(line1.getIndex())){
                        tempLc.addLine(line1);
                        indicesUsed.add(line1.getIndex());
                    }
                }
                else {
                    tempLc.addLine(line1);
                }
            }
        }

	// **********************************************

	// Watch out change here from tempLC to lc in EAN!!!!!!!!!!!
	// **********************************************
        EventActivityNetwork ean = new EventActivityNetwork(lc,
                ModelFrequency.FREQUENCY_AS_ATTRIBUTE,
                ModelChange.SIMPLE, ModelHeadway.NO_HEADWAYS);
        // The period length should not play a role here; we can chose it
        // arbitrarily. However, we must set it, since it is needed
        // for the PeriodicEventActivityNetworkGenerator.
        ean.setPeriodLength(60.0);

        PeriodicEventActivityNetworkGenerator peangen
        = new PeriodicEventActivityNetworkGenerator(ean, null,
                minimalChangeTime, minimalChangeTime, minimalWaitTime,
                minimalWaitTime);
        peangen.setForceAllFrequenciesOne(!skipFrequencyZero);
        peangen.generateLineConceptRepresentation();

//        peangen.generateChanges();
//        if(skipFrequencyZero){
//        }
//        else{
//            System.err.println("here we are");
//            System.exit(1);
//        }

        ShortestPathsGraph<Event, Activity> sp =
            new ShortestPathsGraph<Event, Activity>();

        for (Event event : ean.getEvents()) {
            sp.addVertex(event);
        }

        for (Activity activity : ean.getActivities()) {
            if (activity.getType() == ActivityType.DRIVE
                    || activity.getType() == ActivityType.WAIT
                    || activity.getType() == ActivityType.CHANGE) {

                sp.addEdge(activity, activity
                        .getFromEvent(), activity.getToEvent(),
                        activity.getLowerBound());
            }
        }

        // Generate changes that do not consume too much main memory, which is
        // cruical if !skipFrequencyZero.
        LinkedHashSet<Event> events = ean.getEvents();
        for(Event e1 : events){
            for(Event e2 : events){
                if(e1.getStation() == e2.getStation() &&
                        e1.getType() == EventType.ARRIVAL &&
                        e2.getType() == EventType.DEPARTURE &&
                        e1.getLine() != e2.getLine()){

                    sp.addEdge(new Activity(1,ActivityType.CHANGE,e1,e2,null,0.0,0.0,0.0,0.0,0.0), e1, e2, minimalChangeTime);
                }
            }
        }

        LinkedHashMap<Station, Event> sourceEvents =
            new LinkedHashMap<Station, Event>();
        LinkedHashMap<Station, Event> sinkEvents =
            new LinkedHashMap<Station, Event>();

        LinkedHashMap<Station, LinkedHashSet<Event>> stationDepartureMap
        = ean.getStationDepartureEventMap();

        LinkedHashMap<Station, LinkedHashSet<Event>> stationArrivalMap
        = ean.getStationArrivalEventMap();

        for (Station s1 : ptn.getStations()){
            LinkedHashSet<Event> departures = stationDepartureMap.get(s1);

            if(departures == null){
                throw new DataInconsistentException("no departures at " +
                        "station " + s1.getIndex());
            }

            Event sourceEvent = new Event(null, null, s1, null, null, null,
                    null, null, null);

            sourceEvents.put(s1, sourceEvent);
            sp.addVertex(sourceEvent);

            for (Event departure : departures) {
                sp.addEdge(new Activity(2,ActivityType.CHANGE,sourceEvent,departure,null,0.0,0.0,0.0,0.0,0.0), sourceEvent, departure, 0.0);
            }
        }

        for (Station s1 : ptn.getStations()) {
            LinkedHashSet<Event> arrivals = stationArrivalMap.get(s1);

            if(arrivals == null){
                throw new DataInconsistentException("no arrivals at " +
                        "station " + s1.getIndex());
            }

            Event sinkEvent = new Event(null, null, s1, null, null, null, null,
                    null, null);

            sinkEvents.put(s1, sinkEvent);
            sp.addVertex(sinkEvent);

            for (Event arrival : arrivals) {
                sp.addEdge(new Activity(3,ActivityType.CHANGE,arrival,sinkEvent,null,0.0,0.0,0.0,0.0,0.0), arrival, sinkEvent, 0.0);
            }
        }

//        IterationProgressCounterDump ipc = new IterationProgressCounterDump();
//        ipc.setTotalNumberOfIterations(ptn.getStations().size());

        for (Map.Entry<Station, Event> e1 : sourceEvents.entrySet()) {

//            ipc.reportIteration();

            Station s1 = e1.getKey();
            Event source = e1.getValue();

            try {
                sp.compute(source);
            } catch (GraphMalformedException e) {
                throw new DataInconsistentException(
                        "shortest paths computation: " + e.getMessage());
            }

            for (Map.Entry<Station, Event> e2 : sinkEvents.entrySet()) {

                Station s2 = e2.getKey();
                Event sink = e2.getValue();
                Double passengers = od.get(s1, s2);
                if (s1 == s2 || Math.abs(passengers) < MathHelper.epsilon) {
                    continue;
                }
                retval += sp.getDistance(sink)*passengers;

            }
        }

        return retval;

    }*/

    /**
     * Computes the minimal time distance for every pair of stations for a given
     * {@link LineCollection}, i.e. the sum over the lower bounds along the
     * shortest paths between every pair of stations.
     * @param lc the given {@link LineCollection}
     * @param minimalWaitTime the lower bound for every wait activity
     * @param minimalChangeTime the lower bound for every change activity
     * @param skipFrequencyZero true if lines with frequency zero should be
     * excluded; false otherwise. This parameter controls whether the pairwise
     * distances are computed for the line concept (false) or the whole line
     * pool (true).
     * @return a {@link BiLinkedHashMap} with two indices: index one is the
     * from-station, index two the to-station; values are minimal time distances.
     * @throws DataInconsistentException if something goes wrong in the shortest
     * paths calculation.
     */
    public static BiLinkedHashMap<Station, Station, Double>
    pairwiseStationMinimalDistance(LineCollection lc, Double minimalWaitTime,
            Double minimalChangeTime, Boolean skipFrequencyZero)
            throws DataInconsistentException{

        BiLinkedHashMap<Station, Station, Double> retval =
            new BiLinkedHashMap<Station, Station, Double>();

        EventActivityNetwork ean = new EventActivityNetwork(lc,
                ModelFrequency.FREQUENCY_AS_ATTRIBUTE,
                ModelChange.SIMPLE,
                ModelHeadway.NO_HEADWAYS);
        // The period length should not play a role here; we can chose it
        // arbitrarily. However, we must set it, since it is needed
        // for the PeriodicEventActivityNetworkGenerator.
        ean.setPeriodLength(60.0);

        PeriodicEventActivityNetworkGenerator peangen
        = new PeriodicEventActivityNetworkGenerator(ean, null,
                minimalChangeTime, minimalChangeTime, minimalWaitTime,
                minimalWaitTime);
        peangen.setForceAllFrequenciesOne(!skipFrequencyZero);
        peangen.generateLineConceptRepresentation();
        peangen.generateChanges();

        ShortestPathsGraph<Event, Activity> sp =
            new ShortestPathsGraph<Event, Activity>();

        for (Event event : ean.getEvents()) {
            sp.addVertex(event);
        }

        for (Activity activity : ean.getActivities()) {
            if (activity.getType() == ActivityType.DRIVE
                    || activity.getType() == ActivityType.WAIT
                    || activity.getType() == ActivityType.CHANGE) {

                sp.addEdge(null, activity.getFromEvent(), activity.getToEvent(),
                        activity.getLowerBound());
            }
        }

        LinkedHashMap<Station, Event> sourceEvents =
            new LinkedHashMap<Station, Event>();
        LinkedHashMap<Station, Event> sinkEvents =
            new LinkedHashMap<Station, Event>();

        for (Map.Entry<Station, LinkedHashSet<Event>> e1 : ean
                .getStationDepartureEventMap().entrySet()) {

            Station s1 = e1.getKey();
            LinkedHashSet<Event> departures = e1.getValue();

            Event sourceEvent = new Event(null, null, s1, null, null, null, null,
                    null, null, null, -1);

            sourceEvents.put(s1, sourceEvent);
            sp.addVertex(sourceEvent);

            for (Event departure : departures) {
                sp.addEdge(null, sourceEvent, departure, 0.0);
            }
        }

        for (Map.Entry<Station, LinkedHashSet<Event>> e1 : ean
                .getStationArrivalEventMap().entrySet()) {

            Station s1 = e1.getKey();
            LinkedHashSet<Event> arrivals = e1.getValue();

            Event sinkEvent = new Event(null, null, s1, null, null, null, null, null,
                    null, null, -1);

            sinkEvents.put(s1, sinkEvent);
            sp.addVertex(sinkEvent);

            for (Event arrival : arrivals) {
                sp.addEdge(null, arrival, sinkEvent, 0.0);
            }
        }

        for (Map.Entry<Station, Event> e1 : sourceEvents.entrySet()) {

            Station s1 = e1.getKey();
            Event source = e1.getValue();

            try {
                sp.compute(source);
            } catch (GraphMalformedException e) {
                throw new DataInconsistentException(
                        "shortest paths computation: " + e.getMessage());
            }

            for (Map.Entry<Station, Event> e2 : sinkEvents.entrySet()) {

                Station s2 = e2.getKey();
                Event sink = e2.getValue();

                retval.put(s1, s2, sp.getDistance(sink));

            }
        }

        return retval;

    }

    /**
     * Computes a map for a given {@link LineCollection}: number of undirected
     * {@link Line}s maps to line length. Nice as histrogram data, as in
     * {@link LineConceptEvaluation}.
     * @param lc the given {@link LineCollection}
     * @param skipFrequencyZero true if lines with frequency zero should be
     * excluded; false otherwise. This parameter controls whether the map is
     * computed for the line concept (false) or the whole line pool (true).
     * @return a map: number of undirected {@link Line}s maps to line length.
     */
    public static LinkedHashMap<Integer, Integer>
    undirectedLineLengthDistribution(LineCollection lc, Boolean skipFrequencyZero){

        if(!lc.isUndirected()){
            throw new UnsupportedOperationException(
                    "line collection not undirected");
        }

        LinkedHashMap<Integer, Integer> retval = new LinkedHashMap<Integer, Integer>();

        for(Line line : lc.getUndirectedLines()){
            if(skipFrequencyZero && (line.getFrequency() == null ||
                    line.getFrequency().intValue() == 0)){

                continue;
            }

            Integer length = line.getLinks().size();
            Integer occurance = retval.get(length);

            if(occurance == null){
                occurance = 1;
            }
            else{
                occurance++;
            }

            retval.put(length, occurance);
        }

        return retval;
    }

    /**
     * Computes a map for a given {@link LineCollection}: number of undirected
     * {@link Link}s maps to number of undirected lines. Nice as histrogram
     * data, as in {@link LineConceptEvaluation}.
     * @param lc the given {@link LineCollection}
     * @param skipFrequencyZero true if lines with frequency zero should be
     * excluded; false otherwise. This parameter controls whether the map is
     * computed for the line concept (false) or the whole line pool (true).
     * @return a map: number of undirected {@link Link}s maps to number of
     * undirected lines.
     */
    public static LinkedHashMap<Integer, Integer>
    undirectedLinkUndirectedLineDistribution(LineCollection lc,
            Boolean skipFrequencyZero){

        if(!lc.isUndirected()){
            throw new UnsupportedOperationException(
                    "line collection not undirected");
        }

        LinkedHashMap<Link, Integer> buffer = new LinkedHashMap<Link, Integer>();

        for(Line line : lc.getUndirectedLines()){
            if(skipFrequencyZero && (line.getFrequency() == null ||
                    line.getFrequency().intValue() == 0)){

                continue;
            }

            for(Link prelink : line.getLinks()){
                Link link = prelink.getUndirectedRepresentative();
                Integer currentNumberOfLines = buffer.get(link);

                if(currentNumberOfLines == null){
                    currentNumberOfLines = 1;
                }
                else{
                    currentNumberOfLines++;
                }

                buffer.put(link, currentNumberOfLines);
            }
        }

        LinkedHashMap<Integer, Integer> retval = new LinkedHashMap<Integer, Integer>();

        for(Integer amount : buffer.values()){
            Integer occurance = retval.get(amount);

            if(occurance == null){
                occurance = 1;
            }
            else{
                occurance++;
            }

            retval.put(amount, occurance);
        }

        return retval;
    }

    /**
     * Computes a map for a given {@link LineCollection}: number of
     * {@link Station}s maps to number of undirected lines. Nice as histrogram
     * data, as in {@link LineConceptEvaluation}.
     * @param lc the given {@link LineCollection}
     * @param skipFrequencyZero true if lines with frequency zero should be
     * excluded; false otherwise. This parameter controls whether the map is
     * computed for the line concept (false) or the whole line pool (true).
     * @return a map: number of {@link Station}s maps to number of undirected
     * lines.
     */
    public static LinkedHashMap<Integer, Integer>
    stationUndirectedLineDistribution(LineCollection lc,
            Boolean skipFrequencyZero){

        LinkedHashMap<Station, Integer> buffer = new LinkedHashMap<Station, Integer>();

        for(Line line : lc.getUndirectedLines()){
            if(skipFrequencyZero && (line.getFrequency() == null ||
                    line.getFrequency().intValue() == 0)){

                continue;
            }

            for(Station station : line.getStations()){
                Integer currentNumberOfLines = buffer.get(station);

                if(currentNumberOfLines == null){
                    currentNumberOfLines = 1;
                }
                else{
                    currentNumberOfLines++;
                }

                buffer.put(station, currentNumberOfLines);
            }

        }

        LinkedHashMap<Integer, Integer> retval = new LinkedHashMap<Integer, Integer>();

        for(Integer amount : buffer.values()){
            Integer occurance = retval.get(amount);

            if(occurance == null){
                occurance = 1;
            }
            else{
                occurance++;
            }

            retval.put(amount, occurance);
        }

        return retval;
    }

}
