import java.util.*;

public class PTN {
	
	private LinkedList<Stop> stops;
	private LinkedList<Edge> edges_undirected;//used for calculations
	private LinkedList<Edge> edges_directed;//used to reconstruct ptn, if ptn was directed
	
	private boolean directed;
	private int first_unused_stop_index=0;
	private int first_unused_edge_index=0;
	
//constructor--------------------------------------------------
	public PTN(boolean directed){
		this.directed=directed;
		stops= new LinkedList<Stop>();
		edges_undirected= new LinkedList<Edge>();
		if(directed){
			edges_directed= new LinkedList<Edge>();
		}else{
			edges_directed=null;
		}
	}

	public PTN(boolean directed, LinkedList<Stop> stops, LinkedList<Edge> edges){
		this.stops = stops;
		this.directed = directed;
		if(directed)
			this.edges_directed = edges;
		else
			this.edges_undirected = edges;
	}
	
	
//Getter-------------------------------------------------------
	public boolean isDirected(){
		return directed;
	}
	
	public LinkedList<Stop> getStops(){
		return stops;
	}
	
	public LinkedList<Edge> getEdges(){
		if(directed)
			return edges_directed;
		else
			return edges_undirected;
	}
	
	/**
	 * In edge-file, stops are referred to by index but the edges need a reference to the stop itself.
	 * @param index
	 * @return
	 */
	public Stop getStop(int index){
		int list_index= stops.indexOf(new Stop(index,"","",0.,0.));
		if(list_index==-1)
			return null;
		return stops.get(list_index);
	}
	
	public Edge getEdge(int index){
		for(Edge e:this.getEdges()){
				if(e.getIndex() == index){
					return e;
				}
		}
		return null;		
	}
	
	
    public Edge getEdge(int leftStopIndex, int rightStopIndex){
        if(this.directed){
            for(Edge edge:this.edges_directed)
                if(edge.getLeft_stop().getIndex()==leftStopIndex && edge.getRight_stop().getIndex()==rightStopIndex)
					return edge;
        } else {
			for(Edge edge:this.edges_undirected)
				if((edge.getLeft_stop().getIndex()==leftStopIndex && edge.getRight_stop().getIndex()==rightStopIndex) || (edge.getLeft_stop().getIndex()==rightStopIndex && edge.getRight_stop().getIndex()==leftStopIndex))
					return edge;
        }
        return null;
        }
//Methods------------------------------------------------------
	
	public void addStop(Stop stop){
		if(first_unused_stop_index <= stop.getIndex())
			first_unused_stop_index=stop.getIndex()+1;
		stops.add(stop);
	}
	
	/**
	 * Adds an edge (i,j) to the PTN. If the PTN is directed and (j,i) already exists, the backward-edge of (j,i) is set
	 * to (i,j) and (i,j) is not added to the undirected edges.
	 * @param edge
	 */
	public void addEdge(Edge edge){
		if(first_unused_edge_index <= edge.getIndex())
			first_unused_edge_index=edge.getIndex()+1;
		if(!directed){
			edges_undirected.add(edge);
			return;
			}
		edges_directed.add(edge);
		int index_reverse_edge=edges_undirected.indexOf(edge.getReverseEdge());
		if(index_reverse_edge != -1){
			edges_undirected.get(index_reverse_edge).setBackward_edge(edge);
		}
	}
	
	public void renameStops(){
		int index=1;
		Stop current_stop;
		Collections.sort(stops);
		Iterator<Stop> it=stops.iterator();
		while(it.hasNext()){
			current_stop=it.next();
			current_stop.setIndex(index);
			index++;
		}
		first_unused_stop_index=index;
	}
	
	public void renameEdges(){
		int index=1;
		Edge current_edge;
		LinkedList<Edge> edges_to_sort;
		if(directed){
			edges_to_sort=edges_directed;
		}else{
			edges_to_sort=edges_undirected;
		}
		Collections.sort(edges_to_sort);
		Iterator<Edge> it=edges_to_sort.iterator();
		while(it.hasNext()){
			current_edge=it.next();
			current_edge.setIndex(index);
			index++;
		}
		first_unused_edge_index=index;
	}
	
	public void insertCandidate(Candidate candidate){
		ArrayList<Edge> edges = candidate.getEdges(edges_undirected);
		// Only if this candidate is associated with exactly one edge (no breakpoint of the network) the candidate is inserted
		// Otherwise the candidate already exists
		if(edges != null && edges.size()==1){	
		Edge edge_to_split=edges.get(0);
		
		Edge backward_edge=edge_to_split.getBackward_edge();
		Edge left_edge;
		Edge right_edge;
		Edge backward_left_edge=null;
		Edge backward_right_edge=null;
		double distance_left;
		double distance_right;
		Distance distance=new EuclideanNorm();//length of edges is calculated using the euclidean norm
		Stop stop_to_add=candidate.toStop(first_unused_stop_index++);
		
		//Add new stop
		this.addStop(stop_to_add);
		
		//change edges
		//1st step: remove old edge
		removeEdge(edge_to_split, backward_edge);
		//2nd step: create new edges
		distance_left=distance.calcDist(edge_to_split.getLeft_stop(), stop_to_add);
		distance_right=distance.calcDist(edge_to_split.getRight_stop(), stop_to_add);
		left_edge= new Edge(directed, first_unused_edge_index++, edge_to_split.getLeft_stop(), 
				stop_to_add, distance_left,edge_to_split.getLower_bound(), edge_to_split.getUpper_bound());
		left_edge.setOriginal_edge(edge_to_split.getOriginal_edge());
		right_edge= new Edge(directed, first_unused_edge_index++, stop_to_add, edge_to_split.getRight_stop(), 
				 distance_right,edge_to_split.getLower_bound(), edge_to_split.getUpper_bound());
		right_edge.setOriginal_edge(edge_to_split.getOriginal_edge());
		if(backward_edge != null){
			backward_left_edge= new Edge(directed, first_unused_edge_index++, stop_to_add, backward_edge.getRight_stop(), 
					 distance_left,backward_edge.getLower_bound(), backward_edge.getUpper_bound());
			left_edge.setBackward_edge(backward_left_edge);
			backward_left_edge.setOriginal_edge(backward_edge.getOriginal_edge());
			backward_right_edge= new Edge(directed, first_unused_edge_index++, backward_edge.getLeft_stop(), 
					stop_to_add, distance_right, backward_edge.getLower_bound(), backward_edge.getUpper_bound());
			right_edge.setBackward_edge(backward_right_edge);
			backward_right_edge.setOriginal_edge(backward_edge.getOriginal_edge());
		}
		//3rd step: add new edges
		insertEdge(left_edge, backward_left_edge);
		insertEdge(right_edge, backward_right_edge);
		}
	}

	// Nodes that have only two adjacent edges can be removed without changing the structure of the network.

	public void removeDestructableStations(){
		LinkedList<Edge> adjacentEdges = new LinkedList<Edge>();
		LinkedList<Stop> toBeRemoved = new LinkedList<Stop>();
		for(Stop stop:stops){
			adjacentEdges = new LinkedList<Edge>();
			for(Edge edge:this.getEdges()){
				if(stop.getIndex()==edge.getLeft_stop().getIndex() || stop.getIndex()==edge.getRight_stop().getIndex())
					adjacentEdges.add(edge);
			}
			if(adjacentEdges.size()==2){
				toBeRemoved.add(stop);
				if(stop.getIndex()==adjacentEdges.get(0).getLeft_stop().getIndex())
					this.getEdges().add(new Edge(adjacentEdges.get(0).isDirected(),adjacentEdges.get(0).getIndex(),adjacentEdges.get(1).getLeft_stop(),adjacentEdges.get(0).getRight_stop(),adjacentEdges.get(0).getLength()+adjacentEdges.get(1).getLength(),adjacentEdges.get(0).getLower_bound()+adjacentEdges.get(1).getLower_bound(),adjacentEdges.get(0).getUpper_bound()+adjacentEdges.get(1).getUpper_bound()));
				else 
					this.getEdges().add(new Edge(adjacentEdges.get(0).isDirected(),adjacentEdges.get(0).getIndex(),adjacentEdges.get(0).getLeft_stop(),adjacentEdges.get(1).getRight_stop(),adjacentEdges.get(0).getLength()+adjacentEdges.get(1).getLength(),adjacentEdges.get(0).getLower_bound()+adjacentEdges.get(1).getLower_bound(),adjacentEdges.get(0).getUpper_bound()+adjacentEdges.get(1).getUpper_bound()));
				this.getEdges().removeAll(adjacentEdges);
			}
		}
		stops.removeAll(toBeRemoved);
		this.renameStops();
		this.renameEdges();
	}

//Private methods-----------------------------------------------------------------------
	
	private void removeEdge(Edge edge, Edge backward_edge){
		edges_undirected.remove(edge);
		if(directed){
			edges_directed.remove(edge);
			if(backward_edge!= null)
				edges_directed.remove(backward_edge);
		}
	}
	
	private void insertEdge(Edge edge, Edge backward_edge){
		edges_undirected.add(edge); 
		if(directed){
			edges_directed.add(edge);
			if(backward_edge != null)
				edges_directed.add(backward_edge);
		}
	}
	
}
