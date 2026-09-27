package net.lintim.model;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a network or a graph. Used to derive all other graph classes for this program.
 * Networks will be directed, except the public transportation network
 */
public class Network {
	/**
	 * The name of the network
	 */
	protected String name;
	/**
	 * The vertices of the network, stored by their ids
	 */
	protected Map<Integer, Vertex> vertexMap;
	/**
	 * The edges of the network, stored by their ids
	 */
	protected Map<Integer, Edge> edgeMap;
	/**
	 * The maximal edge id in this network
	 */
	protected int maxEdgeId;

	/**
	 * Create a new network without vertices and edges with the given name
	 * @param name the new name of the network
	 */
	public Network(String name){
		this.name = name;
		this.vertexMap = new HashMap<>();
		this.edgeMap = new HashMap<>();
		this.maxEdgeId = 0;
	}

	/**
	 * Return the vertex with the given id or null, if there is none
	 * @param vertexId the id of the vertex to return
	 * @return the vertex with the given id or null, if there is none
	 */
	public Vertex getVertex(int vertexId){
		return vertexMap.get(vertexId);
	}

	/**
	 * Return the edge with the given id or null, if there is none
	 * @param edgeId the id of the edge to return
	 * @return the edge with the given id or null, if there is none
	 */
	public Edge getEdge(int edgeId){
		return edgeMap.get(edgeId);
	}

	/**
	 * Return the number of edges in the network
	 * @return the number of edges
	 */
	public int numberOfEdges(){
		return edgeMap.size();
	}

	/**
	 * Return the vertices of the network
	 * @return the vertices
	 */
	public Collection<Vertex> getVertices(){
		return vertexMap.values();
	}

	/**
	 * Return the edges of the network
	 * @return the edges
	 */
	public Collection<Edge> getEdges(){
		return edgeMap.values();
	}

	@Override
	public String toString(){
		StringBuilder returnString = new StringBuilder();
		returnString.append("Name: ").append(name).append("\nVertices:");
		for(Vertex vertex : vertexMap.values()){
			returnString.append(vertex).append(" ");
		}
		returnString.append("\nEdges: ");
		for(Edge edge : edgeMap.values()){
			returnString.append(edge).append(" ");
		}
		return returnString.toString();
	}

	/**
	 * Return the name of the network
	 * @return the name
	 */
	public String getName(){
		return name;
	}

	@Override
	public boolean equals(Object other){
		if(other == null){
			return false;
		}
		if(other == this){
			return true;
		}
		if(!(other instanceof Network)){
			return false;
		}
		Network otherNetwork = (Network) other;
		if(otherNetwork.getVertices().size() != this.getVertices().size()){
			return false;
		}
		if(otherNetwork.getEdges().size() != this.getEdges().size()){
			return false;
		}
		if(!otherNetwork.getName().equals(this.getName())){
			return false;
		}
		//Now check all vertices and after that all edges
		for(Vertex vertex : vertexMap.values()){
			Vertex otherVertex = otherNetwork.getVertex(vertex.getId());
			if(!vertex.equals(otherVertex)){
				return false;
			}
		}
		for(Edge edge : edgeMap.values()){
			Edge otherEdge = otherNetwork.getEdge(edge.getId());
			if(!edge.equals(otherEdge)){
				return false;
			}
		}
		return true;
	}
}
