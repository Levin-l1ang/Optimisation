package graph.structures.digraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple class that can be used to model directed graphs, where arbitrary types
 * of data are associated with the nodes and arcs of the graph. Note that this
 * implementation of an arc allows multiple copies of the same arc. As such, it
 * can not be assumed that graphs stored by instances of this class are simple;
 * they can be multigraphs.
 * 
 * It is assumed that the data type associated with the nodes has a consistent
 * implementation of hashCode() and equals().
 * 
 * @author Paul Bouman
 *
 * @param <V> the type of data associated with nodes in this graph
 * @param <A> the type of data associated with arcs in this graph
 */

public class DirectedGraph<V extends DirectedGraphNodeIndex, A>
{
	protected final List<V> nodes;
	protected final LinkedHashSet<DirectedGraphArc<V, A>> arcs;
	protected final Map<V, LinkedHashSet<DirectedGraphArc<V, A>>> outArcs;
	protected final Map<V, LinkedHashSet<DirectedGraphArc<V, A>>> inArcs;

	/**
	 * Creates an empty graph with no nodes or arcs.
	 */
	public DirectedGraph()
	{
		this.nodes = new ArrayList<>();
		this.arcs = new LinkedHashSet<>();
		this.outArcs = new LinkedHashMap<>();
		this.inArcs = new LinkedHashMap<>();
	}

	/**
	 * Add a new node to this graph
	 * 
	 * @param node the data associated with the node that is added
	 * @throws IllegalArgumentException if the node is already in the graph or is
	 *                                  null
	 */
	public void addNode(V node) throws IllegalArgumentException
	{
		if (node == null)
		{
			throw new IllegalArgumentException("Unable to add null to the graph");
		}
		else
			if (inArcs.containsKey(node))
			{
				throw new IllegalArgumentException("Unable to add the same node twice to the same graph");
			}
			else
			{
				nodes.add(node);
				inArcs.put(node, new LinkedHashSet<>());
				outArcs.put(node, new LinkedHashSet<>());
			}
	}

	/**
	 * Adds an arc to this graph.
	 * 
	 * @param from    the origin node of the arc to be added
	 * @param to      the destination of the arc to be added
	 * @param arcData the data associated with the arc
	 * @param weight  the weight of the arc
	 * @throws IllegalArgumentException if one of the end points is not in the graph
	 */
	public DirectedGraphArc<V, A> addArc(V from, V to, A arcData, double cost) throws IllegalArgumentException
	{
		if (!inArcs.containsKey(to) || !outArcs.containsKey(from))
		{
			throw new IllegalArgumentException("Unable to add arcs between nodes not in the graph");
		}
		DirectedGraphArc<V, A> a = new DirectedGraphArc<>(from, to, arcData, cost);
		outArcs.get(from).add(a);
		inArcs.get(to).add(a);
		arcs.add(a);
		return a;
	}

	public void addArc(V from, V to, A arcData, double[] costs, double[] duals) throws IllegalArgumentException
	{
		if (!inArcs.containsKey(to) || !outArcs.containsKey(from))
		{
			throw new IllegalArgumentException("Unable to add arcs between nodes not in the graph");
		}
		DirectedGraphArc<V, A> a = new DirectedGraphArc<>(from, to, arcData, costs, duals);
		outArcs.get(from).add(a);
		inArcs.get(to).add(a);
		arcs.add(a);
	}

	public void removeNode(V node) throws IllegalArgumentException
	{
		if (node == null)
		{
			throw new IllegalArgumentException("Unable to remove null from the graph");
		}
		else
			if (!nodes.contains(node))
			{
				throw new IllegalArgumentException("Unable to remove node that is not in the graph");
			}
			else
			{
				nodes.remove(node);
				arcs.removeAll(inArcs.get(node));
				arcs.removeAll(outArcs.get(node));
				for (DirectedGraphArc<V, A> arc : inArcs.get(node))
				{
					outArcs.get(arc.getFrom()).remove(arc);
				}
				inArcs.remove(node);
				for (DirectedGraphArc<V, A> arc : outArcs.get(node))
				{
					inArcs.get(arc.getTo()).remove(arc);
				}
				outArcs.remove(node);
			}
	}

	public void sortNodes(Comparator<V> comparator)
	{
		nodes.sort(comparator);
	}

	public void removeArc(DirectedGraphArc<V, A> arc) throws IllegalArgumentException
	{
		if (arc == null)
		{
			throw new IllegalArgumentException("Unable to remove null from the graph");
		}
		else
			if (!arcs.contains(arc))
			{
				throw new IllegalArgumentException("Unable to remove arc that is not in the graph");
			}
			else
			{
				arcs.remove(arc);
				outArcs.get(arc.getFrom()).remove(arc);
				inArcs.get(arc.getTo()).remove(arc);
			}
	}

	/**
	 * Gives a list of all nodes currently in the graph
	 * 
	 * @return the nodes in the graph
	 */
	public List<V> getNodes()
	{
		return Collections.unmodifiableList(nodes);
	}

	/**
	 * Gives a list of all arcs currently in the graph
	 * 
	 * @return the arcs in the graph
	 */
	public Set<DirectedGraphArc<V, A>> getArcs()
	{
		return Collections.unmodifiableSet(arcs);
	}

	/**
	 * Gives all the arcs that leave a particular node in the graph. Note that this
	 * list may be empty if no arcs leave this node.
	 * 
	 * @param node the node for which we want the leaving arcs
	 * @return a list of arcs leaving the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public Set<DirectedGraphArc<V, A>> getOutArcs(V node) throws IllegalArgumentException
	{
		if (!outArcs.containsKey(node))
		{
			throw new IllegalArgumentException("Unable to provide out-arcs for a node that is not in the graph");
		}
		return Collections.unmodifiableSet(outArcs.get(node));
	}

	/**
	 * Gives all the arcs that enter a particular node in the graph. Note that this
	 * list may be empty if no arcs enter this node.
	 * 
	 * @param node the node for which we want the entering arcs
	 * @return a list of arcs entering the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public Set<DirectedGraphArc<V, A>> getInArcs(V node) throws IllegalArgumentException
	{
		if (!inArcs.containsKey(node))
		{
			throw new IllegalArgumentException("Unable to provide in-arcs for a node that is not in the graph");
		}
		return Collections.unmodifiableSet(inArcs.get(node));
	}

	/**
	 * The total number of nodes in this graph
	 * 
	 * @return the number of nodes in the graph
	 */
	public int getNumberOfNodes()
	{
		return nodes.size();
	}

	/**
	 * The total number of arcs in this graph
	 * 
	 * @return the number of arcs in the graph
	 */
	public int getNumberOfArcs()
	{
		return arcs.size();
	}

	/**
	 * Gives the in-degree of a node in the graph.
	 * 
	 * @param node the node for which we want the in-degree
	 * @return the in-degree of the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public int getInDegree(V node) throws IllegalArgumentException
	{
		return getInArcs(node).size();
	}

	/**
	 * Gives the out-degree of a node in the graph
	 * 
	 * @param node the node for which we want the out-degree
	 * @return the out-degree of the node
	 * @throws IllegalArgumentException if the node is not in the graph
	 */
	public int getOutDegree(V node) throws IllegalArgumentException
	{
		return getOutArcs(node).size();
	}

	/**
	 * Iterate over all nodes to set the index. This is an auxiliary function used
	 * in the shortest path algorithms
	 */
	public void setNodeIndices()
	{
		for (int i = 0; i < nodes.size(); i++)
		{
			nodes.get(i).setIndex(i);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 17;
		result = prime * result + ((arcs == null) ? 0 : arcs.hashCode());
		result = prime * result + ((nodes == null) ? 0 : nodes.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DirectedGraph<?, ?> other = (DirectedGraph<?, ?>) obj;
		if (arcs == null)
		{
			if (other.arcs != null)
				return false;
		}
		else
			if (!arcs.equals(other.arcs))
				return false;
		if (nodes == null)
		{
			if (other.nodes != null)
				return false;
		}
		else
			if (!nodes.equals(other.nodes))
				return false;
		return true;
	}

	@Override
	public String toString()
	{
		return "DirectedGraph [nodes=" + nodes + ", arcs=" + arcs + "]";
	}
}
