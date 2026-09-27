import java.util.*;

public class Edge implements Comparable<Edge> {
	private static String header = "";

	private int index;
	private Stop left_stop;
	private Stop right_stop;

	private double length;
	private int lower_bound;
	private int upper_bound;

	private int lower_frequency_bound;
	private int upper_frequency_bound;

	private boolean directed;
	private Edge backward_edge;
	private Edge original_edge;

	public Edge(boolean directed, int index, Stop left_stop,
			Stop right_stop, double length, int lower_bound, int upper_bound) {

		this.directed = directed;
		this.index = index;
		this.left_stop = left_stop;
		this.right_stop = right_stop;
		this.length = length;
		this.lower_bound = lower_bound;
		this.upper_bound = upper_bound;
		this.lower_frequency_bound = 0;
		this.upper_frequency_bound = 0;
	}


// Setter---------------------------------------------------------------------------

	public void setIndex(int index) {
		this.index = index;
	}

	public void setLower_bound(int lowerBound) {
		this.lower_bound = lowerBound;
	}

	public void setUpper_bound(int upperBound) {
		this.upper_bound = upperBound;
	}

	public void setBackward_edge(Edge backward_edge) {
		this.backward_edge = backward_edge;
	}

	public void setOriginal_edge(Edge original_edge){
		this.original_edge=original_edge;
	}

	public void setLowerFrequencyBound(int bound){
		this.lower_frequency_bound = bound;
	}

	public void setUpperFrequencyBound(int bound){
		this.upper_frequency_bound = bound;
	}
// Getter------------------------------------------------------------------------------
	public int getIndex() {
		return index;
	}

	public Stop getLeft_stop() {
		return left_stop;
	}

	public Stop getRight_stop() {
		return right_stop;
	}

	public double getLength() {
		return length;
	}

	public int getLower_bound() {
		return lower_bound;
	}

	public int getUpper_bound() {
		return upper_bound;
	}

	public boolean isDirected() {
		return directed;
	}

	public Edge getBackward_edge() {
		return backward_edge;
	}

	public int getLowerFrequencyBound(){
		return lower_frequency_bound;
	}

	public int getUpperFrequencyBound(){
		return upper_frequency_bound;
	}

	/**
	 * Returns an edge which is equal to the backward-edge, if it exists
	 */
	public Edge getReverseEdge() {
		return new Edge(directed, 0, right_stop, left_stop, 0,0,0);
	}

	public Edge getOriginal_edge(){
		if(original_edge == null)
			return this;
		else
			return original_edge;
	}


//CompareTo-------------------------------------------------------------------------

    @Override
    public int compareTo(Edge o) {
        return index-o.index;
    }

//equals---------------------------------------------------------------------------
    /**
     * Not consistent with compareTo!
     */
    public boolean equals(Object o){
    	if(o==null)
    		return false;
    	if(!(o instanceof Edge))
    		return false;
    	Edge other= (Edge) o;
    	if(other.getLeft_stop().equals(left_stop)&&other.getRight_stop().equals(right_stop))
    		return true;
    	return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left_stop, right_stop);
    }

    //CSV--------------------------------------------------------------------------------------
	public static String printHeader() {
		return "# "+header;
	}

	public String toCSV() {
		return index + "; " + left_stop.getIndex() + "; "
				+ right_stop.getIndex() + "; " + length + "; " + lower_bound
				+ "; " + upper_bound;
	}

	public String toString(){
		return index + "; " + left_stop.toString() + "; " + right_stop.toString();
	}

//static methods--------------------------------------------------------------------------
	public static void setHeader(String head){
		header=head;
	}

}
