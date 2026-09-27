#ifndef _SHORTEST_PATHSV_H
#define _SHORTEST_PATHSV_H

#include <iostream>
#include <iterator>
#include <fstream>
#include<stdio.h>
#include<stdlib.h>
//#include<sstream>
#include<utility>
using namespace std;
#include<string>
#include<string.h>
#include<vector>
#include<queue>
#include<stack>
#include<sstream>

#include<algorithm>

class shortest_pathsv
{
 protected:
  struct event
  {
    int eventid;
    string eventtype;
    int station;
    int line;
    int passengers;
  };
  struct activity
  {
    int tailevent;
    int headevent;
    int exptailevent;
    int expheadevent;
    string type;
    int line;
    int waitchangetime;
    int traveltime;
    int totaltime;
    //int changetime;
  
  };
  struct expevent
  {
    int time;
    int expeventid;
  };
  struct passenger
  {
    int sourcestation;
    int targetstation;
    int deptime;
  };
  struct Comp
  {
    bool operator()(const activity& a, const activity& b)
    {
      return a.totaltime>b.totaltime;
    
    }
    bool operator()(const pair<int,int>& a, pair<int,int>& b)
    {
      return a.first>b.first;
    }
    bool operator()(const expevent& a,const expevent& b)
    {
      return a.time<b.time;
    }
  } myobject;
  //static priority_queue<activity, vector<activity>, Comp > actpq;
  //static priority_queue<pair<int,int> > pq;
  int infin;
  int stations;
  int repeattime;
  int starttime;
  vector<event> perevents;
  vector<vector<expevent> > expevents;
  vector<vector<activity> > graph;
  vector<bool> visit;
  vector<int> expeventtime;
  vector<int> deparrpair;
  vector<int> stationtime;
  vector<int> eventspath;
  
  activity route[300];
  int search_start_expevent(int l,int r,int pereventid,int deptime);
  
 public:
  vector<passenger> odmatrix;
  shortest_pathsv();
  void read_events_periodic(string event_filename);
  void read_activities_periodic(string activity_filename);
  void read_events_expanded(string event_filename);
  void read_activities_expanded(string activity_filename);
 
  void read_passenger_paths(string pass_filename);
  int dijkstra(int source, int target, int origdeptime);
  void printtest();
  void printroute(int source,int target);
  int mod(int t);
  int startperiodtime(int t);
  void sort_expanded_events();
  vector<int> getpath(int source,int target);
};
#endif
