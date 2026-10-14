package linePlanning.columnGeneration.columns;

import java.util.Objects;

import linePlanning.lineGeneration.Line;

public class LineActivationColumn extends LinePlanningColumn
{
	private final Line line;

	public LineActivationColumn(Line line)
	{
		super(0, false, true);

		this.line = line;
	}

	public Line getLine()
	{
		return line;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(line);
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
		LineActivationColumn other = (LineActivationColumn) obj;
		return Objects.equals(line, other.line);
	}

	@Override
	public String toString()
	{
		return "LineActivationColumn [line=" + line + "]";
	}
}
