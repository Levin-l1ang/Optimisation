#include "../../../core/cpp/include/core.hpp"
#include "evaluation.h"

int main (void)
{
    // read config
    Config config;
    ConfigReader configr = ConfigReader(&config, "Config.cnf", false, "basis/");
    configr.read();

    // read statistic
    Statistic stat;
	std::string filename_statistic = config.getStringValue("default_statistic_file");
    StatisticReader statr = StatisticReader(&stat, filename_statistic);
    try {
        statr.read();
    }
    catch (const InputFileException& e) {
        std::cout << "Could not read statistic, continue" << std::endl;
    }
	evaluation eval;

	eval.init(
        config.getStringValue("default_activities_periodic_unbuffered_file"),
        config.getStringValue("default_events_periodic_file"),
        config.getStringValue("default_timetable_periodic_file"),
        config.getStringValue("default_od_file"),
        config.getIntegerValue("period_length"),
        config.getBooleanValue("tim_eval_extended"),
        config.getIntegerValue("ean_change_penalty"),
        config.getIntegerValue("gen_passengers_per_vehicle")
    );
	eval.evaluate();
	//eval.eval_robustness(config.getStringValue("default_activity_buffer_file"));


	eval.results_to_statistic(stat);

    // statistic writer
    StatisticWriter statw;
    statw.writeStatistic(config, stat);
}
