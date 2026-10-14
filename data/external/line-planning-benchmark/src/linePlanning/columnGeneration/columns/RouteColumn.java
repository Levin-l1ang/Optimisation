package linePlanning.columnGeneration.columns;

import java.util.Objects;

import linePlanning.instance.ODPair;
import linePlanning.instance.Route;

public class RouteColumn extends LinePlanningColumn
{
	protected final ODPair ODPair;
	private final Route route;

	public RouteColumn(double coefficient, boolean isRemovableColumn, ODPair ODPair, Route route)
	{
		super(coefficient, isRemovableColumn, false);

		this.ODPair = ODPair;
		this.route = route;
	}

	public Route getRoute()
	{
		return route;
	}

	public ODPair getODPair()
	{
		return ODPair;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(ODPair, route);
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
		RouteColumn other = (RouteColumn) obj;
		return Objects.equals(ODPair, other.ODPair) && Objects.equals(route, other.route);
	}

	@Override
	public String toString()
	{
		return "Route column " + route;
	}
}
