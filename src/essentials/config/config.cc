#include "config.h"
#include <sstream>
#include <stdexcept>
#include <cmath>

#define CONFIG_HEADER "setting-name; setting-value"

using namespace string_helper;

std::map<std::string, std::string> config::settings = std::map<std::string, std::string>();

void config::ensure_setting_exists(const std::string setting){

    if((setting.compare("default_activities_periodic_unbuffered_file")!=0) && settings.find(setting) == settings.end()){

        std::stringstream error;
        error << "setting name \"" << setting << "\" not found";
        throw std::invalid_argument(error.str());

    }
}

int config::get_integer_value(const char *setting_name){

    std::string strval = std::string(setting_name);

    ensure_setting_exists(strval);

	return atoi(settings[strval].c_str());

}

unsigned int config::get_unsigned_integer_value(const char *setting_name){

    std::string strval = std::string(setting_name);

    ensure_setting_exists(strval);

	return (unsigned int) atoi(settings[strval].c_str());

}

bool config::get_bool_value(const char *setting_name){

    std::string strval = std::string(setting_name);

    ensure_setting_exists(strval);

	return (unsigned int) atob(settings[strval].c_str());

}

double config::get_double_value(const char *setting_name){

    std::string strval = std::string(setting_name);

    ensure_setting_exists(strval);

	return atof(settings[strval].c_str());

}

std::string config::get_string_value(const char *setting_name){

    std::string strval = std::string(setting_name);

    ensure_setting_exists(strval);

	if ((strval.compare("default_activities_periodic_file") == 0)
		&& (settings.find("use_buffered_activities") != settings.end())
		&& get_bool_value("use_buffered_activities"))
			return get_string_value("default_activity_buffer_file");

	if ((strval.compare("default_activities_periodic_unbuffered_file") == 0)
		&& (settings.find("use_buffered_activities") != settings.end())
		&& get_bool_value("use_buffered_activities"))
			return get_string_value("default_activity_relax_file");

        if ((strval.compare("default_activities_periodic_unbuffered_file") == 0)
                &&((settings.find("use_buffered_activities") == settings.end())
                || ~get_bool_value("use_buffered_activities")))
                        return get_string_value("default_activities_periodic_file");

	return settings[strval];

}

std::set<std::string> config::get_settings_names(){

	std::set<std::string> output = std::set<std::string>();

	for(std::map<std::string, std::string>::iterator itr = settings.begin();
	        itr != settings.end(); itr++){

		output.insert(itr->first);

	}

	return output;

}

std::map<std::string, std::string> config::get_settings(){

    return settings;

}

void config::clear(){

    settings.clear();

}

void config::from_file(const char *filename_config, bool message_if_successful, bool only_if_exists) {

	std::ifstream config_stream(filename_config, std::ios::in);

	unsigned int line_in_file,
		     header_found,
		     position;

	std::string line_buffer,
	       name,
	       value;

	// let's do the config
	if(config_stream.fail()){
	    if(only_if_exists){
	        return;
	    }
	    std::stringstream error;
		error << "unable to open file \"" << filename_config << "\" for reading";
		throw std::invalid_argument(error.str());
	}

	header_found=0;

	try{
		for(line_in_file=1; getline(config_stream, line_buffer); line_in_file++){
			// skip comments
			if(is_comment(line_buffer, '#')){
				continue;
            }
            // skip empty lines
            if (line_buffer.empty()) {
                continue;
            }
			// header
			if(sloppy_match(CONFIG_HEADER, line_buffer)){
				if(header_found){
				    std::stringstream error;
					error << "header inserted a second time or not at the beginning";
					throw std::invalid_argument(error.str());
				}
				header_found=1;
				continue;
			}
			// From this line on we finally have configuration data!
			if(!header_found){
				header_found=1;
			}

			position = line_buffer.find(";");

			if(position == 0){

                std::stringstream error;
				error << "line starts with semicolon, no setting-name given";
				throw std::invalid_argument(error.str());

			}

			name = remove_boundary_whitespaces(line_buffer.substr(0, position));

			if(name.size() == 0){

                std::stringstream error;
				error << "no setting-name given";
				throw std::invalid_argument(error.str());

			}

			if(contains_whitespaces(name)){

                std::stringstream error;
				error << "name contains whitespaces";
				throw std::invalid_argument(error.str());

			}

			if(position == line_buffer.size()-1){

                std::stringstream error;
				error << "line ends with semicolon, no setting-value given";
				throw std::invalid_argument(error.str());

			}

			value = remove_boundary_whitespaces(line_buffer.substr(position+1));

			if(value.size() == 0){

                std::stringstream error;
				error << "no setting-value given";
				throw std::invalid_argument(error.str());

			}

            // Unquote, if needed.
			if(string_quoted_correctly(value)){

			    value = value.substr(1, value.length()-2);

			}

			if(name == "include"){

				from_file((dirname(std::string(filename_config))+
				            unquote(value)).c_str(), message_if_successful);

				continue;

			}

			else if(name == "include_if_exists"){

				from_file((dirname(std::string(filename_config))+
				            unquote(value)).c_str(), message_if_successful,
				        true);

				continue;
			}

            if(settings.find(name) == settings.end()){
    			settings.insert(make_pair(name, value));
    		}
    		else {
    		    settings[name] = value;
    		}

		}

	}

	catch(std::exception& e){

        std::stringstream error;
		error << "line: " << line_in_file << ", file: \""
		    << filename_config
			<< "\"; " << e.what();
		throw std::invalid_argument(error.str());

	}

	config_stream.close();

	if(message_if_successful){

		std::cerr << "Read " << line_in_file-1 << " lines from \""
		    << filename_config << "\".";

	}

}

void config::to_file(const char *filename_config, bool message_if_successful) {

	std::ofstream config_stream(filename_config, std::ios::out);

	if(config_stream.fail()){
        std::stringstream error;
		error << "unable to open file \"" << filename_config
		    << "\" for writing";
		throw std::invalid_argument(error.str());
	}

	config_stream << "# Automatically created by a config object." << std::endl
		<< "# " << CONFIG_HEADER << std::endl;

	for(std::map<std::string, std::string>::iterator itr = settings.begin();
	        itr != settings.end(); itr++){

		config_stream << itr->first;

	    if(sloppy_match(itr->second, std::string("true"))){

	            config_stream << "; true" << std::endl;

	    }
	    else if(sloppy_match(itr->second, std::string("false"))){

	            config_stream << "; false" << std::endl;

	    }
	    else{

	    	double floatValue;

	    	std::stringstream stream1;
	    	stream1.str(itr->second);

	    	stream1 >> std::ws >> floatValue;

	    	if(stream1.fail() || !stream1.eof()){
	    		config_stream << "; \"" << itr->second
	    				<< "\"" << std::endl;
	    	}
	    	else{
	    		config_stream << "; " << itr->second << std::endl;
	    	}
	    }

	}

	config_stream.close();
	if(message_if_successful){
	    std::cerr << "Wrote " << 3+settings.size() << " lines to \""
	    << filename_config << "\"."  << std::endl;
	}

}
