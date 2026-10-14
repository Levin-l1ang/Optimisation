package linePlanning.DPTN;

import java.util.Objects;

import linePlanning.PTN.Connection;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;

public class StationConnectionNode extends StationNode
{
	private final Connection connection;
	private final boolean isArrival;

	public StationConnectionNode(Station station, Connection connection, boolean isArrival)
	{
		super(station);

		this.connection = connection;
		this.isArrival = isArrival;
	}

	public boolean isArrival()
	{
		return isArrival;
	}

	public Connection getConnection()
	{
		return connection;
	}

	@Override
	public String toString()
	{
		return "[" + station + " connection=" + connection + ", isArrival=" + isArrival + "]";
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(connection, isArrival);
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
		StationConnectionNode other = (StationConnectionNode) obj;
		return Objects.equals(connection, other.connection) && isArrival == other.isArrival;
	}
}
