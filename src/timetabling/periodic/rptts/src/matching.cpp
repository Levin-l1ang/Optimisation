//
//  matching.cpp
//  rptts

#include "matching.h"
// functions
cm_edge mat[MaxNX + 1][MaxNX + 1];

vector<int> cm_mate (MaxNX+1);
vector<int> cm_lab (MaxNX + 1);
vector<int> cm_q (MaxN);
vector<int> cm_fa (MaxNX + 1);
vector<int> cm_col (MaxNX + 1);
vector<int> cm_slackv (MaxNX + 1);
vector<int> cm_bel (MaxNX + 1);
vector<int> cm_bloch [MaxNX + 1];
int cm_q_n, cm_n_x, cm_n;
vector<vector<int> > cm_blofrom (MaxNX + 1);


void init(){
    for (int i = 0; i < MaxNX + 1; i++){
        cm_blofrom[i].resize(MaxNX + 1);
    }
}

inline int e_delta(const cm_edge &e) // does not work inside blossoms
{
    return cm_lab[e.v] + cm_lab[e.u] - mat[e.v][e.u].w * 2;
}
inline void update_slackv(int v, int x)
{
    if (!cm_slackv[x] || e_delta(mat[v][x]) < e_delta(mat[cm_slackv[x]][x]))
        cm_slackv[x] = v;
}
inline void calc_slackv(int x)
{
    cm_slackv[x] = 0;
    for (int v = 1; v <= cm_n; v++)
        if (mat[v][x].w > 0 && cm_bel[v] != x && cm_col[cm_bel[v]] == 0)
            update_slackv(v, x);
}

inline void q_push(int x)
{
    if (x <= cm_n)
        cm_q[cm_q_n++] = x;
    else
    {
        for (int i = 0; i < size(cm_bloch[x]); i++)
            q_push(cm_bloch[x][i]);
    }
}
inline void set_mate(int xv, int xu)
{
    cm_mate[xv] = mat[xv][xu].u;
    if (xv > cm_n)
    {
        cm_edge e = mat[xv][xu];
        int xr = cm_blofrom[xv][e.v];
        int pr = (int) (find(cm_bloch[xv].begin(), cm_bloch[xv].end(), xr) - cm_bloch[xv].begin());
        if (pr % 2 == 1)
        {
            reverse(cm_bloch[xv].begin() + 1, cm_bloch[xv].end());
            pr = size(cm_bloch[xv]) - pr;
        }
        
        for (int i = 0; i < pr; i++)
            set_mate(cm_bloch[xv][i], cm_bloch[xv][i ^ 1]);
        set_mate(xr, xu);
        
        rotate(cm_bloch[xv].begin(), cm_bloch[xv].begin() + pr, cm_bloch[xv].end());
    }
}
inline void set_bel(int x, int b)
{
    cm_bel[x] = b;
    if (x > cm_n)
    {
        for (int i = 0; i < size(cm_bloch[x]); i++)
            set_bel(cm_bloch[x][i], b);
    }
}

inline void augment(int xv, int xu)
{
    while (true)
    {
        int xnu = cm_bel[cm_mate[xv]];
        set_mate(xv, xu);
        if (!xnu)
            return;
        set_mate(xnu, cm_bel[cm_fa[xnu]]);
        xv = cm_bel[cm_fa[xnu]], xu = xnu;
    }
}
inline int get_lca(int xv, int xu)
{
    static bool book[MaxNX + 1];
    for (int x = 1; x <= cm_n_x; x++)
        book[x] = false;
    while (xv || xu)
    {
        if (xv)
        {
            if (book[xv])
                return xv;
            book[xv] = true;
            xv = cm_bel[cm_mate[xv]];
            if (xv)
                xv = cm_bel[cm_fa[xv]];
        }
        swap(xv, xu);
    }
    return 0;
}

inline void add_blossom(int xv, int xa, int xu)
{
    int b = cm_n + 1;
    while (b <= cm_n_x && cm_bel[b])
        b++;
    if (b > cm_n_x)
        cm_n_x++;
    
    cm_lab[b] = 0;
    cm_col[b] = 0;
    
    cm_mate[b] = cm_mate[xa];
    
    cm_bloch[b].clear();
    cm_bloch[b].push_back(xa);
    for (int x = xv; x != xa; x = cm_bel[cm_fa[cm_bel[cm_mate[x]]]])
        cm_bloch[b].push_back(x), cm_bloch[b].push_back(cm_bel[cm_mate[x]]), q_push(cm_bel[cm_mate[x]]);
    reverse(cm_bloch[b].begin() + 1, cm_bloch[b].end());
    for (int x = xu; x != xa; x = cm_bel[cm_fa[cm_bel[cm_mate[x]]]])
        cm_bloch[b].push_back(x), cm_bloch[b].push_back(cm_bel[cm_mate[x]]), q_push(cm_bel[cm_mate[x]]);
    
    set_bel(b, b);
    
    for (int x = 1; x <= cm_n_x; x++)
    {
        mat[b][x].w = mat[x][b].w = 0;
        cm_blofrom[b][x] = 0;
    }
    for (int i = 0; i < size(cm_bloch[b]); i++)
    {
        int xs = cm_bloch[b][i];
        for (int x = 1; x <= cm_n_x; x++)
            if (mat[b][x].w == 0 || e_delta(mat[xs][x]) < e_delta(mat[b][x]))
                mat[b][x] = mat[xs][x], mat[x][b] = mat[x][xs];
        for (int x = 1; x <= cm_n_x; x++)
            if (cm_blofrom[xs][x])
                cm_blofrom[b][x] = xs;
    }
    calc_slackv(b);
}
inline void expand_blossom1(int b) // cm_lab[b] == 1
{
    for (int i = 0; i < size(cm_bloch[b]); i++)
        set_bel(cm_bloch[b][i], cm_bloch[b][i]);
    
    int xr = cm_blofrom[b][mat[b][cm_fa[b]].v];
    int pr =  (int) (find(cm_bloch[b].begin(), cm_bloch[b].end(), xr) - cm_bloch[b].begin());
    if (pr % 2 == 1)
    {
        reverse(cm_bloch[b].begin() + 1, cm_bloch[b].end());
        pr = size(cm_bloch[b]) - pr;
    }
    
    for (int i = 0; i < pr; i += 2)
    {
        int xs = cm_bloch[b][i], xns = cm_bloch[b][i + 1];
        cm_fa[xs] = mat[xns][xs].v;
        cm_col[xs] = 1, cm_col[xns] = 0;
        cm_slackv[xs] = 0, calc_slackv(xns);
        q_push(xns);
    }
    cm_col[xr] = 1;
    cm_fa[xr] = cm_fa[b];
    for (int i = pr + 1; i < size(cm_bloch[b]); i++)
    {
        int xs = cm_bloch[b][i];
        cm_col[xs] = -1;
        calc_slackv(xs);
    }
    
    cm_bel[b] = 0;
}
inline void expand_blossom_final(int b) // at the final stage
{
    for (int i = 0; i < size(cm_bloch[b]); i++)
    {
        if (cm_bloch[b][i] > cm_n && cm_lab[cm_bloch[b][i]] == 0)
            expand_blossom_final(cm_bloch[b][i]);
        else
            set_bel(cm_bloch[b][i], cm_bloch[b][i]);
    }
    cm_bel[b] = -1;
}

inline bool on_found_edge(const cm_edge &e)
{
    int xv = cm_bel[e.v], xu = cm_bel[e.u];
    if (cm_col[xu] == -1)
    {
        int nv = cm_bel[cm_mate[xu]];
        cm_fa[xu] = e.v;
        cm_col[xu] = 1, cm_col[nv] = 0;
        cm_slackv[xu] = cm_slackv[nv] = 0;
        q_push(nv);
    }
    else if (cm_col[xu] == 0)
    {
        int xa = get_lca(xv, xu);
        if (!xa)
        {
            augment(xv, xu), augment(xu, xv);
            for (int b = cm_n + 1; b <= cm_n_x; b++)
                if (cm_bel[b] == b && cm_lab[b] == 0)
                    expand_blossom_final(b);
            return true;
        }
        else
            add_blossom(xv, xa, xu);
    }
    return false;
}

bool match()
{
    for (int x = 1; x <= cm_n_x; x++)
        cm_col[x] = -1, cm_slackv[x] = 0;
    
    cm_q_n = 0;
    for (int x = 1; x <= cm_n_x; x++)
        if (cm_bel[x] == x && !cm_mate[x])
            cm_fa[x] = 0, cm_col[x] = 0, cm_slackv[x] = 0, q_push(x);
    if (cm_q_n == 0)
        return false;
    
    while (true)
    {
        for (int i = 0; i < cm_q_n; i++)
        {
            int v = cm_q[i];
            for (int u = 1; u <= cm_n; u++)
                if (mat[v][u].w > 0 && cm_bel[v] != cm_bel[u])
                {
                    int d = e_delta(mat[v][u]);
                    if (d == 0)
                    {
                        if (on_found_edge(mat[v][u]))
                            return true;
                    }
                    else if (cm_col[cm_bel[u]] == -1 || cm_col[cm_bel[u]] == 0)
                        update_slackv(v, cm_bel[u]);
                }
        }
        
        int d = INF;
        for (int v = 1; v <= cm_n; v++)
            if (cm_col[cm_bel[v]] == 0)
                tension(d, cm_lab[v]);
        for (int b = cm_n + 1; b <= cm_n_x; b++)
            if (cm_bel[b] == b && cm_col[b] == 1)
                tension(d, cm_lab[b] / 2);
        for (int x = 1; x <= cm_n_x; x++)
            if (cm_bel[x] == x && cm_slackv[x])
            {
                if (cm_col[x] == -1)
                    tension(d, e_delta(mat[cm_slackv[x]][x]));
                else if (cm_col[x] == 0)
                    tension(d, e_delta(mat[cm_slackv[x]][x]) / 2);
            }
        
        for (int v = 1; v <= cm_n; v++)
        {
            if (cm_col[cm_bel[v]] == 0)
                cm_lab[v] -= d;
            else if (cm_col[cm_bel[v]] == 1)
                cm_lab[v] += d;
        }
        for (int b = cm_n + 1; b <= cm_n_x; b++)
            if (cm_bel[b] == b)
            {
                if (cm_col[cm_bel[b]] == 0)
                    cm_lab[b] += d * 2;
                else if (cm_col[cm_bel[b]] == 1)
                    cm_lab[b] -= d * 2;
            }
        
        cm_q_n = 0;
        for (int v = 1; v <= cm_n; v++)
            if (cm_lab[v] == 0) // all unmatched vertices' labels are zero! cheers!
                return false;
        for (int x = 1; x <= cm_n_x; x++)
            if (cm_bel[x] == x && cm_slackv[x] && cm_bel[cm_slackv[x]] != x && e_delta(mat[cm_slackv[x]][x]) == 0)
            {
                if (on_found_edge(mat[cm_slackv[x]][x]))
                    return true;
            }
        for (int b = cm_n + 1; b <= cm_n_x; b++)
            if (cm_bel[b] == b && cm_col[b] == 1 && cm_lab[b] == 0)
                expand_blossom1(b);
    }
    return false;
}

void calc_max_weight_match()
{
    // mates are one-based?
    for (int v = 1; v <= cm_n; v++)
        cm_mate[v] = 0;
    
    cm_n_x = cm_n;
    
    cm_bel[0] = 0;
    for (int v = 1; v <= cm_n; v++)
        cm_bel[v] = v, cm_bloch[v].clear();
    for (int v = 1; v <= cm_n; v++)
        for (int u = 1; u <= cm_n; u++)
            cm_blofrom[v][u] = v == u ? v : 0;
    
    int w_max = 0;
    for (int v = 1; v <= cm_n; v++)
        for (int u = 1; u <= cm_n; u++)
            relax(w_max, mat[v][u].w);
    for (int v = 1; v <= cm_n; v++)
        cm_lab[v] = w_max;
    
    while (match()){
        
    }
    
}

vector<pair<int, int> > chinese_matching(vector<vector<int> > &weights)
{
    // main
    vector<pair<int, int> > ret;
    if (weights.size() == 1){
        ret.resize(0);
        return ret;
    }//cout << "y do u come here?" << endl;
    init();
    cm_n = (int) weights.size();
    
    // rewrite as max weighted matching problem
    int maximum = 0;
    for (int v = 0; v < cm_n ; v++){
        for (int u = v + 1; u < cm_n; u++){
            maximum = max(maximum,weights[v][u]);
        }
    }
    for (int v = 0; v < cm_n; v++){
        for (int u = 0; u < cm_n; u++){
            weights[v][u] = maximum + 1 - weights[v][u];
        }
    }
    // diagonal is zero
    for (int v = 0; v < cm_n; v++) {
        weights[v][v] = 0;
    }
    for (int v = 1; v <= cm_n; v++)
    {
        for (int u = 1; u <= cm_n; u++)
        {
            mat[v][u] = cm_edge(v, u, 0);
            mat[u][v] = cm_edge(u, v, 0);
            mat[v][u].w = mat[u][v].w = weights[u-1][v-1];
        }
    }
    calc_max_weight_match();
    

    for (int i = 0; i < cm_n; i++){
        if (cm_mate[i+1] != 0){
            ret.push_back(make_pair(i, cm_mate[i+1]-1));
            cm_mate[cm_mate[i+1]] = 0;
            cm_mate[i+1] = 0;
        }
    }
    return ret;
}
