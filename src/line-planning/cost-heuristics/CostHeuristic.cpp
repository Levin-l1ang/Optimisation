#include <list>
#include "CostHeuristic.h"

bool CostHeuristic::calculateGreedy1Solution(AdjacencyListGraph<Stop, Link>* ptn, LinePool* linePool){
	/**
	 * Sort lines due to greedy objective function
	 */
	struct LineSorter{
		inline bool operator() (Line line1, Line line2){
			return ((line1.getCost()/line1.getLength()) < (line2.getCost()/line2.getLength()));
		}
	};
	//Track, if the user was already warned, that a violation of the upper freq bounds occurs
	bool hasBeenWarned = false;
	auto ptnEdges = ptn->getEdges();
	std::list<Link> edges(ptnEdges.begin(), ptnEdges.end());
	auto lines = linePool->getLines();
	std::sort(lines.begin(), lines.end(), LineSorter());
	//std::set<Line, LineSorter> orderedLines(lines.begin(), lines.end());
	edges.erase(std::remove_if(edges.begin(), edges.end(), [](Link & link) { return link.getLowerFrequencyBound() == 0;}), edges.end());
	// We will track the upper frequency bounds as well, to give a warning if one is violated
	// Note, that we only track it heuristically due to performance reasons. Therefore, we will not
	// always give a warning if the generated line concept is infeasible, but every time we give a warning, it will be!
	for(auto it = lines.begin(); it != lines.end() && edges.size() > 0; it++){
		Line nextLine = *it;
		bool wouldBeInfeasible = false;
		int maximalMinimalFrequency = 0;
		for(Link& link : nextLine.getLinePath().getEdges()){
			Link* ptnLink = ptn->getEdge(link.getId());
			if(ptnLink->getUpperFrequencyBound() < maximalMinimalFrequency){
				wouldBeInfeasible = true;
			}
			maximalMinimalFrequency = std::max(maximalMinimalFrequency, ptnLink->getLowerFrequencyBound());
			ptnLink->setLowerFrequencyBound(0);
			ptnLink->setUpperFrequencyBound(ptnLink->getUpperFrequencyBound() - maximalMinimalFrequency);
		}
		if(wouldBeInfeasible && !hasBeenWarned){
			std::cout << "WARNING: The calculated line concept will be infeasible due to upper frequency bounds" << std::endl;
			hasBeenWarned = true;
		}
		linePool->getLine(nextLine.getId())->setFrequency(maximalMinimalFrequency);
		edges.erase(std::remove_if(edges.begin(), edges.end(), [ptn](Link & link) { return ptn->getEdge(link.getId())->getLowerFrequencyBound() == 0;}), edges.end());
	}
	if(edges.size() > 0){
		std::cout << "ERROR: Could not compute a feasible line concept due to lower frequency bounds" << std::endl;
		return false;
	}
	return true;
}

bool CostHeuristic::calculateGreedy2Solution(AdjacencyListGraph<Stop, Link>* ptn, LinePool* linePool){
	/**
	 * Sort links based on their lower frequency bound
	 */
	struct LinkSorter{
		inline bool operator() (Link* link1, Link* link2){
			return link1->getLowerFrequencyBound() < link2->getLowerFrequencyBound();
		}
	};
	/**
	 * Sort lines due to the greed objective function
	 */
	struct LineSorter{
		inline bool operator() (Line line1, Line line2) const {
			//Count elements with positive minimal frequency in line1 and line2
			int sizeLine1 = 0;
			for(Link link : line1.getLinePath().getEdges()){
				if(link.getLowerFrequencyBound() > 0){
					sizeLine1++;
				}
			}
			int sizeLine2 = 0;
			for(Link link : line2.getLinePath().getEdges()){
				if(link.getLowerFrequencyBound() > 0){
					sizeLine2++;
				}
			}
			return ((line1.getCost()/sizeLine1) < (line2.getCost()/sizeLine2));
		}
	};
	//Track, if the user was already warned, that a violation of the upper freq bounds occurs
	bool hasBeenWarned = false;
	auto edgeCopies = ptn->getEdges();
	std::vector<Link*> edges;
	for(Link link : edgeCopies){
		edges.insert(edges.begin(), ptn->getEdge(link.getId()));
	}
	//We use a heap to sort the edges, the max element will be at the beginning
	std::make_heap(edges.begin(), edges.end(), LinkSorter());
	while(true){
		Link* maxLink = edges.front();
		if(maxLink->getLowerFrequencyBound() == 0){
			//If this is the case, there is no edge with minimal frequency left, we are done
			return true;
		}
		//Build the lines
		std::set<Line, LineSorter> lines;
		for(Line line : linePool->getLines()){
			for(Link link : line.getLinePath().getEdges()){
				if(link.getId() == maxLink->getId()){
					updateFrequencyLowerBoundsInLine(ptn, &line);
					lines.insert(line);
					break;
				}
			}
		}
		if(lines.empty()){
			//There is no line for our element. Therefore we cannot build a feasible line concept!
			std::cout << "WARNING: Could not compute a feasible line concept due to lower frequency bounds" << std::endl;
			return false;
		}
		Line nextLineCopy = *lines.begin();
		auto nextLine = linePool->getLine(nextLineCopy.getId());
		//Calculate the new frequency
		int fMin = maxLink->getLowerFrequencyBound();
		for(Link link : nextLine->getLinePath().getEdges()){
			auto ptnLink = ptn->getEdge(link.getId());
			if(ptnLink->getLowerFrequencyBound() > 0){
				fMin = std::min(fMin, ptnLink->getLowerFrequencyBound());
			}
		}
		nextLine->setFrequency(nextLine->getFrequency() + fMin);
		for(Link linkCopy : nextLine->getLinePath().getEdges()){
			auto link = ptn->getEdge(linkCopy.getId());
			if(link->getUpperFrequencyBound() < fMin && !hasBeenWarned){
				std::cout << "WARNING: The calculated line concept will be infeasible due to upper frequency bounds" << std::endl;
				hasBeenWarned = true;
			}
			link->setUpperFrequencyBound(link->getUpperFrequencyBound() - fMin);
			link->setLowerFrequencyBound(std::max(0, link->getLowerFrequencyBound() - fMin));
		}
		std::make_heap(edges.begin(), edges.end(), LinkSorter());
	}
}

void CostHeuristic::updateFrequencyLowerBoundsInLine(AdjacencyListGraph<Stop, Link>* ptn, Line* line){
	for(Link link : line->getLinePath().getEdges()){
		link.setLowerFrequencyBound(ptn->getEdge(link.getId())->getLowerFrequencyBound());
	}
}