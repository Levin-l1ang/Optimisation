//
//  io.h
//  rptts

#ifndef rptts_io_h
#define rptts_io_h

#include <stdio.h>
#include <iostream>
#include <fstream>
#include <vector>
#include <map>
#include <set>
#include <string>
#include <algorithm>
#include <cmath>
#include <queue>


using namespace std;

typedef vector<pair<vector<pair<int, int> >, vector<pair<int, int> > > > vpvpvp;
typedef vector<pair<int, int> > vp;
typedef vector<vector<int> > vv;
typedef vector<vector<pair<int, int> > > vvp;

// important parameters
const long long LINF = 1000000000000000000;
const int INF = 1e9;
const bool verbose = false;
const bool ververbose = false;
const bool only_pos_weights = true;
const bool onlymerge = true;
const int strategy = 3;
const int compl_val = 6;
const bool tree_off = true;
const bool write_xw = false;
const bool line_split = true;


// paths
const string instance = "bahn04/";
const string prefix = "/Users/JP/Desktop/Masterarbeit/rptts/rptts/" + instance;

extern string path_stops;// = prefix + "Stop.giv";
extern string path_edges;// = prefix + "Edge.giv";
extern string path_activities;// = prefix + "Activities-periodic.giv";
extern string path_events;// = prefix + "Events-periodic.giv";
extern string path_od;// = prefix + "OD.giv";
extern string path_lines;// = prefix + "Line-Concept.lin";
extern string path_timetable;// = prefix + "Timetable-periodic.tim";


// structures
struct Stop{
    int identity;
    set<int> adj_stops;
    set<int> activities;
    set<int> dep_events;
    set<int> arr_events;
};
struct Edge{
    int identity;
    int left_stop;
    int right_stop;
    int length;
    int min_travel_time;
    int max_travel_time;
};
struct Line{
    int identity = -1;
    vector<int> path;
    set<int> stops;
    int frequency = 1;
};
struct Event{
    int identity;
    int stop_id;
    int line_id;
    int time = -1;
    bool is_arr;
    set<int> to_events;
    set<int> from_events;
    int passengers;
    int line_path_pred = -1;
    int line_path_succ = -1;
};
struct Activity{
    int identity;
    int tail;
    int head;
    int lower_bound;
    int upper_bound;
    int passengers;
    int duration;
    bool is_change;
    bool is_drive;
};
struct Ch_Act{
    int identity;
    int tail;
    int head;
    int lower_bound;
    int upper_bound;
    int passengers;
};


// global variables
extern int time_period;
extern int max_line_id;
extern map<int, Stop> Stops;
extern map<int, Edge> Edges;
extern map<int, Line> Lines;
extern map<int, Event> Events;
extern map<int, Activity> Activities;
extern map<int, Ch_Act> Ch_Acts;
extern map<pair<int, int>,int> EA_corr;
extern map<pair<int, int>,int> SE_corr;
extern map<int, int> Line_id_to_it;
extern vector<vector<int> > T_Matrix;

int mod(int n);
int get_slack(Activity a);

bool read_stops(void);
bool read_edges(void);
bool read_lines(void);
bool read_events(void);
bool read_activities(void);

void split_lines(void);

bool get_instance(void);
void print_timetable(void);
void print_solution(void);



#endif
