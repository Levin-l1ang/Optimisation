.PHONY: all dependencies clean

SRC_FILES=evaluation.cpp evaluation.h main.cpp
CORE_LOCATION=../../../core/cpp
CORE_LIB=$(CORE_LOCATION)/lib/core.o

all: dependencies evaluation

dependencies:
	make -C $(CORE_LOCATION) all

evaluation: $(CORE_LIB) $(SRC_FILES)
	g++ -o evaluation evaluation.cpp main.cpp $(CORE_LIB) --std=c++17

clean:
	rm -f evaluation
