//
//  timetable.h
//  matching-merge
//

#ifndef __MATCHING_MERGE_H__
#define __MATCHING_MERGE_H__


#include <stdio.h>
#include <vector>
#include <string>

class matching_merge
{
public:
    //Constructor and destructor.
    matching_merge(int seed);
    ~matching_merge();
    
    // read in data and initialize stuff
    void init(std::string activities_file, std::string events_file, std::string od_matrix_file, std::string lines_file, int given_period);
    
    // main function
    void solve();
    void solve_fast();
    
    // routines in solve()
    int synchronize(int i, int j);
    int evaluate();
    int mod_evaluate(int i, int j);
    void merge_to_temp(int i, int j, int t2);
    void merge_to_first(int i, int j, int t2);
    
    // prompt/data output
    void print_solution(std::string filename);
    
    // standard graph routines
    void matching();
    int shortest_path(int s, int t);
    int mod_shortest_path(int s, int t, int, int);
    void mod_2_shortest_path(int stop_from, int, int);
    
private:
    
    typedef int vertex_t, weight_t;
    
    int period, nbr_of_events, nbr_of_lines, dummy, nbr_of_activities, nbr_of_stops,  biiig_M = 1e9, obj_value;

    //===============================================================================================
    // important stuff                                                                              =
    std::vector<int> timetable; // starting times for each event                                    =
    int big_M = 1000;           // for evaluating                                                   =
    //                                                                                              =
    std::vector<std::vector<std::vector<int> > > Adj_Matrices;  // List of matrices (EAN-networks)  =
    //===============================================================================================
    
    
    // "database" stuff (better: create structures -- sorry, next time..)
    std::vector<int> corr_stop_to_event_index; // vector containing stop number (beginning at 1) of corresponding event_index
    std::vector<int> corr_line_to_event_index; // vector containing line number of corresponding event_index
    std::vector<int> freq_of_event;
    std::vector<int> event_is_arrival;
    
    std::vector<int> corr_tail_to_activity_index;
    std::vector<int> corr_head_to_activity_index;

    std::vector<std::vector<int> >  corr_dep_events_to_stop_index;
    std::vector<std::vector<int> >  corr_arr_events_to_stop_index;
    std::vector<std::vector<int> >  stop_in_cluster;                // is stop k reached by cluster i?
    
    std::vector<int> existing_lines;
    std::vector<int> freq_of_line;
    std::vector<int> inv_existing_lines;
    
    // the rest
    std::vector<std::vector<int> > Duration;        // minimum duration of each event
    std::vector<std::vector<int> > OD_Matrix;       // nbr of passengers from one stop to other
    std::vector<std::vector<int> > t2_Matrix;       // corresponding t_2 value
    std::vector<std::vector<int> > Adj_List;
    
    std::vector<std::vector<int> > TEMP;            // temporary matrix
    std::vector<int> dummy_vector;

};

#endif
