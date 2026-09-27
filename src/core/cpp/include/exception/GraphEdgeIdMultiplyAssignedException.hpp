/**
 * Exception to throw if the same edge id is assigned multiple times.
 */

#ifndef GRAPHEDGEIDMULTIPLYASSIGNEDEXCEPTION_HPP
#define GRAPHEDGEIDMULTIPLYASSIGNEDEXCEPTION_HPP


class GraphEdgeIdMultiplyAssignedException : public std::runtime_error{
public:
    GraphEdgeIdMultiplyAssignedException(int edgeId) :
        std::runtime_error("Error G2: Edge with id " + std::to_string(edgeId) + " already exists."){}
};

#endif
