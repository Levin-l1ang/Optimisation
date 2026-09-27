


public abstract class Event
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	private int ID;
	private int station;
	private double weight;
	private boolean isArrivalEvent;
	private boolean isStartofTrip;
	private boolean isEndOfTrip;



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	public Event(int ID, double weight, boolean isArrivalEvent,
	             boolean isStartofTrip, boolean isEndOfTrip)
	{
		this.ID = ID;
		this.weight = weight;
		this.isArrivalEvent = isArrivalEvent;
		this.isStartofTrip = isStartofTrip;
		this.isEndOfTrip = isEndOfTrip;
	}

	public Event(int ID, int station, double weight, boolean isArrivalEvent,
                 boolean isStartofTrip, boolean isEndOfTrip)
	{
		this.ID = ID;
		this.station = station;
		this.weight = weight;
		this.isArrivalEvent = isArrivalEvent;
		this.isStartofTrip = isStartofTrip;
		this.isEndOfTrip = isEndOfTrip;
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public int getID()
	{
		return this.ID;
	}

	public void setID(int ID)
	{
		this.ID = ID;
	}



	public int getStation()
	{
		return this.station;
	}

	public void setStation(int station)
	{
		this.station = station;
	}



	public double getWeight()
	{
		return this.weight;
	}

	public void setWeight(double weight)
	{
		this.weight = weight;
	}



	public boolean isArrivalEvent()
	{
		return this.isArrivalEvent;
	}

	public void setArrivalEvent(boolean isArrivalEvent)
	{
		this.isArrivalEvent = isArrivalEvent;
	}

	public boolean isStartofTrip()
	{
		return isStartofTrip;
	}

	public void setStartofTrip(boolean isStartofTrip)
	{
		this.isStartofTrip = isStartofTrip;
	}



	public boolean isEndOfTrip()
	{
		return isEndOfTrip;
	}

	public void setEndOfTrip(boolean isEndOfTrip)
	{
		this.isEndOfTrip = isEndOfTrip;
	}



	/************************************************************************
	 * misc functions                                                       *
	 ************************************************************************/

	@Override 
	public String toString()
	{
		// we use a high-capacity StringBuilder here as - in the case of
		// headway activities - we might have quite many incoming and/or
		// outgoing activities
		StringBuilder string = new StringBuilder(4096);
		string.append("Event: ID: ");
		string.append(this.ID);
		string.append("; weight: ");
		string.append(this.weight);
		string.append("; type: ");
		string.append(isArrivalEvent? "arrival" : "departure");
		string.append("; start of trip? ");
		string.append(this.isStartofTrip);
		string.append("; end of trip? ");
		string.append(this.isEndOfTrip);
		return string.toString();
	}
}
