/**
 * \file EANetworkParser.cpp
 * The Implementation of the parser
 * Contains the methods for parsing the EAN and the passengers.
 */

#include <iostream>
#include <iterator>
#include <fstream>
#include <stdlib.h>
#include "RepastProcess.h"
#include "EANetworkParser.h"
#include "Passenger.h"
#include "PathCalculator.h"
#include "StringHelper.h"

repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>* EANetworkParser::applyNetwork(
    std::string event_file_name, std::string act_file_name, repast::SharedContext<MessagingAgent> * context,
    ActivityContentManager * edgeContentManager, std::map<int, std::unordered_set<Event*>>& id_to_event_map, Manager* manager) {
  //First create the agents and add them to the context. Therefore check the
  //number of agents to create to split them on all the processes
  std::ifstream event_file;
  event_file.open(event_file_name.c_str(), std::ifstream::in);
  std::string cur_line;
  std::vector<std::string> entries;
  int rank = repast::RepastProcess::instance()->rank();
  repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>* EAN =
      new repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>("EAN", true,
          edgeContentManager);
  int event_id;
  int station_id;
  int time;
  double weight;
  repast::AgentId agent_id;
  if (event_file.is_open()) {
    while (getline(event_file, cur_line)) {
      StringHelper::trimComment(cur_line);
      if (cur_line.empty()) {
        continue;
      }
      entries = StringHelper::split(cur_line);
      event_id = std::stoi(entries[0]);
      station_id = std::stoi(entries[5]);
      time = std::stoi(entries[3]);
      weight = std::stod(entries[4]);
      agent_id = repast::AgentId(event_id, rank, EVENT);
      Event * cur_event_ptr = new Event(agent_id, event_id, station_id, time, weight, manager);
      if(entries[2] == "\"departure\""){
        id_to_event_map[station_id].insert(cur_event_ptr);
      }
      context->addAgent(cur_event_ptr);
    }
  }
  else { //The event file could not be opened.
    std::cerr << "Could not open event file " << event_file_name << std::endl;
    throw std::runtime_error("Could not open event file!");
  }
  context->addProjection(EAN);
  //Now the events are read in, lets get to the activities!
  std::ifstream act_file;
  act_file.open(act_file_name.c_str(), std::ifstream::in);
  Event * tail_event;
  Event * head_event;
  int tail_event_index;
  int head_event_index;
  repast::AgentId tail_event_id;
  repast::AgentId head_event_id;
  //First remove all activities from the activity map, that might already be there from an old readin
  Activity act;
  act.act_by_id_map_ptr()->clear();
  act.act_map_ptr()->clear();
  if (act_file.is_open()) {
    while (getline(act_file, cur_line)) {
      StringHelper::trimComment(cur_line);
      if (cur_line.empty()) {
        continue;
      }
      entries = StringHelper::split(cur_line);
      if (entries.size() != 7) {
        std::cerr << "There were " << entries.size() << "entries in the line " << cur_line << "instead of 7!"
            << std::endl;
        throw std::runtime_error("Invalid format of the activity file!");
      }
      //Get the corresponding events from the event_map
      tail_event_index = stoi(entries[3]);
      head_event_index = stoi(entries[4]);
      tail_event_id = repast::AgentId(tail_event_index, rank, EVENT);
      head_event_id = repast::AgentId(head_event_index, rank, EVENT);
      tail_event = dynamic_cast<Event*>(context->getAgent(tail_event_id));
      head_event = dynamic_cast<Event*>(context->getAgent(head_event_id));
      //Create the new activity
      Activity * new_act = new Activity(stoi(entries[0]), stoi(entries[5]), entries[2], stod(entries[6]), tail_event,
          head_event);
      boost::shared_ptr<Activity> act_pointer(new_act);
      EAN->addEdge(act_pointer);
    }
  }
  else { //The activity file could not be opened.
    std::cerr << "Could not open activity file " << act_file_name << std::endl;
    throw std::runtime_error("Could not open activity file!");
  }
  return EAN;
}

void EANetworkParser::applyPassengersByODMatrix(ODParser & OD, repast::SharedContext<MessagingAgent> * context,
      repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN,
      std::map<int, std::unordered_set<Event*>>& id_to_event_map, repast::Properties* props){
  int rank = repast::RepastProcess::instance()->rank();
  int world_size = repast::RepastProcess::instance()->worldSize();
  int debug_level = repast::strToInt(props->getProperty("debug_level"));
  int random_seed = repast::strToInt(props->getProperty("random_seed"));
  double offline_passenger_share = repast::strToDouble(props->getProperty("offline_passenger_share"));
  int sp_algo = repast::strToInt(props->getProperty("sp_algo"));
  srand(random_seed);
  std::vector<Event*> event_vector;
  shortest_pathsv sp;
  if(sp_algo == VIBHOR_DIJKSTRA){
    sp.read_events_periodic(props->getProperty("data_folder_location") + "/Events-periodic.giv");
    sp.read_activities_periodic(props->getProperty("data_folder_location") + "/Activities-periodic.giv");
    sp.read_events_expanded(props->getProperty("data_folder_location") + "/Events-expanded.giv");
    sp.read_activities_expanded(props->getProperty("data_folder_location") + "/Activities-expanded.giv");
    sp.sort_expanded_events();
  }
  long number_of_passengers = OD.numberOfPassengers();
  double passengers_per_rank = ((double) number_of_passengers)/((double)world_size);
  if(rank == 0 && debug_level > 0){
    std::cout << "Have " << number_of_passengers << " passengers in total, i.e. " << passengers_per_rank << " per rank." << std::endl;
  }
  std::vector<long> actual_number_of_passengers_per_rank(world_size);
  std::vector<std::list<ODEntry>> passenger_distribution(world_size);
  distributeLoadOnRanks(OD, passenger_distribution, actual_number_of_passengers_per_rank, props);
  long sum_of_already_created_passengers = 0;
  double next_output_step = 0;
  int count_online = 0;
  int count_offline = 0;
  int passenger_count = 0;
  for(std::list<ODEntry>::iterator pass_it = passenger_distribution[rank].begin(); pass_it != passenger_distribution[rank].end(); pass_it++){
    int current_origin_id = (*pass_it).origin_id;
    int current_destination_id = (*pass_it).destination_id;
    int current_od_weight = (*pass_it).weight;
    if(current_od_weight == 0){
      continue;
    }
    //Determine how many departure events have paths to the destination_id station
    std::vector<Path> new_paths;
    int count_events;
    if(sp_algo == HEAP_DIJKSTRA){
      count_events = calculatePaths(new_paths, current_origin_id, current_destination_id, id_to_event_map);
    }
    else if(sp_algo == VIBHOR_DIJKSTRA){
      count_events = calculatePaths(new_paths, current_origin_id, current_destination_id, id_to_event_map, sp, *context, EAN);
    }
    else{
      throw std::runtime_error("Unknown sp_algo" + to_string(sp_algo));
    }
    //Now there are count_events possible paths. Divide the passengers on these paths.
    if(count_events == 0){
      //This can happen, if the rollout time frame is too short.
      continue;
    }
    int passengers_per_event = current_od_weight / count_events;
    int number_of_remaining_passengers = current_od_weight % count_events;
    for(int index = 0; index < count_events ; index++){
      for(int i = 0; i < passengers_per_event; i++){
        repast::AgentId new_id(passenger_count++, rank, PASSENGER);
        //Determine the persona of the new passenger
        int persona;
        if( (rand() / (double) RAND_MAX) <= offline_passenger_share){
          persona = OFFLINE;
          count_offline++;
        }
        else{
          persona = ONLINE;
          count_online++;
        }
        Passenger * new_pass = new Passenger(new_id, new_paths[index], new_paths[index].getFirst(), current_destination_id, persona);
        context->addAgent(new_pass);
        new_paths[index].getFirst()->addAgent(new_pass);
      }
      if(index < number_of_remaining_passengers){
        repast::AgentId new_id(passenger_count++, rank, PASSENGER);
        //Determine the persona of the new passenger
        int persona;
        if( (rand() / (double) RAND_MAX) <= offline_passenger_share){
          persona = OFFLINE;
          count_offline++;
        }
        else{
          persona = ONLINE;
          count_online++;
        }
        Passenger * new_pass = new Passenger(new_id, new_paths[index], new_paths[index].getFirst(), current_destination_id, persona);
        context->addAgent(new_pass);
        new_paths[index].getFirst()->addAgent(new_pass);
      }
    }
    sum_of_already_created_passengers += current_od_weight;
    if(debug_level > 0 && sum_of_already_created_passengers / ((double) actual_number_of_passengers_per_rank[rank]) >= next_output_step){
      std::cout << "Rank " << rank << " has already assigned " << sum_of_already_created_passengers << " of " <<
          actual_number_of_passengers_per_rank[rank] << " passengers (>= " << next_output_step*100 << "%)." << std::endl;
      next_output_step += 0.05;
    }
  }
  if(debug_level > 0){
    std::cout << "Created " << count_offline + count_online << " passengers on rank " << repast::RepastProcess::instance()->rank() <<
        "(" << count_online << ", " << count_offline << "). Intended offline share was " << offline_passenger_share << "." << std::endl;
  }
  /*if(rank==0){
    std::cout << "Elapsed time in microseconds: " << duration.count() << std::endl;
  }*/
}

void EANetworkParser::readDelays(std::map<int, std::unordered_set<SourceDelayMessage>> & delay_map, std::string delay_file_name, int debug_level){
  std::ifstream delay_file;
  delay_file.open(delay_file_name.c_str(), std::ifstream::in);
  std::string cur_line;
  std::vector<std::string> entries;
  Event * event_to_delay_ptr;
  std::map<int, Activity*> * act_by_id_map_ptr = Activity::act_by_id_map_ptr();
  std::map<int, std::unordered_set<SourceDelayMessage>>::iterator find_iter;
  if(delay_file.is_open()){
    while (getline(delay_file, cur_line)) {
      StringHelper::trimComment(cur_line);
      if (cur_line.empty()) {
        continue;
      }
      if(repast::RepastProcess::instance()->rank()==0 && debug_level > 1){
        std::cout << "Reading delay " << cur_line << std::endl;
      }
      entries = StringHelper::split(cur_line);
      event_to_delay_ptr = act_by_id_map_ptr->at(stoi(entries[0]))->target();
      //Check if there is already a delay at the specified time
      find_iter = delay_map.find(event_to_delay_ptr->time());
      if(find_iter == delay_map.end()){
        std::unordered_set<SourceDelayMessage> new_set;
        delay_map.insert(std::make_pair(event_to_delay_ptr->time(), new_set));
        delay_map.at(event_to_delay_ptr->time()).insert(SourceDelayMessage(event_to_delay_ptr, stoi(entries[1])));
      }
      //There is already a delay at this specific time, now check if the event is already present
      else{
    	  bool found_event=false;
    	  SourceDelayMessage delay_message(NULL,0);
    	  for(std::unordered_set<SourceDelayMessage>::iterator delay_it = (*find_iter).second.begin(); delay_it != (*find_iter).second.end(); delay_it++){
    		  if(*((*delay_it).event())==*event_to_delay_ptr){
    			  found_event=true;
    			  delay_message = *delay_it;
    			  break;
    		  }
    	  }
    	  if(!found_event){
    		  delay_map.at(event_to_delay_ptr->time()).insert(SourceDelayMessage(event_to_delay_ptr, stoi(entries[1])));
    	  }
    	  else{
    		  //Check if the delay in the new delay is greater than the present one. If not, just use the old one and continue
    		  if(delay_message.delay() < stoi(entries[1])){
    			  (*find_iter).second.erase(delay_message);
    			  (*find_iter).second.insert(SourceDelayMessage(event_to_delay_ptr, stoi(entries[1])));
    		  }
    	  }
      }
    }
  }
  else { //The delay file could not be opened.
    std::cerr << "Could not open delay file " << delay_file_name << std::endl;
    throw std::runtime_error("Could not open delay file!");
  }
  if(repast::RepastProcess::instance()->rank()==0 && debug_level > 1){
    std::cout << "Read the following delays: " << std::endl;
    for(std::map<int, std::unordered_set<SourceDelayMessage>>::iterator it = delay_map.begin(); it != delay_map.end(); it++){
      for(std::unordered_set<SourceDelayMessage>::iterator delay_it = (*it).second.begin(); delay_it != (*it).second.end(); delay_it++){
        std::cout << "Time: " << (*it).first << ", Delay: " << *delay_it << std::endl;
      }
    }
  }

}

void EANetworkParser::distributeLoadOnRanks(ODParser & OD, std::vector<std::list<ODEntry>>& passenger_distribution,
    std::vector<long>& actual_number_of_passengers_per_rank, repast::Properties* props){
  long sum_of_already_assigned_passengers = 0;
  int debug_level = repast::strToInt(props->getProperty("debug_level"));
  int current_rank = 0;
  int distribution_method = repast::strToInt(props->getProperty("distribution_method"));
  double passengers_per_rank = OD.numberOfPassengers() / (double) repast::RepastProcess::instance()->worldSize();
  //first method: distribute by od entry
  if(distribution_method == FIRST_OD_FIRST_RANK){
    //Just iterate over the od entries and fill the ranks one after one
    if(repast::RepastProcess::instance()->rank() == 0 && debug_level > 0){
      std::cout << "Distribution method: First od first rank" << std::endl;
    }
    for(size_t origin_id = 1; origin_id <= OD.size(); origin_id++){
      for(size_t destination_id = 1; destination_id <= OD.size(); destination_id++){
        int od_entry = OD.getODEntry(origin_id, destination_id);
        ODEntry od = {origin_id, destination_id, od_entry};
        passenger_distribution[current_rank].push_back(od);
        sum_of_already_assigned_passengers += od_entry;
        if(sum_of_already_assigned_passengers >= passengers_per_rank){
          //The current rank is full, change to the next one
          actual_number_of_passengers_per_rank[current_rank] = sum_of_already_assigned_passengers;
          sum_of_already_assigned_passengers = 0;
          current_rank++;
        }
      }
    }
    actual_number_of_passengers_per_rank[current_rank] = sum_of_already_assigned_passengers;
  }
  else if(distribution_method == GREEDY_DISTRIBUTION){
    //Distribute the "heaviest" od entries first, change the ranks evenly
    if(repast::RepastProcess::instance()->rank() == 0 && debug_level > 0){
      std::cout << "Distribution method: Greedy" << std::endl;
    }
    std::vector<ODEntry> od_entries(OD.size()*OD.size());
    //First read every od entry and create a vector to sort
    for(size_t origin_id = 1; origin_id <= OD.size(); origin_id++){
      for(size_t destination_id = 1; destination_id <= OD.size(); destination_id++){
        ODEntry od = {origin_id, destination_id, OD.getODEntry(origin_id, destination_id)};
        od_entries[(origin_id-1)*OD.size() + (destination_id-1)] = od;
      }
    }
    std::sort(od_entries.begin(), od_entries.end(), compByWeight);
    current_rank = 0;
    //Now distribute the od entries
    for(std::vector<ODEntry>::iterator od_it = od_entries.begin(); od_it != od_entries.end(); od_it++){
      passenger_distribution[current_rank%repast::RepastProcess::instance()->worldSize()].push_back(*od_it);
      actual_number_of_passengers_per_rank[current_rank%repast::RepastProcess::instance()->worldSize()] += (*od_it).weight;
      current_rank++;
    }
  }

  if(repast::RepastProcess::instance()->rank() == 0 && debug_level > 0){
    for(size_t i = 0; i < (unsigned) repast::RepastProcess::instance()->worldSize(); i++){
      int count = 0;
      for(std::list<ODEntry>::iterator pass_it = passenger_distribution[i].begin(); pass_it != passenger_distribution[i].end(); pass_it++){
        count += (*pass_it).weight;
      }
      std::cout << "Rank " << i << ": " << count << " Passengers." << std::endl;
    }
  }
}

int EANetworkParser::calculatePaths(std::vector<Path>& shortest_paths, int origin_id, int destination_id,
    std::map<int, std::unordered_set<Event*>>& id_to_event_map){
  int count_events = 0;
  shortest_paths = std::vector<Path>(id_to_event_map[origin_id].size());
  std::vector<Event*> event_vector(id_to_event_map[origin_id].begin(), id_to_event_map[origin_id].end());
  std::sort(event_vector.begin(), event_vector.end(), compByTime);
  for(std::vector<Event*>::reverse_iterator it = event_vector.rbegin(); it!=event_vector.rend(); it++){
    try{
      Path shortest_path;
      shortest_path = Dijkstra::calculatePath(**it, destination_id);
      if((*it)->event_id() == 8 && destination_id == 7){
        std::cout << "Found shortest path from " << **it << " to destination " << destination_id << std::endl;
        std::cout << "The path is: " << std::endl;
        std::cout << shortest_path << std::endl;
      }
      //Check if the new path arrives earlier than the last path
      if(count_events == 0 || shortest_path.getArrivalTime() < shortest_paths[count_events-1].getArrivalTime()){
        shortest_paths[count_events++] = shortest_path;
      }
    }
    catch(std::runtime_error & e){
      //There was no way, this is possible if the rollout is to short.
    }
  }
  return count_events;
}

int EANetworkParser::calculatePaths(std::vector<Path>& shortest_paths, int origin_id, int destination_id,
    std::map<int, std::unordered_set<Event*>>& id_to_event_map, shortest_pathsv& sp, repast::SharedContext<MessagingAgent>& context,
    repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN){
  int count_events = 0;
  shortest_paths = std::vector<Path>(id_to_event_map[origin_id].size());
  std::vector<Event*> event_vector(id_to_event_map[origin_id].begin(), id_to_event_map[origin_id].end());
  std::sort(event_vector.begin(), event_vector.end(), compByTime);
  for(std::vector<Event*>::reverse_iterator it = event_vector.rbegin(); it!=event_vector.rend(); it++){
    int path_length = sp.dijkstra((*it)->station(), destination_id, (*it)->time());
    if(path_length == -1){
      continue;
    }
    Path shortest_path(sp.getpath((*it)->station(), destination_id), context, EAN);
    //Check if the new path arrives earlier than the last path
    if(count_events == 0 || shortest_path.getArrivalTime() < shortest_paths[count_events-1].getArrivalTime()){
      shortest_paths[count_events++] = shortest_path;
    }
  }
  return count_events;
}

bool EANetworkParser::compByWeight(ODEntry e1, ODEntry e2){
  return e1.weight < e2.weight;
}

