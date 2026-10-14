package linePlanning.lineGeneration;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import graph.algorithms.search.DFS;
import graph.algorithms.shortestPath.Dijkstra;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.activation.Activation;
import graph.structures.digraph.activation.DefaultActivationFunction;
import linePlanning.DPTN.DPTN;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.DPTN.DPTNDwellArc;
import linePlanning.PTN.Connection;
import linePlanning.instance.Index;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.instance.Track;

public class LinePoolGenerator
{
	public static int MAX_LINES_PER_PAIR = Integer.MAX_VALUE;

	private final double detourFactor;
	private final double maxDurationSprinter;
	private final DPTN DPTN;

	private List<Line> lines;

	public LinePoolGenerator(LinePlanningInstance instance)
	{
		try
		{
			BufferedReader br = new BufferedReader(new java.io.FileReader(instance.getLineGenerationFile()));
			detourFactor = Double.valueOf(br.readLine());
			double defaultMaxDurationSprinter = Double.valueOf(br.readLine());
			br.close();
			maxDurationSprinter = instance.hasICs() ? defaultMaxDurationSprinter : Double.MAX_VALUE;
		}
		catch (Exception e)
		{
			throw new RuntimeException(
					"Failed to read line generation parameters from " + instance.getLineGenerationFile(), e);
		}
		lines = new ArrayList<>();
		this.DPTN = instance.getDPTN();
		for (Station origin : instance.getStations())
		{
			if (!origin.isTerminalIC() && !origin.isTerminalSPR())
			{
				continue;
			}

			for (Station destination : instance.getStations())
			{
				if (origin.getName().compareTo(destination.getName()) >= 0)
				{
					continue;
				}
				if (!destination.isTerminalIC() && !destination.isTerminalSPR())
				{
					continue;
				}
				Dijkstra<StationNode, Track> dijkstra = new Dijkstra<StationNode, Track>(instance.getTrackNetwork(),
						instance.getStationNode(origin), new DistanceWeightFunction<Track>());
				dijkstra.computeDistances();
				if (dijkstra.containsPath(instance.getStationNode(destination)))
				{
					boolean print = false;

					double distance = dijkstra.getDistance(instance.getStationNode(destination));
					double threshold = detourFactor * distance;
					if (print)
					{
						System.out.println("Dist is " + distance + " threshold is " + threshold);
						System.out.println("Path is ");
						for (DirectedGraphArc<StationNode, Track> a : dijkstra
								.getPath(instance.getStationNode(destination)))
						{
							System.out.println(a.getData());
						}
					}
					// Find lines in station network.
					Activation.getActivation().setActivationFunction(new LineActivationFunction());
					StationNode from = instance.getDepartureODNode(origin);
					StationNode to = instance.getArrivalODNode(destination);
					DFS<StationNode, DPTNArc> DFS = new DFS<>();
					List<Line> linesPerPair = new ArrayList<>();

					for (List<DirectedGraphArc<StationNode, DPTNArc>> path : DFS.applyDFS(DPTN, from, to, threshold,
							new DistanceWeightFunction<DPTNArc>()))
					{
						// Generate a line.
						Line line = new Line(Index.getLineIndex(), origin, destination, path, DPTN, instance);

						if (print)
						{
							System.out.println(line);
						}

						// Sprinter duration sanity check.
						if (!satisfiesSprinterDuration(line))
						{
							continue;
						}

						// Station elementarity check.
						if (!isElementary(line))
						{
							continue;
						}

						// Add to pool.
						linesPerPair.add(line);
						if (print)
						{
							System.out.println(line);
						}
					}

					// Add at most K columns per pair.
					linesPerPair.sort((a, b) -> Double.compare(a.getDuration(), b.getDuration()));
					linesPerPair = linesPerPair.subList(0, Math.min(MAX_LINES_PER_PAIR, linesPerPair.size()));
					lines.addAll(linesPerPair);
					Activation.getActivation().setActivationFunction(new DefaultActivationFunction());
				}
			}
		}
		System.out.println("Line pool contains " + lines.size() + " lines.");
	}

	public List<Line> getLines()
	{
		return lines;
	}

	private boolean isElementary(Line line)
	{
		Set<Station> stations = new LinkedHashSet<>();
		stations.add(line.getOrigin());
		for (Connection connection : line.getConnectionPathForward())
		{
			Station to = connection.getTo();
			if (stations.contains(to))
			{
				return false;
			}
			stations.add(to);
		}
		return true;
	}

	private boolean satisfiesSprinterDuration(Line line)
	{
		int sprinterDuration = 0;
		for (DPTNArc arc : line.getArcPathForward())
		{
			if (arc instanceof DPTNDrivingArc)
			{
				DPTNDrivingArc drivingArc = (DPTNDrivingArc) arc;
				if (drivingArc.getConnection().getType() == LinePlanningParameters.SPRINTER)
				{
					sprinterDuration += (int) Math.floor(drivingArc.getDuration());
				}
			}
			if (arc instanceof DPTNDwellArc)
			{
				DPTNDwellArc dwellArc = (DPTNDwellArc) arc;
				if (dwellArc.getType() == LinePlanningParameters.SPRINTER)
				{
					sprinterDuration += (int) Math.floor(dwellArc.getDuration());
				}
			}
		}
		return sprinterDuration <= maxDurationSprinter;
	}
}
