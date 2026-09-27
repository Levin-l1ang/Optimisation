import java.util.*;
import java.lang.*;

public class Candidate implements Comparable<Candidate>{
	private static String default_name="";
	private static int id_count=1;
	private double x_coordinate;
	private double y_coordinate;
	private int id;
	private ArrayList<Edge> adjacent_edges;
	private int stop_index = 0;
	private boolean isVertex = false;


	public Candidate(double x_coordinate, double y_coordinate, ArrayList<Edge> adjacent_edges){
		this.x_coordinate=x_coordinate;
		this.y_coordinate=y_coordinate;
		id=id_count++;
		this.adjacent_edges=adjacent_edges;
	}


//getter----------------------------------------------------------------------------
	/**
	 * @return the x_coordinate
	 */
	public double getX_coordinate() {
		return x_coordinate;
	}


	/**
	 * @return the y_coordinate
	 */
	public double getY_coordinate() {
		return y_coordinate;
	}


	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @return the edge on which the candidate is originally located
	 */
	public ArrayList<Edge> getEdges(){
		return adjacent_edges;
	}

	public int getStopIndex(){
		return stop_index;
	}

	public boolean isVertex(){
		return isVertex;
	}

	public void setIsVertex(boolean isVertex){
		this.isVertex = isVertex;
	}


//Methods----------------------------------------------------------------------------
	public Stop toStop(int index){
		this.stop_index = index;
		return new Stop(index, ""+"N_"+id , "\""+default_name+id+"\"", x_coordinate, y_coordinate);
	}

	/**
	 * If multiple Candidates are located on the same edge and one of them is chosen,
	 * the other are located on subedges of the original edge.
	 * @param edges Edges of the ptn-
	 * @return Subedge, on which theCandidate is located.
	 */
	public ArrayList<Edge> getEdges(LinkedList<Edge> edges){
		Edge current_edge;
		if(adjacent_edges.size()>1)
			return adjacent_edges;
		if(edges.contains(adjacent_edges.get(0)))
			return adjacent_edges;
  		Iterator<Edge> it=edges.iterator();
		ArrayList<Edge> current_edge_list;
		while(it.hasNext()){
			current_edge=it.next();
			if(current_edge.getOriginal_edge().equals(adjacent_edges.get(0))&&this.inBox(current_edge)){
				current_edge_list = new ArrayList<Edge>();
				current_edge_list.add(current_edge);
				return current_edge_list;
			}
		}
		return null;
	}

//Private methods-------------------------------------------------------------------
	private boolean inBox(Edge edge){
		double min_x=Math.min(edge.getLeft_stop().getX_coordinate(), edge.getRight_stop().getX_coordinate());
		double max_x=Math.max(edge.getLeft_stop().getX_coordinate(), edge.getRight_stop().getX_coordinate());
		double min_y=Math.min(edge.getLeft_stop().getY_coordinate(), edge.getRight_stop().getY_coordinate());
		double max_y=Math.max(edge.getLeft_stop().getY_coordinate(), edge.getRight_stop().getY_coordinate());
		return min_x <=x_coordinate && x_coordinate <= max_x && min_y <=y_coordinate && y_coordinate <= max_y;
	}

 // Method to sort Candidates on an edge (only if they lay on the same edge) according to the relation to the left stop

	@Override
	public int compareTo(Candidate o){
		Distance distance = new EuclideanNorm();
		if(adjacent_edges==null||o.getEdges()==null || adjacent_edges.size()==0 || o.getEdges().size()==0)
			return 0;
		else if(!this.isVertex() && !o.isVertex()){
			if(adjacent_edges.get(0)!=o.getEdges().get(0))
				return 0;
			else if(distance.calcDist(adjacent_edges.get(0).getLeft_stop(),this)<distance.calcDist(adjacent_edges.get(0).getLeft_stop(),o))
				return -1;
			else if(distance.calcDist(adjacent_edges.get(0).getLeft_stop(),this)==distance.calcDist(adjacent_edges.get(0).getLeft_stop(),o))
				return 0;
			else if(distance.calcDist(adjacent_edges.get(0).getLeft_stop(),this)>distance.calcDist(adjacent_edges.get(0).getLeft_stop(),o))
				return 1;
		}else{
			for(Edge edge:adjacent_edges)
				if(o.getEdges().contains(edge)){
					if(distance.calcDist(edge.getLeft_stop(),this)<distance.calcDist(edge.getLeft_stop(),o))
						return -1;
					else if(distance.calcDist(edge.getLeft_stop(),this)==distance.calcDist(edge.getLeft_stop(),o))
						return 0;
					else if(distance.calcDist(edge.getLeft_stop(),this)>distance.calcDist(edge.getLeft_stop(),o))
						return 1;
					break;
				}
			return 0;
		}
		return 0;
	}

//Static methods--------------------------------------------------------------------
	public static void setDefault_name(String name){
		default_name=name;
	}

		public String toCSV() {
		return id + "; " + x_coordinate + "; "+ y_coordinate + "; " + adjacent_edges.size();
	}

    @Override
    public String toString() {
        return "Candidate{" +
            "x_coordinate=" + x_coordinate +
            ", y_coordinate=" + y_coordinate +
            '}';
    }
}
