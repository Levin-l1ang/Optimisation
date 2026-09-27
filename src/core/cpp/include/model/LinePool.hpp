#ifndef LINEPOOL_HPP
#define LINEPOOL_HPP
/**
 * A class to represent the line pool.
 */
#include "Line.hpp"
#include <vector>

class LinePool{
private:
    std::vector <Line> pool;

    bool contains(int id){
        for(Line& l: this->pool){
            if(l.getId() == id){
                return true;
            }
        }
        return false;
    }

public:
    /**
     * Method to add a line, if not already a line with the same id is in the pool.
     *
     * @param line line to add
     * @return true if added
     */
    bool addLine(Line line){
        if(this->contains(line.getId())){
            return false;
        } else{
            pool.push_back(line);
            return true;
        }
    }

    /**
     * Method to remove line with given id, if it exists in pool.
     *
     * @param lineId id of the line to remove
     * @return true if a line was removed, false if not
     */
    bool removeLine(int lineId){
        for(auto it = this->pool.begin(); it != this->pool.end(); ++it){
            if((*it).getId() == lineId){
                this->pool.erase(it);
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a collection of the lines. This is not a copy!
     *
     * @return a collection of the lines
     */
    std::vector <Line> getLines(){
        return this->pool;
    }

    /**
     * Gets the line for a given id or null if it is not in the pool.
     *
     * @param id id of the line to get
     * @return the line with id id or null if not in the pool
     */
    Line* getLine(int id){
        for(Line& l: this->pool){
            if(id == l.getId()){
                return &l;
            }
        }
        return nullptr;
    }

    /**
     * Method to get a vector of all lines with frequency > 0.
     *
     * @return a vector of all lines with frequency > 0.
     */
    std::vector <Line> getLineConcept(){
        std::vector <Line> lineConcept;
        for(Line& line : this->pool){
            if(line.getFrequency() > 0){
                lineConcept.push_back(line);
            }
        }
        return lineConcept;
    }

    // TODO equals operator

};

#endif
