from abc import ABCMeta, abstractmethod
from typing import Callable, TypeVar, Generic, Any, List, Union

class Node(metaclass=ABCMeta):
    """
    The template for a node object for a graph structure.
    """
    # Python 2.7: __metaclass__ = ABCMeta

    @abstractmethod
    def getId(self) -> int:
        """
        Get the id of an edge. This is a representation of the edge in a graph
        and needs to be unique.
        :return: the id of the node
        """
        raise NotImplementedError

    @abstractmethod
    def setId(self, new_id: int) -> None:
        """
        Set the id of this node. THIS METHOD MUST ONLY BE USED IN GRAPH
        IMPLEMENTATIONS. Changing the id of a node in a
        graph may break the graph structure, depending on implementation
        :param new_id: the new id of the node
        """
        raise NotImplementedError

    @abstractmethod
    def __eq__(self, other) -> bool:
        raise NotImplementedError

    @abstractmethod
    def __hash__(self) -> int:
        raise NotImplementedError


N = TypeVar('N', bound=Node)


class Edge(Generic[N], metaclass=ABCMeta):
    """
    The template for an edge object for a graph structure. Used in the directed
    and undirected case.
    """
    # Python 2.7: __metaclass__ = ABCMeta

    @abstractmethod
    def getId(self) -> int:
        """
        Get the id of an edge. This is a representation of the edge in a graph
        and needs to be unique.
        :return: the id of the edge
        """
        raise NotImplementedError

    @abstractmethod
    def setId(self, new_id: int) -> None:
        """
        Set the id of this edge. THIS METHOD MUST ONLY BE USED IN GRAPH
        IMPLEMENTATIONS. Changing the id of a edge in a
        graph may break the graph structure, depending on implementation
        :param new_id: the new id of the edge
        """
        raise NotImplementedError

    @abstractmethod
    def getLeftNode(self) -> N:
        """
        Get the left node of the edge. If the edge is directed, this is the
        source of the edge.
        :return: the left node
        """
        raise NotImplementedError

    @abstractmethod
    def getRightNode(self) -> N:
        """
        Get the right node of the edge. If the edge is directed, this is the
        target of the edge
        :return: the right node
        """
        raise NotImplementedError

    @abstractmethod
    def isDirected(self) -> bool:
        """
        Get whether this edge is directed. If the edge is directed, it "flows"
        from left to right node, i.e., the left
        node is the source and the right node is the target of the edge
        :return: whether the edge is directed
        """
        raise NotImplementedError


E = TypeVar('E', bound=Edge)


class Graph(Generic[N, E], metaclass=ABCMeta):
    """
    The template for a graph structure. There are default implementations of
    this interface. More implementations may
    follow. Choose the appropriate implementation based on your graph
    structure or implement your own.
    """

    @abstractmethod
    def getNode(self, node_id: int) -> N:
        """
        Get the node with the specified id from the graph. If there is no node
        with the given id, null will be returned.
        :param node_id: the id to search for
        :return: the node with the given id, or null if there is none
        """
        raise NotImplementedError

    @abstractmethod
    def getEdge(self, edge_id: int) -> E:
        """
        Get the edge with the specified id from the graph. If there is no edge
        with the given id, null will be returned.
        :param edge_id: the id to search for
        :return: the edge with the given id, or null if there is none
        """
        raise NotImplementedError

    T = TypeVar('T')

    @abstractmethod
    def get_node_by_function(self, lookup_map: Callable[[N], T], value: T) -> N:
        """
        Get a node from the graph on which the given function takes the given
        value
        :param lookup_map: the function whose value must be matched
        :param value: the value the function must take on the node to be
        returned
        :return: one of the nodes on which the provided function yields the
        provided value, or null if none exists
        """
        raise NotImplementedError

    @abstractmethod
    def get_edge_by_function(self, lookup_map: Callable[[E], T], value: T) -> E:
        """
        Get an edge from the graph on which the given function takes the given
        value
        :param lookup_map: the function whose value must be matched
        :param value: the value the function must take on the edge to be
        returned
        :return: one of the edges on which the provided function yields the
        provided value, or null if none exists
        """
        raise NotImplementedError

    def get_edge_by_nodes(self, node1: N, node2: N) -> E:
        return next(iter([link for link in self.getOutgoingEdges(node1) if (link.getRightNode() is node2) or (not link.isDirected() and link.getLeftNode() is node2 and link.getRightNode() is node1)]), None)

    def get_edge_by_node_ids(self, node1_id: int, node2_id: int) -> E:
        node1 = self.getNode(node1_id)
        node2 = self.getNode(node2_id)
        return self.get_edge_by_nodes(node1, node2)

    @abstractmethod
    def addEdge(self, edge: E) -> bool:
        """
        Add the given edge to the graph. There can not be multiple edges with
        the same id in the same graph.
        :param edge: the edge to add to the network
        :return: whether the edge could be added to the graph
        """
        raise NotImplementedError

    @abstractmethod
    def addNode(self, node: N) -> bool:
        """
        Add the given node to the graph. There can not be multiple nodes with
        the same id in the same graph.
        :param node: the edge to add to the network
        :return: whether the node could be added to the graph
        """
        raise NotImplementedError

    @abstractmethod
    def removeEdge(self, edge: E) -> bool:
        """
        Remove the given edge from the graph. After calling this method, there
        will not be an edge in this graph with
        the same id
        :param edge: the edge to remove
        :return: whether the edge could be removed from the graph
        """
        raise NotImplementedError

    @abstractmethod
    def removeNode(self, node: N) -> bool:
        """
        Remove the given node from the graph. After calling this method, there
        will not be an node in this graph with
        the same id
        :param node: the node to remove
        :return: whether the node could be removed from the graph
        """
        raise NotImplementedError

    @abstractmethod
    def orderNodes(self, key_function: Callable[[N], Any]) -> None:
        """
        Order the nodes by the given key function.

        This will assign new ids to the nodes. After calling this method, the
        nodes will be numbered consecutively, with
        the order imposed by the given key function. If the key function is
        None, no order is guaranteed.
        :param key_function: the key function to order the nodes by
        """
        raise NotImplementedError

    @abstractmethod
    def orderEdges(self, key_function: Callable[[E], Any]) -> None:
        """
        Order the edges by the given key function.

        This will assign new ids to the edges. After calling this method, the
        edges will be numbered consecutively, with
        the order imposed by the given key function. If the key function is
        None, no order is guaranteed.
        :param key_function: the key function to order the edges by
        """
        raise NotImplementedError

    @abstractmethod
    def getOutgoingEdges(self, node: N) -> List[E]:
        """
        Get a collection of the outgoing edges for the given node. If the
        graph is undirected, all incident edges
        to the given node will be included in the returned collection. Note
        that the returned collection is not a
        reference to the underlying graph structure, i.e., removing edges from
        it will not remove the edges from the
        graph.

        :param node: the node to get the outgoing edges for
        :return: all outgoing edges for the given node
        """
        raise NotImplementedError

    @abstractmethod
    def getIncomingEdges(self, node: N) -> List[E]:
        """
        Get a collection of the incoming edges for the given node. If the
        graph is undirected, all incident edges
        to the given node will be included in the returned collection. Note
        that the returned collection is not a
        reference to the underlying graph structure, i.e., removing edges from
        it will not remove the edges from the
        graph.
        :param node: the node to get the incoming edges for
        :return: all incoming edges for the given node
        """
        raise NotImplementedError

    @abstractmethod
    def getIncidentEdges(self, node: N) -> List[E]:
        """
        Get a collection of the incident edges for the given node. Note that
        the returned collection is not a
        reference to the underlying graph structure, i.e., removing edges from
        it will not remove the edges from the
        graph
        :param node: the node to get the incident edges for
        :return: all incident edges for the given node
        """
        raise NotImplementedError

    @abstractmethod
    def getNodes(self) -> List[N]:
        """
        Get a collection of all nodes in the graph. Note that the returned
        collection is not a
        reference to the underlying graph structure, i.e., removing nodes from
        it will not remove the nodes from the
        graph.
        :return: all nodes in the graph
        """
        raise NotImplementedError

    @abstractmethod
    def getEdges(self) -> List[E]:
        """
        Get a collection of all edges in the graph. Note that the returned
        collection is not a reference to the underlying graph structure, i.e.,
        removing edges from it will not remove the edges from the graph.
        :return: all edges in the graph
        """
        raise NotImplementedError

    @abstractmethod
    def isDirected(self) -> bool:
        """
        Get the information, whether the graph is directed, i.e., if it
        contains directed or undirected edges.
        Directed edges lead from left to right by convention, i.e. the left
        node is the source and the right node is
        the target of the edge.
        :return: whether the graph is directed
        """
        raise NotImplementedError

    def getAdjacentNodes(self, node: N) -> List[N]:
        """Get a list of all adjacent nodes of a given node.

        :param node: given to to determine all adjacent nodes
        :type node: N
        :return: list of all adjacent nodes to given node
        :rtype: List[N]
        """
        adjacent_nodes = []
        for edge in self.getIncidentEdges(node):
            if edge.getLeftNode().getId() == node.getId():
                adjacent_nodes.append(edge.getRightNode())
            else:
                adjacent_nodes.append(edge.getLeftNode())
        return adjacent_nodes


    def get_edge_from_nodes(self, left_node: N, right_node: N) -> Union[E, None]:
        """
        Get edge by its nodes. The optional will be empty, when the edge does not exist. For directed edges, the nodes
        must be provided in the correct order.
        :param left_node: the left node, i.e., the start, of the edge
        :param right_node: the right node, i.e., the end, of the edge
        :return: the corresponding edge, when it exists
        """
        return next(iter([edge for edge in self.getOutgoingEdges(left_node) if edge.getRightNode() == right_node or
                   (not edge.isDirected() and edge.getLeftNode() == right_node)]),
                    None)
