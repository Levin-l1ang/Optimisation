# Tabu Search Results — Top-20 OD Instance

## Experimental setup

- **Method:** Tabu-search frequency planning, adapted from Martínez et al. (2014).
- **Instance:** Reduced benchmark containing the top 20 OD pairs.
- **Initial solution:** Greedy frequency plan.
- **Iterations:** 20 per run.
- **Maximum sampled neighbours:** 40 per iteration.
- **Tabu tenure:** 5 iterations.
- **Budget:** 60 per period.
- **Inter-period similarity limit:** \(K=10\).
- **Random seeds:** 42, 123, 456, 789.

## Results

| Method / seed | Best objective | Improvement |
|---|---:|---:|
| Greedy baseline | 1,155,562.751 | 0.0000% |
| Tabu search — 42 | 1,154,509.316 | 0.0912% |
| Tabu search — 123 | 1,155,171.466 | 0.0339% |
| Tabu search — 456 | 1,155,562.751 | 0.0000% |
| Tabu search — 789 | 1,155,171.466 | 0.0339% |

The mean runtime across the four runs was approximately 28.19 seconds. All four final plans achieved a 100% service rate in both periods and opened seven lines per period.

## Interpretation and limitations

Three of the four runs improved upon the greedy baseline. The best run improved the objective by approximately 0.0912%, indicating a modest improvement on this reduced instance. The results depend on the random seed and do not establish global optimality or performance on the full benchmark.

The implementation is a project-specific adaptation of Martínez et al. (2014). In particular, it permits single-line frequency changes and opening or closing lines, whereas the referenced paper uses a compound increase/decrease move.
