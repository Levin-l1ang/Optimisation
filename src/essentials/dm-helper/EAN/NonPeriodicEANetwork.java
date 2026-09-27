
import java.util.*;



public class NonPeriodicEANetwork
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	private LinkedHashSet<NonPeriodicEvent> events;
	private LinkedHashSet<NonPeriodicActivity> activities;
	private LinkedHashSet<NonPeriodicActivity> drivingActivities;
	private LinkedHashSet<NonPeriodicActivity> waitingActivities;
	private LinkedHashSet<NonPeriodicActivity> circulationActivities;
	private LinkedHashSet<NonPeriodicActivity> niceActivities;
	private LinkedHashSet<NonPeriodicChangingActivity> changingActivities;
	private LinkedHashSet<NonPeriodicHeadwayActivity> headwayActivities;
	private int period;



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	// Note that this constructor does not copy the sets passed as parameter,
	// so any change to these sets later on will change this instance of
	// NonPeriodicEANetwork, too! This behaviour is needed by methods in
	// class DM, so if you change this behaviour, you have to adapt class
	// DM to these changes!

	public NonPeriodicEANetwork(LinkedHashSet<NonPeriodicEvent> events,
	                            LinkedHashSet<NonPeriodicActivity> activities,
	                            LinkedHashSet<NonPeriodicActivity> drivingActivities,
	                            LinkedHashSet<NonPeriodicActivity> waitingActivities,
	                            LinkedHashSet<NonPeriodicActivity> circulationActivities,
	                            LinkedHashSet<NonPeriodicActivity> niceActivities,
	                            LinkedHashSet<NonPeriodicChangingActivity> changingActivities,
	                            LinkedHashSet<NonPeriodicHeadwayActivity> headwayActivities,
	                            int period, boolean checkConsistency) throws Exception
	{
		if (   events == null
		    || activities == null
		    || drivingActivities == null
		    || circulationActivities == null
		    || waitingActivities == null
		    || niceActivities == null
		    || changingActivities == null
		    || headwayActivities == null)
		{
			throw new Exception("all parameters must be different from null");
		}

		this.events = events;
		this.activities = activities;
		this.drivingActivities = drivingActivities;
		this.waitingActivities = waitingActivities;
		this.circulationActivities = circulationActivities;
		this.niceActivities = niceActivities;
		this.changingActivities = changingActivities;
		this.headwayActivities = headwayActivities;
		this.period = period;

		if (checkConsistency)
		{
			checkConsistency();
			checkTimetable();
		}
	}



	// Note that this constructor *does* copy the sets passed as parameter,
	// so any change to these sets later on will not influence this instance
	// of NonPeriodicEANetwork (while changes applied to the *elements* in
	// the sets will!). If you change this behaviour, you might have to adapt
	// other classes that rely on this behaviour as well!

	public NonPeriodicEANetwork(List<NonPeriodicEvent> events,
                                List<NonPeriodicActivity> activities,
                                List<NonPeriodicActivity> drivingActivities,
                                List<NonPeriodicActivity> waitingActivities,
                                List<NonPeriodicActivity> circulationActivities,
                                List<NonPeriodicActivity> niceActivities,
                                List<NonPeriodicChangingActivity> changingActivities,
                                List<NonPeriodicHeadwayActivity> headwayActivities,
                                int period, boolean checkConsistency) throws Exception
	{
		if (   events == null
		    || activities == null
		    || drivingActivities == null
		    || waitingActivities == null
		    || circulationActivities  == null
		    || niceActivities == null
		    || changingActivities == null
		    || headwayActivities == null)
		{
			throw new Exception("all parameters must be different from null");
		}

		this.events = new LinkedHashSet<NonPeriodicEvent>(events);
		this.activities = new LinkedHashSet<NonPeriodicActivity>(activities);
		this.drivingActivities = new LinkedHashSet<NonPeriodicActivity>(drivingActivities);
		this.waitingActivities = new LinkedHashSet<NonPeriodicActivity>(waitingActivities);
		this.circulationActivities = new LinkedHashSet<NonPeriodicActivity>(circulationActivities);
		this.niceActivities = new LinkedHashSet<NonPeriodicActivity>(niceActivities);
		this.changingActivities = new LinkedHashSet<NonPeriodicChangingActivity>(changingActivities);
		this.headwayActivities = new LinkedHashSet<NonPeriodicHeadwayActivity>(headwayActivities);
		this.period = period;

		if (checkConsistency)
		{
			checkConsistency();
			checkTimetable();
		}
	}



	/************************************************************************
	 * getter/setter/similar methods                                        *
	 ************************************************************************/

	public LinkedHashSet<NonPeriodicEvent> getEvents()
	{
		return this.events;
	}

	// no method to add events as this could produce isolated events;
	// use methods to add activities instead



	public LinkedHashSet<NonPeriodicActivity> getActivities()
	{
		return this.activities;
	}

	// no method to add an activity to the activities set; use
	// addXXXActivity or addXXXActivities instead



	public LinkedHashSet<NonPeriodicActivity> getDrivingActivities()
	{
		return drivingActivities;
	}

	public void addDrivingActivity(NonPeriodicActivity a) throws Exception
	{
		if (a == null || a.getType() == null || ! a.getType().equals("drive"))
			throw new Exception("activity is no driving activity: " + a);

		addActivity(a);
		drivingActivities.add(a);
		niceActivities.add(a);
	}

	public void addDrivingActivities(List<NonPeriodicActivity> toAdd) throws Exception
	{
		if (toAdd == null)
			throw new Exception("no activity to add");

		for (NonPeriodicActivity a: toAdd)
			if (a == null || a.getType() == null || ! a.getType().equals("drive"))
				throw new Exception("activity is no driving activity: " + a);

		addActivities(toAdd);
		drivingActivities.addAll(toAdd);
		niceActivities.addAll(toAdd);
	}



	public LinkedHashSet<NonPeriodicActivity> getWaitingActivities()
	{
		return waitingActivities;
	}

	public void addWaitingActivity(NonPeriodicActivity a) throws Exception
	{
		if (a == null || a.getType() == null || ! (a.getType().equals("wait") || a.getType().equals("turn")))
			throw new Exception("activity is no waiting activity: " + a);

		addActivity(a);
		waitingActivities.add(a);
		niceActivities.add(a);
	}

	public void addWaitingActivities(List<NonPeriodicActivity> toAdd) throws Exception
	{
		if (toAdd == null)
			throw new Exception("no activity to add");

		for (NonPeriodicActivity a: toAdd)
			if (a == null || a.getType() == null || ! (a.getType().equals("wait") || a.getType().equals("turn")))
				throw new Exception("activity is no waiting activity: " + a);

		addActivities(toAdd);
		waitingActivities.addAll(toAdd);
		niceActivities.addAll(toAdd);
	}



	public LinkedHashSet<NonPeriodicActivity> getCirculationActivities()
	{
		return circulationActivities;
	}

	public void addCirculationActivity(NonPeriodicActivity a) throws Exception
	{
		if (a == null || a.getType() == null || ! (a.getType().equals("fixed-turnaround") || a.getType().equals
			("turnaround")))
			throw new Exception("activity is no (fixed) turnaround activity: " + a);

		addActivity(a);
		circulationActivities.add(a);
		niceActivities.add(a);
	}

	public void addCirculationActivities(List<NonPeriodicActivity> toAdd) throws Exception
	{
		if (toAdd == null)
			throw new Exception("no activity to add");

		for (NonPeriodicActivity a: toAdd)
			if (a == null || a.getType() == null || ! (a.getType().equals("fixed-turnaround") || a.getType().equals
					("turnaround")))
				throw new Exception("activity is no (fixed) turnaround activity: " + a);

		LinkedList<NonPeriodicActivity> tmp = new LinkedList<NonPeriodicActivity>(toAdd);
		addActivities(tmp);
		circulationActivities.addAll(toAdd);
		niceActivities.addAll(toAdd);
	}



	public LinkedHashSet<NonPeriodicActivity> getNiceActivities()
	{
		return niceActivities;
	}

	// no method to add an activity to the niceActivities set; use
	// addXXXActivity or addXXXActivities instead



	public LinkedHashSet<NonPeriodicChangingActivity> getChangingActivities()
	{
		return changingActivities;
	}

	public void addChangingActivity(NonPeriodicChangingActivity a) throws Exception
	{
		if (a == null || a.getType() == null || ! a.getType().equals("change"))
			throw new Exception("activity is no changing activity: " + a);

		addActivity(a);
		changingActivities.add(a);
	}

	public void addChangingActivities(List<NonPeriodicChangingActivity> toAdd) throws Exception
	{
		if (toAdd == null)
			throw new Exception("no activity to add");

		for (NonPeriodicActivity a: toAdd)
			if (a == null || a.getType() == null || ! a.getType().equals("change"))
				throw new Exception("activity is no changing activity: " + a);

		LinkedList<NonPeriodicActivity> tmp = new LinkedList<NonPeriodicActivity>(toAdd);
		addActivities(tmp);
		waitingActivities.addAll(toAdd);
	}



	public LinkedHashSet<NonPeriodicHeadwayActivity> getHeadwayActivities()
	{
		return headwayActivities;
	}

	// no method to add a single headway activity as headways always
	// occur in pairs

	public void addHeadwayActivities(List<NonPeriodicHeadwayActivity> toAdd) throws Exception
	{
		if (toAdd == null)
			throw new Exception("no activity to add");

		for (NonPeriodicActivity a: toAdd)
			if (a == null || a.getType() == null || ! a.getType().equals("headway"))
				throw new Exception("activity is no headway activity: " + a);

		LinkedHashSet<NonPeriodicHeadwayActivity> lookupTable = new LinkedHashSet<NonPeriodicHeadwayActivity>(toAdd);
		NonPeriodicHeadwayActivity a2;
		for (NonPeriodicHeadwayActivity a1: lookupTable)
		{
			a2 = a1.getCorrespodingHeadway();
			if (a2 == null || ! lookupTable.contains(a2))
				throw new Exception("corresponding headway not in set\n" + a1 + "\n" + a2);
		}

		LinkedList<NonPeriodicActivity> tmp = new LinkedList<NonPeriodicActivity>(toAdd);
		addActivities(tmp);
		headwayActivities.addAll(toAdd);
	}
	//**************************************************************************

	// Edited by Robert Wichmann

	/*	Funktionen, die folgende Fahr und Wartekante zu einer Kante zurückgeben,
	 *	Berechnung des Slacks einer Aktivität,
	 *	Von einem Event ausgehende Umstiege
	 */

	public NonPeriodicActivity getNextDrivingActivity(NonPeriodicActivity a)
	{
		if (a == null)
			return null;
		LinkedList<NonPeriodicActivity> list = a.getTarget().getOutgoingActivities();
		list.retainAll(this.getDrivingActivities());
		if (list.isEmpty())
			return null;
		else return list.getFirst();
	}

	public NonPeriodicActivity getNextWaitingActivity(NonPeriodicActivity a)
	{
		if (a==null)
			return null;
		LinkedList<NonPeriodicActivity> list = a.getTarget().getOutgoingActivities();
		list.retainAll(this.getWaitingActivities());
		if (list.isEmpty())
			return null;
		else return list.getFirst();
	}

	public int getSlack(NonPeriodicActivity a)
	{
		if (a==null)
			return 0;
		return a.getTarget().getDispoTime() - a.getSource().getDispoTime() - a.getLowerBound();
	}

	public LinkedList<NonPeriodicActivity> getOutgoingChangingActivities(NonPeriodicEvent e)
	{
		LinkedList<NonPeriodicActivity> list = e.getOutgoingActivities();
		list.retainAll(this.getChangingActivities());
		return list;

	}


	//**************************************************************************




	public int getPeriod()
	{
		return this.period;
	}

	public void setPeriod(int period)
	{
		this.period = period;
	}



	/**************************************************************
	 * methods for checking the consistency of the event-activity *
	 * network and the timetable                                  *
	 **************************************************************/

	public void checkConsistency() throws Exception
	{
		if (   events == null
		    || activities == null
		    || drivingActivities == null
		    || waitingActivities == null
		    || circulationActivities == null
		    || niceActivities == null
		    || changingActivities == null
		    || headwayActivities == null)
		{
			throw new Exception("all sets must be different from null");
		}



		/*********************************************************
		 * check the consistency of each event and each activity *
		 *********************************************************/

		// check whether each event has at least one incoming or
		// outgoing activity and if events are consecutively numbered,
		// starting with 1
		int i = 1;
		for (NonPeriodicEvent e: events)
		{
			LinkedList<NonPeriodicActivity> in = e.getIncomingActivities();
			LinkedList<NonPeriodicActivity> out = e.getOutgoingActivities();
			if (in == null || out == null)
				throw new Exception("inconsistent event: " + e);
			if (in.isEmpty() && out.isEmpty())
				throw new Exception("isolated event: " + e);
			if (e.getID() != i++)
				throw new Exception("events not consecutively numbered (or not starting with 1)");
		}

		// check whether each activity has a source and a target
		// and no self loop
		NonPeriodicEvent source, target;
		for (NonPeriodicActivity a: activities)
		{
			source = (NonPeriodicEvent) a.getSource();
			target = (NonPeriodicEvent) a.getTarget();
			if (source == null || target == null || source == target)
				throw new Exception("inconsistent source/target of activity " + a + "\n" + source + "\n" + target);
			if (! (events.contains(source) && events.contains(target)))
				throw new Exception("end point of activity not contained in set of events:\n" + a);
		}

		// check whether all activities have the right type
		for (NonPeriodicActivity a: drivingActivities)
			if (a.getType() == null || ! a.getType().equals("drive"))
				throw new Exception("activity with wrong type in drivingActivities: " + a);

		for (NonPeriodicActivity a: waitingActivities)
			if (a.getType() == null || ! (a.getType().equals("wait")))
				throw new Exception("activity with wrong type in waitingActivities: " + a);

		for (NonPeriodicActivity a: circulationActivities)
			if (a.getType() == null || ! (a.getType().equals("fixed-turnaround") || a.getType().equals("turnaround")))
				throw new Exception("activity with wrong type in circulationActivities: " + a);

		for (NonPeriodicChangingActivity a: changingActivities)
			if (a.getType() == null || ! a.getType().equals("change"))
				throw new Exception("activity with wrong type in changingActivities: " + a);

		for (NonPeriodicHeadwayActivity a: headwayActivities)
			if (a.getType() == null || ! a.getType().equals("headway"))
				throw new Exception("activity with wrong type in headwayActivities: " + a);

		// check whether each headway activity has a counterpart and whether
		// all headways have a lower bound > 0
		for (NonPeriodicHeadwayActivity a1: headwayActivities)
		{
			if (a1.getLowerBound() <= 0)
			{
				throw new Exception(  "headway with lower bound <= 0 does not "
				                    + "make sense (and might cause trouble in "
				                    + "heuristics for delay mangement): " + a1);
			}

			NonPeriodicHeadwayActivity a2 = a1.getCorrespodingHeadway();
			if (a2 == null)
				throw new Exception("headway activity does not have a counterpart: " + a1);
			if (   a1.getSource() != a2.getTarget()
			    || a1.getTarget() != a2.getSource()
			    || a2.getCorrespodingHeadway() != a1)
			{
				throw new Exception("inconsistent headway activities:\n" + a1 + "\n" + a2);
			}
			if (! headwayActivities.contains(a2))
				throw new Exception("corresponding headway not in set\n" + a1 + "\n" + a2);
		}
	}



	public void checkTimetable() throws Exception
	{
		LinkedList<NonPeriodicActivity> fixedActivities =
			new LinkedList<NonPeriodicActivity>();
		fixedActivities.addAll(drivingActivities);
		fixedActivities.addAll(waitingActivities);
		fixedActivities.addAll(circulationActivities);

		for (NonPeriodicActivity a: fixedActivities)
			if (a.getTarget().getTime() - a.getSource().getTime() < a.getLowerBound())
				throw new Exception("timetable not valid:\n" + a + "\n" + a.getSource() + "\n" + a.getTarget());

		for (NonPeriodicChangingActivity a: changingActivities)
			if (a.getTarget().getTime() - a.getSource().getTime() < a.getLowerBound())
				throw new Exception("timetable not valid:\n" + a + "\n" + a.getSource() + "\n" + a.getTarget());

		int time1, time2, bound1, bound2;
		for (NonPeriodicHeadwayActivity a: headwayActivities)
		{
			time1 = a.getSource().getTime();
			time2 = a.getTarget().getTime();
			bound1 = a.getLowerBound();
			bound2 = a.getCorrespodingHeadway().getLowerBound();
			if (time2 - time1 < bound1 && time1 - time2 < bound2)
				throw new Exception("timetable not valid:\n" + a + "\n" + a.getCorrespodingHeadway() + "\n" + a.getSource() + "\n" + a.getTarget());
		}
	}



	public void checkDispositionTimetable() throws Exception
	{
		for (NonPeriodicEvent e: events)
			if (e.getDispoTime() < e.getTime() + e.getSourceDelay())
				throw new Exception("invalid disposition time: " + e);

		LinkedList<NonPeriodicActivity> fixedActivities =
			new LinkedList<NonPeriodicActivity>();
		fixedActivities.addAll(drivingActivities);
		fixedActivities.addAll(waitingActivities);
		fixedActivities.addAll(circulationActivities);

		for (NonPeriodicActivity a: fixedActivities)
			if (a.getTarget().getDispoTime() - a.getSource().getDispoTime() < a.getLowerBound())
				throw new Exception("lower bound not respected:\n" + a + "\n" + a.getSource() + "\n" + a.getTarget());

		for (NonPeriodicChangingActivity a: changingActivities)
		{
			if (a.getZ() != 0 && a.getZ() != 1)
				throw new Exception("invalid value for z: " + a);
			if (a.getZ() == 0 && a.getTarget().getDispoTime() - a.getSource().getDispoTime() < a.getLowerBound())
				throw new Exception("lower bound not respected:\n" + a + "\n" + a.getSource() + "\n" + a.getTarget());
		}

		for (NonPeriodicHeadwayActivity a1: headwayActivities)
		{
			NonPeriodicHeadwayActivity a2 = a1.getCorrespodingHeadway();
			int g1 = a1.getG();
			int g2 = a2.getG();
			if (! ((g1 == 0 && g2 == 1) || (g1 == 1 && g2 == 0)))
				throw new Exception("invalid values for g:\n" + a1 + "\n" + a2);
			if (g1 == 0 && a1.getTarget().getDispoTime() - a1.getSource().getDispoTime() < a1.getLowerBound())
				throw new Exception("lower bound not respected:\n" + a1 + "\n" + a1.getSource() + "\n" + a1.getTarget());
		}
	}



	public void resetSourceDelays()
	{
		for (NonPeriodicEvent e: events)
			e.setSourceDelay(0);
		for (NonPeriodicActivity a: activities)
			a.setSourceDelay(0);
	}



	// in most cases, the following method should not be needed as a directed
	// circle in the EAN would lead to an infeasible timetable; however, in
	// some cases, it might be convenient to have such a function...
	public boolean containsTimetableCircle()
	{
		// for each event e, we test if there is a directed path,
		// starting and ending with e, containing only activities
		// of type drive, wait, turn, fixed-circulation, change,
		// or (forward) headway, i.e., we ignore backwards headways
		for (NonPeriodicEvent e: events)
		{
			boolean[] visited = new boolean[events.size()];
			LinkedHashSet<NonPeriodicEvent> toVisit =
				new LinkedHashSet<NonPeriodicEvent>(events.size());
			toVisit.add(e);
			while (! toVisit.isEmpty())
			{
				NonPeriodicEvent current = toVisit.iterator().next();
				toVisit.remove(current);
				visited[current.getID()-1] = true;

				for (NonPeriodicActivity a: current.getOutgoingActivities())
				{
					String type = a.getType();
					if (   type.equals("drive")
					    || type.equals("wait")
					    || type.equals("turn")
					    || type.equals("fixed-circulation")
					    || type.equals("change")
					    || (type.equals("headway") && a.getTarget().getTime() > a.getSource().getTime()))
					{
						NonPeriodicEvent target = a.getTarget();
						if (target == e)
							return true;
						if (! visited[target.getID()-1])
							toVisit.add(target);
					}
				}
			}
		}
		return false;
	}

		public boolean containsDispoCircle()
	{
		// for each event e, we test if there is a directed path,
		// starting and ending with e, containing only activities
		// of type drive, wait, turn, fixed-circulation, change,
		// or (forward) headway, i.e., we ignore backwards headways
		for (NonPeriodicEvent e: events)
		{
			boolean[] visited = new boolean[events.size()];
			LinkedHashSet<NonPeriodicEvent> toVisit =
				new LinkedHashSet<NonPeriodicEvent>(events.size());
			toVisit.add(e);
			while (! toVisit.isEmpty())
			{
				NonPeriodicEvent current = toVisit.iterator().next();
				toVisit.remove(current);
				visited[current.getID()-1] = true;

				for (NonPeriodicActivity a: current.getOutgoingActivities())
				{
					String type = a.getType();
					if (   type.equals("drive")
					    || type.equals("wait")
					    || type.equals("turn")
					    || type.equals("fixed-circulation")
					    || type.equals("change")
					    || (type.equals("headway") && a.getTarget().getDispoTime() > a.getSource().getDispoTime()))
					{
						NonPeriodicEvent target = a.getTarget();
						if (target == e)
							return true;
						if (! visited[target.getID()-1])
							toVisit.add(target);
					}
				}
			}
		}
		return false;
	}



	public void resetDispositionDecisions()
	{
		// set all disposition decisions to invalid values
		// to make it easier to detect errors

		for (NonPeriodicEvent e: events)
			e.setDispoTime(e.getTime()-1);

		for (NonPeriodicChangingActivity a: changingActivities)
			a.setZ(-1);

		for (NonPeriodicHeadwayActivity a: headwayActivities)
			a.setG(-1);
	}



	public void setZ()
	{
		for (NonPeriodicChangingActivity a: changingActivities)
		{
			if (a.getTarget().getDispoTime() - a.getSource().getDispoTime() < a.getLowerBound())
				a.setZ(1);
			else
				a.setZ(0);
		}
	}



	public void setG()
	{
		Iterator<NonPeriodicHeadwayActivity> hActivityIterator = headwayActivities.iterator();
		while (hActivityIterator.hasNext())
		{
			NonPeriodicHeadwayActivity a1 = hActivityIterator.next();
			NonPeriodicHeadwayActivity a2 = hActivityIterator.next();
			int lower = a1.getLowerBound();
			NonPeriodicEvent e1 = a1.getSource();
			NonPeriodicEvent e2 = a1.getTarget();
			int time1 = e1.getTime();
			int time2 = e2.getTime();
			int dispoTime1 = e1.getDispoTime();
			int dispoTime2 = e2.getDispoTime();

			if (   dispoTime1 < dispoTime2
			    || (dispoTime1 == dispoTime2 && lower == 0 && time1 <= time2))
			{
				a1.setG(0);
				a2.setG(1);
			}
			else
			{
				a1.setG(1);
				a2.setG(0);
			}
		}
	}



	/**
	 * Returns all events of the desired type at a given station.
	 * @param station the ID of the station
	 * @param type the type of the event like "departure" or "arrival";
	 * null for all
	 * @return an array containing all matching events
	 */
	public NonPeriodicEvent[] getEventsAtStation(int station, boolean arrival)
	{
		LinkedList<NonPeriodicEvent> list = new LinkedList<NonPeriodicEvent>();
		for (NonPeriodicEvent e : this.events)
			if ((e.getStation() == station) && (e.isArrivalEvent() == arrival))
				list.add(e);
		return list.toArray(new NonPeriodicEvent[0]);
	}



	public static LinkedList<NonPeriodicActivity>
			getPathOnTrip(NonPeriodicEvent source, NonPeriodicEvent destination)
	{
		LinkedList<NonPeriodicActivity> path =
				new LinkedList<NonPeriodicActivity>();
		while (source != null)
		{
			NonPeriodicActivity next = null;
			for (NonPeriodicActivity a : source.getOutgoingActivities())
			{
				if (a.getTarget() == destination && !a.getType().equalsIgnoreCase("headway"))
				{
					path.add(a);
					return path;
				}
				if (a.getType().equalsIgnoreCase("drive")
						|| a.getType().equalsIgnoreCase("wait"))
					next = a;
			}
			if (next != null)
			{
				path.add(next);
				source = next.getTarget();
			}
			else
				source = null;
		}
		return null;
	}

	public LinkedList<NonPeriodicHeadwayActivity> getHeadwaysOnTrip(NonPeriodicEvent source,
			NonPeriodicEvent destination){
		LinkedList<NonPeriodicHeadwayActivity> headways = new LinkedList<NonPeriodicHeadwayActivity>();
		while (source != null)
		{
			NonPeriodicActivity next = null;
			for (NonPeriodicActivity a : source.getOutgoingActivities()){
				if (a.getTarget() == destination){
					return headways;
				}
				if (a.getType().equalsIgnoreCase("drive")
						|| a.getType().equalsIgnoreCase("wait")){
					next = a;
				}
				if (a.getType().equalsIgnoreCase("headway")){
					headways.add((NonPeriodicHeadwayActivity) a);
				}
			}
			if (next != null)
			{
				source = next.getTarget();
			}
			else
				source = null;
		}
		return headways;
	}




	/***************************
	 * internal helper methods *
	 ***************************/

	private void addActivity(NonPeriodicActivity a) throws Exception
	{
		NonPeriodicEvent source = a.getSource();
		NonPeriodicEvent target = a.getTarget();

		if (   source == null
		    || source.getOutgoingActivities() == null
		    || (! source.getOutgoingActivities().contains(a))
		    || target == null
		    || target.getOutgoingActivities() == null
		    || (! target.getOutgoingActivities().contains(a)))
		{
			throw new Exception("inconsistent activity: " + a);
		}

		activities.add(a);
		events.add(source);
		events.add(target);
	}



	private void addActivities(List<NonPeriodicActivity> ActivitiesToAdd) throws Exception
	{
		LinkedList<NonPeriodicEvent> eventsToAdd = new LinkedList<NonPeriodicEvent>();
		NonPeriodicEvent source, target;
		for (NonPeriodicActivity a: ActivitiesToAdd)
		{
			source = a.getSource();
			target = a.getTarget();

			if (   source == null
			    || source.getOutgoingActivities() == null
			    || (! source.getOutgoingActivities().contains(a))
			    || target == null
			    || target.getOutgoingActivities() == null
			    || (! target.getOutgoingActivities().contains(a)))
			{
				throw new Exception("inconsistent activity: " + a);
			}

			eventsToAdd.add(source);
			eventsToAdd.add(target);
		}

		activities.addAll(ActivitiesToAdd);
		events.addAll(eventsToAdd);
	}
}
