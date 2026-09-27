import unittest

from core.model.impl.fullOD import FullOD
from core.model.od import OD
from tests.test_od import ODTest


class FullODTest(ODTest, unittest.TestCase):

    def getOd(self, size: int) -> OD:
        return FullOD(size)