#include "../../../core/cpp/include/core.hpp"
#include "evaluation.h"

evaluation::evaluation(){}

/**
*   Setting up the evaluation model.
*   EAN and OD matrix are read in by the core. Then the RoutingEAN is initalized
*/

void evaluation::init(std::string activityfile, std::string eventfile,
     std::string timetablefile, std::string odfile, int periodvalue,
     bool _eval_extended, int ch_penalty, int capacity) {

	//Set period
	period = periodvalue;
    // set evaluation parameter
    eval_extended = _eval_extended;

    // set the penalty for taking a change
    change_penalty = ch_penalty;

    this->capacity = capacity;

    // read in EAN
    AdjacencyListGraph<PeriodicEvent, PeriodicActivity> tempEAN;
    PeriodicEANReader pEANr = PeriodicEANReader(&tempEAN, activityfile, eventfile,
                      timetablefile);
    pEANr.read();

    // convert to adjusted EAN we want to work with here
    for (PeriodicEvent &e: tempEAN.getNodes()){
        EvalEvent ee = EvalEvent(e);
        EAN.addNode(ee);
    }
    for (PeriodicActivity &a: tempEAN.getEdges()){
        EvalActivity ea = EvalActivity(a, EAN);
        ea.calcDuration(period);
        EAN.addEdge(ea);
    }
    // read in OD Matrix
    ODReader odr(&tempod, odfile);
    try {
        odr.read();
        od = tempod.toFullOD();
        routingEAN = RoutingGraph(EAN);
        // remove all activities that may not be used by passengers
        std::vector<EvalActivity> allActivities = routingEAN.getEdges();
        for (EvalActivity& activity: allActivities) {
            if (activity.getType() != ActivityType::DRIVE && activity.getType() != ActivityType::CHANGE && activity.getType() != ActivityType::WAIT) {
                routingEAN.removeEdge(activity);
            }
        }
        // add nodes in EAN for routing from station to station
        routingEAN.station_nodes.resize(od.getSize());
        for (int i = 0; i < od.getSize(); i++){
            int id = routingEAN.getMaxNodeId() + 1;
            PeriodicEvent p(id, i+1,ARRIVAL,-1,-1, -1, FORWARDS, 0);
            EvalEvent e(p, true );
            routingEAN.addNode(e);
            routingEAN.station_nodes[i] = routingEAN.getNode(id);
        }
        //connect stations to all arrival/departure events at this station
        for (EvalEvent &e: routingEAN.getNodes()){
            if (e.getArtificial()) continue;
            int id = routingEAN.getMaxEdgeId()+1;
            if (e.getType() == ARRIVAL){
                EvalActivity a(id, 0, routingEAN.getNode(e.getId()), routingEAN.station_nodes[e.getStopId()-1]);
                routingEAN.addEdge(a);
            }
            else{ // DEPARTURE
                EvalActivity a(id, 0, routingEAN.station_nodes[e.getStopId()-1],  routingEAN.getNode(e.getId()));
                routingEAN.addEdge(a);
            }
        }
    } catch(const InputFileException& e) {
        // We do not have a od matrix, therefore we will not route in this evaluation
        std::cout << "Did not find OD matrix, skip all passenger routing" << std::endl;
    }


}

// calls the evaluation functions
void evaluation::evaluate(){
	feasible_result = feasible();
	average_weighted_slack_result = average_weighted_slack();
    average_slack_result = average_slack();
	weighted_times_result = weighted_times();
    sum_od_pairs_result = sum_od_pairs();
    perceived_traveling_time_result = perceived_traveling_time();
	if(eval_extended){
        max_changetime_result = max_changetime();
		min_changetime_result = min_changetime();
		average_change_slack_result = average_type_slack(CHANGE);
		average_headway_slack_result = average_type_slack(HEADWAY);
		average_drive_slack_result = average_type_slack(DRIVE);
		average_wait_slack_result = average_type_slack(WAIT);
		number_of_transfers_result = number_of_transfers();
        overcrowded_time_result = overcrowded_time();
	}

	//variance_headway_slack_result = variance_headway_slack();
	//average_travel_time_result = average_travel_time();
	//feasible_head_result = feasible_head();
}

/**
*   Procedures calculating the properties of the EAN.
*   Note that EAN and not routingEAN is used.
*   Thanks to the core quite self-explanatory.
*/
int evaluation::max_changetime(){
	int maxtime = 0;
    for (EvalActivity &a: EAN.getEdges()){
        if (a.getType() != CHANGE || a.getNumberOfPassengers() == 0) continue;
        else maxtime = std::max(maxtime, a.getDuration());
    }
	return maxtime;
}

int evaluation::min_changetime(){
    int mintime = std::numeric_limits<int>::max();
    for (EvalActivity &a: EAN.getEdges()){
        if (a.getType() != CHANGE || a.getNumberOfPassengers() == 0) continue;
        else mintime = std::min(mintime, a.getDuration());
    }
	return mintime;
}


bool evaluation::feasible(){
    for (EvalActivity &a: EAN.getEdges()){
        if (a.getDuration() < a.getLowerBound()
            || a.getDuration() > a.getUpperBound()){
            return false;
        }
    }
	return true;
}

unsigned long evaluation::weighted_times(){
    double result = 0;
    for (EvalActivity &a: EAN.getEdges()){
        result += a.getDuration() * a.getNumberOfPassengers();
    }
    return result;
}

double evaluation::average_slack(bool weighted){
	double result = 0;
    double divisor = 0;
    for (EvalActivity &a: EAN.getEdges()){
        double slack =  double(a.getDuration() - a.getLowerBound());
        if (weighted) result += slack *  double(a.getNumberOfPassengers());
        else result += slack;
        if (weighted) divisor += double(a.getNumberOfPassengers());
        else divisor++;
    }
    if (divisor == 0) return 0.0;
	return result / divisor;
}
double evaluation::average_weighted_slack(){
    return average_slack(true);
}

double evaluation::average_type_slack(ActivityType type){
	double result = 0;
	double counter = 0;
    for (EvalActivity &a: EAN.getEdges()){
        if (a.getType() != type) continue;
        result += double(a.getDuration() - a.getLowerBound())
                * double(a.getNumberOfPassengers());
        counter += double(a.getNumberOfPassengers());
    }
	if(counter == 0) return 0;
	else return result/counter;
}

double evaluation::number_of_transfers(){
	double number_of_transfers = 0;
    for (EvalActivity &a: EAN.getEdges()){
        if (a.getType() != CHANGE) continue;
        else number_of_transfers += a.getNumberOfPassengers();
    }
	return number_of_transfers;
}

double evaluation::variance_headway_slack(){
	double result = 0;
	int counter = 0;
	double average = average_type_slack(HEADWAY);
    for (EvalActivity &a: EAN.getEdges()){
        if (a.getType() != HEADWAY) continue;
        result += square(double(a.getDuration() - a.getLowerBound()) - average);
        counter++;
    }
	if(counter == 0) return 0;
	else return result/counter;
}
/**
*   Calculates the sum of OD pairs, i.e. the sum of traveling times from each
*   station to each other station. The traveling times are weighted with the
*   amount of passengers, meaning the OD-matrix entry.
*/
double evaluation::sum_od_pairs(){
	long double result = 0;
	long double passengers = 0;
    for (EvalEvent* station_from: routingEAN.station_nodes){
        int number_stations = routingEAN.station_nodes.size();
        std::vector<int> distances;
        routingEAN.getAllRouting(*station_from, distances, number_stations);
        for (EvalEvent* station_to: routingEAN.station_nodes){
            if (station_from->getId() == station_to->getId()) continue;
            double passenger = od.getValue(station_from->getStopId(), station_to->getStopId());
            int res = distances[station_to->getId()];
            if (res == std::numeric_limits<int>::max()){
                std::cout << "Watch out: No connection from " <<
                    station_from->getStopId() << " to " <<
                    station_to->getStopId() << " possible." << std::endl;
            }
            else {
                result +=  res * passenger;
                passengers += passenger;
            }
        }
    }

	if(passengers == 0) return result;
	else return result/passengers;
}

double evaluation::overcrowded_time() {
    long double result = 0;
    long double passengers = od.computeNumberOfPassengers();
    for (EvalActivity& a: routingEAN.getEdges()) {
        if (a.getType() == DRIVE || a.getType() == WAIT) {
            if (a.getNumberOfPassengers() > capacity) {
                result += a.getDoubleDuration() * a.getNumberOfPassengers();
            }
        }
    }
    return result / passengers;
}

/**
*   The perceived traveling time differs from the actual travel times by adding
*   a penalty for each change a customer has to take. Therefore the change
*   activities are modified, the sum of od-pairs is being calculated, and
*   after that the change penalty is substracted again from the activity
*   duration.
*/
double evaluation::perceived_traveling_time()
{
    for (EvalActivity &a: routingEAN.getEdges()){
        if (a.getType() == CHANGE)
            routingEAN.getEdge(a.getId())->setDuration(routingEAN.getEdge(a.getId())->getDuration() + change_penalty);
    }
    double ret = evaluation::sum_od_pairs();
    for (EvalActivity &a: routingEAN.getEdges()){
        if (a.getType() == CHANGE)
            routingEAN.getEdge(a.getId())->setDuration(routingEAN.getEdge(a.getId())->getDuration() - change_penalty);
    }
    return ret;
}


void evaluation::results_to_statistic(Statistic &stat)
{
    stat.setBooleanValue("tim_feasible", feasible_result);
    stat.setDoubleValue("tim_obj_ptt1", weighted_times_result); // former tim_weighted_times
    stat.setDoubleValue("tim_obj_slack_average", average_slack_result); // former tim_weighted_slack
    if (routingEAN.getEdges().size() > 0) {
        // Only add these values if we routed beforehand
        stat.setDoubleValue("tim_perceived_time_average", perceived_traveling_time_result);
        stat.setDoubleValue("tim_time_average", sum_od_pairs_result); // former tim_sum_od_pairs
    }
    if(eval_extended){
        stat.setDoubleValue("tim_obj_slack_drive_average", average_drive_slack_result); // former tim_weighted_change_slack
		stat.setDoubleValue("tim_obj_slack_wait_average", average_wait_slack_result); // former tim_average_headway_slack
		stat.setDoubleValue("tim_obj_slack_change_average", average_change_slack_result); // former tim_weighted_change_slack
		stat.setDoubleValue("tim_obj_slack_headway_average", average_headway_slack_result); // former tim_average_headway_slack
		stat.setIntegerValue("tim_prop_changes_od_max", max_changetime_result); // former tim_max_changetime
		stat.setIntegerValue("tim_prop_changes_od_min", min_changetime_result); // former tim_max_changetime
		stat.setDoubleValue("tim_number_of_transfers", number_of_transfers_result);
		stat.setDoubleValue("tim_overcrowded_time_average", overcrowded_time_result);
	}

    //statistic::set_double_value("tim_average_travel_time", average_travel_time_result);
	//statistic::set_double_value("tim_robustness", robustness_result);
	//statistic::set_double_value("tim_variance_headway_slack", variance_headway_slack_result);
	//statistic::set_bool_value("tim_feasible_head", feasible_head_result);
}

//
// double evaluation::average_travel_time()
// {
//     double passengers = 0;
//     for (std::map<std::pair<int,int>,double>::iterator it = OD.begin(); it != OD.end(); ++it)
//         passengers += it->second;
//     return (double)weighted_times_result / passengers;
//
// }

//
// void evaluation::eval_robustness(string robfile)
// {
// 	ifstream rob( robfile.c_str() );
//
// 	if (!rob)
// 	{
// 	    robustness_result = -1;
// 	    return;
// 	}
//
// 	cout<<"File "<<robfile<<" exists!\n";
// 	vector<double> robustness;
//
// 	robustness_result = 0;
// 	int i=0;
// 	string line;
// 	while (!rob.eof())
// 	{
// 		getline(rob,line);
// 		if(line!=""&&(line.c_str())[0]>=48&&(line.c_str())[0]<=57)
// 		{
// 			size_t pos = line.find(";");
// 			line=line.substr(pos+1);
//
// 			robustness_result += atof(line.c_str()) * (activities[i].time - activities[i].min);
// 			++i;
// 		}
// 	}
// 	rob.close();
//
// }
