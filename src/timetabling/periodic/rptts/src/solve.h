//
//  main.cpp

#ifndef rptts_solve_h
#define rptts_solve_h

#include "io.h"
#include "matching.h"

struct vertex{ // vertex of line graph
    int identity; // LG.vertex[i].identity = i (so is obsolet actually)
    int line_id;
    set<int> adj_vertices;
};
typedef pair<int, int> edge;// edges of line graph

struct Line_Graph{
    vector<vertex> vertices; // vertices from 1 to |LG|. use ltvi to get the according vertex
    vector<edge> edges;
    map<pair<int,int>, int> VE_corr;
    map<int, int> ltvi; // line_id to  vertex_id;
};

struct Line_Cluster{
    int line_id;
    vector<vector<pair<int,int> > >  xvals; // second vals are weights
    long long intern_val = 0;
//    vector<int> nij;
};
struct Line_Clusters{
    vector<Line_Cluster> lc; // clusters are from 1 to |LC|. use ltvi to cluster according to line_id
    map<int, int> ltvi;
};

struct Cluster{
    vector<int> lines; // list of line ids
};

struct edge_it{
    int lcs_left;// vertex id of LCS, not line_id
    int lcs_right;
    int left; // id for local tij
    int right;
};

void dfs_bridge(int v, int prev, int prevind, Line_Graph LG,
                vector<bool> &used, vector<int> &fout, vector<int> &tin, int timer, vector<bool> &bridge);
void dfs_color (int v , Line_Graph LGC,  vector<int> &color, int comp);
Line_Graph define_lg();
vector<bool> find_bridges(Line_Graph LG);
pair<vector<int>, int> find_cc(Line_Graph LG);
int get_dur_to_start(int eid);

Line_Clusters init_LC(void);

int pivot(int k, int n, vector<vector<long double> > &A);
void row_swap(int k, int r, vector<vector<long double> > &A);
int order_determinant(vector<vector<long double> > A);

bool complexity(Cluster cl, Line_Graph LG);

int sum_it_up(int t, vector<pair<int, int> > x, vector<pair<int, int> > y);
pair<int, int> evaluate(Line_Clusters &LCS, int lid1, int lid2);
pair<int, int> evaluate2(Line_Clusters &LCS, int lid1, int lid2);
pair<int, int> evaluate3(Line_Clusters &LCS, int lid1, int lid2);

vp black_box_matching(vector<vector<pair<int, int> > > w);
void adjust_t(int t, int lid1, int lid2);

void merge(Line_Clusters &LCS, int lid1, int lid2, int t);
void merge_LG(Line_Graph &LG, int lid1, int lid2);
void matching_merge(Cluster &cl, Line_Clusters &LCS, Line_Graph &LG);


bool next_it(vp &iteration, vp &edges_to_iterate, Line_Clusters &LCS, vpvpvp &iv);
void setup_tij(vp &iteration, vp &edges_to_iterate, vv &tij, Line_Clusters &LCS, vpvpvp &iv);
pair<long long, vv> bf_normal_eval(Line_Clusters &LCS, Line_Graph &mygraph);
pair<long long, vv> bf_eval(vector<bool> &perm, Line_Clusters &LCS, Line_Graph &mg, vv &tij);

bool is_tree_bfs(int s, vector<bool> &used, vector<int> &from, vv &edges);
bool is_tree(vector<bool> &edg_id, Line_Graph &G);
void brute_force(Cluster &cl, Line_Clusters &LCS);

void rollout_T(vv &T);

void solve_instance(void);



// helper (to be deleted for release)
void write_xvals(Line_Clusters &LC);

#endif
