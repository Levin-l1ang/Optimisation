#include "shortest_paths.h"

#include <stdexcept>
#include <cstdlib>
#include <sstream>
#include <limits>
#include <iostream>
#include <errno.h>
#include <unistd.h>

using namespace std;

void helpmessage(){

    cerr << "shortest_paths"                                                                << endl
        << "This program by default calculates the all pair shortest paths and the single " << endl
        << "source shortest paths, if source and target node are given (see below)."        << endl
                                                                                            << endl
        << "File formats:"                                                                  << endl
        << "NODES_FILE: node_index"                                                         << endl
        << "EDGES_FILE: edge_index; from_node; to_node; weight"                             << endl
        << "DISTANCES_FILE: path_source; path_target; distance"                             << endl
        << "PATHS_FILE: path_source; path_target; edge_source; edge_target; edge_index"     << endl
                                                                                            << endl
        << "Options:"                                                                       << endl
        << "-h                    show this help message"                                   << endl
        << "-u                    regard the graph as undirected"                           << endl
        << "-n NODES_FILE         file that contains the node indices"                      << endl
        << "-e EDGES_FILE         file that contains edges with weight"                     << endl
        << "-d DISTANCES_FILE     file that will the shortest distances"                    << endl
        << "-p PATHS_FILE         file that will the shortest paths"                        << endl
        << "-g GRAPHVIZ_FILE      file that will contain the GraphViz representation"       << endl
                                                                                            << endl
        << "The next parameters, if set, will lead to only one path being calculated. "     << endl
        << "-s SOURCE_NODE_INDEX  index of the source node"                                 << endl
        << "-t TARGET_NODE_INDEX  index of the target node"                                 << endl
                                                                                            << endl
        << "When single source shortest paths is enabled, it will also alter the format of" << endl
        << " the distances and paths file canonically:"                                     << endl
        << "DISTANCES_FILE: distance"                                                       << endl
        << "PATHS_FILE: edge_source; edge_target; edge_index"                               << endl
                                                                                            << endl
        << "In case of successful computation of the shortest paths, the returned value "   << endl
        << "is 0, otherwise 1."                                                             << endl;

}

void usagemessage(){

    cerr << "Error: Type \"shortest_paths -h\" to get more information on correct "
        "usage." << endl;

}

int main(int argc, char** argv){

    string filename_nodes,
         filename_edges,
         filename_distances,
         filename_paths,
         filename_graphviz;

    bool filename_nodes_given = false,
         filename_edges_given = false,
         filename_distances_given = false,
         filename_paths_given = false,
         filename_graphviz_given = false,
         graph_is_undirected = false,
         source_node_given = false,
         target_node_given = false,

         show_usage_message = false;

    unsigned int source_node,
                 target_node;

    int option;

    opterr = 0;

    try{
    
        while((option = getopt(argc, argv, "hun:e:d:p:s:t:g:")) != -1){

            switch(option){
                case 'n':
                    filename_nodes = string(optarg);
                    if(filename_nodes_given){
                        stringstream error;
                        error << "Error: more than one file with nodes given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    filename_nodes_given=1;
                    break;

                case 'e':
                    filename_edges = string(optarg);
                    if(filename_edges_given){
                        stringstream error;
                        error << "Error: more than one file with edges given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    filename_edges_given=1;
                    break;

                case 'd':
                    filename_distances = string(optarg);
                    if(filename_distances_given){
                        stringstream error;
                        error << "Error: more than one file for distances given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    filename_distances_given=1;
                    break;

                case 'p':
                    filename_paths = string(optarg);
                    if(filename_paths_given){
                        stringstream error;
                        error << "Error: more than one file for paths given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    filename_paths_given=1;
                    break;

                case 'g':
                    filename_graphviz = string(optarg);
                    if(filename_graphviz_given){
                        stringstream error;
                        error << "Error: more than one file for GraphViz given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    filename_graphviz_given=1;
                    break;

                case 's':
                    source_node = (unsigned int) atoi(optarg);
                    if(source_node_given){
                        stringstream error;
                        error << "Error: more than one source node given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    source_node_given=1;
                    break;

                case 't':
                    target_node = (unsigned int) atoi(optarg);
                    if(target_node_given){
                        stringstream error;
                        error << "Error: more than one target node given."
                            << endl;
                        show_usage_message = true;
                        throw invalid_argument(error.str());
                    }
                    target_node_given=1;
                    break;

                case 'h':
                    helpmessage();
                    return 0;

                case 'u':
                    graph_is_undirected = true;
                    break;

                case '?':
                    stringstream error;
                    show_usage_message = true;

                    if(optopt == 'n' || optopt == 'e' || optopt == 'd' ||
                            optopt == 'p' || optopt == 's' || optopt == 't' ||
                            optopt == 'g'){
                        
                        error << "option ´-" << optopt 
                            << "´ requires an argument";
                    }

                    else if (isprint (optopt)){
                        error << "unknown option ‘-" << optopt << "’"; 
                    }
                    
                    else{
                        error << "unknown option character ‘" <<
                            optopt << "’";
                    }

                    throw invalid_argument(error.str());
            }

        }
        
        if (optind < argc){
            stringstream error;
            error << "argument \"" << argv[optind] << "\" invalid";
            throw invalid_argument(error.str());
        }

        if(!filename_nodes_given){
            stringstream error;
            error << "no nodes file given";
            throw invalid_argument(error.str());
        }

        if(!filename_edges_given){
            stringstream error;
            error << "no edges file given";
            throw invalid_argument(error.str());
        }

        if(!filename_distances_given && !filename_paths_given &&
                !filename_graphviz_given){

            stringstream error;
            error << "neither distances, nor paths nor GraphViz"
                << " file given; void operation";
            throw invalid_argument(error.str());

        }

        if(source_node_given && !target_node_given){
            stringstream error;
            error << "path source but no path target given";
            throw invalid_argument(error.str());
        }

        if(!source_node_given && target_node_given){
            stringstream error;
            error << "path target but no path source given";
            throw invalid_argument(error.str());
        }

        shortest_paths sp = shortest_paths();
        sp.graph_from_file(filename_nodes.c_str(), filename_edges.c_str());

        if(graph_is_undirected){
            sp.set_directed(false);
        }
        
        // At this point, source_node_given = target_node_given
        if(source_node_given){
            if(filename_distances_given){
                sp.distance_to_file(filename_distances.c_str(), source_node, target_node);
            }
            if(filename_paths_given){
                sp.path_to_file(filename_paths.c_str(), source_node, target_node);
            }
        }

        else{
            if(filename_distances_given){
                sp.pairwise_distances_to_file(filename_distances.c_str());
            }
            if(filename_paths_given){
                sp.pairwise_paths_to_file(filename_paths.c_str());
            }
        }

        if(filename_graphviz_given){
            stringstream error;
            error << "GraphViz not supported yet";
            throw invalid_argument(error.str());
        }
    }

    catch(exception& e){

        cerr << endl << "Error: " << e.what() << "." << endl 
            << "An error occured. Exiting." << endl;

        if(show_usage_message){
            usagemessage();
        }

        return 1;

    }
}
