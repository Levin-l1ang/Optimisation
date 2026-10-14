package graph.algorithms.spprc;

import graph.structures.digraph.DirectedGraphNodeIndex;

public abstract class AbstractResourceCompletionTest<V extends DirectedGraphNodeIndex, A>
{
	public abstract boolean isFeasible(Label<V, A> label, boolean forward);
}
