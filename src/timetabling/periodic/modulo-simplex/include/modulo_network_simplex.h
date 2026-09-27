/*
 * modulo_network_simplex.h
 */

//prevent double includes
#ifndef MODULO_NETWORK_SIMPLEX_H_
#define MODULO_NETWORK_SIMPLEX_H_

#include <vector>
#include <queue>
#include <map>

//boost stuff
#include "boost/utility.hpp"
#include "boost/config.hpp"
#include "boost/property_map/property_map.hpp"
#include "boost/graph/adjacency_list.hpp"
#include "boost/bimap.hpp"

//boost definitions
enum edge_mintime_t { edge_mintime };
enum edge_maxtime_t { edge_maxtime };

namespace boost
{
BOOST_INSTALL_PROPERTY(edge, mintime);
BOOST_INSTALL_PROPERTY(edge, maxtime);
}

typedef boost::property<edge_maxtime_t, int> Maxtime;
typedef boost::property<edge_mintime_t, int, Maxtime> Mintime;
typedef boost::property<boost::edge_weight_t, int, Mintime> Edge_props;
typedef boost::adjacency_list<boost::vecS, boost::vecS, boost::bidirectionalS,boost::no_property,Edge_props> Graph;
typedef boost::adjacency_list<boost::vecS, boost::vecS, boost::undirectedS,boost::no_property,boost::no_property> Tree;
typedef boost::graph_traits<Graph>::edge_descriptor Edge;
typedef boost::graph_traits<Graph>::vertex_descriptor Vertex;
typedef boost::graph_traits<Graph>::vertex_iterator Vertex_Iterator;

//algorithm namespace
namespace modulosimplex
{

	//These are the possible search methods for the fundamental cuts.
	enum TABLEAU_SEARCH
	{
		//Takes the best of all neighbours.
		TAB_FULL = 1,

		//Sorts the columns so that the shortest will be used first
		//in the search. Takes the first fundamental cut that is
		//good enough according to the min_pivot_improvement percentage.
		TAB_FASTEST = 2,

		//Sorts the columns so that the shortest will be used first
		//in the search. Takes the best fundumental cut out of the
		//first percentage_improvement percentage.
		TAB_PERCENTAGE = 3,

		//Uses simulated annealing to find the next pivot step. Parameters
		//are sa_temperature and sa_cooling_factor.
		TAB_SIMULATED_ANNEALING = 4,

		//Tabu search.
		TAB_SIMPLE_TABU_SEARCH = 5,

		//Uses steepest descend until it arrives at the local optimum,
		//then switches to simulated annealing.
		TAB_STEEPEST_SA_HYBRID = 6,

		//Do not perfom simplex iterations.
		TAB_NOTHING = 7
	};

	//These are the possible ways the minimum improvement of a pivot step changes when none is found.
	enum TABLEAU_MIN_IMPROVEMENT
	{
		//The criterion stays fixed.
		FIXED = 1,

		//The criterion becomes smaller.
		DYNAMIC = 2
	};

	//These are the available local seach algorithms.
	enum LOCAL_IMPROVEMENT
	{
		//Single node cuts.
		SINGLE_NODE_CUT = 1,

		//Random node cuts.
		RANDOM_CUT = 2,

		//Waiting edge cuts.
		WAITING_CUT = 3,

		//Connected cuts.
		CONNECTED_CUT = 4,

		NO_CUT = 5
	};

	//The output mode.
	enum OUTPUT_MODE
	{
		//Well-formatted output, sparse information.
		OUT_DEFAULT = 1,

		//CSV table for evaluation
		OUT_TABLE = 2,

		//ALL INFORMATION FOR DEBUGGING
		OUT_DEBUG = 3

	};

	//The nonperiodic solver.
	enum NONPERIODIC_SOLVER
	{
		//Gurobi
		SOLVER_GUROBI = 1,

		//MCF
		SOLVER_MCF = 2
	};

//main solver class
class simplex
{
public:
	//Constructor and destructor.
	simplex(int seed);
	~simplex();

	//Setter-methods.
	void set_tab_search(TABLEAU_SEARCH tab_search);
	void set_min_pivot_improvement(double min_percentage);
	void set_min_cut_improvement(double min_percentage);
	void set_tab_min_improvement(TABLEAU_MIN_IMPROVEMENT tab_min);
	void set_percentage_improvement(double percentage);
	void set_sa_init_temperature(double temp);
	void set_sa_coolness_factor(double fac);
	void set_ts_memory(int length);
	void set_ts_max_iterations(int number);
	void set_pivot_count_max_iterations(int number);
	void set_loc_improvement(LOCAL_IMPROVEMENT loc);
	void set_loc_number_of_nodes(int number);
	void set_loc_number_of_tries(int number);
	void set_dynamic_pivot_factor(double factor);
	void set_headways(bool what);
	void set_limit(int number);
	void set_timelimit(double time);
	void set_gurobi_threads(int number);

	//Initialises the algorithm.
	void init(std::string activities_file, std::string events_file, int given_period);

	//Initialises the algorithm with feasible timetable given.
	void init(std::string activities_file, std::string events_file, int given_period, std::string timetable);

	void improvable(clock_t begin);
	void transform();
	void non_periodic();
	bool pivot();

	//Starts the algorithm.
	void solve();

	//Writes the found timetable to the given file.
	void write_result(std::string filename);

	void check_spanning_tree();
	int calculate_number_of_connected_components(); //TODO: Can be removed
	int repair_spanning_tree(int *gurobi_vbasis, int gurobi_basis_size);

private:
	//struct for tabu list
	struct ts_memory
	{
		std::set<int> lower_tree;
		std::set<int> upper_tree;

		uint lower_checksum;
		uint upper_checksum;
	};

	//struct for local cuts
	//(could be used for any cut, though)
	struct eta
	{
		 int where;
		 int delta;
		 bool active;
		 std::vector<int> directions;
	};

	//finds the times recursively
	void set_time();
	void set_time(Vertex where);

	//calculates the current objective value
	int get_objective();

	//calculates the change of the objective value when using the pivot in_edge <-> out_edge
	int get_obj_change(int in_edge, int out_edge, bool as_lower);

	//get/set methods for the tableau
	void set_coeff(int out_edge, int in_edge, int value);
	int get_coeff(int out_edge, int in_edge);

	//deprecated
	void set_coeff_temp(int out_edge, int in_edge, int value);
	void update_coeffs();

	//recalculates the tableau
	void swap_edges(int into_edge, int out_edge, bool as_lower);

	//creates the tableau
	void build();

	//calculates the current modulo parameters
	void find_modulo();

	//finds feasible modulo parameters
	void find_feasible_periodic();

	//private variables
	std::vector<int> m_rhs;
	std::vector<int> m_slack;
	std::vector<int> modulo_param;
	std::map<std::pair<int,int>,bool> coeff;
	std::map<std::pair<int,int>,bool> coeff_temp;
	std::vector<std::set<int> > columns;
	Graph* g;
	Tree* spanning_tree;
	std::set<int> spanning_set;
	std::vector<Edge> span;
	std::vector<int> in_tree_edges;
	std::vector<int> out_tree_edges;

	boost::bimap<int,Edge> graph_edges;
	std::vector<Edge> graph_edges_left;


	boost::bimap<int,Vertex> graph_vertices;

	std::map<int,int> m_pi;
	std::vector<bool> lower_tree_edges;
	std::vector<std::string> edge_types;

	int nr_of_edges;
	int nr_of_nodes;
	int nr_of_connected_components; //TODO: Can be removed
	int period;
	eta cut;

	//slows down the algorithm for presentation purposes, when true
	static const bool presentation = false;

	//coming from java-frameworks or such, disable any possible keylistener
	bool allow_keylistener;

	//counts the distribution of column entries when true
	static const bool countpercentages = false;

	std::vector<uint> distribution;

	TABLEAU_SEARCH search;
	TABLEAU_MIN_IMPROVEMENT search_impro;
	LOCAL_IMPROVEMENT local_search;
	OUTPUT_MODE verbose;
	NONPERIODIC_SOLVER non_solver;
	//min_pivot_improvement = 0 means no aborting criteria
	double min_pivot_improvement;
	double min_cut_improvement;
	double percentage_improvement;
	double dynamic_pivot_factor;

	int loc_number_of_nodes;
	int loc_number_of_tries;
	int loc_current_tries;

	int gurobi_threads;

	double sa_temperature;
	double sa_cooling_factor;

	int ts_memory_length;
	int ts_max_iterations;
	int pivot_count_max_iterations;
	ts_memory best_solution;
	int best_objective;
	std::deque< ts_memory > ts_memory_deque;
	void ts_recreate_best();

	bool improved_last_time;

	bool sa_hybrid_active;

	bool use_headways;
	int limit;
	unsigned long nr_col_ops;
	unsigned long nr_piv_ops;
	unsigned long nr_calc_ops;
	double timelimit;

	double current_robustness;
	double min_robustness;
	double dyn_rob_penalty;
	std::vector<double> robustness;

	std::vector<int> best_feasible;
	double best_feasible_obj;

	void save_best_feasible();
	void restore_best_feasible();

};
}//namespace

#endif /* SIMPLEX_H_ */
