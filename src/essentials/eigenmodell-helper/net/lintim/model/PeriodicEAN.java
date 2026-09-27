package net.lintim.model;

import net.lintim.util.Config;
import net.lintim.util.CsvReader;
import net.lintim.util.PeriodicEANEdgeBuilder;
import net.lintim.util.PeriodicEANVertexBuilder;

/**
 * Class representing a periodic Event Activity Network(EAN) containing {@link PeriodicEANVertex} as vertices and
 * {@link PeriodicEANEdge} as edges.
 */
public class PeriodicEAN extends Network{
	/**
	 * The corresponding PTN
	 */
	private final PTN ptn;

	/**
	 * Read the periodic EAN from the given data folder. This periodic EAN has to be consistent with the given PTN
	 * @param name the name of the network
	 * @param ptn a PTN consistent with the data to read
	 */
	public PeriodicEAN(String name, PTN ptn){
		super(name);
		this.ptn = ptn;
		readEAN();
	}

	/**
	 * Read the periodic EAN and the PTN from the given data folder. The data has to be consistent
	 * @param name the name of the network
	 */
	public PeriodicEAN(String name){
		this(name, new PTN("PTN"));
	}

	/**
	 * Read the EAN from the given datafolder. The folder should contain "Events-periodic.giv" and
	 * "Activities-periodic.giv", consistent with the PTN
	 */
	private void readEAN(){
		readEvents(Config.getStringValue("default_events_periodic_file"));
		readActivities(Config.getStringValue("default_activities_periodic_file"));
	}

	/**
	 * Read the events from the given file. The file has to be in the LinTim-format
	 * @param dataFileName the name of the data file
	 */
	private void readEvents(String dataFileName){
		CsvReader.readCsv(dataFileName, new PeriodicEANVertexBuilder(this));
	}

	/**
	 * Read the activities from the given file. The file has to be in the LinTim-format
	 * @param dataFileName the name of the data file
	 */
	private void readActivities(String dataFileName){
		CsvReader.readCsv(dataFileName, new PeriodicEANEdgeBuilder(this));
	}

	/**
	 * Add the given vertex to the network
	 * @param vertex the vertex to add
	 */
	public void addVertex(PeriodicEANVertex vertex){
		vertexMap.put(vertex.getId(), vertex);
	}

	/**
	 * Add the edge to the ean
	 * @param edge the edge to add
	 */
	public void addEdge(PeriodicEANEdge edge){
		edgeMap.put(edge.getId(), edge);
	}

	/**
	 * Return the underlying ptn of this network
	 * @return the ptn
	 */
	public PTN getPTN(){
		return ptn;
	}

	@Override
	public PeriodicEANVertex getVertex(int vertexId){
		return (PeriodicEANVertex) vertexMap.get(vertexId);
	}

	@Override
	public PeriodicEANEdge getEdge(int edgeId){
		return (PeriodicEANEdge) edgeMap.get(edgeId);
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof PeriodicEAN)){
			return false;
		}
		PeriodicEAN otherEAN = (PeriodicEAN) other;
		return this.getPTN().equals(otherEAN.getPTN());
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + super.hashCode();
		result = result * 31 + ptn.hashCode();
		return result;
	}
}
