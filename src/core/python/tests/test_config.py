import logging
import unittest

from core.solver.generic_solver_interface import SolverType
from core.util.config import Config


class ConfigTest(unittest.TestCase):

    def test_add_values(self):
        config = Config()
        config.put("test", "abc")
        config.put("test2", 2)
        config.put("test3", True)
        self.assertEqual(3, len(config.data))
        config.put("test3", 5.3)
        self.assertEqual(3, len(config.data))

    def test_read_values(self):
        config = Config()
        config.put("test", "abc")
        config.put("test2", 2)
        config.put("test3", True)
        config.put("test4", 5.3)
        config.put("test5", "FATAL")
        config.put("test6", "XPRESS")
        self.assertEqual("abc", config.getStringValue("test"))
        self.assertEqual(2, config.getIntegerValue("test2"))
        self.assertEqual(True, config.getBooleanValue("test3"))
        self.assertEqual(5.3, config.getDoubleValue("test4"))
        self.assertEqual(logging.CRITICAL, config.getLogLevel("test5"))
        self.assertEqual(SolverType.XPRESS, config.getSolverType("test6"))

