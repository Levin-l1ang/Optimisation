#include "shortest_paths.h"
#include <iostream>

void test1(){

	shortest_paths path;

	path.add_node(1);
	path.add_node(2);
	path.add_node(3);

	path.add_edge(1,1,2,3);
	path.add_edge(2,2,3,1);
	path.add_edge(3,1,3,3);

	cout<<path.get_distance(1,3)<<"\n";

	std::vector<unsigned int> nodes = path.get_path_nodes(1,3);

	for (std::vector<unsigned int>::iterator it = nodes.begin(); it != nodes.end(); ++it)
		std::cout<<*it<<" - ";
	std::cout<<"\n";

}

void test2(){

    shortest_paths sp;

    sp.graph_from_file("nodes.csv", "edges.csv");

    sp.compute_pairwise();

    for(unsigned int i=0; i < 84; i++){

        for(unsigned int j=0; j < 84; j++){

            vector<unsigned int> nodes = sp.get_path_edges(i,j);

            cerr << "loop 3: " << endl;

	        for (std::vector<unsigned int>::iterator 
	                it = nodes.begin(); 
	                it != nodes.end(); ++it){
		        
		        std::cerr <<*it<<" - ";

		    }

            std::cerr << endl;

		}
	}

}


int main(void)
{

    test2();

	return 0;

}

