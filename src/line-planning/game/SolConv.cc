/* SolConv.cc
*Hilfsprogramm
*
*Bereitet die von xpress ausgegeben Daten in die Standardform auf
*
*Ben�tigt
*-line-planning/xpresssol
*-basis/Load.giv
*-basis/Pool.giv
*
*Ausgabe
*-line-planning/Line-Concept.lin
*
*/

#include <iostream>
#include <fstream>
#include <cstdlib>
#include <cmath>
#include <vector>
#include <algorithm>
#include <string>

#include "../../essentials/config/config.h"

using namespace std;

int main(void) {
    cout << "Finished computing line concept game" << endl;
    cout << "Reread configuration to write output data" << endl;
    config::from_file("basis/Config.cnf", false);


    string log_level = config::get_string_value("console_log_level");
    transform(log_level.begin(), log_level.end(), log_level.begin(), ::toupper);
    bool verbose_output = log_level.compare("DEBUG") == 0;
    string pool_file = config::get_string_value("default_pool_file");
    string line_concept_file = config::get_string_value("default_lines_file");

    int temp;
    string text;
    int Linienzahl;
    vector <vector<int>> L;
    size_t pos;
    vector<int> freq;

    ifstream pool(pool_file.c_str());

    if (verbose_output) {
        cout << "Start reading pool file" << endl;
    }

    //Linien einlesen
    while (!pool.eof()) {
        getline(pool, text);
        if (text != "" && (text.c_str())[0] >= 48 && (text.c_str())[0] <= 57) {
            //Linienzahl=atoi(text.c_str());
            if (Linienzahl != atoi(text.c_str())) {
                Linienzahl = atoi(text.c_str());
                L.resize(L.size() + 1);
            }
            pos = text.find(";");
            text = text.substr(pos + 1);
            temp = atoi(text.c_str());
            pos = text.find(";");
            text = text.substr(pos + 1);
            int edge = atoi(text.c_str());
            (L.back()).push_back(edge - 1);
            //L[Linienzahl-1][temp-1]=atoi(text.c_str());
        }
    }

    pool.close();
    if (verbose_output) {
        cout << "Read pool" << endl;
        cout << "Start reading solution" << endl;
    }

	ifstream optfile("line-planning/xpresssol");

	    while(!optfile.eof())
	    {
		getline(optfile,text);
		if(text!="")
		{
		    temp=atoi(text.c_str());
		    pos=text.find(";");
		    text=text.substr(pos+1);
		    double val = atof(text.c_str());
		    //freq[temp]=floor(val + 0.5);
            freq.push_back(floor(val+0.5));
		}
	    }

	optfile.close();

    if (verbose_output) {
        cout << "Read found solution" << endl;
    }

	//Alle Daten fertig eingelesen!
	//Beginne jetzt, die Datei zu schreiben

    if (verbose_output) {
        cout << "Begin writing output file" << endl;
    }
	
	ofstream out(line_concept_file.c_str());
	
	out<<"# feasible line concept"<<endl;
	out<<"# optimal solution won by xpress"<<endl;

	out<<"# line-id; edge-order; edge-id; frequency"<<endl;

    int edge_id;
    for (int i=0; i<Linienzahl; i++) {
        edge_id=1;
        for (int j = 0; j < L[i].size(); j++) {
            out<<i+1<<";"<<edge_id<<";"<<L[i][j]+1<<";"<<freq[i]<<endl;
            edge_id++;
        }
    }

	out.close();
    cout<<"Finished writing output data"<<endl;
	return 0;
	}
	
