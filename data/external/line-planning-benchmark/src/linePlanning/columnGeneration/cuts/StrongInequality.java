package linePlanning.columnGeneration.cuts;

import java.util.Objects;
import java.util.Set;

import linePlanning.CGN.CGNArc;
import linePlanning.CGN.CGNTravelArc;
import linePlanning.DPTN.DPTNArc;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.columns.RouteSlackColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class StrongInequality
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Station origin;
	private final Set<ODPair> pairs;
	private final double totalVolume;
	private final NetworkArc arc;

	public StrongInequality(Station origin, Set<ODPair> pairs, double totalVolume, NetworkArc arc)
	{
		super(ConstraintType.LESSER, 0);

		this.origin = origin;
		this.pairs = pairs;
		this.totalVolume = totalVolume;
		this.arc = arc;
	}

	public Station getOrigin()
	{
		return origin;
	}

	public Set<ODPair> getPairs()
	{
		return pairs;
	}

	public NetworkArc getArc()
	{
		return arc;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof RouteColumn)
		{
			if (column instanceof RouteSlackColumn)
			{
				return false;
			}
			RouteColumn routeColumn = (RouteColumn) column;
			return pairs.contains(routeColumn.getODPair()) && routeColumn.getRoute().getArcs().contains(arc);
		}
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			Line line = lineColumn.getLine();
			if (arc instanceof CGNArc)
			{
				CGNTravelArc travelArc = (CGNTravelArc) arc;
				return line.equals(travelArc.getLine());
			}
			if (arc instanceof DPTNArc)
			{
				return line.getArcPathForward().contains(arc) || line.getArcPathBackward().contains(arc);
			}
		}
		return false;
	}

	@Override
	public double getCoefficient(LinePlanningColumn column)
	{
		if (column instanceof RouteColumn)
		{
			return 1;
		}
		return -1 * totalVolume;
	}

	@Override
	public void updateGenericDuals(LinePlanningInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(LinePlanningPricingProblem pricingProblem, double dual)
	{
		if (pricingProblem.getOrigin().equals(origin))
		{
			for (ODPair pair : pairs)
			{
				pricingProblem.addArcDual(pair, arc, dual);
			}
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(arc, origin, pairs, totalVolume);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		StrongInequality other = (StrongInequality) obj;
		return Objects.equals(arc, other.arc) && Objects.equals(origin, other.origin)
				&& Objects.equals(pairs, other.pairs)
				&& Double.doubleToLongBits(totalVolume) == Double.doubleToLongBits(other.totalVolume);
	}
}
