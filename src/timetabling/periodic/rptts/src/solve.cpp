//
//  solve.cpp
//  rptts

#include "solve.h"




void dfs_bridge(int v, int prev, int prevind, Line_Graph LG,
                vector<bool> &used, vector<int> &fout, vector<int> &tin, int timer, vector<bool> &bridge){

    used[v] = 1;
    fout[v] = tin[v] = timer++;
    for (int i: LG.vertices[v].adj_vertices){
        int next = i;
        int ind = LG.VE_corr[make_pair(v,next)];
        if (next == prev && ind == prevind){
            continue;
        }
        if (!used[next]){
            dfs_bridge(next, v, ind, LG, used, fout, tin, timer, bridge);
            fout[v] = min(fout[v], fout[next]);
            if (tin[v] < fout[next]){
                bridge[ind] = 1;
            }
        }
        else{
            fout[v] = min(fout[v],tin[next]);
        }
    }
}
void dfs_color (int v , Line_Graph LGC,  vector<int> &color, int comp = 1 ) {
    color[v] = comp;
    for (int i: LGC.vertices[v].adj_vertices) {
        int next = i;
        if (!color[next]) {
            dfs_color(next, LGC, color, comp);
        }
    }
}
Line_Graph define_lg(){
    Line_Graph LG;
    for (auto it: Lines){
        Line l = it.second;
        vertex v;
        v.identity = (int) LG.vertices.size();
        v.line_id = l.identity;
        LG.vertices.push_back(v);
        LG.ltvi[v.line_id] = v.identity;
    }
    for (auto it: Ch_Acts){
        Ch_Act a = it.second;
        int left = LG.ltvi[Events[a.head].line_id];
        int right = LG.ltvi[Events[a.tail].line_id];
        if (LG.VE_corr.find(make_pair(left,right)) == LG.VE_corr.end()){
            LG.edges.push_back(make_pair(left, right));
            LG.VE_corr[make_pair(left, right)] = (int) LG.edges.size() - 1;
            LG.VE_corr[make_pair(right, left)] = (int) LG.edges.size() - 1;
            LG.vertices[left].adj_vertices.insert(right);
            LG.vertices[right].adj_vertices.insert(left);
        }
    }
    return LG;
}
vector<bool> find_bridges(Line_Graph LG){
    int n = (int) LG.vertices.size();
    int m = (int) LG.edges.size();
    vector<bool> used(n), bridges(m);
    vector<int> fout(n), tin(n);
    dfs_bridge(0, -1, -1, LG, used, fout, tin, 0, bridges);
    return bridges;
}
pair<vector<int>, int> find_cc(Line_Graph LG){
    int n = (int) LG.vertices.size();
    vector<int> color(n);
    int cnt = 0;
    for (int i = 0; i < n ; i++) {
        if (!color[i]) {
            cnt++;
            dfs_color(i, LG, color, cnt);
        }
    }
    return make_pair(color,cnt);
}

int get_dur_to_start(int eid){ // get pi_i^j - pi_0^j
    int value = 0;
    while (Events[eid].line_path_pred != -1){
        value += Activities[EA_corr[make_pair(Events[eid].line_path_pred, eid)]].lower_bound;
        eid = Events[eid].line_path_pred;
    }
    return value;
}

Line_Clusters init_LC(void){
    Line_Clusters LCS;
    for (auto it: Lines){
        int n = (int) Lines.size();
        Line_Cluster LC;
        LC.line_id = it.second.identity;
        LC.xvals.resize(n);
        LCS.lc.push_back(LC);
        LCS.ltvi[LC.line_id] = (int) LCS.lc.size() - 1;
    }
    for (auto it: Ch_Acts){
        Ch_Act a = it.second;
        int x = mod(get_dur_to_start(a.head) - get_dur_to_start(a.tail) - a.lower_bound);
        if (a.passengers > 0){
            LCS.lc[LCS.ltvi[Events[a.tail].line_id]].xvals[LCS.ltvi[Events[a.head].line_id]].push_back(make_pair(x, a.passengers));
        }
    }
    return LCS;
}

int pivot(int k, int n, vector<vector<long double> > &A){
    int ret = k+1;
    for (int i = k+2; i < n; i++){
        if (fabs(A[i][k]) > fabs(A[ret][k])){
            ret = i;
        }
    }
    return ret;
}
void row_swap(int k, int r, vector<vector<long double> > &A){
    int n = (int) A.size();
    for (int i = 0; i < n; i++){
        swap(A[k][i], A[r][i]);
    }
}
int order_determinant(vector<vector<long double> > A){
    // returns order of determinant of A

    int n = (int) A.size();
    vector<vector<long double> > L(n), U, P(n);
    for (int i = 0; i < n; i++){
        L[i].resize(n);
        P[i].resize(n);
        P[i][i] = 1;
    }
    U = L;
    for (int k = 0; k < n-1; k++){
        int r = pivot(k, n, A);
        if ( A[r][k] == 0){
            cout << "Fatal error" << endl;
            return 0;
        }
        else{
            row_swap(k, r, L);
            row_swap(k, r, A);

            for (int i = k+1; i < n; i++){
                L[i][k] = A[i][k]/A[k][k];
                for (int j = k+1; j < n; j++){
                    A[i][j] = A[i][j] - L[i][k] * A[k][j];
                }
            }
        }
    }
    for (int i = 0; i < n; i++){
        L[i][i]++;
        for (int j = i; j < n; j++){
            U[i][j] = A[i][j];
        }
    }

    // calculate order
    long double det = 0;
    for (int i = 0; i < n; i++){
        det += log10(fabs(L[i][i])) + log10(fabs(U[i][i]));
    }

    if (verbose) cout << "there are approx 10^" << floor(det)+1 << " spanning trees in the line graph" << endl;

    return (int) floor(det) + 1;
}
bool complexity(Cluster cl, Line_Graph LG){

    if (onlymerge) return false;

    if (tree_off){
        if (cl.lines.size() < 4) return true;
        else return false;
    }

    if (cl.lines.size() == 1) return true;


    // calculate number of trees in graph
    int n = (int) cl.lines.size();
    vector<vector<long double> > A(n);
    for (int i = 0; i < n; i++){
        A[i].resize(n);
    }
    for (int i = 0; i < (int) cl.lines.size(); i++){
        A[i][i] = LG.vertices[LG.ltvi[cl.lines[i]]].adj_vertices.size();
    }
    for (int i = 0; i < cl.lines.size(); i++){
        for (int j = i+1; j < cl.lines.size(); j++){
            set<int> v = LG.vertices[LG.ltvi[cl.lines[i]]].adj_vertices;
            if (v.find(LG.ltvi[cl.lines[j]]) != v.end() ){
                A[i][j] = -1;
                A[j][i] = -1;
            }
        }
    }

    A.resize(n-1);
    for (int i = 0; i < n-1; i++){
        A[i].resize(n-1);
    }


    if (order_determinant(A) <= compl_val){
        return true;
    }
    return false;
}

int sum_it_up(int t, vector<pair<int, int> > x, vector<pair<int, int> > y){
    int ret = 0;

    for (pair<int, int> xw: x){
        ret += mod(xw.first + t) * xw.second;
    }
    for (pair<int, int> yw: y){
        ret += mod(yw.first - t) * yw.second;
    }
    return ret;
}

pair<int, int> evaluate(Line_Clusters &LCS, int lid1, int lid2){
    // which weights weigh more?
    int id1 = LCS.ltvi[lid1];
    int id2 = LCS.ltvi[lid2];
    Line_Cluster lc1 = LCS.lc[id1];
    Line_Cluster lc2 = LCS.lc[id2];

    int sum1 = 0, sum2 = 0;
    for (auto w: lc1.xvals[id2]){
        sum1 += w.second;
    }
    for (auto w: lc2.xvals[id1]){
        sum2 += w.second;
    }
    vector<int> t_poss;
    if (sum1 >= sum2){
        for (auto x: lc1.xvals[id2]){
            t_poss.push_back(mod(-x.first));
        }
    }
    else{
        for (auto x: lc2.xvals[id1]){
            t_poss.push_back(x.first);
        }
    }

    pair<int, int> ret = make_pair(INF,0);

    for (int t: t_poss){
        int val = sum_it_up(t, lc1.xvals[id2], lc2.xvals[id1]);
        if (val < ret.first){
            ret = make_pair(val, t);
        }
    }
    return ret;
}

pair<int, int> evaluate2(Line_Clusters &LCS, int lid1, int lid2){
    // which weights weigh more?
    int id1 = LCS.ltvi[lid1];
    int id2 = LCS.ltvi[lid2];
    Line_Cluster lc1 = LCS.lc[id1];
    Line_Cluster lc2 = LCS.lc[id2];
    pair<int, int> ret = make_pair(INF,0);

    // get minimum t
    int bestval = INF, worstval = 0;
    for (int i = 0; i < time_period; i++){
        int val = sum_it_up(i, lc1.xvals[id2], lc2.xvals[id1]);
        worstval = max(worstval, val);
        if (val < bestval){
            bestval = val;
            ret.second = i;
        }
    }

    ret.first = -(worstval - bestval);
    return ret;
}
pair<int, int> evaluate3(Line_Clusters &LCS, int lid1, int lid2){
    // which weights weigh more?
    int id1 = LCS.ltvi[lid1];
    int id2 = LCS.ltvi[lid2];
    Line_Cluster lc1 = LCS.lc[id1];
    Line_Cluster lc2 = LCS.lc[id2];
    pair<int, int> ret = make_pair(INF,0);

    // get minimum t
    long double var = 0;
    int bestval = INF;
    for (int i = 0; i < time_period; i++){
        int val = sum_it_up(i, lc1.xvals[id2], lc2.xvals[id1]);
        var += (double) val/time_period;
        if (val < bestval){
            bestval = val;
            ret.second = i;
        }
    }

    ret.first = -(round(var) - bestval);
    return ret;
}

vector<pair<int, int> > black_box_matching(vector<vector<pair<int, int> > > w){
    // only need first entries
    int n = (int) w.size();
    vector<pair<int, int>> matches;

    vector<vector<int> > match_w(n);
    for (int i = 0; i < n; i++){
        match_w[i].resize(n);
        for (int j = 0; j < n; j++){
            match_w[i][j] = w[i][j].first;
        }
    }

    matches = chinese_matching(match_w);
    return matches;
}

void adjust_t(int t, int lid1, int lid2){
    int i = Line_id_to_it[lid1], j = Line_id_to_it[lid2];
    T_Matrix[i][j] = t;
    T_Matrix[j][i] = mod(-t);
    if (verbose) cout << "inserting i=" << i << " j=" << j << " tij=" << t << endl;
}

void merge(Line_Clusters &LCS, int lid1, int lid2, int t){
    // merges to line_cluster with line_id = lid1, line_cluster lid2 will be deleted from LCS.
    int n = (int) LCS.lc.size();

    int id1 = LCS.ltvi[lid1], id2 = LCS.ltvi[lid2];
    Line_Cluster lc1 = LCS.lc[id1];
    Line_Cluster lc2 = LCS.lc[id2];

    for (pair<int, int> x: lc1.xvals[id2]){
        lc1.intern_val += mod(x.first + t) * x.second;
    }
    for (pair<int, int> x: lc2.xvals[id1]){
        lc1.intern_val += mod(x.first - t) * x.second;
    }
    lc1.intern_val += lc2.intern_val;

    // transform all changes from id2 to k into changes from id1 to k
    // i.e. they should depend on t_{id1,k}, not on t_{id2,k}
    // x_{id2,k,l} + t_{id2,k} = (x_{id2,k,l} - t_{id1,id2}) + t_{id1,k}

    // and vice versa (transform all changes from k to id2 into changes from k to id1)
    // t_{k,id1} +  t_{id1,id2} = t_{k,id2}
    // x_{k,id2,l} + t_{k,id2} = (x_{id2,k,l} + t_{id1,id2}) + t_{k,id1}

    for (int k = 0; k < n; k++){
        if (k == id1 || k == id2) continue;
        for (pair<int, int> x: lc2.xvals[k]){
            lc1.xvals[k].push_back(make_pair(mod(x.first - t),x.second));
        }
        for (pair<int, int> x: LCS.lc[k].xvals[id2]){
            LCS.lc[k].xvals[id1].push_back(make_pair(mod(x.first + t), x.second));
        }
    }

    // delete entries from lid1 to lid2 and from lid1 to lid1
    lc1.xvals[id1].resize(0);

    // update first cluster
    LCS.lc[id1] = lc1;

    // erase second cluster from everywhere
    LCS.lc.erase(LCS.lc.begin() + id2);
    for (int i = 0; i < LCS.lc.size(); i++){
        LCS.lc[i].xvals.erase(LCS.lc[i].xvals.begin() + id2);
//        LCS.lc[i].nij.erase(LCS.lc[i].nij.begin() + id2);
    }

    // update ltvi
    LCS.ltvi[lid2] = id1;
    for (auto l: LCS.ltvi){
        if (l.second >= id2){
            LCS.ltvi[l.first]--;
        }
    }

    // insert new value to T_Matrix
    adjust_t(t, lid1, lid2);

}

void merge_LG(Line_Graph &LG, int lid1, int lid2){

    // merge two vertices in line graph to one. (the one corr to lid1)

    int id1 = LG.ltvi[lid1], id2 = LG.ltvi[lid2];
    for (int v: LG.vertices[id2].adj_vertices){
        LG.vertices[id1].adj_vertices.insert(v);
    }
    for (int i = 0; i < LG.edges.size(); i++){
        edge e = LG.edges[i];
        if (e.first == id2) {
            LG.edges[i].first = id1;
        }
        if (e.second == id2) {
            LG.edges[i].first = id1;
        }
    }
    for (int i = (int) LG.edges.size()-1; i >= 0; i--){
        if (LG.edges[i].first == id1 && LG.edges[i].second == id1){
            LG.edges.erase(LG.edges.begin() + i);
        }
    }
    LG.vertices.erase(LG.vertices.begin() + id2);

    for (auto v: LG.vertices){
        auto it = v.adj_vertices.find(id2);
        if (it != v.adj_vertices.end()){
            v.adj_vertices.erase(it);
        }
    }

    // update ltvi
    LG.ltvi[lid2] = id1;
    for (auto l: LG.ltvi){
        if (l.second >= id2){
            LG.ltvi[l.first]--;
        }
    }


}

void matching_merge(Cluster &cl, Line_Clusters &LCS, Line_Graph &LG){
    // performs one matching-merge step for the pool of lines in cl

    int n = (int) cl.lines.size();
    vector<vector<pair<int, int> > > w(n);
    for (int i = 0; i < n; i++) w[i].resize(n);
    // for all elements get values
    for (int i = 0; i < n; i++){
        for (int j = i+1; j < n; j++){
            int lid1 = cl.lines[i], lid2 = cl.lines[j];
            // or choose different strategy here
            if (strategy == 1) w[i][j] = evaluate(LCS, lid1, lid2);
            else if (strategy == 2) w[i][j] = evaluate2(LCS, lid1, lid2);
            else if (strategy == 3) w[i][j] = evaluate3(LCS, lid1, lid2);
            else{
                cout << "error! specify correct strategy!" << endl;
                return;
            }
            if (ververbose) cout << LCS.ltvi[lid1] << " " << LCS.ltvi[lid2] << " " << w[i][j].first << " " << w[i][j].second << endl;
            w[j][i] = make_pair(w[i][j].first, mod(-w[i][j].second));
        }
    }
    // match best values
    vector<pair<int, int> > m = black_box_matching(w);

    vector<pair<int, int> > del_pairs;
    for (int i = 0; i < m.size(); i++){
        del_pairs.push_back(make_pair(cl.lines[m[i].first], cl.lines[m[i].second]));
    }
    // merge best values
    for (int i = 0; i < (int) del_pairs.size(); i++){
        int lid1 = del_pairs[i].first, lid2 = del_pairs[i].second;
        merge(LCS, lid1, lid2, w[m[i].first][m[i].second].second);
        //  erase line from cluster
        cl.lines.erase(find(cl.lines.begin(), cl.lines.end(), lid2));
        merge_LG(LG, lid1, lid2);
    }

}


bool next_it(vp &iteration, vp &edges_to_iterate, Line_Clusters &LCS, vpvpvp &iter_vals){


    // create next iteration

    int n = (int) edges_to_iterate.size();

    if (iteration[0].first == iter_vals[0].first.size()){
        iteration[0].second++;
    }
    else iteration[0].first++;

    int i = 0;
    while (iteration[i].first == iter_vals[i].first.size() && iteration[i].second == iter_vals[i].second.size()){
        iteration[i] = make_pair(0,0);
        i++;
        if (i == n) return false;
        if (iteration[i].first == iter_vals[i].first.size()){
            iteration[i].second++;
        }
        else iteration[i].first++;
    }
    return true;
}
struct speedy_it{
    set<int> adj_vals;
};

void setup_tij(vp &iteration, vp &edges_to_iterate, vv &tij, Line_Clusters &LCS, vpvpvp &iter_vals, vector<speedy_it> &spit){    // set up tij

    int n = (int) edges_to_iterate.size();

    tij.resize(0);
    tij.resize(n+1);
    for (int i = 0; i < n+1; i++) tij[i].resize(n+1, 0);

    for (int i = 0; i < n; i++){
        int f = edges_to_iterate[i].first, t = edges_to_iterate[i].second;
        if (iteration[i].first == iter_vals[i].first.size()){
            tij[f][t] = mod(iter_vals[i].second[iteration[i].second].first);
        }
        else{
            tij[f][t] = mod(-iter_vals[i].first[iteration[i].first].first);
        }
        tij[t][f] = mod(-tij[f][t]);
    }



    // make fast rollout
    set<int> vis;
    vector<bool> used(n+1);
    queue<int> q;
    vis.insert(0);
    q.push(0);
    used[0] = true;

    while (!q.empty()){
        int i = q.front(); q.pop();
        vis.insert(i);
        for (int j: spit[i].adj_vals){
            if (used[j]) continue;
            for (int k: vis){
                tij[j][k] = mod(tij[j][i] + tij[i][k]);
                tij[k][j] = mod(-tij[j][k]);
            }
            q.push(j);
            vis.insert(j);
            used[j] = true;
        }
    }


    return;
}

pair<long long, vv> bf_normal_eval(Line_Clusters &LCS, Line_Graph &mg){


    long long mini = LINF, summe;
    vv min_tij;
    int n = (int) mg.vertices.size();
    bool br = true;

    // if (verbose)
    if (verbose) cout << "Brute force cluster with " << n << " vertices (may take time..)" << endl;

    vector<int> T(n);
    while (br){
        vv tij(n);
        for (int i = 0; i < n; i++) tij[i].resize(n);
        // setup tij
        for (int i = 0; i < n; i++){
            tij[0][i] = T[i];
            tij[i][0] = mod(-T[i]);
        }
        for (int i = 0; i < n; i++){
            for (int j = i+1; j < n; j++){
                tij[i][j] = mod(tij[i][0] + tij[0][j]);
                tij[j][i] = mod(-tij[i][j]);
            }
        }
        summe = 0;
        for (edge e: mg.edges){
            int id1 = LCS.ltvi[mg.vertices[e.first].line_id], id2 = LCS.ltvi[mg.vertices[e.second].line_id];
            summe += sum_it_up(tij[e.first][e.second], LCS.lc[id1].xvals[id2], LCS.lc[id2].xvals[id1]);
        }
        if (summe < mini){
            mini = summe;
            min_tij = tij;
        }

        T[1]++;
        int index = 1;
        while (T[index] == time_period){
            T[index] = 0;
            index++;
            if (index == n ){
                br = false;
                break;
            }
            T[index]++;
        }

    }

    return make_pair(mini, min_tij);

}

pair<long long, vv> bf_eval(vector<bool> &perm, Line_Clusters &LCS, Line_Graph &mg, vv &tij){
    // given permutation that is a tree iterate among all tij possibilities

    vp etoi; // edges to iterate (line_ids)
    vpvpvp iter_vals;


    // init which direction of edges should be iterated
    for (int i = 0; i < perm.size(); i++){
        if (perm[i]){
            int left = mg.vertices[mg.edges[i].first].line_id;
            int right = mg.vertices[mg.edges[i].second].line_id;
            etoi.push_back(make_pair(mg.edges[i].first, mg.edges[i].second));
            iter_vals.push_back(make_pair(LCS.lc[LCS.ltvi[left]].xvals[LCS.ltvi[right]], LCS.lc[LCS.ltvi[right]].xvals[LCS.ltvi[left]]));
        }
    }

    // set up structure for rollout tij
    vector<speedy_it> spit(etoi.size()+1);
    for (pair<int, int> e: etoi){
        spit[e.first].adj_vals.insert(e.second);
        spit[e.second].adj_vals.insert(e.first);
    }


    // iterate
    long long minsum = LINF;
    vv min_tij;
    vp iteration(etoi.size());

    do {
        setup_tij(iteration, etoi, tij, LCS, iter_vals, spit);
        long long summe = 0;
        for (edge e: mg.edges){
            int id1 = LCS.ltvi[mg.vertices[e.first].line_id], id2 = LCS.ltvi[mg.vertices[e.second].line_id];
            summe += sum_it_up(tij[e.first][e.second], LCS.lc[id1].xvals[id2], LCS.lc[id2].xvals[id1]);
        }
        if (summe < minsum){
            min_tij = tij;
            minsum = summe;
        }
    }
    while (next_it(iteration, etoi, LCS, iter_vals));


    return make_pair(minsum, min_tij);
}

bool is_tree_bfs(int s, vector<bool> &used, vector<int> &from, vv &edges) {
    queue<int> q;
    used[s] = 1;
    from[s] = s;
    q.push(s);
    while (!q.empty()) {
        int cur = q.front();
        q.pop();
        for (int i = 0; i < edges[cur].size(); i++) {
            int next = edges[cur][i];
            if (!used[next]) {
                used[next] = used[cur] + 1;
                from[next] = cur;
                q.push(next);
            }
            else if (used[next] && from[cur] != next) {
                return false;
            }
        }
    }
    return true;
}

bool is_tree(vector<bool> &edg_id, Line_Graph &G){

    int n = (int) G.vertices.size();
    vector<bool> used(n);
    vector<int> from(n);
    vv edges(n);
    for (int i = 0; i < edg_id.size(); i++){
        if (edg_id[i]){
            edges[G.edges[i].first].push_back(G.edges[i].second);
            edges[G.edges[i].second].push_back(G.edges[i].first);
        }
    }
    if (!is_tree_bfs(0, used, from, edges)) return false;
    for (int i = 0; i < n; i++){
        if (!used[i]){
            return false;
        }
    }
    return true;
}


void brute_force(Cluster &cl, Line_Clusters &LCS){

    // create graph where we iterate among all spanning trees
    if (cl.lines.size() < 2){
        // nothing to do here
        if (ververbose) cout << "cluster of size 1 detected" << endl;
        return;
    }

    Line_Graph mygraph;
    int n = (int) cl.lines.size();
    for (int i = 0; i < n; i++){
        vertex v;
        v.identity = i;
        v.line_id = cl.lines[i];
        mygraph.vertices.push_back(v);
    }
    for (int i = 0; i < n; i++){
        for (int j = i+1; j < n; j++){
            if (LCS.lc[LCS.ltvi[cl.lines[i]]].xvals[LCS.ltvi[cl.lines[j]]].size() > 0
                || LCS.lc[LCS.ltvi[cl.lines[j]]].xvals[LCS.ltvi[cl.lines[i]]].size() > 0){
                mygraph.edges.push_back(make_pair(i,j));
                mygraph.vertices[i].adj_vertices.insert(j);
                mygraph.vertices[j].adj_vertices.insert(i);
            }
        }
    }


    // generate all permutations
    int m = (int) mygraph.edges.size();

    if (m == 0 && n == 2){
        // only happens after everything is merged that should be merged
        if (ververbose) cout << "Merging two independent clusters" << endl;
        merge(LCS, cl.lines[0], cl.lines[1], 0);
        return;
    }



    vector<bool> perm(m);
    for (int i = 0; i < n-1; i++){
        perm[m-1-i] = true;
    }

    long long minimum = LINF;
    long long dum;
    vector<bool> min_perm;
    vv tij(n);
    for (int i = 0; i < n; i++) tij[i].resize(n);

    vv min_tij = tij;
    vv dum_tij = tij;

    // calculate normal brute_force
    if (tree_off){
        tie(dum, min_tij) = bf_normal_eval(LCS, mygraph);
    }

    else{
        do {
            // if permutation is tree, evaluate this permutation
            if (is_tree(perm, mygraph)){
                tie(dum, dum_tij) = bf_eval(perm, LCS, mygraph, tij);
                if (dum < minimum){
                    minimum = dum;
                    min_tij = dum_tij;
                }
            }
        }
        while (next_permutation(perm.begin(), perm.end()));
    }

    // merge lines
    for (int i = 1; i < cl.lines.size(); i++){
        merge(LCS, cl.lines[0], cl.lines[i], min_tij[0][i]);
    }
    return;
}
void rollout_T(vector<vector<int> > &T){
    int ls = (int) T.size();
    for (int i = 0; i < ls; i++) T[i][i] = 0;
    queue<int> q;
    set<int> vis;
    vector<bool> used(ls);
    q.push(0);
    used[0] = true;

    while (!q.empty()){
        int i = q.front();
        vis.insert(i);
        q.pop();
        for (int j = 0; j < ls; j++){
            if (!used[j]){
                for (int k: vis){
                    if ( T[k][j] != -1){
                        for (int l: vis){
                            T[l][j] = mod(T[l][k] + T[k][j]);
                            T[j][l] = mod(-T[l][j]);
                        }
                        used[j] = true;
                        vis.insert(j);
                        q.push(j);
                        break;
                    }
                }
            }
        }
    }
}

void write_xvals(Line_Clusters &LCS){
    string s = prefix + "/xvals.txt";
    remove(s.c_str());
    ofstream ofs;
    ofs.open (s.c_str(), ios_base::app);
    ofs << "[";
    for (int i = 0; i < LCS.lc.size(); i++){
        ofs << "[";
        for (int j = 0; j < LCS.lc[i].xvals.size(); j++){
            ofs << "[";
            int l = (int) LCS.lc[i].xvals[j].size() - 1;
            if (l != -1){
                for (int k = 0; k < l; k++){
                    ofs << LCS.lc[i].xvals[j][k].first << ",";
                }
                ofs << LCS.lc[i].xvals[j][l].first;
            }
            if (j == LCS.lc[i].xvals.size()-1) ofs << "]";
            else ofs << "],";
        }
        if (i == LCS.lc.size() -1) ofs << "]";
        else ofs << "]," << endl;
    }
    ofs << "]" << endl;
    ofs.close();

    s = prefix + "/wvals.txt";
    remove(s.c_str());
    ofs.open (s.c_str(), ios_base::app);
    ofs << "[";
    for (int i = 0; i < LCS.lc.size(); i++){
        ofs << "[";
        for (int j = 0; j < LCS.lc[i].xvals.size(); j++){
            ofs << "[";
            int l = (int) LCS.lc[i].xvals[j].size() - 1;
            if (l != -1){
                for (int k = 0; k < l; k++){
                    ofs << LCS.lc[i].xvals[j][k].second << ",";
                }
                ofs << LCS.lc[i].xvals[j][l].second;
            }
            if (j == LCS.lc[i].xvals.size()-1) ofs << "]";
            else ofs << "],";
        }
        if (i == LCS.lc.size() -1) ofs << "]";
        else ofs << "]," << endl;
    }
    ofs << "]" << endl;
    ofs.close();

    return;

}


void solve_instance(void){

    // define line_graph
    Line_Graph LG = define_lg();

    // find bridges
    vector<bool> b = find_bridges(LG);

    // delete bridges in copy of LG
    vector<edge> Bridges;
    Line_Graph LGC = LG;
    for (int i = (int) b.size(); i >= 0; i--){
        if (b[i]){

            Bridges.push_back(LGC.edges[i]);
            int left = LGC.edges[i].first;
            int right = LGC.edges[i].second;
            if (verbose) cout << "bridge found! connecting lines " << LG.vertices[left].line_id << " and " << LG.vertices[right].line_id << endl;
            LGC.vertices[left].adj_vertices.erase(right);
            LGC.vertices[right].adj_vertices.erase(left);
            LGC.edges.erase(LGC.edges.begin()+i);
        }
    }

    // find connected components and create clusters to be solved
    vector<int> cc;
    int c;
    tie(cc, c) = find_cc(LGC);
    vector<Cluster> Clusters(c);
    for (int i = 0; i < (int) cc.size(); i++){
        Clusters[cc[i]-1].lines.push_back(LG.vertices[i].line_id);
    }

    // initialize Line candidates
    Line_Clusters LCS = init_LC();


    // write xvals
    if (write_xw) write_xvals(LCS);


    // initialize T_Matrix
    int ls = (int) Lines.size();
    T_Matrix.resize(ls);
    for (int i = 0; i < ls ; i++) T_Matrix[i].resize(ls,-1);

    // solve clusters seperately
    for (Cluster cl: Clusters){
        while (!complexity(cl, LGC) && cl.lines.size() > 1){
            matching_merge(cl, LCS, LGC);
        }

        // at some point the line graph is so small that we can calculate the exact best solution
        brute_force(cl, LCS);
        if (ververbose){
            cout << "Cluster done" << endl;
            cout << LCS.lc[LCS.ltvi[cl.lines[0]]].intern_val << endl;
        }
    }

    // merge all clusters to one
    while (LCS.lc.size() > 1){
        bool found = false;
        for (int i = 0; i < LCS.lc.size(); i++){
            for (int j = i+1; j < LCS.lc.size(); j++){
                if (LCS.lc[i].xvals[j].size() > 0 || LCS.lc[j].xvals[i].size() > 0){
                    Cluster cl;
                    cl.lines.push_back(LCS.lc[i].line_id);
                    cl.lines.push_back(LCS.lc[j].line_id);
                    brute_force(cl, LCS);
                    found = true;
                }
            }
        }
        if (!found) {
            Cluster cl;
            cl.lines.push_back(LCS.lc[0].line_id);
            cl.lines.push_back(LCS.lc[1].line_id);
            brute_force(cl, LCS);
        }
    }

    // roll out final T-matrix
    rollout_T(T_Matrix);

    if (verbose){
        for (auto v: T_Matrix){
            for (int i: v) cout << i << " ";
            cout << endl;
        }
    }

    // write new timetable
    for (auto it: Events){
        Event e = it.second;
        if (e.line_path_pred == -1){
            Events[e.identity].time = T_Matrix[0][Line_id_to_it[e.line_id]];
            while (e.line_path_succ != -1){
                Events[e.line_path_succ].time = mod(Events[e.identity].time + Activities[EA_corr[make_pair(e.identity,e.line_path_succ)]].lower_bound);
                e = Events[e.line_path_succ];
            }
        }
    }


    // get sum
    long long summe = 0, summe2 = 0;
    for (auto it: Ch_Acts){
        Ch_Act a = it.second;
        summe2 += a.passengers * mod(T_Matrix[0][Line_id_to_it[Events[a.head].line_id]] + get_dur_to_start(a.head) -
                                  (T_Matrix[0][Line_id_to_it[Events[a.tail].line_id]] + get_dur_to_start(a.tail)) - a.lower_bound);
        summe += mod(Events[a.head].time - Events[a.tail].time - a.lower_bound) * a.passengers;
    }

    if (summe2 != summe){
        cout << "what?" << endl;
    }
    long long summe3 = 0;
    for (auto it: Activities){
      Activity a = it.second;
      Event e1 = Events[a.tail], e2 = Events[a.head];
      summe3 += (mod(e2.time - e1.time - a.lower_bound) + a.lower_bound) * a.passengers;
   }

    cout << "Algorithm done!" << endl << "Slack times: " << LCS.lc[0].intern_val << endl;
    cout << "PTT1: " << summe3 << endl;
    if (verbose) cout << "evaluation yields objective of " << summe << endl;
}
