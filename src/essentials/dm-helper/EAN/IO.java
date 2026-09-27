
import java.io.*;
import java.util.*;



public class IO
{
    private IO() {}  // class only contains static methods



	// settings read from the config file
	private static String linePlanFile;
	private static String pEventsInputFile;
	private static String eventFileName;
	private static String activityFileName;
	private static String eventDelaysFileName;
	private static String activityDelaysFileName;
	private static String dispoHeader;
	private static String dispoFile;
	private static String endEventsofTripsFileName;
	private static String odExpandedFileName;
	private static String passengerPathsFileName;
	private static String passengerDelayTableFileName;
	private static int period;
	private static int timeUnitsPerMinute;
	private static boolean CHECK_CONSISTENCY;
	private static boolean DEBUG;
	private static boolean VERBOSE;
	private static double lowerBoundReductionFactor;


	/**
	 * Parses an event-activity network given in the LinTim format.
	 * Assumes that
	 * <ul>
	 * <li>event IDs are continous (and starting with 1),
	 * <li>activity IDs are continous (and starting with 1),
	 * <li>each pair of two headway activities directly follows each other in {@code activityFileName}.
	 * </ul>
	 * If DM_verbose is set, verbose output to {@link System#out} is enabled.
	 * @return an instance of {@link EANetwork}, representing the event-activity network defined in {@code eventFileName} and {@code activityFileName}
	 * @throws IOException if an I/O error occurs (input file does not exist etc.)
	 */

	public static NonPeriodicEANetwork readNonPeriodicEANetwork(boolean readSourceDelays,
	                                                            boolean readDispositionTimetable) throws Exception
	{
		readConfig();

		// read the event file
		if (VERBOSE)
			System.out.println("IO: reading events from file " + new File(eventFileName).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(eventFileName))
			throw new IllegalArgumentException ("IO: invalid numbering of IDs or empty input file: " + eventFileName);

		int count = Tools.countRelevantLines(eventFileName);
		ArrayList<NonPeriodicEvent> E = new ArrayList<NonPeriodicEvent>(count);

		BufferedReader reader = new BufferedReader(new FileReader(eventFileName));
		String line;
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			int periodicID = Integer.parseInt(tokens[1].trim());
			boolean arrivalEvent = tokens[2].trim().equals("\"arrival\"");
			int time = Integer.parseInt(tokens[3].trim());
			double w = Double.parseDouble(tokens[4].trim()); // number of alighting passengers, directly reaching their destination without further changing
			NonPeriodicEvent event =
					new NonPeriodicEvent(ID, time, w, arrivalEvent, periodicID);
			try
			{
				event.setStation(Integer.parseInt(tokens[5].trim()));
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
			}
			E.add(event);
			if (DEBUG)
				System.out.println("  event " + E.get(E.size()-1));
		}
		reader.close();
		// count the number of activities in activityFileName
		if (VERBOSE)
			System.out.println("IO: reading activities from file " + new File(activityFileName).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(activityFileName))
			throw new IllegalArgumentException ("IO: invalid numbering of IDs or empty input file: " + activityFileName);
		int driveCount = 0;
		int waitCount = 0;
		int circCount = 0;
		int changeCount = 0;
		int headwayCount = 0;
		reader = new BufferedReader(new FileReader(activityFileName));
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String type = line.split(";")[2].trim().toLowerCase();
			// remove " chars at beginning and end of the string
			type = type.substring(1, type.length()-1);
			if (type.equals("drive"))
				driveCount++;
			else if (type.equals("wait"))
				waitCount++;
			else if (type.equals("fixed-circulation"))
				circCount++;
			else if (type.equals("change"))
				changeCount++;
			else if (type.equals("headway"))
				headwayCount++;
			else if (type.equals("turn") || type.equals("circulation") || type.equals("turnaround"))
				waitCount++;
			else{
				reader.close();
				throw new IllegalArgumentException("IO: unsupported activity type: " + type);
			}
		}
		reader.close();
		int totalCount = driveCount + waitCount + circCount + changeCount + headwayCount;

		// read the activity file
		ArrayList<NonPeriodicActivity> A = new ArrayList<NonPeriodicActivity>(totalCount);
		ArrayList<NonPeriodicActivity> A_drive = new ArrayList<NonPeriodicActivity>(driveCount);
        ArrayList<NonPeriodicActivity> A_wait = new ArrayList<NonPeriodicActivity>(waitCount);
        ArrayList<NonPeriodicActivity> A_circ = new ArrayList<NonPeriodicActivity>(circCount);
        ArrayList<NonPeriodicActivity> A_nice = new ArrayList<NonPeriodicActivity>();
        ArrayList<NonPeriodicChangingActivity> A_change = new ArrayList<NonPeriodicChangingActivity>(changeCount);
        ArrayList<NonPeriodicHeadwayActivity> A_head = new ArrayList<NonPeriodicHeadwayActivity>(headwayCount);

        reader = new BufferedReader(new FileReader(activityFileName));
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			int periodicID = Integer.parseInt(tokens[1].trim());
			String type = tokens[2].trim().toLowerCase();
			// remove " chars at beginning and end of the string
			type = type.substring(1, type.length()-1);
			int sID = Integer.parseInt(tokens[3].trim());
			int tID = Integer.parseInt(tokens[4].trim());
			int lowerBound = Integer.parseInt(tokens[5].trim());
			int upperBound = Integer.parseInt(tokens[6].trim());
			double w = Double.parseDouble(tokens[7].trim());

			NonPeriodicEvent source = E.get(sID-1);
			NonPeriodicEvent target = E.get(tID-1);

			if (type.equals("drive"))
			{
				lowerBound = (int) Math.round(lowerBoundReductionFactor * lowerBound);
				NonPeriodicActivity a = new NonPeriodicActivity(ID, source, target, lowerBound, upperBound, w, type, periodicID);
				A.add(a);
				A_drive.add(a);
				A_nice.add(a);
				source.addOutgoingActivity(a);
				target.addIncomingActivity(a);
				if (DEBUG)
					System.out.println("  driving activity " + a);
			}
			else if (type.equals("wait"))
			{
				lowerBound = (int) Math.round(lowerBoundReductionFactor * lowerBound);
				NonPeriodicActivity a = new NonPeriodicActivity(ID, source, target, lowerBound, upperBound, w, type, periodicID);
				A.add(a);
				A_wait.add(a);
				A_nice.add(a);
				source.addOutgoingActivity(a);
				target.addIncomingActivity(a);
				if (DEBUG)
					System.out.println("  waiting activity " + a);
			}
			else if (type.equals("fixed-circulation"))
			{
				lowerBound = (int) Math.round(lowerBoundReductionFactor * lowerBound);
				NonPeriodicActivity a = new NonPeriodicActivity(ID, source, target, lowerBound, upperBound, w, type, periodicID);
				A.add(a);
				A_circ.add(a);
				A_nice.add(a);
				source.addOutgoingActivity(a);
				target.addIncomingActivity(a);
				if (DEBUG)
					System.out.println("  (fixed) circulation activity " + a);
			}
			else if (type.equals("circulation"))
			{
				if (DEBUG)
					System.out.println("  ignoring (non-fixed) circulation activity");
			}
			else if (type.equals("change"))
			{
				NonPeriodicChangingActivity a = new NonPeriodicChangingActivity(ID, source, target, lowerBound, upperBound, w, type, periodicID);
				A.add(a);
				A_change.add(a);
				source.addOutgoingActivity(a);
				target.addIncomingActivity(a);
				if (DEBUG)
					System.out.println("  changing activity " + a);
			}
			else if (type.equals("headway"))
			{
				NonPeriodicHeadwayActivity a = new NonPeriodicHeadwayActivity(ID, source, target, lowerBound, upperBound, w, type, periodicID);
				A.add(a);
				A_head.add(a);
				source.addOutgoingActivity(a);
				target.addIncomingActivity(a);
				if (DEBUG)
					System.out.println("  headway activity " + a);
			}
			else if (type.equals("turn"))
			{
				NonPeriodicActivity a = new NonPeriodicActivity(ID, source, target, lowerBound, upperBound, w, type, periodicID);
				A.add(a);
				A_wait.add(a);
				source.addOutgoingActivity(a);
				target.addIncomingActivity(a);
				if (DEBUG)
					System.out.println("  turn activity " + a);
			}
		}
		reader.close();

		// Match corresponding headways. Note that we assume each pair of
		// headway activities to be contained in the input file
		// ON CONSECUTIVE LINES.
		for(NonPeriodicHeadwayActivity a1:A_head){
			if(a1.getCorrespodingHeadway() == null && A_head.indexOf(a1)<A_head.size() && A_head.get(A_head.indexOf(a1)+1).getSource()==a1.getTarget() && A_head.get(A_head.indexOf(a1)+1).getTarget() == a1.getSource()){
				a1.setCorrespodingHeadway(A_head.get(A_head.indexOf(a1)+1));
				A_head.get(A_head.indexOf(a1)+1).setCorrespodingHeadway(a1);
			} else if (a1.getCorrespodingHeadway() == null) {
				for(NonPeriodicActivity a2:a1.getTarget().getOutgoingActivities()){
					if(a2.getType().equals("headway")  && a1.getSource() == a2.getTarget() && a1.getTarget() == a2.getSource()){
						a1.setCorrespodingHeadway((NonPeriodicHeadwayActivity)a2);
						((NonPeriodicHeadwayActivity)a2).setCorrespodingHeadway(a1);
						break;
					}
				}
			}
		}

		NonPeriodicEANetwork Net = new NonPeriodicEANetwork(E, A, A_drive, A_wait, A_circ, A_nice, A_change, A_head, period, CHECK_CONSISTENCY);

		// find out which events are end events of some trip
		if (VERBOSE)
			System.out.println("IO: reading end events of trips from file " + new File(endEventsofTripsFileName).getAbsolutePath());

		reader = new BufferedReader(new FileReader(endEventsofTripsFileName));
		while ((line = reader.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			int ID = Integer.parseInt(tokens[0].trim());
			E.get(ID-1).setEndOfTrip(true);
			if (DEBUG)
				System.out.println("  event " + ID + " is the end event of some trip");
		}
		reader.close();
		if (readSourceDelays)
			readSourceDelays(E, A);

		if (readDispositionTimetable)
			readDispositionTimetable(E);


		// in general, checking for directed circles in the EAN should
		// not be necessary (as directed circles _should_ result in an
		// infeasible timetable), so we only do this if the DEBUG flag
		// is enabled
		if (DEBUG && Net.containsTimetableCircle())
			throw new Exception("IO: EAN contains a directed circle");

		return Net;
	}



	public static HashMap<NonPeriodicActivity, Integer> readActivityFrequencies(LinkedHashSet<NonPeriodicActivity> activities) throws IOException
	{
		// read frequencies from linePlanFile
		if (DEBUG)
			System.out.println("IO: reading line plan from file " + new File(linePlanFile).getAbsolutePath());

		int count = Tools.countRelevantLines(linePlanFile);
		int[] lineFrequencies = new int[count];

		String line;
		BufferedReader in = new BufferedReader(new FileReader(linePlanFile));
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

			lineFrequencies[ID-1] = f; // assumes that IDs are continuous, starting with 1
		}
		in.close();



		// read associated lines from pEventsInputFile
		if (DEBUG)
			System.out.println("IO: reading periodic events from file " + new File(pEventsInputFile).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(pEventsInputFile))
			throw new IOException("IO: invalid numbering of IDs or empty input file: " + pEventsInputFile);

		count = Tools.countRelevantLines(pEventsInputFile);
		int[] linesOfPeriodicEvents = new int[count];

		in = new BufferedReader(new FileReader(pEventsInputFile));
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
			int lineID = Integer.parseInt(tokens[3].trim());
			linesOfPeriodicEvents[ID-1] = lineID; // assumes that IDs are continuous, starting with 1
		}
		in.close();



		HashMap<NonPeriodicActivity, Integer> frequencies =
			new HashMap<NonPeriodicActivity, Integer>(activities.size());

		// assumes that IDs are continuous, starting with 1
		for (NonPeriodicActivity a: activities){
			frequencies.put(a, lineFrequencies[linesOfPeriodicEvents[a.getTarget().getPeriodicParentEventID()-1]-1]);
			//if(frequencies.get(a)==0)
			//	System.out.println("Activity: " + a.getID() + "Frequ: " + lineFrequencies[linesOfPeriodicEvents[a.getTarget().getPeriodicParentEventID()-1]-1]);

		}
		return frequencies;
	}



	private static void readDispositionTimetable(ArrayList<NonPeriodicEvent> events) throws IOException
	{
		if (VERBOSE)
			System.out.println("IO: reading disposition timetable from file " + new File(dispoFile).getAbsolutePath());
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(dispoFile))
			throw new IllegalArgumentException ("IO: invalid numbering of IDs or empty input file: " + dispoFile);

		BufferedReader reader = new BufferedReader(new FileReader(dispoFile));
		String line;
		while ((line = reader.readLine()) != null)
	    {
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

    		String[] tokens = line.split(";");
    		int ID = Integer.parseInt(tokens[0].trim());
    		int time = Integer.parseInt(tokens[1].trim());
    		NonPeriodicEvent e = events.get(ID-1);
    		if (e.getID() != ID){
    			reader.close();
    			throw new IOException("IO: ID does not match");
    		}
    		e.setDispoTime(time);
	    }
		reader.close();
	}



	private static void readSourceDelays(ArrayList<NonPeriodicEvent> E,
	                                     ArrayList<NonPeriodicActivity> A) throws IOException
	{
		// read event-based source delays (if they exist)
		File file = new File(eventDelaysFileName);
		if (file.exists() && file.isFile())
		{
			if (VERBOSE)
				System.out.println("IO: reading event source delays from file " + new File(eventDelaysFileName).getAbsolutePath());

			BufferedReader reader = new BufferedReader(new FileReader(eventDelaysFileName));
			String line;
			while ((line = reader.readLine()) != null)
			{
				int position = line.indexOf('#');
				if (position != -1)
					line = line.substring(0, position);
				line = line.trim();
				if (line.isEmpty())
					continue;

				String[] tokens = line.split(";");
				int ID = Integer.parseInt(tokens[0].trim());
				int sourceDelay = Integer.parseInt(tokens[1].trim());
				E.get(ID-1).setSourceDelay(sourceDelay);
				if (DEBUG)
					System.out.println("  delaying event " + ID + " by " + sourceDelay);
			}
			reader.close();
		}

		// read activity-based source delays (if they exist)
		file = new File(activityDelaysFileName);
		if (file.exists() && file.isFile())
		{
			if (VERBOSE)
				System.out.println("IO: reading activity source delays from file " + new File(activityDelaysFileName).getAbsolutePath());

			BufferedReader reader = new BufferedReader(new FileReader(activityDelaysFileName));
			String line;
			while ((line = reader.readLine()) != null)
			{
				int position = line.indexOf('#');
				if (position != -1)
					line = line.substring(0, position);
				line = line.trim();
				if (line.isEmpty())
					continue;

				String[] tokens = line.split(";");
				int ID = Integer.parseInt(tokens[0].trim());
				int sourceDelay = Integer.parseInt(tokens[1].trim());
				A.get(ID-1).setSourceDelay(sourceDelay);
				if (DEBUG)
					System.out.println("  delaying activity " + ID + " by " + sourceDelay);
			}
			reader.close();
		}
	}


	public static void outputDispoTimetable(NonPeriodicEANetwork Net, String filename, String header) throws IOException
	{
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
		out.println("# " + header);
		for (NonPeriodicEvent e: Net.getEvents())
			out.println(e.getID() + "; " + e.getDispoTime());
		out.close();
	}



	private static void readConfig() throws Exception
	{
		Config config = new Config(new File("basis/Config.cnf"));

		linePlanFile = config.getStringValue("default_lines_file");
		pEventsInputFile = config.getStringValue("default_events_periodic_file");
		eventFileName = config.getStringValue("default_events_expanded_file");
		activityFileName = config.getStringValue("default_activities_expanded_file");
		eventDelaysFileName = config.getStringValue("default_event_delays_file");
		activityDelaysFileName = config.getStringValue("default_activity_delays_file");
		endEventsofTripsFileName = config.getStringValue("default_expanded_end_events_of_trips_file");
		odExpandedFileName = config.getStringValue("default_od_expanded_file");
		dispoHeader = config.getStringValue("timetable_header_disposition");
		dispoFile = config.getStringValue("default_disposition_timetable_file");
		passengerPathsFileName = config.getStringValue("default_passenger_paths_file");
		passengerDelayTableFileName = config.getStringValue("default_passenger_delay_table_file");
		timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
		// attention: timetabling works with minutes, we use seconds!
		period = 60 * config.getIntegerValue("period_length") / timeUnitsPerMinute;
		CHECK_CONSISTENCY = config.getBooleanValue("DM_enable_consistency_checks");
		DEBUG = config.getBooleanValue("DM_debug");
		VERBOSE = config.getBooleanValue("DM_verbose");
		lowerBoundReductionFactor = config.getDoubleValue("DM_lower_bound_reduction_factor");

		if (period <= 0)
			throw new Exception("IO: period_length has to be strictly positive");
		if (lowerBoundReductionFactor <= 0)
			throw new Exception("IO: DM_lower_bound_reduction_factor has to be strictly positive");
		if (lowerBoundReductionFactor > 1)
			throw new Exception("IO: DM_lower_bound_reduction_factor has to less than or equal to 1");
		if (timeUnitsPerMinute <= 0)
			throw new Exception("IO: time_units_per_minute has to be strictly positive");

		if (DEBUG)
		{
			CHECK_CONSISTENCY = true;
			VERBOSE = true;
		}

		if (VERBOSE)
		{
			System.out.println("IO: using the following configuration:");
			System.out.println("  non-periodic events input file: " + new File(eventFileName).getAbsolutePath());
			System.out.println("  non-periodic activities input file: " + new File(activityFileName).getAbsolutePath());
			System.out.println("  source delays on events input file: " + new File(eventDelaysFileName).getAbsolutePath());
			System.out.println("  source delays on activities input file: " + new File(activityDelaysFileName).getAbsolutePath());
			System.out.println("  disposition timetable input file: " + new File(dispoFile).getAbsolutePath());
			System.out.println("  end events of trips intput file: " + new File(endEventsofTripsFileName).getAbsolutePath());
			System.out.println("  period length: " + period + " seconds");
			System.out.println("  lower bound reduction factor: " + lowerBoundReductionFactor);
		}
	}

	/**
	 * Reads the expanded od matrix created by rollout extended and returns the matrix stored in a nested ArrayList.
	 * @return the expanded od matrix
	 * @throws Exception if something in the in- or output fails
	 */
	public static ArrayList<ArrayList<TreeMap<Integer,Integer>>> readExpandedOD() throws Exception{
		System.err.print("Reading expanded OD matrix... ");
		readConfig();
		//Read the od file to get the size of the od matrix
		BufferedReader reader = new BufferedReader(new FileReader(odExpandedFileName));
		int max_id=0;
		String[] values;
		String line;
		int position;
		while((line=reader.readLine())!=null){
			position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;
			values = line.split(";");
			if(max_id<Integer.parseInt(values[0].trim()))
				max_id=Integer.parseInt(values[0].trim());
			else if(max_id<Integer.parseInt(values[1].trim()))
				max_id=Integer.parseInt(values[1].trim());
		}
		reader.close();
		//Initialise the expanded od matrix. The entries are treemaps of two integers where the key is
		//the departure time and the values is the weight. These maps are nested in two arraylists
		System.err.print("Creating empty expanded od matrix... ");
		ArrayList<ArrayList<TreeMap<Integer,Integer>>> od_expanded = new ArrayList<ArrayList<TreeMap<Integer,Integer>>>(max_id);
		for(int i=0;i<max_id;i++){
			od_expanded.add(new ArrayList<TreeMap<Integer,Integer>>(max_id));
			for(int j=0;j<max_id;j++){
				od_expanded.get(i).add(new TreeMap<Integer,Integer>());
			}
		}
		System.err.println("Done!");
		//Read the od file and write the expanded od file
		reader = new BufferedReader(new FileReader(odExpandedFileName));
		int start_id, end_id, time, weight;
		while((line=reader.readLine())!=null){
			position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;
			values = line.split(";");
			start_id = Integer.parseInt(values[0].trim());
			end_id = Integer.parseInt(values[1].trim());
			time = Integer.parseInt(values[2].trim());
			weight = Integer.parseInt(values[3].trim());
			od_expanded.get(start_id-1).get(end_id-1).put(time,weight);
		}
		reader.close();
		System.err.println("Done!");
		return od_expanded;
	}

	/**
	 * Reads the current passenger paths created by rollout extended and creates a table of the arrival times of the passengers, where the arrival times of passengers
	 * for the same od pair are stored in a linked list.
	 * @param events_by_id a map of the event ids to the events
	 * @return a hashtable where the first value is the departure of the od pair, the second value the arrival and the third a list of arrival times. If more than one passenger
	 * of one od pair arrives at the same time, the time is stored multiple times.
	 * @throws Exception if something in the in- or output fails
	 */
	public static Hashtable<Integer,Hashtable<Integer,LinkedList<Integer>>> readOriginalPassengerTimes (TreeMap<Integer,NonPeriodicEvent> events_by_id) throws Exception{
		System.err.println("Reading the original passenger times...");
		readConfig();
		//Read the od file to get the size of the od matrix
		BufferedReader reader = new BufferedReader(new FileReader(passengerPathsFileName));
		int max_id=0;
		String line;
		int position;
		String[] values;
		while((line=reader.readLine())!=null){
			position = line.indexOf('#');
			if (position != -1){
				line = line.substring(0, position);
			}
			line = line.trim();
			if (line.isEmpty()){
				continue;
			}
			values = line.split(";");
			if(max_id<Integer.parseInt(values[3].trim()))
				max_id=Integer.parseInt(values[3].trim());
			else if(max_id<Integer.parseInt(values[4].trim()))
				max_id=Integer.parseInt(values[4].trim());
		}
		reader.close();
		Hashtable<Integer,Hashtable<Integer,LinkedList<Integer>>> original_passenger_times = new Hashtable<Integer,Hashtable<Integer,LinkedList<Integer>>>(max_id);
		reader = new BufferedReader(new FileReader(passengerPathsFileName));
		while((line=reader.readLine())!=null){
			position = line.indexOf('#');
			if (position != -1){
				line = line.substring(0, position);
			}
			line = line.trim();
			if (line.isEmpty()){
				continue;
			}
			values = line.split(";");
			//If the departure or the corresponding arrival of the od pair is not existing in the table, create a new entry.
			if(original_passenger_times.get(Integer.parseInt(values[3].trim()))==null){
				original_passenger_times.put(Integer.parseInt(values[3].trim()), new Hashtable<Integer,LinkedList<Integer>>(max_id));
			}
			if(original_passenger_times.get(Integer.parseInt(values[3].trim())).get(Integer.parseInt(values[4].trim()))==null){
				original_passenger_times.get(Integer.parseInt(values[3].trim())).put(Integer.parseInt(values[4].trim()),new LinkedList<Integer>());
			}
			//If more than one passenger has the same arrival time in this od pair, store it multiple times. This can be perceived by the weight.
			for(int i=Integer.parseInt(values[0].trim());i>0;i--){
				original_passenger_times.get(Integer.parseInt(values[3].trim())).get(Integer.parseInt(values[4].trim())).add(events_by_id.get(Integer.parseInt(values[2].trim())).getTime());
			}
		}
		reader.close();
		return original_passenger_times;
	}

	/**
	 * Writer a table of the passenger delays to the file specified in the config file.
	 * @param passenger_delay The table with the delays of the passengers. The key is the delay, the value the number of passengers with this delay
	 * @throws Exception if something in the in- or output fails
	 */
	public static void outputDelayTable(Hashtable<Integer,Integer> passenger_delay) throws Exception{
		System.err.println("Writing the delay table...");
		readConfig();
		BufferedWriter writer = new BufferedWriter(new FileWriter(passengerDelayTableFileName));
		writer.write("#The delays of the passengers");
		writer.newLine();
		writer.write("#delay;number of passengers with this delay");
		writer.newLine();
		List <Integer> delays = Collections.list(passenger_delay.keys());
		Collections.sort(delays);
		Iterator<Integer> it_delays = delays.iterator();
		int next_delay;
		while(it_delays.hasNext()){
			next_delay=it_delays.next();
			writer.write(next_delay+";"+passenger_delay.get(next_delay));
			writer.newLine();
		}
		writer.close();
	}

	public static LinkedList<Path> readPassengerPaths(NonPeriodicEANetwork Net) throws Exception{
		//Reorder the activities, so we can save the activities themselves in a list by knowing their ID.
		System.err.println("Reorder Activities...");
		HashMap<Integer,NonPeriodicChangingActivity> activities = new HashMap<Integer,NonPeriodicChangingActivity>();
		for(NonPeriodicChangingActivity act:Net.getChangingActivities()){
			activities.put(act.getID(), act);
		}
		System.err.println("Done!");
		System.err.println("Reorder Events...");
		HashMap<Integer,NonPeriodicEvent> events = new HashMap<Integer,NonPeriodicEvent>();
		for(NonPeriodicEvent e:Net.getEvents()){
			events.put(e.getID(), e);
		}
		System.err.println("Reading passenger paths...");
		readConfig();
		LinkedList<Path> path_list= new LinkedList<Path>();
		Scanner scan = new Scanner(new File(passengerPathsFileName));
		int position;
		String line;
		String[] values;
		String[] change_ids;
		int weight, source_id, target_id;
		Path path;
		while(scan.hasNext()){
			line=scan.nextLine();
			position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty()){
				continue;
			}
			values = line.split(";");
			weight = Integer.parseInt(values[0].trim());
			if(weight==0){
				continue;
			}
			source_id = Integer.parseInt(values[1].trim());
			target_id = Integer.parseInt(values[2].trim());
			path = new Path(weight, events.get(source_id), events.get(target_id));
			path_list.add(path);
			if(values.length<=5){
				continue;
			}
			if(values[5].trim().isEmpty()){
				continue;
			}
			change_ids = values[5].trim().split(",");
			for(String change:change_ids){
				path.addChange(activities.get(Integer.parseInt(change.trim())));
			}
		}
		return path_list;
	}
}
