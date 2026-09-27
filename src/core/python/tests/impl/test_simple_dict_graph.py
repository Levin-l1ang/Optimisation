import unittest

from core.model.impl.simple_dict_graph import SimpleDictGraph
from tests.test_graph import GraphTest


class SimpleDictGraphTest(GraphTest, unittest.TestCase):
    def setUp(self):
        self.graph = SimpleDictGraph()