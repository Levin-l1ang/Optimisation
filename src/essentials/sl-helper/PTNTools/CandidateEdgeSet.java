import java.util.*;

public class CandidateEdgeSet {
	private LinkedList<CandidateEdge> candidateEdges;
	private PTN ptn;
	private FiniteDominatingSet fds;
	
	public CandidateEdgeSet(PTN ptn, FiniteDominatingSet fds){
		this.ptn = ptn;
		this.fds = fds;
		this.candidateEdges = calcCandidateEdges(ptn,fds);
	}
	
//Getter---------------------------------------------------------------------
	public LinkedList<CandidateEdge> getCandidateEdges(){
		return candidateEdges;
	}
	
	public int getNumberOfCandidateEdges(){
		return candidateEdges.size();
	}
	
//Methods--------------------------------------------------------------------

	/*
	* Calculate candidate edge set according to a ptn and a finite dominating candidate set of nodes
	* @param ptn Public Transportation Network
	* @param fds Finite Dominating Node Set
	* @return ces Finite Candidate Edge Set
	*/
	private LinkedList<CandidateEdge> calcCandidateEdges(PTN ptn, FiniteDominatingSet fds){
		LinkedList<CandidateEdge> candidateEdges = new LinkedList<CandidateEdge>();
		LinkedList<Candidate> candidatesOnCurrentEdge;
		LinkedList<Edge> edges = ptn.getEdges();
		Iterator<Edge> edge_it = edges.iterator();
		Iterator<Candidate> candidate_it;
		Edge current_edge;
		Candidate current_candidate;
		boolean verbose = false;
		while(edge_it.hasNext()){
			current_edge = edge_it.next();
			candidate_it = fds.getCandidates().iterator();
			candidatesOnCurrentEdge = new LinkedList<Candidate>();
			while(candidate_it.hasNext()){
				current_candidate = candidate_it.next();
				if(current_candidate.getEdges().size()==1 && current_edge.equals(current_candidate.getEdges().get(0))){
					candidatesOnCurrentEdge.add(current_candidate);
				}
				else if(current_candidate.getEdges().size()>1){
					for(Edge edge:current_candidate.getEdges()){
						if(current_edge.equals(edge)){
							candidatesOnCurrentEdge.add(current_candidate);
							break;
						}
					}
				}
			}
			candidateEdges.addAll(calcCandidateEdgesOnEdge(current_edge,candidatesOnCurrentEdge,verbose));
		}
		return candidateEdges;
	}

	// Calculate the candidate edges on a given edge according to a given est of candidate nodes

	private LinkedList<CandidateEdge> calcCandidateEdgesOnEdge(Edge edge, LinkedList<Candidate> candidates,boolean verbose){
		Collections.sort(candidates);
		LinkedList<CandidateEdge> candidateEdges = new LinkedList<CandidateEdge>();
		Iterator<Candidate> candidate_it_one = candidates.iterator();
		Iterator<Candidate> candidate_it_two;
		Candidate candidate_one;
		Candidate candidate_two;
		while(candidate_it_one.hasNext()){
			candidate_one = candidate_it_one.next();
			candidate_it_two = candidates.descendingIterator();
			while(candidate_it_two.hasNext()){
				candidate_two = candidate_it_two.next();
				if(candidate_one != candidate_two){
					CandidateEdge candidateEdge = new CandidateEdge(edge.isDirected(),candidate_one,candidate_two,fds.getDistance().calcDist(candidate_one,candidate_two));
					candidateEdge.setOriginalEdge(edge);	
					candidateEdges.add(candidateEdge);
				} else{
					break;
				}
			}
		}
		return candidateEdges;	
	}
	
}
