/**
 * \file PTSimulationModel.cpp
 * The model class for the simulation.
 * Organizes the simulation, i.e. initialization and setting up the schedule
 */

#include "PTSimulationModel.h"
#include "EANetworkParser.h"
#include "Manager.h"

#include <thread>
#include <chrono>
#include <ctime>

PTSimulationModel::PTSimulationModel(std::string propsFile, int argc, char** argv, boost::mpi::communicator* comm) :
    context_(comm) {
  props_ = new repast::Properties(propsFile, argc, argv, comm);
  stop_at_ = repast::strToInt(props_->getProperty("stop_at"));
  count_of_passengers_ = 0;
  debug_level_ = repast::strToInt(props_->getProperty("debug_level"));
  delay_strategy_ = repast::strToInt(props_->getProperty("delay_strategy"));
  stranded_penalty_ = repast::strToInt(props_->getProperty("stranded_penalty"));
  offline_passenger_share_ = repast::strToDouble(props_->getProperty("offline_passenger_share"));
  random_seed_ = repast::strToInt(props_->getProperty("random_seed"));
  edge_content_manager_ = new ActivityContentManager();
  delay_map_ = std::map<int, std::unordered_set<SourceDelayMessage>>();
  manager_ = new Manager();
  event_provider_ = new EventPackageProvider(&context_);
  event_receiver_ = new EventPackageReceiver(&context_);
  passenger_provider_ = new PassengerPackageProvider(&context_);
  passenger_receiver_ = new PassengerPackageReceiver(&context_);
}

void PTSimulationModel::init() {
  std::string data_folder_location = props_->getProperty("data_folder_location");
  std::string event_file_name = data_folder_location + "Events-expanded.giv";
  std::string act_file_name = data_folder_location + "Activities-expanded.giv";
  std::string pass_file_name = data_folder_location + "Passengers.giv";
  std::string od_file_name = data_folder_location + "OD.giv";
  std::string delay_file_name = data_folder_location + "Delays.giv";
  std::map<int, std::unordered_set<Event*>> id_to_event_map;
  repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN =
      EANetworkParser::applyNetwork(event_file_name, act_file_name, &context_, edge_content_manager_, id_to_event_map, manager_);
  //EANetworkParser::applyPassengersWithFixedPaths(pass_file_name, &context_, EAN);
  ODParser OD;
  OD.parse(od_file_name);
  if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 0){
    std::cout << "Reading Passengers...";
  }
  //std::clock_t begin = clock();
  EANetworkParser::applyPassengersByODMatrix(OD, &context_, EAN, id_to_event_map, props_);
  /*std::clock_t end = clock();
  double durationInSeconds = double(end-begin) / CLOCKS_PER_SEC;
  if(debug_level_ > 0 && repast::RepastProcess::instance()->rank() == 0){
    std::ofstream outfile;
    outfile.open("passTimeLog.txt", std::ios_base::app);
    outfile << props_->getProperty("sp_algo") << "; " << durationInSeconds << std::endl;
  }*/

  if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 0){
    std::cout << "Done!" << std::endl;
  }
  if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 0){
    std::cout << "Reading Delays...";
  }
  EANetworkParser::readDelays(delay_map_, delay_file_name, debug_level_);
  if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 0){
    std::cout << "Done!" << std::endl;
  }
    /*int i = 0;
    char hostname[256];
    gethostname(hostname, sizeof(hostname));
      printf("PID %d on %s ready for attach, rank %d \n", getpid(), hostname, repast::RepastProcess::instance()->rank());
      fflush(stdout);
    while(0 == i){
      sleep(5);
    }*/
}

void PTSimulationModel::initSchedule(repast::ScheduleRunner& runner) {
  if (repast::RepastProcess::instance()->rank() == 0 && debug_level_ > 0) {
    std::cout << "Initiating schedule...";
  }
  //First schedule one simulation event for every event time in the EAN
  Event * event;
  for (repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator events_iter = context_.byTypeBegin(
      repast::SharedContext<MessagingAgent>::LOCAL, EVENT);
      events_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, EVENT); events_iter++) {
    try{
      event = dynamic_cast<Event*>(&**events_iter);
      if (event == NULL) {
        throw std::runtime_error("No event but event type!");
      }
      else {
        runner.scheduleEvent(event->time(),
            repast::Schedule::FunctorPtr(
                new repast::MethodFunctor<PTSimulationModel>(this, &PTSimulationModel::executeEvent)));
      }
    }
    catch(...){
      std::cerr << "Was not able to schedule Event " << *event << " on rank " << repast::RepastProcess::instance()->rank() << ". Shutting down." << std::endl;
      throw;
    }
  }
  //Now schedule the times, where the delays get known
  for(std::map<int, std::unordered_set<SourceDelayMessage>>::iterator it = delay_map_.begin(); it != delay_map_.end(); it++){
    try{
      runner.scheduleEvent((*it).first, repast::Schedule::FunctorPtr(new repast::MethodFunctor<PTSimulationModel>(this, &PTSimulationModel::executeEvent)));
    }
    catch(...){
      std::cerr << "Was not able to schedule delay event on rank " << repast::RepastProcess::instance()->rank() <<". Shutting down." << std::endl;
      throw;
    }
  }
  runner.scheduleEvent(stop_at_-1, repast::Schedule::FunctorPtr(new repast::MethodFunctor<PTSimulationModel>(this, &PTSimulationModel::evaluate)));
  runner.scheduleStop(stop_at_);
  if (repast::RepastProcess::instance()->rank() == 0 && debug_level_ > 0) {
    std::cout << "Done!"<< std::endl;
  }
}

void PTSimulationModel::executeEvent() {
  double current_tick = repast::RepastProcess::instance()->getScheduleRunner().currentTick();
//  if(repast::RepastProcess::instance()->rank()==1 && current_tick == 28980){
//    std::this_thread::sleep_for(std::chrono::seconds(900));
//  }
  if(past_ticks_.find(current_tick)!=past_ticks_.end()){
    //The event is already processed. Proceed to next event.
    return;
  }
  if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 0){
    std::cout << "Processing tick " << current_tick << std::endl;
  }
  if(debug_level_ > 1){
    printMonitoringResults();
  }
  //First check if there are any delays to process now
  process_delays(current_tick);
  std::unordered_set<Event *> current_events;
  //Determine the events to process.
  find_events_per_tick(current_events, current_tick);
  if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 1){
    std::cout << " with " << current_events.size() << " events." << std::endl;
  }
  //Now that the events are found, find all incoming activities and advance all agents on them
  advance_passengers(current_events);
  past_ticks_.insert(current_tick);
}

void PTSimulationModel::process_delays(int tick){
  if(delay_map_.find(tick) != delay_map_.end()){
    if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 1){
      std::cout << "Found delays!" << std::endl;
    }
    for(std::unordered_set<SourceDelayMessage>::iterator it = (*delay_map_.find(tick)).second.begin(); it != (*delay_map_.find(tick)).second.end(); it++){
      if(repast::RepastProcess::instance()->rank()==0 && debug_level_ > 1){
        std::cout << "Processing delay" << (*it) << std::endl;
      }
      std::unordered_set<Event> delayed_events;
      (*it).event()->manager()->receive_delay(*it, delay_strategy_, delay_map_, this, delayed_events);
      for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator passenger_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); passenger_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); passenger_iter++){
        Passenger* passenger = dynamic_cast<Passenger*>(&**passenger_iter);
        if(passenger == NULL){
          throw std::runtime_error("No passenger but passenger type!");
        }
        passenger->chooseNewPath(delayed_events, debug_level_);
      }
    }
    delay_map_.erase(tick);
  }
}

void PTSimulationModel::find_events_per_tick(std::unordered_set<Event*>& events, int tick){
  repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator events_iter = context_.byTypeBegin(
        repast::SharedContext<MessagingAgent>::LOCAL, EVENT);
  repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator events_end = context_.byTypeEnd(
      repast::SharedContext<MessagingAgent>::LOCAL, EVENT);
  while (events_iter != events_end) {
    Event * event = dynamic_cast<Event*>(&**events_iter);
    if (event == NULL) {
      throw std::runtime_error("No event but event type!");
    }
    else {
      if (event->time() == tick) {
        events.insert(event);
      }
    }
    events_iter++;
  }
}

void PTSimulationModel::advance_passengers(std::unordered_set<Event *> & events_to_process){
  std::unordered_set<Activity *> current_activities;
  std::unordered_set<Passenger *> local_agents;
  for (std::unordered_set<Event *>::iterator event_it = events_to_process.begin(); event_it != events_to_process.end();
      event_it++) {
    current_activities = *((*event_it)->incoming_activities());
    for (std::unordered_set<Activity *>::iterator act_it = current_activities.begin();
        act_it != current_activities.end(); act_it++) {
      local_agents = *((*act_it)->local_agent_pointer());
      for (std::unordered_set<Passenger *>::iterator agent_it = local_agents.begin();
          agent_it != local_agents.end(); agent_it++) {
        //Check if this is a passenger. Maybe in the future this could also be a Manager?
          if (repast::RepastProcess::instance()->rank() == 0 && debug_level_ > 1) {
            std::cout << "Advancing passenger at event " << **event_it << " --- Passenger: " << **agent_it
                << std::endl;
          }
          (*agent_it)->advance();
      }
    }
  }
}

void PTSimulationModel::evaluate(){
  if(repast::RepastProcess::instance()->rank() == 0){
    if(debug_level_ > 0){
      std::cout << "Evaluating...";
    }
    struct EvaluationResult result = evaluate_passenger_paths();

    props_->putProperty("traveling_time", result.traveling_time);
    props_->putProperty("delay_strategy", delay_strategy_);
    props_->putProperty("stranded_passengers", result.count_of_stranded_passengers);
    std::vector<std::string> key_order;
    key_order.push_back("stop_at");
    key_order.push_back("data_folder_location");
    key_order.push_back("debug_level");
    key_order.push_back("delay_strategy");
    key_order.push_back("traveling_time");
    key_order.push_back("stranded_passengers");
    props_->writeToSVFile("results.csv", key_order);
    if(debug_level_ > 0){
          std::cout << "Done!" << std::endl;
        }
  }
}

struct EvaluationResult PTSimulationModel::evaluate_passenger_paths(){
  struct EvaluationResult result = {0,0};
  //First iterate over all local agents
  long count = 1;
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator local_passenger_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); local_passenger_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); local_passenger_iter++){
    Passenger * passenger = dynamic_cast<Passenger *>(&**local_passenger_iter);
    if(passenger == NULL){
      throw std::runtime_error("No passenger but passenger type!");
    }
    else{
      if(passenger->stranded()){
        result.count_of_stranded_passengers++;
      }
      else{
        result.traveling_time += passenger->current_path().getArrivalTime() - passenger->current_path().getDepartureTime();
      }
    }
  }

  //And now the nonlocal agents
  count = 1;
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator nonlocal_passengers_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::NON_LOCAL, PASSENGER); nonlocal_passengers_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::NON_LOCAL, PASSENGER); nonlocal_passengers_iter++){
    std::cout << "Evaluating nonlocal passenger " << count++ << std::endl;
    Passenger * passenger = dynamic_cast<Passenger *>(&**nonlocal_passengers_iter);
    if(passenger == NULL){
      throw std::runtime_error("No passenger but passenger type!");
    }
    else{
      if(passenger->stranded()){
        result.count_of_stranded_passengers++;
      }
      else{
        result.traveling_time += passenger->current_path().getArrivalTime() - passenger->current_path().getDepartureTime();
      }
    }
  }
  return result;
}

void PTSimulationModel::printMonitoringResults(){
  //First count the passengers
  long number_of_local_passengers = 0;
  long number_of_nonlocal_passengers = 0;
  long number_of_local_events = 0;
  long number_of_nonlocal_events = 0;
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator local_passenger_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); local_passenger_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); local_passenger_iter++){
    number_of_local_passengers++;
  }
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator nonlocal_passengers_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::NON_LOCAL, PASSENGER); nonlocal_passengers_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::NON_LOCAL, PASSENGER); nonlocal_passengers_iter++){
    number_of_nonlocal_passengers++;
  }
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator local_events_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::LOCAL, EVENT); local_events_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, EVENT); local_events_iter++){
    number_of_local_events++;
  }
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator nonlocal_events_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::NON_LOCAL, EVENT); nonlocal_events_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::NON_LOCAL, EVENT); nonlocal_events_iter++){
    number_of_nonlocal_events++;
  }
  std::cout << "Monitoring process before requesting on rank " << repast::RepastProcess::instance()->rank() << ": " << "(" << number_of_local_passengers << ", " << number_of_nonlocal_passengers << ", " << number_of_local_events << ", " << number_of_nonlocal_events << ")"<< std::endl;
  number_of_local_passengers = 0;
  number_of_nonlocal_passengers = 0;
  number_of_local_events = 0;
  number_of_nonlocal_events = 0;
  repast::AgentRequest request(repast::RepastProcess::instance()->rank(), 1-repast::RepastProcess::instance()->rank());
  for(int i = 0; i < repast::RepastProcess::instance()->worldSize(); i++){
    if(i != repast::RepastProcess::instance()->rank()){
      for(int event_id = 1; event_id <= 628; event_id++){
        repast::AgentId id(event_id, i, EVENT);
        id.currentRank(i);
        request.addRequest(id);
      }
    }
  }

  repast::AgentRequest pass_req(repast::RepastProcess::instance()->rank(), 1-repast::RepastProcess::instance()->rank());
  for(int i = 0; i < repast::RepastProcess::instance()->worldSize(); i++){
    if(i != repast::RepastProcess::instance()->rank()){
      for(int passenger_id = 0; passenger_id < 1311; passenger_id++){
        repast::AgentId id(passenger_id, i, PASSENGER);
        id.currentRank(i);
        pass_req.addRequest(id);
      }
    }
  }
  repast::RepastProcess::instance()->requestAgents<MessagingAgent, EventPackage, EventPackageProvider, EventPackageReceiver, EventPackageReceiver>(context_, request, *event_provider_, *event_receiver_, *event_receiver_);
  repast::RepastProcess::instance()->requestAgents<MessagingAgent, PassengerPackage, PassengerPackageProvider, PassengerPackageReceiver, PassengerPackageReceiver>(context_, pass_req, *passenger_provider_, *passenger_receiver_, *passenger_receiver_);
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator local_passenger_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); local_passenger_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, PASSENGER); local_passenger_iter++){
    number_of_local_passengers++;
  }
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator nonlocal_passengers_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::NON_LOCAL, PASSENGER); nonlocal_passengers_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::NON_LOCAL, PASSENGER); nonlocal_passengers_iter++){
    number_of_nonlocal_passengers++;
  }
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator local_events_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::LOCAL, EVENT); local_events_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::LOCAL, EVENT); local_events_iter++){
    number_of_local_events++;
  }
  for(repast::SharedContext<MessagingAgent>::const_state_aware_bytype_iterator nonlocal_events_iter = context_.byTypeBegin(repast::SharedContext<MessagingAgent>::NON_LOCAL, EVENT); nonlocal_events_iter != context_.byTypeEnd(repast::SharedContext<MessagingAgent>::NON_LOCAL, EVENT); nonlocal_events_iter++){
    number_of_nonlocal_events++;
  }
  std::cout << "Monitoring process after requesting on rank " << repast::RepastProcess::instance()->rank() << ": " << "(" << number_of_local_passengers << ", " << number_of_nonlocal_passengers << ", " << number_of_local_events << ", " << number_of_nonlocal_events << ")"<< std::endl;
}

