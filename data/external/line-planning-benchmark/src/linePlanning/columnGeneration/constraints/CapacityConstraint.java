package linePlanning.columnGeneration.constraints;

import java.util.List;
import java.util.Objects;

import graph.structures.digraph.DirectedGraphArc;
import linePlanning.CGN.CGNArc;
import linePlanning.CGN.CGNTravelArc;
import linePlanning.DPTN.DPTNArc;
import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.columns.RouteSlackColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.StationNode;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class CapacityConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final DirectedGraphArc<StationNode, ? extends NetworkArc> graphArc;
	private final NetworkArc arc;
	private final int capacity;

	public CapacityConstraint(DirectedGraphArc<StationNode, ? extends NetworkArc> graphArc, int capacity)
	{
		super(ConstraintType.LESSER, 0);

		this.graphArc = graphArc;
		this.arc = graphArc.getData();
		this.capacity = capacity;
	}

	public NetworkArc getArc()
	{
		return graphArc.getData();
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			Line line = lineColumn.getLine();
			if (arc instanceof Connection)
			{
				// Connection Network
				List<Connection> forward = line.getConnectionPathForward();
				List<Connection> backward = line.getConnectionPathBackward();
				return forward.contains(arc) || backward.contains(arc);
			}
			if (arc instanceof DPTNArc)
			{
				// DPTN
				List<DPTNArc> forward = line.getArcPathForward();
				List<DPTNArc> backward = line.getArcPathBackward();
				return forward.contains(arc) || backward.contains(arc);
			}
			if (arc instanceof CGNArc)
			{
				// Change-and-Go Network, arc must be a travel arc
				CGNTravelArc cgnArc = (CGNTravelArc) arc;
				return line.equals(cgnArc.getLine());
			}
		}
		if (column instanceof RouteColumn)
		{
			if (column instanceof RouteSlackColumn)
			{
				return false;
			}
			RouteColumn routeColumn = (RouteColumn) column;
			return routeColumn.getRoute().getArcs().contains(arc);
		}
		return false;
	}

	@Override
	public double getCoefficient(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			return -1 * capacity;
		}
		return 1;
	}

	@Override
	public void updateGenericDuals(LinePlanningInstance instance, double dual)
	{
		graphArc.addDual(dual, 0);
	}

	@Override
	public void updatePricingProblemDuals(LinePlanningPricingProblem pricingProblem, double dual)
	{
		// Do nothing.
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(arc, capacity, graphArc);
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
		CapacityConstraint other = (CapacityConstraint) obj;
		return Objects.equals(arc, other.arc) && capacity == other.capacity && Objects.equals(graphArc, other.graphArc);
	}
}
