import unittest

from benchmark_2026 import paths
from benchmark_2026.pipeline import frequency_options, load_primary
from benchmark_2026.trip_expansion import TripExpansionConfig, expand_itinerary, expansion_count


class PassengerRoutesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.P = load_primary()
        cls.pool = cls.P.route_pool
        cls.lines = {l.line_id: l for l in cls.P.lines}
        cls.conn = cls.P.instance.connections

    def test_no_od_is_silently_dropped(self):
        d = self.pool.diagnostics
        self.assertEqual(d.od_pairs, 416)
        self.assertEqual(len(self.pool.itineraries) + len(d.od_without_route), 416)
        self.assertEqual(d.od_without_route, [])           # recorded: none for the primary instance
        self.assertEqual(d.window_insufficient, [])
        self.assertEqual(d.ride_time_mismatches, 0)

    def test_every_itinerary_uses_only_frozen_lines_and_is_served_by_them(self):
        for key, its in self.pool.itineraries.items():
            self.assertTrue(its)
            for it in its:
                self.assertEqual((it.origin, it.destination), key)
                self.assertEqual(it.legs[0].board_station, key[0])
                self.assertEqual(it.legs[-1].alight_station, key[1])
                for leg in it.legs:
                    line = self.lines[leg.line_id]
                    ids = tuple(a[0] for a in leg.arcs)
                    n = len(ids)
                    served = any(seq[i:i + n] == ids for seq in (line.connections_forward, line.connections_backward)
                                 for i in range(len(seq) - n + 1))
                    self.assertTrue(served, (key, leg))

    def test_plus_15_policy_relative_to_shortest_realizable(self):
        tp = self.P.instance.parameters.transfer_penalty
        for key, its in self.pool.itineraries.items():
            best = self.pool.shortest_realizable[key]
            durations = [it.ride_time + tp * it.transfers for it in its]
            self.assertAlmostEqual(min(durations), best, places=6)
            self.assertLessEqual(max(durations), best + 15 + 1e-6)
            self.assertGreaterEqual(best, self.pool.infrastructure_shortest[key] - 1e-6)

    def test_realizable_shortest_can_exceed_infrastructure_shortest(self):
        self.assertGreater(self.pool.diagnostics.od_shortest_realizable_above_infrastructure, 0)

    def test_transfer_count_limit_of_reference_generator(self):
        self.assertLessEqual(max(it.transfers for its in self.pool.itineraries.values() for it in its), 2)

    def test_no_dominated_same_line_transfer_remains(self):
        for its in self.pool.itineraries.values():
            for it in its:
                for a, b in zip(it.legs, it.legs[1:]):
                    if a.line_id == b.line_id:
                        line = self.lines[a.line_id]
                        merged = tuple(x[0] for x in a.arcs + b.arcs)
                        n = len(merged)
                        for seq in (line.connections_forward, line.connections_backward):
                            self.assertFalse(any(seq[i:i + n] == merged for i in range(len(seq) - n + 1)))

    def test_capacity_incidence_carries_service_direction(self):
        freqs = frequency_options(self.P.lines, (1,))
        cfg = TripExpansionConfig(freqs, 15)
        # an OD and its opposite use opposite directed connection ids on the same line
        fwd = self.pool.itineraries[("Alm", "Amf")][0]
        back = self.pool.itineraries[("Amf", "Alm")][0]
        tf, tb = expand_itinerary(fwd, cfg)[0], expand_itinerary(back, cfg)[0]
        self.assertEqual({a[2] for a in tf.service_arcs} & {a[2] for a in tb.service_arcs}, set())
        for l, f, cid, a, b in tf.service_arcs:
            self.assertEqual((self.conn[cid].from_station, self.conn[cid].to_station), (a, b))

    def test_trip_ids_are_deterministic_and_unique(self):
        cfg = TripExpansionConfig(frequency_options(self.P.lines, (1, 2)), 15)
        its = self.pool.itineraries[("Alm", "Amf")]
        first = [t.trip_id for it in its for t in expand_itinerary(it, cfg)]
        again = [t.trip_id for it in load_primary().route_pool.itineraries[("Alm", "Amf")] for t in expand_itinerary(it, cfg)]
        self.assertEqual(first, again)
        self.assertEqual(len(first), len(set(first)))

    def test_generalized_time_formula(self):
        cfg = TripExpansionConfig(frequency_options(self.P.lines, (1, 2, 3)), 15)
        it = next(it for its in self.pool.itineraries.values() for it in its if it.transfers == 1)
        for t in expand_itinerary(it, cfg):
            waits = sum(60 / (2 * f) for _l, f in t.services)
            self.assertAlmostEqual(t.generalized_time, it.ride_time + waits + 15)
        self.assertEqual(len(expand_itinerary(it, cfg)), expansion_count(it, cfg))

    def test_max_trip_cost_is_below_primary_penalty(self):
        worst = max(it.ride_time + 15 * it.transfers + 30 * len(it.legs)
                    for its in self.pool.itineraries.values() for it in its)  # all legs at f = 1
        self.assertLess(worst, 242)
        self.assertEqual(self.P.penalty["M"], 242)


if __name__ == "__main__":
    unittest.main()
