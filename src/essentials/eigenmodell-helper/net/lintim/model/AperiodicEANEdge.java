package net.lintim.model;

/**
 * Containing information for an activity in an aperiodic event activity network
 */
public class AperiodicEANEdge extends PeriodicEANEdge {
	/**
	 * The corresponding periodic activity to the aperiodic activity
	 */
	private PeriodicEANEdge periodicActivity;

	/**
	 * Create a periodic activity with the given data. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the activity
	 * @param source the source of the activity
	 * @param target the target of the activity
	 * @param type the type of the activity. Possible types are "drive", "wait" and "change"
	 * @param lowerBound the lower bound of the activity, i.e., the minimal time this activity is allowed to endure in
	 *                    a feasible timetable
	 * @param numberOfPassengers the number of passengers using this activity in the network
	 * @param periodicActivity the corresponding periodic activity
	 */
	public AperiodicEANEdge(int id, AperiodicEANVertex source, AperiodicEANVertex target, String type, int lowerBound,
	                        int upperBound, double numberOfPassengers, PeriodicEANEdge periodicActivity) {
		super(id, source, target, type, lowerBound, upperBound, numberOfPassengers, false);
		this.periodicActivity = periodicActivity;
		addToVertices();
	}

	/**
	 * Create a periodic activity with the given data. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the activity
	 * @param source the source of the activity
	 * @param target the target of the activity
	 * @param type the type of the activity. Possible types are "drive", "wait" and "change"
	 * @param lowerBound the lower bound of the activity, i.e., the minimal time this activity is allowed to endure in
	 *                    a feasible timetable
	 * @param numberOfPassengers the number of passengers using this activity in the network
	 * @param periodicActivity the corresponding periodic activity
	 * @param addToVertices whether or not to add this edge to the source and target vertex as outgoing and incoming
	 *                       edge, respectively. You want to set this to false if the constructor is called from a
	 *                       subclass and add the call to the constructor of the origin class. Otherwise, the vertex
	 *                       tries to add a not fully constructed edge and the hashcode function will crash!
	 */
	public AperiodicEANEdge(int id, AperiodicEANVertex source, AperiodicEANVertex target, String type, int lowerBound,
	                        int upperBound, double numberOfPassengers, PeriodicEANEdge periodicActivity, boolean
			                        addToVertices) {
		super(id, source, target, type, lowerBound, upperBound, numberOfPassengers, false);
		this.periodicActivity = periodicActivity;
		addToVertices();
		if(addToVertices){
			addToVertices();
		}
	}

	@Override
	public AperiodicEANVertex getSource() {
		return (AperiodicEANVertex) source;
	}

	@Override
	public AperiodicEANVertex getTarget() {
		return (AperiodicEANVertex) target;
	}

	/**
	 * Get the corresponding periodic activity
	 * @return the periodic activity
	 */
	public PeriodicEANEdge getPeriodicActivity() {
		return periodicActivity;
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof AperiodicEANEdge)){
			return false;
		}
		AperiodicEANEdge otherEdge = (AperiodicEANEdge) other;
		return otherEdge.getPeriodicActivity().equals(this.getPeriodicActivity());
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + super.hashCode();
		result = result * 31 + periodicActivity.hashCode();
		return result;
	}

	public String getCsvRepresentation(){
		return id + "; " + periodicActivity.getId() + "; \"" + type + "\"; " + source.getId() + "; " + target.getId() +
				"; " + lowerBound + "; " + upperBound + "; " + numberOfPassengers;
	}
}
