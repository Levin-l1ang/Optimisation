"""Evaluate a fixed line/frequency plan: fix every z, solve the passenger-flow LP (no integers).

Same constraints and objective as the line-level model (benchmark_2026/model_compact.py);
no formulation change.  The plan is a JSON list with one {line_id: frequency} per period,
or a result file of `solve` (its last run).
Usage: PYTHONPATH=. python scripts/evaluate_plan.py PLAN.json OUT.json [--top-od N --budgets B1,B2]
"""
import argparse, json, time
import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import coo_matrix
from benchmark_2026.cli import _load_plan
from benchmark_2026.model_compact import _construct
from benchmark_2026.multiperiod import generate
from benchmark_2026.pipeline import build_compact_data, build_config, frequency_options, load_primary, top_od_subset
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument("plan", type=Path); ap.add_argument("output", type=Path)
ap.add_argument("--top-od", type=int); ap.add_argument("--budgets", default="60,60")
ap.add_argument("--M", type=float, default=242); ap.add_argument("--K", type=float, default=10)
a = ap.parse_args()
budgets = [float(v) for v in a.budgets.split(",")]
plan = _load_plan(a.plan, len(budgets))
p = load_primary()
demand = generate(p.instance.od_pairs, len(budgets), 20261013)
data = build_compact_data(p, demand, top_od_subset(p, a.top_od) if a.top_od else None)
cfg = build_config(p, frequency_options(p.lines), len(budgets), a.M, a.K, budgets)
for t, period in enumerate(plan):  # the plan must itself be feasible
    spent = sum(cfg.operating_costs[(l, f)] for l, f in period.items())
    assert spent <= budgets[t] + 1e-9, f"plan exceeds budget in period {t+1}: {spent}"
b, z, x, u, size, _ = _construct(data, cfg)
lower, upper = list(b.lower), list(b.upper)
for (t, l, f), j in z.items():
    value = 1.0 if plan[t].get(l, 0) == f else 0.0
    lower[j] = upper[j] = value
r, c, v = [], [], []
for i, row in enumerate(b.rows):
    for j, val in row.items(): r.append(i); c.append(j); v.append(val)
A = coo_matrix((v, (r, c)), shape=(len(b.rows), len(b.cost))).tocsr()
t0 = time.time()
res = milp(c=np.asarray(b.cost), integrality=np.zeros(len(b.cost)), bounds=Bounds(np.asarray(lower), np.asarray(upper)),
           constraints=LinearConstraint(A, np.asarray(b.row_lower), np.asarray(b.row_upper)))
elapsed = time.time() - t0
sol = res.x
unserved = [sum(sol[u[(t, od)]] for od in data.demands[t]) for t in range(len(budgets))]
total = [sum(data.demands[t].values()) for t in range(len(budgets))]
out = {"plan_file": str(a.plan), "top_od": a.top_od, "budgets": budgets, "M": a.M, "status": int(res.status),
       "objective": float(res.fun), "service_rate_by_period": [1 - un / tt for un, tt in zip(unserved, total)],
       "overall_service_rate": 1 - sum(unserved) / sum(total), "lines_open": [len(pl) for pl in plan],
       "lp_seconds": elapsed, "note": "z fixed to the plan; flows solved by LP (HiGHS)"}
a.output.write_text(json.dumps(out, indent=1) + "\n"); print(json.dumps(out))
