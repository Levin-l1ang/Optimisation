import math
import sys
import unittest
from unittest import mock

import numpy as np

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import fake_gurobipy  # noqa: E402
from benchmark_2026.model import ModelConfig  # noqa: E402
from benchmark_2026.solvers import solve_gurobi  # noqa: E402
from test_compact_model_2026 import both, toy_itineraries, toy_line, TAU  # noqa: E402,F401
from benchmark_2026.model_compact import CompactData, solve_compact  # noqa: E402


def with_gurobi():
    return mock.patch.dict(sys.modules, {"gurobipy": fake_gurobipy.install()})


class AdapterContractTest(unittest.TestCase):
    def test_all_row_types_bounds_and_infinite_upper_bounds(self):
        # min x0 + 2 x1 + 3 b   s.t.  x0 + x1 >= 4 (>=),  x0 - b*10 <= 0 (<=),  x1 == 1 (equality), x0 free upper
        with with_gurobi():
            res = solve_gurobi([1, 2, 3], [0, 0, 0], [math.inf, math.inf, 1], [0, 0, 1],
                               [{0: 1, 1: 1}, {0: 1, 2: -10}, {1: 1}, {}],
                               [4, -math.inf, 1, -math.inf], [math.inf, 0, 1, math.inf])
        self.assertEqual(res.status, 0)
        np.testing.assert_allclose(res.x, [3, 1, 1])
        self.assertAlmostEqual(res.fun, 3 + 2 + 3)

    def test_infeasible_maps_to_status_2_and_has_no_solution(self):
        with with_gurobi():
            res = solve_gurobi([1], [0], [1], [0], [{0: 1}], [2], [math.inf])
        self.assertEqual(res.status, 2)
        self.assertIsNone(res.x)

    def test_warm_start_is_set_on_listed_variables_only(self):
        fake_gurobipy.Model.instances.clear()
        with with_gurobi():
            solve_gurobi([1, 1], [0, 0], [1, 1], [1, 1], [{0: 1, 1: 1}], [1], [math.inf], start={1: 1.0})
        # variables are not kept after dispose, so check through the recorded model
        model = fake_gurobipy.Model.instances[-1]
        self.assertEqual([v.Start for v in model.vars], [None, 1.0])
        self.assertTrue(model.disposed)

    def test_parameters_are_forwarded(self):
        fake_gurobipy.Model.instances.clear()
        with with_gurobi():
            solve_gurobi([1], [0], [1], [1], [], [], [], time_limit=12.5, mip_gap=0.02, threads=3)
        p = fake_gurobipy.Model.instances[-1].Params
        self.assertEqual((p.TimeLimit, p.MIPGap, p.Threads), (12.5, 0.02, 3))

    def test_missing_gurobipy_gives_a_helpful_error(self):
        with mock.patch.dict(sys.modules, {"gurobipy": None}):
            with self.assertRaises(RuntimeError) as ctx:
                solve_gurobi([1], [0], [1], [0], [], [], [])
        self.assertIn("pip install gurobipy", str(ctx.exception))


class ModelThroughGurobiTest(unittest.TestCase):
    def build(self, solver, plan=None):
        lines = {0: toy_line(0), 1: toy_line(1)}
        d = [{("A", "B"): 6.0, ("A", "C"): 5.0}, {("A", "B"): 7.0, ("A", "C"): 4.0}]
        cfg = ModelConfig({0: (1, 2), 1: (1, 2)}, {0: 8.0, 1: 8.0},
                          {(l, f): 1.0 + f for l in (0, 1) for f in (1, 2)}, (5.0, 5.0), 2, 400.0,
                          time_limit=30, solver=solver, initial_plan=plan)
        return solve_compact(CompactData(lines, tuple(d), toy_itineraries(), TAU), cfg)

    def test_compact_model_same_optimum_with_both_backends(self):
        ref = self.build("highs")
        with with_gurobi():
            got = self.build("gurobi", plan=({0: 2}, {0: 2}))
        self.assertEqual(got.status, "optimal")
        self.assertAlmostEqual(ref.objective, got.objective, places=6)
        self.assertEqual(ref.selected_frequencies, got.selected_frequencies)

    def test_invalid_solver_and_plan_are_rejected(self):
        with self.assertRaises(ValueError):
            ModelConfig({0: (1,)}, {0: 1.0}, {(0, 1): 1.0}, (1.0,), 0, 10.0, solver="cplex")


if __name__ == "__main__":
    unittest.main()
