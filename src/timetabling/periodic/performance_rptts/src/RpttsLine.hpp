#ifndef RPTTSLINE_HPP
#define RPTTSLINE_HPP
#include "../../../../core/cpp/include/core.hpp"
class RpttsLine : public Line {
private:
    int start_time = -1;

public:
    RpttsLine(Line l): Line(l) {}
    int getStartTime(){
        return start_time;
    }
    void setStartTime(int i){
        start_time = i;
    }
    friend bool operator ==(RpttsLine &a, RpttsLine &b){
        return (a.getId() == b.getId());
    }
    friend bool operator !=(RpttsLine &a, RpttsLine &b){
        return !(a == b);
    }
};

#endif
