#ifndef _SHORTEST_PATHS_H
#define _SHORTEST_PATHS_H

#include<fstream>
#include<list>
#include<set>
#include<vector>
#include<map>
#include<stdexcept>

using namespace std;

/** Basic graph implementation to efficiently compute shortest paths. The
 * design goals are:
 * 1. Speed.
 * 2. No learning curve.
 * 3. Manual control possible. */
class shortest_paths {

    protected:

        enum sssp_type {

            dijkstra,
            bellman_ford,
            sssp_automatic

        } sssp;

        enum apsp_type {

            johnson,
            floyd_warshall,
            apsp_automatic

        } apsp;

        struct node {

            unsigned int index;

        };

        struct edge {

            unsigned int index;
            unsigned int source;
            unsigned int target;
            double weight;

        };

        struct path_struct {

            double distance;
            unsigned int predecessor;

        };

        map<unsigned int, unsigned int> node_index_map;
        map<unsigned int, unsigned int> edge_index_map;

        vector<map<unsigned int, unsigned int> > sparse_adjacency_matrix;
        vector<map<unsigned int, unsigned int> > sparse_transposed_adjacency_matrix;

        vector<node> nodes;
        vector<edge> edges;

        bool integrity_check;
        bool negative_edge_exists;
        bool pairwise_computation_done;
        bool pairwise_tracking_done;
        bool verbose;
        bool directed;

        vector<vector<path_struct> > d;
        vector<vector<list<unsigned int> > > node_paths;
        vector<vector<list<unsigned int> > > edge_paths;

        vector<vector<vector<list<unsigned int> > > > paths;

        // TODO still some work remaining
        void compute_path(
                unsigned int path_source,
                unsigned int path_target);

        // TODO still some work remaining
        void track_path(
                unsigned int path_source,
                unsigned int path_target);

        vector<unsigned int> edge_indices(const list<unsigned int>& mapped_indices);
        vector<unsigned int> node_indices(const list<unsigned int>& mapped_indices);

    public:

        /** Default Constructor. */
        shortest_paths();

        /** Adds a node to the graph. Note: you cannot remove and/or edit
         * nodes; all prior computations will be invalidated.
         * @param index A unique number that identifies the node.
         * */
        void add_node(unsigned int index);

        /** Adds an edge to the graph. Note: you cannot remove and/or edit
         * edges; all prior computations will be invalidated; multi edges will
         * be reduced to the edge with the lowest cost.
         * @param index A unique number that identifies the edge.
         * @param source The source node identifier.
         * @param target The target node identifier.
         * @param weight The weight to be minimized by the shortest paths.
         */
        void add_edge(
                unsigned int index,
                unsigned int source,
                unsigned int target,
                double weight);

        /** Activates/Deactivates the integrity check.
         * @param integrity_check Is true by default, can be set to false to
         * improve performance and will skip all consistency related checks in
         * add_node/add_edge. WARNING: if there are errors in the data, and
         * the parameter is false, then results may be corrupted arbitrarily.
         */
        void set_integrity_check(bool integrity_check);

        /** Activates/Deactivates the status output for pairwise computation.
         * @param verbose Is true by default, can be set to false to
         * let compute_pairwise be totally silent.
         */
        void set_verbose(bool verbose);

        /** Sets whether or not the graph should be regarded as directed.
         * @param directed Is true by default, can be set to false to
         * let the algorithms work as if the graph had been undirected.
         */
        void set_directed(bool directed);

        /** Reads a graph from a node and an edge file.
         * @param filename_nodes The filename of the file that contains the
         * nodes; one index per line.
         * @param filename_edges The filename of the file that contains the
         * edfges; one edge per line, format: index; source; target; weight.
         */
        void graph_from_file(
                const char* filename_nodes,
                const char* filename_edges);

        /** Exports the graph's nodes and edges to a file.
         * @param filename_nodes See {@link #graph_from_file}.
         * @param filename_edges See {@link #graph_from_file}.
         */
        void graph_to_file(
                const char* filename_nodes,
                const char* filename_edges);

        /** Exports a distance to a file: single source shortest paths;
         * File will only contain some comment lines and a single distance.
         * @param filename The filename of the file for the distances.
         * @param path_source The node index of the source node.
         * @param path_target The node index of the target node.
         */
        void distance_to_file(
                const char* filename,
                unsigned int path_source,
                unsigned int path_target);

        /** Exports the distances to files, all pair shortest paths; Each line
         * will contain an entry from the distance matrix in the following
         * format: path_source; path_target; distance.
         * @param filename Filename of the file for the distances.
         */
        void pairwise_distances_to_file(const char* filename);

        /** Exports a path to a file: single source shortest paths; each line
         * will contain "edge_source; edge_target; edge_index" for each edge
         * in the shortest path.
         * @param filename Filename of the file for the path.
         * @param path_source Source node of the shortest path.
         * @param path_target Target node of the shortest path.
         * */
        void path_to_file(
                const char* filename,
                unsigned int path_source,
                unsigned int path_target);

        /** Exports paths to a file: all pair shortest paths; each line will
         * contain "path_source; path_target; edge_source; edge_target;
         * edge_index" for all pairwise source and target nodes for each edge
         * in the shortest path.
         * @param filename Filename of the file for the shortest paths.
         */
        void pairwise_paths_to_file(const char* filename);

        /** Returns the length of a shortest path between two nodes.
         * @param path_source Source node of the shortest path.
         * @param path_target Target node of the shortest path.
         */
        double get_distance(
                unsigned int path_source,
                unsigned int path_target);

        /** Returns the vector of node indices of a shortest path.
         * @param path_source The source node of the path.
         * @param path_target The target node of the path.
         */
        vector<unsigned int> get_path_nodes(
                unsigned int path_source,
                unsigned int path_target);

        /** Returns the vector of edge indices of a shortest path.
         * @param path_source The source node of the path.
         * @param path_target The target node of the path.
         */
        vector<unsigned int> get_path_edges(
                unsigned int path_source,
                unsigned int path_target);

	/* Computes all shortest paths */
        void compute_all_paths();
	/** Returns for all node-combinations a vector consisting of all shortest paths **/
	vector<vector<vector<list<unsigned int> > > > get_all_paths();

	/** Returns for all node-combinations from path_source in a vector consisting of all shortest paths
	* @param path_source The source node of the path.
 	*/
        vector<vector<list<unsigned int> > > get_all_paths_from(
		unsigned int path_source);

	vector<vector<unsigned int> > get_all_paths_from_to(
		unsigned int path_source,
		unsigned int path_target);


        /** Sets Dijkstra method for the single source shortest paths. */
        void set_sssp_method_dijkstra();
        /** Sets Bellman Ford method for the single source shortest paths. */
        void set_sssp_method_bellman_ford();
        /** Depending on the density of the graph, use the "best" method. */
        void set_sssp_method_automatic();

        /** Sets Floyd Warshall method for the all pairs shortest paths. */
        void set_apsp_method_floyd_warshall();
        /** Sets Johnson method for the all pairs shortest paths. */
        void set_apsp_method_johnson();
        /** Depending on the density of the graph, use the "best" method. */
        void set_apsp_method_automatic();

        /** TODO Precomputes all shortest paths for quick access. */
        void compute_pairwise(int start = 0, int end = -1);
        /** TODO Tracks all shortest paths for quick access. */
        void track_pairwise();
};
#endif
