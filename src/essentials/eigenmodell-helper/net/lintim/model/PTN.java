package net.lintim.model;

import net.lintim.util.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a public transportation network, containing {@link PTNVertex} as vertices and {@link PTNEdge} as
 * edges.
 */
public class PTN extends Network{
	/**
	 * The OD matrix belonging to the PTN
	 */
	private final OD odMatrix;

	/**
	 * Map of all edges in the PTN. The edges are sorted by their stop ids
	 */
	private final Map<Pair<Integer>, PTNEdge> edgesByStopIds;

	/**
	 * Create an empty PTN and fill it with the data from the given data folder. The folder should contain consistent
	 * "Stop.giv", "Edge.giv" and "Load.giv". Furthermore the OD matrix is read from the data folder. For information
	 * regarding the needed files for that, see {@link OD#OD(String)}.
	 * @param name the name of the PTN
	 */
	public PTN(String name){
		super(name);
		edgesByStopIds = new HashMap<>();
		readStops(Config.getStringValue("default_stops_file"));
		readEdges(Config.getStringValue("default_edges_file"));
		readLoad(Config.getStringValue("default_loads_file"));
		odMatrix = new OD(Config.getStringValue("default_od_file"));
	}

	/**
	 * Read the stops from the given file. Has to be in the LinTim-format.
	 * @param filename the name of the file
	 */
	private void readStops(String filename){
		CsvReader.readCsv(filename, new PTNVertexBuilder(this));
	}

	/**
	 * Read the edges from the given file. Has to be in the LinTim-format.
	 * @param filename the name of the file
	 */
	private void readEdges(String filename){
		CsvReader.readCsv(filename, new PTNEdgeBuilder(this));
	}

	/**
	 * Read the loads from the given file. Has to be in the LinTim-format.
	 * @param filename the name of the file
	 */
	private void readLoad(String filename){
		CsvReader.readCsv(filename, new PTNLoadProcessor(this));
	}

	/**
	 * Add the given vertex to the ptn
	 * @param vertex the vertex to add
	 */
	public void addVertex(PTNVertex vertex){
		vertexMap.put(vertex.getId(), vertex);
	}

	/**
	 * Add the given edge to the ptn
	 * @param edge the edge to add
	 */
	public void addEdge(PTNEdge edge){
		edgeMap.put(edge.getId(), edge);
		edgesByStopIds.put(new Pair<>(edge.getSource().getId(), edge.getTarget().getId()), edge);
	}

	/**
	 * Returns the edge belonging to the two given ids or null if there is none. An edge belongs to two ids if the
	 * source and target id of the edge coincide with the ids or vice versa. Therefore the order of the given ids does
	 * not matter.
	 * @param firstId the first id
	 * @param secondId the second id
	 * @return the found edge or null if there is none
	 */
	@SuppressWarnings("unchecked")
	public PTNEdge getEdge(int firstId, int secondId){
		//The pair is symmetric so we only have to look for one of the two possible pairs
		return edgesByStopIds.get(new Pair(firstId, secondId));
	}

	@Override
	public PTNVertex getVertex(int vertexId){
		return (PTNVertex) vertexMap.get(vertexId);
	}

	@Override
	public PTNEdge getEdge(int edgeId){
		return (PTNEdge) edgeMap.get(edgeId);
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof PTN)){
			return false;
		}
		PTN otherPTN = (PTN) other;
		return otherPTN.getOdMatrix().equals(this.getOdMatrix());
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + super.hashCode();
		result = result * 31 + odMatrix.hashCode();
		return result;
	}

	/**
	 * Return the od matrix of the ptn
	 * @return the od matrix
	 */
	public OD getOdMatrix(){
		return odMatrix;
	}
}
