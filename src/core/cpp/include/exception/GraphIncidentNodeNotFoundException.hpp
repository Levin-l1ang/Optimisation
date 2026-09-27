/**
 * Exception to throw if edge is incident to a node which does not exist.
 */

#ifndef GRAPHINCIDENTNODENOTFOUNDEXCEPTION_HPP
#define GRAPHINCIDENTNODENOTFOUNDEXCEPTION_HPP


class GraphIncidentNodeNotFoundException : public std::runtime_error{
public:
    GraphIncidentNodeNotFoundException(int edgeId, int nodeId) :
        std::runtime_error(
            "Error G3: Edge " + std::to_string(edgeId) + " is incident to node " + std::to_string(nodeId) + " but node " + std::to_string(nodeId) +
            " does not " + "exist."){}
};

#endif
