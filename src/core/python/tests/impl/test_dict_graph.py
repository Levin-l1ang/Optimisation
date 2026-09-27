import unittest

from core.model.impl.dict_graph import DictGraph
from tests.test_graph import GraphTest


class DictGraphTest(GraphTest, unittest.TestCase):
    def setUp(self):
        self.graph = DictGraph()