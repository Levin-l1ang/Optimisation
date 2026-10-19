
import unittest
from types import SimpleNamespace

from scripts.tabu_frequency_plan import feasible, similarity


def toy_config(operating_costs, budgets=(10.0, 10.0), K=10):
    return SimpleNamespace(
        operating_costs=operating_costs,
        budgets=budgets,
        similarity_limit=K,
    )


class TabuFrequencyPlanTest(unittest.TestCase):
    def test_identical_plans_have_zero_similarity(self):
        plan = [
            {0: 2, 1: 3},
            {0: 2, 1: 3},
        ]
        self.assertEqual(similarity(plan), 0)

    def test_similarity_counts_frequency_changes_and_closed_lines(self):
        plan = [
            {0: 3, 1: 2},
            {0: 1},
        ]
        # Line 0 changes by 2; line 1 closes, contributing 2.
        self.assertEqual(similarity(plan), 4)

    def test_feasible_plan_respects_budget_and_similarity(self):
        cfg = toy_config(
            operating_costs={(0, 1): 2.0, (0, 2): 3.0},
            budgets=(5.0, 5.0),
            K=2,
        )
        plan = [{0: 1}, {0: 2}]
        self.assertTrue(feasible(plan, cfg))

    def test_over_budget_plan_is_infeasible(self):
        cfg = toy_config(
            operating_costs={(0, 2): 6.0},
            budgets=(5.0, 5.0),
            K=10,
        )
        plan = [{0: 2}, {0: 2}]
        self.assertFalse(feasible(plan, cfg))

    def test_similarity_limit_violation_is_infeasible(self):
        cfg = toy_config(
            operating_costs={(0, 1): 1.0, (0, 4): 1.0},
            budgets=(5.0, 5.0),
            K=2,
        )
        plan = [{0: 1}, {0: 4}]
        self.assertFalse(feasible(plan, cfg))
    
    def test_similarity_limit_applies_per_transition(self):
        cfg = toy_config(
            operating_costs={(0, 1): 1.0, (0, 3): 1.0},
            budgets=(5.0, 5.0, 5.0),
            K=2,
        )
        plan = [{0: 1}, {0: 3}, {0: 1}]

        self.assertEqual(similarity(plan), 2)
        self.assertTrue(feasible(plan, cfg))
if __name__ == "__main__":
    unittest.main()
