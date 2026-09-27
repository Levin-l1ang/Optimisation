#ifndef RPTTSEVENT_HPP
#define RPTTSEVENT_HPP

#include "../../../../core/cpp/include/core.hpp"


class RpttsEvent: public PeriodicEvent{
private:
    // duration to start event of line
    int duration_to_start = -1;
    RpttsEvent* prev_event = nullptr;
    RpttsEvent* next_event = nullptr;
    int line_cluster_id;
    int dur_to_prev_event = -1;
    void calcDurationToStart(){
        if (prev_event == nullptr) duration_to_start = 0;
        else duration_to_start = prev_event->getDurationToStart() + dur_to_prev_event;
    }
public:
    RpttsEvent(PeriodicEvent& e): PeriodicEvent(e){
    }
    int getDurationToStart(){
        if (duration_to_start == -1){
            calcDurationToStart();
        }
        return duration_to_start;
    }
    void setLineClusterId(int i){
        line_cluster_id = i;
    }
    int getLineClusterId(){
        return line_cluster_id;
    }
    void setPrevEvent(RpttsEvent* pe){
        prev_event = pe;
        pe->setNextEvent(this);
    }
    void setNextEvent(RpttsEvent* ne){
        next_event = ne;
    }
    RpttsEvent* getPrevEvent(){
        return this->prev_event;
    }
    RpttsEvent* getNextEvent(){
        return this->next_event;
    }
    void setDurationToPrevEvent(int d){
        dur_to_prev_event = d;
    }
    void setLineId(int i){
        this->lineId = i;
    }

};

#endif
