package linePlanning.DPTN;

import java.util.Objects;

import linePlanning.instance.StationNode;

public class DPTNDwellArc extends DPTNArc
{
	private final int type;
	private final int capacity;

	public DPTNDwellArc(StationNode from, StationNode to, int dwellTime, int capacity, double cost, int type,
			int maxFrequency)
	{
		super(from, to, DEPTNArcType.DWELL, dwellTime, cost, maxFrequency);
		this.capacity = capacity;
		this.type = type;
	}

	public int getType()
	{
		return type;
	}
	

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(type);
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
		DPTNDwellArc other = (DPTNDwellArc) obj;
		return type == other.type;
	}

	@Override
	public String toString()
	{
		return "DPTNDwellArc [type=" + type + "]";
	}

	public int getCapacity() {
		return capacity;
	}
}
