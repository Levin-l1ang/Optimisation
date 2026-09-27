/* Convert.cc
*Hilfsprogramm
*
*Bereitet die gegebenen Daten in eine
*f�r xpress geeignete Form auf
*
*Ben�tigt
*-basis/Load.giv		(Min und Maxfrequenzen + Kanten)
*-basis/Pool.giv		(Linienpool)
*-Pool-cost.giv	(Kosten)
*
*Ausgabe
*"moseldaten" in der Form
*
*nr_lines: ?
*nr_edges: ?
*A:[?
*?
*?]
*cost:[?]
*Min:[?]
*Max:[?]
*/

#include <iostream>
#include <fstream>
#include <cstdlib>
#include <vector>
#include <set>
#include <algorithm>
#include <string>

#include "../../essentials/config/config.h"

using namespace std;

struct od_entry
{
	od_entry(unsigned short a, unsigned short b, double value)
	{
		i = a;
		j = b;
		val = value;
	}
	unsigned short i;
	unsigned short j;
	double val;
};

struct edge
{
	edge(unsigned short i, unsigned short j)
	{
		s = i;
		t = j;
	};
	unsigned short s;
	unsigned short t;
};


int main(void)
{
    cout << "Begin reading configuration" << endl;

    config::from_file("basis/Config.cnf", false);


    string edge_file_name = config::get_string_value("default_edges_file");
    string load_file_name = config::get_string_value("default_loads_file");
    string pool_file_name = config::get_string_value("default_pool_file");
    string pool_cost_file_name = config::get_string_value("default_pool_cost_file");
    string od_file_name = config::get_string_value("default_od_file");
    int passengers_per_vehicle = atoi(config::get_string_value("gen_passengers_per_vehicle").c_str());
    string log_level = config::get_string_value("console_log_level");
    transform(log_level.begin(), log_level.end(), log_level.begin(), ::toupper);
    bool verbose_output = log_level.compare("DEBUG") == 0;
    int timelimit = config::get_integer_value("lc_timelimit");
    int thread_limit = config::get_integer_value("lc_threads");
    double mip_gap = config::get_double_value("lc_mip_gap");
    bool write_lp_file = config::get_bool_value("lc_write_lp_file");

    cout << "Finished reading configuration" << endl;
    cout << "Begin reading input data" << endl;

	int temp;
	string text;
	int nr_lines;
	int nr_edges;
	int L[3000][500];
	vector<edge> edges;
	vector<set<unsigned short> > line_knots;
	int Min[3000];
	int Max[3000];
	double cost[3000];
	vector<od_entry> OD;
	size_t pos;

	//Empty matrices
	for (int i=0; i<=2999; i++)
		for(int j=0; j<=499; j++)
			{
			L[i][j]=0;
			//OD[i][j]=0;
			}

	if (verbose_output) {
        cout << "Reading in edges..." << flush;
    }
	ifstream edgef (edge_file_name.c_str());
	int source, sink;
    while (!edgef.eof())
    {
		getline(edgef,text);
		    if(text!=""&&(text.c_str())[0]>=48&&(text.c_str())[0]<=57)
		{
		nr_edges=atoi(text.c_str());

		pos=text.find(";");
		text=text.substr(pos+1);
		source = atoi(text.c_str());

		pos=text.find(";");
		text=text.substr(pos+1);
		sink = atoi(text.c_str());
		
		edge e(source,sink);
		edges.push_back(e);

		/*
		pos=text.find(";");
		text=text.substr(pos+1);
		cost[nr_edges-1] = atof(text.c_str());

		pos=text.find(";");
		text=text.substr(pos+1);
		Min[nr_edges-1]=atoi(text.c_str());

		pos=text.find(";");
		text=text.substr(pos+1);
		Max[nr_edges-1]=atoi(text.c_str());
		*/
		}
    }	   
	
	edgef.close();

	ifstream load(load_file_name.c_str());
    while (!load.eof())
    {
		getline(load,text);
		    if(text!=""&&(text.c_str())[0]>=48&&(text.c_str())[0]<=57)
		{
		nr_edges=atoi(text.c_str());

		pos=text.find(";");
		text=text.substr(pos+1);

		pos=text.find(";");
		text=text.substr(pos+1);
		Min[nr_edges-1]=atoi(text.c_str());

		pos=text.find(";");
		text=text.substr(pos+1);
		Max[nr_edges-1]=atoi(text.c_str());
		}
    }

	load.close();
    if (verbose_output) {
        cout << "done." << endl << flush;
    }


	ifstream pool (pool_file_name.c_str());
	
	int last_line_number = 0;
	int control = 1;

    if (verbose_output) {
        cout << "Reading in lines..." << flush;
    }
	while(!pool.eof())
	{
		getline(pool,text);
		    if(text!=""&&(text.c_str())[0]>=48&&(text.c_str())[0]<=57)
		{
		nr_lines=atoi(text.c_str());
		if (last_line_number != nr_lines)
		{
			last_line_number = nr_lines;
			set<unsigned short> temp_set;
			line_knots.push_back(temp_set);
			control = 1;
		}
		pos=text.find(";");
		text=text.substr(pos+1);
		
		if (atoi(text.c_str()) != control)
		{
			cout<<"ERROR: No consistent numbering in line "<<last_line_number<<"\n";
			exit(1);
		}
		else
			++control;
		  
		pos=text.find(";");
		text=text.substr(pos+1);

		temp=atoi(text.c_str());
		edge e = edges[temp-1];
		line_knots[nr_lines-1].insert(e.s);
		line_knots[nr_lines-1].insert(e.t);

		L[nr_lines-1][temp-1]=1;
		}
	}
	
	pool.close();

    if (verbose_output) {
        cout << "done.\nReading in line costs..." << flush;
    }

	ifstream poolcost(pool_cost_file_name.c_str());
	while(!poolcost.eof())
	{
		getline(poolcost,text);
		if(text!=""&&(text.c_str())[0]>=48&&(text.c_str())[0]<=57)
		{
			nr_lines=atoi(text.c_str());
			pos=text.find(";");
			text=text.substr(pos+1);
			pos=text.find(";");
			text=text.substr(pos+1);
			cost[nr_lines-1] = atof(text.c_str());
		}
	}
	poolcost.close();
    if (verbose_output) {
        cout << "done.\n" << flush;
    }
	
	
	ifstream od_file (od_file_name.c_str());

	int i_pos;
	int j_pos;

    if (verbose_output) {
        cout << "Reading in OD matrix..." << flush;
    }
	while (!od_file.eof())
	{
		getline(od_file,text);
		if(text!=""&&(text.c_str())[0]>=48&&(text.c_str())[0]<=57)
		{
		i_pos=atoi(text.c_str());
		pos=text.find(";");
		text=text.substr(pos+1);
		
		j_pos = atoi(text.c_str());
		pos=text.find(";");
		text=text.substr(pos+1);

		od_entry e(i_pos-1,j_pos-1,atof(text.c_str()));
		OD.push_back(e);
		}
    	}		

	od_file.close();
    if (verbose_output) {
        cout << "done.\n" << flush;
    }
	//Alle Daten fertig eingelesen!
	//Beginne jetzt, die Datei zu schreiben

    cout << "Finished reading input data" << endl;
    cout << "Begin computing line concept game" << endl;

    if (verbose_output) {
        cout << "Begin writing mosel input data" << endl;
    }
	ofstream out("line-planning/Moseldaten");


    out << "Linienzahl: " << nr_lines << endl;
    out << "Kantenzahl: " << nr_edges << endl;
    out << "ODsize: " << OD.size() << endl;
    out << "timelimit: " << timelimit << endl;
    out << "threadlimit: " << thread_limit << endl;
    out << "outputMessages: " << verbose_output << endl;
    out << "mipGap: " << mip_gap << endl;
    out << "writeLpFile: " << write_lp_file << endl;

    if (verbose_output) {
	    cout<<"Edge-path incidence matrix...";
    }
	out<<"H:[";
	for (int i=0; i<nr_lines; i++)
		{
		for (int j=0; j<nr_edges; j++)
		{
			out<<L[i][j];
			if (j!=nr_edges-1||i!=nr_edges-1)
				out<<" ";
		}
		if (i!=nr_lines-1) out<<endl;
		}
	out<<"]"<<endl;

    if (verbose_output) {
        cout << "done.\nCosts...";
    }
	out<<"cost:[";
	for (int i=0; i<nr_lines; i++)
		{
		out<<cost[i];
		if (i!=nr_lines-1) out<<" ";
		}
	out<<"]"<<endl;

    if (verbose_output) {
        cout << "done.\nMin values...";
    }
	
	out<<"Min:[";
	for (int i=0; i<nr_edges; i++)
		{
		out<<Min[i];
		if (i!=nr_edges-1) out<<" ";
		}
	out<<"]"<<endl;

    if (verbose_output) {
        cout << "done.\nMax values...";
    }
	
	out<<"Max:[";
	for (int i=0; i<nr_edges; i++)
		{
		out<<Max[i];
		if (i!=nr_edges-1) out<<" ";
		}
	out<<"]"<<endl;

    if (verbose_output) {
        cout << "done.\nOD-pair-path matrix...";
    }
	vector<bool> feasible(OD.size());

	out<<"K:[";
	for (unsigned int i=0; i<OD.size(); i++)
		{
		feasible[i] = false;
		for (int j=0; j<nr_lines; j++)
		{

			if ( line_knots[j].find((OD[i]).i)!=line_knots[j].end() && line_knots[j].find((OD[i]).j) != line_knots[j].end())
			{
				out<<"1";
				feasible[i] = true;
			}
			else
				out<<"0";
			if (j!=nr_lines-1||i!=OD.size()-1)
				out<<" ";
		}
		if (i!=OD.size()-1) out<<endl;
		}
	out<<"]"<<endl;

    if (verbose_output) {
        cout << "done.\nOD min values...";
    }

	//search for largest OD-Value
	int max_od = 0;
	for (unsigned int i=0; i<OD.size(); ++i)
		if (max_od < OD[i].val)
			max_od = OD[i].val;

	out<<"OD:[";
	for (unsigned int i=0; i<OD.size(); ++i)
	{
		//only consider the most important values
		if (/*OD[i].val >= 0.5 * max_od && */feasible[i])
			out<<(OD[i].val-1)/ passengers_per_vehicle + 1;
		else
			out<<"0";

		if (i!=OD.size()-1) out<<" ";
	}
	out<<"]\n";

    if (verbose_output) {
        cout << "done.\nLP formulation is ready to go!\n";
    }


	out.close();
	
	return 0;
}
	
