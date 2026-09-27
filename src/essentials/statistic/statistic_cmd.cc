/*
 * Like config, but the other way round.
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

#include "statistic.h"

void helpmessage(){

	std::cerr << "statistic_cmd"                                                     << std::endl
		<< "statistic_cmd stands for \"command line statistic interface\""      << std::endl
		<< "and is able to read certain statistic files."                       << std::endl
		                                                                        << std::endl
		<< "Options:"                                                           << std::endl
		<< "-c STATISTIC_FILE       file containing the statistic"              << std::endl
		<< "-s RESULT_NAME          desired result name"                        << std::endl
		<< "-v VALUE                set result to value"                        << std::endl
		<< "-t TYPE                 force result to be of certain type"         << std::endl
		<< "-d STATISTIC_FILE       dumps statistic to file"                    << std::endl
		<< "-p STATISTIC_FILE       print results that differ"                  << std::endl
		<< "-u                      do not escape strings"                      << std::endl
		<< "-n                      outputs all result names available"         << std::endl
		<< "-h                      show this help message"                     << std::endl
		                                                                        << std::endl
		<< "TYPE can be \"integer\", \"unsigned_integer\", \"double\", "        << std::endl
		<< "\"bool\" and \"string\". If no TYPE is given, \"string\" is "       << std::endl
		<< "assumed. If a value is given, -d will dump the modified file."      << std::endl
		                                                                        << std::endl
		<< "In case of entries found for given result-name, the returned"       << std::endl
		<< "value is 0, otherwise 1."                                           << std::endl;


}

void usagemessage(){

	std::cerr << std::endl << "Type \"statistic_cmd -h\" to get more information on correct usage." << std::endl;

}

int main(int argc, char **argv){

	std::string filename_statistic,
	       filename_dump,
	       filename_compare,
	       result_name,
	       result_value,
	       type = "string";

	bool filename_statistic_given = false,
	     filename_dump_given = false,
	     filename_compare_given = false,
	     result_name_given = false,
	     result_value_given = false,
	     type_given = false,
	     output_results_names = false,
	     escape_strings = true;

	int option;

	opterr = 0;

	try{
	
		while((option = getopt(argc, argv, "hnuc:d:p:s:t:v:")) != -1){

			switch(option){
				case 'c':
					filename_statistic = std::string(optarg);
					if(filename_statistic_given){
					    std::stringstream error;
						error << "more than one file with -c given";
						throw std::invalid_argument(error.str());
					}
					filename_statistic_given=1;
					break;
				case 'd':
					filename_dump = std::string(optarg);
					if(filename_dump_given){
					    std::stringstream error;
						error << "more than one file with -d given";
						throw std::invalid_argument(error.str());
					}
					filename_dump_given=1;
					break;
				case 'p':
					filename_compare = std::string(optarg);
					if(filename_compare_given){
					    std::stringstream error;
						error << "more than one file with -p given";
						throw std::invalid_argument(error.str());
					}
					filename_compare_given=1;
					break;
				case 's':
					result_name = std::string(optarg);
					if(result_name_given){
					    std::stringstream error;
						error << "more than one result-name given";
						throw std::invalid_argument(error.str());
					}
					result_name_given=true;
					break;
				case 'v':
					result_value = std::string(optarg);
					if(result_value_given){
					    std::stringstream error;
						error << "more than one result-value given";
						throw std::invalid_argument(error.str());
					}
					result_value_given=true;
					break;
				case 't':
					type = std::string(optarg);
					if(type_given){
					    std::stringstream error;
						error << "more than one type given";
						throw std::invalid_argument(error.str());
					}
					type_given=true;
					break;
				case 'n':
					if(output_results_names){
					    std::stringstream error;
						error << "-n flag set several times" << std::endl;
						throw std::invalid_argument(error.str());
					}
					output_results_names=true;
					break;
				case 'u':
					if(!escape_strings){
					    std::stringstream error;
						error << "-u flag set several times" << std::endl;
						throw std::invalid_argument(error.str());
					}
					escape_strings=false;
					break;
				case 'h':
					helpmessage();
					return 0;
				case '?':
					if(optopt == 'c' || optopt == 's' || optopt == 't' ||
					        optopt == 'd' || optopt == 'p' || optopt == 'v'){
					    std::stringstream error;
					    error << "option -" << optopt << " requires an argument";
					    throw std::invalid_argument(error.str());
					}

					else if (isprint (optopt)){
					    std::stringstream error;
					    error << "unknown option ‘-" << optopt << "’";
					    throw std::invalid_argument(error.str());
					}
					
					else{
					    std::stringstream error;
						error << "unknown option character ‘" << optopt << "’";
					    throw std::invalid_argument(error.str());
					}
			}

		}
		
		if (optind < argc){
		    std::stringstream error;
			error << "argument \"" << argv[optind] << "\" invalid";
			throw std::invalid_argument(error.str());
		}

		if(!filename_statistic_given){
            std::stringstream error;
			error << "no statistic file given";
			throw std::invalid_argument(error.str());
		}

		statistic::from_file(filename_statistic.c_str(), false);

		if(result_value_given){
            statistic::set_string_value(result_name, result_value);
        }

		if(filename_dump_given){
            statistic::to_file(filename_dump.c_str(), false);

            return 0;
        }


		if(filename_compare_given){
		    std::map<std::string, std::string> old_results = statistic::get_results();
            statistic::clear();
            std::set<std::string> changed_entries = std::set<std::string>();
		    statistic::from_file(filename_compare.c_str(), false);
		    std::map<std::string, std::string> results = statistic::get_results();

            // Perhaps there is an easier way, but this way is secure, since
            // the documentation is a bit obscure.
            for(std::map<std::string, std::string>::iterator itr = old_results.begin();
                    itr != old_results.end(); itr++){

                std::string key = itr->first;

                std::map<std::string, std::string>::iterator location =
                    results.find(key);

                if(location == results.end()){
                    changed_entries.insert(key);
                }
                else{
                    if(old_results[key] != results[key]){
                        changed_entries.insert(key);
                    }
                    results.erase(location);
                }

            }

            for(std::map<std::string, std::string>::iterator itr = results.begin();
                    itr != results.end(); itr++){
                
                changed_entries.insert(itr->first);
            
            }

			for(std::set<std::string>::iterator itr = changed_entries.begin();
			        itr != changed_entries.end(); itr++){

				std::cout << *itr << std::endl;

			}

			return 0;
        }

		if(output_results_names){
			std::set<std::string> names = statistic::get_results_names();

			for(std::set<std::string>::iterator itr = names.begin();
			        itr != names.end(); itr++){

				std::cout << *itr << std::endl;

			}

			return 0;

		}

		if(!result_name_given){
			std::stringstream error;
			error << "no result name given";
			throw std::invalid_argument(error.str());
		}

		if(type_given){

			if(type != "integer" && type != "unsigned_integer" && type != "double" &&
					type != "bool" && type != "string"){
				std::stringstream error;
				error << "type given unknown";
				throw std::invalid_argument(error.str());
			}

		}

		if(type == "integer"){

			std::cout << statistic::get_integer_value(result_name.c_str()) << std::endl;

		}
		
		else if(type == "unsigned_integer"){

			std::cout << statistic::get_unsigned_integer_value(result_name.c_str()) << std::endl;

		}

		else if(type == "double"){

			std::cout << statistic::get_double_value(result_name.c_str()) << std::endl;

		}

		else if(type == "bool"){

			std::cout << statistic::get_bool_value(result_name.c_str()) << std::endl;

		}

		else {

            // need this here for caught exceptions
		    std::string value = statistic::get_string_value(result_name.c_str());

			if(escape_strings){
			    std::cout << "\"";
			}
			
			std::cout << value;
			    
			if(escape_strings){
			    std::cout << "\"";
			}
			
			std::cout << std::endl;

		}

	}

	catch(std::exception& e){

	    std::cerr << "Error: " << e.what() << "." << std::endl;
	    usagemessage();

		return 1;

	}

	return 0;

}
