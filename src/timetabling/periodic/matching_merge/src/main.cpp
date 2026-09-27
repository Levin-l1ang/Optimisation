//
//  main.cpp
//  matching-merge

#include <iostream>
#include <string>

#include "matching_merge.h"

#include "../../../../essentials/config/config.h"

using namespace std;

int main(void) {

    // for LinTim
    config::from_file("basis/Config.cnf", false);
    matching_merge solver(config::get_integer_value("tim_nws_seed"));
    solver.init(config::get_string_value("default_activities_periodic_file"), config::get_string_value("default_events_periodic_file"), config::get_string_value("default_od_file"), config::get_string_value("default_lines_file"), config::get_integer_value("period_length"));
    std::string path_timetable = config::get_string_value("default_timetable_periodic_file");
    

    if(config::get_bool_value("tim_matching_merge_fast")){
		solver.solve_fast();
	} else {
		solver.solve();
	}
    
    solver.print_solution(path_timetable);
   
    return 0;
}
