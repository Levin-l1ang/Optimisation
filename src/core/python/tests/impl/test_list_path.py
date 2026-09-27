import unittest

from core.model.impl.list_path import ListPath
from tests.test_path import PathTest


class ListPathTest(PathTest, unittest.TestCase):

    def setDirectedPath(self):
        self.path = ListPath(True)

    def setUndirectedPath(self):
        self.path = ListPath(False)