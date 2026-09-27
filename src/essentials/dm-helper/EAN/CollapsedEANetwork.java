import java.util.*;

/**
 * A collapsed Event-Activity Network is a non-periodic EAN which contains only
 * "connection" activities between departure events at the end of change
 * activities. It resembles the perspective of the passenger, who is only
 * interested in the question which train she can reach from which
 * other train, regardless of stopovers where she does not change trains.
 * 
 * A CollapsedEANetwork is built from and has a reference to an original
 * NonPeriodicEANetwork. Its ConnectionActivities hold references to
 * all of the NonPeriodicActivities they would use in the original EAN.
 *
 *
 */
public class CollapsedEANetwork {
	
	private NonPeriodicEANetwork originalEAN;
	public CollapsedEvent[] events;
	private Hashtable<Integer, CollapsedEvent> eventsByNonPeriodicID;
	//private LinkedHashSet<CollapsedActivity> activities;
	
	public CollapsedEANetwork(NonPeriodicEANetwork originalEAN)
	{
		this.originalEAN = originalEAN;
		//this.activities = new LinkedHashSet<CollapsedActivity>();
		this.eventsByNonPeriodicID = new Hashtable<Integer, CollapsedEvent>();
		for (NonPeriodicActivity a : originalEAN.getChangingActivities())
		{
			NonPeriodicEvent e = a.getTarget();
			this.eventsByNonPeriodicID.put(e.getID(),
					new CollapsedEvent(e, e.getTime()));
		}
		int countActivities = 0;
		for (CollapsedEvent e : this.eventsByNonPeriodicID.values())
		{
			LinkedList<NonPeriodicActivity> linkedList =
					new LinkedList<NonPeriodicActivity>();
			for (NonPeriodicActivity a :
					e.getOriginalEvent().getOutgoingActivities())
				if (a.getType().equalsIgnoreCase("drive"))
					linkedList.add(a);
			if (linkedList.size() == 0)
			{
				//System.out.println("WARNING: departure event " +
				//		e.getOriginalEvent().getID() + " has no outgoing " +
				//		"drive activity!");
				continue;
			}
			depthFirstRecursionCreateCollapsedActivities(e, linkedList,
					this.eventsByNonPeriodicID);
			countActivities += e.getOutgoingActivities().length;
		} 
		this.events = this.eventsByNonPeriodicID.values()
				.toArray(new CollapsedEvent[0]);
		for (int i = 0; i < this.events.length; i++)
			this.events[i].setID(i);
		System.out.println("new CollapsedEANetwork contains "
				+ this.eventsByNonPeriodicID.size() + " events and "
				+ countActivities + " activities");
	}
	
	public void depthFirstRecursionCreateCollapsedActivities(CollapsedEvent e,
			LinkedList<NonPeriodicActivity> linkedList,
			Hashtable<Integer, CollapsedEvent> events)
	{
		NonPeriodicEvent current = linkedList.getLast().getTarget();
		for (NonPeriodicActivity a : current.getOutgoingActivities())
		{
			linkedList.addLast(a);
			if (a.getType().equalsIgnoreCase("change"))
				e.addOutgoingActivity(new CollapsedActivity(e,
						events.get(a.getTarget().getID()),
						linkedList.toArray(new NonPeriodicActivity[0])));
			else if (a.getType().equalsIgnoreCase("wait"))
				for (NonPeriodicActivity a2 :
						a.getTarget().getOutgoingActivities())
					if (a2.getType().equalsIgnoreCase("drive"))
					{
						linkedList.addLast(a2);
						depthFirstRecursionCreateCollapsedActivities(e,
								linkedList, events);
						linkedList.removeLast();
					}
			linkedList.removeLast();
		}
	}
	
	public CollapsedEvent[] getConnectionsFromDeparture(NonPeriodicEvent dep)
	{
		LinkedList<CollapsedEvent> list = new LinkedList<CollapsedEvent>();
		while (dep != null)
		{
			NonPeriodicEvent arr = null;
			for (NonPeriodicActivity a : dep.getOutgoingActivities())
			{
				if (a.getType().equalsIgnoreCase("drive"))
				{
					arr = a.getTarget();
					break;
				}
			}
			if (arr == null)
			{
				//System.out.println("WARNING: departure event " + dep.getID() +
				//		" has no outgoing drive activity");
				break;
			}
			dep = null;
			for (NonPeriodicActivity a : arr.getOutgoingActivities())
			{
				if (a.getType().equalsIgnoreCase("wait"))
					dep = a.getTarget();
				else if (a.getType().equalsIgnoreCase("change"))
				{
					list.add(eventsByNonPeriodicID.get(a.getTarget().getID()));
				}
			}
		}
		return list.toArray(new CollapsedEvent[0]);
	}
		
	public CollapsedEvent[] getConnectionsToArrival(NonPeriodicEvent arr)
	{
		LinkedList<CollapsedEvent> list = new LinkedList<CollapsedEvent>();
		while (arr != null)
		{
			NonPeriodicEvent dep = null;
			for (NonPeriodicActivity a : arr.getIncomingActivities())
			{
				if (a.getType().equalsIgnoreCase("drive"))
				{
					dep = a.getSource();
					break;
				}
			}
			if (dep == null)
			{
				//System.out.println("WARNING: arrival event " + arr.getID() +
				//		" has no incoming drive activity");
				break;
			}
			boolean alreadyAdded = false;
			arr = null;
			for (NonPeriodicActivity a : dep.getIncomingActivities())
			{
				if (a.getType().equalsIgnoreCase("wait"))
					arr = a.getSource();
				else if (!alreadyAdded
						&& a.getType().equalsIgnoreCase("change"))
				{
					alreadyAdded = true;
					list.add(eventsByNonPeriodicID.get(dep.getID()));
				}
			}
		}
		return list.toArray(new CollapsedEvent[0]);
	}
	

	
}
