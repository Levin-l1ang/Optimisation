#ifndef EVALUATION_H_
#define EVALUATION_H_

//#include "../../../core/cpp/include/core.hpp"
#include <algorithm>
#include <limits>
#include <string>
#include <limits>

/**
*   Extends PeriodicEvent by attribute is_artificial, meaning if the node
*   does not belong to the physical EAN.
*/
class EvalEvent: public PeriodicEvent{
private:
    bool is_artificial;
public:
    EvalEvent(PeriodicEvent &e, bool artificial = false): PeriodicEvent(e){
        is_artificial = artificial;
    }
    bool getArtificial(void){
        return is_artificial;
    }
    void setArtificial(bool artificial){
        is_artificial = artificial;
    }
};
/**
*   Extends PeriodicActivity by attribute duration, stating how long it takes
*   to travel from sourceEvent to targetEvent on this edge.
*/
class EvalActivity: virtual public Edge<EvalEvent>, public PeriodicActivity {
private:
    int duration;
    EvalEvent* sourceEvent;
    EvalEvent* targetEvent;
public:
    EvalActivity(PeriodicActivity &a, AdjacencyListGraph<EvalEvent, EvalActivity> &EAN) :
        PeriodicActivity(a) {
        sourceEvent = EAN.getNode(a.getLeftNode()->getId());
        targetEvent = EAN.getNode(a.getRightNode()->getId());
    }
    EvalActivity(int id, int duration, EvalEvent* From, EvalEvent* To){
        activityId = id;
        this->duration = duration;
        this->sourceEvent = From;
        this->targetEvent = To;
    }
    const int getDuration(void){
        return duration;
    }
    double getDoubleDuration(void){
        return double(duration);
    }
    void setDuration(int dur){
        duration = dur;
    }
    void calcDuration(int period){
        duration = getRightNode()->getTime()
            - getLeftNode()->getTime() - getLowerBound();
        duration = duration % period;
        if (duration < 0) duration += period;
        duration += getLowerBound();
    }
    int getId(){
        return activityId;
    }
    void setId(int a){
        PeriodicActivity::setId(a);
    }
    EvalEvent* getLeftNode(){
        return this->sourceEvent;
    }
    EvalEvent* getRightNode(){
        return this->targetEvent;
    }
    bool isDirected(){
        return PeriodicActivity::isDirected();
    }
};

/**
*   Graph on EvalEvent and EvalActivity which is used for the evaluation.
*   For computing the traveling distances between different pairs of stations
*   one has to find the shortest paths within the PeriodicEAN. To this end
*   artificial nodes are added, that represent each station. Every stations
*   node is then linked to the departure events belonging to this station and
*   every arrival node is linked to these artificial nodes. Then for each
*   station node the shortest paths to all other station nodes are calculated
*   via getAllRouting which yields the sum of traveling distances.
*/

class RoutingGraph: public AdjacencyListGraph<EvalEvent, EvalActivity>{
public:
    std::vector<EvalEvent*> station_nodes;

    RoutingGraph(){}
    RoutingGraph(AdjacencyListGraph<EvalEvent, EvalActivity> &EAN) :
        AdjacencyListGraph<EvalEvent, EvalActivity> (EAN){
    }


    /**
    *
    * Calculate all shortest paths from a node
    *
    **/
    void getAllRouting(EvalEvent &s, std::vector<int> &cur_value,
        int number_stations){
        std::vector<bool> used(nodes.size()+1);
        used[s.getId()] = true;
        cur_value.resize(nodes.size()+1, std::numeric_limits<int>::max());
        cur_value[s.getId()] = 0;
        std::set<std::pair<int, int> > heap;
        heap.insert(std::make_pair(0, s.getId()));
        while (!heap.empty()) {
            int cur = heap.begin()->second;
            heap.erase(heap.begin());
            if (getNode(cur)->getArtificial()){
                number_stations--;
                // cannot travel over artificial nodes
                if (cur != s.getId()) continue;
            }
            // we only need distances to all stations
            if (!number_stations) return;
            for (int edge_id: adjacency_list[cur]) {
                EvalActivity* edge = getEdge(edge_id);
                int next = edge->getRightNode()->getId();
                int cost = edge->getDuration();
                if (!used[next] ||
                     (used[next] && cur_value[next] > cur_value[cur] + cost)) {
                    used[next] = 1;
                    heap.erase(std::make_pair(cur_value[next], next));
                    cur_value[next] = cur_value[cur] + cost;
                    heap.insert(std::make_pair(cur_value[next], next));
                }
            }
        }
        return;
    }
};

class evaluation
{
public:

	evaluation();
	void init(std::string activityfile, std::string eventfile,
		std::string timetablefile, std::string odfile,
		int periodvalue, bool eval_extended, int change_penalty, int capacity);
	void evaluate();
	void results_to_statistic(Statistic &);
	//void eval_robustness(std::string robfile);

private:
    // the time-period of the PeriodicEAN
    int period;

    // should we calculate all the stuff - yes or no?
    bool eval_extended;

    //
    int change_penalty;

    int capacity;
    double overcrowded_factor;

    // calculating the square - always useful
    template<typename T>
    T square(T t){ return t*t; }

    // objects that are read in by the core
    AdjacencyListGraph<EvalEvent, EvalActivity> EAN;
    RoutingGraph routingEAN;
    SparseOD tempod;
    FullOD od = 0;

    // procedures that are called for evaluating
	int max_changetime();
	int min_changetime();
	bool feasible();
	bool feasible_head();
	unsigned long weighted_times();
	double average_slack(bool weighted = false);
    double average_weighted_slack();
	double average_type_slack(ActivityType);
	double variance_headway_slack();
	double sum_od_pairs();
	double perceived_traveling_time();
	double average_travel_time();
	double overcrowded_time();
	double number_of_transfers();

    // variables storing the results of above procedures
	bool feasible_result;
	bool feasible_head_result;
	int max_changetime_result;
	int min_changetime_result;
	unsigned long weighted_times_result;
	double average_slack_result;
    double average_weighted_slack_result;
	double average_change_slack_result;
	double average_headway_slack_result;
	double average_drive_slack_result;
	double average_wait_slack_result;
	double variance_headway_slack_result;
	double sum_od_pairs_result;
	double perceived_traveling_time_result;
	double average_travel_time_result;
	double overcrowded_time_result;
	double perceived_overcrowded_travel_time;
	//double robustness_result;
	//shortest_paths path;
	//shortest_paths path_perceived_traveling_time;
	double number_of_transfers_result;
};

#endif
