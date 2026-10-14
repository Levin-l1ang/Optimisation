# Local session continuation notes (for a new Claude Code session)

> English translation of `LOCAL_SESSION_START.md`. The Chinese file is the original; if they differ, the Chinese file wins. An addendum at the end lists what has happened since it was written.

Read first: `docs/LINE_PLANNING_BENCHMARK_2026_HANDOFF_EN.md` (the sole authority, including the Section 14 addendum; the Chinese original is `..._ZH.md`), `docs/MIGRATION_STATUS_2026_EN.md`, `docs/BENCHMARK_2026_IMPLEMENTATION_EN.md`. The old Bahn / Germany-rail documents are not valid requirements.

## Current state
- The 2026 benchmark (Randstad-IC, commit 5d0aae68…) has been migrated: `benchmark_2026/`, 47 tests: `python -m unittest discover -s tests -p "test_*_2026.py"`.
- Model: multi-period passenger-oriented MILP + unserved variable u, M=242. The full-specification model has 72.5 million trips per period and cannot be solved; use the equivalent line-level compact form (`--formulation compact`).
- Backends: HiGHS (default) and Gurobi (`--solver gurobi`, `--threads`, `--initial-plan`). The academic license is activated on this machine, and `scripts/verify_gurobi.py` passes completely (top-10 objective 2,154,830.44).
- On the full instance, HiGHS found no feasible solution in 900 s earlier; a greedy frequency plan (10 lines, f=2) has objective 15,780,946 (all unserved: 23,852,404). The LP relaxation is weak (top-10 gap 57%).

## Next steps (the user has agreed on the direction)
1. Scale-gradient experiments (Gurobi): top-20/40/80/full, 10–15 minute time limit, scaled budget; record objective, gap, runtime and service rate; warm-start the full instance with the greedy plan.
2. Depending on the results, decide on stronger inequalities / decomposition / a restricted problem (the latter two change the problem and need the user's confirmation).
3. Design a heuristic separately afterwards; the fixed-plan LP evaluator (fix z, solve the flow LP) does not require changing the formulation.

## Conventions
- Communicate with the user in Chinese. Do not change the problem formulation (compact is only an equivalent computational form).
- Do not delete old code (`passenger_line_planning/`, `multi_period_line_planning/`, etc.) without the user's consent; do not git commit unless the user asks.

---

## Addendum (not in the Chinese original; state on 2026-10-14)

Steps 1 and 3 above have been carried out, and the details are in `docs/HEURISTIC_GREEDY_EN.md`. In short:

- The scale-gradient experiments were run (top-20/40/80 and the full instance, Gurobi, M=242, K=10). Only top-20 is solved to optimality (3 s); the gap is 5.3% at top-40, 31% at top-80 and 61.5% on the full instance.
- A new greedy plan (11 lines, `scripts/greedy_plan.py`) gives objective **7,372,414** on the full instance, about 36% better than default Gurobi (11,507,931) at 900 s. Gurobi found no improvement when warm-started from it. The fixed-plan LP evaluator now exists (`scripts/evaluate_plan.py`). The earlier "greedy plan (10 lines, f=2, 15,780,946)" mentioned above is a different, unsaved plan.
- The strengthened activation inequality was tried and **not adopted** (it improved the root LP bound by under 0.5%); see `docs/PROBLEM_FORMULATION_DETAILED_EN.md`, Section 9.10.
- K=10 is non-binding in most instances; no sensitivity analysis of K or M is planned (user decision), which deviates from the handoff document's request to run at least $M\in\{161,242,403\}$.
- The work is not committed.
