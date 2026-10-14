package linePlanning.PTN;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.ODPair;
import linePlanning.instance.Route;
import linePlanning.instance.Station;
import linePlanning.lineGeneration.Line;

public class PTNRoute extends Route
{
	private final boolean isDirect;
	private final ODPair odPair;
	private final int nrTransfers; //can be more than 1 based on DPTN calculations
	// for a direct route, we can store the lines that support this route
	private List<Line> supportingLines;

	public PTNRoute(Station origin, Station destination, List<Connection> arcs, double duration, ODPair od,
			int nrTransfers)
	{
		super(origin, destination, arcs, duration);

		this.nrTransfers = nrTransfers;
		this.isDirect = nrTransfers == 0;
		this.odPair = od;
		checkDuration();
	}

	private void checkDuration() {
		double sumSegments = 0;
		for(NetworkArc c: arcs) {
			sumSegments += c.getDuration();
		}
		// (k-1) intermediate stops dwell, except transfer stations (absorbed into transfer arc)
		int nrDwells = arcs.size() - 1 - nrTransfers;
		double transferCost = nrTransfers * LinePlanningParameters.TRANSFER_PENALTY;
		double expectedDuration = sumSegments + nrDwells * LinePlanningParameters.DWELL_TIME + transferCost;
		if (Math.abs(getDuration() - expectedDuration) > 0.001) {
			throw new Error("PTNRoute duration mismatch: stored=" + getDuration() + " expected=" + expectedDuration
					+ " (sumSegments=" + sumSegments + ", nrDwells=" + nrDwells + ", dwellTime=" + LinePlanningParameters.DWELL_TIME
					+ ", nrTransfers=" + nrTransfers + ", transferPenalty=" + LinePlanningParameters.TRANSFER_PENALTY
					+ ") route=" + this);
		}
	}

	public ODPair getOdPair()
	{
		return odPair;
	}

	public boolean isDirect()
	{
		return isDirect;
	}
	
	public void computeSupportingLines(List<Line> linePool)
	{
		if (!isDirect)
		{
			throw new Error("Can only compute support for direct lines");
		}
		supportingLines = new ArrayList<>();
		for (Line line : linePool)
		{
			if (line.supports(this))
			{
				supportingLines.add(line);
			}
		}
	}

	public List<Line> getSupportingLines()
	{
		return supportingLines;
	}

	@Override
	public int getNrTransfers()
	{
		return nrTransfers;
	}

	public List<Connection> getConnections()
	{
		return (List<Connection>) (List<?>) arcs;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(isDirect, odPair);
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
		PTNRoute other = (PTNRoute) obj;
		return isDirect == other.isDirect && Objects.equals(odPair, other.odPair);
	}

	@Override
	public String toString() {
		return "PTNRoute [isDirect=" + isDirect +" arcs=" + arcs + "]";
	}

	
}
