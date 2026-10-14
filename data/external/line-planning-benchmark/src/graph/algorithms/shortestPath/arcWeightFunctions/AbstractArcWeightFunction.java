package graph.algorithms.shortestPath.arcWeightFunctions;

import graph.structures.digraph.DirectedGraphArc;

public abstract class AbstractArcWeightFunction<V, A>
{
	public abstract double getArcWeight(DirectedGraphArc<V, A> arc);
}
