package linePlanning.lineGeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import graph.structures.digraph.DirectedGraphArc;
import linePlanning.DPTN.DPTN;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.PTN.Connection;
import linePlanning.PTN.PTNRoute;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.instance.Track;

public class Line
{
	private final int index;
	private final Station origin;
	private final Station destination;
	private final int type;
	private final List<Connection> connectionPathForward;
	private final List<Connection> connectionPathBackward;
	private final List<DPTNArc> arcPathForward;
	private final List<DPTNArc> arcPathBackward;

	private final List<Station> stationsForward;
	private final List<Station> stationsBackward;
	private final int maxFrequency;
	private final double duration;
	private final double cost;

	private final int capacity; // note this is the capacity at frequency of 1

	public Line(int index, Station origin, Station destination, List<DirectedGraphArc<StationNode, DPTNArc>> arcPath,
			DPTN desNetwork, LinePlanningInstance instance)
	{
		this.index = index;
		this.origin = origin;
		this.destination = destination;
		List<Station> stationList = new ArrayList<>();
		stationList.add(origin);

		List<Connection> cPath = new ArrayList<>();
		int serviceType = -1;
		int maxFreqTemp = Integer.MAX_VALUE;
		double durationTemp = 0;
		List<DPTNArc> tempPathBackward = new ArrayList<>();
		for (DirectedGraphArc<StationNode, DPTNArc> arc : arcPath)
		{
			tempPathBackward.add(desNetwork.getConjugate(arc.getData()));
			durationTemp += arc.getData().getDuration();
			if (arc.getData() instanceof DPTNDrivingArc)
			{
				DPTNDrivingArc dArc = (DPTNDrivingArc) arc.getData();
				cPath.add(dArc.getConnection());
				stationList.add(arc.getTo().getStation());
				int cType = dArc.getConnection().getType();
				if (serviceType == -1)
				{
					serviceType = cType;
				}
				else
					if (serviceType != cType)
					{
						serviceType = LinePlanningParameters.TWIJFEL;
					}
				int maxFreqConn = dArc.getConnection().getMaxFreq();
				if (maxFreqConn < maxFreqTemp)
				{
					maxFreqTemp = maxFreqConn;
				}
			}
		}
		this.connectionPathForward = cPath;
		this.stationsForward = stationList;
		this.stationsBackward = stationList.reversed();
		this.maxFrequency = maxFreqTemp;
		this.duration = durationTemp;

		List<Connection> connectionPathReversed = new ArrayList<>();
		List<Connection> reversed = connectionPathForward.reversed();
		for (Connection connection : reversed)
		{
			connectionPathReversed.add(connection.getReverse());
		}
		connectionPathBackward = connectionPathReversed;

		this.type = serviceType;
		this.cost = (2 * duration + 2 * LinePlanningParameters.TURNAROUND_TIME)
				/ (double) LinePlanningParameters.PERIOD;
		this.capacity = LinePlanningParameters.CAPACITY;

		this.arcPathForward = toFDPTNArcs(arcPath);
		this.arcPathBackward = tempPathBackward.reversed();
	}

	public Line(int index, List<DPTNArc> arcPathForward, List<DPTNArc> arcPathBackward, DPTN desNetwork,
			LinePlanningInstance instance)
	{
		this.index = index;

		this.origin = arcPathForward.getFirst().getFrom().getStation();
		this.destination = arcPathForward.getLast().getTo().getStation();

		this.arcPathForward = arcPathForward;
		this.arcPathBackward = arcPathBackward;

		List<Station> stationList = new ArrayList<>();
		stationList.add(origin);

		List<Connection> cPath = new ArrayList<>();
		int serviceType = -1;
		int maxFreqTemp = Integer.MAX_VALUE;
		double durationTemp = 0;
		List<DPTNArc> tempPathBackward = new ArrayList<>();
		for (DPTNArc arc : arcPathForward)
		{
			tempPathBackward.add(desNetwork.getConjugate(arc));
			durationTemp += arc.getDuration();
			if (arc instanceof DPTNDrivingArc)
			{
				DPTNDrivingArc dArc = (DPTNDrivingArc) arc;
				cPath.add(dArc.getConnection());
				stationList.add(arc.getTo().getStation());
				int cType = dArc.getConnection().getType();
				if (serviceType == -1)
				{
					serviceType = cType;
				}
				else
					if (serviceType != cType)
					{
						serviceType = LinePlanningParameters.TWIJFEL;
					}
				int maxFreqConn = dArc.getConnection().getMaxFreq();
				if (maxFreqConn < maxFreqTemp)
				{
					maxFreqTemp = maxFreqConn;
				}
			}
		}
		if (!arcPathBackward.equals(tempPathBackward.reversed()))
		{
			throw new Error("Paths should be conjugate");
		}

		this.connectionPathForward = cPath;
		this.stationsForward = stationList;
		this.stationsBackward = stationList.reversed();
		this.maxFrequency = maxFreqTemp;
		this.duration = durationTemp;

		List<Connection> connectionPathReversed = new ArrayList<>();
		List<Connection> reversed = connectionPathForward.reversed();
		for (Connection connection : reversed)
		{
			connectionPathReversed.add(connection.getReverse());
		}
		connectionPathBackward = connectionPathReversed;

		this.type = serviceType;
		this.cost = (2 * duration + 2 * LinePlanningParameters.TURNAROUND_TIME)
				/ (double) LinePlanningParameters.PERIOD;
		this.capacity = LinePlanningParameters.CAPACITY;
	}

	private List<DPTNArc> toFDPTNArcs(List<DirectedGraphArc<StationNode, DPTNArc>> arcPath)
	{
		List<DPTNArc> arcs = new ArrayList<>();
		for (DirectedGraphArc<StationNode, DPTNArc> arc : arcPath)
		{
			arcs.add(arc.getData());
		}
		return arcs;
	}

	public boolean supports(DPTNRoute cnRoute)
	{
		int originIndex = stationsForward.indexOf(cnRoute.getOrigin());
		int destinationIndex = stationsForward.indexOf(cnRoute.getDestination());
		if (originIndex == -1 || destinationIndex == -1)
		{
			return false;
		}
		List<Connection> cPath = null;
		if (originIndex > destinationIndex)
		{
			int size = connectionPathBackward.size();
			cPath = connectionPathBackward.subList(size - originIndex, size - destinationIndex);
		}
		else
		{
			cPath = connectionPathForward.subList(originIndex, destinationIndex);
		}

		return cPath.equals(cnRoute.getConnectionPath());
	}

	public boolean supports(PTNRoute cnRoute)
	{
		int originIndex = stationsForward.indexOf(cnRoute.getOrigin());
		int destinationIndex = stationsForward.indexOf(cnRoute.getDestination());
		if (originIndex == -1 || destinationIndex == -1)
		{
			return false;
		}
		List<Connection> cPath = null;
		if (originIndex > destinationIndex)
		{
			int size = connectionPathBackward.size();
			cPath = connectionPathBackward.subList(size - originIndex, size - destinationIndex);
		}
		else
		{
			cPath = connectionPathForward.subList(originIndex, destinationIndex);
		}
		return cPath.equals(cnRoute.getArcs());
	}

	public Station getOrigin()
	{
		return origin;
	}

	public Station getDestination()
	{
		return destination;
	}

	public int getType()
	{
		return type;
	}

	public List<Connection> getConnectionPathForward()
	{
		return connectionPathForward;
	}

	public List<Track> getTrackPath()
	{
		List<Track> trackPath = new ArrayList<>();
		for (Connection connection : connectionPathForward)
		{
			trackPath.addAll(connection.getTrackPath());
		}
		return trackPath;
	}

	public List<Connection> getConnectionPathBackward()
	{
		return connectionPathBackward;
	}

	public int getCapacity()
	{
		return capacity;
	}

	public List<DPTNArc> getArcPathForward()
	{
		return arcPathForward;
	}

	public List<DPTNArc> getArcPathBackward()
	{
		return arcPathBackward;
	}

	public double getDuration()
	{
		return duration;
	}

	public Connection getIncomingConnection(Station station, boolean forward)
	{
		if (forward)
		{
			for (Connection c : connectionPathForward)
			{
				if (c.getTo().equals(station))
				{
					return c;
				}
			}
		}
		for (Connection c : connectionPathBackward)
		{
			if (c.getTo().equals(station))
			{
				return c;
			}
		}
		throw new IllegalArgumentException("No connection");
	}

	public Connection getOutgoingConnection(Station station, boolean forward)
	{
		if (forward)
		{
			for (Connection c : connectionPathForward)
			{
				if (c.getFrom().equals(station))
				{
					return c;
				}
			}
		}
		for (Connection c : connectionPathBackward)
		{
			if (c.getFrom().equals(station))
			{
				return c;
			}
		}
		throw new IllegalArgumentException("No connection");
	}

	public List<Station> getRemainingStations(Station transferStation, boolean forward)
	{
		int nrStations = stationsForward.size();
		if (forward)
		{
			int index = stationsForward.indexOf(transferStation);
			if (index == nrStations - 1)
			{
				return new ArrayList<>();
			}
			return stationsForward.subList(index + 1, nrStations);
		}
		int index = stationsBackward.indexOf(transferStation);
		if (index == nrStations - 1)
		{
			return new ArrayList<>();
		}
		return stationsBackward.subList(index + 1, nrStations);
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
		return Objects.hash(index);
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
		Line other = (Line) obj;
		return Objects.equals(arcPathBackward, other.arcPathBackward)
				&& Objects.equals(arcPathForward, other.arcPathForward) && capacity == other.capacity
				&& Objects.equals(connectionPathBackward, other.connectionPathBackward)
				&& Objects.equals(connectionPathForward, other.connectionPathForward)
				&& Double.doubleToLongBits(cost) == Double.doubleToLongBits(other.cost)
				&& Objects.equals(destination, other.destination)
				&& Double.doubleToLongBits(duration) == Double.doubleToLongBits(other.duration) && index == other.index
				&& maxFrequency == other.maxFrequency && Objects.equals(origin, other.origin)
				&& Objects.equals(stationsBackward, other.stationsBackward)
				&& Objects.equals(stationsForward, other.stationsForward) && type == other.type;
	}

	@Override
	public String toString()
	{
		return "Line [index=" + index + ", origin=" + origin.getNameFull() + ", destination="
				+ destination.getNameFull() + ", type=" + type + ", stations="
				+ stationsForward.stream().map(Station::getNameFull).toList() + ", duration=" + duration + ", cost="
				+ cost + "]";
	}
}
