package linePlanning.DPTN;

import java.util.ArrayList;
import java.util.List;

import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.Route;
import linePlanning.instance.Station;
import linePlanning.instance.Track;
import linePlanning.lineGeneration.Line;

public class DPTNRoute extends Route
{
	private final boolean isDirect;
	private final List<Connection> connectionPath;
	private final int nrTransfers;
	private final List<Station> transferStations;

	// for a direct route, we can store the lines that support this route
	private List<Line> supportingLines;

	public DPTNRoute(Station origin, Station destination, List<DPTNArc> arcs, double duration)
	{
		super(origin, destination, arcs, duration);

		this.connectionPath = new ArrayList<>();
		boolean hasTransfer = false;
		double totDuration = 0;
		int transferCount = 0;
		for (DPTNArc arc : arcs)
		{
			totDuration += arc.getDuration();
			if (arc instanceof DPTNDrivingArc)
			{
				DPTNDrivingArc dArc = (DPTNDrivingArc) arc;
				connectionPath.add(dArc.getConnection());
			}
			else
				if (arc instanceof DPTNTransferArc)
				{
					hasTransfer = true;
					transferCount++;
				}
		}
		this.isDirect = !hasTransfer;
		if (totDuration != duration)
		{
			throw new Error("Oops");
		}
		this.nrTransfers = transferCount;
		this.transferStations = computeTransferStations();
	}

	public boolean isDirect()
	{
		return isDirect;
	}

	private List<Station> computeTransferStations()
	{
		List<Station> transferStations = new ArrayList<>();
		for (NetworkArc arc : arcs)
		{
			if (arc instanceof DPTNTransferArc)
			{
				DPTNTransferArc transferArc = (DPTNTransferArc) (arc);
				transferStations.add(transferArc.getStation());
			}
		}
		return transferStations;
	}

	@Override
	public int getNrTransfers()
	{
		return nrTransfers;
	}

	public List<Connection> getConnectionPath()
	{
		return connectionPath;
	}

	public List<Track> getTrackPath()
	{
		List<Track> trackPath = new ArrayList<>();
		for (Connection c : connectionPath)
		{
			trackPath.addAll(c.getTrackPath());
		}
		return trackPath;
	}

	public void computeSupportingLines(List<Line> linePool)
	{
		if (!isDirect)
		{
			throw new Error("Can only compute support for direct lines");
		}
		// System.out.println("\n Computing support for"+this);
		supportingLines = new ArrayList<>();
		for (Line l : linePool)
		{
			if (l.supports(this))
			{
				supportingLines.add(l);
			}
		}
		if (supportingLines.size() == 0)
		{
			// System.out.println("No lines supporting "+this);
		}
	}

	public List<Line> getSupportingLines()
	{
		return supportingLines;
	}

	public List<Station> getTransferStations()
	{
		return transferStations;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(getOrigin().getName());
		for (Connection c : connectionPath)
		{
			Station to = c.getTo();
			sb.append(" - ").append(to.getName());
			if (transferStations.contains(to))
				sb.append("(T)");
		}
		sb.append(String.format("  [%.1f min]", getDuration()));
		return sb.toString();
	}
}
