import unittest

from core.model.impl.mapOD import MapOD
from core.model.od import OD
from tests.test_od import ODTest


class MapODTest(ODTest, unittest.TestCase):

    def getOd(self, size: int) -> OD:
        return MapOD()