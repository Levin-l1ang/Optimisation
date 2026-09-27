from typing import Callable, TypeVar, Any, Dict, List

from core.model.graph import Graph, Node, Edge

N = TypeVar('N', bound=Node)
E = TypeVar('E', bound=Edge)


class DictGraph(Graph):
    """
    A base implementation of a graph, using dictionaries
    """

    def __init__(self):
        self.nodes = []  # type: List[N]
        self.edges = []  # type: List[E]
        self.node_indices = {}  # type: Dict[N, int]
        self.edge_indices = {}  # type: Dict[E, int]
        self.incident_edges = {}  # type: Dict[N, List[E]]

    T = TypeVar("T")

    @staticmethod
    def order_elements(element_list: [T], indices_map: Dict, key_function: Callable[[T], Any]) -> None:
        """
        Helper method to order elements of the graph. The elements in element_list will be sorted by key_function and
        the indices_map will be updated accordingly.
        :param element_list: the list of elements
        :param indices_map: the index map for the elements
        :param key_function: the function to sort the elements by
        """
        indices_map.clear()
        while None in element_list:
            element_list.remove(None)
        if key_function:
            element_list.sort(key=key_function)
        new_index = 0
        for element in element_list:
            indices_map[element] = new_index
            new_index += 1

    def orderNodes(self, key_function: Callable[[N], Any]) -> None:
        self.order_elements(self.nodes, self.node_indices, key_function)

    def orderEdges(self, key_function: Callable[[E], Any]) -> None:
        self.order_elements(self.edges, self.edge_indices, key_function)

    @staticmethod
    def add_element(element_list: [T], indices_map: Dict, element: T) -> bool:
        """
        Helper method to add elements to the graph. The element will be added to the list and the index map will be
        updated accordingly.
        :param element_list: the list of elements
        :param indices_map: the index map for the elements
        :param element: the element to add
        :return: whether the element could be added. Will not happen, if the element is already in the index map
        """
        if element in indices_map:
            return False
        new_index = len(element_list)
        element_list.append(element)
        indices_map[element] = new_index
        return True

    def addNode(self, node: N) -> bool:
        if not self.add_element(self.nodes, self.node_indices, node):
            return False
        self.incident_edges[node] = []
        return True

    def addEdge(self, edge: E) -> bool:
        left_node = edge.getLeftNode()
        if left_node not in self.node_indices:
            raise ValueError("The edges left node is not a member of the graph")
        right_node = edge.getRightNode()
        if right_node not in self.node_indices:
            raise ValueError("The edges right node is not a member of the graph")
        if not self.add_element(self.edges, self.edge_indices, edge):
            return False
        self.incident_edges[left_node].append(edge)
        self.incident_edges[right_node].append(edge)
        return True

    @staticmethod
    def remove_element(element_list: [T], indices_map: Dict[T, int], element: T) -> bool:
        """
        Helper method to remove elements from the graph. The element will be removed from the list and the index map
        will be updated accordingly.
        :param element_list: the list of elements
        :param indices_map: the index map for the elements
        :param element: the element to remove
        :return: if the element could be removed
        """
        index = indices_map.get(element)
        if index is None:
            return False
        element_list.remove(element)
        # element_list[index] = None
        del indices_map[element]
        return True

    def removeNode(self, node: N) -> bool:
        edges_to_remove = self.incident_edges[node].copy()
        for edge in edges_to_remove:
            self.removeEdge(edge)
        if not self.remove_element(self.nodes, self.node_indices, node):
            return False
        del self.incident_edges[node]
        return True

    def removeEdge(self, edge: E) -> bool:
        if not self.remove_element(self.edges, self.edge_indices, edge):
            return False
        self.incident_edges[edge.getLeftNode()].remove(edge)
        self.incident_edges[edge.getRightNode()].remove(edge)
        return True

    def get_node_by_function(self, search_function: Callable[[N], T], search_value: T) -> N:
        return next(iter([x for x in self.nodes if search_function(x) == search_value] or []), None)

    def getNode(self, search_id: int) -> N:
        return self.get_node_by_function((lambda x: x.getId()), search_id)

    def get_edge_by_function(self, search_function: Callable[[E], T], search_value: T) -> E:
        return next(iter([x for x in self.edges if search_function(x) == search_value] or []), None)

    def getEdge(self, search_id: int) -> E:
        return self.get_edge_by_function((lambda x: x.getId()), search_id)

    def getNodes(self) -> [N]:
        return [x for x in self.nodes if x]

    def getEdges(self) -> [E]:
        return [x for x in self.edges if x]

    def getIncidentEdges(self, node: N) -> [E]:
        return [x for x in self.incident_edges[node]]

    def getOutgoingEdges(self, node: N) -> [E]:
        return [x for x in self.incident_edges[node] if not x.isDirected() or x.getLeftNode() == node]

    def getIncomingEdges(self, node: N) -> [E]:
        return [x for x in self.incident_edges[node] if not x.isDirected() or x.getRightNode() == node]

    def isDirected(self) -> bool:
        return any([x and x.isDirected() for x in self.edges])
