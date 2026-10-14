package linePlanning.DPTN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graph.algorithms.shortestPath.Dijkstra;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import linePlanning.DPTN.DPTNArc.DEPTNArcType;
import linePlanning.PTN.Connection;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.instance.StationODNode;
import util.UnorderedPair;

public class DPTN extends DirectedGraph<StationNode, DPTNArc>
{
	private final LinePlanningInstance instance;

	private Map<Station, List<StationConnectionNode>> departureNodes;
	private Map<Station, List<StationConnectionNode>> arrivalNodes;

	private List<DPTNDwellArc> dwellArcs;
	private List<DPTNTransferArc> transferArcs;
	private List<DPTNDrivingArc> drivingArcs;

	// conjugates
	private Map<StationNode, StationNode> nodeConjugates;
	private List<UnorderedPair<DPTNArc, DPTNArc>> arcConjugates;
	private Map<DPTNArc, UnorderedPair<DPTNArc, DPTNArc>> conjugatePairMap;

	public DPTN(LinePlanningInstance instance, boolean addPassengerRoutingArcs,
			boolean addLineStartArcs)
	{
		this.instance = instance;

		createNodes();
		createDwellArcs();
		createDrivingArcs();
		if (addLineStartArcs)
		{
			createBoardingArcsForLines();
		}
		if (addPassengerRoutingArcs)
		{
			createTransferArcs();
			createBoardingArcs();
		}
		this.determineNodeConjugates(instance);
		this.determineArcConjugates();
	}

	public Map<Station, List<StationConnectionNode>> getDepartureNodes()
	{
		return departureNodes;
	}

	public Map<Station, List<StationConnectionNode>> getArrivalNodes()
	{
		return arrivalNodes;
	}

	public DPTNRoute getShortestPath(Station from, Station to)
	{
		Dijkstra<StationNode, DPTNArc> dijkstra = new Dijkstra<>(this, instance.getDepartureODNode(from));
		dijkstra.setDestination(instance.getArrivalODNode(to));
		dijkstra.computeDistances();
		List<DPTNArc> arcs = new ArrayList<>();
		for (DirectedGraphArc<StationNode, DPTNArc> dArc : dijkstra.getPath(instance.getArrivalODNode(to)))
		{
			arcs.add(dArc.getData());
		}
		if(!dijkstra.containsPath(instance.getArrivalODNode(to))) {
			throw new Error("No path");
		}
		double duration = dijkstra.getDistance(instance.getArrivalODNode(to));

		return new DPTNRoute(from, to, arcs, duration);
	}

	public double getMinimumTravelTime(Station from, Station to)
	{
		DPTNRoute shortestPath = getShortestPath(from, to);
		return shortestPath.getDuration();
	}

	private void createBoardingArcs()
	{
		for (StationNode sNode : instance.getConnectionNetwork().getNodes())
		{
			Station station = sNode.getStation();
			StationODNode stationDeparture = instance.getDepartureODNode(station);
			StationODNode stationArrival = instance.getArrivalODNode(station);
			for (StationConnectionNode departureNode : departureNodes.get(station))
			{
				DPTNArc boardingArc = new DPTNArc(stationDeparture, departureNode, DEPTNArcType.BOARD);
				addArc(stationDeparture, departureNode, boardingArc, boardingArc.getDuration());
			}
			for (StationConnectionNode arrivalNode : arrivalNodes.get(station))
			{
				DPTNArc boardingArc = new DPTNArc(arrivalNode, stationArrival, DEPTNArcType.BOARD);
				addArc(arrivalNode, stationArrival, boardingArc, boardingArc.getDuration());
			}
		}
	}

	private void createBoardingArcsForLines()
	{
		for (StationNode sNode : instance.getConnectionNetwork().getNodes())
		{
			Station station = sNode.getStation();
			StationODNode stationDeparture = instance.getDepartureODNode(station);
			StationODNode stationArrival = instance.getArrivalODNode(station);
			for (StationConnectionNode departureNode : departureNodes.get(station))
			{
				int connectionType = departureNode.getConnection().getType();
				if (connectionType == LinePlanningParameters.INTERCITY && station.isTerminalIC())
				{
					DPTNArc terminalArc = new DPTNArc(stationDeparture, departureNode, DEPTNArcType.TERMINAL);
					addArc(stationDeparture, departureNode, terminalArc, terminalArc.getDuration());
				}
				if (connectionType == LinePlanningParameters.SPRINTER && station.isTerminalSPR())
				{
					DPTNArc terminalArc = new DPTNArc(stationDeparture, departureNode, DEPTNArcType.TERMINAL);
					addArc(stationDeparture, departureNode, terminalArc, terminalArc.getDuration());
				}
			}
			for (StationConnectionNode arrivalNode : arrivalNodes.get(station))
			{
				int cType = arrivalNode.getConnection().getType();
				if (cType == LinePlanningParameters.INTERCITY && station.isTerminalIC())
				{
					DPTNArc terminalArc = new DPTNArc(arrivalNode, stationArrival, DEPTNArcType.TERMINAL);
					addArc(arrivalNode, stationArrival, terminalArc, terminalArc.getDuration());
				}
				if (cType == LinePlanningParameters.SPRINTER && station.isTerminalSPR())
				{
					DPTNArc terminalArc = new DPTNArc(arrivalNode, stationArrival, DEPTNArcType.TERMINAL);
					addArc(arrivalNode, stationArrival, terminalArc, terminalArc.getDuration());
				}
			}
		}
	}

	private void createDrivingArcs()
	{
		drivingArcs = new ArrayList<>();
		for (DirectedGraphArc<StationNode, Connection> arc : instance.getConnectionNetwork().getArcs())
		{
			Connection connection = arc.getData();
			Station depSation = connection.getFrom();
			Station arrStation = connection.getTo();
			int numberSlots = 1;

			for (int slot = 1; slot <= numberSlots; slot++)
			{
				// find the departure and the arrival
				StationConnectionNode departureNode = null;
				for (StationConnectionNode scsNode : departureNodes.get(depSation))
				{
					if (scsNode.getConnection().equals(connection))
					{
						departureNode = scsNode;
						break;
					}
				}
				StationConnectionNode arrivalNode = null;
				for (StationConnectionNode scsNode : arrivalNodes.get(arrStation))
				{
					if (scsNode.getConnection().equals(connection))
					{
						arrivalNode = scsNode;
						break;
					}
				}
				if (departureNode == null || arrivalNode == null)
				{
					throw new Error("Did not find node for " + connection + " departureNode:  " + departureNode
							+ " arrivalNode: " + arrivalNode);
				}
				double cost = connection.getDuration() / (double) LinePlanningParameters.PERIOD;
				int maxFrequency = connection.getMaxFreq();
				DPTNDrivingArc travelArc = new DPTNDrivingArc(departureNode, arrivalNode, connection,
						LinePlanningParameters.CAPACITY, cost, maxFrequency);
				addArc(departureNode, arrivalNode, travelArc, travelArc.getDuration());
				drivingArcs.add(travelArc);
			}
		}
	}

	private void createDwellArcs()
	{
		dwellArcs = new ArrayList<>();
		transferArcs = new ArrayList<>();
		for (StationNode sNode : instance.getConnectionNetwork().getNodes())
		{
			Station station = sNode.getStation();
			// create dwell and transfer arcs from all arrivals to all departures
			for (StationConnectionNode arrival : arrivalNodes.get(station))
			{
				for (StationConnectionNode departure : departureNodes.get(station))
				{
					Connection incoming = arrival.getConnection();
					Connection outgoing = departure.getConnection();

					if (instance.feasibleDriveArcSequence(incoming, outgoing))
					{
						int type = incoming.getType();
						if (type != outgoing.getType())
						{
							type = LinePlanningParameters.TWIJFEL;
						}
						double cost = LinePlanningParameters.DWELL_TIME / (double) LinePlanningParameters.PERIOD;
						int maxFrequency = Math.min(incoming.getMaxFreq(), outgoing.getMaxFreq());
						DPTNDwellArc dwellArc = new DPTNDwellArc(arrival, departure,
								LinePlanningParameters.DWELL_TIME, LinePlanningParameters.CAPACITY, cost, type,
								maxFrequency);
						addArc(arrival, departure, dwellArc, dwellArc.getDuration());
						dwellArcs.add(dwellArc);
					}
				}
			}
		}
	}

	private void createTransferArcs()
	{
		dwellArcs = new ArrayList<>();
		transferArcs = new ArrayList<>();
		for (StationNode sNode : instance.getConnectionNetwork().getNodes())
		{
			Station station = sNode.getStation();
			// Create transfer arcs from all arrivals to all departures.
			for (StationConnectionNode arrival : arrivalNodes.get(station))
			{
				for (StationConnectionNode departure : departureNodes.get(station))
				{
					Connection incoming = arrival.getConnection();
					Connection outgoing = departure.getConnection();
					if (instance.sensibleTransfer(incoming, outgoing))
					{
						// Create a transfer arc.
						int transferTime = 0;
						DPTNTransferArc transferArc = new DPTNTransferArc(arrival, departure, station, transferTime,
								LinePlanningParameters.TRANSFER_PENALTY);
						addArc(arrival, departure, transferArc, transferArc.getDuration());
						transferArcs.add(transferArc);
					}
				}
			}
		}
	}

	private void determineNodeConjugates(LinePlanningInstance instance)
	{
		nodeConjugates = new HashMap<>();
		for (StationNode sNode : instance.getConnectionNetwork().getNodes())
		{
			Station station = sNode.getStation();
			StationODNode source = instance.getDepartureODNode(station);
			StationODNode sink = instance.getArrivalODNode(station);
			nodeConjugates.put(source, sink);

			for (StationConnectionNode dep : departureNodes.get(station))
			{
				boolean foundConjugate = false;
				for (StationConnectionNode arr : arrivalNodes.get(station))
				{
					if (Connection.opposites(dep.getConnection(), arr.getConnection()))
					{
						nodeConjugates.put(arr, dep);
						nodeConjugates.put(dep, arr);
						foundConjugate = true;
						break;
					}
				}
				if (!foundConjugate)
				{
					throw new Error("Did not find conjugate for " + dep);
				}
			}
		}
	}

	public List<UnorderedPair<DPTNArc, DPTNArc>> getArcConjugates()
	{
		return arcConjugates;
	}

	private void determineArcConjugates()
	{
		conjugatePairMap = new HashMap<>();

		arcConjugates = new ArrayList<>();
		for (StationNode sNode : nodeConjugates.keySet())
		{
			StationNode conjugateNode = nodeConjugates.get(sNode);
			for (DirectedGraphArc<StationNode, DPTNArc> outOrig : outArcs.get(sNode))
			{
				if (!(outOrig.getData() instanceof DPTNDrivingArc)
						&& !(outOrig.getData() instanceof DPTNDwellArc)
						&& !(outOrig.getData().getArcType().equals(DEPTNArcType.TERMINAL)))
				{
					continue; // only dwell and drive arcs have conjugatess
				}
				boolean foundConjugate = false;
				for (DirectedGraphArc<StationNode, DPTNArc> inConjugateNode : inArcs.get(conjugateNode))
				{
					if (!(inConjugateNode.getData() instanceof DPTNDrivingArc)
							&& !(inConjugateNode.getData() instanceof DPTNDwellArc)
							&& !(inConjugateNode.getData().getArcType().equals(DEPTNArcType.TERMINAL)))
					{
						continue; // only dwell and drive arcs have conjugatess
					}
					if (outOrig.getTo().equals(nodeConjugates.get(inConjugateNode.getFrom())))
					{
						UnorderedPair<DPTNArc, DPTNArc> pair = new UnorderedPair<>(outOrig.getData(),
								inConjugateNode.getData());
						if (!arcConjugates.contains(pair))
						{
							arcConjugates.add(pair);
							conjugatePairMap.put(outOrig.getData(), pair);
							conjugatePairMap.put(inConjugateNode.getData(), pair);
						}
						foundConjugate = true;
						break;
					}
				}
				if (!foundConjugate)
				{
					throw new Error("Did not find conjugate for " + outOrig);
				}
			}
		}
	}

	private void createNodes()
	{
		departureNodes = new HashMap<>();
		arrivalNodes = new HashMap<>();
		for (StationNode sNode : instance.getConnectionNetwork().getNodes()) // changed to ConnectionNetwork
		{
			Station station = sNode.getStation();
			// find outgoing connections
			List<StationConnectionNode> depsAtStation = new ArrayList<>();
			for (DirectedGraphArc<StationNode, Connection> out : instance.getConnectionNetwork().getOutArcs(sNode))
			{
				Connection c = out.getData();
				boolean arrival = false;

				StationConnectionNode sclNode = new StationConnectionNode(station, c, arrival);
				addNode(sclNode);
				depsAtStation.add(sclNode);
			}
			departureNodes.put(station, depsAtStation);

			// find incoming connections
			List<StationConnectionNode> arrsAtStation = new ArrayList<>();
			for (DirectedGraphArc<StationNode, Connection> in : instance.getConnectionNetwork().getInArcs(sNode))
			{
				Connection c = in.getData();
				boolean arrival = true;
				StationConnectionNode sclNode = new StationConnectionNode(station, c, arrival);
				addNode(sclNode);
				arrsAtStation.add(sclNode);
			}
			arrivalNodes.put(station, arrsAtStation);
		}

		for (Station station : instance.getStations())
		{
			StationODNode dep = instance.getDepartureODNode(station);
			StationODNode arr = instance.getArrivalODNode(station);
			addNode(dep);
			addNode(arr);
		}
	}

	public void resetCosts()
	{
		for (DirectedGraphArc<StationNode, DPTNArc> arc : arcs)
		{
			arc.setCost(arc.getData().getDuration(), 0);
		}
	}

	public void resetDuals()
	{
		for (DirectedGraphArc<StationNode, DPTNArc> arc : arcs)
		{
			arc.setCost(arc.getData().getDuration(), 0);
			arc.setCost(0, 1);

			arc.setDual(0, 0);
			arc.setDual(0, 1);
		}
	}

	public DPTNArc getConjugate(DPTNArc arc)
	{
		UnorderedPair<DPTNArc, DPTNArc> pair = conjugatePairMap.get(arc);
		if (pair.getFirst().equals(arc))
		{
			return pair.getSecond();
		}
		if (pair.getSecond().equals(arc))
		{
			return pair.getFirst();
		}
		throw new Error("oops");
	}

	public UnorderedPair<DPTNArc, DPTNArc> getConjugatePair(DPTNArc arc)
	{
		return conjugatePairMap.get(arc);
	}
}
