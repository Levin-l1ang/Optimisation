import java.io.*;
import java.util.*;


public class DelayedPassengerPaths {
	
	private static long weighted_delayed_time = 0L;
	private static boolean passenger_routing_arrival_on_time;
	private static ArrayList<ArrayList<TreeMap<Integer,Integer>>> expanded_od;
	private static Hashtable<Integer,Integer> passenger_delay;
	private static Hashtable<Integer,Hashtable<Integer,LinkedList<Integer>>> original_passenger_times;
	
	/**
	 * Calculates the weighted sum of passenger delays for the given EAN. The passengers are distributed according to passenger_routing_arrival_on_time.
	 * @param passenger_routing_arrival_on_time Whether or not the arrival of the passengers depends on the entries in OD-Expanded.giv, written by the rollout routine if
	 * 											the parameter rollout_passenger_paths is set to true. If passenger_routing_arrival_on_time is true, the passengers arrive 
	 * 											at the station when their non delayed trains would have departured. If the parameter is false, they are distributed uniformly 
	 * 											over the whole time.
	 * @param Net The EAN the calculation should be made in.
	 * @return The sum of the weighted delayed arrivals of the passengers minus the sum of the weighted non-delayed arrivals of the passengers
	 * @throws Exception if the OD file or the OD-expanded file can not be found or are formatted badly.
	 */
	public static long calculateWeightedDelayTime(boolean passenger_routing_arrival_on_time, NonPeriodicEANetwork Net, File delayedPassengerPathsFile, TreeMap<Integer,NonPeriodicEvent> events_by_id) throws Exception
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(delayedPassengerPathsFile));
		DelayedPassengerPaths.passenger_routing_arrival_on_time = passenger_routing_arrival_on_time;
		expanded_od = IO.readExpandedOD();
		passenger_delay = new Hashtable<Integer,Integer>();
		original_passenger_times = new Hashtable<Integer,Hashtable<Integer,LinkedList<Integer>>>();
		// output (human-readable) timestamps for performance evaluation
		System.out.println(new Date());
		System.out.println("Parsing and constructing EAN...");
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
		
		// Read all relevant config parameters
		Config config = new Config(new File("basis/Config.cnf"));
		int beginOfDay = config.getIntegerValue("DM_earliest_time");
		String odFileName = config.getStringValue("default_od_file");
		BufferedReader reader = new BufferedReader(new FileReader(odFileName));
		String line;
		original_passenger_times=IO.readOriginalPassengerTimes(events_by_id);
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
				continue;
			}
			calculateOdPaths(cean, Net, m, origin, destination, weight,
					beginOfDay, departuresFromOrigin, arrivalsAtDestination,
					connectionsFromDeparture, connectionsToArrival, writer);

		}
		writer.close();
		System.out.println(new Date());
		reader.close();
		//Output the table of delays
		IO.outputDelayTable(passenger_delay);
		return weighted_delayed_time;
	}
	
	private static void calculateOdPaths(
			CollapsedEANetwork cean, NonPeriodicEANetwork Net, long[][] m,
			int origin, int destination, double weight, int beginOfDay,
			Hashtable<Integer, NonPeriodicEvent[]> departuresFromOrigin,
			Hashtable<Integer, NonPeriodicEvent[]> arrivalsAtDestination,
			Hashtable<Integer, CollapsedEvent[]> connectionsFromDeparture,
			Hashtable<Integer, CollapsedEvent[]> connectionsToArrival,
			BufferedWriter writer
			) throws IOException
	{
		NonPeriodicEvent[] departures, arrivals;
		if (!departuresFromOrigin.containsKey(origin))
		{
			departures = Net.getEventsAtStation(origin, false);
			Arrays.sort(departures, new NonPeriodicEventComparator());
			departuresFromOrigin.put(origin, departures);
		}
		else departures = departuresFromOrigin.get(origin);
		if (!arrivalsAtDestination.containsKey(destination))
		{
			arrivals = Net.getEventsAtStation(destination, true);
			Arrays.sort(arrivals, new NonPeriodicEventComparator());
			arrivalsAtDestination.put(destination, arrivals);
		}
		else arrivals = arrivalsAtDestination.get(destination);
		for (NonPeriodicEvent departure : departures)
			if (!connectionsFromDeparture.containsKey(departure.getID()))
				connectionsFromDeparture.put(departure.getID(),
						cean.getConnectionsFromDeparture(departure));
		for (NonPeriodicEvent arrival : arrivals)
			if (!connectionsToArrival.containsKey(arrival.getID()))
				connectionsToArrival.put(arrival.getID(),
						cean.getConnectionsToArrival(arrival));
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
					&& (departures[d].getDispoTime() ==
						departures[d - sameTimeDepartures - 1].getDispoTime()))
				sameTimeDepartures++;
			// traversing arrivals in ascending order to find the earliest
			for (NonPeriodicEvent arrival : arrivals)
			{
				// no need to consider arrivals earlier than the departure
				if (arrival.getDispoTime() < departures[d].getDispoTime()) continue;
				// if the earliest previously found arrival time
				// (i.e., for this or a later departure) is already
				// exceeded, no need to look any further
				if (arrival.getDispoTime() > earliestArrivalTime) break;
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
								earliestArrivalTime = arrival.getDispoTime();
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
									earliestArrivalTime = arrival.getDispoTime();
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
			return;
		}
		
		
		// finish off the current OD pair by calculating weights
		// for individual paths and writing them out
		int weightAlreadyDistributed = 0;
		
		Iterator<Integer> original_passenger_times_for_this_od = original_passenger_times.get(origin).get(destination).iterator();
		
		if(!passenger_routing_arrival_on_time){
			//Calculate the latest departure in the non-delayed network by reading the expanded od matrix
			int latestDeparture=expanded_od.get(origin-1).get(destination-1).lastKey();
			//System.err.println("Latest Departure from "+origin+" to "+destination+": "+latestDeparture);
			double weightPerSecond = 1.0 * weight / (latestDeparture - beginOfDay);
			int pathGroupWeight = 0;
			for (LinkedList<LinkedList<NonPeriodicActivity>> shortestPaths : odSp){
			// "path group" = paths with same departure time
				if(latestDeparture==beginOfDay)
					pathGroupWeight=(int)weight;
				else if(shortestPaths.getFirst().getFirst().getSource().getDispoTime()<=latestDeparture){
					pathGroupWeight = (int) Math.round(
							weightPerSecond *(shortestPaths.getFirst().getFirst().getSource().getDispoTime() - beginOfDay)); // sort of the "Cumulative Distribution Function" (CDF)
				}
				else{
					pathGroupWeight = (int) Math.round(weightPerSecond *(latestDeparture-beginOfDay));
				}
				
				pathGroupWeight -= weightAlreadyDistributed; // "difference of CDF values"
				weightAlreadyDistributed += pathGroupWeight;
				
				// distribute equally within the current "path group"
				int div = pathGroupWeight / shortestPaths.size();
				int mod = pathGroupWeight % shortestPaths.size();
				int spC = 0;
				for (LinkedList<NonPeriodicActivity> sp : shortestPaths)
				{
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
				//Need to iterate two times, s.t. the acitivities are sorted
					for (NonPeriodicActivity a: sp){
						if(a.getType().equals("headway")){
							writer.write(a.getID() + ",");
							a.setWeight(a.getWeight() + partialWeight);
						}
					}
					int delayed_time = sp.getLast().getTarget().getDispoTime();
					weighted_delayed_time += partialWeight*sp.getLast().getTarget().getDispoTime();
					int original_time;
					//Calculate the delays of the passengers and put them in the delay table
					for(int i=1;i<=partialWeight;i++){
						if(!original_passenger_times_for_this_od.hasNext()){
							throw new RuntimeException("Trying to distribute more passenger in the delayed network than in the undelayed network!");
						}
						original_time = original_passenger_times_for_this_od.next();
						if(passenger_delay.get(delayed_time-original_time)==null){
							passenger_delay.put(delayed_time-original_time, 1);
						}
						else{
							passenger_delay.put(delayed_time-original_time, passenger_delay.get(delayed_time-original_time)+1);
						}
					}
					sp.getLast().getTarget().setWeight(sp.getLast().getTarget().getWeight()
							+ partialWeight);
					writer.newLine();
				}
			}
		}
		else{
			TreeMap<Integer,Integer> passenger_map = expanded_od.get(origin-1).get(destination-1);
			TreeMap<Integer,Integer> passenger_time_map = new TreeMap<Integer, Integer>();
			for (LinkedList<LinkedList<NonPeriodicActivity>> shortestPaths : odSp){
				int next_departure = shortestPaths.getFirst().getFirst().getSource().getDispoTime();
				int pathGroupWeight = 0;
				int tempGroupWeight = 0;
				//Determine how many passengers have come to the station since the last departure
				for(int time:passenger_map.keySet()){
					if(time<=next_departure){
						//Add all passengers that have come to the station since the morning
						pathGroupWeight += passenger_map.get(time);
					}
					else
						break;
				}
				for(int time:passenger_map.keySet()){
					int current_weight = passenger_map.get(time);
					if(tempGroupWeight+current_weight<weightAlreadyDistributed)
						tempGroupWeight+=current_weight;
					else if(tempGroupWeight<=weightAlreadyDistributed&&tempGroupWeight+current_weight>=pathGroupWeight){
						passenger_time_map.put(time, pathGroupWeight-weightAlreadyDistributed);
						break;
					}
					else if(tempGroupWeight<=weightAlreadyDistributed&&tempGroupWeight+current_weight<pathGroupWeight){
						tempGroupWeight+=current_weight;
						passenger_time_map.put(time,tempGroupWeight-weightAlreadyDistributed);
					}
					else if(tempGroupWeight>weightAlreadyDistributed&&tempGroupWeight+current_weight<pathGroupWeight){
						passenger_time_map.put(time, current_weight);
						tempGroupWeight+=current_weight;
					}
					else if(tempGroupWeight>weightAlreadyDistributed&&tempGroupWeight+current_weight>=pathGroupWeight){
						passenger_time_map.put(time,pathGroupWeight-tempGroupWeight);
						break;
					}
				}
				//Substract the passengers that have already been distributed
				pathGroupWeight -= weightAlreadyDistributed;
				weightAlreadyDistributed += pathGroupWeight;
				int div = pathGroupWeight / shortestPaths.size();
				int mod = pathGroupWeight % shortestPaths.size();
				int spC = 0;
				for (LinkedList<NonPeriodicActivity> sp : shortestPaths)
				{
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
				//Need to iterate two times, s.t. the acitivities are sorted
					for (NonPeriodicActivity a: sp){
						if(a.getType().equals("headway")){
							writer.write(a.getID() + ",");
							a.setWeight(a.getWeight() + partialWeight);
						}
					}
					int delayed_time = sp.getLast().getTarget().getDispoTime();
					weighted_delayed_time += partialWeight*sp.getLast().getTarget().getDispoTime();
					int original_time;
					//Calculate the delays of the passengers and put them in the delay table
					for(int i=1;i<=partialWeight;i++){
						if(!original_passenger_times_for_this_od.hasNext()){
							throw new RuntimeException("Trying to distribute more passenger in the delayed network than in the undelayed network!");
						}
						original_time = original_passenger_times_for_this_od.next();
						if(passenger_delay.get(delayed_time-original_time)==null){
							passenger_delay.put(delayed_time-original_time, 1);
						}
						else{
							passenger_delay.put(delayed_time-original_time, passenger_delay.get(delayed_time-original_time)+1);
						}
					}
					sp.getLast().getTarget().setWeight(sp.getLast().getTarget().getWeight()
							+ partialWeight);
					writer.newLine();
				}
			}
		}
		if (weightAlreadyDistributed != (int) weight){
			//System.out.println("WARNING: distribution wrong at " + origin + "-" +
			//		destination + ": " + weight + " vs. " + weightAlreadyDistributed);		
		}
	}

	
	private static LinkedList<LinkedList<NonPeriodicActivity>> depthFirstRecursionAddAllPaths
			(long[][] m, CollapsedEvent departure,
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
