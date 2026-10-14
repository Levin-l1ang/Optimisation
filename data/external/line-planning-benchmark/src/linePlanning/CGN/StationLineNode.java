package linePlanning.CGN;

import java.util.Objects;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.lineGeneration.Line;

public class StationLineNode extends StationNode
{
	private final Line line;
	private final boolean isForward;

	public StationLineNode(Station station, Line line, boolean forward)
	{
		super(station);

		this.line = line;
		this.isForward = forward;
	}

	public boolean isForward()
	{
		return isForward;
	}

	public Line getLine()
	{
		return line;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(isForward, line);
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
		StationLineNode other = (StationLineNode) obj;
		return isForward == other.isForward && Objects.equals(line, other.line);
	}
}
