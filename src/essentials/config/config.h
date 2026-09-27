#ifndef _CONFIG_H
#define _CONFIG_H

#include <string>
#include <map>
#include <set>
#include <fstream>
#include <iostream>
#include <cstdio>
#include <cstdlib>
#include <exception>

#include "../string-helper/string_helper.h"

class config {

	private:

		static std::map<std::string, std::string> settings;
		static void ensure_setting_exists(const std::string setting);

	public:

		static int get_integer_value(const char *setting_name);
		static unsigned int get_unsigned_integer_value(
		        const char *setting_name);
		static double get_double_value(const char *setting_name);
		static bool get_bool_value(const char *setting_name);
		static std::string get_string_value(const char *setting_name);

		static std::set<std::string> get_settings_names();
		static std::map<std::string, std::string> get_settings();
		static void clear();

		static void from_file(const char *filename_config, 
		        bool message_if_successful = true,
		        bool only_if_exists = false);

		static void to_file(const char *filename_config,
		        bool message_if_successful = true);

};

#endif
