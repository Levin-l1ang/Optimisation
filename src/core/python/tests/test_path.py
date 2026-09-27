import unittest
from abc import abstractmethod, ABCMeta

from core.model.impl.list_path import ListPath
from tests.graph_impl import AbstractNode, AbstractEdge


class PathTest(metaclass=ABCMeta):

    @abstractmethod
    def setDirectedPath(self):
        raise NotImplementedError

    @abstractmethod
    def setUndirectedPath(self):
        raise NotImplementedError

    # ------------------------------------------------------------------ #
    #  Helper methods                                                      #
    # ------------------------------------------------------------------ #

    def _build_directed_path(self):
        """Build a directed path n1->n2->n3->n4->n5 and return its nodes and edges."""
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.path.addLast([edge1, edge2, edge3, edge4])
        return (node1, node2, node3, node4, node5), (edge1, edge2, edge3, edge4)

    def _build_undirected_path(self):
        """Build an undirected path n1-n2-n3-n4-n5 and return its nodes and edges."""
        self.setUndirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.path.addLast([edge1, edge2, edge3, edge4])
        return (node1, node2, node3, node4, node5), (edge1, edge2, edge3, edge4)

    # ------------------------------------------------------------------ #
    #  contains – directed                                                 #
    # ------------------------------------------------------------------ #

    def test_contains_empty_subpath_directed(self):
        """An empty sub-path is always contained in a directed path."""
        self._build_directed_path()
        empty = ListPath(True)
        self.assertTrue(self.path.contains(empty))

    def test_contains_self_directed(self):
        """A directed path contains itself."""
        _, edges = self._build_directed_path()
        sub = ListPath(True)
        sub.addLast(list(edges))
        self.assertTrue(self.path.contains(sub))

    def test_contains_subpath_at_start_directed(self):
        """A matching sub-path at the beginning of a directed path is found."""
        _, edges = self._build_directed_path()
        sub = ListPath(True)
        sub.addLast([edges[0], edges[1]])
        self.assertTrue(self.path.contains(sub))

    def test_contains_subpath_at_end_directed(self):
        """A matching sub-path at the end of a directed path is found."""
        _, edges = self._build_directed_path()
        sub = ListPath(True)
        sub.addLast([edges[2], edges[3]])
        self.assertTrue(self.path.contains(sub))

    def test_contains_subpath_in_middle_directed(self):
        """A matching sub-path in the middle of a directed path is found."""
        _, edges = self._build_directed_path()
        sub = ListPath(True)
        sub.addLast([edges[1], edges[2]])
        self.assertTrue(self.path.contains(sub))

    def test_contains_reversed_subpath_not_found_directed(self):
        """A reversed sub-path is NOT found in a directed path."""
        nodes, _ = self._build_directed_path()
        # edge goes n3->n2, which is opposite to the path direction n2->n3
        reversed_edge = AbstractEdge(99, nodes[2], nodes[1])
        sub = ListPath(True)
        sub.addLast([reversed_edge])
        self.assertFalse(self.path.contains(sub))

    def test_contains_foreign_subpath_not_found_directed(self):
        """A sub-path with unknown edges returns False for a directed path."""
        self._build_directed_path()
        foreign_edge = AbstractEdge(99, AbstractNode(6), AbstractNode(7))
        sub = ListPath(True)
        sub.addLast([foreign_edge])
        self.assertFalse(self.path.contains(sub))

    def test_contains_longer_subpath_not_found_directed(self):
        """A sub-path longer than the path itself returns False (directed)."""
        _, edges = self._build_directed_path()
        extra_edge = AbstractEdge(99, AbstractNode(5), AbstractNode(6))
        sub = ListPath(True)
        sub.addLast(list(edges) + [extra_edge])
        self.assertFalse(self.path.contains(sub))

    # ------------------------------------------------------------------ #
    #  contains – undirected                                               #
    # ------------------------------------------------------------------ #

    def test_contains_empty_subpath_undirected(self):
        """An empty sub-path is always contained in an undirected path."""
        self._build_undirected_path()
        empty = ListPath(False)
        self.assertTrue(self.path.contains(empty))

    def test_contains_self_undirected(self):
        """An undirected path contains itself."""
        _, edges = self._build_undirected_path()
        sub = ListPath(False)
        sub.addLast(list(edges))
        self.assertTrue(self.path.contains(sub))

    def test_contains_subpath_forward_undirected(self):
        """A forward sub-path in the middle of an undirected path is found."""
        _, edges = self._build_undirected_path()
        sub = ListPath(False)
        sub.addLast([edges[1], edges[2]])
        self.assertTrue(self.path.contains(sub))

    def test_contains_subpath_reversed_undirected(self):
        """A reversed sub-path IS found in an undirected path."""
        _, edges = self._build_undirected_path()
        # Traverse edges[2] then edges[1]: n4-n3-n2 (reversed order)
        sub = ListPath(False)
        sub.addLast([edges[2], edges[1]])
        self.assertTrue(self.path.contains(sub))

    def test_contains_foreign_subpath_not_found_undirected(self):
        """A sub-path with unknown edges returns False for an undirected path."""
        self._build_undirected_path()
        foreign_edge = AbstractEdge(99, AbstractNode(6), AbstractNode(7))
        sub = ListPath(False)
        sub.addLast([foreign_edge])
        self.assertFalse(self.path.contains(sub))

    def test_contains_longer_subpath_not_found_undirected(self):
        """A sub-path longer than the path itself returns False (undirected)."""
        _, edges = self._build_undirected_path()
        extra_edge = AbstractEdge(99, AbstractNode(5), AbstractNode(6))
        sub = ListPath(False)
        sub.addLast(list(edges) + [extra_edge])
        self.assertFalse(self.path.contains(sub))

    # ------------------------------------------------------------------ #
    #  Existing tests (unchanged)                                          #
    # ------------------------------------------------------------------ #

    def test_add_edge_front_directed(self):
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertEqual(0, len(self.path.getEdges()))
        self.assertEqual(0, len(self.path.getNodes()))
        self.assertTrue(self.path.addFirstEdge(edge4))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertTrue(self.path.addFirstEdge(edge3))
        self.assertEqual(2, len(self.path.getEdges()))
        self.assertEqual(3, len(self.path.getNodes()))
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))

    def test_add_edge_end_directed(self):
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertEqual(0, len(self.path.getEdges()))
        self.assertEqual(0, len(self.path.getNodes()))
        self.assertTrue(self.path.addLastEdge(edge1))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertTrue(self.path.addLastEdge(edge2))
        self.assertEqual(2, len(self.path.getEdges()))
        self.assertEqual(3, len(self.path.getNodes()))
        self.assertTrue(self.path.addLast([edge3, edge4]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))

    def test_remove_edge_front_directed(self):
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertTrue(self.path.addFirstEdge(edge4))
        self.assertTrue(self.path.addFirstEdge(edge3))
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))
        self.assertTrue(self.path.removeEdge(edge1))
        self.assertEqual(3, len(self.path.getEdges()))
        self.assertEqual(4, len(self.path.getNodes()))
        self.assertTrue(self.path.remove([edge2, edge3]))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))

    def test_remove_edge_end_directed(self):
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertTrue(self.path.addFirstEdge(edge4))
        self.assertTrue(self.path.addFirstEdge(edge3))
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))
        self.assertTrue(self.path.removeEdge(edge4))
        self.assertEqual(3, len(self.path.getEdges()))
        self.assertEqual(4, len(self.path.getNodes()))
        self.assertTrue(self.path.remove([edge3, edge2]))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))

    def test_add_edge_front_undirected(self):
        self.setUndirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node2, node1)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node4, node3)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertEqual(0, len(self.path.getEdges()))
        self.assertEqual(0, len(self.path.getNodes()))
        self.assertTrue(self.path.addFirstEdge(edge4))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertTrue(self.path.addFirstEdge(edge3))
        self.assertEqual(2, len(self.path.getEdges()))
        self.assertEqual(3, len(self.path.getNodes()))
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))

    def test_add_edge_end_undirected(self):
        self.setUndirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node2, node1)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node4, node3)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertEqual(0, len(self.path.getEdges()))
        self.assertEqual(0, len(self.path.getNodes()))
        self.assertTrue(self.path.addLastEdge(edge1))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertTrue(self.path.addLastEdge(edge2))
        self.assertEqual(2, len(self.path.getEdges()))
        self.assertEqual(3, len(self.path.getNodes()))
        self.assertTrue(self.path.addLast([edge3, edge4]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))

    def test_remove_edge_front_undirected(self):
        self.setUndirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node2, node1)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node4, node3)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertTrue(self.path.addFirstEdge(edge4))
        self.assertTrue(self.path.addFirstEdge(edge3))
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))
        self.assertTrue(self.path.removeEdge(edge1))
        self.assertEqual(3, len(self.path.getEdges()))
        self.assertEqual(4, len(self.path.getNodes()))
        self.assertTrue(self.path.remove([edge2, edge3]))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))

    def test_remove_edge_end_undirected(self):
        self.setUndirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node2, node1)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node4, node3)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertTrue(self.path.addFirstEdge(edge4))
        self.assertTrue(self.path.addFirstEdge(edge3))
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(4, len(self.path.getEdges()))
        self.assertEqual(5, len(self.path.getNodes()))
        self.assertTrue(self.path.removeEdge(edge4))
        self.assertEqual(3, len(self.path.getEdges()))
        self.assertEqual(4, len(self.path.getNodes()))
        self.assertTrue(self.path.remove([edge2, edge3]))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))

    def test_cannot_add_unfitting_edge_directed(self):
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        edge1 = AbstractEdge(1, node2, node1)
        edge2 = AbstractEdge(2, node2, node3)
        self.assertTrue(self.path.addFirstEdge(edge1))
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertFalse(self.path.addFirstEdge(edge2))
        # The path must remain unchanged
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertEqual(1, len(self.path.getEdges()))

    def test_cannot_add_unfitting_edge_undirected(self):
        self.setUndirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        edge1 = AbstractEdge(2, node3, node2)
        edge2 = AbstractEdge(1, node2, node1)
        edge3 = AbstractEdge(3, node3, node4)
        self.assertTrue(self.path.addFirst([edge1, edge2]))
        self.assertEqual(3, len(self.path.getNodes()))
        self.assertEqual(2, len(self.path.getEdges()))
        self.assertFalse(self.path.addLastEdge(edge3))
        # The path must remain unchanged
        self.assertEqual(3, len(self.path.getNodes()))
        self.assertEqual(2, len(self.path.getEdges()))

    def test_will_reset_unfitting_path(self):
        self.setDirectedPath()
        node1 = AbstractNode(1)
        node2 = AbstractNode(2)
        node3 = AbstractNode(3)
        node4 = AbstractNode(4)
        node5 = AbstractNode(5)
        edge1 = AbstractEdge(1, node1, node2)
        edge2 = AbstractEdge(2, node2, node3)
        edge3 = AbstractEdge(3, node3, node4)
        edge4 = AbstractEdge(4, node4, node5)
        self.assertTrue(self.path.addFirstEdge(edge1))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))
        self.assertFalse(self.path.addLast([edge2, edge4]))
        self.assertEqual(1, len(self.path.getEdges()))
        self.assertEqual(2, len(self.path.getNodes()))