import unittest
import shutil
import tempfile
from pathlib import Path

import numpy as np

from multi_period_line_planning import (
    load_lintim_instance,
    prepare_germany_rail,
    solve_lower_bound,
    solve_mp_b,
    solve_mp_b_ext,
    solve_mp_b_iter,
    solve_mp_f,
    solve_mp_w,
    solve_upper_bound,
)
from passenger_line_planning import load_lintim_dataset


ROOT = Path(__file__).resolve().parents[1]


class MultiPeriodLinePlanningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.instance = load_lintim_instance(ROOT / "datasets/toy", periods=2, seed=2023)

    def test_lintim_loader_and_monotone_bounds(self):
        self.assertEqual(self.instance.edge_count, 8)
        self.assertEqual(self.instance.line_count, 8)
        self.assertEqual(self.instance.period_count, 2)
        self.assertTrue(
            all(a <= b for a, b in zip(*self.instance.lower_bounds))
        )
        self.assertTrue(
            all(a <= b for a, b in zip(*self.instance.upper_bounds))
        )

    def test_weighted_jaccard_line_distance(self):
        distances = self.instance.line_distance_matrix
        np.testing.assert_allclose(np.diag(distances), 0.0)
        np.testing.assert_allclose(distances, distances.T)
        self.assertTrue(np.all((distances >= 0.0) & (distances <= 1.0)))
        self.assertAlmostEqual(distances[0, 7], 3.8 / 5.8)

    def test_lower_bound_is_feasible(self):
        result = solve_lower_bound(self.instance)
        self.assertTrue(result.optimal, result.message)
        self.assertEqual(len(result.frequencies), 2)
        incidence = self.instance.incidence
        for period, frequencies in enumerate(result.frequencies):
            edge_frequencies = incidence @ np.asarray(frequencies)
            self.assertTrue(np.all(edge_frequencies >= self.instance.lower_bounds[period]))
            self.assertTrue(np.all(edge_frequencies <= self.instance.upper_bounds[period]))

    def test_upper_bound_is_no_better_than_lower_bound(self):
        lower = solve_lower_bound(self.instance)
        upper = solve_upper_bound(self.instance)
        self.assertTrue(upper.optimal, upper.message)
        self.assertGreaterEqual(upper.objective, lower.objective)
        self.assertEqual(upper.frequencies[0], upper.frequencies[1])

    def test_frequency_model_has_identical_active_lines_and_respects_k(self):
        result = solve_mp_f(self.instance, k=8)
        self.assertTrue(result.optimal, result.message)
        active = [tuple(value > 0 for value in period) for period in result.frequencies]
        self.assertEqual(active[0], active[1])
        self.assertLessEqual(result.dissimilarity, 8.0 + 1e-7)

    def test_binary_model_is_monotone(self):
        result = solve_mp_b(self.instance, k=2)
        self.assertTrue(result.optimal, result.message)
        self.assertTrue(
            all(a <= b for a, b in zip(result.frequencies[0], result.frequencies[1]))
        )
        self.assertLessEqual(result.dissimilarity, 2.0)

    def test_iterative_binary_model_is_monotone(self):
        result = solve_mp_b_iter(self.instance, k=1)
        self.assertTrue(result.optimal, result.message)
        self.assertTrue(
            all(a <= b for a, b in zip(result.frequencies[0], result.frequencies[1]))
        )

    def test_extended_binary_large_k_reaches_lower_bound(self):
        lower = solve_lower_bound(self.instance)
        extended = solve_mp_b_ext(self.instance, k=self.instance.line_count)
        self.assertTrue(extended.optimal, extended.message)
        self.assertAlmostEqual(extended.objective, lower.objective)

    def test_wasserstein_large_k_reaches_lower_bound(self):
        lower = solve_lower_bound(self.instance)
        wasserstein = solve_mp_w(self.instance, k=1000.0)
        self.assertTrue(wasserstein.optimal, wasserstein.message)
        self.assertAlmostEqual(wasserstein.objective, lower.objective)

    def test_relaxing_similarity_does_not_increase_cost(self):
        frequency_costs = [solve_mp_f(self.instance, k).objective for k in range(4)]
        wasserstein_costs = [solve_mp_w(self.instance, k).objective for k in range(4)]
        self.assertTrue(all(a + 1e-7 >= b for a, b in zip(frequency_costs, frequency_costs[1:])))
        self.assertTrue(all(a + 1e-7 >= b for a, b in zip(wasserstein_costs, wasserstein_costs[1:])))

    def test_germany_rail_reconstruction_is_deterministic(self):
        checked_in = ROOT / "datasets/germany-rail"
        with tempfile.TemporaryDirectory() as temporary:
            rebuilt = Path(temporary) / "germany-rail"
            (rebuilt / "timpasslib").mkdir(parents=True)
            for name in ("Activities.csv", "Events.csv"):
                shutil.copy(checked_in / "timpasslib" / name, rebuilt / "timpasslib" / name)
            prepared = prepare_germany_rail(
                rebuilt,
                ROOT / "src/simulation/data/bahn-01/OD.giv",
            )
            self.assertEqual((prepared.stops, prepared.edges, prepared.lines), (250, 326, 132))
            self.assertEqual(prepared.positive_od_pairs, 48842)
            reconstructed = load_lintim_dataset(rebuilt)
            self.assertFalse(reconstructed.directed)
            self.assertEqual(
                (len(reconstructed.stops), len(reconstructed.edges), len(reconstructed.lines)),
                (250, 326, 132),
            )
            self.assertEqual(
                (rebuilt / "basis/Pool.giv").read_text(encoding="utf-8"),
                (checked_in / "basis/Pool.giv").read_text(encoding="utf-8"),
            )


if __name__ == "__main__":
    unittest.main()
