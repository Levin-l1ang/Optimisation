/**
 * Exception to throw if the same node id is assigned multiple times.
 */
#ifndef GRAPHNODEIDMULTIPLYASSIGNEDEXCEPTION_HPP
#define GRAPHNODEIDMULTIPLYASSIGNEDEXCEPTION_HPP


class GraphNodeIdMultiplyAssignedException : public std::runtime_error{
public:
    GraphNodeIdMultiplyAssignedException(int nodeId) :
        std::runtime_error("Error G1: Node with id " + std::to_string(nodeId) + " already exists."){}
};

#endif
