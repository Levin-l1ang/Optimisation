//
//  main.cpp
//  rptts

#include "solve.h"
#include "../../../../essentials/config/config.h"

int main(void) {


    config::from_file("basis/Config.cnf", false);
    path_stops = config::get_string_value("default_stops_file");
    path_edges = config::get_string_value("default_edges_file");;
    path_activities = config::get_string_value("default_activities_periodic_file");
    path_events = config::get_string_value("default_events_periodic_file");
    path_od = config::get_string_value("default_od_file");
    path_lines = config::get_string_value("default_lines_file");
    time_period = config::get_integer_value("period_length");
    path_timetable = config::get_string_value("default_timetable_periodic_file");
    if (get_instance()){
        cout << "Some file is missing. Aborting." << endl;
        return 1;
    }
    else{
        cout << "Instance read in successfully!" << endl;
    }



    solve_instance();

    print_solution();

    cout << "Solution Printed!" << endl;

    return 0;
}
