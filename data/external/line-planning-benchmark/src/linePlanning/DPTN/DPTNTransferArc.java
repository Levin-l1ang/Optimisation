package linePlanning.DPTN;

import java.util.Objects;

import linePlanning.instance.Station;
import linePlanning.instance.StationNode;

public class DPTNTransferArc extends DPTNArc
{
	private final Station station;
	private final int transferTime;
	private final int transferPenalty;

	public DPTNTransferArc(StationNode from, StationNode to, Station station, int transferTime, int transferPenalty)
	{
		super(from, to, DEPTNArcType.TRANSFER, transferTime + transferPenalty, 0, 0);

		this.station = station;
		this.transferTime = transferTime;
		this.transferPenalty = transferPenalty;
	}

	public Station getStation()
	{
		return station;
	}

	public int getTransferTime()
	{
		return transferTime;
	}

	@Override
	public String toString()
	{
		return "DPTNTransferArc [station=" + station + ", transferTime=" + transferTime + ", transferPenalty="
				+ transferPenalty + "]";
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(station, transferPenalty, transferTime);
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
		DPTNTransferArc other = (DPTNTransferArc) obj;
		return Objects.equals(station, other.station) && transferPenalty == other.transferPenalty
				&& transferTime == other.transferTime;
	}
}
