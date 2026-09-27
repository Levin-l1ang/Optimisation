#include "string_helper.h"
#include <cctype>
#include <cstdlib>

namespace string_helper {

	bool is_comment(const std::string &input, char comment_sign){

		std::size_t i;

		for(i = 0; i < input.length(); i++){
			if(!isspace(input[i])){
				break;
			}
		}

		if(i < input.length() && input[i] == comment_sign){
			return true;
		}

		return false;

	}

	bool sloppy_match(const std::string &pattern, const std::string &input){

		std::size_t i, j=0, template_whitespace=1;

		for(i=0; i<pattern.length(); i++){

			if(isspace(pattern[i])){
				template_whitespace=1;
				continue;
			}

			if(pattern[i] == ';'){
				template_whitespace=1;
			}

			while(j<input.length()){

				if(isspace(input[j]) && template_whitespace){
					j++;
					continue;
				}

				else if(tolower(pattern[i])!=tolower(input[j])){
					return false;
				}

				j++;
				template_whitespace=0;
				break;
			}
		}

		// Whitespaces at the end are allowed.
		while(j<input.size()){

			if(!isspace(input[j])){

				return false;

			}

			j++;
		}

		return true;
	}

	bool contains_whitespaces(const std::string &input){

		std::size_t i;

		for(i = 0; i < input.size(); i++){

			if(isspace(input[i])){

				return true;

			}
		}

		return false;

	}

	std::string remove_boundary_whitespaces(const std::string &input){

		std::size_t i,
		    lower = 0,
		    length = input.size();

		for(i = 0; i < input.size(); i++){

			if(!isspace(input[i])){

				break;

			}

			lower++;
			length--;
		}

		for(i = input.size()-1; length > 0; i--, length--){

			if(!isspace(input[i])){

				break;

			}
		}

		return input.substr(lower, length);

	}

	bool string_quoted_correctly(const std::string& input){

		std::size_t i;

		if(input[0] != '"'){

			return false;

		}

		if(input[input.size()-1] != '"'){

			return false;

		}

		for(i = 1; i < input.size()-1; i++){

			if(input[i] == '"' && input[i-1] != '\\'){

				return false;

			}

		}

		return true;

	}

	bool string_represents_double(const std::string& input){

		const char * input_chars = input.c_str();
		char * end;

		strtod(input_chars, &end);
		
		return &input_chars[input.size()] == end;

	}

	bool string_represents_integer(const std::string& input){

		std::size_t i=0;

		std::string buffer = remove_boundary_whitespaces(input);

		if(buffer.size() == 0 || (buffer.size() == 1 && !isdigit(buffer[0]))){

			return false;

		}

		if(buffer[0] == '+' || buffer[0] == '-'){

			i++;
		
		}

		for(; i<buffer.size(); i++){

			if(!isdigit(buffer[i])){

				return false;

			}

		}

		return true;

	}

	bool string_represents_unsigned_integer(const std::string& input){

		return string_represents_integer(input) && (remove_boundary_whitespaces(input))[0] != '-';

	}

	std::string unquote(const std::string& input){

		std::size_t i;

		std::string buffer;

		if(!string_quoted_correctly(input)){

			return input;

		}

		buffer = input.substr(1, input.size()-2);

		for(i=0; i < buffer.size(); i++){

			if(buffer[i] == '"'){

				buffer.erase(i-1,1);

			}

		}

		return buffer;

	}

	std::string dirname(const std::string& input){

		return input.substr(0,input.find_last_of('/')+1);

	}

	bool string_represents_bool(const std::string& input){

		return input == "true" || input == "false";

	}

	bool atob(const std::string& input){

		return input == "true";

	}

}
