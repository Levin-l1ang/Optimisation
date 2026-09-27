#include <iostream>

#include "core.hpp"
#include "CostHeuristic.h"

int main(int argc, char* argv[]){
	std::cout << "Reading files" << std::endl;
	Config config;
	if(argc < 2){
		throw ConfigNoFileNameGivenException();
	}
	ConfigReader configReader(&config, "basis/Config.cnf", false);
	configReader.read();

	std::string stopFileName = config.getStringValue("default_stops_file");
	std::string linkFileName = config.getStringValue("default_edges_file");
	std::string loadFileName = config.getStringValue("default_loads_file");
	std::string poolFileName = config.getStringValue("default_pool_file");
	std::string poolCostFileName = config.getStringValue("default_pool_cost_file");
	std::string lineConceptFileName = config.getStringValue("default_lines_file");
	bool directed = !config.getBooleanValue("ptn_is_undirected");
	std::string heuristicMethod = config.getStringValue("lc_model");

	AdjacencyListGraph<Stop, Link> ptn;
	PTNReader ptnReader(&ptn, stopFileName, linkFileName, directed, loadFileName);
	ptnReader.read();

	LinePool pool;
	LinePoolReader linePoolReader(&pool, &ptn, poolFileName, poolCostFileName, directed, false);
	linePoolReader.read();

	std::cout << "Computing solution" << std::endl;
	bool feasibleSolutionFound;
	if(heuristicMethod == "cost_greedy_1"){
		feasibleSolutionFound = CostHeuristic::calculateGreedy1Solution(&ptn, &pool);
	}
	else if(heuristicMethod == "cost_greedy_2"){
		feasibleSolutionFound = CostHeuristic::calculateGreedy2Solution(&ptn, &pool);
	}
	else {
		throw AlgorithmInfeasibleParameterSettingException("Cost-Heuristic", "lc_model", heuristicMethod);
	}
	if(!feasibleSolutionFound){
		throw AlgorithmStoppingCriterionException(heuristicMethod);
	}
	std::cout << "Writing solution" << std::endl;

	LineWriter::writeLineConcept(pool, config);

	std::cout << "Done" << std::endl;
}
