package net.lintim.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Class for representing a vertex in a graph. Is used to derive all other vertex classes.
 */
public class Vertex {
	/**
	 * The id of the vertex. Should be unique for a network
	 */
	protected int id;
	/**
	 * The set of incoming edges
	 */
	protected Set<Edge> incomingEdges;
	/**
	 * The set of outgoing edges
	 */
	protected Set<Edge> outgoingEdges;

	/**
	 * Create a vertex
	 * @param id the new id of the vertex. Should be unique inside the network
	 */
	public Vertex(int id) {
		this.id = id;
		this.incomingEdges = new HashSet<>();
		this.outgoingEdges = new HashSet<>();

	}

	/**
	 * Create a vertex with id 0
	 */
	public Vertex() {
		this(0);
	}

	/**
	 * Add an edge to the incoming edges
	 * @param newEdge the edge to add
	 * @return whether the edge was already present
	 */
	public boolean addIncomingEdge(Edge newEdge) {
		return incomingEdges.add(newEdge);
	}

	/**
	 * Add an edge to the outgoing edges
	 * @param newEdge the edge to add
	 * @return whether the edge was already present
	 */
	public boolean addOutgoingEdge(Edge newEdge) {
		return outgoingEdges.add(newEdge);
	}

	/**
	 * Return the set of incoming edges
	 * @return the incoming edges
	 */
	public Set<Edge> getIncomingEdges() {
		return incomingEdges;
	}

	/**
	 * Return the set of outgoing edges
	 * @return the outgoing edges
	 */
	public Set<Edge> getOutgoingEdges() {
		return outgoingEdges;
	}

	/**
	 * Return the id of the vertex
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	@Override
	public String toString() {
		return "" + id;
	}

	@Override
	public boolean equals(Object other){
		if(other == null){
			return false;
		}
		if(other == this){
			return true;
		}
		if(!(other instanceof Vertex)){
			return false;
		}
		Vertex otherVertex = (Vertex) other;
		return otherVertex.getId() == this.getId();
	}

	public String getCsvRepresentation(){
		return "" + id;
	}
}
