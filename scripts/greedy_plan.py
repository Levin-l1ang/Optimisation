"""Simple greedy frequency plan for the full Randstad-IC instance (HEURISTIC, warm start only).

Same plan in both periods, chosen on the period-2 demand D. Each step applies the move
(open a line at f, or raise f) with the best (demand-weighted cost reduction) / (extra budget).
OD cost = min over itineraries whose lines are all open of ride + 15*transfers + sum 60/(2f), capped at M;
capacity is ignored while choosing (the MILP warm start completes the flows).
Usage: PYTHONPATH=. python scripts/greedy_plan.py OUT.json [--top-od N --budget B]
(--top-od / --budget define a REDUCED experiment; default = full instance, budget 60.)
"""
import argparse, json, sys, time
from collections import defaultdict
from benchmark_2026.multiperiod import generate
from benchmark_2026.pipeline import build_compact_data, frequency_options, load_primary, top_od_subset
from benchmark_2026 import paths

primary = load_primary()
ap = argparse.ArgumentParser()
ap.add_argument("output")
ap.add_argument("--top-od", type=int)
ap.add_argument("--budget", type=float, default=60.0)
args = ap.parse_args()
M, B = 242.0, args.budget
started = time.time()
demand = generate(primary.instance.od_pairs, 2, 20261013)
data = build_compact_data(primary, demand, top_od_subset(primary, args.top_od) if args.top_od else None)
D = data.demands[1]
lines = data.lines
fmax = {l: lines[l].max_frequency for l in lines}
pen = data.transfer_penalty
by_line = defaultdict(set)
for od, its in data.itineraries.items():
    for k, it in enumerate(its):
        for leg in it.legs:
            by_line[leg.line_id].add((od, k))

def od_cost(od, plan):
    best = M
    for it in data.itineraries[od]:
        if all(leg.line_id in plan for leg in it.legs):
            c = it.ride_time + pen * it.transfers + sum(30.0 / plan[leg.line_id] for leg in it.legs)
            best = min(best, c)
    return best

plan, cur = {}, {od: M for od in D}
spent = 0.0
def cost(l, f): return 1.0 + lines[l].cost_per_frequency * f
while True:
    best = None
    for l in lines:
        f0 = plan.get(l, 0)
        extra0 = cost(l, f0) if f0 else 0.0
        for f in range(f0 + 1, fmax[l] + 1):
            extra = cost(l, f) - extra0
            if spent + extra > B + 1e-9: break
            trial = {**plan, l: f}
            ods = {od for od, _ in by_line[l]}
            gain = sum(D[od] * (cur[od] - od_cost(od, trial)) for od in ods)
            if gain > 1e-9 and (best is None or gain / extra > best[0]):
                best = (gain / extra, l, f, extra, ods, trial)
    if best is None: break
    _, l, f, extra, ods, trial = best
    plan, spent = trial, spent + extra
    for od in ods: cur[od] = od_cost(od, plan)
    print(f"line {l} f={f} spent={spent:.2f}", flush=True)
obj = sum(D[od] * cur[od] for od in D)
print("uncapacitated per-period cost on D:", obj, "lines:", len(plan))
print(f"greedy seconds: {time.time() - started:.1f}")
json.dump([{str(l): f for l, f in plan.items()}] * 2, open(args.output, "w"))
