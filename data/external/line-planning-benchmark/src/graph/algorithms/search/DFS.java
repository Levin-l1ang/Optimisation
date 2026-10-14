package graph.algorithms.search;

import java.util.ArrayList;
import java.util.List;

import graph.algorithms.shortestPath.TargetDijkstra;
import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.Activation;

public class DFS<V extends DirectedGraphNodeIndex, A>
{
	private List<List<DirectedGraphArc<V, A>>> paths;
	private TargetDijkstra<V, A> targetDijkstra;
	private DirectedGraph<V, A> graph;
	private V destination;
	private double threshold;
	private AbstractArcWeightFunction<V, A> arcWeightFunction;

	private boolean[] visited;

	public List<List<DirectedGraphArc<V, A>>> applyDFS(DirectedGraph<V, A> graph, V origin, V destination,
			double threshold, AbstractArcWeightFunction<V, A> arcWeightFunction)
	{
		// Settings.
		this.graph = graph;
		this.destination = destination;
		this.threshold = threshold;
		this.arcWeightFunction = arcWeightFunction;

		// Initialise a list of paths.
		paths = new ArrayList<>();

		// Use a target-version of Dijkstra to compute completion bounds.
		targetDijkstra = new TargetDijkstra<>(graph, destination, arcWeightFunction);
		targetDijkstra.computeDistances();

		// Initialise array of visited nodes.
		visited = new boolean[graph.getNumberOfNodes()];
		for (V node : graph.getNodes())
		{
			visited[node.getNodeIndex()] = false;
		}

		// Perform recursive DFS starting at the origin.
		DFSLabel<V, A> originLabel = new DFSLabel<>(null, null, 0);
		visited[origin.getNodeIndex()] = true;
		recursiveSearch(origin, originLabel);

		// Return all found paths.
		return paths;
	}

	private void recursiveSearch(V currentNode, DFSLabel<V, A> label)
	{
		if (currentNode.equals(destination))
		{
			paths.add(backtrackPath(label));
		}
		else
		{
			for (DirectedGraphArc<V, A> arc : graph.getOutArcs(currentNode))
			{
				// Avoid nodes that are already visited.
				V to = arc.getTo();
				if (visited[to.getNodeIndex()])
				{
					continue;
				}
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}

				// Avoid paths leading to too high completion bounds.
				// Check cost criterion.
				double cost = label.getCost() + arcWeightFunction.getArcWeight(arc);
				if (cost > threshold)
				{
					continue;
				}

				// Check cost completion bound.
				if (cost + targetDijkstra.getDistance(to) > threshold)
				{
					continue;
				}

				// Call recursion with updated label.
				DFSLabel<V, A> expandedLabel = new DFSLabel<>(label, arc, cost);
				visited[to.getNodeIndex()] = true;
				recursiveSearch(to, expandedLabel);
				visited[to.getNodeIndex()] = false;
			}
		}
	}

	private List<DirectedGraphArc<V, A>> backtrackPath(DFSLabel<V, A> label)
	{
		List<DirectedGraphArc<V, A>> path = new ArrayList<>();
		DFSLabel<V, A> currentLabel = label;
		while (true)
		{
			path.add(0, currentLabel.getArc());
			currentLabel = currentLabel.getPreviousLabel();
			if (currentLabel.getArc() == null)
			{
				break;
			}
		}
		return path;
	}
}