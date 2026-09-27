package net.lintim.evaluator;

import net.lintim.exception.DataInconsistentException;
import net.lintim.graph.GraphMalformedException;
import net.lintim.graph.ShortestPathsGraph;
import net.lintim.main.PublicTransportationNetworkEvaluation;
import net.lintim.model.Link;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.PublicTransportationNetwork;
import net.lintim.model.Station;
import net.lintim.util.BiLinkedHashMap;
import net.lintim.util.MathHelper;
import net.lintim.util.SinglePair;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;


/**
 * Evaluates different properties of a {@link PublicTransportationNetwork}.
 */
public class PublicTransportationNetworkEvaluator {

	private static boolean feasible = true;
	private static boolean feasibility_yet_checked = false;

	private static class DirectedStation {
		private static final long serialVersionUID = 1L;
		@SuppressWarnings("unused")
		public Station station;

		public DirectedStation(Station station) {
			this.station = station;
		}
	}

	/**
	 * Computes the sum of loads over all directed {@link Link}s of a given
	 * {@link PublicTransportationNetwork}.
	 *
	 * @param ptn
	 *            the given {@link PublicTransportationNetwork}.
	 * @return the sum of loads over all directed {@link Link}s.
	 */
	public static double overallDirectedLoad(PublicTransportationNetwork ptn) {
		double retval = 0.0;
		for (Link link : ptn.getDirectedLinks()) {
			retval += link.getLoad();
		}
		return retval;
	}

	/**
	 * Computes the sum of loads over all undirected {@link Link}s of a given
	 * {@link PublicTransportationNetwork}.
	 *
	 * @param ptn
	 *            the given {@link PublicTransportationNetwork}.
	 * @return the sum of loads over all undirected {@link Link}s.
	 */
	public static double overallUndirectedLoad(PublicTransportationNetwork ptn) {
		if (!ptn.isUndirected()) {
			throw new UnsupportedOperationException(
					"public transportation network not undirected");
		}
		double retval = 0.0;
		for (Link link : ptn.getUndirectedLinks()) {
			retval += link.getLoad();
		}
		return retval;
	}

	/**
	 * Computed the number of passthrough {@link Station}s, i.e. {@link Station}
	 * s with exactly two adjacent {@link Station}s for a given
	 * {@link PublicTransportationNetwork}.
	 *
	 * @param ptn
	 *            the given {@link PublicTransportationNetwork}.
	 * @return the number of passthrough {@link Station}s.
	 */
	public static Integer passthroughStations(PublicTransportationNetwork ptn) {
		Integer counter = 0;

		for (Station station : ptn.getStations()) {
			if (station.getReachableStations().size() == 2) {
				counter++;
			}
		}

		return counter;
	}

	/**
	 * Computed the number of dead end {@link Station}s, i.e. {@link Station}s
	 * with exactly one adjacent {@link Station} for a given
	 * {@link PublicTransportationNetwork}.
	 *
	 * @param ptn
	 *            the given {@link PublicTransportationNetwork}.
	 * @return the number of dead end {@link Station}s.
	 */
	public static Integer deadEndStations(PublicTransportationNetwork ptn) {
		Integer counter = 0;

		for (Station station : ptn.getStations()) {
			if (station.getReachableStations().size() == 1) {
				counter++;
			}
		}

		return counter;
	}

	/**
	 * Computes a lower bound for the sum of duration*passengers, i.e. a lower
	 * bound for the average traveling time for a given
	 * {@link PublicTransportationNetwork} and {@link OriginDestinationMatrix}
	 * by setting all drive and wait durations to lower bounds, change
	 * activities to the wait activities lower bounds, distributing the
	 * passengers along the shortest paths in time and calculating sum of
	 * duration*passengers for these assumptions.
	 *
	 * @param ptn
	 *            the given {@link PublicTransportationNetwork}.
	 * @param od
	 *            the given {@link OriginDestinationMatrix}.
	 * @param minimalWaitingTime
	 *            the given minimal waiting time.
	 * @return a lower bound for the average traveling time.
	 * @throws DataInconsistentException
	 */
	public static Double averageTravelingTimeLowerBound(
			PublicTransportationNetwork ptn, OriginDestinationMatrix od,
			Double minimalWaitingTime) throws DataInconsistentException {

		Double retval = 0.0;

		BiLinkedHashMap<Station, Station, Double> shortestPathLowerBoundLength = computeShortestPathLowerBoundLengths(
				ptn, od, minimalWaitingTime);

		BiLinkedHashMap<Station, Station, Double> odData = od.getMatrix();

		for (Entry<Station, LinkedHashMap<Station, Double>> e1 : shortestPathLowerBoundLength
				.entrySet()) {

			for (Entry<Station, Double> e2 : e1.getValue().entrySet()) {
				retval += e2.getValue() * odData.get(e1.getKey(), e2.getKey());
			}
		}

		return retval;
	}

	private static BiLinkedHashMap<Station, Station, Double> computeShortestPathLowerBoundLengths(
			PublicTransportationNetwork ptn, OriginDestinationMatrix od,
			Double minimalWaitingTime) throws DataInconsistentException {

		BiLinkedHashMap<Station, Station, Double> retval = new BiLinkedHashMap<Station, Station, Double>();

		ShortestPathsGraph<DirectedStation, Link> sp = new ShortestPathsGraph<DirectedStation, Link>();

		LinkedHashMap<Station, SinglePair<DirectedStation>> directedStationMap = new LinkedHashMap<Station, SinglePair<DirectedStation>>();

		LinkedHashSet<Station> stations = ptn.getStations();

		for (Station station : stations) {
			DirectedStation incomingLinks = new DirectedStation(station);
			DirectedStation outgoingLinks = new DirectedStation(station);
			directedStationMap.put(station, new SinglePair<DirectedStation>(
					incomingLinks, outgoingLinks));

			sp.addVertex(incomingLinks);
			sp.addVertex(outgoingLinks);
			sp.addEdge(null, incomingLinks, outgoingLinks, minimalWaitingTime);
		}

		for (Link link : ptn.getDirectedLinks()) {
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
						feasible = false;
					}

					retval.put(s1, s2, distance);

				}

			}

		}

		feasibility_yet_checked=true;
		return retval;

	}

	/**
	 * Computes the average traveling time in the PTN
	 * @param ptn the PublicTransportationNetwork used.
	 * @param od the OD-Matrix used.
	 * @param minimalWaitingTime the Time approximatly spend at any station
	 * @return total travel time / number of passengers
	 */
	public static double ptnTimeAverage(PublicTransportationNetwork ptn,
			OriginDestinationMatrix od, Double minimalWaitingTime)
			throws DataInconsistentException {

		Double retval = 0.0;
		Double passengers = 0.0;
		Double passengers_od = 0.0;

		BiLinkedHashMap<Station, Station, Double> ptnTime =
				computeShortestPathLowerBoundLengths(ptn, od, minimalWaitingTime);

		BiLinkedHashMap<Station, Station, Double> odData = od.getMatrix();

		for (Entry<Station, LinkedHashMap<Station, Double>> e1 :
				ptnTime.entrySet()) {

			for (Entry<Station, Double> e2 : e1.getValue().entrySet()) {
				passengers_od = odData.get(e1.getKey(), e2.getKey());
				retval += e2.getValue() * passengers_od;
				passengers += passengers_od;
			}
		}

		return retval/passengers;

	}


/*	private static BiLinkedHashMap<Station, Station, Double>
		computePtnTime(PublicTransportationNetwork ptn, OriginDestinationMatrix od)
		throws DataInconsistentException {

		BiLinkedHashMap<Station, Station, Double> retval = new BiLinkedHashMap<Station, Station, Double>();

		ShortestPathsGraph<Station, Link> sp = new ShortestPathsGraph<Station, Link>();

		LinkedHashSet<Station> stations = ptn.getStations();

		for (Station station : stations) {
			sp.addVertex(station);
		}

		for (Link link : ptn.getDirectedLinks()) {
			sp.addEdge(link,link.getFromStation(),
					link.getToStation(),
					link.getLowerBound());
		}

		for (Station s1 : stations) {
			try {
				sp.compute(s1);
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
							.getDistance(s2);

					if (distance == Double.POSITIVE_INFINITY) {
						/*throw new DataInconsistentException("there is no "
								+ "path from station " + s1.getIndex() + " to "
								+ s2.getIndex());*//*
						feasible = false;
					}

					retval.put(s1, s2, distance);

				}

			}

		}

		feasibility_yet_checked=true;
		return retval;

	}*/


	/**
	 * Returns wheter there is a way for each OD-pair through the PTN.
	 * @param ptn the given {@link PublicTransportationNetwork}.
	 * @param od the given {@link OriginDestinationMatrix}.
	 * @return Whether all OD-pair can travel.
	 */
	public static Boolean ptnFeasibleOd(PublicTransportationNetwork ptn,
			OriginDestinationMatrix od, Double minimalWaitingTime)
			throws DataInconsistentException {
		if(!feasibility_yet_checked)
			ptnTimeAverage(ptn, od, minimalWaitingTime);
		return feasible;
	}

	/**
	 * Computes a map for a given {@link PublicTransportationNetwork}: number of
	 * {@link Station}s maps to degree, i.e. the number of adjacent stations.
	 * Nice as histrogram data, as in
	 * {@link PublicTransportationNetworkEvaluation}.
	 *
	 * @param ptn
	 *            the given {@link PublicTransportationNetwork}.
	 * @return a map: number of {@link Station}s maps to degree.
	 */
	public static LinkedHashMap<Integer, Integer> stationDegreeDistribution(
			PublicTransportationNetwork ptn) {

		LinkedHashMap<Integer, Integer> retval = new LinkedHashMap<Integer, Integer>();

		for (Station s1 : ptn.getStations()) {
			LinkedHashSet<Station> reachableStations = s1
					.getReachableStations();

			Integer degree = reachableStations.size();
			Integer occurance = retval.get(degree);

			if (occurance == null) {
				occurance = 1;
			} else {
				occurance++;
			}

			retval.put(degree, occurance);

		}

		return retval;
	}

}
