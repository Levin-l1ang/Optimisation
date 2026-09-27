//
//
//  matching_merge.cpp
//

#include <stdio.h>     
#include <stdlib.h>
#include <iostream>
#include <fstream>
#include <cstring>
#include <algorithm>
#include <math.h>
#include <set>

// personal header
#include "matching_merge.h"
#include "matching.h"



using namespace std;

// constructor
matching_merge::matching_merge(int seed)
{

}
// destructor
matching_merge::~matching_merge()
{

}

// initializes graphs
void matching_merge::init(string activities_file,string events_file, string od_matrix_file, string lines_file, int given_period)
{
    period = given_period;
    
    //tell time
    time_t rawtime;
    struct tm * timeinfo;
    time ( &rawtime );
    timeinfo = localtime ( &rawtime );
    cout << "Starting time: " << asctime(timeinfo) << "\n";
    cout << "Initializing graphs...";
    
    //read in the events
    string line;
    ifstream data;
    data.open(events_file.c_str());
    while (!data.eof())
    {
        getline(data,line);
        if(line!=""&&(line.c_str())[0]>=48 && (line.c_str())[0]<=57)
        {
            size_t pos = line.find(";");
            line=line.substr(pos+1);
            if (strncmp(line.c_str()," \"arrival\"", 8) == 0 ) {
                event_is_arrival.push_back(1);
            }
            else
            {
                event_is_arrival.push_back(0);
            }
            pos = line.find(";");
            line=line.substr(pos+1);
            corr_stop_to_event_index.push_back(atoi(line.c_str()));
            pos = line.find(";");
            line=line.substr(pos+1);
            corr_line_to_event_index.push_back(atoi(line.c_str()));
        }
    }
    data.close();
    nbr_of_events = (int) corr_stop_to_event_index.size();
    timetable.resize(nbr_of_events);
    
    // get nbr_of_stops (STOPS MUST BE NUMERATED STRICTLY INCREASING BEGINNING AT 1)
    nbr_of_stops = 0;
    for (int i = 0; i < nbr_of_events; i++)
    {
        if (corr_stop_to_event_index[i] > nbr_of_stops)
        {
            nbr_of_stops = corr_stop_to_event_index[i];
        }
    }
    
    // get departure events for every stop_id
    corr_dep_events_to_stop_index.resize(nbr_of_stops);
    corr_arr_events_to_stop_index.resize(nbr_of_stops);
    for (int i = 0; i < nbr_of_events; i++)
    {
        if (event_is_arrival[i])
        {
            corr_arr_events_to_stop_index[corr_stop_to_event_index[i]-1].push_back(i);
        }
        else
        {
            corr_dep_events_to_stop_index[corr_stop_to_event_index[i]-1].push_back(i);
        }
        
    }
    
    // retrieve existing lines
    existing_lines = corr_line_to_event_index;
    sort( existing_lines.begin(), existing_lines.end() );
    existing_lines.erase( unique( existing_lines.begin(), existing_lines.end() ), existing_lines.end() );
    inv_existing_lines.resize(existing_lines.back()+1,-1);
    for (int i = 0; i < existing_lines.size(); i++)
    {
        inv_existing_lines[existing_lines[i]] = i;
    }
    nbr_of_lines = (int) existing_lines.size();
    
    // get information if stop k is reached by line_cluster i
    stop_in_cluster.resize(nbr_of_stops);
    for (int i = 0; i < nbr_of_stops; i++) {
        stop_in_cluster[i].resize(nbr_of_lines);
    }
    for (int i = 0; i < nbr_of_events; i++)
    {
        stop_in_cluster[corr_stop_to_event_index[i]-1][inv_existing_lines[corr_line_to_event_index[i]]] = 1;
    }
    
    // create vector of Adj_Matrices and TEMP
    Adj_Matrices.resize(nbr_of_lines);
    TEMP.resize(nbr_of_events);
    Adj_List.resize(nbr_of_events);
    
    for (int i=0; i < nbr_of_lines; i++)
    {
        Adj_Matrices[i].resize(nbr_of_events);
        for (int j=0; j < nbr_of_events; j++)
        {
            Adj_Matrices[i][j].resize(nbr_of_events);
            TEMP[j].resize(nbr_of_events);
        }
    }
    Duration.resize(nbr_of_events);
    for (int i=0; i < nbr_of_events; i++) {
        Duration[i].resize(nbr_of_events);
    }
    
    // fill each matrix with activity-edges corresponding to their line
    data.open(activities_file.c_str());
    while (!data.eof())
    {
        getline(data,line);
        if(line!=""&&(line.c_str())[0]>=48&&(line.c_str())[0]<=57)
        {
            size_t pos = line.find(";");
            line=line.substr(pos+1);
            pos = line.find(";");
            
            std::string name(line,0,pos);
            line=line.substr(pos+1);
            
            int tail, head, min;//, max, weight;
            
            tail=atoi(line.c_str());
            pos = line.find(";");
            line=line.substr(pos+1);
            
            head=atoi(line.c_str());
            pos = line.find(";");
            line=line.substr(pos+1);
            
            min=atoi(line.c_str());
            pos = line.find(";");
            line=line.substr(pos+1);
            
            /*max=atoi(line.c_str());
            pos = line.find(";");
            line=line.substr(pos+1);
            
            weight=atoi(line.c_str());*/
            
            Duration[tail-1][head-1] = min;
            corr_tail_to_activity_index.push_back(tail-1);
            corr_head_to_activity_index.push_back(head-1);
            
            if (corr_line_to_event_index[tail-1] == corr_line_to_event_index[head-1])
            {
                Adj_Matrices[inv_existing_lines[corr_line_to_event_index[tail-1]]][tail-1][head-1] = min;
            }
            
        }
    }
    data.close();
    nbr_of_activities = (int) corr_tail_to_activity_index.size();
    
    freq_of_line.resize(nbr_of_lines);
    // get frequency for each event
    data.open(lines_file.c_str());
    while (!data.eof())
    {
        getline(data,line);
        if(line != "" && (line.c_str())[0] >= 48 && (line.c_str())[0] <= 57)
        {
            int line_id, frequency;
            line_id = atoi(line.c_str());
            size_t pos = line.find(";");
            line = line.substr(pos+1);
            pos = line.find(";");
            line = line.substr(pos+1);
            pos = line.find(";");
            line = line.substr(pos+1);
            frequency = atoi(line.c_str());
            if (frequency > 0) {
                freq_of_line[inv_existing_lines[line_id]] = frequency;
            }
        }
    }
    data.close();
    freq_of_event.resize(nbr_of_events);
    for (int i = 0; i < nbr_of_events; i++)
    {
        freq_of_event[i] = freq_of_line[inv_existing_lines[corr_line_to_event_index[i]]];
    }
    
    // fill in timetable (Event 0 is set to zero)
    dummy_vector.resize(nbr_of_events,1);
    dummy_vector[0] = 0;
    for (int i = 1; i < nbr_of_events; i++) {
        if (dummy_vector[i] != 0)
        {
            dummy_vector[i] = 0;
            // search for events that already have a timeslot
            for (int j = 0; j < nbr_of_events; j++)
            {
                if (dummy_vector[j]==0 && j != i && Duration[j][i] > 0 && corr_line_to_event_index[i] == corr_line_to_event_index[j])
                {
                    timetable[i] = timetable[j] + Duration[j][i];
                    if (timetable[i] >= period)
                    {
                        timetable[i] -= period;
                    }
                    break;
                }
            }
        }
    }
    
    // read in OD-matrix (for evaluation purposes)
    OD_Matrix.resize(nbr_of_stops+1);
    for (int i = 0; i <= nbr_of_stops; i++) {
        OD_Matrix[i].resize(nbr_of_stops+1);
    }
    data.open(od_matrix_file.c_str());
    while (!data.eof())
    {
        getline(data,line);
        if(line!=""&&(line.c_str())[0]>=48&&(line.c_str())[0]<=57)
        {
            int from, to, nbr;
            from = atoi(line.c_str());
            size_t pos = line.find(";");
            line=line.substr(pos+1);
            to = atoi(line.c_str());
            pos = line.find(";");
            line=line.substr(pos+1);
            nbr = atoi(line.c_str());
            if (nbr > 0)
            {
                OD_Matrix[from][to] = nbr;
                OD_Matrix[to][from] = nbr;
            }
        }
    }
    data.close();
    
    cout << "Done.\n";
}
int ggT(int m, int n)
{
    if (n == 0) return m;
    else return ggT (n, m % n);
}

void matching_merge::solve()
{
    

    // while more than one linecluster
    while (Adj_Matrices.size() > 1)
    {
        
        curr_length = (int) Adj_Matrices.size();
        
        cout << curr_length << " line clusters atm..\n";
        // resize matrices
        Cost_Matrix.resize(curr_length);
        t2_Matrix.resize(curr_length);
        for (int i = 0; i < curr_length; i++)
        {
            Cost_Matrix[i].resize(curr_length);
            t2_Matrix[i].resize(curr_length);
        }
        
        // get costs of possible matchings
        for (int i = 0; i < curr_length-1; i++)
        {
            //cout << i+1 << "\n";
            for (int j = i+1; j < curr_length; j++)
            {
                t2_Matrix[i][j] = synchronize(i,j);
                t2_Matrix[j][i] = t2_Matrix[i][j];
                //cout << j + 1 << ": " << Cost_Matrix[i][j] << "\n";
            }
        }
        cout << "\n";
        
        // get matching in form of matching_list
        matching_list.resize((int) curr_length/2);
        for (int i = 0; i < matching_list.size(); i++)
        {
            matching_list[i].resize(2);
        }
        obj_value = Cost_Matrix[0][1];
        if (chinese_matching()) cout << "matching done!\n";
        
        
        // merge matched graphs together
        dummy_vector.resize(matching_list.size());
        for (int i = 0; i < matching_list.size(); i++)
        {
            //output for debug
            //cout << i+1 << ": " << matching_list[i][0] << " " << matching_list[i][1] << " " << t2_Matrix[matching_list[i][0]][matching_list[i][1]] << "\n";
            
            merge_to_first(matching_list[i][0],matching_list[i][1], t2_Matrix[matching_list[i][0]][matching_list[i][1]]);
            dummy_vector[i] = matching_list[i][1];
            freq_of_line[matching_list[i][0]] = ggT(freq_of_line[matching_list[i][0]], freq_of_line[matching_list[i][1]]);
        }
        sort( dummy_vector.begin(), dummy_vector.end() );
        
        // erase second ones from vector of graphs
        for (int j = (int) dummy_vector.size() - 1; j >= 0; j--)
        {
            Adj_Matrices.erase(Adj_Matrices.begin()+dummy_vector[j]);
            existing_lines.erase(existing_lines.begin()+dummy_vector[j]);
            freq_of_line.erase(freq_of_line.begin()+dummy_vector[j]);
            for (int k = 0; k < nbr_of_stops; k++)
            {
                stop_in_cluster[k].erase(stop_in_cluster[k].begin()+dummy_vector[j]);
            }
        }
    }
    
    cout << obj_value << " is the objective value.\n";
    
    cout << "Finish\n";
    
}
int matching_merge::synchronize (int i, int j) // synchronizes two line(-clusters), returns t2 of evaluation of this lineset
{
    int t2 = 0, minimum = biiig_M, obj_val, common_stop;
    int cap = max(freq_of_line[i],freq_of_line[j]);
    cap = (int) period / cap;
    
    common_stop = 0;
    for (int k = 0; k < nbr_of_stops; k++)
    {
        if ( stop_in_cluster[k][i] == 1 && stop_in_cluster[k][j] == 1 )
        {
            common_stop = 1;
            break;
        }
    }
    if (common_stop)
    {
        for (int time_shift = 0; time_shift < cap; time_shift++)
        {
            merge_to_temp(i,j,time_shift);
            obj_val = mod_evaluate(i, j);
            if (obj_val < minimum) {
                t2 = time_shift;
                minimum = obj_val;
            }
        }
    }
    Cost_Matrix[i][j] = minimum;
    Cost_Matrix[j][i] = Cost_Matrix[i][j];
    return t2;
}
int matching_merge::mod_evaluate (int a, int b) // evaluates linecluster TEMP. speed improvements as we know which lines are being merged.
{
    int line_a = existing_lines[a], line_b = existing_lines[b];
    long int obj_val = 0;
    
    for (int i = 1; i < nbr_of_stops+1; i++)
    {
        // get all shortest paths from stop i
        dummy_vector.resize(0);
        dummy_vector.resize(nbr_of_stops+1, big_M);
        mod_2_shortest_path(i-1, line_a, line_b);
        for (int j = 1; j < nbr_of_stops + 1; j++) {
            obj_val += (dummy_vector[j] * OD_Matrix[i][j]);
        }
    }
    //return obj_val;
    return round(10000000*log(obj_val));
}

void matching_merge::merge_to_temp(int a, int b, int t2) // merges two line(-clusters) of vector Graphs into TEMP
{
    int line_a = existing_lines[a];
    int line_b = existing_lines[b];
    
    // implicitly also clears TEMP
    for (int i=0; i < nbr_of_events; i++) {
        for (int j=0; j < nbr_of_events; j++) {
            // is ok as we join disjoint sets
            TEMP[i][j] = Adj_Matrices[a][i][j] + Adj_Matrices[b][i][j];
        }
    }
    for (int i=0; i < nbr_of_activities; i++){
        
        int tail = corr_tail_to_activity_index[i], head = corr_head_to_activity_index[i];
        int mod = (int) period/freq_of_event[head];
        
        // assign times to "change"-edges
        if (corr_line_to_event_index[tail] == line_a && corr_line_to_event_index[head] == line_b)
        {
            int dur = (t2 + timetable[head] - timetable[tail] - Duration[tail][head]) % mod;
            if (dur < 0) {
                dur += mod;
            }
            dur += Duration[tail][head];
            TEMP[tail][head] = dur;
        }
        else if (corr_line_to_event_index[tail] == line_b && corr_line_to_event_index[head] == line_a)
        {
            int dur = (timetable[head] - timetable[tail] - t2 - Duration[tail][head]) % mod;
            if (dur < 0) {
                dur += mod;
            }
            dur += Duration[tail][head];
            TEMP[tail][head] = dur;
        }
    }
    for (int i = 0; i < nbr_of_events; i++) {
        Adj_List[i].resize(0);
        for (int j = 0; j < nbr_of_events; j++) {
            if (i != j && TEMP[i][j] > 0) {
                Adj_List[i].push_back(j);
            }
        }
    }
}
void matching_merge::merge_to_first(int a, int b, int t2) // merges second into first linecluster
{
    int line_a = existing_lines[a];
    int line_b = existing_lines[b];
    
    for (int i = 0; i < nbr_of_events; i++) {
        for (int j = 0; j < nbr_of_events; j++) {
            Adj_Matrices[a][i][j] = Adj_Matrices[a][i][j] + Adj_Matrices[b][i][j];
        }
    }
    for (int i=0; i < nbr_of_activities; i++){
        
        int tail = corr_tail_to_activity_index[i], head = corr_head_to_activity_index[i];
        int mod = (int) period/freq_of_event[head];
        
        if (line_a != line_b && corr_line_to_event_index[tail] == line_a && corr_line_to_event_index[head] == line_b)
        {
            int dur = (t2 + timetable[head] - timetable[tail] - Duration[tail][head]) % mod;
            if (dur < 0) {
                dur += mod;
            }
            dur += Duration[tail][head];
            Adj_Matrices[a][tail][head] = dur;
        }
        else if (line_a != line_b && corr_line_to_event_index[tail] == line_b && corr_line_to_event_index[head] == line_a)
        {
            int dur = (timetable[head] - timetable[tail] - t2 - Duration[tail][head]) % mod;
            if (dur < 0) {
                dur += mod;
            }
            dur += Duration[tail][head];
            Adj_Matrices[a][tail][head] = dur;
        }
    }
    
    // update timetable
    for (int i = 0; i < nbr_of_events; i++) {
        if (corr_line_to_event_index[i] == line_b) {
            corr_line_to_event_index[i] = line_a;
            timetable[i] = (timetable[i] + t2) % period;
        }
    }
    // update stop_matrix
    for (int i = 0; i < nbr_of_stops; i++) {
        if (stop_in_cluster[i][b] == 1)
        {
            stop_in_cluster[i][a] = 1;
        }
        stop_in_cluster[i][b] = 0;
    }
    
}
void matching_merge::print_solution(string filename)
{
    std::ofstream output (filename.c_str());
    output << "#timetable found by matching-merge algorithm\n";
    time_t rawtime;
    struct tm * timeinfo;
    time ( &rawtime );
    timeinfo = localtime ( &rawtime );
    output << "#" << asctime(timeinfo);
    cout << "End time: " << asctime(timeinfo) << "\n";
    output << "#objective value: " << obj_value << "\n";
    
    for (int i = 0; i < nbr_of_events; i++)
    {
        int tmp = timetable[i];
        while (tmp < 0)
        {
            tmp += period;
        }
        if (tmp > period)
        {
            tmp %= period;
        }
        output << i+1 << "; " << tmp << "\n";
    }
    
    output.close();
    cout << "Ouput written!\n";
}
void matching_merge::matching(){
    
    /* CAUTION: bad matching below, only for testing purposes. Correct matching is located in matching.h */
    for (int i = 0; i < (int)(Adj_Matrices.size())/2; i++)
    {
        matching_list[i][0] = 2*i;
        matching_list[i][1] = 2*i + 1;
    }
    
}
// returns shortest paths from stop_from to all other stops
void matching_merge::mod_2_shortest_path(int stop_from, int line_a, int line_b)
{
    dummy_vector[stop_from+1] = 0;
    
    vector<int> label;
    int can, s;

    label.resize(nbr_of_events, biiig_M);
    
    set<std::pair<weight_t, vertex_t> > vertex_queue;
    
    // get all start events from stop index
    for (int i = 0; i < corr_dep_events_to_stop_index[stop_from].size(); i++)
    {
        s = corr_line_to_event_index[corr_dep_events_to_stop_index[stop_from][i]];
        if ((s == line_a || s == line_b))
        {
            label[corr_dep_events_to_stop_index[stop_from][i]] = 0;
            vertex_queue.insert(make_pair(0, corr_dep_events_to_stop_index[stop_from][i]));
        }
    }
    
    while (!vertex_queue.empty())
    {
        // get and delete Minimum
        vertex_t chosen_candidate = vertex_queue.begin()->second;
        weight_t dist = vertex_queue.begin()->first;
        vertex_queue.erase(vertex_queue.begin());
        
        for (int i = 0; i < Adj_List[chosen_candidate].size(); i++)
        {
            can = Adj_List[chosen_candidate][i];
            if (label[can] > dist + TEMP[chosen_candidate][can])
            {
                // insert in binary search tree
                vertex_queue.erase(make_pair(label[can],can));
                label[can] = dist + TEMP[chosen_candidate][can];
                vertex_queue.insert(make_pair(label[can], can));
                if (label[can] < dummy_vector[corr_stop_to_event_index[can]])
                {
                    dummy_vector[corr_stop_to_event_index[can]] = label[can];
                }
            }
        }
    }
    
    return;
}

void matching_merge::solve_fast()
{
    while (Adj_Matrices.size() > 1)
    {
        curr_length = (int) Adj_Matrices.size();
        cout << curr_length << " line clusters atm..\n";
        // resize matrices
        Cost_Matrix.resize(curr_length);
        t2_Matrix.resize(curr_length);
        for (int i = 0; i < curr_length; i++)
        {
            Cost_Matrix[i].resize(curr_length);
            t2_Matrix[i].resize(curr_length);
        }
        
        /*for (int k = 0; k < nbr_of_stops; k++) {
            for (int i = 0; i < curr_length; i++) {
                cout << stop_in_cluster[k][i] << " ";
            }
            cout << "\n";
        }*/
        
        // match lines with common stop together
        for (int i = 0; i < curr_length; i++)
        {
            for (int j = i+1; j < curr_length; j++)
            {
                Cost_Matrix[i][j] = 10;
                for (int k = 0; k < nbr_of_stops; k++)
                {
                    if (stop_in_cluster[k][i] == 1 && stop_in_cluster[k][j] == 1)
                    {
                        Cost_Matrix[i][j] = 1;
                    }
                }
                Cost_Matrix[j][i] = Cost_Matrix[i][j];
            }
        }
        // get matching in form of matching_list
        matching_list.resize((int) curr_length/2);
        for (int i = 0; i < matching_list.size(); i++)
        {
            matching_list[i].resize(2);
        }
        if (chinese_matching()) cout << "matching done!\n";
        
        // get costs of possible matchings
        for (int k = 0; k < curr_length/2; k = k+1)
        {
            int i = matching_list[k][0];
            int j = matching_list[k][1];
            //cout << i << " " << j << " ";
            t2_Matrix[i][j] = synchronize(i,j);
            //cout << t2_Matrix[i][j] << "\n";
            t2_Matrix[j][i] = t2_Matrix[i][j];
        }
        
        // merge graphs to first one in matching
        dummy_vector.resize(matching_list.size());
        for (int i = 0; i < matching_list.size(); i++)
        {
            merge_to_first(matching_list[i][0],matching_list[i][1], t2_Matrix[matching_list[i][0]][matching_list[i][1]]);
            dummy_vector[i] = matching_list[i][1];
            freq_of_line[matching_list[i][0]] = ggT(freq_of_line[matching_list[i][0]], freq_of_line[matching_list[i][1]]);
        }
        sort( dummy_vector.begin(), dummy_vector.end() );
        
        // erase second ones from vector of graphs
        for (int j = (int) dummy_vector.size() - 1; j >= 0; j--)
        {
            Adj_Matrices.erase(Adj_Matrices.begin()+dummy_vector[j]);
            existing_lines.erase(existing_lines.begin()+dummy_vector[j]);
            freq_of_line.erase(freq_of_line.begin()+dummy_vector[j]);
            for (int k = 0; k < nbr_of_stops; k++)
            {
                stop_in_cluster[k].erase(stop_in_cluster[k].begin()+dummy_vector[j]);
            }
        }
    }
    
    cout << Cost_Matrix[0][1] << " is the objective value.\n";
    
    cout << "Finish\n";
    
}
