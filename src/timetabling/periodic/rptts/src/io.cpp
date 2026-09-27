//
//  io.cpp
//  rptts

#include "io.h"

string path_stops;// = prefix + "Stop.giv";
string path_edges;// = prefix + "Edge.giv";
string path_activities;// = prefix + "Activities-periodic.giv";
string path_events;// = prefix + "Events-periodic.giv";
string path_od;// = prefix + "OD.giv";
string path_lines;// = prefix + "Line-Concept.lin";
string path_timetable;

// global variables
int time_period;
int max_line_id = 0;
map<int, Stop> Stops;
map<int, Edge> Edges;
map<int, Line> Lines;
map<int, Event> Events;
map<int, Activity> Activities;
map<int, Ch_Act> Ch_Acts;
map<pair<int, int>,int> EA_corr;
map<pair<int, int>,int> SE_corr;
map<int, int> Line_id_to_it;
map<int, int> Timetable;
vector<vector<int> > T_Matrix;


// functions
int mod(int n){
    int T = time_period;
    n = n % T;
    while (n < 0) n += T;
    return n;
}
int get_slack(Activity a){
    return mod(Events[a.head].time - Events[a.tail].time - a.lower_bound);
}

// initializing instance
bool read_stops(void){
    string line;
    ifstream data;
    data.open(path_stops.c_str());
    if (!data.good()) return true;
    while (!data.eof())
    {
        getline(data,line);
        if (line[0] != '#') {
            Stop mystop;
            sscanf(line.c_str(), "%d;%*s\n", &mystop.identity);
            Stops[mystop.identity] = mystop;
        }
    }
    data.close();
    return false;
}
bool read_edges(void){
    string line;
    ifstream data;
    data.open(path_edges.c_str());
    if (!data.good()) return true;
    while (!data.eof())
    {
        getline(data,line);
        if (line[0] != '#') {
            Edge myedge;
            sscanf(line.c_str(), "%d;%d;%d;%d;%d;%d\n", &myedge.identity, &myedge.left_stop, &myedge.right_stop,
                   &myedge.length, &myedge.min_travel_time, &myedge.max_travel_time);
            Edges[myedge.identity] = myedge;
            Stops[myedge.left_stop].adj_stops.insert(myedge.right_stop);
            Stops[myedge.right_stop].adj_stops.insert(myedge.left_stop);
            SE_corr[make_pair(myedge.left_stop, myedge.right_stop)] = myedge.identity;
            SE_corr[make_pair(myedge.right_stop, myedge.left_stop)] = myedge.identity;
        }
    }
    data.close();
    return false;
}
bool read_lines(void){
    string line;
    ifstream data;
    data.open(path_lines.c_str());
    if (!data.good()) return true;
    while (!data.eof())
    {
        int edge_order, edge_nbr;
        getline(data,line);
        if (line[0] != '#' && !line.empty()) {
            Line myline;
            sscanf(line.c_str(), "%d;%d;%d;%d\n", &myline.identity, &edge_order, &edge_nbr, &myline.frequency);
            if (myline.frequency != 0)
            {
                int left = Edges[edge_nbr].left_stop;
                int right = Edges[edge_nbr].right_stop;
                if (edge_order == 1){
                    myline.path.push_back(left);
                    myline.path.push_back(right);
                    Lines[myline.identity] = myline;
                    Line_id_to_it[myline.identity] = (int) Lines.size() - 1;
                    max_line_id = max(myline.identity+1, max_line_id);
                }
                else{
                    vector<int> Path = Lines[myline.identity].path;
                    if      (Path.front() == left) Path.insert(Path.begin(), right);
                    else if (Path.front() == right) Path.insert(Path.begin(), left);
                    else if (Path.back() == right) Path.push_back(left);
                    else if (Path.back() == left) Path.push_back(right);
                    Lines[myline.identity].path = Path;
                    Lines[myline.identity].stops.insert(left);
                    Lines[myline.identity].stops.insert(right);

                }
            }
        }
    }
    data.close();
    return false;
}
bool read_events(void){
    string line;
    ifstream data;
    data.open(path_events.c_str());
    if (!data.good()) return true;
    while (!data.eof())
    {
        char typ[20];
        char dummy_read_in_char;
        int line_frequency_repetition;
        getline(data,line);
        if (line[0] != '#') {
            Event myevent;
            sscanf(line.c_str(), "%d;%[^;];%d;%d;%d;%c;%d\n", &myevent.identity,
                typ, &myevent.stop_id, &myevent.line_id, &myevent.passengers,
                &dummy_read_in_char, &line_frequency_repetition);
            string dummy(typ);
            if (dummy.find("arrival") != std::string::npos){
                myevent.is_arr = true;
                Stops[myevent.stop_id].arr_events.insert(myevent.identity);
            }
            else{
                myevent.is_arr = false;
                Stops[myevent.stop_id].dep_events.insert(myevent.identity);
            }
            Events[myevent.identity] = myevent;

        }
    }
    data.close();
    return false;
}
bool read_activities(void){
    string line;
    ifstream data;
    bool told_yet = false;
    data.open(path_activities.c_str());
    if (!data.good()) return true;
    while (!data.eof())
    {
        char typ[20];
        getline(data,line);
        if (line[0] != '#') {
            Activity myact;
            sscanf(line.c_str(), "%d;%[^;];%d;%d;%d;%d;%d\n", &myact.identity, typ, &myact.tail, &myact.head,
                   &myact.lower_bound, &myact.upper_bound, &myact.passengers);
            string dummy(typ);
            if (dummy.find("change") != std::string::npos){
                myact.is_change = true;
                myact.is_drive = false;
                Stops[Events[myact.tail].stop_id].activities.insert(myact.identity);

                // warning maybe not feasible
                if (myact.upper_bound - myact.lower_bound + 1 < time_period && !told_yet){
                   told_yet = true;
                   cout << "Warning: u_a - l_a < T. Maybe timetable wont be feasible!" << endl;
                }

                // add change activities to A_ch
                Ch_Act a;
                a.identity = myact.identity;
                a.tail = myact.tail;
                a.head = myact.head;
                a.lower_bound = myact.lower_bound;
                a.upper_bound = myact.upper_bound;
                a.passengers = myact.passengers;
                if (only_pos_weights){
                    if (myact.passengers > 0){
                        Ch_Acts[a.identity] = a;
                    }
                }
                else {
                    Ch_Acts[a.identity] = a;
                }
            }
            else if (dummy.find("drive") != std::string::npos){
                myact.is_change = false;
                myact.is_drive = true;
                Events[myact.head].line_path_pred = myact.tail;
                Events[myact.tail].line_path_succ = myact.head;
            }
            else{
                myact.is_change = false;
                myact.is_drive = false;
                Stops[Events[myact.tail].stop_id].activities.insert(myact.identity);
                Events[myact.head].line_path_pred = myact.tail;
                Events[myact.tail].line_path_succ = myact.head;
            }
            Events[myact.tail].to_events.insert(myact.head);
            Events[myact.head].from_events.insert(myact.tail);
            Activities[myact.identity] = myact;
            EA_corr[make_pair(myact.tail, myact.head)] = myact.identity;
        }
    }
    data.close();
    return false;
}


void split_lines(void){

    for (auto it: Events){
        Event e = it.second;
        if (e.line_path_pred == -1 && e.stop_id != Lines[e.line_id].path[0]){
            // this is start of a new line
            vector<int> p = Lines[e.line_id].path;
            reverse(p.begin(), p.end());
            Line l = Lines[e.line_id];
            l.path = p;
            l.identity = max_line_id + 1;
            max_line_id++;
            Lines[l.identity] = l;
            Line_id_to_it[l.identity] = (int) Lines.size() - 1;
            while (e.line_path_succ != -1){
                Events[e.identity].line_id = l.identity;
                e = Events[e.line_path_succ];
            }
            Events[e.identity].line_id = l.identity;
        }
    }

}

bool get_instance(void){
    if (read_stops()) return true;
    if (read_edges()) return true;;
    if (read_lines()) return true;
    if (read_events()) return true;
    if (read_activities()) return true;

    split_lines(); // because one line back and forth is declared with the same line_id

}

void print_timetable(void){
    remove(path_timetable.c_str());
    ofstream ofs;
    ofs.open(path_timetable.c_str(), ios_base::app);
    ofs << "# feasible timetable found with matching-merge/spanning-tree hybrid" << endl;
    ofs << "# objective function is ptt1" << endl;
    ofs << "# event_index; time" << endl;
    for (auto it: Events){
        Event e = it.second;
        ofs << e.identity << "; " << e.time << endl;
    }
    ofs.close();
}

void print_solution(){
    print_timetable();
}
