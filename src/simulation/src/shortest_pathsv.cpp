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
#include "shortest_pathsv.h"

shortest_pathsv::shortest_pathsv() {
  infin=1000000;
  graph.reserve(3700);
  eventspath.reserve(300);
  expevents.reserve(125000);
  perevents.reserve(3700);
  //route.reserve(300);
  expeventtime.reserve(125000);
  deparrpair.reserve(125000);
  stationtime.reserve(300);
  odmatrix.reserve(3600000);
  visit.reserve(300);
  repeattime=28800;
  starttime=14400;
  
}

int shortest_pathsv::mod(int t)
{
  return t%repeattime;
}
int shortest_pathsv::startperiodtime(int t)
{
  return t-mod(t);
}
void shortest_pathsv::read_events_periodic(string event_filename)
{
  
  ifstream event_file;
  
  event_file.open(event_filename.c_str(), std::ifstream::in);
  stations= 0;
  string cur_line;
  if (event_file.is_open())
  {
    int count=0;
    while (getline(event_file, cur_line))
      {
        if (cur_line.empty())
	{
	  continue;
	}
	vector<std::string> entries;
	string s;
	istringstream f(cur_line);
	while (getline(f, s, ';'))
	{
	  //cout << s << endl;
	  entries.push_back(s);
	}
	
	event newevent;
		
	newevent.eventid = atoi(entries[0].c_str());
	newevent.station = atoi(entries[2].c_str());
	newevent.line= atoi(entries[3].c_str());
	newevent.passengers=0;
	string str=entries[1];
	str=str.substr(1);
	
	newevent.eventtype=str;
	perevents.push_back(newevent);
	if(newevent.station>stations)stations=newevent.station;
	
      }
  }
  else { //The periodic event file could not be opened.
    std::cerr << "Could not open periodic event file " << event_filename << std::endl;
    //throw std::runtime_error("Could not open event file!");
  }
  
}

void shortest_pathsv::read_activities_periodic(string activity_filename)
{
  std::ifstream act_file;
  act_file.open(activity_filename.c_str(), std::ifstream::in);
 
  int tail_event_index;
  string cur_line;
  int head_event_index;
  if (act_file.is_open())
  {
    while (getline(act_file, cur_line))
    {
      
      if (cur_line.empty())
      {
        continue;
      }
      vector<std::string> entries;
      string s;
      istringstream f(cur_line);
      while (getline(f, s, ';'))
      {
	  //cout << s << endl;
	  entries.push_back(s);
      }
	
      
      if (entries.size() != 7)
      {
        std::cerr << "There were " << entries.size() << "entries in the line " << cur_line << "instead of 7!"
            << std::endl;
        
      }
      //Get the corresponding events from the event_map
      
      tail_event_index = atoi(entries[2].c_str());
      head_event_index = atoi(entries[3].c_str());
      int tailevent_id = perevents[tail_event_index].eventid;
      int headevent_id = perevents[head_event_index].eventid;
      
      string acttype=entries[1].substr(1);
      activity newact;
      if(acttype=="\"drive\"")
	{
	  newact.tailevent=tailevent_id;
	  newact.headevent=headevent_id;
	  newact.type="drive";
	  newact.line=perevents[tail_event_index].line;
	  int depstation=perevents[tail_event_index].station;
	  
	  graph[depstation].push_back(newact);
	}
     
      else ;
      
    }
  }
  else { //The activity file could not be opened.
    std::cerr << "Could not open periodic activity file " << activity_filename << std::endl;
    // throw std::runtime_error("Could not open activity file!");
  }
}

void shortest_pathsv::read_events_expanded(string event_filename)
{
  
  ifstream event_file;
  
  event_file.open(event_filename.c_str(), std::ifstream::in);
  string cur_line;
  if (event_file.is_open())
  {
    
    while (getline(event_file, cur_line))
      {
        if (cur_line.empty())
	{
	  continue;
	}
	vector<std::string> entries;
	string s;
	istringstream f(cur_line);
	while (getline(f, s, ';'))
	{
	  //cout << s << endl;
	  entries.push_back(s);
	}
	
	int pereventid=atoi(entries[1].c_str());
	expevent newevent;
	//newevent.time=atoi(entries[3].c_str());//-starttime;
	newevent.expeventid=atoi(entries[0].c_str());
	newevent.time=atoi(entries[3].c_str());
	expevents[pereventid].push_back(newevent);	
	expeventtime[newevent.expeventid]=atoi(entries[3].c_str());//-starttime;
	
      }
  }
  else { //The expanded event file could not be opened.
    std::cerr << "Could not open expanded event file " << event_filename << std::endl;
    //throw std::runtime_error("Could not open event file!");
  }
}

void shortest_pathsv::read_activities_expanded(string activity_filename)
{
  std::ifstream act_file;
  act_file.open(activity_filename.c_str(), std::ifstream::in);
 
  int tail_event_index;
  string cur_line;
  int head_event_index;
  
  if (act_file.is_open())
  {
    while (getline(act_file, cur_line))
    {
      
      if (cur_line.empty())
      {
        continue;
      }
      vector<std::string> entries;
      string s;
      istringstream f(cur_line);
      while (getline(f, s, ';'))
      {
	  //cout << s << endl;
	  entries.push_back(s);
      }
	
      
      if (entries.size() != 7)
      {
        std::cerr << "There were " << entries.size() << "entries in the line " << cur_line << "instead of 7!"
            << std::endl;
        
      }
      //Get the corresponding events from the event_map
      
      tail_event_index = atoi(entries[3].c_str());
      head_event_index = atoi(entries[4].c_str());
            
      string acttype=entries[2].substr(1);
     
      if(acttype=="\"drive\"")
	{
	  deparrpair[tail_event_index]=head_event_index;
	}
     
      else ;
      
    }
  }
  else { //The activity file could not be opened.
    std::cerr << "Could not open expanded activity file " << activity_filename << std::endl;
    // throw std::runtime_error("Could not open activity file!");
  }
}



void shortest_pathsv::read_passenger_paths(string pass_filename)
{
  ifstream pass_file;
  
  pass_file.open(pass_filename.c_str(), std::ifstream::in);
  string cur_line;
  if (pass_file.is_open())
  {
    int count=0;
    while (getline(pass_file, cur_line))
      {
        if (cur_line.empty())
	{
	  continue;
	}
	vector<std::string> entries;
	string s;
	istringstream f(cur_line);
	while (getline(f, s, ';'))
	{
	  //cout << s << endl;
	  entries.push_back(s);
	}
	//passenger matrix starts from 1 and not 0
	passenger newpass;
	int sourceevent,targetevent;	
	sourceevent= atoi(entries[1].c_str());
	targetevent= atoi(entries[2].c_str());
	newpass.sourcestation=atoi(entries[3].c_str());
	newpass.targetstation=atoi(entries[4].c_str());
	newpass.deptime=this->expeventtime[sourceevent];
	odmatrix.push_back(newpass);
      }
  }
  else { //The passenger file could not be opened.
    std::cerr << "Could not open passenger file " << pass_filename << std::endl;
    //throw std::runtime_error("Could not open passenger file!");
  }
  
}

void shortest_pathsv::sort_expanded_events()
{
  vector<event>::iterator it;
  int i=1;it=perevents.begin();
  for(it++;it!=perevents.end();it++)
    {
      sort(expevents[i].begin(),expevents[i].end(),myobject);
      
      i++;
    }
}

int shortest_pathsv::search_start_expevent(int l,int r,int pereventid,int deptime)
{
  int m=(l+r)/2;
  if(l==r)return l;
  if(l<r)
    {
      //printf("%d %d \n",l,r);
      int val=expevents[pereventid][m].time;
      if(deptime>val)return search_start_expevent(m+1,r,pereventid,deptime);
      else return search_start_expevent(l,m,pereventid,deptime);
    }
  else return -1;
}

int shortest_pathsv::dijkstra(int source, int target, int origdeptime)
{
  priority_queue<activity, vector<activity>, Comp > actpq;
  priority_queue<pair<int,int>, vector<pair<int,int> >, Comp > pq;
  bool rolloutincrease=true;
  int anstime;
  int minchangetime=60;
  while(!pq.empty())
    {
      pq.pop();
      
    }
  pq.push(make_pair(origdeptime,source));
  
  int currstation,currtime;
  pair<int,int> currnode;
  fill(visit.begin(),visit.begin()+stations+1,false);
  fill(stationtime.begin(),stationtime.begin()+stations+1,infin);
  stationtime[source]=origdeptime;
  activity curract;
  while(!pq.empty())
    {
      currnode=pq.top();
      currstation=currnode.second;
      currtime=currnode.first;
      
      
      if(visit[currstation]==true)
	{
	  pq.pop();continue;
	}
      visit[currstation]=true;
      if(currstation==target){/*printroute(source,target);printf("dijkstra %d    ",stationtime[target]);*/anstime=stationtime[target];rolloutincrease=false;break;}
      vector<activity>::iterator it;
      for(it = graph[currstation].begin();it != graph[currstation].end();it++)
	{
	  int taileventid=perevents[it->tailevent].eventid;
	  int headeventid=perevents[it->headevent].eventid;
	  
	  vector<expevent>::iterator eventit;
	  int mindeptime=infin,minarrtime=infin;
	  int exptailevent,expheadevent;
	  bool flag=false;
	  int index;
	  int trnsfertime=0;
	  if(currstation==source)trnsfertime=0;
	  else if(it->line==route[currstation].line)trnsfertime=0;
	  else trnsfertime=minchangetime;
	  
	  index=search_start_expevent(0,expevents[taileventid].size(),taileventid,currtime+trnsfertime);
	  if(index==expevents[taileventid].size())continue;
	  exptailevent=expevents[taileventid][index].expeventid;
	  if(deparrpair[exptailevent]==0)continue;
	  else
	  {
	     expheadevent=deparrpair[exptailevent];
	     minarrtime=expeventtime[expheadevent];
	     mindeptime=expeventtime[exptailevent];
	  }
	  int arrstation=perevents[headeventid].station;
	   if(minarrtime<stationtime[arrstation])
	   {
	     stationtime[arrstation]=minarrtime;
	     pq.push(make_pair(stationtime[arrstation],arrstation));
	     activity newact;
	     newact.tailevent=it->tailevent;
	     newact.headevent=it->headevent;
	     newact.exptailevent=exptailevent;
	     newact.expheadevent=expheadevent;
	     if(actpq.empty())newact.type="wait";
	     else if(it->line==route[currstation].line)newact.type="wait";
	     else newact.type="change";
	     newact.line=it->line;
             newact.waitchangetime=mindeptime-currtime;
	     newact.traveltime=stationtime[arrstation]-mindeptime;
	     newact.totaltime=stationtime[arrstation];
	     route[arrstation]=newact;
	     
		      
	    }
	   	      
	}
      pq.pop();
      
    }
  if(rolloutincrease==true){/*printf("in dijkstra please increase the rollout time      ");*/anstime=-1;}
  return anstime;
    
}

void shortest_pathsv::printroute(int source,int target)
{
  int currstation=target;
  activity newact;
  while(true)
    {
      printf("currstation %d\n",currstation);
      newact=route[currstation];
      if(currstation==source)break;
      printf("tailevent %d headevent %d type %s waitchangetime %d traveltime %d totaltime %d \n",newact.exptailevent,newact.expheadevent,(newact.type).c_str(),newact.waitchangetime,newact.traveltime,newact.totaltime);
      currstation=perevents[newact.tailevent].station;
      }
}

vector<int> shortest_pathsv::getpath(int source,int target)
{
  int currstation=target;
  activity newact;
  stack<int> st;
  vector<int> path;
  while(true)
    {
      newact=route[currstation];
      if(currstation==source)break;
      st.push(newact.expheadevent);
      st.push(newact.exptailevent);
      currstation=perevents[newact.tailevent].station;
    }
  while(!st.empty())
    {
      int evnt=st.top();
      st.pop();
      path.push_back(evnt);
    }
  return path;
}
void shortest_pathsv::printtest()
{
  printf("printing event 5 eventid %d type%s station %d line %d\n",perevents[5].eventid,(perevents[5].eventtype).c_str(),perevents[5].station, perevents[5].line);
  printf("strations %d\n",stations);
   printf("dep station 7 %d %d\n",graph[11].front().tailevent, graph[11].front().headevent);
    printf("dep %d arr %d\n",10,deparrpair[10]);
    expevent newexpevent;
  newexpevent=expevents[1500].front();
  printf("time id %d \n",newexpevent.expeventid);
 
  
}
