#ifndef CONPROP_H_
#define CONPROP_H_

#include <vector>
#include <list>
#include <map>
#include <string>

namespace conprop
{

typedef std::pair<int,int> interval;
typedef std::vector<bool> feasible_set;
typedef std::pair<int,int> edge;
typedef std::vector<feasible_set> timetable;

enum searchmode
{
	UP = 1,
	DOWN = 2,
	RANDOM = 3
};

class graph
{
public:
	graph();
	void init(std::string file, int in_period);
	std::multimap<edge, interval > bounds;
	int num_edges;
	int num_vertices;
	int period;
private:
};

class potential
{
public:
	potential();
	void reset();
	bool update();
	bool feasible();
	void draw(std::string filename, std::string dirname);
	timetable m_timetable;
	std::vector<int> fix;
	std::vector<interval> searchspace;
	graph* the_graph;
private:
	int mod(int i, int T);
	std::string hexadec(int zahl);
};

class prop
{
public:
	prop(std::string file, int period, searchmode mode = UP, bool anim = false, std::string draw_dir = "Zeichnung/cp_animation", int seed=0);
	bool check_feasibility_heuristic();
	void set_time_limit(int _time);
	bool solve();
	void write(std::string file);
private:
	bool recurse();
	potential p;
	graph m_graph;
	searchmode smode;
	int pic_counter;
	bool animate;
	std::string animate_dir;
	bool use_time;
	time_t time_limit;
};


}

#endif
