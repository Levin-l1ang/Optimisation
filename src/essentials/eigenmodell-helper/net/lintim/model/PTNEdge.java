package net.lintim.model;

/**
 * Class representing an edge in a Public Transportation Network(PTN).
 */
public class PTNEdge extends Edge {
	/**
	 * The length of the edge, given in km
	 */
	private double length;
	/**
	 * The load of the edge, i.e. the number of passengers that want to traverse the edge in the given period
	 */
	private int load;
	/**
	 * The minimal frequency of the edge, i.e. the number of times a vehicle has to traverse the edge in a feasible
	 * line concept
	 */
	private int minFrequency;
	/**
	 * The maximal frequency of the edge, i.e. the number of times a vehicle can traverse the edge in a feasible line
	 * concept
	 */
	private int maxFrequency;
	/**
	 * The minimal time in seconds a vehicle needs to traverse this edge
	 */
	private int minTime;
	/**
	 * The maximal time in seconds a vehicle is allowed to traverse this edge
	 */
	private int maxTime;

	/**
	 * Create a new PTN edge from the given information. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the edge
	 * @param source the source of the edge
	 * @param target the target of the edge
	 * @param length the length of the edge, in km
	 * @param minTime the minimal time allowed to traverse this edge for a vehicle in seconds
	 * @param maxTime the maximal time allowed to traverse this edge for a vehicle in seconds
	 */
	public PTNEdge(int id, PTNVertex source, PTNVertex target, double length, int minTime, int maxTime) {
		super(id, source, target, false);
		this.length = length;
		this.load = -1;
		this.minFrequency = -1;
		this.maxFrequency = -1;
		this.minTime = minTime;
		this.maxTime = maxTime;
		addToVertices();
	}

	/**
	 * Create a new PTN edge from the given information. The edge is added to the outgoing and incoming edges of the
	 * source and target respectively.
	 * @param id the id of the edge
	 * @param source the source of the edge
	 * @param target the target of the edge
	 * @param length the length of the edge, in km
	 * @param minTime the minimal time allowed to traverse this edge for a vehicle in seconds
	 * @param maxTime the maximal time allowed to traverse this edge for a vehicle in seconds
	 * @param addToVertices whether or not to add this edge to the source and target vertex as outgoing and incoming
	 *                       edge, respectively. You want to set this to false if the constructor is called from a
	 *                       subclass and add the call to the constructor of the origin class. Otherwise, the vertex
	 *                       tries to add a not fully constructed edge and the hashcode function will crash!
	 */
	public PTNEdge(int id, PTNVertex source, PTNVertex target, double length, int minTime, int maxTime, boolean
			addToVertices) {
		super(id, source, target, false);
		this.length = length;
		this.load = -1;
		this.minFrequency = -1;
		this.maxFrequency = -1;
		this.minTime = minTime;
		this.maxTime = maxTime;
		if(addToVertices){
			addToVertices();
		}
	}

	/**
	 * Set all information from the Load.giv file for this edge
	 * @param load the load of the edge, i.e., the number of passengers that want to traverse the edge in the given period
	 * @param minFrequency the minimal frequency of the edge, i.e., the number of times a vehicle has to traverse the
	 *                      edge in a feasible line concept
	 * @param maxFrequency the maximal frequency of the edge, i.e., the number of times a vehicle can traverse the edge
	 *                      in a feasible line concept
	 */
	public void setLoadFileContent(int load, int minFrequency, int maxFrequency) {
		this.load = load;
		this.minFrequency = minFrequency;
		this.maxFrequency = maxFrequency;
	}

	/**
	 * Get the length of the edge, in km
	 * @return the length
	 */
	public double getLength() {
		return length;
	}

	/**
	 * Get the load of the edge, i.e., the number of passengers that want to traverse the edge in the given period
	 * @return the load
	 */
	public int getLoad() {
		return load;
	}

	/**
	 * Get the minimal frequency of the edge, i.e., the number of times a vehicle has to traverse the edge in a
	 * feasible line concept
	 * @return the minimal frequency
	 */
	public int getMinFrequency() {
		return minFrequency;
	}

	/**
	 * Get the maximal frequency of the edge, i.e., the number of times a vehicle can traverse the edge in a feasible
	 * line concept
	 * @return the maximal frequency
	 */
	public int getMaxFrequency() {
		return maxFrequency;
	}

	/**
	 * Get the minimal time in seconds a vehicle needs to traverse this edge
	 * @return the minimal time
	 */
	public int getMinTime() {
		return minTime;
	}

	/**
	 * Get the maximal time in seconds a vehicle is allowed to traverse this edge
	 * @return the maximal time
	 */
	public int getMaxTime() {
		return maxTime;
	}

	@Override
	public PTNVertex getSource() {
		return (PTNVertex) source;
	}

	@Override
	public PTNVertex getTarget() {
		return (PTNVertex) target;
	}

	@Override
	public boolean equals(Object other){
		if(!super.equals(other)){
			return false;
		}
		if(!(other instanceof PTNEdge)){
			return false;
		}
		PTNEdge otherEdge = (PTNEdge) other;
		return this.getLength() == otherEdge.getLength() && otherEdge.getLoad() == this.getLoad() && this.getMinTime() ==
				otherEdge.getMinTime() && this.getMaxTime() == otherEdge.getMaxTime() && this.getMinFrequency() == otherEdge
				.getMinFrequency() && this.getMaxFrequency() == otherEdge.getMaxFrequency();
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = result * 31 + super.hashCode();
		long f = Double.doubleToLongBits(length);
		result = result * 31 + (int) (f ^ (f>>>32));
		result = result * 31 + load;
		result = result * 31 + minTime;
		result = result * 31 + maxTime;
		result = result * 31 + minFrequency;
		result = result * 31 + maxFrequency;
		return result;
	}
}
