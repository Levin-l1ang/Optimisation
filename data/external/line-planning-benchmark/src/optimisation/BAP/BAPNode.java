package optimisation.BAP;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public class BAPNode<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	protected final BAPNode<T, U, V> parent;
	protected final int depth;
	protected AbstractSolution<T, U, V> solution;
	protected double lowerBound;
	protected double upperBound;
	protected Set<AbstractBranchingDecision<T, U, V>> branchingDecisions;
	protected AbstractReducedCostFixingDecision<T, U, V> fixingDecision;
	protected List<U> potentialColumns;

	public BAPNode(BAPNode<T, U, V> parent)
	{
		this.parent = parent;
		if (parent != null)
		{
			this.depth = parent.getDepth() + 1;
		}
		else
		{
			this.depth = 0;
		}
		this.branchingDecisions = new LinkedHashSet<>();
	}

	public void addBranchingDecision(AbstractBranchingDecision<T, U, V> branchingDecision)
	{
		branchingDecisions.add(branchingDecision);
	}

	public BAPNode<T, U, V> getParent()
	{
		return parent;
	}

	public int getDepth()
	{
		return depth;
	}

	public void setPotentialColumns(List<U> potentialColumns)
	{
		this.potentialColumns = potentialColumns;
	}

	public List<U> getPotentialColumns()
	{
		return potentialColumns;
	}

	public void setSolution(AbstractSolution<T, U, V> solution)
	{
		this.solution = solution;
	}

	public AbstractSolution<T, U, V> getSolution()
	{
		return solution;
	}

	public void setLowerBound(double lowerBound)
	{
		this.lowerBound = lowerBound;
	}

	public double getLowerBound()
	{
		return lowerBound;
	}

	public void setUpperBound(double upperBound)
	{
		this.upperBound = upperBound;
	}

	public double getUpperBound()
	{
		return upperBound;
	}

	public Set<AbstractBranchingDecision<T, U, V>> getBranchingDecisions()
	{
		return branchingDecisions;
	}

	public void setReducedCostFixingDecision(AbstractReducedCostFixingDecision<T, U, V> fixingDecision)
	{
		this.fixingDecision = fixingDecision;
	}

	public AbstractReducedCostFixingDecision<T, U, V> getReducedCostFixingDecision()
	{
		return fixingDecision;
	}
}
