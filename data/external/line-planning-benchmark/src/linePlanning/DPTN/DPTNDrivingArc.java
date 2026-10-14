package linePlanning.DPTN;

import java.util.Objects;

import linePlanning.PTN.Connection;
import linePlanning.instance.StationNode;

public class DPTNDrivingArc extends DPTNArc
{
	private final Connection connection;
	private final int capacity;

	public DPTNDrivingArc(StationNode from, StationNode to, Connection connection, int capacity, double cost,
			int maxFrequency)
	{
		super(from, to, DEPTNArcType.DRIVE, connection.getDuration(), cost, maxFrequency);
		this.capacity = capacity;
		this.connection = connection;
	}

	public double getDistance()
	{
		return connection.getLength();
	}

	

	public Connection getConnection()
	{
		return connection;
	}

	@Override
	public String toString()
	{
		return "DPTNDrivingArc [connection=" + connection + "]";
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(connection);
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
		DPTNDrivingArc other = (DPTNDrivingArc) obj;
		return Objects.equals(connection, other.connection);
	}

	public int getCapacity() {
		return capacity;
	}
}
