package graph.algorithms.spprc;

import java.util.Objects;

import graph.structures.digraph.DirectedGraphArc;

public class Label<V, A>
{
	private final int depth;
	private final Label<V, A> previousLabel;
	private final DirectedGraphArc<V, A> arc;
	private final int[] resourceVector;
	private final double cost;
	private final double completionBound;

	public Label(Label<V, A> previousLabel, DirectedGraphArc<V, A> arc, int[] resourceVector, double cost,
			double completionBound)
	{
		this.depth = (previousLabel != null) ? previousLabel.getDepth() + 1 : 0;
		this.previousLabel = previousLabel;
		this.arc = arc;
		this.resourceVector = resourceVector;
		this.cost = cost;
		this.completionBound = completionBound;
	}

	public int getDepth()
	{
		return depth;
	}

	public Label<V, A> getPreviousLabel()
	{
		return previousLabel;
	}

	public DirectedGraphArc<V, A> getArc()
	{
		return arc;
	}

	public int[] getResourceVector()
	{
		return resourceVector;
	}

	public double getCost()
	{
		return cost;
	}

	public double getCompletionBound()
	{
		return completionBound;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(arc, completionBound, cost, depth, previousLabel, resourceVector);
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
		Label other = (Label) obj;
		return Objects.equals(arc, other.arc)
				&& Double.doubleToLongBits(completionBound) == Double.doubleToLongBits(other.completionBound)
				&& Double.doubleToLongBits(cost) == Double.doubleToLongBits(other.cost) && depth == other.depth
				&& Objects.equals(previousLabel, other.previousLabel)
				&& Objects.equals(resourceVector, other.resourceVector);
	}
}
