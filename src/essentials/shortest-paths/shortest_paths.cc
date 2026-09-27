#include <stdexcept>
#include <sstream>
#include <limits>
#include <iostream>
#include <cmath>
#include <stdlib.h>
#include <boost/lexical_cast.hpp>
#include <sstream>

#include "shortest_paths.h"
#include "fheap.h"

using namespace std;

shortest_paths::shortest_paths() :
    sssp(sssp_automatic),
    apsp(apsp_automatic),
    integrity_check(true),
    negative_edge_exists(false),
    pairwise_computation_done(false),
    pairwise_tracking_done(false),
    verbose(true),
    directed(true){

}

void shortest_paths::add_node(unsigned int index){

    node to_add = { index };
    if(integrity_check){
        if(node_index_map.find(index) != node_index_map.end()){
            stringstream error;
            error << "node index " << index
                << " occured at least twice";
            throw invalid_argument(error.str());
        }
    }
    node_index_map[index] = nodes.size();
    nodes.push_back(to_add);
    sparse_adjacency_matrix.push_back(map<unsigned int, unsigned int>());
    sparse_transposed_adjacency_matrix.push_back(map<unsigned int, unsigned int>());
    pairwise_computation_done = false;
    pairwise_tracking_done = false;

}

void shortest_paths::add_edge(
        unsigned int index,
        unsigned int source,
        unsigned int target,
        double weight){

    edge to_add = { index, source, target, weight };

    if(integrity_check){
        if(edge_index_map.find(index) != edge_index_map.end()){
            stringstream error;
            error << "edge index " << index
                << " occured at least twice";
            throw invalid_argument(error.str());
        }
        if(node_index_map.find(source) == node_index_map.end()){
            stringstream error;
            error << "source node " << source << " does not exist";
            throw invalid_argument(error.str());
        }
        if(node_index_map.find(target) == node_index_map.end()){
            stringstream error;
            error << "target node " << target << " does not exist";
            throw invalid_argument(error.str());
        }
    }

    unsigned int mapped_source = node_index_map[source];
    unsigned int mapped_target = node_index_map[target];
    unsigned int mapped_index = edges.size();

    map<unsigned int, unsigned int>::iterator old_edge =
        sparse_adjacency_matrix[mapped_source].find(mapped_target);

    // It may be that we have multi edges. In that case, we use the
    // edge with the lowest weight.
    if(old_edge == sparse_adjacency_matrix[mapped_source].end() ||
            edges[old_edge->second].weight > weight){

        sparse_adjacency_matrix[mapped_source][mapped_target] = mapped_index;
        sparse_transposed_adjacency_matrix[mapped_target][mapped_source] = mapped_index;

        if(weight < 0){
            negative_edge_exists = true;
        }

        pairwise_computation_done = false;
        pairwise_tracking_done = false;

    }

    edge_index_map[index] = mapped_index;
    edges.push_back(to_add);

}


void shortest_paths::set_integrity_check(bool integrity_check){

    this->integrity_check = integrity_check;

}

void shortest_paths::graph_from_file(
        const char* filename_nodes,
        const char* filename_edges){

    ifstream nodes_stream(filename_nodes, ios::in),
         edges_stream(filename_edges, ios::in);

    unsigned int node_index,

        edge_index,
        source,
        target,

        line_in_file;

    float weight;

    string line_buffer;

    // let's do the nodes
    if(nodes_stream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename_nodes << "\" for reading";
        throw invalid_argument(error.str());
    }

    try{
        for(line_in_file=1; getline(nodes_stream, line_buffer);
                line_in_file++){

            // A single whitespace in the template allows any number
            // of whitespaces in the scanned file, including none,
            // so don't remove them!
            if(sscanf(line_buffer.c_str(), " %u ", &node_index) < 1){
                stringstream error;
                error << "malformed input: missing node index";
                throw invalid_argument(error.str());
            }

            add_node(node_index);
        }

    }

    catch(exception e){

        stringstream error;
        error << "line: " << line_in_file << ", file: \""
            << filename_nodes << "\", " << e.what();
        throw invalid_argument(error.str());

    }

    nodes_stream.close();

    if(nodes.size()==0){
        stringstream error;
        error << "file \"" << filename_nodes
            << "\" did not contain any nodes";
        throw invalid_argument(error.str());
    }

    // let's do the edges
    if(edges_stream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename_edges << "\" for reading";
        throw invalid_argument(error.str());
    }

    try{
        for(line_in_file=1; getline(edges_stream, line_buffer);
                line_in_file++){

            // A single whitespace in the template allows any number
            // of whitespaces in the scanned file, including none,
            // so don't remove them!
            if(sscanf(line_buffer.c_str(), " %u ; %u ; %u ; %f",
                        &edge_index, &source, &target, &weight) < 4){
                stringstream error;
                error << "malformed input: must be like "
                    << "\"index; source; target; weight\"";
                throw invalid_argument(error.str());
            }

            add_edge(edge_index, source, target, (double)weight);
        }

    }

    catch(exception e){

        stringstream error;
        error << "line: " << line_in_file << ", file: \""
            << filename_edges << "\", " << e.what();
        throw invalid_argument(error.str());

    }

    edges_stream.close();

}

void shortest_paths::graph_to_file(
        const char* filename_nodes,
        const char* filename_edges){

    ofstream nodes_stream(filename_nodes, ios::out),
         edges_stream(filename_edges, ios::out);

    if(nodes_stream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename_nodes << "\" for writing";
        throw invalid_argument(error.str());
    }

    nodes_stream << "# node_index" << endl;

    for(vector<node>::iterator itr = nodes.begin();
            itr != nodes.end(); itr++){

        nodes_stream << itr->index << endl;

    }

    nodes_stream.close();

    if(edges_stream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename_edges << "\" for writing";
        throw invalid_argument(error.str());
    }

    edges_stream << "# edge_index; from_node; to_node; weight" << endl;

    for(vector<edge>::iterator itr = edges.begin();
            itr != edges.end(); itr++){

        edges_stream << itr->index << "; " << itr->source << "; "
            << itr->target << "; " << itr->weight << endl;

    }

    edges_stream.close();
}

void shortest_paths::compute_path(
        unsigned int path_source,
        unsigned int path_target){

    path_struct tmp = {numeric_limits<double>::infinity(), numeric_limits<unsigned int>::max()};

    d.resize(nodes.size(), vector<path_struct>(nodes.size(), tmp));

    unsigned int mapped_source = node_index_map[path_source];

    if(sssp == sssp_automatic){

        // TODO make differentiation here
        sssp = dijkstra;

    }

    // This part is taken from Wikipedia, Dijkstra's Algorithm.
    if(sssp == dijkstra){

        if(integrity_check && negative_edge_exists){
            stringstream error;
            error << "dijkstra method cannot deal with "
                << "negative edges that exist in the graph.";
            throw invalid_argument(error.str());
        }

        d[mapped_source][mapped_source].distance = 0;

        FHeap heap(nodes.size());

        for(unsigned int i=0; i < nodes.size(); i++){

            heap.insert(i, d[mapped_source][i].distance);

        }

        double minimum = 0;
        unsigned int index = 0;

        for(unsigned int i = 0; i < nodes.size()
                && minimum != numeric_limits<double>::infinity(); i++){

            // Will overwrite minimum and index
            index = heap.deleteMin();
            // if (index == node_index_map[path_target]){
            //     if (d[mapped_source][index].distance < 0){
            //         cout << d[mapped_source][index].distance << endl;
            //       }
            //     break;
            // }
            minimum = d[mapped_source][index].distance;

            for(map<unsigned int, unsigned int>::iterator itr
                    = sparse_adjacency_matrix[index].begin();
                    itr != sparse_adjacency_matrix[index].end();
                    itr++){

                double alt = d[mapped_source][index].distance
                    + edges[itr->second].weight;

                if(alt < d[mapped_source][itr->first].distance){

                    d[mapped_source][itr->first].distance = alt;
                    d[mapped_source][itr->first].predecessor = index;
                    heap.decreaseKey(itr->first, alt);

                }

            }

            if(!directed){

                for(map<unsigned int, unsigned int>::iterator itr
                        = sparse_transposed_adjacency_matrix[index].begin();
                        itr != sparse_transposed_adjacency_matrix[index].end();
                        itr++){

                    double alt = d[mapped_source][index].distance
                        + edges[itr->second].weight;

                    if(alt < d[mapped_source][itr->first].distance){

                        d[mapped_source][itr->first].distance = alt;
                        d[mapped_source][itr->first].predecessor = index;
                        heap.decreaseKey(itr->first, alt);
                    }

                }

            }

        }

    }

    if(sssp == bellman_ford){

        stringstream error;
        error << "bellman_ford not implemented yet";
        throw invalid_argument(error.str());

        // TO BE CONTINUED

        for(map<unsigned int, unsigned int>::iterator itr =
                sparse_adjacency_matrix[path_source].begin();
                itr != sparse_adjacency_matrix[path_source].end();
                itr++){

            d[mapped_source][itr->first].distance = edges[itr->second].weight;
            d[mapped_source][itr->first].predecessor = path_source;

        }

    }

}

void shortest_paths::track_path(
        unsigned int path_source,
        unsigned int path_target){

    unsigned int mapped_source = node_index_map[path_source];
    unsigned int mapped_target = node_index_map[path_target];

    double length = 0.0;

    node_paths.resize(nodes.size(), vector<list<unsigned int> >());
    node_paths[mapped_source].resize(nodes.size(), list<unsigned int>());

    edge_paths.resize(nodes.size(), vector<list<unsigned int> >());
    edge_paths[mapped_source].resize(nodes.size(), list<unsigned int>());

    if(!directed){
        node_paths[mapped_target].resize(nodes.size(), list<unsigned int>());
        edge_paths[mapped_target].resize(nodes.size(), list<unsigned int>());
    }

    unsigned int predecessor = mapped_target;
    unsigned int old_predecessor = d[mapped_source][predecessor].predecessor;

    bool path_exists = false;

    while(old_predecessor != numeric_limits<unsigned int>::max()){

        path_exists = true;

        node_paths[mapped_source][mapped_target].push_front(predecessor);

        map<unsigned int, unsigned int>::iterator itr =
            sparse_adjacency_matrix[old_predecessor].find(predecessor);

        if(!directed && itr == sparse_adjacency_matrix[old_predecessor].end()){
            itr = sparse_adjacency_matrix[predecessor].find(old_predecessor);
        }

        edge_paths[mapped_source][mapped_target].push_front(itr->second);
        length += edges[itr->second].weight;

        predecessor = old_predecessor;
        old_predecessor = d[mapped_source][predecessor].predecessor;

    }

    if(path_exists){

        node_paths[mapped_source][mapped_target].push_front(predecessor);

    }

    // TODO introduce proper epsilon
    if(d[mapped_source][mapped_target].distance != numeric_limits<double>::infinity() &&
            std::abs(length - d[mapped_source][mapped_target].distance) > 0.01){
        stringstream error;
        error << "something is wrong: shortest distance " <<
               d[mapped_source][mapped_target].distance
               << " not matched by path length " << length;
        throw invalid_argument(error.str());
    }

}

void shortest_paths::compute_all_paths(){

	paths.resize(nodes.size(), vector<vector<list<unsigned int> > >());
	for(int ii = 0; ii<nodes.size(); ii++){

		paths[ii].resize(nodes.size(), vector<list<unsigned int> >());

		paths[ii] = get_all_paths_from(ii);

	}
}

vector<vector<list<unsigned int> > > shortest_paths::get_all_paths_from(
        unsigned int path_source){

	unsigned int mapped_source = node_index_map[path_source];

        vector<double> min;
	min.reserve(nodes.size());
	vector<vector<list<unsigned int> > > path;
	path.resize(nodes.size(), vector<list<unsigned int> >());
	vector<bool> visited;
	visited.reserve(nodes.size());

	for(unsigned int ii = 0; ii<nodes.size();ii++){
		visited[ii] = false;
		min[ii] = numeric_limits<double>::infinity();
	}

	min[mapped_source] = 0;
	unsigned int current = mapped_source;

	while(true){
		visited[current] = true;
		for(std::map<unsigned int, unsigned int>::iterator itr = sparse_adjacency_matrix[current].begin(); itr != sparse_adjacency_matrix[current].end(); itr++){
			if(edges[itr->second].weight > 0 && !visited[itr->first]){
				if(edges[itr->second].weight + min[current] < min[itr->first]){
					min[itr->first] = edges[itr->second].weight + min[current];
					path[itr->first].clear();
				}
				if(edges[itr->second].weight + min[current] == min[itr->first]){
					if(path[current].size() == 0){
						path[itr->first].resize(1,list<unsigned int>());
						path[itr->first].back().push_back(edges[itr->second].index);
					} else {
						path[itr->first].resize(path[current].size(),list<unsigned int>());
						for(unsigned int ii = 0;ii< path[current].size(); ii++){
							path[itr->first].push_back(path[current][ii]);
							path[itr->first].back().push_back(edges[itr->second].index);
						}
					}
				}
			}
		}
		for(map<unsigned int, unsigned int>::iterator itr = sparse_transposed_adjacency_matrix[current].begin(); itr != sparse_transposed_adjacency_matrix[current].end() && !directed; itr++){
			if(edges[itr->second].weight > 0 && !visited[itr->first]){
				if(edges[itr->second].weight + min[current] < min[itr->first]){
					min[itr->first] = edges[itr->second].weight + min[current];
					path[itr->first].clear();
				}
				if(edges[itr->second].weight + min[current] == min[itr->first]){
					if(path[current].size() == 0){
						path[itr->first].resize(1,list<unsigned int>());
						path[itr->first].back().push_back(edges[itr->second].index);
					} else {
						path[itr->first].resize(path[current].size(),list<unsigned int>());
						for(unsigned int ii = 0;ii< path[current].size(); ii++){
							path[itr->first].push_back(path[current][ii]);
							path[itr->first].back().push_back(edges[itr->second].index);
						}
					}
				}
			}
		}

		double least = numeric_limits<double>::infinity();
		int k = -1;
		for(unsigned int nodes_count= 0; nodes_count<nodes.size(); nodes_count++){
			if(!visited[nodes_count] && min[nodes_count]<least){
				k = nodes_count;
				least = min[nodes_count];
			}
		}
		if(k==-1)break;
		current = k;
	}
	return path;
}

vector<vector<unsigned int> > shortest_paths::get_all_paths_from_to(
	unsigned int path_source,
	unsigned int path_target){

	vector<vector<unsigned int> > all_paths_remapped;

	all_paths_remapped.reserve(paths[node_index_map[path_source]][node_index_map[path_target]].size());
	for(unsigned int ii = 0; ii<paths[node_index_map[path_source]][node_index_map[path_target]].size();ii++){
		all_paths_remapped.push_back(edge_indices(paths[path_source][path_target][ii]));
	}
	return all_paths_remapped;
}


vector<unsigned int> shortest_paths::edge_indices(const list<unsigned int>& mapped_indices){

    vector<unsigned int> retval;
    retval.reserve(mapped_indices.size());

    for(list<unsigned int>::const_iterator itr = mapped_indices.begin();
            itr != mapped_indices.end(); itr++){

        retval.push_back(edges[*itr].index);

    }

    return retval;

}

vector<unsigned int> shortest_paths::node_indices(const list<unsigned int>& mapped_indices){

    vector<unsigned int> retval;
    retval.reserve(mapped_indices.size());

    for(list<unsigned int>::const_iterator itr = mapped_indices.begin();
            itr != mapped_indices.end(); itr++){

        retval.push_back(nodes[*itr].index);

    }

    return retval;

}

void shortest_paths::compute_pairwise(int start, int end){ // default values of 0 and -1
    if (end == -1) end = nodes.size();
    if(pairwise_computation_done || nodes.size() == 0){
        return;
    }

    if(apsp == floyd_warshall){

        path_struct tmp = {numeric_limits<double>::infinity(),
            numeric_limits<unsigned int>::max()};

        d.resize(nodes.size(), vector<path_struct>(nodes.size(), tmp));

        for(unsigned int i=0; i<sparse_adjacency_matrix.size(); i++){

            for(map<unsigned int, unsigned int>::iterator itr2 =
                    sparse_adjacency_matrix[i].begin();
                    itr2 != sparse_adjacency_matrix[i].end();
                    itr2++){

                d[i][itr2->first].distance = edges[itr2->second].weight;
                d[i][itr2->first].predecessor = i;

                if(!directed){
                    d[itr2->first][i].distance = edges[itr2->second].weight;
                    d[itr2->first][i].predecessor = i;
                }

            }

            d[i][i].distance = 0;

        }

        for(unsigned int k=0; k<nodes.size(); k++){

            if(verbose){
                if(k % 50 == 0){
                    cerr << "node " << k << " of " << nodes.size() << endl;
                }
            }

            for(unsigned int i=0; i<nodes.size(); i++){

                for(unsigned int j=0; j<nodes.size(); j++){

                    double alt = d[i][k].distance + d[k][j].distance;

                    if(alt < d[i][j].distance){

                        d[i][j].distance = alt;
                        d[i][j].predecessor = d[k][j].predecessor;

                    }

                }

            }

        }
        for(unsigned int i=0; i<nodes.size(); i++){

            if(d[i][i].distance < 0){

                stringstream error;
                error << "graph has negative cycles" << endl;
                throw invalid_argument(error.str());

            }

        }

    }

    else {

        for(unsigned int i=start; i < end; i++){

            if(verbose){
                if(i % 50 == 0){
                    cerr << "node " << i - start << " of " << end - start  << endl;
                }
            }
            compute_path(i,i);

        }

    }

    pairwise_computation_done = true;

}

void shortest_paths::track_pairwise(){

    if(pairwise_tracking_done){
        return;
    }

    if(!pairwise_computation_done){

        compute_pairwise();

    }

    for(unsigned int i=0; i<nodes.size(); i++){

        unsigned int startpoint;

        if(directed){
            startpoint = 0;
        }
        else{
            startpoint = i+1;
        }

        for(unsigned int j=startpoint; j<nodes.size(); j++){

            track_path(nodes[i].index, nodes[j].index);
            if(!directed){
                node_paths[j][i] = node_paths[i][j];
                edge_paths[j][i] = edge_paths[i][j];
            }

        }

    }

    pairwise_tracking_done = true;

}

void shortest_paths::distance_to_file(
        const char* filename,
        unsigned int path_source,
        unsigned int path_target){

    if(integrity_check){
        if(node_index_map.find(path_source) == node_index_map.end()){
            stringstream error;
            error << "source node " << path_source << " does not exist";
            throw invalid_argument(error.str());
        }
        if(node_index_map.find(path_target) == node_index_map.end()){
            stringstream error;
            error << "target node " << path_target << " does not exist";
            throw invalid_argument(error.str());
        }
    }

    compute_path(path_source, path_target);

    ofstream filestream(filename, ios::out);

    if(filestream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename << "\" for writing";
        throw invalid_argument(error.str());
    }

    filestream << "# Automatically created by a shortest_paths object."
        << "# path_source; path_target; distance" << endl
        << path_source << "; " << path_target << "; " <<
        d[node_index_map[path_source]][node_index_map[path_target]].distance
        << endl;

    filestream.close();
}

void shortest_paths::pairwise_distances_to_file(const char* filename) {

    compute_pairwise();

    ofstream filestream(filename, ios::out);

    if(filestream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename << "\" for writing";
        throw invalid_argument(error.str());
    }

    filestream << "# Automatically created by a shortest_paths object."
        << "# path_source; path_target; distance" << endl;

    for(unsigned int i=0; i < nodes.size(); i++){

        unsigned int startpoint;

        if(directed){
            startpoint = 0;
        }
        else{
            startpoint = i+1;
        }

        for(unsigned int j=startpoint; j < nodes.size(); j++){

            filestream << nodes[i].index << "; "
                << nodes[j].index << "; " << d[i][j].distance << endl;

        }

    }

    filestream.close();
}

void shortest_paths::path_to_file(
        const char* filename,
        unsigned int path_source,
        unsigned int path_target) {

    if(integrity_check){
        if(node_index_map.find(path_source) == node_index_map.end()){
            stringstream error;
            error << "source node " << path_source << " does not exist";
            throw invalid_argument(error.str());
        }
        if(node_index_map.find(path_target) == node_index_map.end()){
            stringstream error;
            error << "target node " << path_target << " does not exist";
            throw invalid_argument(error.str());
        }
    }

    track_path(path_source, path_target);

    ofstream filestream(filename, ios::out);

    if(filestream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename << "\" for writing";
        throw invalid_argument(error.str());
    }

    filestream << "# Automatically created by a shortest_paths object." << endl;

    if(directed){
        filestream << "# edge_source; edge_target; edge_index" << endl;
    }
    else{
        filestream << "# edge_left_node; edge_right_node; edge_index" << endl;
    }


    list<unsigned int> edge_list = edge_paths[
        node_index_map[path_source]][node_index_map[path_target]];

    for(list<unsigned int>::iterator itr = edge_list.begin();
            itr != edge_list.end(); itr++){

        filestream << edges[*itr].source << "; " << edges[*itr].target << "; "
            << edges[*itr].index << endl;

    }

    filestream.close();

}

void shortest_paths::pairwise_paths_to_file(const char* filename) {

    track_pairwise();

    ofstream filestream(filename, ios::out);

    if(filestream.fail()){
        stringstream error;
        error << "unable to open file \""
            << filename << "\" for writing";
        throw invalid_argument(error.str());
    }

    filestream << "# Automatically created by a shortest_paths object." << endl;

    if(directed){
        filestream << "# path_source; path_target; edge_source; edge_target; edge_index" << endl;
    }
    else{
        filestream << "# path_source; path_target; edge_left_node; edge_right_node; edge_index" << endl;
    }

    for(unsigned int i=0; i < nodes.size(); i++){

        unsigned int startpoint;

        if(directed){
            startpoint = 0;
        }
        else{
            startpoint = i+1;
        }

        for(unsigned int j=startpoint; j < nodes.size(); j++){

            for(list<unsigned int>::iterator
                    itr = edge_paths[i][j].begin();
                    itr != edge_paths[i][j].end(); itr++){

                filestream << nodes[i].index << "; "
                    << nodes[j].index << "; " << edges[*itr].source << "; "
                    << edges[*itr].target << "; " << edges[*itr].index << endl;

            }

        }

    }

    filestream.close();

}

double shortest_paths::get_distance(
        unsigned int path_source,
        unsigned int path_target) {

    if(!pairwise_computation_done){
        compute_path(path_source, path_target);
    }

    return d[node_index_map[path_source]]
        [node_index_map[path_target]].distance;

}

vector<unsigned int> shortest_paths::get_path_nodes(
        unsigned int path_source,
        unsigned int path_target){

    track_path(path_source, path_target);

    return node_indices(node_paths[node_index_map[path_source]][node_index_map[path_target]]);

}

vector<unsigned int> shortest_paths::get_path_edges(
        unsigned int path_source,
        unsigned int path_target){

    track_path(path_source, path_target);

    return edge_indices(edge_paths[node_index_map[path_source]][node_index_map[path_target]]);

}

void shortest_paths::set_sssp_method_dijkstra(){

    sssp = dijkstra;

}

void shortest_paths::set_sssp_method_bellman_ford(){

    sssp = bellman_ford;

}

void shortest_paths::set_sssp_method_automatic(){

    sssp = sssp_automatic;

}

void shortest_paths::set_apsp_method_floyd_warshall(){

    apsp = floyd_warshall;

}

void shortest_paths::set_apsp_method_johnson(){

    apsp = johnson;

}

void shortest_paths::set_verbose(bool verbose){

    this->verbose = verbose;

}

void shortest_paths::set_directed(bool directed){

    this->directed = directed;

}

void shortest_paths::set_apsp_method_automatic(){

    apsp = apsp_automatic;

}
