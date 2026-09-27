from abc import ABCMeta, abstractmethod
from typing import TypeVar, Generic, Sequence, Iterable

from core.model.graph import Edge, Node

N = TypeVar('N', bound=Node)
E = TypeVar('E', bound=Edge)


class Path(Generic[N, E]):
    """
    Abstract class for a possibly directed Path in a Graph
    """
    __metaclass__ = ABCMeta

    @abstractmethod
    def getNodes(self) -> Sequence[N]:
        """
        Get a list of the nodes of this path. Note that this returns a copy,
        i.e., appending or deleting nodes to the
        returned list will not be reflected in the path.
        :return: a list of the nodes
        """
        raise NotImplementedError

    @abstractmethod
    def getEdges(self) -> Sequence[E]:
        """
        Get a list of the edges of this path. Note that this returns a copy,
        i.e., appending or deleting edges to the
        returned list will not be reflected in the path.
        :return: a list of the edges
        """
        raise NotImplementedError

    @abstractmethod
    def isDirected(self) -> bool:
        """
        Return whether the path is directed, i.e., whether all the edges are
        traversed in "forward" direction or not
        :return: whether the path is directed
        """
        raise NotImplementedError

    @abstractmethod
    def addFirst(self, edges: Sequence[E]) -> bool:
        """
        Add a list of edges to the beginning of the path. Note, that the first
        element in the given list will be the
        first element of the path after the addition.
        :param edges: a list of edges to add to the beginning of the path
        :return: whether all edges could be added
        """
        raise NotImplementedError

    @abstractmethod
    def addLastEdge(self, edge: E) -> bool:
        """
        Add the given edge to the end of the path.
        :param edge: the edge to add
        :return: whether the edge could be added
        """
        raise NotImplementedError

    @abstractmethod
    def addFirstEdge(self, edge: E) -> bool:
        """
        Add the given edge to the start of the path.
        :param edge: the edge to add
        :return: whether the edge could be added
        """
        raise NotImplementedError

    @abstractmethod
    def addLast(self, edges: Sequence[E]) -> bool:
        """
        Add a list of edges to the end of the path. Note, that the last element
        in the given list will be the
        last element of the path after the addition.
        :param edges: a list of edges to add to the end of the path
        :return: whether all edges could be added
        """
        raise NotImplementedError

    @abstractmethod
    def remove(self, edges: Iterable[E]) -> bool:
        """
        Will remove the given edges from the path, if possible. Currently only
        supported for the beginning or the end
        of a path. Will raise otherwise
        :param edges: the edges to remove
        :return: whether the edges could be removed
        """
        raise NotImplementedError

    def contains(self, sub_path: 'Path[N, E]') -> bool:
        """
        Check if the given path is contained in this path. Considers whether
        this path is directed, i.e., if this path
        is directed, the given sub_path must be contained in forward direction
        :param sub_path: the path to check
        :return: whether sub_path is contained in this path
        """
        raise NotImplementedError

    def containsEdge(self, edge: E) -> bool:
        """
        Check, whether this path contains the given edge
        :param edge:
        :return:
        """
        return edge in self.getEdges()

    def containsNode(self, node: N) -> bool:
        """
        Check, whether this path contains the given node.
        :param node: the node to check
        :return: whether this path contains the given node
        """
        return node in self.getNodes()

    def __eq__(self, other) -> bool:
        if not isinstance(other, Path):
            return False
        return (self.getNodes() == other.getNodes() and
                self.getEdges() == other.getEdges())

    def __ne__(self, other) -> bool:
        return not self.__eq__(other)

    def __str__(self) -> str:
        return "Path (directed {}):\nNodes:\n".format(self.isDirected()) + \
               "\n".join([str(node) for node in self.getNodes()]) + \
               "\nEdges:\n" + "\n".join([str(edge) for edge in self.getEdges()])
