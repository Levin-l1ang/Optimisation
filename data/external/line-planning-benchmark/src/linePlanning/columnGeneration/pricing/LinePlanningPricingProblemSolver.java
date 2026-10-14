package linePlanning.columnGeneration.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import graph.algorithms.shortestPath.Dijkstra;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Route;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.instance.StationODNode;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblemSolver;
import util.Pair;

public class LinePlanningPricingProblemSolver<T extends NetworkArc>
		extends AbstractPricingProblemSolver<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final DirectedGraph<StationNode, T> network;

	public LinePlanningPricingProblemSolver(DirectedGraph<StationNode, T> network)
	{
		this.network = network;
	}

	@Override
	public List<Pair<LinePlanningColumn, Double>> generateColumns(
			AbstractMasterProblem<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> masterProblem,
			LinePlanningPricingProblem pricingProblem, double reducedCostThreshold, boolean enumerateColumns)
	{
		// Initialise list of columns.
		List<Pair<LinePlanningColumn, Double>> columns = new ArrayList<>();

		// Apply Dijkstra to all OD-pairs departing from this origin simultaneously.
		Station origin = pricingProblem.getOrigin();
		StationODNode originNode = masterProblem.getInstance().getDepartureODNode(origin);
		Dijkstra<StationNode, T> dijkstra = new Dijkstra<>(network, originNode);
		dijkstra.computeDistances();
		for (ODPair pair : pricingProblem.getODPairs())
		{
			// Compute reduced cost.
			double reducedCost = dijkstra.getDistance(pair.getDestinationNode()) - pricingProblem.getODPairDual(pair);
			if (reducedCost < reducedCostThreshold)
			{
				// TODO: Is there a way to get rid of this cast?
				Map<T, Double> arcDualMap = (Map<T, Double>) pricingProblem.getArcDualMap(pair);
				if (arcDualMap.isEmpty())
				{
					// Retrieve arc dual map. If no arc duals are present, this is exact.
					columns.add(constructColumn(pair, reducedCost, dijkstra.getPath(pair.getDestinationNode())));
				}
				else
				{
					// If arc duals are present, we must solve a new pricing problem.
					ArcDualWeightFunction<T> weightFunction = new ArcDualWeightFunction<>(arcDualMap);
					Dijkstra<StationNode, T> pairSpecificDijkstra = new Dijkstra<>(network, originNode, weightFunction);
					pairSpecificDijkstra.setDestination(pair.getDestinationNode());
					pairSpecificDijkstra.computeDistances();
					reducedCost = pairSpecificDijkstra.getDistance(pair.getDestinationNode())
							- pricingProblem.getODPairDual(pair);
					if (reducedCost < reducedCostThreshold)
					{
						columns.add(constructColumn(pair, reducedCost,
								pairSpecificDijkstra.getPath(pair.getDestinationNode())));
					}
				}
			}
		}

		// Returns columns.
		return columns;
	}

	private Pair<LinePlanningColumn, Double> constructColumn(ODPair pair, double reducedCost,
			List<DirectedGraphArc<StationNode, T>> arcs)
	{
		List<T> routeArcs = new ArrayList<>();
		double duration = 0;
		for (DirectedGraphArc<StationNode, T> arc : arcs)
		{
			routeArcs.add(arc.getData());
			duration += arc.getData().getDuration();
		}
		Route route = new Route(pair.getOrigin(), pair.getDestination(), routeArcs, duration);
		return new Pair<>(new RouteColumn(duration, true, pair, route), reducedCost);
	}
}
