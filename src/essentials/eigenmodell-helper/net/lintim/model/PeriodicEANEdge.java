package net.lintim.model;

/**
 * Class for representing an activity in a periodic event activity network
 */
public class PeriodicEANEdge extends Edge {
	/**
	 * The type of the activity. Possible types are "drive", "wait" and "change"
	 */
	protected String type;
	/**
	 * The lower bound of the activity, i.e., the minimal time this activity is allowed to endure in a feasible timetable
	 */
	protected int lowerBound;
	/**
	 * The upper bound of the activity, i.e., the maximal time this activity is allowed to endure in a feasible timetable
	 */
	protected int upperBound;
	/**
	 * The number of passengers travelling on this activity
	 */
	protected double numberOfPassengers;

	/**
	 * Create a periodic activity with the given data. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the activity
	 * @param source the source of the activity
	 * @param target the target of the activity
	 * @param type the type of the activity. Possible types are "drive", "wait" and "change"
	 * @param lowerBound the lower bound of the activity, i.e., the minimal time this activity is allowed to endure in
	 *                    a feasible timetable
	 * @param upperBound the upper bound of the activity, i.e., the maximal time this activity is allowed to endure in
	 *                    a feasible timetable
	 * @param numberOfPassengers the number of passengers travelling on this activity
	 */
	public PeriodicEANEdge(int id, PeriodicEANVertex source, PeriodicEANVertex target, String type, int lowerBound,
	                       int upperBound, double numberOfPassengers) {
		super(id, source, target, false);
		this.type = type;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.numberOfPassengers = numberOfPassengers;
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
	 * @param upperBound the upper bound of the activity, i.e., the maximal time this activity is allowed to endure in
	 *                    a feasible timetable
	 * @param numberOfPassengers the number of passengers travelling on this activity
	 * @param addToVertices whether or not to add this edge to the source and target vertex as outgoing and incoming
	 *                       edge, respectively. You want to set this to false if the constructor is called from a
	 *                       subclass and add the call to the constructor of the origin class. Otherwise, the vertex
	 *                       tries to add a not fully constructed edge and the hashcode function will crash!
	 */
	public PeriodicEANEdge(int id, PeriodicEANVertex source, PeriodicEANVertex target, String type, int lowerBound,
	                       int upperBound, double numberOfPassengers, boolean addToVertices) {
		super(id, source, target, false);
		this.type = type;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.numberOfPassengers = numberOfPassengers;
		if(addToVertices){
			addToVertices();
		}
	}

	/**
	 * Get the lower bound of the activity, i.e., the minimal time this activity is allowed to endure in a feasible
	 * timetable
	 * @return the lower bound
	 */
	public int getLowerBound() {
		return lowerBound;
	}

	/**
	 * Get the upper bound of the activity, i.e., the maximal time this activity is allowed to endure in a feasible
	 * timetable
	 * @return the upper bound
	 */
	public int getUpperBound() {
		return upperBound;
	}

	/**
	 * Get the number of passengers travelling on this activity
	 * @return the number of passengers
	 */
	public double getNumberOfPassengers() {
		return numberOfPassengers;
	}

	@Override
	public PeriodicEANVertex getSource() {
		return (PeriodicEANVertex) source;
	}

	@Override
	public PeriodicEANVertex getTarget() {
		return (PeriodicEANVertex) target;
	}

	/**
	 * Return the type of the activity
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof PeriodicEANEdge)){
			return false;
		}
		PeriodicEANEdge otherEdge = (PeriodicEANEdge) other;
		return this.getType().equals(otherEdge.getType()) && this.getLowerBound() == otherEdge.getLowerBound() && this
				.getUpperBound() == otherEdge.getUpperBound() && this.getNumberOfPassengers() == otherEdge
				.getNumberOfPassengers();
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + super.hashCode();
		result = result * 31 + type.hashCode();
		result = result * 31 + lowerBound;
		result = result * 31 + upperBound;
		long f = Double.doubleToLongBits(numberOfPassengers);
		result = result * 31 + (int) (f ^ (f>>>32));
		return result;
	}
}
