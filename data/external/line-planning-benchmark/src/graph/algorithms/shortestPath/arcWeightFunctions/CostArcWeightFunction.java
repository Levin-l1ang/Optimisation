package graph.algorithms.shortestPath.arcWeightFunctions;

import graph.structures.digraph.DirectedGraphArc;

public class CostArcWeightFunction<V, A> extends AbstractArcWeightFunction<V, A>
{
	@Override
	public double getArcWeight(DirectedGraphArc<V, A> arc)
	{
		return arc.getCost();
	}
}
