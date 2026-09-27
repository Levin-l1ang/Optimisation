#ifndef FULLOD_HPP
#define FULLOD_HPP

#include "../OD.hpp"
#include "../ODPair.hpp"
#include <vector>
#include "../../exception/exceptions.hpp"

/**
 * Implements the od interface using a two dimensional array. Therefore accessing the od pairs by index is cheap, but
 * getting nonempty od-pairs by {@link #getODPairs()} may be expensive.
 */
class FullOD : public OD {
    /**
     * The data object to store the od pairs in. Remember to use index shifting for every raw access inside this class!
     */
private:
    std::vector<std::vector<ODPair> >  matrix;
    int size;

public:
    /**
     * Create a new full od matrix. This matrix will store the od pairs in a two dimensional array. Therefore accessing
     * the od pairs by index is cheap, but getting nonempty od-pairs by {@link #getODPairs()} may be expensive.
     * @param size the size of the matrix
     */
    FullOD(int size){
        ODPair odp_init(0,0,0);
        this->size = size;
        this->matrix.resize(size);
        for (int i = 0; i < size; i++) this->matrix[i].resize(size, odp_init);
    }

    int getSize(void){
        return this->size;
    }
    double getValue(int origin, int destination) {
         if (origin <= 0 || origin > size) throw IndexOutOfBoundsException(origin, size);
         if (destination <= 0 || destination > size) throw IndexOutOfBoundsException(destination, size);
        else return matrix[origin-1][destination-1].getValue();
    }

    void setValue(int origin, int destination, double newValue) {
        if (origin <= 0 || origin > size) throw IndexOutOfBoundsException(origin, size);
        if (destination <= 0 || destination > size) throw IndexOutOfBoundsException(destination, size);
        matrix[origin-1][destination-1] = ODPair(origin, destination, newValue);
    }


    double computeNumberOfPassengers() {
        double sum = 0;
        for(int origin = 1; origin <= this->size; origin++){
            for(int destination = 1; destination <= this->size; destination++){
                sum += getValue(origin, destination);
            }
        }
        return sum;
    }

    std::vector<ODPair> getODPairs() {
        std::vector<ODPair> ret;
        for (int i = 0; i < size; i++){
            for (int j = 0; j < size; j++){
                if (matrix[i][j].getValue() != 0){
                    ret.push_back(matrix[i][j]);
                }
            }
        }
        return ret;
    }
};
#endif
