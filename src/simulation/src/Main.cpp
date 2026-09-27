/**
 * \file Main.cpp
 * The main class of the project.
 * Just for starting the simulation or calling tests
 */

#include <ctime>
#include <iostream>
#include <boost/mpi.hpp>
#include "PTSimulationModel.h"
#include "ActivityContentManager.h"
#include "EANetworkParser.h"
#include "ODParser.h"
#include "PathCalculator.h"

/**
 * The main function of the project. All the output is only done for rank 0.
 * @param argc The number of arguments provided in the command line
 * @param argv The arguments provided in the command line
 * @return 0 for victory!
 */
int main(int argc, char** argv) {
  //Time for debug output
  std::clock_t begin = clock();

  std::string configFile = argv[1]; // The name of the configuration file is Arg 1
  std::string propsFile = argv[2]; // The name of the properties file is Arg 2

  //Initializing repast...
  boost::mpi::environment env(argc, argv);
  boost::mpi::communicator world;
  repast::RepastProcess::init(configFile);

  //Setting up the model
  PTSimulationModel * model = new PTSimulationModel(propsFile, argc, argv, &world);
  repast::ScheduleRunner& runner = repast::RepastProcess::instance()->getScheduleRunner();
  model->init();
  model->initSchedule(runner);
  int debug_level = model->debug_level();

  //Starting the simulation
  if (repast::RepastProcess::instance()->rank() == 0 && debug_level > 0) {
    std::cout << "Running simulation..." << std::endl;
  }
  runner.run();
  if (repast::RepastProcess::instance()->rank() == 0 && debug_level > 0) {
    std::cout << "Done!" << std::endl;
  }

  //Cleaning up
  delete model;
  if(debug_level > 0){
    std::cout << "Shutting down process " << repast::RepastProcess::instance()->rank() << "...";
  }
  repast::RepastProcess::instance()->done();
  if(debug_level > 0){
    std::cout << "Done!" << std::endl;
  }

  std::clock_t end = clock();
  double elapsed_seconds = double(end-begin) / CLOCKS_PER_SEC;
  if (repast::RepastProcess::instance()->rank() == 0 && debug_level > 0) {
    std::cout << "Elapsed time(seconds): " << elapsed_seconds << std::endl;
  }



  return 0;

}

