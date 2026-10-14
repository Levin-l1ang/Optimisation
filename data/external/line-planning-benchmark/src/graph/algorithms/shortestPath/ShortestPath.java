package graph.algorithms.shortestPath;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.algorithms.shortestPath.arcWeightFunctions.DefaultArcWeightFunction;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.Activation;

/**
 * Class that solves a shortest path problem on a directed acyclic graph using
 * dynamic programming.
 * 
 * @author Bart van Rossum
 */
public class ShortestPath<V extends DirectedGraphNodeIndex, A>
{
	private final V origin;
	private final V destination;
	private final AbstractArcWeightFunction<V, A> arcWeightFunction;

	private final int maxIndex;
	private double[] distances;
	private DirectedGraphArc<V, A>[] arcs;
	private final boolean forwardDirection;

	/**
	 * Initialise distance and predecessor maps, and then computes the shortest path
	 * from origin to destination.
	 * 
	 * @param graph            directed acyclic graph which we assume to be
	 *                         topologically sorted
	 * @param origin           origin node
	 * @param destination      destination node
	 * @param forwardDirection whether we move in forward direction or not
	 */
	public ShortestPath(DirectedGraph<V, A> graph, V origin, V destination, boolean forwardDirection)
	{
		this(graph, origin, destination, forwardDirection, new DefaultArcWeightFunction<>());
	}

	public ShortestPath(DirectedGraph<V, A> graph, V origin, V destination, boolean forwardDirection,
			AbstractArcWeightFunction<V, A> arcWeightFunction)
	{
		this.origin = origin;
		this.destination = destination;
		this.forwardDirection = forwardDirection;
		this.arcWeightFunction = arcWeightFunction;

		graph.setNodeIndices();
		this.maxIndex = Math.max(origin.getNodeIndex(), destination.getNodeIndex());
		initialiseArrays(graph);
		updateDistances(graph);
	}

	public double getDistance(V target)
	{
		return distances[target.getNodeIndex()];
	}

	public boolean containsPath(V target)
	{
		if (target.getNodeIndex() >= distances.length)
		{
			return false;
		}
		return distances[target.getNodeIndex()] < Double.MAX_VALUE;
	}

	public List<DirectedGraphArc<V, A>> getPath(V target)
	{
		List<DirectedGraphArc<V, A>> path = new ArrayList<>();
		V current = target;
		while (!current.equals(origin))
		{
			DirectedGraphArc<V, A> arc = arcs[current.getNodeIndex()];
			path.add(arc);
			if (forwardDirection)
			{
				current = arc.getFrom();
			}
			else
			{
				current = arc.getTo();
			}
		}
		return path;
	}

	@SuppressWarnings("unchecked")
	private void initialiseArrays(DirectedGraph<V, A> graph)
	{
		this.distances = new double[maxIndex + 1];
		this.arcs = (DirectedGraphArc<V, A>[]) Array.newInstance(DirectedGraphArc.class, maxIndex + 1);

		for (int i = 0; i < maxIndex + 1; i++)
		{
			distances[i] = Double.MAX_VALUE;
		}
		distances[origin.getNodeIndex()] = 0;
	}

	private void updateDistances(DirectedGraph<V, A> graph)
	{
		if (forwardDirection)
		{
			for (int i = origin.getNodeIndex(); i < destination.getNodeIndex(); i++)
			{
				V node = graph.getNodes().get(i);
				if (!Activation.getActivation().getActivationFunction().isActiveNode(node))
				{
					continue;
				}
				if (distances[node.getNodeIndex()] == Double.MAX_VALUE)
				{
					continue;
				}
				for (DirectedGraphArc<V, A> arc : graph.getOutArcs(node))
				{
					if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
					{
						continue;
					}
					processArc(node, arc.getTo(), arc);
				}
			}
		}
		else
		{
			for (int i = origin.getNodeIndex(); i > destination.getNodeIndex(); i--)
			{
				V node = graph.getNodes().get(i);
				if (!Activation.getActivation().getActivationFunction().isActiveNode(node))
				{
					continue;
				}
				if (distances[node.getNodeIndex()] == Double.MAX_VALUE)
				{
					continue;
				}
				for (DirectedGraphArc<V, A> arc : graph.getInArcs(node))
				{
					if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
					{
						continue;
					}
					processArc(node, arc.getFrom(), arc);
				}
			}
		}
	}

	private void processArc(V from, V to, DirectedGraphArc<V, A> arc)
	{
		double distance = arcWeightFunction.getArcWeight(arc);
		if (distances[from.getNodeIndex()] + distance < distances[to.getNodeIndex()])
		{
			distances[to.getNodeIndex()] = distances[from.getNodeIndex()] + distance;
			arcs[to.getNodeIndex()] = arc;
		}
	}
}
