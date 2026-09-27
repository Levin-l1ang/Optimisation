import java.io.*;
import java.util.*;



public class DelayGenerator
{
	private DelayGenerator() {}  // class only contains static methods



	// settings from config file
	private static String generator;
	private static boolean delayEvents;
	private static boolean delayActivities;
	private static boolean absoluteDelays;
	private static boolean absoluteDelaysCount;
	private static boolean appendDelays;
	private static long seed;
	private static int numberOfDelays;
	private static int stationForDelays;
	private static String stationFile;
	private static String periodicEventsFile;
	private static int trackForDelays;
	private static String trackFile;
	private static int earliestTime;
	private static int latestTime;
	private static int minTime;
	private static int maxTime;
	private static int minDelay;
	private static int maxDelay;
	private static String eventDelayFile;
	private static String activityDelayFile;
	private static boolean CHECK_CONSISTENCY;
	private static boolean DEBUG;
	private static boolean VERBOSE;
	


	public static void main(String[] args) throws Exception
	{
        readConfig();

		NonPeriodicEANetwork Net = IO.readNonPeriodicEANetwork(appendDelays, false);

		LinkedList<NonPeriodicEvent> delayedEvents =
			new LinkedList<NonPeriodicEvent>();
		LinkedList<NonPeriodicActivity> delayedActivities =
			new LinkedList<NonPeriodicActivity>();

		if (generator.equals("uniform_distribution"))
		{
			if (delayEvents)
				delayedEvents = generateRandomEventDelays(Net.getEvents());
			if (delayActivities)
			{
				delayedActivities =
					generateRandomActivityDelays(Net.getDrivingActivities());
			}
		}
		else if(generator.equals("neg_exp")){
			if (delayActivities)
			{
				delayedActivities =
					generateRandomActivityDelaysExp(Net.getDrivingActivities());
			}
		}
		else if (generator.equals("events_in_station"))
		{
			delayedEvents = delayEventsInStation(Net.getEvents());
		}
		else if (generator.equals("events_in_station_exp"))
		{
			delayedEvents = delayEventsInStationExp(Net.getEvents());
		}
		else 
		{
			delayedActivities = delayActivitiesOnTrack(Net.getDrivingActivities());
		}

		outputDelays(delayedEvents, delayedActivities, Net);
	}


	private static void readConfig() throws Exception
	{
		Config config = new Config(new File("basis/Config.cnf"));

		generator = config.getStringValue("delays_generator");
		absoluteDelays = config.getBooleanValue("delays_absolute_numbers");
		absoluteDelaysCount = config.getBooleanValue("delays_count_is_absolute");
		appendDelays = config.getBooleanValue("delays_append");
		numberOfDelays = config.getIntegerValue("delays_count");
		if ((!absoluteDelaysCount) && numberOfDelays > 100)
		{
				throw new Exception(  "DelayGenerator: if delays_count_is_absolute"
				                    + " is set, delays_count must be <= 100");
		}
		if (generator.equals("uniform_distribution"))
		{
			delayEvents = config.getBooleanValue("delays_events");
			delayActivities = config.getBooleanValue("delays_activities");
			if ((! delayActivities) && (! absoluteDelays))
			{
				throw new Exception(  "DelayGenerator: setting both delays_activities"
				                    + " and delays_absolute_numbers to false does not"
				                    + " make sense for delay generator uniform_distribution!");
			}
		}
		else if (generator.equals("events_in_station") || generator.equals("events_in_station_exp"))
		{
			if (! absoluteDelays)
			{
				throw new Exception(  "DelayGenerator: setting delays_absolute_numbers"
				                    + " to false does not make sense for delay generator"
				                    + " events_in_station!");
			}
			delayEvents = true;
			delayActivities = false;
			stationForDelays = config.getIntegerValue("delays_station_id_for_delays");
			stationFile = config.getStringValue("default_stops_file");
			periodicEventsFile = config.getStringValue("default_events_periodic_file");
		}
		else if (generator.equals("activities_on_track"))
		{
			delayEvents = false;
			delayActivities = true;
			trackForDelays = config.getIntegerValue("delays_edge_id_for_delays");
			trackFile = config.getStringValue("default_edges_file");
			periodicEventsFile = config.getStringValue("default_events_periodic_file");
		}
		else if (generator.equals("neg_exp"))
		{
			delayEvents = config.getBooleanValue("delays_events");
			delayActivities = config.getBooleanValue("delays_activities");
			if ((! delayActivities) && (! absoluteDelays))
			{
				throw new Exception(  "DelayGenerator: setting both delays_activities"
				                    + " and delays_absolute_numbers to false does not"
				                    + " make sense for delay generator uniform_distribution!");
			}
		}
		else
		{
			throw new Exception(  "DelayGenerator: unsupported delay "
			                    + "generator: " + generator);
		}

		earliestTime = config.getIntegerValue("DM_earliest_time");
		latestTime = config.getIntegerValue("DM_latest_time");
		minTime = config.getIntegerValue("delays_min_time");
		maxTime = config.getIntegerValue("delays_max_time");
		minDelay = config.getIntegerValue("delays_min_delay");
		maxDelay = config.getIntegerValue("delays_max_delay");
		seed = (long)config.getIntegerValue("delays_seed");
		eventDelayFile = config.getStringValue("default_event_delays_file");
		activityDelayFile = config.getStringValue("default_activity_delays_file");
		CHECK_CONSISTENCY = config.getBooleanValue("DM_enable_consistency_checks");
		DEBUG = config.getBooleanValue("DM_debug");
		VERBOSE = config.getBooleanValue("DM_verbose");

		if (earliestTime > latestTime)
		{
			throw new Exception(  "DelayGenerator: DM_latest_time must not "
			                    + "be smaller than DM_earliest_time");
		}
		if (minTime > maxTime)
		{
			throw new Exception(  "DelayGenerator: delays_max_time must not "
			                    + "be smaller than delays_min_time");
		}
		if (minTime < earliestTime || maxTime > latestTime)
		{
			throw new Exception(  "  DelayGenerator: [delays_min_time,delays_max_time]"
			                    + " must be contained in [DM_earliest_time,DM_latest_time]");
		}
		if (minDelay < 0)
			throw new Exception("DelayGenerator: delays_min_time must not be negative");
		if (minDelay > maxDelay)
		{
			throw new Exception(  "DelayGenerator: delays_max_delay must not "
			                    + "be smaller than delays_min_delay");
		}
		if (numberOfDelays < 0)
			throw new Exception("DelayGenerator: delays_count must not be negative");

		if (DEBUG)
			VERBOSE = true;

		if (VERBOSE)
		{
			System.out.println("DelayGenerator: using the following configuration:");
			System.out.println(  "  expecting rolled out events in [" + earliestTime
			                   + "," + latestTime + "]");
			if (! absoluteDelaysCount)
				System.out.println("  using delays_count as relative value");
			if (delayEvents)
			{
				if (generator.equals("uniform_distribution"))
				{
					System.out.println(  "  generating " + numberOfDelays
					                   + (absoluteDelaysCount ? "" : "%")
					                   + " random delays on events between "
					                   + minDelay + " and " + maxDelay
					                   + " seconds in [" + minTime + ","
					                   + maxTime + "]");
				}
				else if (stationForDelays > 0 && numberOfDelays > 0)
				{
					System.out.println(  "  delaying " + numberOfDelays + " events in [" + minTime
					                   + "," + maxTime + "] in station "
					                   + stationForDelays + (generator.equals("events_in_station_exp")?" with exp distribution and mean":"") + " between " + minDelay
					                   + " and " + maxDelay + " seconds");
				}
				else if(numberOfDelays > 0)
				{
					System.out.println(  "  delaying " + numberOfDelays + " events in [" + minTime
					                   + "," + maxTime + "] in a random station "
					                   + (generator.equals("events_in_station_exp")?" with exp distribution and mean":"") + "between " + minDelay + " and "
					                   + maxDelay + " seconds");
				} else {
					System.out.println(  "  delaying all events in [" + minTime
					                   + "," + maxTime + "] in a random station "
					                   + (generator.equals("events_in_station_exp")?" with exp distribution and mean":"") + "between " + minDelay + " and "
					                   + maxDelay + " seconds");					
				}
				System.out.println(  "  delay output file: "
				                   + new File(eventDelayFile).getAbsolutePath());
			}
			if (delayActivities)
			{
				if (generator.equals("uniform_distribution"))
				{
					if (absoluteDelays)
					{
						System.out.println(  "  generating " + numberOfDelays
						                   + (absoluteDelaysCount ? "" : "%")
						                   + " delays on activities between "
						                   + minDelay + " and " + maxDelay
						                   + " seconds in [" + minTime + ","
						                   + maxTime + "]");
					}
					else
					{
						System.out.println(  "  generating " + numberOfDelays
						                   + (absoluteDelaysCount ? "" : "%")
						                   + " delays on activities of "
						                   + minDelay + "% - " + maxDelay
						                   + "% of their minimal duration in ["
						                   + minTime + "," + maxTime + "]");
					}
				}
				else if (trackForDelays > 0)
				{
					if (absoluteDelays && numberOfDelays > 0)
					{
						System.out.println(  "  delaying  "+ numberOfDelays + " activities in ["
						                   + minTime + "," + maxTime
						                   + "] on track " + trackForDelays
						                   + " between " + minDelay
						                   + " and " + maxDelay + " seconds");
					}
					else if(numberOfDelays > 0)
					{
						System.out.println(  "  delaying " + numberOfDelays + " activities in ["
						                   + minTime + "," + maxTime
						                   + "] on track " + trackForDelays
						                   + " by " + minDelay + "% - "
						                   + maxDelay + "% of their minimal"
						                   + " duration");
					} else if (absoluteDelays && numberOfDelays <= 0){
						System.out.println(  "  delaying  all activities in ["
						                   + minTime + "," + maxTime
						                   + "] on track " + trackForDelays
						                   + " between " + minDelay
						                   + " and " + maxDelay + " seconds");

					} else {
						System.out.println(  "  delaying all activities in ["
						                   + minTime + "," + maxTime
						                   + "] on track " + trackForDelays
						                   + " by " + minDelay + "% - "
						                   + maxDelay + "% of their minimal"
						                   + " duration");
					}
				}
				else
				{
					if (absoluteDelays && numberOfDelays > 0)
					{
						System.out.println(  "  delaying " + numberOfDelays + " activities in ["
						                   + minTime + "," + maxTime
						                   + "] on a random track between "
						                   + minDelay + " and " + maxDelay
						                   + " seconds");
					}
					else if(numberOfDelays > 0)
					{
						System.out.println(  "  delaying " + numberOfDelays + " activities in ["
						                   + minTime + "," + maxTime
						                   + "] on a random track by " + minDelay
						                   + "% - " + maxDelay + "% of their "
						                   + "minimal duration");
					} else if(absoluteDelays && numberOfDelays <= 0){
						System.out.println(  "  delaying all activities in ["
						                   + minTime + "," + maxTime
						                   + "] on a random track between "
						                   + minDelay + " and " + maxDelay
						                   + " seconds");
					} else {
						System.out.println(  "  delaying all activities in ["
						                   + minTime + "," + maxTime
						                   + "] on a random track by " + minDelay
						                   + "% - " + maxDelay + "% of their "
						                   + "minimal duration");	
					}
				}
				System.out.println(  "  delay output file: "
				                   + new File(activityDelayFile).getAbsolutePath());
			}
		}
	}



	private static LinkedList<NonPeriodicEvent>
		generateRandomEventDelays(Set<NonPeriodicEvent> E) throws Exception
	{
		ArrayList<NonPeriodicEvent> E2 = new ArrayList<NonPeriodicEvent>(E.size());
		for (NonPeriodicEvent e: E)
		{
			int time = e.getTime();
			if (time >= minTime && time <= maxTime)
				E2.add(e);
		}

		int count;
		if (absoluteDelaysCount)
			count = numberOfDelays;
		else
			count = (int) Math.floor((E2.size()*numberOfDelays) / 100);

		if (E2.size() < count)
		{
			throw new Exception(  "DelayGenerator: number of events to delay "
			                    + "is larger than total number of events that "
			                    + "might be delayed!");
		}

		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);
			
		HashSet<Integer> indices = new HashSet<Integer>(count);
		for (int i=0; i<count; i++)
			if (! indices.add(r.nextInt(E2.size())))
				i--;

		LinkedList<NonPeriodicEvent> delayedEvents =
			new LinkedList<NonPeriodicEvent>();
		for (int i: indices)
		{
			NonPeriodicEvent e = E2.get(i);
			e.setSourceDelay(e.getSourceDelay() + minDelay + r.nextInt(maxDelay - minDelay + 1));
			delayedEvents.add(e);
		}

		return delayedEvents;
	}



	private static LinkedList<NonPeriodicActivity>
		generateRandomActivityDelays(Set<NonPeriodicActivity> A) throws Exception
	{
		ArrayList<NonPeriodicActivity> A2 = new ArrayList<NonPeriodicActivity>(A.size());
		for (NonPeriodicActivity a: A)
			if (a.getSource().getTime() >= minTime && a.getTarget().getTime() <= maxTime)
				A2.add(a);

		int count;
		if (absoluteDelaysCount)
			count = numberOfDelays;
		else
			count = (int) Math.floor((A2.size()*numberOfDelays) / 100);

		if (A2.size() < count)
		{
			throw new Exception(  "DelayGenerator: number of activities to "
			                    + "delay is larger than total number of drive "
			                    + "activities that can be delayed!");
		}
		
		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);

		HashSet<Integer> indices = new HashSet<Integer>(count);
		for (int i=0; i<count; i++)
			if (! indices.add(r.nextInt(A2.size())))
				i--;

		LinkedList<NonPeriodicActivity> delayedActivities =
			new LinkedList<NonPeriodicActivity>();
		for (int i: indices)
		{
			NonPeriodicActivity a = A2.get(i);
			if (absoluteDelays)
			{
				a.setSourceDelay((int)a.getSourceDelay() + minDelay + r.nextInt(maxDelay - minDelay + 1));
			}
			else
			{
				// deprecated: nominal duration: int duration = a.getTarget().getTime() - a.getSource().getTime();
				int duration = a.getLowerBound();
				double percent = (minDelay + r.nextDouble() * (maxDelay-minDelay));
				a.setSourceDelay((int) (duration * percent / 100));
			}
			delayedActivities.add(a);
		}

		return delayedActivities;
	}
	
	private static LinkedList<NonPeriodicActivity>
		generateRandomActivityDelaysExp(Set<NonPeriodicActivity> A) throws Exception
	{
		ArrayList<NonPeriodicActivity> A2 = new ArrayList<NonPeriodicActivity>(A.size());
		for (NonPeriodicActivity a: A)
			if (a.getSource().getTime() >= minTime && a.getTarget().getTime() <= maxTime)
				A2.add(a);

		int count;
		if (absoluteDelaysCount)
			count = numberOfDelays;
		else
			count = (int) Math.floor((A2.size()*numberOfDelays) / 100);

		if (A2.size() < count)
		{
			throw new Exception(  "DelayGenerator: number of activities to "
			                    + "delay is larger than total number of drive "
			                    + "activities that can be delayed!");
		}
		
		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);

		HashSet<Integer> indices = new HashSet<Integer>(count);
		for (int i=0; i<count; i++)
			if (! indices.add(r.nextInt(A2.size())))
				i--;

		LinkedList<NonPeriodicActivity> delayedActivities =
			new LinkedList<NonPeriodicActivity>();
		for (int i: indices)
		{
			NonPeriodicActivity a = A2.get(i);
			if (absoluteDelays)
			{
				if(!(a.getSourceDelay()>0.0))
					a.setSourceDelay((int)Math.round(a.getSourceDelay() - 1*(minDelay + r.nextInt(maxDelay - minDelay + 1))*Math.log(r.nextDouble())));
			}
			delayedActivities.add(a);
		}

		return delayedActivities;
	}
	
		private static LinkedList<NonPeriodicEvent>
		delayEventsInStationExp(LinkedHashSet<NonPeriodicEvent> events) throws IOException
	{
		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);
			
		int delaysCount;
		int[] stationsOfPeriodicEvents = mapPeriodicEventsToStations();
		
		int count = Tools.countRelevantLines(stationFile);
		
		if (stationForDelays < 1)
			stationForDelays = 1 + r.nextInt(count);
		
		ArrayList<NonPeriodicEvent> delayableEvents = new ArrayList<NonPeriodicEvent>();

		for (NonPeriodicEvent e: events)
		{
			if(e.getTime() >= minTime && e.getTime() <= maxTime){
				if (stationsOfPeriodicEvents[e.getPeriodicParentEventID()-1] == stationForDelays)
				{
					delayableEvents.add(e);
				}
			}
		}


		
		if(absoluteDelaysCount && numberOfDelays >0)
			delaysCount = numberOfDelays;
		else if(!absoluteDelaysCount && numberOfDelays >0 && numberOfDelays<=100)
			delaysCount = (int) Math.floor((delayableEvents.size()*numberOfDelays) / 100);
		else
			delaysCount = delayableEvents.size();
			
			
		if (stationForDelays > count)
		{
			throw new IOException("  DelayGenerator: delays_station_id_for_delays "
			                      + "is set to" + stationForDelays + ", but "
			                      + new File(stationFile).getAbsolutePath()
			                      + " only contains " + count + " stations");
		}
		

		
		if(delayableEvents.size()<delaysCount)
		{
			System.out.println("  DelayGenerator: delays_station_id_for_delays "
			                      + "is supposed to generate " + delaysCount + " delays, but there are only " + delayableEvents.size() + " delays possible.\n"+
			                      "Number of delayed events in station is set to " + delayableEvents.size() + ".");
			delaysCount = delayableEvents.size();
		}



		if (DEBUG)
		{
			System.out.println(  "DelayGenerator: delaying all events with exponential distribution in station "
			                   + stationForDelays);
			System.out.println(  "  reading stations from file "
			                   + new File(stationFile).getAbsolutePath());
			System.out.println(  "  reading periodic events from file "
			                   + new File(periodicEventsFile).getAbsolutePath());
		}
		
		java.util.Collections.shuffle(delayableEvents, r);
		
		LinkedList<NonPeriodicEvent> delayedEvents =
			new LinkedList<NonPeriodicEvent>();
			System.out.println(delaysCount);
		for (NonPeriodicEvent e:delayableEvents)
		{
				if(delayableEvents.indexOf(e)<delaysCount){
					e.setSourceDelay((int)Math.round(-1*(minDelay + r.nextInt(maxDelay - minDelay + 1))*Math.log(r.nextDouble())));
					delayedEvents.add(e);
				}
		}
		return delayedEvents;
	}

	private static LinkedList<NonPeriodicEvent>
		delayEventsInStation(LinkedHashSet<NonPeriodicEvent> events) throws IOException
	{
		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);
			
		int delaysCount;
		int[] stationsOfPeriodicEvents = mapPeriodicEventsToStations();
		
		int count = Tools.countRelevantLines(stationFile);
		
		if (stationForDelays < 1)
			stationForDelays = 1 + r.nextInt(count);
		
		ArrayList<Integer> delayableEvents = new ArrayList<Integer>();

		for (NonPeriodicEvent e: events)
		{
			if(e.getTime() >= minTime && e.getTime() <= maxTime){
				if (stationsOfPeriodicEvents[e.getPeriodicParentEventID()-1] == stationForDelays)
				{
					delayableEvents.add(e.getID());
				}
			}
		}


		
		if(absoluteDelaysCount && numberOfDelays >0)
			delaysCount = numberOfDelays;
		else if(!absoluteDelaysCount && numberOfDelays >0 && numberOfDelays<=100)
			delaysCount = (int) Math.floor((delayableEvents.size()*numberOfDelays) / 100);
		else
			delaysCount = delayableEvents.size();
			
			
		if (stationForDelays > count)
		{
			throw new IOException("  DelayGenerator: delays_station_id_for_delays "
			                      + "is set to" + stationForDelays + ", but "
			                      + new File(stationFile).getAbsolutePath()
			                      + " only contains " + count + " stations");
		}
		

		
		if(delayableEvents.size()<delaysCount)
		{
			System.out.println("  DelayGenerator: delays_station_id_for_delays "
			                      + "is supposed to generate " + delaysCount + " delays, but there are only " + delayableEvents.size() + " delays possible.\n"+
			                      "Number of delayed events in station is set to " + delayableEvents.size() + ".");
			delaysCount = delayableEvents.size();
		}



		if (DEBUG)
		{
			System.out.println(  "DelayGenerator: delaying all events in station "
			                   + stationForDelays);
			System.out.println(  "  reading stations from file "
			                   + new File(stationFile).getAbsolutePath());
			System.out.println(  "  reading periodic events from file "
			                   + new File(periodicEventsFile).getAbsolutePath());
		}
		
		java.util.Collections.shuffle(delayableEvents);
		
		LinkedList<NonPeriodicEvent> delayedEvents =
			new LinkedList<NonPeriodicEvent>();
		for (NonPeriodicEvent e:events)
		{
				if(delayableEvents.contains(e.getID()) && delayableEvents.indexOf(e.getID())<delaysCount){
					e.setSourceDelay(minDelay + r.nextInt(maxDelay - minDelay + 1));
					delayedEvents.add(e);
				}
		}
		return delayedEvents;
	}



	private static LinkedList<NonPeriodicActivity>
		delayActivitiesOnTrack(LinkedHashSet<NonPeriodicActivity> activities)
			throws IOException
	{
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(trackFile))
		{
			throw new IOException(  "DelayGenerator: invalid numbering of IDs "
			                      + "or empty input file: " + trackFile);
		}
		
		Random r;
		if(seed == 0)
			r = new Random();
		else
			r = new Random(seed);

		int delaysCount;
		int[] stationsOfPeriodicEvents = mapPeriodicEventsToStations();

		int count = Tools.countRelevantLines(trackFile);
		
		if (trackForDelays < 1)
			trackForDelays = 1 + r.nextInt(count);
			
		int station1 = -1;
		int station2 = -1;
		BufferedReader reader = new BufferedReader(new FileReader(trackFile));
		String line;
		int i=1;
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
			line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			if (i == trackForDelays)
			{
				String[] tokens = line.split(";");
				station1 = Integer.parseInt(tokens[1].trim());
				station2 = Integer.parseInt(tokens[2].trim());
				break;
			}
			i++;
		}
		reader.close();
		
		ArrayList<Integer> delayableActivities = new ArrayList<Integer>();

		for (NonPeriodicActivity a: activities)
		{
			int start = stationsOfPeriodicEvents[a.getSource().getPeriodicParentEventID()-1];
			int end = stationsOfPeriodicEvents[a.getTarget().getPeriodicParentEventID()-1];
			if ((start == station1 && end == station2) || (start == station2 && end == station1))
			{
				delayableActivities.add(a.getID());
			}
		}
		
		if(absoluteDelaysCount && numberOfDelays >0)
			delaysCount = numberOfDelays;
		else if(!absoluteDelaysCount && numberOfDelays >0 && numberOfDelays<=100)
			delaysCount = (int) Math.floor((delayableActivities.size()*numberOfDelays) / 100);
		else
			delaysCount = delayableActivities.size();
		
		if (trackForDelays > count)
		{
			throw new IOException(  "DelayGenerator: delays_edge_id_for_delays "
			                      + "is set to" + trackForDelays + ", but "
			                      + new File(trackFile).getAbsolutePath()
			                      + " only contains " + count + " tracks");
		}
		if(delayableActivities.size()<delaysCount)
		{
			System.out.println("  DelayGenerator: delays_edge_id_for_delays "
			                      + "is supposed to generate " + delaysCount + " delays, but there are only " + delayableActivities.size() + " delays possible.\n"+
			                      "Number of delayed activities on track " +trackForDelays + " is set to " + delayableActivities.size() + ".");
			delaysCount = delayableActivities.size();
		}
		if (DEBUG)
		{
			System.out.println(  "DelayGenerator: delaying " + delaysCount + " activities on track "
			                   + trackForDelays);
			System.out.println(  "  reading tracks from file "
			                   + new File(trackFile).getAbsolutePath());
			System.out.println(  "  reading periodic events from file "
			                   + new File(periodicEventsFile).getAbsolutePath());
		}


		java.util.Collections.shuffle(delayableActivities);

		LinkedList<NonPeriodicActivity> delayedActivities =
			new LinkedList<NonPeriodicActivity>();
		for (NonPeriodicActivity a: activities)
		{
			int start = stationsOfPeriodicEvents[a.getSource().getPeriodicParentEventID()-1];
			int end = stationsOfPeriodicEvents[a.getTarget().getPeriodicParentEventID()-1];
			if (((start == station1 && end == station2) || (start == station2 && end == station1)) && delayableActivities.indexOf(a.getID())<delaysCount)
			{
				if (absoluteDelays)
				{
					a.setSourceDelay(minDelay + r.nextInt(maxDelay - minDelay + 1));
				}
				else
				{
					int duration = a.getTarget().getTime() - a.getSource().getTime();
					double percentage = minDelay + r.nextDouble() * (maxDelay-minDelay);
					a.setSourceDelay((int) (duration * percentage / 100));
				}
				delayedActivities.add(a);
			}
		}
		return delayedActivities;
	}



	private static int[] mapPeriodicEventsToStations() throws IOException
	{
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(periodicEventsFile))
		{
			throw new IOException (  "DelayGenerator: invalid numbering of IDs "
			                       + "or empty input file: " + periodicEventsFile);
		}
		int[] stationsOfPeriodicEvents =
			new int[Tools.countRelevantLines(periodicEventsFile)];
		BufferedReader reader =
			new BufferedReader(new FileReader(periodicEventsFile));
		String line;
		int i=0;
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int stationID = Integer.parseInt(tokens[2].trim());
			stationsOfPeriodicEvents[i++] = stationID;
		}
		reader.close();
		return stationsOfPeriodicEvents;
	}



	private static void outputDelays(List<NonPeriodicEvent> delayedEvents,
			                         List<NonPeriodicActivity> delayedActivities, NonPeriodicEANetwork Net)
		throws IOException
	{
		if (VERBOSE)
		{
			System.out.println(  "DelayGenerator: writing source delays on "
			                   + "events to file "
			                   + new File(eventDelayFile).getAbsolutePath());
			System.out.println(  "DelayGenerator: writing source delays on "
			                   + "activities to file "
			                   + new File(activityDelayFile).getAbsolutePath());
			if (appendDelays)
			{
				System.out.println(  "DelayGenerator: appending new source "
				                   + "delays to existing ones");
			}
			else
			{
				System.out.println(  "DelayGenerator: replacing existing "
				                   + "source delays by new source delays");
			}
		}

		PrintWriter outputFile =
			new PrintWriter
			    (new BufferedWriter
		             (new FileWriter(eventDelayFile, false)));
		outputFile.println("# ID; delay");
		for (NonPeriodicEvent e: Net.getEvents())
		{
			if(e.getSourceDelay()>0){
				outputFile.println(e.getID() + "; " + e.getSourceDelay());
				if (DEBUG)
				{
					System.out.println(  "  delaying event " + e.getID() + " by "
				                    + e.getSourceDelay());
				}
			}
		}
		outputFile.close();
			
		outputFile = new PrintWriter
		                 (new BufferedWriter
		                      (new FileWriter(activityDelayFile, false)));
		outputFile.println("# ID; delay");
		for (NonPeriodicActivity a: Net.getActivities())
		{
			if(a.getSourceDelay()>0){
				outputFile.println(a.getID() + "; " + a.getSourceDelay());
				if (DEBUG)
				{
					System.out.println(  "  delaying \"" + a.getType()
				                   + "\" activity with ID " + a.getID()
				                   + " by " + a.getSourceDelay());
				}
			}
		}
		outputFile.close();
	}
	
	
}
