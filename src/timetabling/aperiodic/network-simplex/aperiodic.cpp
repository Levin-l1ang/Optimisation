#include "aperiodic.h"

//stl stuff
#include <fstream>
#include <string>
#include <vector>
#include <iostream>
#include <limits>

using namespace std;

struct Edge
{
	int from;
	int to;
	int min;
	int max;
	Edge(int f, int t, int mi, int ma):from(f),to(t),min(mi),max(ma){};
};

aperiodic::aperiodic()
{}

void aperiodic::solve(std::string edge, std::string activity, std::string timetable)
{
	gob = new goblinController();
	gob->traceLevel = 0;
	gob->methMCF = abstractMixedGraph::MCF_BF_SIMPLEX;
	sparseDiGraph *graph = new sparseDiGraph((TNode)0, *gob);
	sparseRepresentation* goblin_rep = static_cast<sparseRepresentation*> (graph->Representation());

	string line;

	int nodes = 0;
	ifstream nodefile(edge.c_str());
	while (!nodefile.eof())
	{
		getline(nodefile,line);
		if(line!=""&&(line.c_str())[0]>=48&&(line.c_str())[0]<=57)
			++nodes;
	}
	nodefile.close();

	vector<int> demand;
	for (int i=0; i<nodes; ++i)
	{
		graph->InsertNode();
		demand.push_back(0);
	}

	vector<Edge> edges;
	ifstream edgefile (activity.c_str());
	while (!edgefile.eof())
	{
		getline(edgefile,line);
		if(line!=""&&(line.c_str())[0]>=48&&(line.c_str())[0]<=57)
		{
			size_t pos = line.find(";");
			line=line.substr(pos+1);
			pos = line.find(";");
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
			demand[tail-1]+=weight;
			demand[head-1]-=weight;

			goblin_rep->SetDemand(tail-1,demand[tail-1]);
			goblin_rep->SetDemand(head-1,demand[head-1]);

			graph->InsertArc(tail-1,head-1,InfCap,max,0);
			graph->InsertArc(head-1,tail-1,InfCap,-min,0);

			edges.push_back(Edge(tail-1,head-1,min,max));
		}
	}
	edgefile.close();

	double objective = - graph->MinCostBFlow();
	//check feasibility
	bool feas = true;
	for (uint i=0; i<edges.size() && feas==true; ++i)
	{
		int duration = graph->Pi(edges[i].to) - graph->Pi(edges[i].from);
		if (edges[i].max >  duration || edges[i].min < duration)
			feas = false;
	}
	
	if (!feas)
	{
		cout<<"Problem is infeasible!\n";
		exit(0);
	}
	cout<<"Optimum found!\n";

	ofstream out(timetable.c_str());
	out<<"#Optimal Aperiodic Timetable\n#Found by classic network simplex.\n";
	out<<"#Objective value: "<<objective<<"\n";
	int min=std::numeric_limits<int>::max();
	for (uint i=0; i<nodes; ++i)
		if (graph->Pi(i) < min)
			min = graph->Pi(i);
	for (uint i=0; i<nodes; ++i)
		out<<i<<"; "<<graph->Pi(i) - min<<"\n";
	out.close();
	
}
