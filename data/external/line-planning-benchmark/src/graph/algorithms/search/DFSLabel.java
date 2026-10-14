package graph.algorithms.search;

import java.util.Objects;

import graph.structures.digraph.DirectedGraphArc;

public class DFSLabel<V, A>
{
	private final DFSLabel<V, A> previousLabel;
	private final DirectedGraphArc<V, A> arc;
	private final double cost;

	public DFSLabel(DFSLabel<V, A> previousLabel, DirectedGraphArc<V, A> arc, double cost)
	{
		this.previousLabel = previousLabel;
		this.arc = arc;
		this.cost = cost;
	}

	public DFSLabel<V, A> getPreviousLabel()
	{
		return previousLabel;
	}

	public DirectedGraphArc<V, A> getArc()
	{
		return arc;
	}

	public double getCost()
	{
		return cost;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(arc, cost, previousLabel);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DFSLabel other = (DFSLabel) obj;
		return Objects.equals(arc, other.arc) && Double.doubleToLongBits(cost) == Double.doubleToLongBits(other.cost)
				&& Objects.equals(previousLabel, other.previousLabel);
	}
}
