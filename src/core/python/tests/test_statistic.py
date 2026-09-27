import unittest

from core.util.statistic import Statistic


class StatisticTest(unittest.TestCase):

    def test_add_values(self):
        statistic = Statistic()
        statistic.setValue("test", "abc")
        statistic.setValue("test2", 2)
        statistic.setValue("test3", True)
        self.assertEqual(3, len(statistic.data))
        statistic.setValue("test3", 5.3)
        self.assertEqual(3, len(statistic.data))

    def test_read_values(self):
        statistic = Statistic()
        statistic.setValue("test", "abc")
        statistic.setValue("test2", 2)
        statistic.setValue("test3", True)
        statistic.setValue("test4", 5.3)
        self.assertEqual("abc", statistic.getStringValue("test"))
        self.assertEqual(2, statistic.getIntegerValue("test2"))
        self.assertEqual(True, statistic.getBooleanValue("test3"))
        self.assertEqual(5.3, statistic.getDoubleValue("test4"))
