#ifndef RPTTSACTIVITY_HPP
#define RPTTSACTIVITY_HPP
#include "../../../../core/cpp/include/core.hpp"
#include "RpttsEvent.hpp"

class RpttsActivity: public Edge<RpttsEvent>, public PeriodicActivity {
private:
    int x; // slack of activity
    RpttsEvent* sourceEvent;
    RpttsEvent* targetEvent;
public:
    RpttsActivity(PeriodicActivity &a, RpttsEvent* from, RpttsEvent* to) : PeriodicActivity(a){
        sourceEvent = from;
        targetEvent = to;
    }
    void calculateX(){
        x = targetEvent->getDurationToStart() - sourceEvent->getDurationToStart() - lowerBound;
    }
    int getX(){
        return x;
    }
    RpttsEvent* getLeftNode(){
        return sourceEvent;
    }
    RpttsEvent* getRightNode(){
        return targetEvent;
    }
    int getId(){
        return activityId;
    }
    void setId(int i){
        activityId = i;
    }
    bool isDirected(){
        return true;
    }

};

#endif
