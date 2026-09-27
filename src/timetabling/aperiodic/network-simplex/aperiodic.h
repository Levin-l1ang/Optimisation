#ifndef APERIODIC_H_
#define APERIODIC_H_

//goblin stuff
#include "goblin.h"
#include "networkSimplex.h"

#include <string>

class aperiodic
{
public:
	aperiodic();
	void solve(std::string edge, std::string activity, std::string timetable);
private:
	goblinController *gob;
};

#endif
