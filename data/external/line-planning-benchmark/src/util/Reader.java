package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import linePlanning.PTN.Connection;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import linePlanning.instance.Track;

public class Reader
{
	public static double readDouble(String file) throws IOException
	{
		double value = 0;
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			value = Double.valueOf(line);
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return value;
	}

	public static int readBudget(String file) throws IOException
	{
		return readInt(file);
	}

	public static int readInt(String file) throws IOException
	{
		int value = 0;
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			value = Integer.valueOf(line);
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return value;
	}

	public static boolean readBoolean(String file) throws IOException
	{
		boolean result = false;
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			result = Boolean.valueOf(line);
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return result;
	}

	public static List<Station> readStations(String file) throws IOException
	{
		List<Station> stations = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			stations.add(readStation(line));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return stations;
	}

	public static List<Connection> readConnections(String file, Map<String, Station> stationMap,
			Map<Integer, Track> trackIndexMap) throws IOException
	{
		// First pass to initialise connections.
		List<Connection> connections = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			String[] data = line.split(" ");
			int index = Integer.valueOf(data[0]);
			Station from = stationMap.get(data[1]);
			Station to = stationMap.get(data[2]);
			double length = Double.valueOf(data[3]);
			double duration = Double.valueOf(data[4]);
			int maxFreq = Integer.valueOf(data[5]);
			int type = Integer.valueOf(data[6]);
			int reverseIndex = Integer.valueOf(data[7]);

			// Read tracks.
			List<Track> trackPath = new ArrayList<>();
			for (int i = 8; i < data.length; i++)
			{
				int trackIndex = Integer.valueOf(data[i]);
				trackPath.add(trackIndexMap.get(trackIndex));
			}
			connections.add(new Connection(index, from, to, length, duration, trackPath, maxFreq, type));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();

		// Second pass to set reverse connections.
		Map<Integer, Connection> connectionIndexMap = new LinkedHashMap<>();
		for (Connection connection : connections)
		{
			connectionIndexMap.put(connection.getIndex(), connection);
		}
		bufferedReader = new BufferedReader(new FileReader(file));
		line = bufferedReader.readLine();
		while (line != null)
		{
			String[] data = line.split(" ");
			int index = Integer.valueOf(data[0]);
			int reverseIndex = Integer.valueOf(data[7]);
			connectionIndexMap.get(index).setReverse(connectionIndexMap.get(reverseIndex));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return connections;
	}

	public static Track readTrack(String line, Map<String, Station> stationMap)
	{
		String[] data = line.split(" ");
		int index = Integer.valueOf(data[0]);
		Station from = stationMap.get(data[1]);
		Station to = stationMap.get(data[2]);
		double length = Double.valueOf(data[3]);
		int speed = Integer.valueOf(data[4]);
		double duration = Double.valueOf(data[5]);
		int nrOfTracks = Integer.valueOf(data[6]);
		return new Track(index, from, to, length, speed, duration, nrOfTracks);
	}

	public static List<Track> readTracks(String file, Map<String, Station> stationMap) throws IOException
	{
		List<Track> tracks = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			tracks.add(readTrack(line, stationMap));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return tracks;
	}

	public static List<ODPair> readODPairs(String file, Map<String, Station> stationMap) throws IOException
	{
		return readODPairs(file, stationMap, 1.0);
	}

	public static List<ODPair> readODPairs(String file, Map<String, Station> stationMap, double demandScalar) throws IOException
	{
		List<ODPair> pairs = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			String[] data = line.split(" ");
			Station origin = stationMap.get(data[0]);
			Station destination = stationMap.get(data[1]);
			double volume = Double.valueOf(data[2]) * demandScalar;
			pairs.add(new ODPair(origin, destination, volume));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return pairs;
	}

	public static Station readStation(String line)
	{
		String[] data = line.split(" ");
		int index = Integer.valueOf(data[0]);
		String name = data[1];
		String nameFull = data[2];
		boolean terminalIC = Boolean.valueOf(data[3]);
		boolean terminalSPR = Boolean.valueOf(data[4]);
		int type = Integer.valueOf(data[5]);
		boolean inRandstad = Boolean.valueOf(data[6]);
		double latitude = Double.valueOf(data[7]);
		double longitude = Double.valueOf(data[8]);
		return new Station(index, name, nameFull, terminalIC, terminalSPR, type, inRandstad, latitude, longitude);
	}

	public static List<UnorderedPair<Track, Track>> readForbiddenTurns(String file, Map<Integer, Track> trackIndexMap)
			throws IOException
	{
		List<UnorderedPair<Track, Track>> forbiddenTurns = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			String[] data = line.split(" ");
			Track first = trackIndexMap.get(Integer.valueOf(data[0]));
			Track second = trackIndexMap.get(Integer.valueOf(data[1]));
			forbiddenTurns.add(new UnorderedPair<>(first, second));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return forbiddenTurns;
	}

	public static List<Triple<Station, Station, Station>> readAllowedTypeChanges(String file,
			Map<String, Station> stationMap) throws IOException
	{
		List<Triple<Station, Station, Station>> allowedTypeChanges = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			String[] data = line.split(" ");
			Station first = stationMap.get(data[0]);
			Station second = stationMap.get(data[1]);
			Station third = stationMap.get(data[2]);
			allowedTypeChanges.add(new Triple<>(first, second, third));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return allowedTypeChanges;
	}
}
