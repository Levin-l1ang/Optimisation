# Implementation and Usage Guide

## 1. Purpose

This guide is for teammates who need to rebuild the Germany-rail data, rerun the
multi-period line-planning experiment, inspect results, or integrate the implementation
with another part of the project. No commercial solver is required.

## 2. Environment setup

Run these commands from the repository root:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

The implementation uses NumPy, SciPy/HiGHS, and Matplotlib. It does not use Gurobi.

## 3. Relevant files

```text
multi_period_line_planning/
  germany_rail.py   data reconstruction and line-pool generation
  instance.py       LinTim reader and deterministic demand-period generation
  models.py         MILP formulations
  reproduce.py      parameter sweeps, CSV export, and plots
  cli.py            command-line interface

datasets/germany-rail/
  timpasslib/        retained public TimPassLib source CSV files
  basis/             generated LinTim PTN, OD, load, and line-pool files

results/germany_rail/
  benchmark.csv
  frequencies.csv
  summary.json
  cost_similarity_tradeoff.png
  line_concepts.png

tests/
  test_multi_period_line_planning.py
```

## 4. Rebuild the Germany-rail data

The required TimPassLib source files are already checked in under
`datasets/germany-rail/timpasslib/`. The OD source is the Bahn-01 matrix that was
already present in this repository.

```bash
.venv/bin/python -m multi_period_line_planning prepare-germany-rail \
  --dataset datasets/germany-rail \
  --source-od src/simulation/data/bahn-01/OD.giv \
  --pool-size 132
```

This command deterministically overwrites the generated files in
`datasets/germany-rail/basis/`. It does not alter the raw TimPassLib CSV files or the
source OD matrix.

Available data-generation options are:

| Option | Default | Meaning |
|---|---:|---|
| `--pool-size` | 132 | Number of candidate lines |
| `--minimum-line-edges` | 2 | Minimum path length admitted to the pool |
| `--nominal-total-demand` | 5000 | Total demand after normalization |
| `--vehicle-capacity` | 70 | Passengers represented by one unit of frequency |
| `--upper-frequency` | 20 | Fixed edge-frequency upper bound |

The generated `basis/Generation-Metadata.json` records all effective assumptions.

### Line-pool algorithm

`germany_rail.py` reconstructs the physical network from TimPassLib drive activities,
reconstructs the 42 public operated lines, and computes one shortest-path tree per
origin. It then adds distinct shortest paths for OD pairs in descending demand order
until the requested pool size is reached. Reverse paths are deduplicated.

For the default instance, the preprocessing cost is small: 250 Dijkstra runs on a
326-edge graph plus sorting the positive OD pairs. No MILP is used for pool generation.

## 5. Run the complete compact reproduction

```bash
.venv/bin/python -m multi_period_line_planning reproduce \
  --dataset datasets/germany-rail \
  --output results/germany_rail \
  --periods 2 \
  --seed 2023 \
  --penalty 1 \
  --time-limit 60
```

For a dataset with more than 32 lines, the command uses the compact Germany-rail
parameter grids documented in the reproduction report. The time limit is applied to
each MILP, not to the whole sweep.

The command prints the number of completed and optimal solves. Detailed statuses are
written to `benchmark.csv`.

## 6. Solve one model

Examples:

```bash
# Independent-period lower bound
.venv/bin/python -m multi_period_line_planning solve lower \
  --dataset datasets/germany-rail --periods 2 --seed 2023

# Frequency-based similarity with K = 16
.venv/bin/python -m multi_period_line_planning solve mp-f \
  --dataset datasets/germany-rail --periods 2 --seed 2023 --k 16

# Wasserstein similarity with K = 24 and unit source/sink penalty
.venv/bin/python -m multi_period_line_planning solve mp-w \
  --dataset datasets/germany-rail --periods 2 --seed 2023 --k 24 --penalty 1
```

Valid model names are `lower`, `upper`, `mp-f`, `mp-b`, `mp-b-iter`, `mp-b-ext`, and
`mp-w`. The command prints JSON including the objective, status, runtime, realized
dissimilarity, and frequency vectors.

## 7. Python API

```python
from pathlib import Path

from multi_period_line_planning import load_lintim_instance, solve_mp_w

instance = load_lintim_instance(
    Path("datasets/germany-rail"),
    periods=2,
    seed=2023,
)
result = solve_mp_w(instance, k=24, penalty=1.0, time_limit=60)

assert result.optimal
print(result.objective)
print(result.dissimilarity)
```

`LinePlanningInstance` stores immutable edge ids, edge lengths, line-edge incidence,
line costs, and one lower/upper frequency vector per period. Its
`line_distance_matrix` property computes the length-weighted Jaccard distance used by
MP-W.

## 8. Model implementation notes

All models share integer line-frequency variables and edge constraints of the form

```text
lower[p,e] <= sum(incidence[e,l] * frequency[p,l]) <= upper[p,e].
```

`models.py` builds sparse SciPy constraints directly and calls `scipy.optimize.milp`.
The main model-specific additions are:

- MP-F: binary line-use variables, common active sets, and absolute frequency-change
  variables.
- MP-B: monotone frequency constraints and bounded new-line counts.
- MP-B_iter: one single-period MILP per period, fixing monotonicity relative to the
  previously computed solution.
- MP-B_ext: pairwise binary symmetric-difference variables.
- MP-W: integer transport variables between every pair of candidate lines plus source
  and sink variables.

MP-W has quadratic growth in the line-pool size. The default 132-line pool is suitable
for a lightweight reproduction; a 2,770-line MP-W run would be substantially larger
and is outside the intended scope of the compact workflow.

## 9. Demand-period generation

When explicit per-period load files are not supplied, `instance.py` treats
`basis/Load.giv` as the peak period. Earlier periods use deterministic random lower
bounds generated from the supplied seed; the last period equals the peak. Upper bounds
remain fixed across periods.

For full control, Python callers may pass explicit files:

```python
instance = load_lintim_instance(
    Path("datasets/germany-rail"),
    load_files=[Path("period_1.Load.giv"), Path("period_2.Load.giv")],
)
```

Every explicit load file must contain one row per edge in LinTim's
`edge-id; load; lower-frequency; upper-frequency` format.

## 10. Tests

```bash
.venv/bin/python -m unittest discover -s tests -v
```

The suite checks formulation feasibility and similarity semantics, monotone cost
behavior, the weighted Jaccard distance, recovery of the lower bound for relaxed
models, and deterministic reconstruction of the 250-stop/326-edge/132-line data.

## 11. Result interpretation

- `optimal` means HiGHS proved optimality within the requested settings.
- `limit_reached` means a time or solver limit was reached; inspect `mip_gap` before
  comparing the objective with proven-optimal results.
- `infeasible` usually indicates inconsistent lower/upper edge bounds or a line pool
  that does not cover a required edge.
- Objective values are comparable only when the dataset, costs, periods, and seed are
  identical.
- Runtimes are not comparable to the paper's Gurobi timings.

For the assumptions, evidence, numerical results, and exact-reproduction boundary, see
the [reproduction report](REPRODUCTION_REPORT.md).
