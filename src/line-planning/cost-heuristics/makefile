.PHONY: all dependencies clean

SRC_FILES=CostHeuristic.cpp CostHeuristic.h main.cpp
CORE_LOCATION=../../core/cpp
CORE_LIB=$(CORE_LOCATION)/lib/core.o

all: dependencies cost_heuristics

dependencies:
	make -C ../../core/cpp/ all

cost_heuristics: $(SRC_FILES) $(CORE_LIB)
	g++ -o cost_heuristics CostHeuristic.cpp main.cpp $(CORE_LIB) --std=c++17 -I $(CORE_LOCATION)/include

clean:
	rm -f cost_heuristics
