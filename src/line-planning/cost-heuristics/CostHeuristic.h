#ifndef COST_HEURISTICS_COSTHEURISTIC_H
#define COST_HEURISTICS_COSTHEURISTIC_H

#include "core.hpp"

class CostHeuristic{
public:
	/**
	 * Calculate a line concept with the cost heuristic 8 from the public transportation lecture of Prof. Schöbel. Will find a feasible line concept w.r.t the
	 * lower frequency bounds, if there is one. Will warn, if it detects a violation of the upper frequency bounds, but there is no guarantee, that the algo
	 * will detect this.
	 * @param ptn the ptn
	 * @param linePool the linepool
	 * @return whether a feasible solution w.r.t. the lower freq bounds could be found
	 */
	static bool calculateGreedy1Solution(AdjacencyListGraph<Stop, Link>* ptn, LinePool* linePool);
	/**
	 * Calculate a line concept with the cost heuristic 9 from the public transportation lecture of Prof. Schöbel. Will find a feasible line concept w.r.t the
	 * lower frequency bounds, if there is one. Will warn, if the solution validates the upper frequency bounds.
	 * @param ptn the ptn
	 * @param linePool the linepool
	 * @return whether a feasible solution w.r.t. the lower freq bounds could be found
	 */
	static bool calculateGreedy2Solution(AdjacencyListGraph<Stop, Link>* ptn, LinePool* linePool);

private:
	static void updateFrequencyLowerBoundsInLine(AdjacencyListGraph<Stop, Link>* ptn, Line* line);
};


#endif //COST_HEURISTICS_COSTHEURISTIC_H
