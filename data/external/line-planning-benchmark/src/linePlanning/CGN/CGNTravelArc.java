package linePlanning.CGN;

import linePlanning.PTN.Connection;
import linePlanning.lineGeneration.Line;

public class CGNTravelArc extends CGNArc
{
	private final Line line;
	private final Connection lineSegment;
	private final int capacity;
	private final int dwellTime;

	public CGNTravelArc(Line line, Connection lineSegment, int capacity, int dwellTime)
	{
		this.line = line;
		this.lineSegment = lineSegment;
		this.capacity = capacity;
		this.dwellTime = dwellTime;
	}

	@Override
	public double getDuration()
	{
		return lineSegment.getDuration()+dwellTime;
	}

	public double getCapacity()
	{
		return capacity;
	}

	public Line getLine()
	{
		return line;
	}

	public Connection getLineSegment()
	{
		return lineSegment;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((line == null) ? 0 : line.hashCode());
		result = prime * result + ((lineSegment == null) ? 0 : lineSegment.hashCode());
		return result;
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
		CGNTravelArc other = (CGNTravelArc) obj;
		if (line == null)
		{
			if (other.line != null)
				return false;
		}
		else
			if (!line.equals(other.line))
				return false;
		if (lineSegment == null)
		{
			if (other.lineSegment != null)
				return false;
		}
		else
			if (!lineSegment.equals(other.lineSegment))
				return false;
		return true;
	}

	@Override
	public String toString() {
		return "Travel from " + lineSegment.getFrom().getNameFull()+" to "+lineSegment.getTo().getNameFull()
		 + " with duration "+lineSegment.getDuration();
	}

	
}
