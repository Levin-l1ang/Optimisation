/********************************************************************
 * This class can be used to turn a periodic event-activity network *
 * into a time-expanded (non-periodic) event-activity network.      *
 *                                                                  *
 * For documentation, see Readme.txt.                               *
 ********************************************************************/



import java.io.*;
import java.util.*;


public class Rollout
{
	private Rollout() {}  // class only contains static methods



	// settings read from the config file
	private static boolean periodicallyRollOut;
	private static String edgeFile;
	private static String eventInputFile;
	private static String activityInputFile;
	private static String timetableFile;
	private static String linePlanFile;
	private static String eventOutputFile;
	private static String activityOutputFile;
	private static String endEventsofTripsFileName;
	private static String edgeHeadwayFile;
	private static String aperiodicEventHeader;
	private static String aperiodicActivityHeader;
	private static int earliestTime;
	private static int latestTime;
	private static int period;
	private static int timeUnitsPerMinute;
	private static boolean onlyWholeTrips;
	private static boolean discardUnusedChangingActivities;
	private static boolean rolloutFortimetabling;
	private static boolean rolloutPassengerPaths;
	private static boolean headways;
	private static boolean CHECK_CONSISTENCY;
	private static boolean DEBUG;
	private static boolean VERBOSE;
	private static HashMap<String, Integer> edgeHeadways;
	private static int[] eventHeadways;
	private static NonPeriodicEANetwork Net;


	// ArrayLists for the periodic network as we easily can compute the needed
	// size for the lists and need to access specific indices, and LinkedLists
	// for the aperiodic network as adding to the end of the list is faster
	// than for an ArrayList and we do not need to access elements by their index.
	private static ArrayList<PeriodicEvent> pEvents;
	private static LinkedList<NonPeriodicEvent> events = new LinkedList<NonPeriodicEvent>();
	private static ArrayList<PeriodicActivity> pActivities;
	private static LinkedList<NonPeriodicActivity> activities = new LinkedList<NonPeriodicActivity>();
	private static int[] frequencies;



	public static void main(String[] args) throws Exception
	{
		readConfig();

		if (VERBOSE)
			System.out.println("Rollout: preparing array lists...");
		prepareArrayLists();

		if (VERBOSE)
			System.out.println("Rollout: reading input files...");
		readInputFiles();

		if (VERBOSE)
			System.out.println("Rollout: rolling out...");
		rollOut();

		if (VERBOSE)
			System.out.println("Rollout: cleaning up headways...");
		cleanHeadways();

		if (events.size() == 0 || activities.size() == 0)
			throw new Exception("Rollout: empty network after rollout (only_whole_trips enabled and [DM_earliest_time,DM_latest_time] too small?)");

		if (VERBOSE)
			System.out.println("Rollout: writing output files...");
		writeOutputFiles(events, activities);

		// call path-distribution of passengers
		// (a little bit unlucky: as it needs a NonPeriodicEANetwork object,
		// we have to read in the files we've just written out,
		// and will write them out anew in the end because of new weights)

		if (rolloutPassengerPaths)
		{
			if (VERBOSE)
				System.out.println("Rollout: calling passenger distribution...");
			rolloutPassengerPaths();
			writeOutputFiles(Net.getEvents(), Net.getActivities());
		}


	}



	// This methods checks if for some pair of events, there are two or
	// more directed headway edges between them; if this is the case, all
	// such headway arcs are deleted, except for the one with the lowest
	// lower bound. Multiple headway arcs between the same pair of events
	// are needed in periodic timetabling if at least one events of such a
	// pair of events has a frequency of at least 2 to ensure feasibility.
	private static void cleanHeadways()
	{
		LinkedHashSet<NonPeriodicActivity> remove =
			new LinkedHashSet<NonPeriodicActivity>(activities.size());

		for (NonPeriodicEvent e: events)
		{
			ArrayList<NonPeriodicActivity> incoming =
				new ArrayList<NonPeriodicActivity>(e.getIncomingActivities());

			for (int i=0; i<incoming.size(); i++)
			{
				NonPeriodicActivity a1 = incoming.get(i);
				if (! a1.getType().equals("headway"))
					continue;

				int lower1 = a1.getLowerBound();
				NonPeriodicEvent source = a1.getSource();
				for (int k=i+1; k<incoming.size(); k++)
				{
					NonPeriodicActivity a2 = incoming.get(k);
					if (   (! a2.getType().equals("headway"))
					    || source != a2.getSource()
					    || remove.contains(a2))
					{
						continue;
					}

					int lower2 = a2.getLowerBound();
					if (lower1 <= lower2)
					{
						remove.add(a2);
					}
					else
					{
						remove.add(a1);
						break;
					}
				}
			}
		}

		activities.removeAll(remove);
		for (NonPeriodicActivity a: remove)
		{
			a.getSource().removeOutgoingActivity(a);
			a.getTarget().removeIncomingActivity(a);
		}

		// renumber remaining activities
		int newID = 1;
		for (NonPeriodicActivity a: activities)
			a.setID(newID++);
	}



	private static void readConfig() throws Exception
	{
		Config config = new Config(new File("basis/Config.cnf"));

		edgeFile = config.getStringValue("default_edges_file");
		eventInputFile = config.getStringValue("default_events_periodic_file");
		activityInputFile = config.getStringValue("default_activities_periodic_unbuffered_file");
		linePlanFile = config.getStringValue("default_lines_file");
		timetableFile = config.getStringValue("default_timetable_periodic_file");
		edgeHeadwayFile = config.getStringValue("default_headways_file");
		aperiodicActivityHeader = config.getStringValue("activities_header");
		aperiodicEventHeader = config.getStringValue("events_header");
		headways = !config.getStringValue("ean_construction_target_model_headway").equals("NO_HEADWAYS");
		rolloutPassengerPaths = config.getBooleanValue("rollout_passenger_paths");
		rolloutFortimetabling = config.getBooleanValue("rollout_for_nonperiodic_timetabling");
		if (rolloutFortimetabling)
		{
			eventOutputFile = config.getStringValue("default_events_for_nonperiodic_timetabling_file");
			activityOutputFile = config.getStringValue("default_activities_for_nonperiodic_timetabling_file");
		}
		else
		{
			eventOutputFile = config.getStringValue("default_events_expanded_file");
			activityOutputFile = config.getStringValue("default_activities_expanded_file");
		}
		endEventsofTripsFileName = config.getStringValue("default_expanded_end_events_of_trips_file");
		earliestTime = config.getIntegerValue("DM_earliest_time");
		latestTime = config.getIntegerValue("DM_latest_time");
		timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
		// attention: in the operational phase, we need seconds!
		period = 60 * config.getIntegerValue("period_length") / timeUnitsPerMinute;
		onlyWholeTrips = config.getBooleanValue("rollout_whole_trips");
		periodicallyRollOut =
			config.getStringValue("ean_model_frequency").equalsIgnoreCase("FREQUENCY_AS_ATTRIBUTE");
		discardUnusedChangingActivities =
			config.getBooleanValue("rollout_discard_unused_change_edges");
		CHECK_CONSISTENCY = config.getBooleanValue("DM_enable_consistency_checks");
		DEBUG = config.getBooleanValue("DM_debug");
		VERBOSE = config.getBooleanValue("DM_verbose");

		if (DEBUG)
		{
			CHECK_CONSISTENCY = true;
			VERBOSE = true;
		}

		if (earliestTime > latestTime)
			throw new Exception("Rollout: DM_latest_time must not be smaller than DM_earliest_time");
		if (period <= 0)
			throw new Exception("Rollout: period_length has to be strictly positive");
		if (timeUnitsPerMinute <= 0)
			throw new Exception("Rollout: time_units_per_minute has to be strictly positive");

		if (VERBOSE)
		{
			System.out.println("Rollout: using the following configuration:");
			if (rolloutFortimetabling)
				System.out.println("  rolling out for nonperiodic timetabling instead for delay management!");
			if (onlyWholeTrips)
				System.out.println("  rolling out only whole trips in [" + earliestTime  + "," + latestTime + "]");
			else
				System.out.println("  rolling out all events in [" + earliestTime  + "," + latestTime + "]");
			if (periodicallyRollOut)
				System.out.println("  also rolling out periodically (ean_model_frequency set to FREQUENCY_AS_ATTRIBUTE)");
			else
				System.out.println("  not rolling out periodically (ean_model_frequency not set to FREQUENCY_AS_ATTRIBUTE)");
			if (discardUnusedChangingActivities)
				System.out.println("  ignoring changing activities with weight 0");
			else
				System.out.println("  also rolling out changing activities with weight 0");
			System.out.println("  period length: " + period + " seconds");
			System.out.println("  periodic events input file: " + new File(eventInputFile).getAbsolutePath());
			System.out.println("  periodic activities input file: " + new File(activityInputFile).getAbsolutePath());
			System.out.println("  line plan input file:" + new File(linePlanFile).getAbsolutePath());
			System.out.println("  periodic timetable input file: " + new File(timetableFile).getAbsolutePath());
			System.out.println("  non-periodic events output file: " + new File(eventOutputFile).getAbsolutePath());
			System.out.println("  non-periodic activities output file: " + new File(activityOutputFile).getAbsolutePath());
			System.out.println("  end events of trips output file: " + new File(endEventsofTripsFileName).getAbsolutePath());
			System.out.println("  edge input file: " + new File(edgeFile).getAbsolutePath());
			System.out.println("  edge headways input file: " + new File(edgeHeadwayFile).getAbsolutePath());
		}
	}



	private static void prepareArrayLists() throws IOException
	{
		int count = Tools.countRelevantLines(eventInputFile);
		pEvents = new ArrayList<PeriodicEvent>(count);

		count = Tools.countRelevantLines(activityInputFile);
		pActivities = new ArrayList<PeriodicActivity>(count);

		count = Tools.countRelevantLines(linePlanFile);
		frequencies = new int[count];
	}



	private static void readInputFiles() throws IOException
	{
		String line;

		// read PTN edges from edgeFile
		if (DEBUG)
			System.out.println("Rollout: reading PTN edges from file " + new File(edgeFile).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(edgeFile)) {
			//throw new IOException("Rollout: invalid numbering of IDs or empty input file: " + edgeFile);
		}
		int count = Tools.countRelevantLines(edgeFile);
		Map<Integer, Map<Integer, Integer>> stops = new HashMap<>();
		stops.put(0, new HashMap<>());
		stops.put(1, new HashMap<>());
		BufferedReader in = new BufferedReader(new FileReader(edgeFile));
		while ((line = in.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			stops.get(0).put(ID-1, Integer.parseInt(tokens[1].trim()));
			stops.get(1).put(ID-1, Integer.parseInt(tokens[2].trim()));
		}
		in.close();

        if (headways) {
            // read edge headways from edgeHeadwayFile
            if (DEBUG)
                System.out.println("Rollout: reading edge headways from file " + new File(edgeHeadwayFile).getAbsolutePath());
            if (CHECK_CONSISTENCY && !Tools.checkIDs(edgeHeadwayFile))
                throw new IOException("Rollout: invalid numbering of IDs or empty input file: " + edgeHeadwayFile);

            count = 2 * Tools.countRelevantLines(edgeHeadwayFile);
            edgeHeadways = new HashMap<String, Integer>(count);
            in = new BufferedReader(new FileReader(edgeHeadwayFile));
            while ((line = in.readLine()) != null) {
                int position = line.indexOf('#');
                if (position != -1)
                    line = line.substring(0, position);
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] tokens = line.split(";");
                int ID = Integer.parseInt(tokens[0].trim());
                int headway = 60 / timeUnitsPerMinute * Integer.parseInt(tokens[1].trim());
                edgeHeadways.put(stops.get(0).get(ID - 1) + ";" + stops.get(1).get(ID - 1), headway);
                edgeHeadways.put(stops.get(1).get(ID - 1) + ";" + stops.get(0).get(ID - 1), headway);
            }
            in.close();
        }

		// read frequencies from linePlanFile
		if (DEBUG)
			System.out.println("Rollout: reading line plan from file " + new File(linePlanFile).getAbsolutePath());
		in = new BufferedReader(new FileReader(linePlanFile));
		while ((line = in.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			int f = Integer.parseInt(tokens[3].trim());

			frequencies[ID-1] = f;
		}
		in.close();

		// read events from eventInputFile
		if (DEBUG)
			System.out.println("Rollout: reading periodic events from file " + new File(eventInputFile).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(eventInputFile))
			throw new IOException("Rollout: invalid numbering of IDs or empty input file: " + eventInputFile);
		in = new BufferedReader(new FileReader(eventInputFile));
		while ((line = in.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			boolean isArrival = tokens[1].trim().equals("\"arrival\"");
			int stationID = Integer.parseInt(tokens[2].trim());
			int lineID = Integer.parseInt(tokens[3].trim());
			double weight = Double.parseDouble(tokens[4].trim());

			// for events with frequency > 1, the customers are equally distributed
			// over all occurences of this event during one period
			weight = weight / frequencies[lineID-1];

			// properties of this event that will be overwritten later on
			boolean isStart = true;
			boolean isEnd = true;

			int frequency = frequencies[lineID-1];

			PeriodicEvent newEvent =
				new PeriodicEvent(ID, stationID, weight, isArrival, isStart, isEnd, frequency);
			pEvents.add(newEvent);
		}
		in.close();
		eventHeadways = new int[pEvents.size()];

		// read timetable from timetableFile and (periodically) roll out
		if (DEBUG)
			System.out.println("Rollout: reading periodic timetable from file " + new File(timetableFile).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(timetableFile))
			throw new IOException("Rollout: invalid numbering of IDs or empty input file: " + timetableFile);
		in = new BufferedReader(new FileReader(timetableFile));
		while ((line = in.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			// attention: timetabling works with time units, we use seconds!
			int pTime = ((60 * Integer.parseInt(tokens[1].trim())) / timeUnitsPerMinute) % period;

			PeriodicEvent e = pEvents.get(ID-1);
			int f = e.getFrequency();

			if (periodicallyRollOut)
				for (int i=0; i<f; i++)
					e.addPeriodicTime((int) (i*period / ((double) f) + pTime) % period);
			else
				e.addPeriodicTime(pTime);
		}
		in.close();


		// read activities from activityInputFile
		if (DEBUG)
			System.out.println("Rollout: reading periodic activities from file " + new File(activityInputFile).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(activityInputFile))
			throw new IOException("Rollout: invalid numbering of IDs or empty input file: " + activityInputFile);
		in = new BufferedReader(new FileReader(activityInputFile));
		while ((line = in.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			String type = tokens[1].trim();
			// remove " chars at beginning and end of the string
			type = type.substring(1, type.length()-1);

			double weight = Double.parseDouble(tokens[6].trim());
			if (   type.equals("sync")
			    || (discardUnusedChangingActivities && weight == 0 && type.equals("change")))
			{
				continue;
			}
			int ID = Integer.parseInt(tokens[0].trim());
			int sID = Integer.parseInt(tokens[2].trim());
			int tID = Integer.parseInt(tokens[3].trim());
			// attention: timetabling works with time units, we use seconds!
			int lowerBound = 60*Integer.parseInt(tokens[4].trim()) / timeUnitsPerMinute;
			int upperBound = 60*Integer.parseInt(tokens[5].trim()) / timeUnitsPerMinute;

			if (CHECK_CONSISTENCY)
			{
				if (sID<1 || sID>pEvents.size() || tID<1 || tID>pEvents.size()){
					in.close();
					throw new IndexOutOfBoundsException("Rollout: invalid event ID in file " + activityInputFile);
				}
				if (lowerBound>upperBound){
					in.close();
					throw new RuntimeException("Rollout: lower bound larger than upper bound!");
				}
			}

			PeriodicEvent pSource = pEvents.get(sID-1);
			PeriodicEvent pTarget = pEvents.get(tID-1);

			if (type.equals("drive") || type.equals("wait"))
			{
				pTarget.setStartofTrip(false);
				pSource.setEndOfTrip(false);
			}
			if (type.equals("drive") && headways) {
			    Integer headway = edgeHeadways.get(pSource.getStation() + ";" + pTarget.getStation());
			    if (headway == null) {
			        throw new IllegalStateException("Cannot find headway from station " + pSource.getStation() + " to "
                        + pTarget.getStation() + ", invalid headway state!");
                }
			    eventHeadways[sID - 1] = headway;
            }
			PeriodicActivity newActivity =
				new PeriodicActivity(ID, pSource, pTarget, lowerBound, upperBound, weight, type);
			pActivities.add(newActivity);
		}
		in.close();
	}



	private static void rollOut()
	{
		// roll out the events
		int ID = 1;
		for (PeriodicEvent e: pEvents)
		{
			for (int pTime: e.getPeriodicTimes())
			{
				for (int k =  (int) Math.ceil((earliestTime-pTime) / ((double) period));
				         k <= (int) Math.floor((latestTime-pTime) / ((double) period));
				         k++)
				{
					int currentTime = k*period + pTime;
					NonPeriodicEvent newEvent =
						new NonPeriodicEvent(ID++, currentTime, e.getWeight(), e.isArrivalEvent(), e.isStartofTrip(), e.isEndOfTrip(), e.getID());
					newEvent.setStation(e.getStation());
					e.addRolledOutEvent(newEvent);
					events.add(newEvent);
				}
			}
			Collections.sort(e.getRolledOutEvents());
		}



		// before rolling out the activities, we have to add some
		// headways to make sure we have headways between vehicles
		// of the same line
		if (headways)
		{
			ID = pActivities.size() + 1;
			LinkedList<PeriodicActivity> toAdd = new LinkedList<PeriodicActivity>();
			for (PeriodicActivity currentPActivity: pActivities)
			{
				if (! currentPActivity.getType().equalsIgnoreCase("drive"))
					continue;

				PeriodicEvent pSource = currentPActivity.getSource();
				int lowerBound = eventHeadways[pSource.getID()-1];
				int upperBound = period - lowerBound;

				PeriodicActivity a = new PeriodicActivity(ID, pSource, pSource, lowerBound, upperBound, 0, "headway");
				toAdd.add(a);
				ID++;
			}
			pActivities.addAll(toAdd);
		}



		// roll out activities
		ID = 1;
		for (PeriodicActivity currentPActivity: pActivities)
		{
			int pID = currentPActivity.getID();
			PeriodicEvent pSource = currentPActivity.getSource();
			PeriodicEvent pTarget = currentPActivity.getTarget();
			String type = currentPActivity.getType();
			int lowerBound = currentPActivity.getLowerBound();
			int upperBound = currentPActivity.getUpperBound();
			double weight = currentPActivity.getWeight();

			// For each activity, we equally distribute the customers
			// over all occurrences of this activity during one period.
			weight /= pSource.getFrequency();

			// To roll out headway activities, we have to create a headway
			// activity from each rolled-out source event to each rolled-out
			// target event and vice versa.

			if (type.equals("headway"))
			{
				while (lowerBound < 0)
					lowerBound += period;
				while (upperBound < 0)
					upperBound += period;

				while (lowerBound >= period)
					lowerBound -= period;
				while (upperBound >= period)
					upperBound -= period;

				for (NonPeriodicEvent source: pSource.getRolledOutEvents())
				{
					for (NonPeriodicEvent e: pTarget.getRolledOutEvents())
					{
						if (source == e)
							continue;

						NonPeriodicActivity a = new NonPeriodicActivity(ID++, source, e, lowerBound, upperBound, weight, type, pID);
						activities.add(a);
						source.addOutgoingActivity(a);
						e.addIncomingActivity(a);

						a = new NonPeriodicActivity(ID++, e, source, period-upperBound, period-lowerBound, weight, type, pID);
						activities.add(a);
						e.addOutgoingActivity(a);
						source.addIncomingActivity(a);
					}
				}
			}
			// To roll out driving/waiting/changing/turning activities, we have
			// to find for each rolled-out source event that rolled-out target
			// event with minimal (aperiodic) time that respects the lower bound.
			// We can use the fact that for each PEvent, the list rolledOutEvents
			// by construction is sorted by time.
			else
			{
				for (NonPeriodicEvent source: pSource.getRolledOutEvents())
				{
					int sTime = source.getTime();
					for (NonPeriodicEvent e: pTarget.getRolledOutEvents())
					{
						if (e.getTime()-sTime >= lowerBound)
						{
							NonPeriodicActivity a = new NonPeriodicActivity(ID++, source, e, lowerBound, upperBound, weight, type, pID);
							activities.add(a);
							source.addOutgoingActivity(a);
							e.addIncomingActivity(a);
							break;
						}
					}
				}
			}
		}

		// now we delete some events/activities, depending on whether
		// onlyWholeTrips is set to true or false
		HashSet<NonPeriodicEvent> eventsToDelete = new HashSet<NonPeriodicEvent>((int) Math.ceil(4.0/3.0 * events.size()));
		HashSet<NonPeriodicActivity> activitiesToDelete = new HashSet<NonPeriodicActivity>((int) Math.ceil(4.0/3.0 * activities.size()));

		// If only whole (--> complete) trips should be considered, we have to
		// delete all events which have no incoming activities, but are not the
		// start event of some trip. The same holds for all events which have
		// no outgoing activities, but are not the end event of some trip.
		if (onlyWholeTrips)
		{
			if (VERBOSE)
				System.out.println("Rollout: removing incomplete trips");

			// We start with the first type of events.
			LinkedList<NonPeriodicEvent> marked = new LinkedList<NonPeriodicEvent>();

			for (NonPeriodicEvent e: events)
			{
				boolean first = true;
				for (NonPeriodicActivity a: e.getIncomingActivities())
				{
					String type = a.getType();
					if (type.equals("drive") || type.equals("wait"))
					{
						first = false;
						break;
					}
				}
				if (first && ! pEvents.get(e.getPeriodicParentEventID()-1).isStartofTrip())
					marked.add(e);
			}

			while (! marked.isEmpty())
			{
				NonPeriodicEvent e = marked.remove();

				// remove all incoming activities
				for (NonPeriodicActivity a: e.getIncomingActivities())
					a.getSource().getOutgoingActivities().remove(a);

				activitiesToDelete.addAll(e.getIncomingActivities());

				// remove all outgoing activities
				for (NonPeriodicActivity a: e.getOutgoingActivities())
				{
					String type = a.getType();
					NonPeriodicEvent target = a.getTarget();
					if (type.equals("drive") || type.equals("wait"))
						marked.add(target);
					target.getIncomingActivities().remove(a);
				}
				activitiesToDelete.addAll(e.getOutgoingActivities());

				// remove the event itself
				eventsToDelete.add(e);
			}



			// Now, we treat the second type of events.
			marked = new LinkedList<NonPeriodicEvent>();
			for (NonPeriodicEvent e: events)
			{
				boolean last = true;
				for (NonPeriodicActivity a: e.getOutgoingActivities())
				{
					String type = a.getType();
					if (type.equals("drive") || type.equals("wait"))
					{
						last = false;
						break;
					}
				}

				if (last && ! pEvents.get(e.getPeriodicParentEventID()-1).isEndOfTrip())
					marked.add(e);
			}

			while (! marked.isEmpty())
			{
				NonPeriodicEvent e = marked.remove();

				// remove all incoming activities
				for (NonPeriodicActivity a: e.getIncomingActivities())
				{
					String type = a.getType();
					NonPeriodicEvent source = a.getSource();
					if (type.equals("drive") || type.equals("wait"))
						marked.add(source);
					a.getSource().getOutgoingActivities().remove(a);
				}
				activitiesToDelete.addAll(e.getIncomingActivities());

				// remove all outgoing activities
				for (NonPeriodicActivity a: e.getOutgoingActivities())
					a.getTarget().getIncomingActivities().remove(a);

				activitiesToDelete.addAll(e.getOutgoingActivities());

				// remove the event itself
				eventsToDelete.add(e);
			}
		}
		else // onlyWholeTrips == false
		{
			// remove dangling activities
			for (NonPeriodicActivity a: activities)
			{
				if (a.getSource() != null && a.getTarget() != null)
					continue;

				if (a.getSource() != null)
					a.getSource().removeOutgoingActivity(a);
				else if (a.getTarget() != null)
					a.getTarget().removeIncomingActivity(a);

				activitiesToDelete.add(a);
			}

			// remove isolated events
			for (NonPeriodicEvent e: events)
				if (e.getIncomingActivities().isEmpty() && e.getOutgoingActivities().isEmpty())
					eventsToDelete.add(e);
		}

		if (DEBUG)
			System.out.println("Rollout: activities to be removed:\n" + activitiesToDelete);
		activities.removeAll(activitiesToDelete);
		if (DEBUG)
			System.out.println("Rollout: events to be removed:\n" + eventsToDelete);
		events.removeAll(eventsToDelete);

		// now re-number the remaining events/activities
		ID=1;
		for (NonPeriodicEvent e: events)
			e.setID(ID++);
		ID=1;
		for (NonPeriodicActivity a: activities)
			a.setID(ID++);
	}



	private static void writeOutputFiles(Iterable<NonPeriodicEvent> events, Iterable<NonPeriodicActivity> activities) throws IOException
	{
		// write the rolled-out events
		File outputFile = new File(eventOutputFile);
		outputFile.getParentFile().mkdirs();
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
		out.println("#" + aperiodicEventHeader);
		for(NonPeriodicEvent e: events)
		{
			StringBuilder line = new StringBuilder(128);
			line.append(e.getID());
			line.append("; ");
			line.append(e.getPeriodicParentEventID());
			line.append("; ");
			line.append(e.isArrivalEvent() ? "\"arrival\"" : "\"departure\"");
			line.append("; ");
			line.append(e.getTime());
			line.append("; ");
			line.append(e.getWeight());
			line.append("; ");
			line.append(e.getStation());
			out.println(line);
		}
		out.close();

		out = new PrintWriter(new BufferedWriter(new FileWriter(endEventsofTripsFileName)));
		out.println("# event-id");
		for(NonPeriodicEvent e: events)
			if (e.isEndOfTrip())
				out.println(e.getID());
		out.close();

		// write the rolled-out activities
		out = new PrintWriter(new BufferedWriter(new FileWriter(activityOutputFile)));
		out.println("#" + aperiodicActivityHeader);
		for(NonPeriodicActivity a: activities)
		{
			// for non-periodic timetabling, we consider only
			// forward headways!
			if (   rolloutFortimetabling
			    && a.getType().equals("headway")
			    && a.getTarget().getTime() - a.getSource().getTime() < a.getLowerBound())
			{
				continue;
			}

			StringBuilder line = new StringBuilder(128);
			line.append(a.getID());
			line.append("; ");
			line.append(a.getPeriodicID());
			line.append("; \"");
			line.append(a.getType());
			line.append("\"; ");
			line.append(a.getSource().getID());
			line.append("; ");
			line.append(a.getTarget().getID());
			line.append("; ");
			line.append(a.getLowerBound());
			line.append("; ");
			line.append(a.getUpperBound());
			line.append("; ");
			line.append(a.getWeight());
			out.println(line);
		}
		out.close();
	}


// ------------------------------------------------
// Path Distribution

	public static void rolloutPassengerPaths() throws Exception
	{
		// output (human-readable) timestamps for performance evaluation
		System.out.println(new Date());
		System.out.println("Parsing and constructing EAN...");
		// read EAN without source delays and without disposition timetable
		Net = IO.readNonPeriodicEANetwork(false, false);
		// delete weights for we will calculate new ones
		for (NonPeriodicChangingActivity a : Net.getChangingActivities())
			a.setWeight(0.0);
		for (NonPeriodicEvent e : Net.getEvents())
			e.setWeight(0.0);
		// construct collapsed version
		CollapsedEANetwork cean = new CollapsedEANetwork(Net);
		// construct reachability matrix (long integers, considered bitwise,
		// for more efficient CPU computations than with individual booleans)
		int n = cean.events.length;
		System.out.println("Events in Collapsed EAN: "+n);
		long[][] m = new long[n][(n-1)/64 + 1];
		for (int i = 0; i < n; i++)
			for (CollapsedActivity a : cean.events[i].getOutgoingActivities())
				Warshall.setReachable(m, i, a.getTarget().getID(), true);
		System.out.println(new Date());
		System.out.println("Calling Warshall to apply transitive closure...");
		Warshall.applyTransitiveClosure(m);
		System.out.println(new Date());

		// save (and re-use) references to events by station ID
		Hashtable<Integer, NonPeriodicEvent[]> departuresFromOrigin =
				new Hashtable<Integer, NonPeriodicEvent[]>();
		Hashtable<Integer, NonPeriodicEvent[]> arrivalsAtDestination =
				new Hashtable<Integer, NonPeriodicEvent[]>();
		Hashtable<Integer, CollapsedEvent[]> connectionsFromDeparture =
				new Hashtable<Integer, CollapsedEvent[]>();
		Hashtable<Integer, CollapsedEvent[]> connectionsToArrival =
				new Hashtable<Integer, CollapsedEvent[]>();


		Config config = new Config(new File("basis/Config.cnf"));
		int beginOfDay = config.getIntegerValue("DM_earliest_time");
		String odFileName = config.getStringValue("default_od_file");
		String odExpandedFileName = config.getStringValue("default_od_expanded_file");
		String pathsFileName = config.getStringValue("default_passenger_paths_file");
		BufferedReader reader = new BufferedReader(new FileReader(odFileName));
		BufferedWriter writer = new BufferedWriter(new FileWriter(pathsFileName));
		BufferedWriter odWriter = new BufferedWriter(new FileWriter(odExpandedFileName));
		writer.write("#weight;source-id;target-id;source-station-id;target-station-id;changes(seperated by \",\")");
		writer.newLine();
		String line;
		// Now iterating over all OD pairs (directly while reading the file)
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())	continue;
			String[] tokens = line.split(";");
			int origin = Integer.parseInt(tokens[0].trim());
			int destination = Integer.parseInt(tokens[1].trim());
			double weight = Double.parseDouble(tokens[2].trim());
			if (weight == 0.0)
			{
				//System.out.println("skipped due to lack of passengers");
				continue;
			}
			writeOdPaths(writer, odWriter, cean, Net, m, origin, destination, weight,
					beginOfDay, departuresFromOrigin, arrivalsAtDestination,
					connectionsFromDeparture, connectionsToArrival);
		}
		odWriter.close();
		writer.close();
		System.out.println(new Date());
		reader.close();
	}

	private static void writeOdPaths(BufferedWriter writer, BufferedWriter odWriter,
			CollapsedEANetwork cean, NonPeriodicEANetwork Net, long[][] m,
			int origin, int destination, double weight, int beginOfDay,
			Hashtable<Integer, NonPeriodicEvent[]> departuresFromOrigin,
			Hashtable<Integer, NonPeriodicEvent[]> arrivalsAtDestination,
			Hashtable<Integer, CollapsedEvent[]> connectionsFromDeparture,
			Hashtable<Integer, CollapsedEvent[]> connectionsToArrival

			) throws IOException
	{
		NonPeriodicEvent[] departures, arrivals;
		if (!departuresFromOrigin.containsKey(origin))
		{
			departures = Net.getEventsAtStation(origin, false);
			Arrays.sort(departures);
			departuresFromOrigin.put(origin, departures);
		}
		else departures = departuresFromOrigin.get(origin);
		if (!arrivalsAtDestination.containsKey(destination))
		{
			arrivals = Net.getEventsAtStation(destination, true);
			Arrays.sort(arrivals);
			arrivalsAtDestination.put(destination, arrivals);
		}
		else arrivals = arrivalsAtDestination.get(destination);
		for (NonPeriodicEvent departure : departures) {
            if (!connectionsFromDeparture.containsKey(departure.getID()))
                connectionsFromDeparture.put(departure.getID(),
                    cean.getConnectionsFromDeparture(departure));
        }
		for (NonPeriodicEvent arrival : arrivals) {
            if (!connectionsToArrival.containsKey(arrival.getID()))
                connectionsToArrival.put(arrival.getID(),
                    cean.getConnectionsToArrival(arrival));
        }
		// find all shortest paths
		// traversing departures in descending order allows to judge
		// immediately whether the earliest arrival time for the current
		// departure is possibly later than or equal to the earliest
		// arrival time for a later departure (as all later departures
		// have already been considered)
		int earliestArrivalTime = Integer.MAX_VALUE;
		// each entry will be a collection of equivalent shortest paths
		// for the current OD pair at one distinct departure *time*
		LinkedList<LinkedList<LinkedList<NonPeriodicActivity>>> odSp =
				new LinkedList<LinkedList<LinkedList<NonPeriodicActivity>>>();
		for (int d = departures.length - 1; d >= 0; d--)
		{
			// find shortest paths for every possible departure
			LinkedList<LinkedList<NonPeriodicActivity>> shortestPaths =
					new LinkedList<LinkedList<NonPeriodicActivity>>();
			// we actually want to traverse departure *times*,
			// so if there are multiple departure events with the same time,
			// we have to consider them together
			int sameTimeDepartures = 0;
			while ((d - sameTimeDepartures > 0)
					&& (departures[d].getTime() ==
						departures[d - sameTimeDepartures - 1].getTime()))
				sameTimeDepartures++;
			// traversing arrivals in ascending order to find the earliest
			for (NonPeriodicEvent arrival : arrivals)
			{
				// no need to consider arrivals earlier than the departure
				if (arrival.getTime() < departures[d].getTime()) continue;
				// if the earliest previously found arrival time
				// (i.e., for this or a later departure) is already
				// exceeded, no need to look any further
				if (arrival.getTime() > earliestArrivalTime) break;
				// limited depth-first-search with increasing depth limit
				// until paths are found
				// (we only want paths with minimal number of changes (= "hops"))
				for (int hops = 0; shortestPaths.size() == 0; hops++)
				{
					boolean reachable = false;
					for (int i = 0; i <= sameTimeDepartures; i++)
					{
						NonPeriodicEvent departure = departures[d - i];
						// look for all possible paths from departure to arrival
						if (hops == 0) // special case without Warshall matrix
						{
							LinkedList<NonPeriodicActivity> directPath =
									NonPeriodicEANetwork.getPathOnTrip(departure,
											arrival);
							if (directPath != null){
								shortestPaths.add(directPath);
								earliestArrivalTime = arrival.getTime();
							}
							continue; // the rest of the loop body is "else" (hops > 0)
						}
						CollapsedEvent[] departureConnections =
								connectionsFromDeparture.get(departure.getID());
						CollapsedEvent[] arrivalConnections =
								connectionsToArrival.get(arrival.getID());
						for (CollapsedEvent departureConnection :
								departureConnections)
							for (CollapsedEvent arrivalConnection :
									arrivalConnections)
							{
								if (Warshall.isReachable(m,
										departureConnection.getID(),
										arrivalConnection.getID()))
								{

									reachable = true;
									LinkedList<LinkedList<NonPeriodicActivity>>
											newPaths =
											depthFirstRecursionAddAllPaths(m,
													departureConnection,
													arrivalConnection, 1, hops);
									for (LinkedList<NonPeriodicActivity> subpath :
											newPaths)
									{
										subpath.addAll(0, NonPeriodicEANetwork
												.getPathOnTrip(departure,
														departureConnection
																.getOriginalEvent())
												);
										subpath.addAll(NonPeriodicEANetwork
												.getPathOnTrip(arrivalConnection
														.getOriginalEvent(),
														arrival));
									}
									shortestPaths.addAll(newPaths);
									earliestArrivalTime = arrival.getTime();
								}
							}
					}
				  // no need to try every depth if not reachable at all
					// (otherwise infinite loop for unreachable dep-arr pairs!)
					if ((hops > 0) && !reachable) break;
				}
			}
			if (shortestPaths.size() > 0)
				odSp.addFirst(shortestPaths);
			d -= sameTimeDepartures;
		}

		if (odSp.size() <= 0)
		{
			//System.out.println("WARNING: there is not path at all between stations "
			//		+ origin + " and " + destination + "!");
			return;
		}


		// finish off the current OD pair by calculating weights
		// for individual paths and writing them out
		// PASSENGER DISTRIBUTION HERE

		// scaling for uniform distribution between last possible departure
		// and begin of day
		int latestDeparture = odSp.getLast().getFirst().getFirst().getSource().getTime();
		//System.err.println("Latest Departure from "+origin+" to "+destination+": "+latestDeparture);
		double weightPerSecond = 1.0 * weight / (latestDeparture - beginOfDay);
		int weightAlreadyDistributed = 0;
		for (LinkedList<LinkedList<NonPeriodicActivity>> shortestPaths : odSp)
		{
			// "path group" = paths with same departure time
			int pathGroupWeight;
			if(latestDeparture==beginOfDay)
				pathGroupWeight=(int)weight;
			else
				pathGroupWeight = (int) Math.round(weightPerSecond *
					(shortestPaths.getFirst().getFirst().getSource().getTime() -
							beginOfDay)); // sort of the "Cumulative Distribution Function" (CDF)
			pathGroupWeight -= weightAlreadyDistributed; // "difference of CDF values"
			weightAlreadyDistributed += pathGroupWeight;
			// distribute equally within the current "path group"
			odWriter.write(origin + ";" + destination + ";");
			odWriter.write(shortestPaths.getFirst().getFirst().getSource().getTime()
					+ ";" + pathGroupWeight);
			odWriter.newLine();
			int div = pathGroupWeight / shortestPaths.size();
			int mod = pathGroupWeight % shortestPaths.size();
			int spC = 0;
			for (LinkedList<NonPeriodicActivity> sp : shortestPaths)
			{
				// path format: weight; (aperiodic) departure EVENT from origin;
				// (aperiodic) arrival EVENT at destination;
				// comma-separated list of (aperiodic) change ACTIVITIES in between;
				// comma-separated list of (aperiodic) headway ACTIVITIES in between
				int partialWeight = (spC++ < mod ? div + 1 : div);
				writer.write(partialWeight + ";");
				writer.write(sp.getFirst().getSource().getID() + ";");
				writer.write(sp.getLast().getTarget().getID() + ";");
				writer.write(sp.getFirst().getSource().getStation() + ";");
				writer.write(sp.getLast().getTarget().getStation() + ";");
				for (NonPeriodicActivity a : sp){
					if (a.getType().equals("change"))
					{
						writer.write(a.getID() + ",");
						a.setWeight(a.getWeight() + partialWeight);
					}
				}
				sp.getLast().getTarget().setWeight(sp.getLast().getTarget().getWeight()
						+ partialWeight);
				writer.newLine();
			}
		}
		if (weightAlreadyDistributed != (int) weight)
			System.out.println("WARNING: distribution wrong at " + origin + "-" +
					destination + ": " + weight + " vs. " + weightAlreadyDistributed);
	}

	private static LinkedList<LinkedList<NonPeriodicActivity>>
			depthFirstRecursionAddAllPaths(long[][] m,
			CollapsedEvent departure,
			CollapsedEvent arrival, int hops, int maxHops)
	{
		LinkedList<LinkedList<NonPeriodicActivity>> pathCollection =
				new LinkedList<LinkedList<NonPeriodicActivity>>();
		if (departure == arrival) // exit criterion
		{
			pathCollection.add(new LinkedList<NonPeriodicActivity>());
			return pathCollection;
		}
		if (hops >= maxHops) return pathCollection;
		for (CollapsedActivity a : departure.getOutgoingActivities())
		{
			// for every possible next hop
			LinkedList<LinkedList<NonPeriodicActivity>> subpaths = null;
			// recursively get all paths from next hop to target
			if (Warshall.isReachable(m,
					a.getTarget().getID(), arrival.getID()))
				subpaths = depthFirstRecursionAddAllPaths(m, a.getTarget(),
						arrival, hops + 1, maxHops);
			if ((subpaths != null) && (subpaths.size() > 0))
			{
				// and add the current hop in front of all paths
				for (LinkedList<NonPeriodicActivity> subpath : subpaths)
					subpath.addAll(0,
							Arrays.asList(a.getOriginalActivities()));
				pathCollection.addAll(subpaths);
			}
		}
		return pathCollection;
	}


}
