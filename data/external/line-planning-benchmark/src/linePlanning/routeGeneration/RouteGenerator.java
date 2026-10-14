package linePlanning.routeGeneration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graph.algorithms.search.DFS;
import graph.algorithms.shortestPath.Dijkstra;
import graph.algorithms.shortestPath.arcWeightFunctions.DefaultArcWeightFunction;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.activation.Activation;
import graph.structures.digraph.activation.DefaultActivationFunction;
import linePlanning.CGN.CGN;
import linePlanning.PTN.Connection;
import linePlanning.CGN.CGNArc;
import linePlanning.CGN.CGNTransferArc;
import linePlanning.DPTN.DPTN;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.DPTN.DPTNTransferArc;
import linePlanning.PTN.PTNRoute;
import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Route;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import util.Reader;

public class RouteGenerator
{
	private final static int MAX_NUMBER_TRANSFERS = 2;

	private final LinePlanningInstance instance;
	private final DPTN DPTN;
	private final CGN CGN;
	private final int maxDetourMinutes;

	private Map<ODPair, List<DPTNRoute>> routes;
	private final Map<ODPair, Double> shortestPathDurations = new HashMap<>();

	public RouteGenerator(LinePlanningInstance instance, String routeGenerationFile)
	{
		this.instance = instance;
		this.DPTN = instance.getDPTN();
		this.CGN = instance.getCGN();
		try
		{
			this.maxDetourMinutes = Reader.readInt(routeGenerationFile);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to read route generation parameters from " + routeGenerationFile, e);
		}
	}

	public int computeMaximumNumberTransfers(StationNode from, StationNode to)
	{
		Dijkstra<StationNode, DPTNArc> dijkstra = new Dijkstra<StationNode, DPTNArc>(DPTN, from);
		dijkstra.computeDistances();

		// Compute number of transfers in shortest path.
		int transfers = 0;
		for (DirectedGraphArc<StationNode, DPTNArc> arc : dijkstra.getPath(to))
		{
			if (arc.getData() instanceof DPTNTransferArc)
			{
				transfers++;
			}
		}
		int maximumNumberTransfers = Math.min(MAX_NUMBER_TRANSFERS, transfers + 1);
		return maximumNumberTransfers;
	}

	public List<DPTNRoute> getRoutes(ODPair pair, boolean filterDominatedTransfers)
	{
		Activation.getActivation().setActivationFunction(new RouteActivationFunction());

		Station origin = pair.getOrigin();
		Station destination = pair.getDestination();

		StationNode from = instance.getDepartureODNode(origin);
		StationNode to = instance.getArrivalODNode(destination);

		List<DPTNRoute> routes = new ArrayList<>();

		Dijkstra<StationNode, DPTNArc> dijkstra = new Dijkstra<StationNode, DPTNArc>(DPTN, from);
		dijkstra.computeDistances();
		if (dijkstra.containsPath(to))
		{
			double distance = dijkstra.getDistance(to);
			shortestPathDurations.put(pair, distance);
			double threshold = distance + maxDetourMinutes;
			int maximumNumberTransfers = computeMaximumNumberTransfers(from, to);
			// Enumerate paths.
			DFS<StationNode, DPTNArc> DFS = new DFS<>();
			for (List<DirectedGraphArc<StationNode, DPTNArc>> path : DFS.applyDFS(DPTN, from, to, threshold,
					new DefaultArcWeightFunction<>()))
			{
				double duration = 0;
				List<DPTNArc> arcPath = new ArrayList<>();
				for (DirectedGraphArc<StationNode, DPTNArc> arc : path)
				{
					duration += arc.getData().getDuration();
					arcPath.add(arc.getData());
				}
				DPTNRoute route = new DPTNRoute(origin, destination, arcPath, duration);
				if (satisfiesNumberTransfers(route, maximumNumberTransfers))
				{
					routes.add(route);
				}
			}
		}
		Activation.getActivation().setActivationFunction(new DefaultActivationFunction());

		if (filterDominatedTransfers)
		{
			Map<List<Connection>, Integer> minTransfers = new HashMap<>();
			for (DPTNRoute r : routes)
				minTransfers.merge(r.getConnectionPath(), r.getNrTransfers(), Math::min);
			List<DPTNRoute> filtered = new ArrayList<>();
			for (DPTNRoute r : routes)
				if (r.getNrTransfers() == minTransfers.get(r.getConnectionPath()))
					filtered.add(r);
			return filtered;
		}
		return routes;
	}

	public List<PTNRoute> toPTNRoutes(List<DPTNRoute> dptnRoutes, ODPair od, boolean allDirect)
	{
		List<PTNRoute> result = new ArrayList<>();
		for (DPTNRoute r : dptnRoutes)
		{
			if (allDirect)
			{
				result.add(new PTNRoute(r.getOrigin(), r.getDestination(), r.getConnectionPath(),
						r.getDuration(), od, r.getNrTransfers()));
			}
			else if (r.isDirect())
			{
				result.add(new PTNRoute(r.getOrigin(), r.getDestination(), r.getConnectionPath(),
						r.getDuration(), od, 0));
				result.add(new PTNRoute(r.getOrigin(), r.getDestination(), r.getConnectionPath(),
						r.getDuration() + LinePlanningParameters.TRANSFER_PENALTY - LinePlanningParameters.DWELL_TIME, od, 1));
			}
			else
			{
				if (r.getNrTransfers() == 0)
				{
					throw new Error("What? " + r);
				}
				result.add(new PTNRoute(r.getOrigin(), r.getDestination(), r.getConnectionPath(),
						r.getDuration(), od, r.getNrTransfers()));
			}
		}
		return result;
	}

	public List<Route> getCGNRoutes(ODPair pair)
	{
		Activation.getActivation().setActivationFunction(new RouteActivationFunction());
		Station origin = pair.getOrigin();
		Station destination = pair.getDestination();

		StationNode from = instance.getDepartureODNode(origin);
		StationNode to = instance.getArrivalODNode(destination);

		List<Route> routes = new ArrayList<>();

		Dijkstra<StationNode, CGNArc> dijkstra = new Dijkstra<StationNode, CGNArc>(CGN, from);
		dijkstra.computeDistances();
		if (dijkstra.containsPath(to))
		{
			double distance = dijkstra.getDistance(to);
			double threshold = distance + maxDetourMinutes;
			int maximumNumberTransfers = computeMaximumNumberTransfers(from, to);

			DFS<StationNode, CGNArc> DFS = new DFS<>();
			for (List<DirectedGraphArc<StationNode, CGNArc>> path : DFS.applyDFS(CGN, from, to, threshold,
					new DefaultArcWeightFunction<>()))
			{
				double duration = 0;
				List<CGNArc> arcPath = new ArrayList<>();
				for (DirectedGraphArc<StationNode, CGNArc> arc : path)
				{
					duration += arc.getData().getDuration();
					arcPath.add(arc.getData());
				}
				Route route = new Route(origin, destination, arcPath, duration);

				// Check number of transfers.
				if (satisfiesNumberTransfers(route, maximumNumberTransfers))
				{
					routes.add(route);
				}
			}
		}
		return routes;
	}

	public Map<ODPair, Double> getShortestPathDurations()
	{
		return shortestPathDurations;
	}

	public static boolean satisfiesNumberTransfers(Route route, int maximumNumberTransfers)
	{
		int numberTransfers = 0;
		for (NetworkArc arc : route.getArcs())
		{
			if (arc instanceof DPTNTransferArc)
			{
				numberTransfers++;
			}
			if (arc instanceof CGNTransferArc)
			{
				numberTransfers++;
			}
		}
		return numberTransfers <= maximumNumberTransfers;
	}
}
