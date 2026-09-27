#ifndef SUPEREDGE_HPP
#define SUPEREDGE_HPP
#include <vector>
#include <iostream>
class superEdge{
private:
    std::vector<std::pair<int, int> > x_w_pairs;
    int from = -1;
    int to = -1;

public:
    superEdge(){}
    superEdge(int f, int t){
        from = f;
        to = t;
    }

    int getFrom(){
        return from;
    }
    int getTo(){
        return to;
    }
    void setFrom(int i){
        from = i;
    }
    void setTo(int i){
        to = i;
    }
    void addPair(int i, int j, int shift = 0){
        x_w_pairs.push_back({i+shift,j});
    }
    std::vector<std::pair<int, int> > getValues(){
        return x_w_pairs;
    }
    void removeAllValues(){
        x_w_pairs.resize(0);
    }


};
#endif
