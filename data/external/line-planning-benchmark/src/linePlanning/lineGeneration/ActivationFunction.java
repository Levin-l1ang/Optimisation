package linePlanning.lineGeneration;

import graph.structures.digraph.DirectedGraphArc;

public interface ActivationFunction<V, A>
{
	public boolean isActiveNode(V v);

	public boolean isActiveArc(DirectedGraphArc<V, A> arc);
}
