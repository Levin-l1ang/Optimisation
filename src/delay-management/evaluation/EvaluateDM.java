import java.io.*;
import java.util.*;



public class EvaluateDM
{
	private EvaluateDM() {}  // class only contains static methods



	private static String dispoFile;
	private static String outputFile;
	private static String odFile;
	private static String statisticFile;
	private static String delayedPassengerPathsFileName;
	private static NonPeriodicEANetwork Net;
	private static boolean DEBUG;
	private static boolean VERBOSE;
	private static boolean extendedEval;
	private static boolean passenger_routing_arrival_on_time;
	private static long weighted_delay_time;
	static HashMap<NonPeriodicActivity, Integer> frequencies;
	private static int number_of_passengers;



	public static void main(String[] args) throws Exception
	{

		readConfig();
		readEAN();
		int delayedEvents = 0;
		int totalDelay = 0;
		long weightedTotalDelay = 0;
		for (NonPeriodicEvent e: Net.getEvents())
		{
			int delay = e.getDispoTime() - e.getTime();
			if (delay > 0)
			{
				delayedEvents++;
				totalDelay += delay;
				if (e.isArrivalEvent()) {
					weightedTotalDelay += delay * e.getWeight();
				}
			}
		}

		//Read the number of passengers from the OD-matrix to calculate the average of the objective value

		Scanner od_scanner = new Scanner(new BufferedReader(new FileReader(new File(odFile))));
		number_of_passengers=0;
		String od_line;
		String[] values;
		while(od_scanner.hasNext()){
			od_line = od_scanner.nextLine();
			od_line.trim();
			int position = od_line.indexOf('#');
			if (position != -1)
				od_line = od_line.substring(0, position);
			od_line = od_line.trim();
			if (od_line.isEmpty())
				continue;
			values = od_line.split(";");
			number_of_passengers+=Integer.parseInt(values[2].trim());
		}
		od_scanner.close();


		Statistic s = new Statistic(new File(statisticFile));
		int missedUsedConnections = 0;
		long missedConnectionDelay = 0;
		int period = Net.getPeriod();
		for (NonPeriodicChangingActivity a: Net.getChangingActivities())
		{
			if (a.getZ() == 1)
			{
				double weight = a.getWeight();
				if (weight > 0)
					missedUsedConnections++;
				missedConnectionDelay += (weight*(period));
			}
		}

		int swappedHeadways = 0;
		for (NonPeriodicHeadwayActivity a: Net.getHeadwayActivities())
			if (a.getG() == 0 && a.getTarget().getTime() - a.getSource().getTime() < a.getLowerBound())
				swappedHeadways++;


		StringBuilder line = new StringBuilder(4096);
		line.append("\n\n\n************************************************************\n\n");
		line.append("Feasibility of the disposition timetable: ");
		boolean feasible = checkFeasiblity();
		line.append(feasible);
		s.setBooleanValue("dm_feasible", feasible);
		line.append("\nMissed used connections: ");
		line.append(missedUsedConnections);
		s.setIntegerValue("dm_obj_changes_missed_od", missedUsedConnections);
		line.append("\nAverage delay per event: ");
		double dm_obj_delay_events_average = ((double)totalDelay)/Net.getEvents().size();
		line.append(dm_obj_delay_events_average);
		s.setDoubleValue("dm_obj_delay_events_average", dm_obj_delay_events_average);
		line.append("\nObjective value DM2: ");
		long dm_obj_dm2=weightedTotalDelay+missedConnectionDelay;
		line.append(dm_obj_dm2);
		s.setLongValue("dm_obj_dm2", dm_obj_dm2);
		line.append(", average: ");
		double dm_obj_dm2_average = ((double)dm_obj_dm2)/number_of_passengers;
		line.append(dm_obj_dm2_average);
		s.setDoubleValue("dm_obj_dm2_average", dm_obj_dm2_average);
		line.append("\nNumber of delayed events: ");
		line.append(delayedEvents);
		s.setIntegerValue("dm_prop_events_delayed",	delayedEvents);
		line.append("\nNumber of swapped headways: ");
		line.append(swappedHeadways);
		s.setIntegerValue("dm_prop_headways_swapped", swappedHeadways);
		line.append("\nAverage travel time of the passengers: ");
		double dm_time_average=calcAverageTravelTime();
		line.append(calcAverageTravelTime());
		s.setDoubleValue("dm_time_average", dm_time_average);
		line.append("\nDuration of evaluation without reading:");
		if(extendedEval){
			line.append("\n\nExtended evaluation:\n\n");
			long passenger_delay_after_rerouting = evaluatePassengerPaths();
			s.setLongValue("dm_passenger_delay", passenger_delay_after_rerouting);
			s.setDoubleValue("dm_passenger_delay_average", passenger_delay_after_rerouting/(double)number_of_passengers);
			line.append("passenger delay after rerouting: ");
			line.append(passenger_delay_after_rerouting);
			line.append("(");
			line.append(passenger_delay_after_rerouting/(double)number_of_passengers);
			line.append(" per passenger)\n");
			long real_passenger_delay = calculateRealDelay();
			s.setLongValue("dm_obj_dm1", real_passenger_delay);
			line.append("Objective value DM1: ");
			line.append(real_passenger_delay);
			line.append("(");
			line.append(real_passenger_delay/(double)number_of_passengers);
			line.append(" per passenger)\n");
			s.setDoubleValue("dm_obj_dm1_average", real_passenger_delay/(double)number_of_passengers);
			line.append("\n\n");
		}
		line.append("\n************************************************************\n\n\n\n");
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
		out.print(line.toString());
		out.close();
		s.writeStatistic(new File(statisticFile));
		if (VERBOSE)
			System.out.print(line);
	}



	private static void readConfig() throws IOException
	{
		Config config = new Config(new File("basis/Config.cnf"));
		dispoFile = config.getStringValue("default_disposition_timetable_file");
		outputFile = config.getStringValue("default_evaluation_dm_file");
		statisticFile = config.getStringValue("default_statistic_file");
		odFile = config.getStringValue("default_od_file");
		delayedPassengerPathsFileName = config.getStringValue("default_delayed_passenger_paths_file");
		DEBUG = config.getBooleanValue("DM_debug");
		VERBOSE = config.getBooleanValue("DM_verbose");
		extendedEval = config.getBooleanValue("DM_eval_extended");
		passenger_routing_arrival_on_time = config.getBooleanValue("DM_passenger_routing_arrival_on_time");

		if (DEBUG || VERBOSE)
		{
			System.out.println("EvaluateDM: using the following configuration:");
			System.out.println("  disposition timetable input file: " + dispoFile);
			System.out.println("  evaluation output file: " + outputFile);
		}
	}



	private static void readEAN() throws Exception
	{
		Net = IO.readNonPeriodicEANetwork(true, true);
		Net.setZ(); // sets the attributes z of changing activities: If z=0, the activity can be used according to the dispo, if z=1, the activity can not be used with the dispo
		Net.setG(); // sets attributes g of headway activities: g=1, the headway is fulfilled, g=0, the headway is not fulfilled
	}

	private static long evaluatePassengerPaths() throws Exception{
		Config config = new Config(new File("basis/Config.cnf"));
		String pathsFileName = config.getStringValue("default_passenger_paths_file");
		String line;
		String[] values;
		Long weighted_time=0L;
		TreeMap<Integer, NonPeriodicEvent> events_by_id = new TreeMap<Integer,NonPeriodicEvent>();
		System.err.print("Reorder EAN...");
		long before = System.currentTimeMillis();
		for(NonPeriodicEvent e : Net.getEvents()){
			events_by_id.put(e.getID(), e);
		}
		long after = System.currentTimeMillis();
		System.err.println("Done! Took "+(after-before)+" ms.");
		//Calculate the weighted time needed by the passengers to drive in the non delayed timetable
		System.err.print("Reading PassengerPath...");
		BufferedReader reader = new BufferedReader(new FileReader(new File(pathsFileName)));
		int weight;
		while ((line = reader.readLine()) != null){
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;
			values = line.split(";");
			weight=Integer.parseInt(values[0].trim());
			weighted_time += events_by_id.get(Integer.parseInt(values[2].trim())).getTime()*weight;
		}
		reader.close();
		System.err.println("Done!");
		//Calculate the sum of the delayed arrival times and subtract the sum of the non-delayed arrival times to get the sum of the delay
		weighted_delay_time = DelayedPassengerPaths.calculateWeightedDelayTime(passenger_routing_arrival_on_time,Net,new File(delayedPassengerPathsFileName),events_by_id);
		System.err.println("Calculated delay time: "+weighted_delay_time);
		System.err.println("Calculated non-delayed time: "+weighted_time);
		return weighted_delay_time-weighted_time;
	}

	private static long calculateRealDelay() throws IOException{
		Config config = new Config(new File("basis/Config.cnf"));
		String pathsFileName = config.getStringValue("default_passenger_paths_file");
		String line;
		String[] values;
		String[] changes;
		int position;
		boolean connection_missed;
		long realDelay = 0L;
		int weight;
		TreeMap<Integer, NonPeriodicEvent> events_by_id = new TreeMap<Integer,NonPeriodicEvent>();
		TreeMap<Integer, NonPeriodicChangingActivity> changing_activities_by_id = new TreeMap<Integer, NonPeriodicChangingActivity>();
		System.err.print("Reorder EAN...");
		for(NonPeriodicEvent e : Net.getEvents()){
			events_by_id.put(e.getID(), e);
		}
		for(NonPeriodicChangingActivity a : Net.getChangingActivities()){
			changing_activities_by_id.put(a.getID(), a);
		}
		System.err.println("Done!");
		System.err.print("Calculate the real delay of the passengers given the nondelayed paths...");
		BufferedReader reader = new BufferedReader(new FileReader(pathsFileName));
		while((line=reader.readLine())!=null){
			position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;
			values = line.split(";");
			weight = Integer.parseInt(values[0].trim());
			if(weight>0){
				if(values.length <= 5){//There are no changes in the path
					NonPeriodicEvent last_event_on_path = events_by_id.get(Integer.parseInt(values[2].trim()));
					realDelay += (last_event_on_path.getDispoTime()-last_event_on_path.getTime())*weight;
				}
				else{//Check if any connection is missed for the path. The delay is then the period length
					changes = values[5].split(",");
					connection_missed=false;
					for(int i=0;i<changes.length;i++){
						NonPeriodicChangingActivity act = changing_activities_by_id.get(Integer.parseInt(changes[i].trim()));
						if(act.getZ()==1){//The connection is missed. The delay is considered to be the period length.
							realDelay += Net.getPeriod()*weight;//frequencies.get(act);
							connection_missed = true;
							break;
						}
					}
					if(!connection_missed){//There were no missed connections
						NonPeriodicEvent last_event_on_path = events_by_id.get(Integer.parseInt(values[2].trim()));
						realDelay += (last_event_on_path.getDispoTime()-last_event_on_path.getTime())*weight;
					}
				}
			}
		}
		reader.close();
		return realDelay;
	}

	private static boolean checkFeasiblity(){
		int duration;
		NonPeriodicChangingActivity act_change;
		NonPeriodicHeadwayActivity act_headway;
		for(NonPeriodicActivity act:Net.getActivities()){
			duration=act.getTarget().getDispoTime()-act.getSource().getDispoTime();
			//Changing activities should be treated different! If z==1, i.e. the change is not fulfilled,
			//the lower bound is irrelevant!
			if(act instanceof NonPeriodicChangingActivity){
				act_change = (NonPeriodicChangingActivity) act;
				if(act_change.getZ()==0&&duration<act_change.getLowerBound()){
					return false;
				}
			}
			//Same thing for headways, just with g
			else if(act instanceof NonPeriodicHeadwayActivity){
				act_headway = (NonPeriodicHeadwayActivity) act;
				if(act_headway.getG()==0&&duration<act_headway.getLowerBound()){
					return false;
				}
			}
			else if(duration<act.getLowerBound()){
				System.out.println("Disposition timetable is infeasible for:");
				System.out.println(act);
				return false;
			}
		}
		return true;
	}

	private static double calcAverageTravelTime(){
		double weighted_travel_time=0;
		for(NonPeriodicActivity act: Net.getActivities()){
			weighted_travel_time +=(act.getTarget().getDispoTime()-act.getSource().getDispoTime())*act.getWeight();
		}
		return weighted_travel_time/number_of_passengers;
	}

}
