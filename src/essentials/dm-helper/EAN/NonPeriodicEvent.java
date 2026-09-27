
import java.util.LinkedList;



public class NonPeriodicEvent extends Event implements Comparable<NonPeriodicEvent>
{
	private int periodicParentEventID;
	private int time;
	private int sourceDelay;
	private int dispoTime;
	private LinkedList<NonPeriodicActivity> outgoingActivities;
	private LinkedList<NonPeriodicActivity> incomingActivities;



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	public NonPeriodicEvent(int ID, int time, double weight, boolean isArrivalEvent,
                            boolean isStartofTrip, boolean isEndOfTrip,
                            int periodicParentEventID)
	{
		super(ID, weight, isArrivalEvent, isStartofTrip, isEndOfTrip);
		this.periodicParentEventID = periodicParentEventID;
		this.time = time;
		this.sourceDelay = 0;
		this.dispoTime = -1;
		this.outgoingActivities = new LinkedList<NonPeriodicActivity>();
		this.incomingActivities = new LinkedList<NonPeriodicActivity>();
	}

	public NonPeriodicEvent(int ID, int time, double weight, boolean isArrivalEvent,
                            int periodicParentEventID)
	{
		super(ID, weight, isArrivalEvent, false, false);
		this.periodicParentEventID = periodicParentEventID;
		this.time = time;
		this.sourceDelay = 0;
		this.dispoTime = -1;
		this.outgoingActivities = new LinkedList<NonPeriodicActivity>();
		this.incomingActivities = new LinkedList<NonPeriodicActivity>();
	}
	
	public NonPeriodicEvent(int ID, int station, int time, double weight, boolean isArrivalEvent,
            				boolean isStartofTrip, boolean isEndOfTrip,
            				int periodicParentEventID)
	{
		super(ID, station, weight, isArrivalEvent, isStartofTrip, isEndOfTrip);
		this.periodicParentEventID = periodicParentEventID;
		this.time = time;
		this.sourceDelay = 0;
		this.dispoTime = -1;
		this.outgoingActivities = new LinkedList<NonPeriodicActivity>();
		this.incomingActivities = new LinkedList<NonPeriodicActivity>();
	}

	public NonPeriodicEvent(int ID, int station, int time, double weight, boolean isArrivalEvent,
            				int periodicParentEventID)
	{
		super(ID, station, weight, isArrivalEvent, false, false);
		this.periodicParentEventID = periodicParentEventID;
		this.time = time;
		this.sourceDelay = 0;
		this.dispoTime = -1;
		this.outgoingActivities = new LinkedList<NonPeriodicActivity>();
		this.incomingActivities = new LinkedList<NonPeriodicActivity>();
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public int getPeriodicParentEventID()
	{
		return periodicParentEventID;
	}

	public void setPeriodicParentEventID(int periodicParentEventID)
	{
		this.periodicParentEventID = periodicParentEventID;
	}



	public int getTime()
	{
		return this.time;
	}

	public void setTime(int time)
	{
		this.time = time;
	}



	public int getSourceDelay()
	{
		return this.sourceDelay;
	}

	public void setSourceDelay(int sourceDelay)
	{
		this.sourceDelay = sourceDelay;
	}



	public int getDispoTime()
	{
		return this.dispoTime;
	}

	public void setDispoTime(int dispoTime)
	{
		this.dispoTime = dispoTime;
	}



	public LinkedList<NonPeriodicActivity> getOutgoingActivities()
	{
		return this.outgoingActivities;
	}

	public void setOutgoingActivities(LinkedList<NonPeriodicActivity> outgoingActivities)
	{
		this.outgoingActivities = outgoingActivities;
	}

	public void addOutgoingActivity(NonPeriodicActivity a)
	{
		outgoingActivities.add(a);
	}

	public void removeOutgoingActivity(NonPeriodicActivity a)
	{
		outgoingActivities.remove(a);
	}



	public LinkedList<NonPeriodicActivity> getIncomingActivities()
	{
		return this.incomingActivities;
	}

	public void setIncomingActivities(LinkedList<NonPeriodicActivity> incomingActivities)
	{
		this.incomingActivities = incomingActivities;
	}

	public void addIncomingActivity(NonPeriodicActivity a)
	{
		incomingActivities.add(a);
	}

	public void removeIncomingActivity(NonPeriodicActivity a)
	{
		incomingActivities.remove(a);
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
		StringBuilder string = new StringBuilder(8192);
		string.append(super.toString());
		string.append("; periodic parent event: ");
		string.append(this.periodicParentEventID);
		string.append("; time: ");
		string.append(this.time);
		string.append("; source delay: ");
		string.append(this.sourceDelay);
		string.append("; dispo time: ");
		string.append(this.dispoTime);
		string.append("; incoming activities: ");
		for (NonPeriodicActivity a: incomingActivities)
		{
			string.append(a.getID());
			string.append(",");
		}
		string.deleteCharAt(string.length()-1);
		string.append("; outgoing activities: ");
		for (NonPeriodicActivity a: outgoingActivities)
		{
			string.append(a.getID());
			string.append(",");
		}
		string.deleteCharAt(string.length()-1);
		return string.toString();
	}



	@Override
	public int compareTo(NonPeriodicEvent otherEvent)
	{
		if (this.time < otherEvent.time)
			return -1;
		else if (this.time > otherEvent.time)
			return 1;
		else if (this.getID() < otherEvent.getID())
			return -1;
		else if (this.getID() > otherEvent.getID())
			return 1;
		else return 0;
	}
	


	@Override
	public boolean equals(Object otherEvent)
	{
		if (otherEvent instanceof NonPeriodicEvent)
			return ((NonPeriodicEvent) otherEvent).getID() == this.getID();
		return false;
	}
	
	
}
