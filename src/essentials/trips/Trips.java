import java.io.*;



public class Trips
{
	private Trips() {}  // class only contains static methods



	// settings from config file
	private static String eventInputFile;
	private static String tripOutputFile;
	private static boolean CHECK_CONSISTENCY;
	private static boolean DEBUG;
	private static boolean VERBOSE;

	private static String[] stations;
	private static int[] lines;



	public static void main(String[] args) throws Exception
	{
		readConfig();
		NonPeriodicEANetwork Net = IO.readNonPeriodicEANetwork(false, false);

		if (VERBOSE)
			System.out.println("Trips: reading periodic events from file " + new File(eventInputFile).getAbsolutePath());
		readPeriodicEventFile();

		if (VERBOSE)
			System.out.println("Trips: writing trips to file " + new File(tripOutputFile).getAbsolutePath());
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(tripOutputFile)));
		out.println(  "# start-ID; periodic-start-ID; start-station; start-time; "
		            + "end-ID; periodic-end-ID; end-station; end-time; line");

		String type;
		NonPeriodicEvent end;
		boolean hasIncomingTrainEdge;
		for (NonPeriodicEvent start: Net.getEvents())
		{
			hasIncomingTrainEdge = false;
			for (NonPeriodicActivity a: start.getIncomingActivities())
			{
				type = a.getType();
				if (type.equals("drive") || type.equals("wait"))
				{
					hasIncomingTrainEdge = true;
					break;
				}
			}
			if (hasIncomingTrainEdge) // not start event of a trip
				continue;
				

			end = findEndEvent(start);
			
			StringBuilder lineStr = new StringBuilder(256);
			lineStr.append(start.getID());
			lineStr.append("; ");
			lineStr.append(start.getPeriodicParentEventID());
			lineStr.append("; ");
			lineStr.append(stations[start.getPeriodicParentEventID()-1]);
			lineStr.append("; ");
			lineStr.append(start.getTime());
			lineStr.append("; ");
			lineStr.append(end.getID());
			lineStr.append("; ");
			lineStr.append(end.getPeriodicParentEventID());
			lineStr.append("; ");
			lineStr.append(stations[end.getPeriodicParentEventID()-1]);
			lineStr.append("; ");
			lineStr.append(end.getTime());
			lineStr.append("; ");
			lineStr.append(lines[start.getPeriodicParentEventID()-1]);

			out.println(lineStr);
		}
		out.close();
	}



	private static void readConfig() throws IOException
	{
		Config config = new Config(new File("basis/Config.cnf"));

		eventInputFile = config.getStringValue("default_events_periodic_file");
		tripOutputFile = config.getStringValue("default_trips_file");
		CHECK_CONSISTENCY = config.getBooleanValue("DM_enable_consistency_checks");
		DEBUG = config.getBooleanValue("DM_debug");
		VERBOSE = config.getBooleanValue("DM_verbose");

		if (DEBUG)
		{
			CHECK_CONSISTENCY = true;
			VERBOSE = true;
		}

		if (VERBOSE)
		{
			System.out.println("Trips: using the following configuration:");
			System.out.println("  periodic events input file: " + new File(eventInputFile).getAbsolutePath());
			System.out.println("  trips output file: " + new File(tripOutputFile).getAbsolutePath());
		}
	}



	private static NonPeriodicEvent findEndEvent(NonPeriodicEvent start)
	{
		boolean endEventFound = false;
		NonPeriodicEvent end = start;
		while (! endEventFound)
		{
			endEventFound = true;

			for (NonPeriodicActivity a: end.getOutgoingActivities())
			{
				String type = a.getType();
				if (type.equals("drive") || type.equals("wait"))
				{
					end = a.getTarget();
					endEventFound = false;
					break;
				}
			}
		}
		return end;
	}



	private static void readPeriodicEventFile() throws IOException
	{
		if (CHECK_CONSISTENCY && ! Tools.checkIDs(eventInputFile))
			throw new IOException("Trips: invalid numbering of IDs or empty input file: " + eventInputFile);

		int count = Tools.countRelevantLines(eventInputFile);
		stations = new String[count];
		lines = new int[count];

		BufferedReader in = new BufferedReader(new FileReader(eventInputFile));
		int i = 0;
		String line;
		while ((line = in.readLine()) != null)
		{
			int position = line.indexOf('#');
			if (position != -1)
				line = line.substring(0, position);
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(";");
			stations[i] = tokens[2].trim();
			lines[i++] = Integer.parseInt(tokens[3].trim());
		}
		in.close();
	}
}
