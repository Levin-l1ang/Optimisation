#include <iostream>
#include <fstream>
#include <cstdlib>
#include <algorithm>
#include <functional>
#include <ctime>
#include <stdlib.h>

#include "conprop.h"

using namespace conprop;

graph::graph()
{

}

void graph::init(std::string file, int in_period)
{
	int vertices = 0;
	//std::string headway("headway");

	//srand ( time(NULL) );
	int r;

	std::ifstream data(file.c_str());
	if(!data.good())
		throw std::runtime_error("Activities File missing!");
	std::string line;
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

			int tail, head, min, max, weight;

			tail=atoi(line.c_str());
			pos = line.find(";");
			line=line.substr(pos+1);

			head=atoi(line.c_str());
			pos = line.find(";");
			line=line.substr(pos+1);

			min=atoi(line.c_str());
			pos = line.find(";");
			line=line.substr(pos+1);

			max=atoi(line.c_str());
			pos = line.find(";");
			line=line.substr(pos+1);

			weight=atoi(line.c_str());

			vertices = std::max(vertices, std::max(tail, head));
			
			if (max - min < in_period -1)
			{
				//if (name.find(headway) == std::string::npos)
				//{
					while (min<0)
					{
						min += in_period;
						max += in_period;
					}
					//std::cout<<"Head: "<<head<<" Vertices: "<<vertices<<"\n";
					bounds.insert(std::pair<edge,interval>(edge(tail-1,head-1),interval(min,max)));

				//}
				//else
				//{
				//	r = rand()%100;
				//	if (r < 100)
				//	{
				//	vertices = std::max(vertices, std::max(tail, head));
				//	bounds.insert(std::pair<edge,interval>(edge(tail-1,head-1),interval(min,max)));
				//	}
				//}
			}
		}
	}
	data.close();

	num_edges = bounds.size();
	num_vertices = vertices;

	std::cout<<"Num edges: "<<bounds.size()<<"\n"<<std::flush;
	std::cout<<"Num vertices: "<<vertices<<"\n"<<std::flush;

	period = in_period;

}

potential::potential()
{

}

inline int potential::mod(int i, int T)
{
	if (i>=0)
		return i%T;
	else if (i%T == 0)
		return 0;
	else
		return (i%T)+T;
}

void potential::reset()
{
	m_timetable.resize(the_graph->num_vertices);
	searchspace.resize(the_graph->num_vertices);
	fix.resize(the_graph->num_vertices);
	for (int i=0; i<the_graph->num_vertices; ++i)
	{
		m_timetable[i].resize(the_graph->period);
		for (int j=0; j<the_graph->period; ++j)
			m_timetable[i][j] = true;
		fix[i] = -1;
		searchspace[i]=interval(0,the_graph->period-1);
	}
}

bool potential::update()
{
	int period = the_graph->period;
	int vert = the_graph->num_vertices;

	for (int i=0; i<vert; ++i)
	{
		if (fix[i] == -1)
		{
			for (int j=0; j<period; ++j)
				m_timetable[i][j] = true;
			searchspace[i] = interval(0,period-1);
		}
		else
		{
			for (int j=0; j<period; ++j)
				m_timetable[i][j] = false;			
			m_timetable[i][fix[i]] = true;
			searchspace[i] = interval(fix[i],fix[i]);
		}
	}

	//for (int v = 0; v<1; ++v)
	bool change = true;
	while(change)
	{
		change = false;
		for (std::multimap<edge,interval>::iterator it = the_graph->bounds.begin(); it!= the_graph->bounds.end(); ++it)
		{
			bool exists;
			int dist;

			//left to right
			for (int i=searchspace[((*it).first).first].first; i<=searchspace[((*it).first).first].second; ++i)
				if (m_timetable[((*it).first).first][i])
				{
					dist = ((*it).second).first;
					exists = false;
					while (exists == false && dist <= ((*it).second).second)
					{
						if (m_timetable[((*it).first).second][mod(i+dist,period)]) 
							exists = true;
						else
							++dist;
					}
					if (exists == false)
					{
						m_timetable[((*it).first).first][i] = false;
						change = true;
					}
				}

			//right to left
			for (int i=searchspace[((*it).first).second].first; i<=searchspace[((*it).first).second].second; ++i)
				if (m_timetable[((*it).first).second][i])
				{
					dist = ((*it).second).first;
					exists = false;
					while (exists == false && dist <= ((*it).second).second)
					{
						if (m_timetable[((*it).first).first][mod(i-dist,period)]) 
							exists = true;
						else
							++dist;
					}
					if (exists == false)
					{
						m_timetable[((*it).first).second][i] = false;
						change = true;
					}
				}

			//check feasibility
			exists = false;
			for (int i=0; i<period; i++)
				if (m_timetable[((*it).first).first][i])
					exists = true;
			if (exists == false)
				return false;
			exists = false;

			for (int i=0; i<period; i++)
				if (m_timetable[((*it).first).second][i])
					exists = true;
			if (exists == false)
				return false;

			/*
			//adjust search spaces
			while (m_timetable[((*it).first).first][searchspace[((*it).first).first].first] == false)
				++searchspace[((*it).first).first].first;

			while (m_timetable[((*it).first).first][searchspace[((*it).first).first].second] == false)
				--searchspace[((*it).first).first].second;

			while (m_timetable[((*it).first).second][searchspace[((*it).first).second].first] == false)
				++searchspace[((*it).first).second].first;

			while (m_timetable[((*it).first).second][searchspace[((*it).first).second].second] == false)
				--searchspace[((*it).first).second].second;
			*/
		}
	}

	return true;
}

prop::prop(std::string file, int period, searchmode mode, bool anim, std::string draw_dir, int seed)
{
	pic_counter = 0;
	m_graph.init(file, period);
	smode = mode;
	if (seed = 0)
	   srand(time(NULL));
	else
	   srand(seed);
	animate = anim;
	animate_dir = draw_dir;
	use_time = false;
}

bool prop::check_feasibility_heuristic()
{
	p.the_graph = &m_graph;
	std::cout<<"Searched:    "<<std::flush;
	int kmax = m_graph.num_vertices;
	for (int k=0; k<kmax; ++k)
	{
		if (k%50 == 0)
			if ((100*k)/kmax < 10)
				std::cout<<"\b\b"<<(100*k)/kmax<<"%"<<std::flush;
			else
				std::cout<<"\b\b\b"<<(100*k)/kmax<<"%"<<std::flush;
		p.reset();
		p.fix[k] = 0;
		if (p.update() == false)
		{
			std::cout<<"\n";
			return false;
		}
	}
	std::cout<<"\n";
	return true;
}

bool prop::solve()
{
	p.the_graph = &m_graph;
	p.reset();
	if (smode == RANDOM)
	{
		int k = rand() % m_graph.num_vertices;
		//k = 16;
		p.fix[k] = 0;
		std::cout<<"Beginning with node "<<k<<".\n";
	}
	else
		p.fix[0] = 0;

	return (recurse());

	/*
	if (recurse(&p))
	{
		std::cout<<"Feasible timetable found!\n";
		for (int i=0; i< p.the_graph->num_vertices; ++i)
			for (int j=0; j<m_graph.period; ++j)
				if (p.m_timetable[i][j])
					std::cout<<"Node "<<i+1<<": "<<j<<"\n";
	}
	else
		std::cout<<"No feasible timetable exists.\n";
	*/
}

bool prop::recurse()
{
        if (use_time)
        {
            time_t now = time(NULL);
            if (time_limit < now)
            {
                std::cout<<"***Time limit reached.***\nAborting.";
                exit(1);
            }
        }

	++pic_counter;
	char bla[50];
	sprintf(bla, "%05d", pic_counter);
	int period = p.the_graph->period;
	if (p.update() == false)
	{
		if (animate)
			p.draw("/pic"+std::string(bla)+".dot", animate_dir);
		return false;
	}
	else
	{
		if (animate)
			p.draw("/pic"+std::string(bla)+".dot", animate_dir);
		//two possible node potentials must be available
		int rek_node = 0;
		bool foundfirst = false;
		bool foundsecond = false;
		int i;
		while(foundsecond == false && rek_node < p.the_graph->num_vertices)
		{
			if (p.fix[rek_node] == -1)
			{
			i = 0;
			foundfirst = false;
			foundsecond = false;
			while (foundfirst == false && i<period)
				if (p.m_timetable[rek_node][i]) 
					foundfirst = true;
				else
					++i;

			++i;

			while (foundsecond == false && i<period)
				if (p.m_timetable[rek_node][i]) 
					foundsecond = true;
				else
					++i;
			if (i == period)
				foundsecond = false;
			}
			++rek_node;
		}

		--rek_node;

		if (foundsecond == false && p.feasible())
			return true;

		std::list<int> candidats;
		switch (smode)
		{
			case UP:
				for (i = 0; i<period; ++i)
					if (p.m_timetable[rek_node][i])
						candidats.push_back(i);
				break;
			case DOWN:
				for (i = period-1; i>=0; --i)
					if (p.m_timetable[rek_node][i])
						candidats.push_back(i);
				break;
			case RANDOM:
				{
				std::vector<int> shuffle;
				shuffle.reserve(period);
				for (i = 0; i<period; ++i)
					if (p.m_timetable[rek_node][i])
						shuffle.push_back(i);
				std::random_shuffle(shuffle.begin(), shuffle.end());
				for (std::vector<int>::iterator vec = shuffle.begin(); vec != shuffle.end(); ++vec)
					candidats.push_back(*vec);
				}
				break;
			default: break;
		}

		

		for (std::list<int>::iterator it = candidats.begin(); it != candidats.end(); ++it)
		{
			if (rek_node % 10 == 9)
				std::cout<<"Fixing node "<<rek_node+1<<" of "<<p.the_graph->num_vertices<<" to  "<<*it<<".\n"<<std::flush;
			p.fix[rek_node] = *it;
			if (recurse())
				return true;
		}
		p.fix[rek_node] = -1;
		return false;
	}
}

void prop::write(std::string file)
{
	std::ofstream data(file.c_str());

	data<<"#Feasible periodic timetable\n";
	data<<"#Found by constraint propagation\n";

	for (int i=0; i< p.the_graph->num_vertices; ++i)
		for (int j=0; j<m_graph.period; ++j)
				if (p.m_timetable[i][j])
					data<<i+1<<"; "<<j<<"\n";	
}

bool potential::feasible()
{
	std::vector<int> time;
	time.resize(the_graph->num_vertices);

	for (int i=0; i< the_graph->num_vertices; ++i)
		for (int j=0; j<the_graph->period; ++j)
				if (m_timetable[i][j])
					time[i] = j;

	int i,j;
	int l, u;
	int x;
	int T = the_graph->period;
	for (std::multimap<edge,interval>::iterator it = the_graph->bounds.begin(); it!= the_graph->bounds.end(); ++it)
	{
		i = (*it).first.first;
		j = (*it).first.second;
		l = (*it).second.first;
		u = (*it).second.second;
		x = time[j] - time[i];
		while (x < l)
			x+=T;
		if (x > u)
			return false;
	}
	return true;
}

void potential::draw(std::string filename, std::string dirname)
{
	std::ofstream out((dirname + filename).c_str());
	if (!out)
	{
		int ret;
		ret = system(("echo \"Creating the directory " + dirname + "\"\n").c_str());
		ret = system(("mkdir " + dirname).c_str());
		out.open((dirname + filename).c_str());
		if (!out)
		{
			std::cout<<"Cannot write to "<<dirname<<"! Aborting.\n";
			exit(0);
		}
	}
	out<<"graph G\n {\n size = \"5,5\"\n ordering=out;\n";
	std::vector<int> dof(the_graph->num_vertices);
	std::fill(dof.begin(), dof.end(), 0);
	for (int i=0; i< the_graph->num_vertices; ++i)
		for (int j=0; j<the_graph->period; ++j)
				if (m_timetable[i][j])
					++dof[i];

	int period = the_graph->period;

	for (int i=0; i< the_graph->num_vertices; ++i)
	{
		out<<" "<<i+1<<" [style=filled, fillcolor=\"#";
		if (dof[i] == 0)		
			out<<"000000";
		else
			out<<"FF"<<hexadec(255*(double)(dof[i]-1)/period)<<hexadec(255*(double)(dof[i]-1)/period);
		out<<"\"];\n";
	}

	int left, right;
	for (std::multimap<edge,interval>::iterator it = the_graph->bounds.begin(); it!= the_graph->bounds.end(); ++it)
	{
		left = (*it).first.first;
		right = (*it).first.second;
		out<<" "<<left+1<<" -- "<<right+1<<" [color=\"#000000\"];\n";
	}
	
	
	out<<" }\n\n";
	out.close();
}

std::string potential::hexadec(int zahl)
  {
  std::string output;
  int gross = zahl / 16;
  zahl = zahl % 16;
  if (gross == 0) output += "0";
  if (gross == 1) output += "1";
  if (gross == 2) output += "2";
  if (gross == 3) output += "3";
  if (gross == 4) output += "4";
  if (gross == 5) output += "5";
  if (gross == 6) output += "6";
  if (gross == 7) output += "7";
  if (gross == 8) output += "8";
  if (gross == 9) output += "9";
  if (gross == 10) output += "A";
  if (gross == 11) output += "B";
  if (gross == 12) output += "C";
  if (gross == 13) output += "D";
  if (gross == 14) output += "E";
  if (gross == 15) output += "F";
  if (zahl == 0) output += "0";
  if (zahl == 1) output += "1";
  if (zahl == 2) output += "2";
  if (zahl == 3) output += "3";
  if (zahl == 4) output += "4";
  if (zahl == 5) output += "5";
  if (zahl == 6) output += "6";
  if (zahl == 7) output += "7";
  if (zahl == 8) output += "8";
  if (zahl == 9) output += "9";
  if (zahl == 10) output += "A";
  if (zahl == 11) output += "B";
  if (zahl == 12) output += "C";
  if (zahl == 13) output += "D";
  if (zahl == 14) output += "E";
  if (zahl == 15) output += "F";
  return output;
  }

void prop::set_time_limit(int _time)
{
    use_time = true;
    time_t start_time = time(NULL);
    time_limit = start_time + _time;

}
