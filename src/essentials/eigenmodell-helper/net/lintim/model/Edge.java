package net.lintim.model;

/**
 * Containing basic information for a directed edge in a graph. Is used to derive all other edge classes.
 */
public class Edge {
	/**
	 * The id of the edge. Should be unique for each class of edges.
	 */
	protected int id;
	/**
	 * The source of this directed edge
	 */
	protected Vertex source;
	/**
	 * The target of this directed edge
	 */
	protected Vertex target;

	/**
	 * Create a new edge from the given information. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the new edge
	 * @param source the source of the new edge
	 * @param target the target of the new edge
	 */
	public Edge(int id, Vertex source, Vertex target) {
		this.id = id;
		this.source = source;
		this.target = target;
		addToVertices();
	}

	/**
	 * Create a new edge from the given information. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the new edge
	 * @param source the source of the new edge
	 * @param target the target of the new edge
	 * @param addToVertices whether or not to add this edge to the source and target vertex as outgoing and incoming
	 *                       edge, respectively. You want to set this to false if the constructor is called from a
	 *                       subclass and add the call to the constructor of the origin class. Otherwise, the vertex
	 *                       tries to add a not fully constructed edge and the hashcode function will crash!
	 */
	public Edge(int id, Vertex source, Vertex target, boolean addToVertices) {
		this.id = id;
		this.source = source;
		this.target = target;
		if(addToVertices){
			addToVertices();
		}
	}

	/**
	 * Add this edge to the source and target vertex as outgoing and incoming edge, respectively
	 */
	protected void addToVertices(){
		source.addOutgoingEdge(this);
		target.addIncomingEdge(this);
	}

	/**
	 * Get the source of the edge
	 * @return the source
	 */
	public Vertex getSource() {
		return source;
	}

	/**
	 * Get the target of the edge
	 * @return the target
	 */
	public Vertex getTarget() {
		return target;
	}

	/**
	 * Get the id of the edge
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	@Override
	public String toString() {
		return "(" + id + "," + source + "," + target + ")";
	}

	/**
	 * Get a key to compare edges. These is either the id or the starting time, depending on the edge class
	 * @return key for comparing edges
	 */
	public int getKey() {
		return id;
	}

	@Override
	public boolean equals(Object other){
		if(other == null){
			return false;
		}
		if(other == this){
			return true;
		}
		if(!(other instanceof Edge)){
			return false;
		}
		Edge otherEdge = (Edge) other;
		return otherEdge.getId() == this.getId() && otherEdge.getSource().equals(this.getSource()) && otherEdge.getTarget
				().equals(this.getTarget());
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + id;
		result = result * 31 + source.hashCode();
		result = result * 31 + target.hashCode();
		return result;
	}

	/**
	 * Get a String in the csv format representing this edge. The string is formatted according to the corresponding
	 * LinTim-files
	 * @return a representation of this edge
	 */
	public String getCsvRepresentation(){
		return id + "; " + source.getId() + "; " + target.getId();
	}
}
