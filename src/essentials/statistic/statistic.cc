	#include <sstream>
	#include <stdexcept>
	#include <cmath>

	#include "statistic.h"
	#include "../string-helper/string_helper.h"

	using namespace string_helper;

	std::map<std::string, std::string> statistic::results;

	void statistic::ensure_result_exists(const std::string result){

	    if(results.find(result) == results.end()){

		std::stringstream error;
		error << "result name \"" << result << "\" not found";
		throw std::invalid_argument(error.str());

	    }

	}

	int statistic::get_integer_value(const std::string& result_name){

	    std::string strval = std::string(result_name);

	    ensure_result_exists(strval);

		return atoi(results[strval].c_str());

	}

	unsigned int statistic::get_unsigned_integer_value(const std::string& result_name){

	    std::string strval = std::string(result_name);

	    ensure_result_exists(strval);

		return (unsigned int) atoi(results[strval].c_str());

	}

	double statistic::get_double_value(const std::string& result_name){

	    std::string strval = std::string(result_name);

	    ensure_result_exists(strval);

		return atof(results[strval].c_str());

	}

	bool statistic::get_bool_value(const std::string& result_name){

	    std::string strval = std::string(result_name);

	    ensure_result_exists(strval);

		return (unsigned int) atob(results[strval].c_str());

	}

	std::string statistic::get_string_value(const std::string& result_name){

	    std::string strval = std::string(result_name);

	    ensure_result_exists(strval);

		return results[strval];

	}

	void statistic::set_integer_value(const std::string& result_name, int value){

	    std::stringstream stream;
	    stream << value;
	    set_string_value(result_name, stream.str());

	}

	void statistic::set_unsigned_integer_value(const std::string& result_name, unsigned int value){

	    std::stringstream stream;
	    stream << value;
	    set_string_value(result_name, stream.str());

	}

	void statistic::set_double_value(const std::string& result_name, double value){

	    std::stringstream stream;
	    stream << value;
	    set_string_value(result_name, stream.str());

	}

	void statistic::set_bool_value(const std::string& result_name, bool value){

	    if(value){
		set_string_value(result_name, std::string("true"));
	    }
	    else{
		set_string_value(result_name, std::string("false"));
	    }

	}

	void statistic::set_string_value(const std::string& result_name, const std::string& value){

	    results[result_name] = value;

	}

	void statistic::clear(){

		results.clear();

	}

	std::set<std::string> statistic::get_results_names(){

		std::set<std::string> output = std::set<std::string>();

		for(std::map<std::string, std::string>::iterator itr = results.begin();
			itr != results.end(); itr++){

			output.insert(itr->first);

		}

		return output;

	}

	std::map<std::string, std::string> statistic::get_results(){

	    return results;

	}

	void statistic::from_file(const std::string& filename_statistic, bool message_if_successful, bool exception_on_fail) {

		std::ifstream statistic_stream(filename_statistic.c_str(), std::ios::in);

		clear();

		unsigned int line_in_file,
			     position;

		std::string line_buffer,
		       name,
		       value;

		if(statistic_stream.fail()){
			if(exception_on_fail){
				std::stringstream error;
				error << "unable to open file \"" << filename_statistic << "\" for reading.";
				throw std::invalid_argument(error.str());
			}
			else{
				return;
			}
		}

		try{
			for(line_in_file=1; getline(statistic_stream, line_buffer); line_in_file++){
				// skip comments
				if(is_comment(line_buffer, '#')){
					continue;
				}

				position = line_buffer.find(";");

				if(position == 0){

					std::stringstream error;
					error << "line starts with semicolon, no result name given";
					throw std::invalid_argument(error.str());

				}

				name = remove_boundary_whitespaces(line_buffer.substr(0, position));

				if(name.size() == 0){

					std::stringstream error;
					error << "no result name given";
					throw std::invalid_argument(error.str());

				}

				if(contains_whitespaces(name)){

					std::stringstream error;
					error << "name contains whitespaces";
					throw std::invalid_argument(error.str());

				}

				if(position == line_buffer.size()-1){

					std::stringstream error;
					error << "line ends with semicolon, no result value given";
					throw std::invalid_argument(error.str());

				}

				value = remove_boundary_whitespaces(line_buffer.substr(position+1));

				if(value.size() == 0){

					std::stringstream error;
					error << "no result value given";
					throw std::invalid_argument(error.str());

				}

		    // Unquote, if needed.
				if(string_quoted_correctly(value)){
				    value = value.substr(1, value.length()-2);
				}

		    results[name] = value;

			}

		}

		catch(std::exception& e){

		std::stringstream error;
			error << "line: " << line_in_file << ", file: \""
			    << filename_statistic
				<< "\", error occured: " << e.what() << std::endl;
			throw std::invalid_argument(error.str());

		}

		statistic_stream.close();

		if(message_if_successful){

			std::cerr << "Read " << line_in_file-1 << " lines from \""
			    << filename_statistic << "\"." << std::endl;

		}

	}

	void statistic::to_file(const std::string& filename_statistic, bool message_if_successful) {

		//Create the folder when its not already present
		std::string folderName = filename_statistic.substr(0, filename_statistic.find_last_of("\\/"));
		std::string order("mkdir -p ");
		system((order + folderName).c_str());

		std::ofstream statistic_stream(filename_statistic.c_str(), std::ios::out);

		if(statistic_stream.fail()){

			// try to create the folder, maybe this help

			std::cerr << "unable to open file \"" << filename_statistic
			    << "\" for writing.";
			throw std::exception();
		}

		unsigned int current_line_number = 1;

		statistic_stream << "# Automatically created by a statistic object." << std::endl;

		for(std::map<std::string, std::string>::const_iterator itr = results.begin();
			itr != results.end(); itr++){

		    statistic_stream << itr->first;

		    if(sloppy_match(itr->second, std::string("true"))){

			    statistic_stream << "; true" << std::endl;

		    }
		    else if(sloppy_match(itr->second, std::string("false"))){

			    statistic_stream << "; false" << std::endl;

		    }
		    else{

		    	double floatValue;

		    	std::stringstream stream1;
		    	stream1.str(itr->second);

		    	stream1 >> std::ws >> floatValue;

		    	if(stream1.fail() || !stream1.eof()){
		    		statistic_stream << "; \"" << itr->second
		    				<< "\"" << std::endl;
		    	}
		    	else{
		    		statistic_stream << "; " << itr->second << std::endl;
		    	}
		    }
		    current_line_number++;

		}

		statistic_stream.close();
		if(message_if_successful){
		    std::cerr << "Wrote " << results.size()+2 << " lines to \""
		    << filename_statistic << "\"."  << std::endl;
	}

}
