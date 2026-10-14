import unittest
from pathlib import Path

from passenger_line_planning import (
    ModelConfig,
    PassengerModelData,
    RoutingConfig,
    TripExpansionConfig,
    audit_dataset,
    estimate_model_size,
    expand_itinerary,
    load_lintim_dataset,
    route_demands,
    solve,
)


ROOT = Path(__file__).resolve().parents[1]


class PassengerLinePlanningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dataset = load_lintim_dataset(ROOT / "datasets/passenger-toy")
        cls.frequencies = {line_id: (1, 2) for line_id in cls.dataset.lines}
        cls.routing = RoutingConfig(
            k_ptn_paths=1,
            k_itineraries=6,
            max_transfers=2,
            transfer_penalty=5,
        )
        cls.expansion = TripExpansionConfig(
            cls.frequencies, period_length=60, beta=5
        )

    def test_lintim_loader_preserves_order_and_audits(self):
        self.assertFalse(self.dataset.directed)
        self.assertEqual(self.dataset.lines[1].stop_ids, (1, 2, 3, 4))
        report = audit_dataset(self.dataset)
        self.assertEqual((report["stops"], report["edges"], report["lines"]), (4, 5, 5))
        self.assertEqual(report["positive_od_pairs"], 2)
        self.assertEqual(report["unserved_edges"], [5])

    def test_routing_generates_line_itineraries_not_only_ptn_paths(self):
        routed = {pair: itineraries for pair, _, itineraries in route_demands(self.dataset, self.routing)}
        signatures = [itinerary.signature for itinerary in routed[(1, 4)]]
        self.assertIn(((1, 1, 4),), signatures)
        self.assertIn(((2, 1, 2), (3, 2, 4)), signatures)
        self.assertTrue(all(itinerary.ride_time == 12 for itinerary in routed[(1, 4)]))

    def test_frequency_expansion_precomputes_waiting_time(self):
        itinerary = next(
            itinerary
            for pair, _, itineraries in route_demands(self.dataset, self.routing)
            if pair == (1, 4)
            for itinerary in itineraries
            if itinerary.signature == ((1, 1, 4),)
        )
        trips = expand_itinerary(itinerary, self.expansion)
        self.assertEqual(len(trips), 2)
        self.assertEqual({trip.waiting_time for trip in trips}, {30.0, 15.0})
        self.assertEqual({trip.generalized_time for trip in trips}, {42.0, 27.0})
        self.assertTrue(all(len(trip.service_arcs) == 3 for trip in trips))

    def _model(self, similarity_limit=1):
        trips = {}
        for pair, _, itineraries in route_demands(self.dataset, self.routing):
            direct = min(itineraries, key=lambda itinerary: (itinerary.transfers, itinerary.signature))
            trips[pair] = expand_itinerary(direct, self.expansion)
        demands = (
            {pair: value * 0.5 for pair, value in self.dataset.demand.items()},
            dict(self.dataset.demand),
        )
        data = PassengerModelData(self.dataset.lines, demands, trips)
        config = ModelConfig(
            frequencies=self.frequencies,
            capacities={line_id: 5 for line_id in self.dataset.lines},
            operating_costs={
                (line_id, frequency): self.dataset.lines[line_id].base_cost * frequency
                for line_id in self.dataset.lines
                for frequency in self.frequencies[line_id]
            },
            budgets=(10, 20),
            similarity_limit=similarity_limit,
            time_limit=10,
        )
        return data, config

    def test_milp_enforces_capacity_activation_and_similarity(self):
        data, config = self._model()
        size = estimate_model_size(data, config)
        self.assertEqual(size.binary_variables, 20)
        self.assertEqual(size.continuous_flow_variables, 8)
        result = solve(data, config)
        self.assertEqual(result.status, "optimal", result.message)
        self.assertAlmostEqual(result.objective, 372.0)
        self.assertEqual(result.selected_frequencies[0][1], 1)
        self.assertEqual(result.selected_frequencies[1][1], 2)
        self.assertLessEqual(
            sum(
                abs(result.selected_frequencies[1][line] - result.selected_frequencies[0][line])
                for line in self.dataset.lines
            ),
            1,
        )
        for period, demand in enumerate(data.demands):
            for pair, value in demand.items():
                assigned = sum(
                    flow
                    for (flow_pair, _trip_id), flow in result.passenger_flows[period].items()
                    if flow_pair == pair
                )
                self.assertAlmostEqual(assigned, value)

    def test_tight_similarity_and_budget_can_make_model_infeasible(self):
        data, config = self._model(similarity_limit=0)
        result = solve(data, config)
        self.assertEqual(result.status, "infeasible")


if __name__ == "__main__":
    unittest.main()
