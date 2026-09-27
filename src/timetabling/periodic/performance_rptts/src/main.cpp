#include "../../../../core/cpp/include/core.hpp"

#include "mergeRequest.hpp"
#include "RpttsLine.hpp"
#include "LineCluster.hpp"
#include "instance.hpp"
#include <iostream>
#include <map>

int main(void){
    instance I = instance();
    I.read_in();

    std::cout << "initializing..." << std::endl;
    // initailize EAN
    I.initializeEAN();

    // initialize Line Cluster
    I.initializeLineClusters();

    std::cout << "initialized. Start solving heuristically.." << std::endl;
    // solve by heuristic
    I.solve_heuristically();
    std::cout << "..solved!" << std::endl;


    I.write_timetable();
    std::cout << "Timetable written!" << std::endl;
}