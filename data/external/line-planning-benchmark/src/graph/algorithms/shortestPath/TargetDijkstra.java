package graph.algorithms.shortestPath;

import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.PriorityQueue;
import java.util.Set;

import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.algorithms.shortestPath.arcWeightFunctions.DefaultArcWeightFunction;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.Activation;

public class TargetDijkstra<V extends DirectedGraphNodeIndex, A>
{
	private final DirectedGraph<V, A> graph;
	private final V destination;
	private final int numNodes;

	private final AbstractArcWeightFunction<V, A> arcWeightFunction;

	private double[] distance;
	private int[] previous;
	private DirectedGraphArc<V, A>[] previousArc;
	private final Set<V> settled;

	public TargetDijkstra(DirectedGraph<V, A> graph, V destination)
	{
		this.graph = graph;
		this.destination = destination;

		this.arcWeightFunction = new DefaultArcWeightFunction<>();

		this.settled = new LinkedHashSet<>();
		graph.setNodeIndices();
		this.numNodes = graph.getNumberOfNodes();
	}

	public TargetDijkstra(DirectedGraph<V, A> graph, V destination, AbstractArcWeightFunction<V, A> arcWeightFunction)
	{
		this.graph = graph;
		this.destination = destination;

		this.arcWeightFunction = arcWeightFunction;

		this.settled = new LinkedHashSet<>();
		graph.setNodeIndices();
		this.numNodes = graph.getNumberOfNodes();
	}

	@SuppressWarnings("unchecked")
	public void computeDistances()
	{
		// Initialise distance matrix.
		this.distance = new double[numNodes];

		// Initialise successor arrays;
		this.previous = new int[numNodes];
		this.previousArc = (DirectedGraphArc<V, A>[]) Array.newInstance(DirectedGraphArc.class, numNodes);

		// Initialise queue.
		PriorityQueue<Label> queue = new PriorityQueue<>(new LabelDistanceComparator());

		// Initiliase arrays.
		for (V node : graph.getNodes())
		{
			distance[node.getNodeIndex()] = Double.MAX_VALUE;
		}
		distance[destination.getNodeIndex()] = 0;
		previous[destination.getNodeIndex()] = destination.getNodeIndex();
		queue.add(new Label(destination, 0));

		// Process queue.
		while (settled.size() < numNodes && queue.size() > 0)
		{
			Label label = queue.poll();
			V node = label.getNode();
			if (settled.contains(node))
			{
				continue;
			}
			if (!Activation.getActivation().getActivationFunction().isActiveNode(node))
			{
				continue;
			}

			for (DirectedGraphArc<V, A> arc : graph.getInArcs(node))
			{
				V next = arc.getFrom();
				if (settled.contains(next))
				{
					continue;
				}
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}
				double newDistance = distance[node.getNodeIndex()] + arcWeightFunction.getArcWeight(arc);
				if (newDistance < distance[next.getNodeIndex()])
				{
					distance[next.getNodeIndex()] = newDistance;
					previous[next.getNodeIndex()] = node.getNodeIndex();
					previousArc[next.getNodeIndex()] = arc;

					// Add label to queue.
					queue.add(new Label(next, newDistance));
				}
			}
			settled.add(node);
		}
	}

	public boolean containsPath(V to)
	{
		return distance[to.getNodeIndex()] < Double.MAX_VALUE;
	}

	public double getDistance(V to)
	{
		return distance[to.getNodeIndex()];
	}

	private class Label
	{
		private final V node;
		private final double distance;

		public Label(V node, double distance)
		{
			this.node = node;
			this.distance = distance;
		}

		public V getNode()
		{
			return node;
		}

		public double getDistance()
		{
			return distance;
		}
	}

	public class LabelDistanceComparator implements Comparator<Label>
	{
		@Override
		public int compare(Label o1, Label o2)
		{
			return Double.compare(o1.getDistance(), o2.getDistance());
		}
	}
}
