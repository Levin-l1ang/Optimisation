/**
 * \file PTSimulationModel.h
 * The model class for the simulation
 */

#ifndef INCLUDE_PTSIMULATIONMODEL_H_
#define INCLUDE_PTSIMULATIONMODEL_H_

#include "Properties.h"
#include "Schedule.h"
#include "SharedContext.h"
#include "SharedNetwork.h"
#include "Activity.h"
#include "Manager.h"
#include "Event.h"
#include "SourceDelayMessage.h"
#include "Passenger.h"
#include "ActivityContentManager.h"

/**
 * The model for the simulation. It contains everything.
 */
class PTSimulationModel{
private:
  int delay_strategy_;
  int debug_level_;
  int stop_at_;
  int count_of_passengers_;
  int stranded_penalty_;
  double offline_passenger_share_;
  int random_seed_;
  repast::Properties * props_;
  repast::SharedContext<MessagingAgent> context_;
  ActivityContentManager * edge_content_manager_;
  std::map<int, std::unordered_set<SourceDelayMessage>> delay_map_;
  std::set<double> past_ticks_;
  Manager* manager_;
  EventPackageProvider* event_provider_;
  EventPackageReceiver* event_receiver_;
  PassengerPackageProvider* passenger_provider_;
  PassengerPackageReceiver* passenger_receiver_;

  /**
   * Process all delays for the given tick out of the delay_map_ by calling their manager. The delays will be deleted from the delay_map_ afterwards.
   * @param tick the tick to proceed.
   */
  void process_delays(int tick);

  /**
   * Find all the events out of the context with the given tick. The events will be added to the provided event set.
   * @param events the set to add the events to
   * @param tick the tick to process
   */
  void find_events_per_tick(std::unordered_set<Event *> & events, int tick);

  /**
   * Advance all passengers who currently are on an activity ending in any event out of the given event set.
   * @param events_to_process determines the advanced passengers
   */
  void advance_passengers(std::unordered_set<Event *>& events_to_process);

  /**
   * Evaluate the passenger paths and returns the results as a struct
   * @return the results of the evaluation
   */
  struct EvaluationResult evaluate_passenger_paths();

public:
  /**
   * Create a new model
   * @param propsFile the location of the property file
   * @param argc the number of arguments given for the original start of the programm
   * @param argv the arguments given for the original start of the program
   * @param comm the communicator to use
   */
  PTSimulationModel(std::string propsFile, int argc, char** argv, boost::mpi::communicator* comm);
  ~PTSimulationModel(){
    delete props_;
    delete edge_content_manager_;
    delete manager_;
    delete event_provider_;
    delete event_receiver_;
  }
  /**
   * Initialize the schedule for the simulation.
   */
  void initSchedule(repast::ScheduleRunner&);
  /**
   * Execute the next event at the current time in the simulation.
   */
  void executeEvent();
  /**
   * Initialize the simulation. Parses all the input and creates the simulation entities.
   */
  void init();

  /**
   * Evaluate the end state of the simulation in terms of stranded passengers and travelling time. Writes the output to the file record.csv.
   */
  void evaluate();

  /**
   * Returns the debug level of the simulation. Controls the amount of output. 0 for none, 1 for normal, 2 for all.
   * @return the debug level
   */
  int debug_level(){ return debug_level_;}

  /**
   * Return a map of all source delays in the model.
   * @return a map of all source delays
   */
  std::map<int, std::unordered_set<SourceDelayMessage>> * delay_map() {
    return &delay_map_;
  }

  /**
   * Return the context of the simulation
   * @return the context of the simulation
   */
  repast::SharedContext<MessagingAgent> * context(){
    return &context_;
  }

  /**
   * Print the result of the agent monitoring
   */
  void printMonitoringResults();



};

/**
 * The results of the evaluation of the passenger paths.
 */
struct EvaluationResult{
  /**
   * The weighted travel time of all passengers
   */
  unsigned long long traveling_time;
  /**
   * The number of stranded passengers, i.e. passengers which could not reach their target in the simulated time frame
   */
  int count_of_stranded_passengers;
};



#endif /* INCLUDE_PTSIMULATIONMODEL_H_ */
