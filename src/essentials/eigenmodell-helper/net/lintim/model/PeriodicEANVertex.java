package net.lintim.model;

/**
 * Class for representing a vertex in a periodic event activity network. Such a vertex will be called periodic event
 * in the rest of the program too.
 */
public class PeriodicEANVertex extends Vertex {
	/**
	 * The type of the vertex. Possible types are "departure" and "arrival"
	 */
	protected String type;
	/**
	 * The corresponding stop in the PTN
	 */
	protected PTNVertex stop;
	/**
	 * The id of the corresponding line
	 */
	protected int lineId;
	/**
	 * The number of passengers using this vertex in the event activity network
	 */
	protected double numberOfPassengers;

	/**
	 * Create a periodic event from the given data
	 * @param id the id of the vertex
	 * @param type the type of the event. Possible types are "departure" and "arrival"
	 * @param stop the corresponding stop in the PTN
	 * @param lineId the id of the corresponding line
	 * @param numberOfPassengers the number of passengers using this vertex in the event activity network
	 */
	public PeriodicEANVertex(int id, String type, PTNVertex stop, int lineId, double numberOfPassengers) {
		super(id);
		this.type = type;
		this.stop = stop;
		this.lineId = lineId;
		this.numberOfPassengers = numberOfPassengers;
	}

	/**
	 * Return the type of the event
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * Return the corresponding stop in the PTN
	 * @return the stop
	 */
	public PTNVertex getStop() {
		return stop;
	}

	/**
	 * Return the corresponding line id
	 * @return the line id
	 */
	public int getLineId() {
		return lineId;
	}

	/**
	 * Return the number of passengers using this event in the EAN
	 * @return the number of passengers
	 */
	public double getNumberOfPassengers() {
		return numberOfPassengers;
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof PeriodicEANVertex)){
			return false;
		}
		PeriodicEANVertex otherVertex = (PeriodicEANVertex) other;
		return otherVertex.getType().equals(this.getType()) && otherVertex.getStop().equals(this.getStop()) &&
				otherVertex.getLineId() == this.getLineId() && otherVertex.getNumberOfPassengers() == this
				.getNumberOfPassengers();

	}
}
