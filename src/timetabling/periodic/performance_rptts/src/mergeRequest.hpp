#ifndef MERGEREQUEST_HPP
#define MERGEREQUEST_HPP

class mergeRequest{
private:
    int leftId;
    int rightId;
    int startDifference;
    long long objval = -1;
    long long minval = -1;
    int nbrTravellers = 0;
public:
    mergeRequest(){}
    mergeRequest(int l, int r,int s, int o){
        leftId = l;
        rightId = r;
        startDifference = s;
        objval = o;
    }

    int getLeftId(){
        return leftId;
    }
    void setLeftId(int i){
        leftId = i;
    }
    int getRightId(){
        return rightId;
    }
    void setRightId(int i){
        rightId = i;
    }
    int getStartDifference(){
        return startDifference;
    }
    void setStartDifference(int i){
        startDifference = i;
    }
    long long getObjVal(){
        return objval;
    }
    long long getMinVal(){
        return minval;
    }
    void setObjVal(long long i){
        objval = i;
    }
    void setNbrTravellers(int i){
        nbrTravellers = i;
    }
    int getNbrTravellers(){
        return nbrTravellers;
    }
    void setMinVal(long long i){
        minval = i;
    }

    friend bool operator>(mergeRequest a, mergeRequest b){
        if (a.objval == b.objval) return a.nbrTravellers > b.nbrTravellers;
        else return a.objval > b.objval;
    }

};


#endif
