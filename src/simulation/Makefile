# VARIABLES (PLEASE SET CORRECT SYSTEM PATHS)
CXX=mpicxx
RHPC_INCLUDE_DIR=../../libs/RHPC/include/repast_hpc
RHPC_LIB_DIR=../../libs/RHPC/lib
BOOST_INCLUDE_DIR=../../libs/boost/include
BOOST_LIB_DIR=../../libs/boost/lib
BOOST_INFIX=
COMPILE_OPTS=-std=c++17 -O0 -c -MMD -MP -g
NUM_PROCS?=4

# DERIVED VARIABLES (DO NOT MODIFY)
INCLUDE_DIRS=-I/usr/include -I"./include" -I$(RHPC_INCLUDE_DIR) -I$(BOOST_INCLUDE_DIR)
LIB_DIRS=-L$(RHPC_LIB_DIR) -L$(BOOST_LIB_DIR) -L/usr/local/lib -L/lib/x86_64-linux-gnu/
LIBS=-lrepast_hpc-2.0 -lboost_mpi$(BOOST_INFIX) -lboost_serialization$(BOOST_INFIX) -lboost_system$(BOOST_INFIX) -lboost_filesystem$(BOOST_INFIX)
SRC_DIR=src
OBJECTS=$(SRC_DIR)/Activity.o $(SRC_DIR)/EANetworkParser.o $(SRC_DIR)/Event.o $(SRC_DIR)/Main.o $(SRC_DIR)/ODParser.o $(SRC_DIR)/PTSimulationModel.o $(SRC_DIR)/Dijkstra.o $(SRC_DIR)/Manager.o $(SRC_DIR)/Passenger.o $(SRC_DIR)/Path.o

# BUILD SIMULATION
.PHONY : build
build:  $(OBJECTS)
	@echo "Building the project..."
	$(CXX) $(LIB_DIRS) -o "PTSimulation" $(OBJECTS) $(LIBS)
	@echo "Done!"

# RUN SIMULATION
.PHONY : start
start:
	@echo "Starting the simulation"
	mpirun -n $(NUM_PROCS) ./PTSimulation ./props/config.props ./props/model.props
	@echo "Finished simulation"

# CLEAN UP
.PHONY : clean
clean:
	rm ./src/*.d
	rm ./src/*.o

# Object files are built from cpp files
%.o: %.cpp include/*.h;
	@echo "Building $@"
	$(CXX) $(INCLUDE_DIRS) -I$(SRC_DIR) $(COMPILE_OPTS) -o $@ $<
