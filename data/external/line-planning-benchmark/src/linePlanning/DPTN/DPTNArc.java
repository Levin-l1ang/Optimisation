package linePlanning.DPTN;

import java.util.Objects;

import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.StationNode;

public class DPTNArc implements NetworkArc
{
	private final DEPTNArcType arcType;
	private final StationNode from;
	private final StationNode to;

	private final double duration;
	private final double cost;
	private final int maxFrequency;

	public enum DEPTNArcType
	{
		DWELL("DWELL"), BOARD("BOARD"), DRIVE("DRIVE"), TRANSFER("TRANSFER"),
		TERMINAL("TERMINAL");

		public String name;

		private DEPTNArcType(String name)
		{
			this.name = name;
		}
	}

	public DPTNArc(StationNode from, StationNode to, DEPTNArcType arcType)
	{
		this.from = from;
		this.to = to;
		this.arcType = arcType;

		this.duration = 0;
		this.cost = 0;
		this.maxFrequency = 0;
	}

	public DPTNArc(StationNode from, StationNode to, DEPTNArcType arcType, double duration, double cost,
			int maxFrequency)
	{
		this.from = from;
		this.to = to;
		this.arcType = arcType;

		this.duration = duration;
		this.cost = cost;
		this.maxFrequency = maxFrequency;
	}

	public StationNode getFrom()
	{
		return from;
	}

	public StationNode getTo()
	{
		return to;
	}

	public DEPTNArcType getArcType()
	{
		return arcType;
	}

	public double getDuration()
	{
		return duration;
	}

	public double getCost()
	{
		return cost;
	}

	public int getMaxFrequency()
	{
		return maxFrequency;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(arcType, cost, duration, from, maxFrequency, to);
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
		DPTNArc other = (DPTNArc) obj;
		return arcType == other.arcType && Double.doubleToLongBits(cost) == Double.doubleToLongBits(other.cost)
				&& Double.doubleToLongBits(duration) == Double.doubleToLongBits(other.duration)
				&& Objects.equals(from, other.from) && maxFrequency == other.maxFrequency
				&& Objects.equals(to, other.to);
	}

	@Override
	public String toString()
	{
		return "DPTNArc [arcType=" + arcType + ", from=" + from + ", to=" + to + ", duration=" + duration + ", cost="
				+ cost + ", maxFrequency=" + maxFrequency + "]";
	}
}
