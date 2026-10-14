#!/usr/bin/env bash
# Scale-gradient experiment (REDUCED instances, budgets scaled by demand share): Gurobi, M=242, K=10.
set -u
cd "$(dirname "$0")/.."
for spec in "20 16.8" "40 24.9" "80 35.3"; do
  set -- $spec
  python -m benchmark_2026 solve --formulation compact --periods 2 --top-od $1 --budgets $2,$2 \
    --M 242 --K 10 --solver gurobi --threads 8 --time-limit 900 \
    --output results/gurobi_gradient/top$1.json > results/logs/gurobi_top$1.log 2>&1
done
