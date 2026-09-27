/*
 * Reads a configuration, shell interface.
 *
 */

#include <cctype>
#include <cstdio>
#include <errno.h>
#include <unistd.h>
#include <exception>
#include <stdexcept>
#include <sstream>
#include <iostream>

#include "config.h"

void helpmessage(){

	std::cerr << "config_cmd" << std::endl
		<< "config_cmd stands for \"command line configuration interface\""     << std::endl
		<< "and is able to read certain configuration files."                   << std::endl
		                                                                        << std::endl
		<< "Options:"                                                           << std::endl
		<< "-c CONFIG_FILE          file containing the configuration"          << std::endl
		<< "-s SETTING_NAME         desired setting name"                       << std::endl
		<< "-t TYPE                 force setting to be of certain type"        << std::endl
		<< "-d CONFIG_FILE          dumps config to file"                       << std::endl
		<< "-p CONFIG_FILE          print settings that differ"                 << std::endl
		<< "-u                      do not escape strings"                      << std::endl
		<< "-n                      outputs all setting names available"        << std::endl
		<< "-h                      show this help message"                     << std::endl
		                                                                        << std::endl
		<< "TYPE can be \"integer\", \"unsigned_integer\", \"double\", "        << std::endl
		<< "\"bool\" and \"string\". If no TYPE is given, \"string\" is "       << std::endl
		<< "assumed."                                                           << std::endl
		                                                                        << std::endl
		<< "In case of entries found for given setting-name, the returned"      << std::endl
		<< "value is 0, otherwise 1."                                           << std::endl;


}

void usagemessage(){

	std::cerr << "Error: Type \"config_cmd -h\" to get more information on correct usage." << std::endl;

}

int main(int argc, char **argv){

	std::string filename_config,
		   filename_dump,
		   filename_compare,
		   setting_name,
		   type = "string";

	bool filename_config_given = false,
		 filename_dump_given = false,
		 filename_compare_given = false,
		 setting_name_given = false,
		 type_given = false,
		 output_settings_names = false,
		 escape_strings = true,

		 show_usage_message = false;

	int option;

	opterr = 0;

	try{
	
		while((option = getopt(argc, argv, "hnuc:d:p:s:t:")) != -1){

			switch(option){
				case 'c':
					filename_config = std::string(optarg);
					if(filename_config_given){
						std::stringstream error;
						error << "more than one file with -c given" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					filename_config_given=1;
					break;
				case 'd':
					filename_dump = std::string(optarg);
					if(filename_dump_given){
						std::stringstream error;
						error << "more than one file with -d given" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					filename_dump_given=1;
					break;
				case 'p':
					filename_compare = std::string(optarg);
					if(filename_compare_given){
						std::stringstream error;
						error << "more than one file with -p given" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					filename_compare_given=1;
					break;
				case 's':
					setting_name = std::string(optarg);
					if(setting_name_given){
						std::stringstream error;
						error << "more than one setting-name given" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					setting_name_given=true;
					break;
				case 't':
					type = std::string(optarg);
					if(type_given){
						std::stringstream error;
						error << "more than one type given" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					type_given=true;
					break;
				case 'n':
					if(output_settings_names){
						std::stringstream error;
						error << "-n flag set several times" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					output_settings_names=true;
					break;
				case 'u':
					if(!escape_strings){
						std::stringstream error;
						error << "-u flag set several times" << std::endl;
						show_usage_message = true;
						throw std::invalid_argument(error.str());
					}
					escape_strings=false;
					break;
				case 'h':
					helpmessage();
					return 0;
				case '?':
					std::stringstream error;
					show_usage_message = true;
					
					if(optopt == 'c' || optopt == 's' || optopt == 't' ||
							optopt == 'd' || optopt == 'p'){

						error << "option ´-" << optopt 
							<< "´ requires an argument";
					}

					else if (isprint (optopt)){
						error << "unknown option ‘-" << optopt << "’"; 
					}
					
					else{
						error << "unknown option character ‘" <<
							optopt << "’";
					}

					throw std::invalid_argument(error.str());
			}

		}
		
		if (optind < argc){
			std::stringstream error;
			error << "argument \"" << argv[optind] << "\" invalid";
			throw std::invalid_argument(error.str());
		}

		if(!filename_config_given){
			std::stringstream error;
			error << "no configuration file given" << std::endl;
			throw std::invalid_argument(error.str());
		}

		config::from_file(filename_config.c_str(), false);

		if(filename_dump_given){
			config::to_file(filename_dump.c_str(), false);

			return 0;
		}


		if(filename_compare_given){
			std::map<std::string, std::string> old_settings = config::get_settings();
			config::clear();
			std::set<std::string> changed_entries = std::set<std::string>();
			config::from_file(filename_compare.c_str(), false);
			std::map<std::string, std::string> settings = config::get_settings();

			for(std::map<std::string, std::string>::iterator itr = old_settings.begin();
					itr != old_settings.end(); itr++){

				std::string key = itr->first;

				std::map<std::string, std::string>::iterator location =
					settings.find(key);

				if(location == settings.end()){
					changed_entries.insert(key);
				}
				else{
					if(old_settings[key] != settings[key]){
						changed_entries.insert(key);
					}
					settings.erase(location);
				}

			}

			for(std::map<std::string, std::string>::iterator itr = settings.begin();
					itr != settings.end(); itr++){
				
				changed_entries.insert(itr->first);
			
			}

			for(std::set<std::string>::iterator itr = changed_entries.begin();
					itr != changed_entries.end(); itr++){

				std::cout << *itr << std::endl;

			}

			return 0;
		}

		if(output_settings_names){
			std::set<std::string> names = config::get_settings_names();

			for(std::set<std::string>::iterator itr = names.begin();
					itr != names.end(); itr++){

				std::cout << *itr << std::endl;

			}

			return 0;

		}

		if(!setting_name_given){
			std::stringstream error;
			error << "no setting name given" << std::endl;
			throw std::invalid_argument(error.str());
		}

		if(type_given){

			if(type != "integer" && type != "unsigned_integer" && type != "double" &&
					type != "bool" && type != "string"){
				std::stringstream error;
				error << "incorrect type given" << std::endl;
				throw std::invalid_argument(error.str());
			}

		}

		if(type == "integer"){

			std::cout << config::get_integer_value(setting_name.c_str()) << std::endl;

		}
		
		else if(type == "unsigned_integer"){

			std::cout << config::get_unsigned_integer_value(setting_name.c_str()) << std::endl;

		}

		else if(type == "double"){

			std::cout << config::get_double_value(setting_name.c_str()) << std::endl;

		}

		else if(type == "bool"){

			std::cout << config::get_bool_value(setting_name.c_str()) << std::endl;

		}

		else {

			if(escape_strings){
				std::cout << "\"";
			}
			
			std::cout << config::get_string_value(setting_name.c_str());
				
			if(escape_strings){
				std::cout << "\"";
			}
			
			std::cout << std::endl;

		}

	}

	catch(std::exception& e){

	    std::cerr << "Error: " << e.what() << "." << std::endl;

		if(show_usage_message){
			usagemessage();
		}

		return 1;

	}

	return 0;

}
