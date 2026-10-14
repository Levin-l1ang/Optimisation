# Greedy frequency-setting heuristic (used for warm starts)

> English version of `HEURISTIC_GREEDY_ZH.md`. The Chinese file is the original; if they differ, the Chinese file wins.

Code: `scripts/greedy_plan.py`. Output: `results/greedy_plan.json` (one `{line id: frequency}` per period, identical for both periods).
This is a heuristic, used only to give Gurobi an initial solution; it **does not change the problem formulation**.
Document date: 2026-10-14. Note: the "greedy plan (10 lines, f=2, objective 15,780,946)" mentioned in `docs/LOCAL_SESSION_START.md` is an earlier, different plan whose algorithm and file were not saved; it is not the same algorithm as the one in this document.

## 1. Idea

Within the budget, step by step add the "best value" line or frequency; at each step choose the action with the largest **cost reduction / extra budget**, until no worthwhile action is left.

## 2. The algorithm

Input: the full instance (416 ODs, 93 candidate lines, frequency sets $\mathcal F_\ell=\{1,\dots,\bar f_\ell\}$), budget $B_t=60$, unserved penalty $M=242$, transfer penalty $\beta=15$, period length 60 minutes. The demand used is $D^2_{od}$ of **period 2** (the larger of the two periods). The same plan is used in both periods, so the period index $t$ is dropped from $F_\ell$ and $B_t$ below.

This section uses the symbols of the original problem. The itinerary set $\mathcal I_{od}$ and the itinerary quantities $T_i^{\mathrm{ride}}$, $N_i^{\mathrm{transfer}}$, $\ell_j(i)$ are those of `PROBLEM_FORMULATION_DETAILED_EN.md`, Section 9.2; only $\Phi$ and $g$ are new, and they are algorithm-local quantities.

**Plan**: the frequency vector $F=(F_\ell)_{\ell\in\mathcal L}$ with $F_\ell\in\{0\}\cup\mathcal F_\ell$ (0 means the line is not open).

**OD cost** (for a given plan $F$):

$$
c_{od}(F)=\min\Bigl\{M,\ \min_{i\in\mathcal I_{od}:\ F_{\ell_j(i)}>0\ \forall j}\Bigl[T_i^{\mathrm{ride}}+\beta N_i^{\mathrm{transfer}}+\sum_{j}\frac{60}{2F_{\ell_j(i)}}\Bigr]\Bigr\}.
$$

If no itinerary is usable, the value is $M$.

**Total plan cost** $\Phi(F)=\sum_{(o,d)}D^2_{od}\,c_{od}(F)$.

**Action**: for a line $\ell$ and a frequency $f>F_\ell$, set the frequency of $\ell$ to $f$, giving the plan $F'$.
- Extra budget $b_{\ell f}-b_{\ell F_\ell}$, with $b_{\ell 0}:=0$ for a line that is not open yet; $b_{\ell f}=1+c^V_\ell f$.
- Gain $g=\Phi(F)-\Phi(F')$.

**Main loop**:

```text
F = empty plan (F_ℓ = 0 for all ℓ);  spent = 0
loop:
    for each line ℓ, for each f > F_ℓ (in increasing f):
        extra = b[ℓ,f] - b[ℓ,F_ℓ]
        if spent + extra > B_t: stop for this line (larger f is only more expensive)
        compute g, recomputing only the ODs touched by itineraries that contain ℓ
        keep the action with the largest g/extra and g>0
    if there is no such action: finish
    apply that action; spent += extra; update the costs of the affected ODs
output: F for both periods
```

## 3. Results

All Gurobi runs: the line-level form, M=242, K=10, 8 threads, default settings unless stated otherwise; for the reduced instances the budget is scaled by demand share (16.8 / 24.9 / 35.3), and for the full instance the budget is 60.
"Greedy + flow LP" is the true objective value obtained by fixing the $z$ of the greedy plan and solving only the flow LP (`scripts/evaluate_plan.py`); it contains no integer search. The LP value agrees exactly with what the Gurobi MILP computes internally, which validates the evaluator itself.

### 3.1 Full instance (budget 60)

| Method | Objective | Gap | Service rate | Time |
|---|---|---|---|---|
| **Greedy + flow LP (alone)** | **7,372,414** | — | 0.933 | about 181 + 2 s |
| Gurobi default | 11,507,931 | 61.5% | 0.738 | 900 s |
| Gurobi default, 183 s limit | 11,507,931 | 62.5% | 0.738 | 188 s |
| Gurobi, MIPFocus=1, Heuristics=0.2 | 11,507,931 | 61.6% | 0.738 | 900 s |
| Gurobi, warm start from the top-80 plan | 11,507,931 | 61.5% | 0.738 | 900 s |
| Gurobi, Heuristics=0 | **no feasible solution** | — | — | 900 s |
| Gurobi, warm start from the greedy plan | 7,372,414 | 39.9% | 0.933 | 900 s |

Key points:
- The objective of the greedy plan alone is exactly equal to the final value of "greedy warm start + Gurobi for 900 s", i.e. **Gurobi did not improve the greedy plan**.
- The three Gurobi runs (default, tuned, top-80 warm start) obtain the same objective and appear to converge to the same plan (13 / 9 lines). With a 183-second limit it is already this value, and over the remaining 700-plus seconds there is no further improvement.
- Greedy is about 36% better than default Gurobi (true under both the 183-second and the 900-second basis).
- With `Heuristics=0` Gurobi finds no feasible solution at all, not even "all unserved", which shows that its result depends entirely on its own heuristics.
- The lower bound is about 4.4M and barely changes; the gap reduction of the greedy warm start comes entirely from the upper bound.

### 3.2 Reduced instances (top-N, budget scaled by demand share, **not a conclusion about the full instance**)

**What "top-N" means.** The full instance has 416 OD pairs and cannot be solved, so smaller instances are cut from it. "top-N" keeps only the $N$ OD pairs $(o,d)$ with the largest base demand $D_{od}$ and drops all other ODs from the model. Ties in demand are broken by the OD key (`benchmark_2026/pipeline.py`, `top_od_subset`; command-line option `--top-od N`). Everything else is unchanged: the candidate line pool $\mathcal L$ still has all 93 lines, the itineraries of the kept ODs are the same as in the full instance, and the parameters $M$, $\beta$, $C_\ell$ and $K$ are not changed.

Since the dropped ODs no longer need capacity, the budget 60 would be far too generous for a reduced instance. The budget $B_t$ is therefore scaled by the share of total demand that the kept ODs carry: $B_t=60\cdot\sum_{(o,d)\in\text{top-}N}D_{od}\big/\sum_{(o,d)}D_{od}$. For example, the top-10 ODs carry 18.6% of the demand, which gives $B_t=60\times0.186=11.2$; the top-20/40/80 instances give 16.8, 24.9 and 35.3 in the table below.

A reduced instance is a separately labelled experiment and not a version of the main experiment: the result JSON carries `reduced_instance=true` and `budget_is_primary_60=false`. Its purpose is to see at which scale the solver starts to struggle (a "scale gradient"), not to draw conclusions about the full Randstad-IC instance.

| Instance | Budget | Greedy + flow LP | Greedy time | Gurobi 900 s | Gurobi, equal time |
|---|---|---|---|---|---|
| top-20 | 16.8 | 2,691,706 | 0.3 s | 2,684,906 (optimal, 3 s) | 2,691,706 (1 s, gap 8.6%) |
| top-40 | 24.9 | 4,363,799 | 4.9 s | **3,671,316** (gap 5.3%) | 9,766,756 (7 s, all unserved) |
| top-80 | 35.3 | 5,563,472 | 11.1 s | 5,530,710 (gap 31%) | 13,972,441 (15 s, all unserved) |

The "equal time" limit is the greedy time plus the evaluation LP time, rounded up (1 / 7 / 15 / 183 s).

Key points:
- top-20 has a proven optimal value: greedy is off by only 0.25%; the plan Gurobi finds at 1 second is exactly the same as greedy's.
- top-40: given 900 seconds, Gurobi is 16% better than greedy (greedy is at least 18.9% worse than Gurobi's best solution; by Gurobi's lower bound, at most about 25% from the optimum).
- top-80: at 900 seconds the two are roughly level (greedy 0.6% worse), but Gurobi's gap is 31%, so it may itself be far from optimal.

## 4. Conclusions (what can be written)

- **Under the same wall-clock time**, greedy is no worse than default Gurobi at every tested scale, and clearly better on top-40 and larger instances.
- **Given Gurobi more time**, at the scale where it can search effectively (top-40) Gurobi overtakes greedy; on the full instance it stalls at 11.5M and greedy still leads by about 36%.
- So a heuristic has two kinds of value: it is better when time is tight, and it is better when the scale is large enough that Gurobi stalls. The dividing line is roughly between top-40 and top-80; top-25 / top-30 were not tested, so the position is imprecise.
- One cannot say "greedy is better than Gurobi under every time budget", nor that the greedy design is excellent, because no other scoring rule was compared.

## 5. Limitations

- **Ignores capacity**: when choosing the plan the per-direction capacity of 1500 is not considered, so the estimate is optimistic; the true objective is given by the evaluator's flow LP.
- **Uses only the period-2 demand**, and uses the same plan in both periods (so the similarity constraint $\sum_\ell\Delta^t_\ell\le K$ is automatically satisfied, with $\Delta^t_\ell=0$); it does not exploit the multi-period structure.
- **Greedy, no backtracking**: each step looks only at the current gain/budget. Being 19% worse than Gurobi on top-40 shows it stops at a rather poor local optimum; the cause was not investigated.
- **No tuning and no comparison with other scoring rules** (e.g. gain only, without dividing by budget).
- **Runtime grows with scale** (0.3 → 4.9 → 11.1 → 181 s); pure Python, not optimized.
- **Single runs only**; Gurobi was not repeated with other random seeds, so none of the differences is a statistical conclusion.
- **Different thread counts**: Gurobi uses 8 threads and greedy uses 1, which favours greedy; Gurobi's time limit covers only the solve phase and not Python model-building time.
- With limits of only 1–15 seconds, Gurobi's result is dominated by presolve and the root-node LP, so the "all unserved" result partly reflects a too-short limit.
- **Only one tuning set was tried** (MIPFocus=1, Heuristics=0.2); it does not show that Gurobi fails under every setting.
- **K=10 is non-binding in most instances** (the total two-period change on top-20/40 is 0–2; on top-10 K=10 and K=1000 give the same result; only top-80 hits it exactly). These results are therefore essentially equivalent to ignoring similarity and cannot be used to discuss the effect of K. No sensitivity analysis of K and M was done (user's decision); this deviates from the handoff document's requirement "run at least $M\in\{161,242,403\}$".

## 6. Reproduction

```bash
# Greedy (about 3 minutes on the full instance; for subsets use --top-od N --budget B)
PYTHONPATH=. python scripts/greedy_plan.py results/greedy_plan.json
PYTHONPATH=. python scripts/greedy_plan.py results/greedy_plan_top40.json --top-od 40 --budget 24.9

# Fixed-z flow-LP evaluator
PYTHONPATH=. python scripts/evaluate_plan.py results/greedy_plan.json results/gurobi_gradient/eval_greedy_plan.json
PYTHONPATH=. python scripts/evaluate_plan.py results/greedy_plan_top40.json out.json --top-od 40 --budgets 24.9,24.9

# Gurobi comparison
python -m benchmark_2026 solve --formulation compact --periods 2 --M 242 --K 10 --solver gurobi --threads 8 --time-limit 900 --output out.json
#   extras: --initial-plan PLAN.json (warm start), --gurobi-param Heuristics=0 (repeatable)
scripts/run_gurobi_gradient.sh      # top-20/40/80, 900 s
scripts/run_equal_time.sh           # Gurobi with the same time as greedy
```

Result files are in `results/gurobi_gradient/`: `full_vanilla*.json`, `full_warm_*.json`, `top{20,40,80}.json`, `*_equal_time.json`, `eval_greedy_*.json`;
the greedy plans are in `results/greedy_plan*.json`.
