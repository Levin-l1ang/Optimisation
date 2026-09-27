import java.io.*;
import java.util.*;

public class CoveringMatrix {
	private HashMap<Candidate, LinkedList<DemandPoint>> candidate_covering_demand_point;
	private HashMap<DemandPoint, LinkedList<Candidate>> demand_point_covered_by_candidate;
	private int highest_index_candidate=0;
	private int highest_index_demand_point=0;
	private int number_of_uncovered_demand_points=0;
	private Distance distance;
	private double radius;
	private FiniteDominatingSet fds;
	
//Constructor------------------------------------------------------------------------------
	
	public CoveringMatrix(Demand demand, FiniteDominatingSet fds){
		distance=fds.getDistance();
		radius=fds.getRadius();
		this.fds=fds;
		this.calcCoveringMatrix(demand, fds);
	}
	
//Getter-------------------------------------------------------------------------------------
	
	public int getHighest_index_candidate(){
		return highest_index_candidate;
	}
	
	public int getHighest_index_demand_point(){
		return highest_index_demand_point;
	}
	
	public int getNumber_of_uncovered_demand_points(){
		return number_of_uncovered_demand_points;
	}

//Method Greedy----------------------------------------------------------------------------
	
	public FiniteDominatingSet solveSL1Greedy()throws IOException{
		//this.toFile("basis/CoveringMatrix-file.dat");
		FiniteDominatingSet solution;
		LinkedList<Candidate> candidates_to_add=new LinkedList<Candidate>();
		//candidates_to_add.addAll(fixStations());
		candidates_to_add.addAll(applyReductionLemma());
		//this.toFile("basis/ReducedCoveringMatrix-file.dat");
		candidates_to_add.addAll(greedy());
		solution=new FiniteDominatingSet(candidates_to_add, distance, radius);
		return solution;
	}
	
//Private methods --------------------------------------------------------------------------
	private void calcCoveringMatrix(Demand demand, FiniteDominatingSet fds){
		candidate_covering_demand_point = new HashMap<Candidate, LinkedList<DemandPoint>>();
		demand_point_covered_by_candidate=new HashMap<DemandPoint, LinkedList<Candidate>>();
		LinkedList<Candidate> candidates_to_add;
		LinkedList<DemandPoint> demand_points_to_add;
		//Insert candidates and the demand-points they are covering
		for(Candidate current_candidate: fds.getCandidates()){
			if(current_candidate.getId()> highest_index_candidate)
				highest_index_candidate=current_candidate.getId();
			demand_points_to_add= new LinkedList<DemandPoint>();
			for(DemandPoint current_demand_point: demand.getDemand_points()){
				if(current_demand_point.isCoveredBy(current_candidate, radius, distance))
					demand_points_to_add.add(current_demand_point);
			}
			candidate_covering_demand_point.put(current_candidate, demand_points_to_add);
		}
		//Insert demand-points and the candidates they are covered by
		for(DemandPoint current_demand_point: demand.getDemand_points()){
			candidates_to_add=new LinkedList<Candidate>();
			for(Candidate current_candidate: fds.getCandidates()){
				if(current_demand_point.isCoveredBy(current_candidate, radius, distance))
					candidates_to_add.add(current_candidate);
			}
			//If a demand-point is not covered it is ignored.
			if(candidates_to_add.isEmpty()){
				number_of_uncovered_demand_points++;
			}else{
				demand_point_covered_by_candidate.put(current_demand_point, candidates_to_add);
				if(current_demand_point.getId()>highest_index_demand_point)
					highest_index_demand_point=current_demand_point.getId();
			}
		}
	}
	
//Private methods greedy-------------------------------------------------------------------------------
	
	private LinkedList<Candidate> fixStations(){
			LinkedList<Candidate> candidates = new LinkedList<Candidate>();
			LinkedList<DemandPoint> demand_points_to_delete;
			for(Candidate cand:fds.getCandidates())
				if(cand.isVertex()){
					candidates.add(cand);
					demand_points_to_delete=new LinkedList<DemandPoint>(candidate_covering_demand_point.get(cand));
					for(DemandPoint demand_point_to_delete: demand_points_to_delete){
						removeDemandPointForAllCandidates(demand_point_to_delete);
						demand_point_covered_by_candidate.remove(demand_point_to_delete);
					}
					removeCandidateForAllDemandPoints(cand);
					candidate_covering_demand_point.remove(cand);
				}
					
		return candidates;
	}
	
	//Private methods reduction lemma------------------------------------------------------------------
	private LinkedList<Candidate> applyReductionLemma(){
		LinkedList<Candidate> candidates_to_add=applyReductionLemma2();
		boolean change=applyReductionLemma3();
		change=applyReductionLemma4()||change; 
		//Apply the parts of the reduction as long as there is a change.
		while(change){
			candidates_to_add.addAll(applyReductionLemma2());
			change=applyReductionLemma3();
			change=applyReductionLemma4()||change; 
		}
		return candidates_to_add;
	}
	
	private LinkedList<Candidate> applyReductionLemma2(){
		LinkedList<Candidate> candidates_to_add=new LinkedList<Candidate>();
		LinkedList<Candidate> candidate_list;
		LinkedList<DemandPoint> demand_points_to_delete;
		Iterator<DemandPoint> it_demand=demand_point_covered_by_candidate.keySet().iterator();
		DemandPoint current_demand_point;
		Candidate current_candidate;
		while(it_demand.hasNext()){
			current_demand_point=it_demand.next();
			candidate_list=demand_point_covered_by_candidate.get(current_demand_point);
			//Check if a demand-point is covered by only one candidate.
			if(candidate_list.size()==1){
				current_candidate=candidate_list.getFirst();
				//This candidates has to be build.
				candidates_to_add.add(current_candidate);
				//All other demand-points also covered by this candidate need not be considered anymore.
				demand_points_to_delete=new LinkedList<DemandPoint>(candidate_covering_demand_point.get(current_candidate));
				for(DemandPoint demand_point_to_delete: demand_points_to_delete){
					removeDemandPointForAllCandidates(demand_point_to_delete);
					demand_point_covered_by_candidate.remove(demand_point_to_delete);
				}
				//Delete the candidate.
				removeCandidateForAllDemandPoints(current_candidate);
				candidate_covering_demand_point.remove(current_candidate);
				it_demand=demand_point_covered_by_candidate.keySet().iterator();
			}
		}
		return candidates_to_add;
	}
	
	
	private boolean applyReductionLemma3(){
		//If all candidates covering demand-point A also cover demand-point B, 
		//demand-point B needs not to be considered. 
		Iterator<DemandPoint> it_containing; //Iterating demand-point B.
		Iterator<DemandPoint> it_contained; //Iterating demand-point A.
		DemandPoint containing_demand_point;
		DemandPoint contained_demand_point;
		boolean remove;
		boolean change=false;
		it_containing=demand_point_covered_by_candidate.keySet().iterator();
		while(it_containing.hasNext()){
			remove=false;
			containing_demand_point=it_containing.next();
			it_contained=demand_point_covered_by_candidate.keySet().iterator();
			while(it_contained.hasNext()){
				contained_demand_point=it_contained.next();
				if(!containing_demand_point.equals(contained_demand_point)
						&&demand_point_covered_by_candidate.get(containing_demand_point).containsAll(demand_point_covered_by_candidate.get(contained_demand_point))){
					remove=true;
					change=true;
					removeDemandPointForAllCandidates(containing_demand_point);
					break;
				}
			}
			if(remove){
				it_containing.remove();
				it_containing=demand_point_covered_by_candidate.keySet().iterator();
			}
		}
		return change;
	}

	
	private boolean applyReductionLemma4(){
		//If all demand-points covered by candidate A are also covered by candidate B, 
		//candidate A needs not to be considered. 
		Iterator<Candidate> it_containing; //Iterating candidate B.
		Iterator<Candidate> it_contained; //Iterating candidate A.
		Candidate containing_candidate;
		Candidate contained_candidate;
		boolean remove;
		boolean change=false;
		it_containing=candidate_covering_demand_point.keySet().iterator();
		while(it_containing.hasNext()){
			remove=false;
			containing_candidate=it_containing.next();
			it_contained=candidate_covering_demand_point.keySet().iterator();
			while(it_contained.hasNext()){
				contained_candidate=it_contained.next();
				if(!containing_candidate.equals(contained_candidate)
						&&candidate_covering_demand_point.get(containing_candidate).containsAll(candidate_covering_demand_point.get(contained_candidate))){
					remove=true;
					change=true;
					removeCandidateForAllDemandPoints(contained_candidate);
					it_contained.remove();
					break;
				}
			}
			if(remove)
				it_containing=candidate_covering_demand_point.keySet().iterator();
		}
		return change;
	}
	
	private void removeDemandPointForAllCandidates(DemandPoint demand_point){
		LinkedList<DemandPoint> demand_point_list;
		for(Candidate candidate: candidate_covering_demand_point.keySet()){
			demand_point_list=candidate_covering_demand_point.get(candidate);
			if(demand_point_list != null)
				demand_point_list.remove(demand_point);
		}
	}
	
	private void removeCandidateForAllDemandPoints(Candidate candidate){
		LinkedList<Candidate> candidate_list;
		for(DemandPoint demand_point: demand_point_covered_by_candidate.keySet()){
			candidate_list= demand_point_covered_by_candidate.get(demand_point); 
			if(candidate_list != null)
				candidate_list.remove(candidate);
		}
	}
	
	//Private methods greedy-algorithm----------------------------------------------------------------------
	private LinkedList<Candidate> greedy(){
		LinkedList<Candidate> candidates_to_add=new LinkedList<Candidate>();
		LinkedList<DemandPoint> demand_points_to_delete;
		int max_covered_demand_points;
		Candidate candidate_covering_max_demand_points=null;
		//Check if a demand-point has to be covered.
		while(!demand_point_covered_by_candidate.isEmpty()){
			//Check if there are candidates left.
			if(candidate_covering_demand_point.isEmpty())
				throw new RuntimeException("Infeasible Constraint!");
			//Choose next candidate, the one covering most demand-points.
			max_covered_demand_points=0;
			for(Candidate candidate: candidate_covering_demand_point.keySet()){
				if(candidate_covering_demand_point.get(candidate).size()>max_covered_demand_points){
					max_covered_demand_points=candidate_covering_demand_point.get(candidate).size();
					candidate_covering_max_demand_points=candidate;
				}
			}
			if(candidate_covering_max_demand_points!= null)
				candidates_to_add.add(candidate_covering_max_demand_points);
			//Remove all demand-points covered by the chosen candidate.
			demand_points_to_delete =new LinkedList<DemandPoint>(candidate_covering_demand_point.get(candidate_covering_max_demand_points));
			for(DemandPoint demand_point: demand_points_to_delete){
				removeDemandPointForAllCandidates(demand_point);
				demand_point_covered_by_candidate.remove(demand_point);
			}
			//Remove chosen candidate.
			removeCandidateForAllDemandPoints(candidate_covering_max_demand_points);
			candidate_covering_demand_point.remove(candidate_covering_max_demand_points);
		}
		return candidates_to_add;
	}
}
