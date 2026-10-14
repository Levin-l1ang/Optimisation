#!/usr/bin/env python3
"""Check that gurobipy + a full (non size-limited) licence work, then reproduce a known HiGHS optimum.

Usage:  python scripts/verify_gurobi.py            (from the repository root)
Exit code 0 = everything passed.
"""
import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
EXPECTED = 2154830.44          # top-10 ODs, 2 periods, f 1..4, budget 11.2, M=242, K=10 (HiGHS, optimal)


def step(msg):
    print(f"\n== {msg}", flush=True)


def main() -> int:
    step("1. import gurobipy")
    try:
        import gurobipy as gp
    except ImportError:
        print("FAIL: gurobipy is not installed in this Python (pip install -r requirements-gurobi.txt)")
        return 1
    version = getattr(getattr(gp, "gurobi", None), "version", lambda: "?")()
    print("gurobipy version:", version)

    step("2. licence is not the size-limited pip licence (model with 5000 variables / 5000 rows)")
    try:
        m = gp.Model()
        m.Params.OutputFlag = 0
        n = 5000
        xs = [m.addVar(lb=0.0, ub=10.0, obj=1.0) for _ in range(n)]
        m.update()
        for i, x in enumerate(xs):
            m.addLConstr(gp.LinExpr([1.0, 1.0], [x, xs[(i + 1) % n]]), gp.GRB.GREATER_EQUAL, 1.0)
        m.optimize()
        print("objective:", round(m.ObjVal, 6), "(expected 2500.0)")
        if abs(m.ObjVal - n / 2) > 1e-6:
            print("FAIL: unexpected objective"); return 1
    except gp.GurobiError as exc:
        print("FAIL:", exc)
        print("-> most likely no valid licence file was found: see docs/BENCHMARK_2026_IMPLEMENTATION.md")
        return 1

    step("3. reproduce the HiGHS optimum on the top-10 reduced instance")
    from benchmark_2026.cli import main as cli_main
    out = Path(tempfile.mkdtemp()) / "check.json"
    cli_main(["solve", "--formulation", "compact", "--periods", "2", "--top-od", "10",
              "--frequencies", "1,2,3,4", "--budgets", "11.2,11.2", "--M", "242", "--K", "10",
              "--solver", "gurobi", "--time-limit", "600", "--output", str(out)])
    run = json.loads(out.read_text())["runs"][0]
    ok = run["status"] == "optimal" and abs(run["objective"] - EXPECTED) <= 1e-6 * EXPECTED + 0.05
    print(f"gurobi: status={run['status']} objective={run['objective']:.2f} runtime={run['runtime_seconds']:.1f}s"
          f"  (HiGHS: {EXPECTED:.2f}, 237 s trips / 30 s compact)")
    if not ok:
        print("FAIL: Gurobi result differs from the HiGHS optimum"); return 1
    print("\nALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
