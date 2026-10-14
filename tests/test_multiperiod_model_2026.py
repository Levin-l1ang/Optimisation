import itertools
import math
import random
import unittest

import numpy as np
from scipy.optimize import linprog

from benchmark_2026.domain import CandidateLine, ODPair, Trip
from benchmark_2026.model import ModelConfig, PassengerModelData, estimate_model_size, solve
from benchmark_2026.multiperiod import generate

# ---------------------------------------------------------------- toy fixtures
# Stations A - B - C.  Directed connections: 0:A>B 1:B>A 2:B>C 3:C>B.


def line(line_id, cost=1.0, cap=10):
    return CandidateLine(line_id, ("A", "B", "C"), (0, 2), (3, 1), 2, 20.0, cost, 4, cap)


def trip(od, services, arcs, ride, key):
    wait = sum(30.0 / f for _l, f in services)
    return Trip(od[0], od[1], key, tuple(services), tuple(arcs), ride + wait, ride, wait, 0.0, 0)


def ab_trip(l, f, key="ab"):
    return trip(("A", "B"), [(l, f)], [(l, f, 0, "A", "B")], 10.0, f"{key}{l}{f}")


def ba_trip(l, f, key="ba"):
    return trip(("B", "A"), [(l, f)], [(l, f, 1, "B", "A")], 10.0, f"{key}{l}{f}")


def make(lines, demands, trips, freqs=(1, 2), budgets=None, K=100, M=1000.0, cap=10, cost=lambda l, f: 1 + f):
    budgets = budgets if budgets is not None else (100,) * len(demands)
    ids = [l.line_id for l in lines]
    data = PassengerModelData({l.line_id: l for l in lines}, tuple(demands), trips)
    cfg = ModelConfig({i: tuple(freqs) for i in ids}, {i: float(cap) for i in ids},
                      {(i, f): cost(i, f) for i in ids for f in freqs}, tuple(budgets), K, M, time_limit=30)
    return data, cfg


class UnservedDemandTest(unittest.TestCase):
    def test_od_without_trip_goes_to_u_and_model_is_feasible(self):
        data, cfg = make([line(0)], [{("A", "B"): 5.0, ("A", "C"): 7.0}], {("A", "B"): tuple(ab_trip(0, f) for f in (1, 2))},
                         budgets=(100,))
        r = solve(data, cfg)
        self.assertEqual(r.status, "optimal")
        self.assertAlmostEqual(r.unserved[0][("A", "C")], 7.0)
        self.assertAlmostEqual(r.service_rate[0], 5 / 12)
        self.assertAlmostEqual(r.unserved_cost, 7 * 1000)
        self.assertAlmostEqual(r.objective, r.travel_cost + r.unserved_cost)
        self.assertAlmostEqual(r.travel_cost, 5 * (10 + 15))   # f = 2 -> wait 15

    def test_budget_too_small_means_everything_unserved(self):
        data, cfg = make([line(0)], [{("A", "B"): 5.0}], {("A", "B"): (ab_trip(0, 1),)}, budgets=(1.5,))
        r = solve(data, cfg)
        self.assertAlmostEqual(r.unserved[0][("A", "B")], 5.0)
        self.assertEqual(r.selected_frequencies[0][0], 0)

    def test_m_must_be_explicit_and_positive(self):
        with self.assertRaises(TypeError):
            ModelConfig({0: (1,)}, {0: 1.0}, {(0, 1): 1.0}, (1.0,), 0.0)  # no default for M
        with self.assertRaises(ValueError):
            make([line(0)], [{("A", "B"): 1.0}], {("A", "B"): (ab_trip(0, 1),)}, M=0.0)

    def test_trip_cost_not_below_m_is_reported_not_hidden(self):
        data, cfg = make([line(0)], [{("A", "B"): 1.0}], {("A", "B"): (ab_trip(0, 1),)}, M=20.0)  # c_p = 40
        with self.assertRaises(ValueError):
            solve(data, cfg)
        import dataclasses
        r = solve(data, dataclasses.replace(cfg, allow_trip_cost_above_penalty=True))
        self.assertFalse(r.metadata["max_trip_cost_below_M"])
        self.assertAlmostEqual(r.unserved[0][("A", "B")], 1.0)    # cheaper to leave unserved


class CapacityTest(unittest.TestCase):
    def test_capacity_binds_per_direction_not_in_total(self):
        trips = {("A", "B"): (ab_trip(0, 1),), ("B", "A"): (ba_trip(0, 1),)}
        data, cfg = make([line(0)], [{("A", "B"): 10.0, ("B", "A"): 10.0}], trips, freqs=(1,), cap=10)
        r = solve(data, cfg)
        self.assertAlmostEqual(r.service_rate[0], 1.0)             # 10 each way fits: directions are separate

    def test_same_direction_demand_exceeding_capacity_is_partly_unserved(self):
        trips = {("A", "B"): (ab_trip(0, 1),)}
        data, cfg = make([line(0)], [{("A", "B"): 14.0}], trips, freqs=(1,), cap=10)
        r = solve(data, cfg)
        self.assertAlmostEqual(r.unserved[0][("A", "B")], 4.0)

    def test_higher_frequency_raises_capacity(self):
        trips = {("A", "B"): tuple(ab_trip(0, f) for f in (1, 2))}
        data, cfg = make([line(0)], [{("A", "B"): 14.0}], trips, freqs=(1, 2), cap=10)
        r = solve(data, cfg)
        self.assertEqual(r.selected_frequencies[0][0], 2)
        self.assertAlmostEqual(r.service_rate[0], 1.0)

    def test_shared_arc_is_counted_across_ods(self):
        # A>C and A>B both use connection 0 of line 0
        t_ac = trip(("A", "C"), [(0, 1)], [(0, 1, 0, "A", "B"), (0, 1, 2, "B", "C")], 20.0, "ac")
        trips = {("A", "B"): (ab_trip(0, 1),), ("A", "C"): (t_ac,)}
        data, cfg = make([line(0)], [{("A", "B"): 6.0, ("A", "C"): 6.0}], trips, freqs=(1,), cap=10)
        r = solve(data, cfg)
        self.assertAlmostEqual(sum(r.unserved[0].values()), 2.0)


class SimilarityAndBudgetTest(unittest.TestCase):
    def setUp(self):
        self.trips = {("A", "B"): tuple(ab_trip(0, f) for f in (1, 2))}
        self.demands = [{("A", "B"): 4.0}, {("A", "B"): 18.0}]

    # b(f=1) = 2, b(f=2) = 3 with the default cost; period 0 can only afford f = 1.
    def test_k_zero_forces_equal_frequencies(self):
        data, cfg = make([line(0)], self.demands, self.trips, budgets=(2.0, 100.0), K=0, M=1000.0)
        f = [p[0] for p in solve(data, cfg).selected_frequencies]
        self.assertEqual(f[0], f[1])

    def test_large_k_allows_change_and_costs_less(self):
        tight = solve(*make([line(0)], self.demands, self.trips, budgets=(2.0, 100.0), K=0))
        loose = solve(*make([line(0)], self.demands, self.trips, budgets=(2.0, 100.0), K=100))
        self.assertLess(loose.objective, tight.objective - 1e-6)
        self.assertEqual([p[0] for p in loose.selected_frequencies], [1, 2])
        mid = solve(*make([line(0)], self.demands, self.trips, budgets=(2.0, 100.0), K=1))
        self.assertAlmostEqual(mid.objective, loose.objective, places=6)   # one unit of change is enough

    def test_period_budget_is_respected(self):
        data, cfg = make([line(0)], self.demands, self.trips, budgets=(3.0, 100.0), K=100)   # b(f=2)=3, b(1)=2
        r = solve(data, cfg)
        for t, freq in enumerate(r.selected_frequencies):
            used = sum(cfg.operating_costs[(0, f)] for l, f in freq.items() if f)
            self.assertLessEqual(used, cfg.budgets[t] + 1e-9)


class SingleVersusTwoPeriodConsistency(unittest.TestCase):
    def test_identical_periods_without_similarity_double_the_single_period_objective(self):
        trips = {("A", "B"): tuple(ab_trip(0, f) for f in (1, 2)), ("B", "A"): tuple(ba_trip(0, f) for f in (1, 2))}
        d = {("A", "B"): 12.0, ("B", "A"): 9.0}
        one = solve(*make([line(0)], [d], trips, budgets=(10,)))
        two = solve(*make([line(0)], [d, d], trips, budgets=(10, 10), K=100))
        self.assertAlmostEqual(two.objective, 2 * one.objective, places=6)
        three = solve(*make([line(0)], [d, d, d], trips, budgets=(10, 10, 10), K=100))
        self.assertAlmostEqual(three.objective, 3 * one.objective, places=6)


def brute_force(data, cfg):
    """Enumerate every frequency plan, solve the flow LP exactly, return the best objective."""
    lines = sorted(data.lines)
    periods = len(data.demands)
    options = [(0, *cfg.frequencies[l]) for l in lines]
    per_period = []
    for t in range(periods):
        plans = []
        for plan in itertools.product(*options):
            cost = sum(cfg.operating_costs[(l, f)] for l, f in zip(lines, plan) if f)
            if cost <= cfg.budgets[t] + 1e-9:
                plans.append(plan)
        per_period.append(plans)

    def flow_cost(t, plan):
        active = dict(zip(lines, plan))
        cols, costs, ub = [], [], []
        demand = data.demands[t]
        pairs = sorted(demand)
        index = {}
        for pair in pairs:
            for i, tr in enumerate(data.trips_of(pair)):
                if all(active[l] == f for l, f in tr.services):
                    index[(pair, i)] = len(cols); cols.append((pair, i)); costs.append(tr.generalized_time); ub.append(demand[pair])
        u_index = {}
        for pair in pairs:
            u_index[pair] = len(cols); cols.append((pair, "u")); costs.append(cfg.unserved_penalty); ub.append(demand[pair])
        A_eq = np.zeros((len(pairs), len(cols))); b_eq = []
        for r, pair in enumerate(pairs):
            A_eq[r, u_index[pair]] = 1
            for (p, i), c in index.items():
                if p == pair:
                    A_eq[r, c] = 1
            b_eq.append(demand[pair])
        rows, rhs, seen = [], [], {}
        for (pair, i), c in index.items():
            for l, f, cid, _a, _b in data.trips_of(pair)[i].service_arcs:
                seen.setdefault((l, cid), []).append((c, cfg.capacities[l] * f))
        for (_l, _cid), items in seen.items():
            row = np.zeros(len(cols))
            for c, _cap in items:
                row[c] = 1
            rows.append(row); rhs.append(items[0][1])
        res = linprog(costs, A_ub=np.array(rows) if rows else None, b_ub=rhs if rows else None, A_eq=A_eq, b_eq=b_eq,
                      bounds=[(0, u) for u in ub], method="highs")
        return res.fun

    costs = [{plan: flow_cost(t, plan) for plan in per_period[t]} for t in range(periods)]
    best = math.inf
    for combo in itertools.product(*per_period):
        if all(sum(abs(a - b) for a, b in zip(combo[t], combo[t + 1])) <= cfg.similarity_limit + 1e-9
               for t in range(periods - 1)):
            best = min(best, sum(costs[t][combo[t]] for t in range(periods)))
    return best


class BruteForceAgreement(unittest.TestCase):
    def test_random_tiny_instances_match_exhaustive_enumeration(self):
        rng = random.Random(7)
        for case in range(6):
            lines = [line(0, cap=8), line(1, cap=8)]
            trips = {}
            for pair, (a, b, cid) in {("A", "B"): ("A", "B", 0), ("B", "A"): ("B", "A", 1)}.items():
                trips[pair] = tuple(
                    trip(pair, [(l, f)], [(l, f, cid, a, b)], 10.0 + 3 * l, f"{pair}{l}{f}") for l in (0, 1) for f in (1, 2))
            # a two-leg A>C trip changing between the two lines at B
            trips[("A", "C")] = tuple(
                trip(("A", "C"), [(0, f0), (1, f1)], [(0, f0, 0, "A", "B"), (1, f1, 2, "B", "C")], 35.0, f"ac{f0}{f1}")
                for f0 in (1, 2) for f1 in (1, 2))
            demands = [{p: rng.uniform(2, 12) for p in trips} for _ in range(2)]
            demands[1] = {p: max(demands[0][p], demands[1][p]) for p in trips}
            data, cfg = make(lines, demands, trips, budgets=(rng.choice([2.5, 4.0, 5.0, 7.0]),) * 2,
                             K=rng.choice([0, 1, 2, 4]), M=500.0, cap=8)
            self.assertAlmostEqual(solve(data, cfg).objective, brute_force(data, cfg), places=5, msg=f"case {case}")


class ModelSizeTest(unittest.TestCase):
    def test_size_formula_matches_builder_counts(self):
        trips = {("A", "B"): tuple(ab_trip(0, f) for f in (1, 2)), ("B", "A"): tuple(ba_trip(0, f) for f in (1, 2))}
        data, cfg = make([line(0)], [{("A", "B"): 3.0, ("B", "A"): 4.0}] * 2, trips)
        size = estimate_model_size(data, cfg)
        self.assertEqual(size.binary_variables, 2 * 2)
        self.assertEqual(size.continuous_flow_variables, 2 * 4)
        self.assertEqual(size.continuous_unserved_variables, 2 * 2)
        self.assertEqual(size.continuous_delta_variables, 1)


class MultiPeriodDemandTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ods = tuple(ODPair(f"S{i}", f"T{i}", 10.0 + i) for i in range(50))

    def test_two_period_properties(self):
        d = generate(self.ods, 2, seed=1)
        r = np.array(d.ratios[0])
        self.assertTrue(((r >= 0.5) & (r <= 0.9)).all())
        self.assertTrue((np.array(d.ratios[1]) == 1.0).all())
        for key in d.demands()[0]:
            self.assertGreater(d.demands()[0][key], 0)
            self.assertLessEqual(d.demands()[0][key], d.demands()[1][key])
        self.assertEqual(len(d.demands()[0]), 50)

    def test_seed_reproducibility_and_sensitivity(self):
        self.assertEqual(generate(self.ods, 2, 5).ratios, generate(self.ods, 2, 5).ratios)
        self.assertNotEqual(generate(self.ods, 2, 5).ratios, generate(self.ods, 2, 6).ratios)

    def test_three_period_sorted_monotone(self):
        d = generate(self.ods, 3, seed=3)
        a, b = np.array(d.ratios[0]), np.array(d.ratios[1])
        self.assertTrue((a <= b).all() and (b <= 1.0).all() and (a >= 0.5).all() and (b <= 0.9).all())


if __name__ == "__main__":
    unittest.main()
