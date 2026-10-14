#!/usr/bin/env bash
# Gurobi (default settings, no warm start) with a wall-clock limit equal to greedy time + evaluation LP.
set -u
cd "$(dirname "$0")/.."
source .venv/bin/activate
for spec in "20 16.8 1" "40 24.9 7" "80 35.3 15" "0 60 183"; do
  set -- $spec
  extra=""; [ "$1" != "0" ] && extra="--top-od $1"
  name=top$1; [ "$1" = "0" ] && name=full
  python -m benchmark_2026 solve --formulation compact --periods 2 $extra --budgets $2,$2 \
    --M 242 --K 10 --solver gurobi --threads 8 --time-limit $3 \
    --output results/gurobi_gradient/${name}_equal_time.json > results/logs/gurobi_${name}_equal_time.log 2>&1
done
