#ifndef _STATISTICS_H
#define _STATISTICS_H

#include <string>
#include <map>
#include <set>
#include <iostream>
#include <fstream>
#include <cstdio>
#include <cstdlib>
#include <exception>

class statistic {

	private:

		static std::map<std::string, std::string> results;
		static void ensure_result_exists(const std::string result);

	public:

		static int get_integer_value(const std::string& result_name);
		static unsigned int get_unsigned_integer_value(const std::string& result_name);
		static double get_double_value(const std::string& result_name);
		static bool get_bool_value(const std::string& result_name);
		static std::string get_string_value(const std::string& result_name);

		static void set_integer_value(const std::string& result_name, int value);
		static void set_unsigned_integer_value(const std::string& result_name, unsigned int value);
		static void set_double_value(const std::string& result_name, double value);
		static void set_bool_value(const std::string& result_name, bool value);
		static void set_string_value(const std::string& result_name, const std::string& value);

		static void clear();

		static std::set<std::string> get_results_names();
		static std::map<std::string, std::string> get_results();

		static void from_file(const std::string& filename_statistic, bool message_if_successful = true, bool exception_on_fail = false);
		static void to_file(const std::string& filename_statistic, bool message_if_successful = true);

};

#endif
