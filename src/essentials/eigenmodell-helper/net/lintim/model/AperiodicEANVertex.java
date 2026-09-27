package net.lintim.model;

/**
 * Class for representing a vertex in an aperiodic event activity network. Such a vertex will be called aperiodic event
 * in the rest of the program too.
 */
public class AperiodicEANVertex extends PeriodicEANVertex {
	/**
	 * The corresponding periodic event
	 */
	private PeriodicEANVertex periodicEvent;
	/**
	 * The time this event occurs
	 */
	private int time;

	/**
	 * Create an aperiodic EANVertex from the given data
	 * @param id the id of the vertex
	 * @param periodicEvent the corresponding periodic event
	 * @param time the time this event occurs
	 * @param numberOfPassengers the number of passengers using this event in the EAN
	 */
	public AperiodicEANVertex(int id, PeriodicEANVertex periodicEvent, int time, double numberOfPassengers) {
		super(id, periodicEvent.getType(), periodicEvent.getStop(), periodicEvent.getLineId(), numberOfPassengers);
		this.periodicEvent = periodicEvent;
		this.time = time;
	}

	/**
	 * Get the corresponding periodic event
	 * @return the periodic event
	 */
	public PeriodicEANVertex getPeriodicEvent() {
		return periodicEvent;
	}

	/**
	 * Return the time this event occurs
	 * @return the time
	 */
	public int getTime() {
		return time;
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof AperiodicEANVertex)){
			return false;
		}
		AperiodicEANVertex otherVertex = (AperiodicEANVertex) other;
		return otherVertex.getPeriodicEvent().equals(this.getPeriodicEvent()) && otherVertex.getTime() == this.getTime();
	}

	/**
	 * Set a new time for this event
	 * @param time the new time
	 */
	public void setTime(int time){
		this.time = time;
	}

	public String getCsvRepresentation(){
		return id + "; " + periodicEvent.getId() + "; \"" + type + "\"; " + time + "; " + numberOfPassengers + "; " +
				stop.getId();
	}
}
