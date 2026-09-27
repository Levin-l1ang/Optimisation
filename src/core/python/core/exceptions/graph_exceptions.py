from core.exceptions.exceptions import LinTimException


class GraphEdgeIdMultiplyAssignedException(LinTimException):
    """
    Exception to throw if the same edge id is assigned multiple times.
    """
    def __init__(self, edge_id: int):
        """
        Exception to throw if the same edge id is assigned multiple times.

        :param edge_id: the edge id
        :type edge_id: int
        """
        super().__init__("Error G2: Edge with id {} already exists.".format(edge_id))


class GraphIncidentNodeNotFoundException(LinTimException):
    """
    Exception to throw if edge is incident to a node which does not exist.
    """
    def __init__(self, edge_id: int, node_id: int):
        """
        Exception to throw if edge is incident to a node which does not exist.

        :param edge_id: the edge id
        :type edge_id: int
        :param node_id: the non-existing node id
        :type node_id: int
        """
        super().__init__("Error G3: Edge {} is incident to node {} but node {} does not exist."
                         .format(edge_id, node_id, node_id))


class GraphNodeIdMultiplyAssignedException(LinTimException):
    """
    Exception to throw if the same node id is assigned multiple times.
    """
    def __init__(self, node_id: int):
        """
        Exception to throw if the same node id is assigned multiple times.

        :param node_id: the node id
        :type node_id: int
        """
        super().__init__("Error G1: Node with id {} already exists.".format(node_id))


class GraphEdgeByNodesNotFoundException(LinTimException):
    """
    Exception to throw if there exists no edge between the requested nodes.
    """
    def __init__(self, node1_id: int, node2_id: int):
        """
        Exception to throw if there exists no edge between the requested nodes.

        :param node1_id: the first node id
        :type node1_id: int
        :param node2_id: the second node id
        :type node2_id: int
        """
        super().__init__("Error G4: Edge between node {} and {} does not exist.".format(node1_id, node2_id))
