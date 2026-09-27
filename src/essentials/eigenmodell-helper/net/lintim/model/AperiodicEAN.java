package net.lintim.model;

import net.lintim.util.AperiodicEANEdgeBuilder;
import net.lintim.util.AperiodicEANVertexBuilder;
import net.lintim.util.Config;
import net.lintim.util.CsvReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Class representing an aperiodic Event Activity Network(EAN). Contains {@link AperiodicEANVertex} as vertices and
 * {@link AperiodicEANEdge} as edges.
 */
public class AperiodicEAN extends Network{
	/**
	 * The corresponding periodic EAN.
	 */
	private final PeriodicEAN periodicEAN;

	/**
	 * Read the aperiodic EAN and all other needed information out of the given data folder. The location of the
	 * input files is read from the config("default_events_expanded_file" and "default_activities_expanded_file"), so
	 * be sure to read the config file first. The files have to be in the corresponding LinTim style.
	 * @param name the name of the network
	 * @param periodicEAN the corresponding periodic EAN
	 */
	public AperiodicEAN(String name, PeriodicEAN periodicEAN){
		super(name);
		this.periodicEAN = periodicEAN;
		readEAN();
	}

	/**
	 * Read the aperiodic EAN and all other needed information out of the given data folder. The location of the
	 * input files is read from the config("default_events_expanded_file" and "default_activities_expanded_file"), so
	 * be sure to read the config file first. The files have to be in the corresponding LinTim style. Furthermore the
	 * data for the underlying networks is created from scratch, see {@link PeriodicEAN#PeriodicEAN(String)}.
	 * @param name the name of the network
	 */
	public AperiodicEAN(String name){
		this(name, new PeriodicEAN("PeriodicEAN"));
	}

	/**
	 * Read the EAN from the files given in the config ("default_events_expanded_file" and
	 * "default_activities_expanded_file") so be sure to read the config first. The files have to be in the
	 * corresponding LinTim style.
	 */
	private void readEAN(){
		readEvents(Config.getStringValue("default_events_expanded_file"));
		readActivities(Config.getStringValue("default_activities_expanded_file"));
	}

	/**
	 * Read the events from the given file. Has to be in the LinTim-format for aperiodic event files
	 * @param dataFileName the name of the data file
	 */
	private void readEvents(String dataFileName){
		CsvReader.readCsv(dataFileName, new AperiodicEANVertexBuilder(this));
	}

	/**
	 * Read the activities from the given file. Has to be in the LinTim-format for aperiodic activity files
	 * @param dataFileName the name of the data file
	 */
	private void readActivities(String dataFileName){
		CsvReader.readCsv(dataFileName, new AperiodicEANEdgeBuilder(this));
	}

	/**
	 * Return the underlying periodic ean
	 * @return the periodic ean
	 */
	public PeriodicEAN getPeriodicEAN(){
		return periodicEAN;
	}

	/**
	 * Add the given vertex to the aperiodic ean
	 * @param vertex the vertex to add
	 */
	public void addVertex(AperiodicEANVertex vertex){
		vertexMap.put(vertex.getId(), vertex);
	}

	/**
	 * Add the given edge to the aperiodic ean
	 * @param edge the edge to add
	 */
	public void addEdge(AperiodicEANEdge edge){
		edgeMap.put(edge.getId(), edge);
	}

	@Override
	public AperiodicEANVertex getVertex(int vertexId){
		return (AperiodicEANVertex) vertexMap.get(vertexId);
	}

	@Override
	public AperiodicEANEdge getEdge(int edgeId){
		return (AperiodicEANEdge) edgeMap.get(edgeId);
	}

	/**
	 * Output the given solution to the event and activity files stated in the Config file
	 * (default_events_expanded_file and default_activities_expanded_file). The config must be read before calling this
	 * function
	 * @throws IOException if the files can not be created/written to
	 */
	public void output() throws IOException {
		//First output the events
		BufferedWriter eventWriter = new BufferedWriter(new FileWriter(new File(Config.getStringValue("default_events_expanded_file"))));
		eventWriter.write("# event-id; periodic-id; type; time; passengers; station-id");
		eventWriter.newLine();
		for(int index = 1; index <= getVertices().size(); index++){
			eventWriter.write(getVertex(index).getCsvRepresentation());
			eventWriter.newLine();
		}
		eventWriter.close();
		BufferedWriter activityWriter = new BufferedWriter(new FileWriter(new File(Config.getStringValue("default_activities_expanded_file"))));
		activityWriter.write("# activity-id; periodic-id; type; tail-event-id; head-event-id; lower-bound; passengers");
		activityWriter.newLine();
		for(int index = 1; index <= getEdges().size(); index++){
			activityWriter.write(getEdge(index).getCsvRepresentation());
			activityWriter.newLine();
		}
		activityWriter.close();
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof AperiodicEAN)){
			return false;
		}
		AperiodicEAN otherEAN = (AperiodicEAN) other;
		return this.getPeriodicEAN().equals(otherEAN.getPeriodicEAN());
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + super.hashCode();
		result = result * 31 + periodicEAN.hashCode();
		return result;
	}
}
