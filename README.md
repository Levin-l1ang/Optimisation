# Multi-period passenger-oriented line planning on the 2026 benchmark

A MILP for **multi-period line planning**: within a fixed, finite pool of candidate lines, choose for every demand period which lines operate and at what frequency, so that the passengers' total generalized travel time plus an unserved-demand penalty is minimized. The model is solved on the `Randstad-IC` network of the [2026 line-planning benchmark](https://github.com/rn-van-lieshout/line-planning-benchmark) (MIT, pinned commit `5d0aae68588701285c42ba47e593fbeb600cbc5c`), with a demand-period structure following Schiewe, Schöbel and Sieber (2023).

This is a **model extension on benchmark data**, not a reproduction of either paper: the frequency-dependent waiting time $60/(2f)$ and the synthetic multi-period demand are this project's own settings.

## The problem in brief

Symbols are those of Section 5 of [the handoff document](docs/LINE_PLANNING_BENCHMARK_2026_HANDOFF_EN.md); the full formulation is in [PROBLEM_FORMULATION_DETAILED_EN.md](docs/PROBLEM_FORMULATION_DETAILED_EN.md).

- **Sets and data:** periods $\mathcal T$, candidate lines $\mathcal L$ (93, frozen from the official generator), frequency set $\mathcal F_\ell=\{1,\dots,\bar f_\ell\}$, OD pairs $(o,d)$ with demand $D^t_{od}$, finite passenger-trip sets $\mathcal P_{od}$, capacity $C_\ell=1500$ per train and direction, operating cost $b_{\ell f}=1+c^V_\ell f$, budget $B_t=60$, transfer penalty $\beta=15$, unserved penalty $M=242$, similarity bound $K$.
- **Variables:** $z^t_{\ell f}\in\{0,1\}$ (line $\ell$ runs with frequency $f$ in period $t$), $x^t_{od,p}\ge0$ (flow on trip $p$), $u^t_{od}\ge0$ (unserved demand), $\Delta^t_\ell\ge0$ (frequency change between adjacent periods).
- **Objective:**

$$
\min\ \sum_{t\in\mathcal T}\sum_{(o,d)}\sum_{p\in\mathcal P_{od}} c_p\,x^t_{od,p}\;+\;M\sum_{t\in\mathcal T}\sum_{(o,d)}u^t_{od},
\qquad c_p=T^{\mathrm{ride}}_p+T^{\mathrm{wait}}_p+\beta N^{\mathrm{transfer}}_p .
$$

- **Constraints:** one frequency per line and period; demand conservation $\sum_p x^t_{od,p}+u^t_{od}=D^t_{od}$; activation $x^t_{od,p}\le D^t_{od}z^t_{\ell f}$; per-direction capacity; per-period budget; and a similarity bound $\sum_\ell\Delta^t_\ell\le K$ between adjacent periods.

At full scale the specification model has about 72.5 million trips per period and cannot be built, so solving uses an **equivalent line-level form** (`--formulation compact`) with the same integer optimum (see Section 9 of the formulation document).

## Repository layout

| Path | Content |
|---|---|
| `benchmark_2026/` | Active implementation: loader, line pool, passenger routes, trip expansion, specification model, line-level model, multi-period demand, solver backends (HiGHS, Gurobi), CLI |
| `scripts/` | Benchmark fetch, Java export, greedy plan, fixed-plan evaluator, experiment drivers, Gurobi setup and check |
| `configs/randstad_ic_primary.yaml` | Primary experiment configuration (no hidden defaults, in particular for $M$) |
| `data/external/` | Pinned benchmark files with a SHA-256 manifest (fetched by `scripts/fetch_benchmark.py`) |
| `data/derived/randstad_ic/` | Frozen 93-line pool, official DPTN routes, per-OD bounds |
| `tests/` | Unit tests (`test_*_2026.py` for the active code) |
| `results/` | Experiment outputs (reduced instances, scale gradient, full-instance size reports) |
| `docs/` | English documentation, see below |
| `passenger_line_planning/`, `multi_period_line_planning/`, `datasets/passenger-toy/` | Earlier implementations, superseded by `benchmark_2026/` and kept for reference |
| `src/`, `libs/`, `ci/`, `docker/`, `datasets/` (other) | Upstream LinTim / OpenLinTim code and data (see `LICENSE`) |

## Getting started

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
python scripts/fetch_benchmark.py          # fetch and verify the pinned benchmark (needs git and network)
python -m benchmark_2026 verify            # re-verify the frozen data (no Java needed)
python -m unittest discover -s tests -p "test_*_2026.py"
```

A reduced instance (the 10 largest OD pairs, frequencies 1–4, budget scaled by demand share):

```bash
python -m benchmark_2026 solve --formulation compact --top-od 10 --frequencies 1,2,3,4 \
       --periods 2 --budgets 11.2,11.2 --M 242 --K 10 --output results/x.json
```

`--top-od N` keeps only the $N$ OD pairs with the largest demand; such runs are flagged `reduced_instance=true` in the result JSON and are not conclusions about the full instance. All commands, the optional Gurobi backend (`--solver gurobi`, needs an academic license) and the data flow are described in [BENCHMARK_2026_IMPLEMENTATION_EN.md](docs/BENCHMARK_2026_IMPLEMENTATION_EN.md). Regenerating the frozen line pool needs JDK 21; daily runs and tests do not.

## Current results

- **Reduced instance (top-10, $M=242$, $K=10$):** solved to optimality, objective 2,154,830.44; HiGHS and Gurobi agree.
- **Scale gradient (Gurobi, $M=242$, $K=10$, 900 s):** top-20 is solved to optimality in 3 s; the gap is 5.3% on top-40, 31% on top-80 and 61.5% on the full instance.
- **Full Randstad-IC (416 ODs, $B_t=60$):** not solved. HiGHS finds nothing better than "all demand unserved" (23,852,404); default Gurobi reaches 11,507,931. A greedy frequency plan (`scripts/greedy_plan.py`) evaluated with a fixed-plan flow LP reaches **7,372,414** (service rate 0.933), and Gurobi did not improve it when warm-started from it. The LP relaxation is weak, which is the main obstacle.
- $K=10$ is non-binding on most instances, and no sensitivity analysis of $K$ or $M$ has been run yet; see the limitations in [HEURISTIC_GREEDY_EN.md](docs/HEURISTIC_GREEDY_EN.md) and [MIGRATION_STATUS_2026_EN.md](docs/MIGRATION_STATUS_2026_EN.md).

An `optimal` result is optimal only for the restricted MILP defined by the frozen line pool, the retained trip sets and the stated parameters, not over all theoretically feasible lines and passenger paths.

## Documentation

| Document | Content |
|---|---|
| [LINE_PLANNING_BENCHMARK_2026_HANDOFF_EN.md](docs/LINE_PLANNING_BENCHMARK_2026_HANDOFF_EN.md) | The authoritative specification: sources, fixed configuration, full formulation (Section 5), verification plan |
| [PROBLEM_FORMULATION_DETAILED_EN.md](docs/PROBLEM_FORMULATION_DETAILED_EN.md) | Detailed formulation, code audit, and the equivalent line-level form with proofs |
| [BENCHMARK_2026_IMPLEMENTATION_EN.md](docs/BENCHMARK_2026_IMPLEMENTATION_EN.md) | Code organization, commands, Gurobi backend |
| [MIGRATION_STATUS_2026_EN.md](docs/MIGRATION_STATUS_2026_EN.md) | What was done, results, deviations and known limitations |
| [HEURISTIC_GREEDY_EN.md](docs/HEURISTIC_GREEDY_EN.md) | Greedy frequency heuristic (warm start), the top-N reduced instances and the comparison with Gurobi |
| [changes.md](docs/changes.md) | The four changes relative to the original formulation |
| [LOCAL_SESSION_START_EN.md](docs/LOCAL_SESSION_START_EN.md) | Current state and next steps |

## License

The repository root `LICENSE` is that of the upstream LinTim code. The benchmark data in `data/external/` comes from the MIT-licensed [line-planning-benchmark](https://github.com/rn-van-lieshout/line-planning-benchmark) and keeps its own `LICENSE`.
