import java.util.*;
import java.lang.*;
import java.lang.Math;

public class CandidateEdge implements Comparable<CandidateEdge> {
	private static String header = "";
	private static int id_count = 1;
	private int index;
	private Candidate left_candidate;
	private Candidate right_candidate;

	private double length;
	private double weight;

	private boolean directed;
	private Edge original_edge;

	public CandidateEdge(boolean directed, Candidate left_candidate,
			Candidate right_candidate, double length) {

		this.directed = directed;
		this.index = id_count++;
		this.left_candidate = left_candidate;
		this.right_candidate = right_candidate;
		this.length = length;
		this.weight = 1.0;
	}

// Setter------------------------------------------------------------------------------

	public void setOriginalEdge(Edge edge){
		this.original_edge = edge;
	}

	public void setWeight(double weight){
		this.weight = weight;
	}

// Getter------------------------------------------------------------------------------
	public int getIndex() {
		return index;
	}

	public Candidate getLeft_candidate() {
		return left_candidate;
	}

	public Candidate getRight_candidate() {
		return right_candidate;
	}

	public double getLength() {
		return length;
	}

	public boolean isDirected() {
		return directed;
	}

	public Edge getOriginal_edge(){
		return original_edge;
	}

	public double getWeight(){
		return weight;
	}

// Methods--------------------------------------------------------------------------

	public Edge toEdge(int index, Stop left_stop, Stop right_stop){
		return new Edge(directed, index, left_stop, right_stop, length, original_edge.getLower_bound(), original_edge.getUpper_bound());
	}

// Calculates if a candidate lays on a candidate edge

	public boolean isOnCandidateEdge(Candidate candidate){
		double lambda_x = (candidate.getX_coordinate() - this.getRight_candidate().getX_coordinate())/(this.getLeft_candidate().getX_coordinate() - this.getRight_candidate().getX_coordinate());
		double lambda_y = (candidate.getY_coordinate() - this.getRight_candidate().getY_coordinate())/(this.getLeft_candidate().getY_coordinate() - this.getRight_candidate().getY_coordinate());
		if(Math.abs(lambda_x - lambda_y)<=Distance.EPSILON && lambda_x > 0 && lambda_x < 1)
			return true;
		else
			return false;
	}


//CompareTo-------------------------------------------------------------------------

   @Override
    public int compareTo(CandidateEdge o) {
        return index-o.index;
    }

//equals---------------------------------------------------------------------------
    /**
     * Not consistent with compareTo!
     */
    public boolean equals(Object o){
    	if(o==null)
    		return false;
    	if(!(o instanceof CandidateEdge ))
    		return false;
    	CandidateEdge other= (CandidateEdge ) o;
    	if(other.getLeft_candidate().equals(left_candidate)&&other.getRight_candidate().equals(right_candidate))
    		return true;
    	return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left_candidate, right_candidate);
    }

    //CSV--------------------------------------------------------------------------------------
	public static String printHeader() {
		return "# "+header;
	}

	public String toCSV() {
		return index + "; " + left_candidate.getId() + "; "
				+ right_candidate.getId() + "; " + length + "; " + original_edge.getIndex();
	}

//static methods--------------------------------------------------------------------------
	public static void setHeader(String head){
		header=head;
	}



}
