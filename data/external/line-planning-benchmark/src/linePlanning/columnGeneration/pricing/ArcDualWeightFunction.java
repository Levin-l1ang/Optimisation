package linePlanning.columnGeneration.pricing;

import java.util.Map;

import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.structures.digraph.DirectedGraphArc;
import linePlanning.instance.StationNode;

public class ArcDualWeightFunction<T> extends AbstractArcWeightFunction<StationNode, T>
{
	private final Map<T, Double> arcDualMap;

	public ArcDualWeightFunction(Map<T, Double> arcDualMap)
	{
		this.arcDualMap = arcDualMap;
	}

	@Override
	public double getArcWeight(DirectedGraphArc<StationNode, T> arc)
	{
		return arc.getWeight() - arcDualMap.getOrDefault(arc.getData(), 0.0);
	}
}
