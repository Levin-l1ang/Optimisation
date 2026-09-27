package net.lintim.model;

/**
 * Class for representing a vertex in a public transportation network.
 */
public class PTNVertex extends Vertex{
	/**
	 * The name of the vertex
	 */
	private String name;

	/**
	 * Create a ptn vertex with the given data.
	 * @param id the id of the vertex
	 * @param name the name of the vertex
	 */
	public PTNVertex(int id, String name) {
		super(id);
		this.name = name;
	}

	/**
	 * Create a copy of the other vertex. The created vertex has the same name and id. Incoming and outgoing edges
	 * are not copied!
	 * @param other the vertex to copy
	 */
	public PTNVertex(PTNVertex other) {
		this(other.getId(), other.getName());
	}

	/**
	 * Return the name of the vertex
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof PTNVertex)){
			return false;
		}
		PTNVertex otherVertex = (PTNVertex) other;
		return otherVertex.getName().equals(this.getName());
	}

}
