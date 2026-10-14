package linePlanning.columnGeneration.columns;

import linePlanning.instance.ODPair;

public class RouteSlackColumn extends RouteColumn
{
	public RouteSlackColumn(double coefficient, ODPair ODPair)
	{
		super(coefficient, false, ODPair, null);
	}

	@Override
	public int hashCode()
	{
		return super.hashCode();
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
		return true;
	}

	@Override
	public String toString()
	{
		return "Slack column: " + ODPair.toString();
	}
}
