package graph.algorithms.spprc;

import graph.structures.digraph.DirectedGraphArc;

public abstract class REF<V, A>
{
	public abstract int[] extendResourceVector(Label<V, A> label, DirectedGraphArc<V, A> arc, boolean forward);

	public abstract boolean isFeasible(int[] resourceVector);

	public abstract boolean isFeasibleAtSink(int[] resourceVector);

	public abstract int[] getEmptyResourceVector();

	public abstract int[] concatenateResourceVectors(DirectedGraphArc<V, A> arc, int[] forwardVector,
			int[] backwardVector);

	public abstract boolean dominates(int[] first, int[] second);

	public boolean dominatesHeuristically(int[] first, int[] second)
	{
		return true;
	}
}
