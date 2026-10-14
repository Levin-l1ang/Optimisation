# 2026 Benchmark Implementation Notes (`benchmark_2026` package)

> English translation of `BENCHMARK_2026_IMPLEMENTATION.md`. The Chinese file is the original; if they differ, the Chinese file wins. A short translator's addendum at the end lists things that changed after the original was written.

This document explains how the code is organized and how to run it. The requirements are defined by `LINE_PLANNING_BENCHMARK_2026_HANDOFF_EN.md` (Chinese original: `..._ZH.md`);
for progress, deviations and known limitations see `MIGRATION_STATUS_2026_EN.md`.

## 1. Data flow

```text
scripts/fetch_benchmark.py      pinned commit 5d0aae68…, fetches only the needed subset, per-file SHA-256 check
        │                       → data/external/line-planning-benchmark/ + benchmark_manifest.json
tools/java/ExportBenchmark.java official LinePoolGenerator / RouteGenerator / DPTN shortest path (no CPLEX needed, JDK 21)
        │  scripts/run_java_export.sh
        ▼
data/derived/randstad_ic/
   line_pool.json        frozen 93 candidate lines (with pool_sha256 and generator-source hash)
   dptn_routes.jsonl     official DPTN routes (+45-minute window, enough to cover "shortest realizable + 15")
   od_bounds.jsonl       DPTN shortest time per OD (the basis of M=242)
   prepare_report.json   validation report
        │
benchmark_2026/passenger_routes.py   leg → lines supporting the leg → itinerary; the +15 filter happens before frequency expansion
benchmark_2026/multiperiod.py        synthetic monotone multi-period OD with a fixed seed
benchmark_2026/model.py              specification model: frequency-specific trips (handoff document §5)
benchmark_2026/model_compact.py      equivalent line-level form (avoids the Cartesian expansion)
```

## 2. Commands

```bash
pip install -r requirements.txt
python scripts/fetch_benchmark.py            # fetch and verify the benchmark (needs git and network)
python -m benchmark_2026 verify              # re-verify the frozen data (no Java needed)
python -m benchmark_2026 prepare --refresh   # re-run the official Java generator and freeze (needs JDK 21)
python -m benchmark_2026 generate-demand --periods 2        # write the two-period synthetic OD (seed 20261013)
python -m benchmark_2026 estimate --formulation trips       # size of the full instance (specification model)
python -m benchmark_2026 estimate --formulation compact     # size of the full instance (line-level)
python -m benchmark_2026 solve --formulation compact --top-od 10 --frequencies 1,2,3,4 \
       --periods 2 --budgets 11.2,11.2 --M 161,242,403 --K 0,10,1000 --output results/x.json
scripts/run_reduced_experiments.sh           # the full set of reduced experiments already done
python scripts/summarize_results.py results/reduced_top10/*.json
python -m unittest discover -s tests -p "test_*_2026.py"
```

When `solve` is used with `--top-od`, `--frequencies` or a `--budgets` value other than 60, the result JSON honestly flags
`reduced_instance` and `budget_is_primary_60`. `M` has no hidden default; it is taken from the configuration file
`configs/randstad_ic_primary.yaml` or from the command line.

## 3. Model

The specification model (`model.py`) has, per period $t$, $z^t_{\ell f}$, $x^t_{od,p}$, $u^t_{od}$ and the adjacent-period $\Delta^t_\ell$:

$$
\min \sum_{t\in\mathcal T}\sum_{(o,d)}\sum_{p\in\mathcal P_{od}} c_p x^t_{od,p} + M\sum_{t\in\mathcal T}\sum_{(o,d)}u^t_{od},\qquad
\sum_{p\in\mathcal P_{od}} x^t_{od,p}+u^t_{od}=D^t_{od}.
$$

- $c_p = T^{\mathrm{ride}}_p + T^{\mathrm{wait}}_p + \beta N^{\mathrm{transfer}}_p$, where $T^{\mathrm{ride}}_p$ is driving + dwell time, $\beta=15$, $T^{\mathrm{wait}}_p=\sum_{(\ell,f)\in S(p)}60/(2f)$;
- Activation $x^t_{od,p}\le\min\{D^t_{od},C_\ell f\}\,z^t_{\ell f}$ for every $(\ell,f)\in S(p)$; capacity is imposed separately per directed connection, line and frequency;
- Budget $\sum_{\ell,f}b_{\ell f}z^t_{\ell f}\le B_t$, $b_{\ell f}=1+c^V_\ell f$, $c^V_\ell=(2\,\mathrm{duration}_\ell+60)/60$;
- Similarity is imposed only between adjacent periods, $\sum_\ell\Delta^t_\ell\le K$;
- ODs without any trip automatically go into $u^t_{od}$; if $\max_p c_p\ge M$, the main experiment raises an error rather than quietly enlarging $M$.

The line-level form (`model_compact.py`) replaces the trip expansion with the itinerary flow $x^t_{od,i}$ ($i\in\mathcal I_{od}$) and the boarding flow $w^t_{\ell f}$ aggregated by (line, frequency); these three symbols are defined in `PROBLEM_FORMULATION_DETAILED_EN.md`, Section 9.
Waiting cost is separable across segments, and each line has at most one active frequency per period, so both have the same optimal value
(`tests/test_compact_model_2026.py` and the cross-check on reduced instances both give the same objective value).
The specification model remains the definition; the line-level form is only the equivalent way of writing it used for solving.

## 4. Relation to the official benchmark

- The network, OD data, line pool and DPTN route rules come from the official code; the output is re-validated by independent Python code
  (the rule checks of `line_pool.validate_line_pool`, and an independent enumerator that must reproduce the same 93-line set line by line).
- The official objective is based on route duration and transfer penalty; this project additionally adds the frequency-dependent waiting time $60/(2f)$,
  so it is a **model extension** on benchmark data, not a reproduction of the reference model.
- The multi-period OD is a synthetic setting of this project: $D^2_{od}=D_{od}$, $D^1_{od}=r_{od}D_{od}$, $r_{od}\sim U[0.5,0.9]$,
  and can only be described as inspired by / adapted from Schiewe, Schöbel and Sieber (2023).

## Gurobi backend (optional)

The default solver is still SciPy/HiGHS; adding `--solver gurobi` switches to Gurobi, with the model, formulas and result format completely unchanged (`benchmark_2026/solvers.py` receives only the already-built sparse rows).

### Obtaining an academic license

1. Register a free account on the [Gurobi User Portal](https://portal.gurobi.com/) with your university e-mail (you must be a student/faculty member of a recognized degree-granting institution).
2. On the Portal's Licenses page, request a **Named-User Academic** license; the page gives a `grbgetkey <KEY>` command.
3. **A pip-installed gurobipy does not include `grbgetkey`** ([official note](https://support.gurobi.com/hc/en-us/articles/360047265972)). Install Gurobi Optimizer separately, or download the *license tools* from the Portal (macOS: `licensetools*_macos_universal2.tar.gz`; after unpacking, `bin/grbgetkey`).
4. Run `grbgetkey <KEY>` **on the campus network (or the university VPN)**, and save to the default location (`gurobi.lic` in the home directory).
5. In the repository root, run `bash scripts/setup_gurobi.sh`: it creates `.venv`, installs `requirements.txt` and `requirements-gurobi.txt` (`gurobipy`), and then runs `scripts/verify_gurobi.py`.
   You can also complete the activation in one step with `bash scripts/setup_gurobi.sh <KEY>` (the script looks for `grbgetkey` automatically, and prompts if it cannot find it).
6. The restricted license that ships with pip allows only about 2000 variables/constraints, which this project exceeds; step 2 of `verify_gurobi.py` uses a 5000-variable model to confirm the license is not the restricted one, and step 3 reproduces the HiGHS optimal value 2,154,830.44 on the top-10 instance.

The license is valid for about one year, is renewable, and is for academic/non-commercial use only. Official notes: <https://www.gurobi.com/academia/academic-program-and-licenses/>, FAQ: <https://support.gurobi.com/hc/en-us/articles/25770868793105-FAQs-for-Academic-Licenses>.

### Usage

```bash
python -m benchmark_2026 solve --formulation compact --periods 2 --top-od 10 \
    --frequencies 1,2,3,4 --budgets 11.2,11.2 --solver gurobi --threads 4 \
    --time-limit 600 --output results/gurobi_top10.json

# warm start from a known plan (JSON: one {line id: frequency} per period, or a result file previously written by this CLI)
python -m benchmark_2026 solve ... --solver gurobi --initial-plan results/some_plan.json
```

### Test status

`tests/fake_gurobipy.py` simulates, with SciPy, the gurobipy interface used by the adapter layer, and `tests/test_gurobi_backend_2026.py` verifies row types, bounds, parameter passing, warm start and status mapping, and that the compact model gives the same result as HiGHS through this backend. **Real Gurobi has not yet been run in the development environment** (the environment cannot install gurobipy); once you have a license, first run the small top-10 instance and compare against the M=242, K=10 objective 2,154,830.44 in `results/reduced_top10/`.

---

## Translator's addendum (not in the Chinese original; reflects the state on 2026-10-14)

- The last paragraph above ("real Gurobi has not yet been run") is **outdated**: the academic license is active on this machine, and `scripts/verify_gurobi.py` passes (top-10 objective 2,154,830.44). Gurobi has since been used for the scale-gradient experiments (see `HEURISTIC_GREEDY_EN.md`).
- New CLI option for `solve`: `--gurobi-param NAME=VALUE` (repeatable), e.g. `--gurobi-param Heuristics=0`. It is empty by default, so default behaviour is unchanged; the parameters used are recorded in the result JSON under `gurobi_params`.
- New scripts: `scripts/greedy_plan.py` (greedy plan, supports `--top-od` and `--budget`), `scripts/evaluate_plan.py` (fix `z`, solve the flow LP), `scripts/run_gurobi_gradient.sh`, `scripts/run_equal_time.sh`.
- The test suite now has 47 tests (the original text above was written when it had 40).
