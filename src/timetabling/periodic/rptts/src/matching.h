//
//  matching.h
//  rptts

// Matching algorithm



#ifndef __MATCHING_H__
#define __MATCHING_H__


#include <iostream>
#include <cstdio>
#include <algorithm>
#include <vector>
#include <stdio.h>

#define INF 1e9
using namespace std;

// declarations

const int MaxN = 400;

template <class T>
inline void tension(T &a, const T &b)
{
    if (b < a)
        a = b;
}
template <class T>
inline void relax(T &a, const T &b)
{
    if (b > a)
        a = b;
}
template <class T>
inline int size(const T &a)
{
    return (int)a.size();
}

const int MaxNX = MaxN + MaxN;

struct cm_edge
{
    int v, u, w;
    
    cm_edge(){}
    cm_edge(const int &_v, const int &_u, const int &_w)
    : v(_v), u(_u), w(_w){}
};

void init(void);
vector<pair<int, int> > chinese_matching(vector<vector<int> > &A);
bool match(void);
void calc_max_weight_match(void);



#endif
