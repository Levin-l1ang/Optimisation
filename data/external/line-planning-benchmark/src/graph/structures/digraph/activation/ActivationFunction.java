package graph.structures.digraph.activation;

import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;

public interface ActivationFunction
{
	public boolean isActiveNode(DirectedGraphNodeIndex node);

	public boolean isActiveArc(DirectedGraphArc<?, ?> arc);
}
