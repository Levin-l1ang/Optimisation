#include "aperiodic.h"
#include "../../../essentials/config/config.h"

using namespace std;

int main(void)
{
	config::from_file("basis/Config.cnf", false);

	aperiodic algo;
	algo.solve(config::get_string_value("default_events_file"),config::get_string_value("default_activities_file"),config::get_string_value("default_timetable_file"));
}
