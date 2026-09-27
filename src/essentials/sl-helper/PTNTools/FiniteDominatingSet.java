import java.util.*;

public class FiniteDominatingSet {
	private LinkedList<Candidate> candidates;
	private Distance distance;
	private double radius;
	private boolean destruction_allowed;
	private boolean removeCoveredDemandPoints;

	public FiniteDominatingSet(PTN ptn, Demand demand, Distance distance, double radius, boolean destruction_allowed, boolean removeCoveredDemandPoints){
		this.distance=distance;
		this.radius=radius;
		this.destruction_allowed = destruction_allowed;
		this.removeCoveredDemandPoints = removeCoveredDemandPoints;
		this.addStopsAsCandidates(ptn);	
		candidates=calcCandidates(ptn, demand);
	}
	
	
	public FiniteDominatingSet(FiniteDominatingSet old_fds, LinkedList<Integer> indices_new_candidates){
		distance=old_fds.getDistance();
		radius=old_fds.getRadius();
		candidates=new LinkedList<Candidate>();
		HashMap<Integer, Candidate> old_candidates_map=old_fds.toHashMap();
		for(int index: indices_new_candidates){
			candidates.add(old_candidates_map.get(index));
		}
	}
	
	public FiniteDominatingSet(LinkedList<Candidate> candidates, Distance distance, double radius){
		this.candidates=candidates;
		this.distance=distance;
		this.radius=radius;
	}
	
//Getter---------------------------------------------------------------------
	public LinkedList<Candidate> getCandidates(){
		return candidates;
	}
	
	public Distance getDistance(){
		return distance;
	}
	
	public double getRadius(){
		return radius;
	}
	
	public int getNumberOfCandidates(){
		return candidates.size();
	}
	
	
//Methods---------------------------------------------------------------------------
	
	public HashMap<Integer, Candidate> toHashMap(){
		HashMap<Integer, Candidate> candidate_map=new HashMap<Integer, Candidate>();
		for(Candidate current_candidate: candidates){
			candidate_map.put(current_candidate.getId(), current_candidate);
		}
		return candidate_map;
	}
	
	// Calculate candidate nodes for existing PTN and demand points
	
	private LinkedList<Candidate> calcCandidates(PTN ptn, Demand demand){
		if(candidates == null)
			candidates= new LinkedList<Candidate>();
		LinkedList<Edge> edges=ptn.getEdges();
		LinkedList<DemandPoint> demand_points = demand.getDemand_points();
		LinkedList<DemandPoint> demand_points_remove = new LinkedList<DemandPoint>();
		LinkedList<Candidate> new_candidates;
		Iterator<Edge> it_edge= edges.iterator();
		Iterator<DemandPoint> it_demand = demand.getDemand_points().iterator();
		Edge current_edge;
		DemandPoint current_demand_point;
		boolean candidateForDemandPoint = false;
		while(it_demand.hasNext()){
			candidateForDemandPoint = false;
			current_demand_point=it_demand.next();
			it_edge= edges.iterator();
			while(it_edge.hasNext()){
				current_edge=it_edge.next();
				new_candidates=distance.candidateOnEdge(current_demand_point, current_edge, radius);
				if(new_candidates != null && !new_candidates.isEmpty()){
					candidateForDemandPoint = true;
					candidates.addAll(new_candidates);
				}
			}
			if(!candidateForDemandPoint){
				demand_points_remove.add(current_demand_point);
			}
		}
		demand_points.removeAll(demand_points_remove);
		return candidates;
	}

	// Add all nodes as candidates. Either they have more than one adjacent edge or they are end points of the network. Mark them as vertices.
	private void addStopsAsCandidates(PTN ptn){
		if(candidates == null)
			candidates = new LinkedList<Candidate>();
		ArrayList<Edge> adjacent_edges;
		LinkedList<Edge> edges = ptn.getEdges();
		LinkedList<Stop> stops = ptn.getStops();
		Iterator<Stop> stops_it = stops.iterator();
		Iterator<Edge> edges_it;
		Stop current_stop;
		Edge current_edge;
		while(stops_it.hasNext()){
			adjacent_edges = new ArrayList<Edge>();
			current_stop = stops_it.next();
			edges_it = edges.iterator();
			while(edges_it.hasNext()){
				current_edge = edges_it.next();
				if(current_edge.getLeft_stop() == current_stop || current_edge.getRight_stop() == current_stop)
					adjacent_edges.add(current_edge);
			}
			Candidate candidate = new Candidate(current_stop.getX_coordinate(), current_stop.getY_coordinate(), adjacent_edges);
			candidate.setIsVertex(true);
			candidates.add(candidate);
		}
	}
	
}
