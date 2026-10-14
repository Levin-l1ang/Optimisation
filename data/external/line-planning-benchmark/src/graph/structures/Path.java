package graph.structures;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;

public class Path<V extends DirectedGraphNodeIndex, A>
{
	private LinkedList<DirectedGraphArc<V, A>> arcs;

	private double weight;

	public Path(List<DirectedGraphArc<V, A>> arcs, double weight)
	{
		this.arcs = new LinkedList<>(arcs);
		this.weight = weight;
	}

	public List<DirectedGraphArc<V, A>> getArcs()
	{
		return new ArrayList<>(arcs);
	}

	public void addWeight(double value)
	{
		this.weight += value;
	}

	public int getNumArcs()
	{
		return arcs.size();
	}

	public double getWeight()
	{
		return weight;
	}
}
