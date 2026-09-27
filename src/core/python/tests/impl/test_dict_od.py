import unittest

from core.model.impl.dictOD import DictOD
from core.model.od import OD
from tests.test_od import ODTest


class DictODTest(ODTest, unittest.TestCase):

    def getOd(self, size: int) -> OD:
        return DictOD()
