# Tabu Search Results — Top-20 OD Instance

## Experimental setup

- **Method:** Tabu-search frequency planning, adapted from Martínez et al. (2014).
- **Instance:** Reduced benchmark containing the top 20 OD pairs.
- **Initial solution:** Greedy frequency plan.
- **Iterations:** 20 per run.
- **Maximum sampled neighbours:** 40 per iteration.
- **Tabu tenure:** 5 iterations.
- **Budget:** 16.8 per period.
- **Inter-period similarity limit:** \\(K=10\\).
- **Random seeds:** 42, 123, 456, 789.

## Results

| Method / seed | Best objective | Improvement |
|---|---:|---:|
| Greedy baseline | 2,691,706.406 | 0.0000% |
| Tabu search — 42 | 2,684,905.586 | 0.2528% |
| Tabu search — 123 | 2,684,905.586 | 0.2528% |
| Tabu search — 456 | 2,684,905.586 | 0.2528% |
| Tabu search — 789 | 2,691,706.406 | 0.0000% |

The mean runtime across the four tabu-search runs was approximately **16.61 seconds**. The greedy plan opened five lines per period. All tabu-search runs also ended with five lines open per period.

The independently evaluated greedy baseline achieved an overall service rate of **80.29%**. The best objective found by tabu search achieved an overall service rate of **80.65%**. For that best solution, the service rates by period were approximately **85.65%** and **77.34%**, compared with **85.65%** and **76.74%** for the greedy baseline.

The best objective was found in three of the four runs. The best observed objective improvement over the greedy baseline was approximately **0.2528%**.

## Interpretation and limitations

Three of the four runs improved upon the greedy baseline, while seed 789 retained the baseline objective. The improvement is modest but consistent across the three successful runs. The results depend on the random seed and do not establish global optimality or performance on the full benchmark.

These results are for the reduced top-20 OD instance with a budget of 16.8 per period, matching the budget used in the team's previous MILP comparisons. They should not be directly compared with results from the earlier budget-60 experiments.

The implementation is a project-specific adaptation of Martínez et al. (2014). In particular, it permits single-line frequency changes and opening or closing lines, whereas the referenced paper uses a compound increase/decrease move.
