package graph.structures.digraph.activation;

import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;

public class DefaultActivationFunction implements ActivationFunction
{
	@Override
	public boolean isActiveNode(DirectedGraphNodeIndex node)
	{
		return true;
	}

	@Override
	public boolean isActiveArc(DirectedGraphArc<?, ?> arc)
	{
		return true;
	}
}
