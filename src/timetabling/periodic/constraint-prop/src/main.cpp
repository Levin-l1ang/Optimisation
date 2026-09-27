#include <iostream>
#include <string>

#include "conprop.h"
#include "../../../../essentials/config/config.h"

using namespace std;
using namespace conprop;

int main (void)
{
	config::from_file("basis/Config.cnf", false);

	searchmode modus;
	string modus_config(config::get_string_value("tim_cp_sortmode"));
	
	if (modus_config == "UP") 
	{
		modus = UP;
		cout<<"Using upwards DOF search.\n";
	}
	else if (modus_config == "DOWN")
	{
		modus = DOWN;
		cout<<"Using downwards DOF search.\n";
	}
	else if (modus_config == "RANDOM")
	{
		modus = RANDOM;
		cout<<"Using random DOF search.\n";
	}
	else 
	{
		modus = DOWN;
		cout<<"Using upwards DOF search (no parameter found).\n";
	}
	
	prop algorithm(config::get_string_value("default_activities_periodic_file"),config::get_integer_value("period_length"), modus, config::get_bool_value("tim_cp_animate"), config::get_string_value("tim_cp_animate_directory"), config::get_integer_value("tim_cp_seed"));

	if (config::get_integer_value("tim_cp_time_limit") > 0)
		algorithm.set_time_limit(config::get_integer_value("tim_cp_time_limit"));

	if (config::get_bool_value("tim_cp_check_feasibility"))
	{
		cout<<"Running feasibility check heuristic... "<<flush;
		if (algorithm.check_feasibility_heuristic())
			cout<<"nothing found.\n";
		else
		{
			cout<<"found infeasibility! Aborting.\n";
			return 0;
		}
	}

	if (algorithm.solve())
	{
		cout<<"Hurray, we did it!\n";
		algorithm.write(config::get_string_value("default_timetable_periodic_file"));
	}
	else
	{
		cout<<"Sorry: There is no feasible timetable at all!\n";
	}

	return 0;
}
