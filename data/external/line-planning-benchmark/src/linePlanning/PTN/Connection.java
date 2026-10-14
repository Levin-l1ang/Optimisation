package linePlanning.PTN;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.Station;
import linePlanning.instance.Track;

public class Connection implements NetworkArc
{
	private final int index;
	private final Station from;
	private final Station to;
	private final double length;
	private final double duration;
	private final List<Track> trackPath;
	private final int maxFreq;
	private final int type; // sprinter=0, IC=2
	private Connection reverse;

	private int minFreq; // based on flow computations

	public Connection(int index, Station from, Station to, double length, double duration, List<Track> trackPath,
			int maxFreq, int type)
	{
		super();

		this.index = index;
		this.from = from;
		this.to = to;
		this.length = length;
		this.duration = duration;
		this.trackPath = trackPath;
		this.maxFreq = maxFreq;
		this.type = type;
	}

	public int getIndex()
	{
		return index;
	}

	public Station getFrom()
	{
		return from;
	}

	public Station getTo()
	{
		return to;
	}

	public double getLength()
	{
		return length;
	}

	public double getDuration()
	{
		return duration;
	}

	public Track getFirstTrack()
	{
		return trackPath.getFirst();
	}

	public Track getLastTrack()
	{
		return trackPath.getLast();
	}

	public List<Track> getTrackPath()
	{
		return trackPath;
	}

	public int getType()
	{
		return type;
	}

	public int getMaxFreq()
	{
		return maxFreq;
	}

	@Override
	public String toString()
	{
		String result = index + " " + from.getName() + " " + to.getName() + " " + length + " " + duration + " "
				+ maxFreq + " " + type + " " + (reverse == null ? "null" : reverse.getIndex());
		for (Track track : trackPath)
		{
			result += " " + track.getIndex();
		}
		return result;
	}

	public static boolean opposites(Connection c1, Connection c2)
	{
		if (!c1.getFrom().equals(c2.getTo()))
		{
			return false;
		}
		if (!c2.getFrom().equals(c1.getTo()))
		{
			return false;
		}
		if (c1.type != c2.type)
		{
			return false;
		}
		return true;
	}

	public List<Station> getPassedStations()
	{
		List<Station> passed = new ArrayList<>();
		for (Track t : trackPath)
		{
			passed.add(t.getFrom());
		}
		passed.add(to);
		return passed;
	}

	public int getMinFreq()
	{
		return minFreq;
	}

	public void setMinFreq(int minFreq)
	{
		this.minFreq = minFreq;
	}

	public void setReverse(Connection reverse)
	{
		this.reverse = reverse;
	}

	public Connection getReverse()
	{
		return reverse;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(index, length, maxFreq, minFreq, to, trackPath, type);
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
		Connection other = (Connection) obj;
		return Double.doubleToLongBits(duration) == Double.doubleToLongBits(other.duration)
				&& Objects.equals(from, other.from) && index == other.index
				&& Double.doubleToLongBits(length) == Double.doubleToLongBits(other.length) && maxFreq == other.maxFreq
				&& minFreq == other.minFreq && Objects.equals(to, other.to) && Objects.equals(trackPath, other.trackPath)
				&& type == other.type;
	}
}
