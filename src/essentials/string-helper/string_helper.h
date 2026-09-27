#ifndef _STRING_HELPER_H
#define _STRING_HELPER_H

#include <string>

namespace string_helper {
	// Checks whether string is a comment starting with comment_sign.
	bool is_comment(const std::string& input, char comment_sign);
	// Checks whether template matches string, in a way that there
	// can be arbitrary many whitespaces in string for each whitespace
	// in template and arbitrary many whitespaces at beginning and end
	// of input.
	bool sloppy_match(const std::string& pattern, const std::string& input);
	// Returns true, if input contains whitespaces, else false.
	bool contains_whitespaces(const std::string& input);
	// Returns true, if input represents a IEEE-754 float.
	bool string_represents_double(const std::string& input);
	// Returns true, if input represents an integer.
	bool string_represents_integer(const std::string& input);
	// Returns true, if input represents an unsigned integer.
	bool string_represents_unsigned_integer(const std::string& input);
	// Returns true, if input represents a bool.
	bool string_represents_bool(const std::string& input);
	// Returns true, if input begins with ", ends with " and
	// double quotes in between are correctly escaped like \".
	bool string_quoted_correctly(const std::string& input);
	// Returns a string which has no whitespaces at the
	// beginning, no whitespaces at the end but else corresponds
	// to input.
	std::string remove_boundary_whitespaces(const std::string& input);
	// Removes bounding double quotes from input and
	// unescapes double quotes in between, that means \" -> ".
	std::string unquote(const std::string& input);
	// Escapes all " in input and adds bounding double quotes.
	std::string quote(const std::string& input);
	// If input is a unix directory name, this function returns the directory name
	std::string dirname(const std::string& input);
	// Converts a string to a bool; if input is not a bool, false is returned
	bool atob(const std::string& input);
}

#endif
