/**
 * \file Dijkstra.cpp
 * Implementation of a Dijkstra algorithm
 */

#include <iostream>
#include "PathCalculator.h"
#include "Activity.h"

Path Dijkstra::calculatePath(Event & source, int target_station_id){
  //Predecessor map to reconstruct the path
  std::map<Event, Event> predecessors;
  //The heap to choose the nearest unfixed event in each step. compare_node builds a min-heap
  boost::heap::fibonacci_heap<Node, boost::heap::compare<Compare_node>> heap;
  //The labels of the events, i.e. is a event fixed yet. Just used to determine whether a specific event was already reached.
  std::map<Event, Label> labels;
  heap.push(Node(source, 0));
  labels[source] = Label::TEMP;
  Event current_event;
  Event act_target;
  Node current_node;
  int current_distance;
  int temp_distance;
  while(!heap.empty()){
    current_node = heap.top();
    current_event = current_node.event;
    if(current_event.station() == target_station_id){
      //Found shortest path to target
      return reconstructPath(source, current_event, predecessors);
    }
    heap.pop();
    labels[current_event] = Label::PERM;
    current_distance = current_node.distance;
    for(std::unordered_set<Activity *>::iterator it = current_event.outgoing_activities()->begin(); it!=current_event.outgoing_activities()->end(); it++){
      if((*it)->type()=="\"headway\""){
        continue;
      }
      act_target = *((*it)->target());
      temp_distance = current_distance + ((*it)->target()->time()-current_event.time());
      //Just one case is necessary because every path between two events has the same length!
      if(labels.find(act_target) == labels.end()){
        labels[act_target] = Label::TEMP;
        heap.push(Node(act_target, temp_distance));
        predecessors[act_target] = current_event;
      }
    }
  }
  //If this happens, target was not reached so there is no path from source to target!
  //std::cerr << "Trying to find a way from " << source << " to station " << target_station_id << " but there is none!" << std::endl;
  throw std::runtime_error("Unreachable target");
}

Path Dijkstra::reconstructPath(Event & source, Event & target, std::map<Event, Event> & predecessors){
  Path new_path = Path();
  Event current_event;
  Event predecessor = target;
  //Build the path iteratively
  while(predecessor != source){
    current_event = predecessor;
    predecessor = predecessors.at(current_event);
    for(std::unordered_set<Activity *>::iterator it = current_event.incoming_activities()->begin(); it!=current_event.incoming_activities()->end(); it++){
      if((*(*it)->source())==predecessor){
        new_path.addToFront(*it);
        break;
      }
    }
  }
  //The first activity is already added to the path! Can just return
  return new_path;
}




