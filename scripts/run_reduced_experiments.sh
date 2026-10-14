#!/usr/bin/env bash
# Reduced end-to-end experiments (solved to optimality).  NOT the full Randstad-IC instance:
#   * the 10 largest OD pairs, frequencies restricted to 1..4,
#   * budget scaled to the demand share of those ODs (60 * 0.186 = 11.2 per period).
# usage: scripts/run_reduced_experiments.sh [OUT_DIR]
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-results/reduced_top10}"; mkdir -p "$OUT"
COMMON=(--formulation compact --top-od 10 --frequencies 1,2,3,4 --time-limit 600)

# 1. consistency: identical periods, no similarity restriction => 2x the single-period optimum
python3 -m benchmark_2026 solve "${COMMON[@]}" --periods 1 --budgets 11.2 --identical-demand --M 242 --K 0 --output "$OUT/consistency_T1.json"
python3 -m benchmark_2026 solve "${COMMON[@]}" --periods 2 --budgets 11.2,11.2 --identical-demand --M 242 --K 0,1000 --output "$OUT/consistency_T2.json"
# 2. main reduced experiment: two synthetic monotone periods, M sensitivity x K
python3 -m benchmark_2026 solve "${COMMON[@]}" --periods 2 --budgets 11.2,11.2 --M 161,242,403 --K 0,10,1000 --output "$OUT/two_period_M_K.json"
# 3. the same instance with the frequency-specific-trip specification (cross-check of the reformulation)
python3 -m benchmark_2026 solve --formulation trips --top-od 10 --frequencies 1,2,3,4 --time-limit 600 \
  --periods 2 --budgets 11.2,11.2 --M 242 --K 10 --output "$OUT/two_period_trips_check.json"
# 4. 3-period robustness
python3 -m benchmark_2026 solve "${COMMON[@]}" --periods 3 --budgets 11.2,11.2,11.2 --M 242 --K 0,10,1000 --output "$OUT/three_period_robustness.json"
