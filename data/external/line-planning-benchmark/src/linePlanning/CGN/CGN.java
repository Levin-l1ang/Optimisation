package linePlanning.CGN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import linePlanning.PTN.Connection;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.instance.StationODNode;
import linePlanning.lineGeneration.Line;

public class CGN extends DirectedGraph<StationNode, CGNArc>
{
	private Map<Station, StationODNode> originNodeMap;
	private Map<Station, StationODNode> destinationNodeMap;
	private Map<Station, StationNode> transferNodeMap;
	private List<StationLineNode> stationLineNodes;
	private Map<Line, List<CGNArc>> arcsByLine;

	public CGN()
	{
		super();

		this.originNodeMap = new LinkedHashMap<>();
		this.destinationNodeMap = new LinkedHashMap<>();
		this.transferNodeMap = new LinkedHashMap<>();
		this.stationLineNodes = new ArrayList<>();
		this.arcsByLine = new LinkedHashMap<>();
	}

	public static CGN generate(LinePlanningInstance instance, List<Line> linePool)
	{
		DirectedGraph<StationNode, Connection> network = instance.getConnectionNetwork();
		CGN cgn = new CGN();

		// Add station OD nodes.
		for (StationNode stationNode : network.getNodes())
		{
			cgn.addOriginNode(instance.getDepartureODNode(stationNode.getStation()));
			cgn.addDestinationNode(instance.getArrivalODNode(stationNode.getStation()));
			cgn.addTransferNode(stationNode);
		}

		// Add nodes and travel arcs for all lines.
		for (Line line : linePool)
		{
			addNodesAndTravelArcs(cgn, line, true, instance);
			addNodesAndTravelArcs(cgn, line, false, instance);
		}

		int travelArc = 0;
		int transferArc = 0;
		for (DirectedGraphArc<StationNode, CGNArc> arc : cgn.getArcs())
		{
			if (arc.getData() instanceof CGNTravelArc)
			{
				travelArc++;
			}
			if (arc.getData() instanceof CGNTransferArc)
			{
				transferArc++;
			}
		}
		System.out.println("CGN contains "+travelArc +" travel arcs and "+
			transferArc +" transfer arcs" + travelArc);

		return cgn;
	}

	private static void addNodesAndTravelArcs(CGN cgn, Line line, boolean forward, LinePlanningInstance instance)
	{
		List<Connection> cPath = forward ? line.getConnectionPathForward() : line.getConnectionPathBackward();
		StationLineNode previous = null;

		for (int i = 0; i < cPath.size(); i++)
		{
			Connection lineSegment = cPath.get(i);

			if (previous == null)
			{
				StationLineNode startDepartureNode = new StationLineNode(lineSegment.getFrom(), line, forward);
				cgn.addStationlineNode(startDepartureNode);
				previous = startDepartureNode;
			}

			cgn.addArc(cgn.getOriginNode(previous.getStation()), previous,
				new CGNBoardingArc(-LinePlanningParameters.DWELL_TIME, previous.getStation(), true),
				-LinePlanningParameters.DWELL_TIME);
			double transferDuration = LinePlanningParameters.TRANSFER_PENALTY - LinePlanningParameters.DWELL_TIME;
			cgn.addArc(cgn.getTransferNode(previous.getStation()), previous, new CGNTransferArc(transferDuration),
				transferDuration);

			StationLineNode nextNode = new StationLineNode(lineSegment.getTo(), line, forward);
			cgn.addStationlineNode(nextNode);

			CGNTravelArc travelArc = new CGNTravelArc(line, lineSegment, LinePlanningParameters.CAPACITY,
					LinePlanningParameters.DWELL_TIME);
			cgn.addArc(previous, nextNode, travelArc, travelArc.getDuration());
			cgn.arcsByLine.computeIfAbsent(line, k -> new ArrayList<>()).add(travelArc);

			cgn.addArc(nextNode, cgn.getDestinationNode(nextNode.getStation()),
					new CGNBoardingArc(0, nextNode.getStation(), false), 0);

			cgn.addArc(nextNode, cgn.getTransferNode(nextNode.getStation()), new CGNTransferArc(0), 0);
			previous = nextNode;
		}
	}

	public void addOriginNode(StationODNode node)
	{
		addNode(node);
		originNodeMap.put(node.getStation(), node);
	}

	public void addDestinationNode(StationODNode node)
	{
		addNode(node);
		destinationNodeMap.put(node.getStation(), node);
	}

	public void addStationlineNode(StationLineNode node)
	{
		addNode(node);
		stationLineNodes.add(node);
	}

	public void addTransferNode(StationNode node) {
		addNode(node);
		transferNodeMap.put(node.getStation(), node);
	}

	public StationNode getTransferNode(Station station) {
		return transferNodeMap.get(station);
	}

	public StationODNode getOriginNode(Station station)
	{
		return originNodeMap.get(station);
	}

	public Collection<StationODNode> getOriginNodes()
	{
		return originNodeMap.values();
	}

	public Collection<StationODNode> getDestinationNodes()
	{
		return destinationNodeMap.values();
	}

	public StationODNode getDestinationNode(Station station)
	{
		return destinationNodeMap.get(station);
	}

	public List<StationLineNode> getStationLineNodes()
	{
		return stationLineNodes;
	}

	public List<CGNArc> getArcs(Line line)
	{
		return arcsByLine.getOrDefault(line, List.of());
	}

	public void resetDuals()
	{
		for (DirectedGraphArc<StationNode, CGNArc> arc : arcs)
		{
			arc.setCost(arc.getData().getDuration(), 0);
			arc.setCost(0, 1);

			arc.setDual(0, 0);
			arc.setDual(0, 1);
		}
	}

	public CGN filter(Set<Line> lines)
	{
		CGN cgn = new CGN();
		for (StationNode node : this.getNodes())
		{
			if (node instanceof StationODNode)
			{
				StationODNode stationODNode = (StationODNode) node;
				if (stationODNode.isOrigin())
				{
					cgn.addOriginNode(stationODNode);
				}
				else
				{
					cgn.addDestinationNode(stationODNode);
				}
			}
			if (node instanceof StationLineNode)
			{
				StationLineNode stationLineNode = (StationLineNode) node;
				if (lines.contains(stationLineNode.getLine()))
				{
					cgn.addStationlineNode(stationLineNode);
				}
			}
		}
		for (DirectedGraphArc<StationNode, CGNArc> arc : this.getArcs())
		{
			if (cgn.getNodes().contains(arc.getFrom()) && cgn.getNodes().contains(arc.getTo()))
			{
				cgn.addArc(arc.getFrom(), arc.getTo(), arc.getData(), arc.getData().getDuration());
			}
		}
		return cgn;
	}
}
