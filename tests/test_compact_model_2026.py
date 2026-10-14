import random
import unittest

from benchmark_2026.domain import CandidateLine, Itinerary, Leg
from benchmark_2026.model import ModelConfig, PassengerModelData, solve
from benchmark_2026.model_compact import CompactData, estimate_compact_size, solve_compact
from benchmark_2026.trip_expansion import TripExpansionConfig, expand_itinerary

TAU = 15.0


def toy_line(i, cap=8):
    return CandidateLine(i, ("A", "B", "C"), (0, 2), (3, 1), 2, 20.0, 1.0, 4, cap)


def leg(line, arcs):
    return Leg(line, arcs[0][1], arcs[-1][2], tuple(arcs))


AB, BC, BA, CB = (0, "A", "B"), (2, "B", "C"), (1, "B", "A"), (3, "C", "B")


def toy_itineraries():
    its = {
        ("A", "B"): (Itinerary("A", "B", (leg(0, [AB]),), 10.0), Itinerary("A", "B", (leg(1, [AB]),), 13.0)),
        ("B", "A"): (Itinerary("B", "A", (leg(0, [BA]),), 10.0), Itinerary("B", "A", (leg(1, [BA]),), 13.0)),
        ("A", "C"): (Itinerary("A", "C", (leg(0, [AB]), leg(1, [BC])), 20.0),
                     Itinerary("A", "C", (leg(0, [AB, BC]),), 22.0)),
        ("C", "B"): (),                                   # an OD without any itinerary
    }
    return its


def both(demands, budgets, K, M, freqs=(1, 2), cap=8, itineraries=None):
    its = itineraries or toy_itineraries()
    lines = {0: toy_line(0, cap), 1: toy_line(1, cap)}
    cfg = ModelConfig({0: freqs, 1: freqs}, {0: float(cap), 1: float(cap)},
                      {(l, f): 1.0 + f for l in (0, 1) for f in freqs}, tuple(budgets), K, M, time_limit=30)
    exp = TripExpansionConfig({0: freqs, 1: freqs}, TAU)
    trips = {od: tuple(t for it in v for t in expand_itinerary(it, exp)) for od, v in its.items()}
    return (solve(PassengerModelData(lines, tuple(demands), trips), cfg),
            solve_compact(CompactData(lines, tuple(demands), its, TAU), cfg), cfg)


class CompactEquivalenceTest(unittest.TestCase):
    def test_random_toy_instances_give_identical_objectives(self):
        rng = random.Random(11)
        for case in range(15):
            ods = [("A", "B"), ("B", "A"), ("A", "C"), ("C", "B")]
            d1 = {od: rng.uniform(1, 14) for od in ods}
            d2 = {od: d1[od] * rng.uniform(1.0, 1.8) for od in ods}
            budgets = (rng.choice([2.5, 4.0, 5.5, 8.0, 12.0]),) * 2
            ref, compact, _ = both([d1, d2], budgets, rng.choice([0, 1, 2, 6]), 400.0, freqs=rng.choice([(1,), (1, 2), (1, 2, 3)]))
            self.assertAlmostEqual(ref.objective, compact.objective, places=5, msg=f"case {case}")
            self.assertAlmostEqual(ref.overall_service_rate, compact.overall_service_rate, places=5)

    def test_worked_example_from_the_documentation(self):
        # one OD, one 2-leg itinerary (a then b), ride 35, tau 15, D = 10, f in {1, 2}
        a = Itinerary("A", "C", (leg(0, [AB]), leg(1, [BC])), 35.0)
        ref, compact, _ = both([{("A", "C"): 10.0}], (100.0,), 0, 1000.0, itineraries={("A", "C"): (a,)}, cap=100)
        self.assertAlmostEqual(compact.objective, 10 * 80.0)    # both lines at f = 2: 35 + 15 + 15 + 15
        self.assertAlmostEqual(ref.objective, compact.objective)

    def test_unserved_and_penalty_check(self):
        ref, compact, cfg = both([{("C", "B"): 5.0, ("A", "B"): 3.0}], (100.0,), 0, 1000.0)
        self.assertAlmostEqual(compact.unserved[0][("C", "B")], 5.0)
        self.assertAlmostEqual(compact.unserved_cost, 5000.0)
        with self.assertRaises(ValueError):
            both([{("A", "B"): 1.0}], (100.0,), 0, 20.0)     # max c_p >= M must not be hidden

    def test_size_estimate_matches_model(self):
        its = toy_itineraries()
        lines = {0: toy_line(0), 1: toy_line(1)}
        cfg = ModelConfig({0: (1, 2), 1: (1, 2)}, {0: 8.0, 1: 8.0}, {(l, f): 1.0 for l in (0, 1) for f in (1, 2)}, (9.0,), 0, 500.0)
        size = estimate_compact_size(CompactData(lines, ({("A", "B"): 2.0, ("A", "C"): 3.0},), its, TAU), cfg)
        self.assertEqual(size.binary_variables, 4)
        self.assertEqual(size.continuous_unserved_variables, 2)


if __name__ == "__main__":
    unittest.main()
