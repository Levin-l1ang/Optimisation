"""Tabu-search frequency-plan improvement based on Martinez et al. (2014).

Project adaptation: unlike the paper's increase/decrease pair move, this script also
allows opening and closing lines. Every candidate is evaluated by fixing the line
frequencies in the existing compact model and solving its passenger-flow LP.

Plan format: JSON list of {line_id: positive_frequency}, one dictionary per period;
closed lines are omitted. Start from an existing feasible plan, e.g. greedy_plan.py.
For tractable initial experiments use --top-od 10 or 20. Full-instance evaluation can
be expensive because each neighbour requires an LP solve.
"""
import argparse
import json
import random
import time
from pathlib import Path

import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import coo_matrix

from benchmark_2026.cli import _load_plan
from benchmark_2026.model_compact import _construct
from benchmark_2026.multiperiod import generate
from benchmark_2026.pipeline import (build_compact_data, build_config, frequency_options,
                                    load_primary, top_od_subset)


def plan_cost(plan, cfg):
    return [sum(cfg.operating_costs[(l, f)] for l, f in period.items()) for period in plan]


def similarity(plan):
    if len(plan) < 2:
        return 0
    lines = set().union(*(set(p) for p in plan))
    return sum(abs(plan[t].get(l, 0) - plan[t + 1].get(l, 0)) for t in range(len(plan) - 1) for l in lines)


def feasible(plan, cfg):
    if any(plan_cost(plan, cfg)[t] > cfg.budgets[t] + 1e-8 for t in range(len(plan))):
        return False
    return similarity(plan) <= cfg.similarity_limit + 1e-8


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('initial_plan', type=Path)
    ap.add_argument('output', type=Path)
    ap.add_argument('--top-od', type=int, default=20, help='reduced experiment; omit only for full instance')
    ap.add_argument('--iterations', type=int, default=10)
    ap.add_argument('--tenure', type=int, default=5)
    ap.add_argument('--max-neighbours', type=int, default=40,
                    help='randomly cap evaluated feasible neighbours per iteration')
    ap.add_argument('--seed', type=int, default=123)
    ap.add_argument('--budgets', default='60,60')
    ap.add_argument('--M', type=float, default=242)
    ap.add_argument('--K', type=float, default=10)
    args = ap.parse_args()
    rng = random.Random(args.seed)

    primary = load_primary()
    periods = len(args.budgets.split(','))
    budgets = [float(x) for x in args.budgets.split(',')]
    demand = generate(primary.instance.od_pairs, periods, 20261013)
    subset = top_od_subset(primary, args.top_od) if args.top_od else None
    data = build_compact_data(primary, demand, subset)
    freqs = frequency_options(primary.lines)
    cfg = build_config(primary, freqs, periods, args.M, args.K, budgets)
    current = _load_plan(args.initial_plan, periods)
    if not feasible(current, cfg):
        raise SystemExit('Initial plan violates a budget or inter-period similarity limit.')

    # Construct model once; each evaluation fixes all line/frequency decisions and solves LP.
    b, z, x, u, size, _ = _construct(data, cfg)
    rows, cols, vals = [], [], []
    for i, row in enumerate(b.rows):
        for j, value in row.items():
            rows.append(i); cols.append(j); vals.append(value)
    A = coo_matrix((vals, (rows, cols)), shape=(len(b.rows), len(b.cost))).tocsr()
    integrality = np.zeros(len(b.cost))

    def evaluate(plan):
        lo, hi = list(b.lower), list(b.upper)
        for (t, line, f), j in z.items():
            value = 1.0 if plan[t].get(line, 0) == f else 0.0
            lo[j] = hi[j] = value
        result = milp(c=np.asarray(b.cost), integrality=integrality,
                      bounds=Bounds(np.asarray(lo), np.asarray(hi)),
                      constraints=LinearConstraint(A, np.asarray(b.row_lower), np.asarray(b.row_upper)))
        if result.x is None or result.fun is None:
            return float('inf')
        return float(result.fun)

    started = time.time()
    current_obj = evaluate(current)
    best, best_obj = [dict(p) for p in current], current_obj
    # Recency memory per period and line, following the paper's TabuStart idea.
    tabu_start = {}
    history = [{'iteration': 0, 'objective': current_obj, 'lines_open': [len(p) for p in current]}]
    line_ids = sorted(data.lines)

    for iteration in range(1, args.iterations + 1):
        candidates = []
        for t in range(periods):
            for line in line_ids:
                old = current[t].get(line, 0)
                allowed = [0] + list(freqs[line])
                for new in allowed:
                    if new == old:
                        continue
                    trial = [dict(p) for p in current]
                    if new == 0:
                        trial[t].pop(line, None)
                    else:
                        trial[t][line] = new
                    if feasible(trial, cfg):
                        candidates.append((t, line, new, trial))
        if not candidates:
            break
        
        # Bound expensive LP evaluations reproducibly when the neighbourhood is large.
        total_feasible = len(candidates)

        if len(candidates) > args.max_neighbours:
           candidates = rng.sample(candidates, args.max_neighbours)

        evaluated = []
        for t, line, new, trial in candidates:
            is_tabu = iteration <= tabu_start.get((t, line), -10**9) + args.tenure
            obj = evaluate(trial)
            # Aspiration: permit a tabu move if it improves the best solution found.
            if is_tabu and obj >= best_obj - 1e-8:
                continue
            evaluated.append((obj, t, line, new, trial))
        if not evaluated:
            history.append({'iteration': iteration, 'objective': current_obj,
                            'evaluated': 0, 'note': 'all sampled moves tabu'})
            continue
        evaluated.sort(key=lambda item: item[0])
        obj, t, line, new, chosen = evaluated[0]
        old = current[t].get(line, 0)
        current = chosen
        current_obj = obj
        tabu_start[(t, line)] = iteration
        if current_obj < best_obj - 1e-8:
            best, best_obj = [dict(p) for p in current], current_obj
        history.append({'iteration': iteration, 'objective': current_obj,
                        'best_objective': best_obj, 'changed_period': t,
                        'changed_line': line, 'from_frequency': old,
                        'to_frequency': new, 'sampled_neighbours': len(candidates),
                        'evaluated_neighbours': len(evaluated),
                        'lines_open': [len(p) for p in current]})
        print(f"iter={iteration} objective={current_obj:.3f} best={best_obj:.3f} move=({t},{line},{old}->{new})", flush=True)

    out = {'method': 'tabu frequency search (project adaptation of Martinez et al. 2014)',
           'adaptation_note': 'Adds single-line open/close/frequency-change moves; paper uses a compound increase/decrease move.',
           'top_od': args.top_od, 'budgets': budgets, 'M': args.M, 'K': args.K,
           'iterations_requested': args.iterations, 'tenure': args.tenure,
           'max_neighbours': args.max_neighbours, 'initial_objective': history[0]['objective'],
           'best_objective': best_obj, 'best_plan': [{str(l): f for l, f in p.items()} for p in best],
           'lines_open': [len(p) for p in best], 'budget_spent': plan_cost(best, cfg),
           'similarity': similarity(best), 'runtime_seconds': time.time() - started,
           'history': history}
    # Keep the primary output directly consumable by evaluate_plan.py / the MILP CLI.
    args.output.write_text(json.dumps(out['best_plan'], indent=2) + '\n')
    summary_path = args.output.with_name(args.output.stem + '_summary.json')
    summary_path.write_text(json.dumps(out, indent=2) + '\n')
    print(json.dumps({k: out[k] for k in ('initial_objective','best_objective','lines_open','budget_spent','similarity','runtime_seconds')}))
    print(f'plan: {args.output}\nsummary: {summary_path}')

if __name__ == '__main__':
    main()
