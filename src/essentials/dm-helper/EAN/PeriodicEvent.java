

import java.util.*;



public class PeriodicEvent extends Event
{
	private int frequency;
	private LinkedList<Integer> periodicTimes;
	private LinkedList<NonPeriodicEvent> rolledOutEvents;



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	public PeriodicEvent(int ID, double weight, boolean isArrivalEvent,
                         boolean isStartofTrip, boolean isEndOfTrip, int frequency)
	{
		super(ID, weight, isArrivalEvent, isStartofTrip, isEndOfTrip);
		this.frequency = frequency;
		this.periodicTimes = new LinkedList<Integer>();
		this.rolledOutEvents = new LinkedList<NonPeriodicEvent>();
	}

	public PeriodicEvent(int ID, int station, double weight, boolean isArrivalEvent,
                         boolean isStartofTrip, boolean isEndOfTrip, int frequency)
	{
		super(ID, station, weight, isArrivalEvent, isStartofTrip, isEndOfTrip);
		this.frequency = frequency;
		this.periodicTimes = new LinkedList<Integer>();
		this.rolledOutEvents = new LinkedList<NonPeriodicEvent>();
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public int getFrequency()
	{
		return frequency;
	}

	public void setFrequency(int frequency)
	{
		this.frequency = frequency;
	}



	public LinkedList<Integer> getPeriodicTimes()
	{
		return periodicTimes;
	}

	public void setPeriodicTimes(LinkedList<Integer> periodicTimes)
	{
		this.periodicTimes = periodicTimes;
	}

	public void addPeriodicTime(int time)
	{
		periodicTimes.add(time);
	}



	public LinkedList<NonPeriodicEvent> getRolledOutEvents()
	{
		return rolledOutEvents;
	}

	public void setRolledOutEvents(LinkedList<NonPeriodicEvent> rolledOutEvents)
	{
		this.rolledOutEvents = rolledOutEvents;
	}

	public void addRolledOutEvent(NonPeriodicEvent event)
	{
		rolledOutEvents.add(event);
	}



	/************************************************************************
	 * misc functions                                                       *
	 ************************************************************************/

	@Override 
	public String toString()
	{
		// we use a high-capacity StringBuilder here as - in the case of
		// headway activities - we might have quite many incoming and/or
		// outgoing activities and many corresponding non-periodic events
		StringBuilder string = new StringBuilder(8192);
		string.append(super.toString());
		string.append("; frequency: ");
		string.append(this.frequency);
		string.append("; periodic times: ");
		for (int time: periodicTimes)
		{
			string.append(time);
			string.append(",");
		}
		string.deleteCharAt(string.length()-1);
		string.append("; rolled out events: ");
		for (NonPeriodicEvent e: rolledOutEvents)
		{
			string.append(e.getID());
			string.append(",");
		}
		string.deleteCharAt(string.length()-1);
		return string.toString();
	}
}
