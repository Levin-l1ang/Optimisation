# Detailed problem formulation and code audit

> English version of `PROBLEM_FORMULATION_DETAILED_ZH.md`. The Chinese file is the original; if they differ, the Chinese file wins.

Basis: Section 5 of `docs/LINE_PLANNING_BENCHMARK_2026_HANDOFF_EN.md` (Chinese original `..._ZH.md`; the sole authority).
Audited code: `benchmark_2026/model.py` (specification model), `model_compact.py` (equivalent line-level form),
`trip_expansion.py`, `passenger_routes.py`, `pipeline.py`, `multiperiod.py`, `configs/randstad_ic_primary.yaml`.
Audit date: 2026-10-14. The handoff document was re-read after its 00:47 modification on that date.

## 1. What is the problem (in one sentence)

Within a fixed, finite candidate line pool, choose for each period which lines operate and at what frequency, so as to minimize the passengers' total generalized travel time plus the unserved-demand penalty,
subject to a budget, per-direction train capacity, at most one frequency per line per period, and a bound K on the total frequency change between adjacent periods.

## 2. Sets and data

| Symbol | Meaning | Value in this instance | Code location |
|---|---|---|---|
| $\mathcal T$ | Demand periods | 2 periods (optional 3-period robustness) | `multiperiod.py` |
| $\mathcal L$ | Candidate line pool, shared by all periods | 93 lines (frozen from the official Java generator) | `data/derived/randstad_ic/line_pool.json` |
| $\mathcal F_\ell$ | Positive frequency set of line $\ell$, $\{1,\dots,\bar f_\ell\}$ | $\bar f_\ell$ = minimum maxFrequency over its connections (2, 10 or 20) | `pipeline.frequency_options` |
| $(o,d)$ | OD pairs | 416, all kept in every period | `ODMatrix/dense.txt` |
| $D^t_{od}$ | Demand in period $t$ | $D^2_{od}=D_{od}$, $D^1_{od}=r_{od}D_{od}$, $r_{od}\sim U[0.5,0.9]$, seed 20261013 | `multiperiod.generate` |
| $\mathcal P_{od}$ | Passenger trip set | 126,158 itineraries; the specification model additionally expands by frequency | `passenger_routes.py`, `trip_expansion.py` |
| $C_\ell$ | Capacity per train and direction | 1500 | configuration, `CandidateLine.capacity` |
| $b_{\ell f}$ | Operating cost | $1 + c^V_\ell f$, $c^V_\ell=(2\,\mathrm{duration}_\ell+60)/60$ | `pipeline.build_config` |
| $B_t$ | Budget | $B_1=B_2=60$ | configuration |
| $\beta$ | Transfer penalty | 15 minutes | configuration |
| $M$ | Unserved penalty | 242 minutes per passenger (sensitivity 161, 403) | configuration |
| $K$ | Similarity bound between adjacent periods | Not prescribed by the formulation; the reduced experiments already run used 0, 10, 1000 (only an implementation record) | configuration `similarity_K` |

## 3. Variables

- $z^t_{\ell f}\in\{0,1\}$: whether line $\ell$ operates with frequency $f$ in period $t$. The actual frequency is $F^t_\ell=\sum_f f\,z^t_{\ell f}$.
- $x^t_{od,p}\ge0$: passenger flow assigned to trip $p$ in period $t$; it may be split among several trips.
- $u^t_{od}\ge0$: unserved demand.
- $\Delta^t_\ell\ge0$: frequency change between adjacent periods $(t,t+1)$.

## 4. Objective

$$
\min\ \sum_{t}\sum_{(o,d)}\sum_{p\in\mathcal P_{od}} c_p\,x^t_{od,p}\;+\;M\sum_{t}\sum_{(o,d)}u^t_{od}.
$$

Generalized time of a trip (specification model, after frequency expansion):

$$
c_p = T^{\mathrm{ride}}_p + \sum_{(\ell,f)\in S(p)}\frac{60}{2f} + \beta\,N^{\mathrm{transfer}}_p,\qquad \beta=15,\ T^{\mathrm{walk}}_p=0.
$$

- There is no walking component, $T^{\mathrm{walk}}=0$, so $\alpha$ does not affect the objective and need not be given a value.
- $T^{\mathrm{ride}}$ contains driving and intermediate dwell time; it contains neither the transfer penalty nor waiting.
- The waiting time $60/(2f)$ is this project's extension of the official benchmark objective (the official objective contains only route duration and transfer penalty). It is therefore a model extension on benchmark data, not a reproduction of the reference model.
- $M$ is a penalty coefficient in the objective; it is not a big-M used for linearization, and it does not guarantee that all demand is served first.

## 5. Constraints

1. **At most one frequency per line per period**: $\sum_{f}z^t_{\ell f}\le1$.
2. **Demand conservation**: $\sum_{p}x^t_{od,p}+u^t_{od}=D^t_{od}$. An OD without trips automatically goes entirely into $u$.
3. **Activation**: $x^t_{od,p}\le D^t_{od}\,z^t_{\ell f}$, for every $(\ell,f)\in S(p)$ used by $p$. (The new version of the handoff document dropped the $\min\{D,C_\ell f\}$ strengthening remark, see 7.1.)
4. **Per-direction capacity**: for each period $t$, line $\ell$, frequency $f$ and directed connection $e\in\ell$:
   $\sum_{(o,d)}\sum_p a_{e,\ell f,p}\,x^t_{od,p}\le C_\ell f\,z^t_{\ell f}$. The two directions are accumulated separately, each with capacity 1500.
5. **Budget**: $\sum_{\ell,f}b_{\ell f}z^t_{\ell f}\le B_t$.
6. **Similarity (adjacent periods only)**: $\Delta^t_\ell\ge\pm(F^{t+1}_\ell-F^t_\ell)$, $\sum_\ell\Delta^t_\ell\le K$.

## 6. Key rules of the data pipeline

- The line pool is generated only once and shared by all periods; demand volume does not affect the line pool.
- Trip pool: official DPTN routes → cut into legs at the transfer points → each leg is mapped to the pool lines that support it → keep itineraries whose duration ≤ shortest realizable + 15 minutes. **The filtering happens before the frequency expansion**; the duration includes the transfer penalty and excludes waiting, so the transfer penalty is not counted twice.
- At full scale the specification model has about 72.5 million trips per period and cannot be solved directly; solving uses the equivalent line-level form (see 7.2).

## 7. Code audit results

### 7.1 Item-by-item comparison

| Item | Specification requirement | Code | Verdict |
|---|---|---|---|
| Variables $z^t_{\ell f},x^t_{od,p},u^t_{od},\Delta^t_\ell$ | All present | Built in both `model.py` and `model_compact.py` | Consistent |
| Objective | $c_p x^t_{od,p} + M u^t_{od}$ | Specification: `generalized_time`; line-level: driving + transfer + aggregated waiting term + $M u^t_{od}$ | Consistent |
| Waiting time | $60/(2f)$ per boarding leg | `expand_itinerary` in `trip_expansion.py` accumulates per leg | Consistent |
| Transfer penalty not duplicated | Added only once | The itinerary's `ride_time` excludes the penalty, `transfer_time` is added once separately; route export checks `ride + penalty = duration` | Consistent |
| Frequency set | $\mathcal F_\ell=\{1,\dots,\bar f_\ell\}$ | `frequency_options` | Consistent (reduced experiments restrict it with `--frequencies`, and are flagged in the results) |
| One frequency per line | Yes | Both forms have it | Consistent |
| Demand conservation | Equality, with $u^t_{od}$ | Equality; the upper bound of $u^t_{od}$ is $D^t_{od}$ | Consistent |
| Activation | $x^t_{od,p}\le D^t_{od}z^t_{\ell f}$ (new document version) | The specification model `model.py` uses $\min\{D^t_{od},C_\ell f\}z^t_{\ell f}$, which is stronger than the document; the line-level form uses $x^t_{od,i}\le D^t_{od}\sum_f z^t_{\ell f}$, consistent with the document | Same feasible region: the capacity row of every $(\ell,f,e)$ already implies $x^t_{od,p}\le C_\ell f z^t_{\ell f}$, so the min is a valid strengthening and does not change the feasible region |
| Directional capacity | Per directed connection | Keyed by directed connection id, one row per direction | Consistent |
| Budget | One row per period | Yes | Consistent |
| Similarity | Adjacent periods only, $K$ | Yes; the upper bound of $\Delta^t_\ell$ is $2\bar f_\ell$, which does not affect the feasible region | Consistent |
| $K$ | Experimental parameter, recorded before running, including at least 0, one intermediate value and one value confirmed to be inactive | The configuration has [0,10,1000], with the comment "pre-fixed" | **Does not match the wording of the new document version, see 7.5** |
| $M$ without a hidden default | Required | `ModelConfig` requires it; an error is raised if $\max c_p\ge M$ | Consistent |
| No OD dropped | All 416 kept | An OD without routes goes into $u^t_{od}$ | Consistent |

### 7.2 Differences from the specification (all declared in the documents, none changes the optimal value)

1. **Equivalent line-level form**: itinerary flow $x^t_{od,i}$ and boarding flow $w^t_{\ell f}$ aggregated by (line, frequency) (symbols defined in Section 9) replace the frequency expansion. Waiting cost is separable across segments and each line has one frequency per period, so the optimal value is the same (cross-validated in tests against exhaustive enumeration and the specification model). The price is a weaker LP relaxation, because the activation constraint is $x^t_{od,i}\le D^t_{od}\sum_f z^t_{\ell f}$ without the $\min\{D^t_{od},C_\ell f\}$ strengthening.
2. **Removal of dominated same-line transfer itineraries**: 5,244 of them; this does not change the optimal value.
3. **Route window**: the official +15 window is not enough (for 50 ODs the shortest realizable route is longer than the infrastructure shortest route, by up to 17.63 minutes), so the export uses a +45-minute window, and Python then filters "shortest realizable + 15" and checks that the window is sufficient.

### 7.3 Additional checks made in this audit

- **The same line appearing twice in one itinerary**: in the specification $S(p)$ is a set of (line, frequency) pairs, whereas the line-level form counts per boarding leg; the two could differ on such an itinerary. Checked on all 126,158 itineraries: **0** such itineraries, at most 3 legs, and no repeated connection. So this difference does not arise in this instance.
- **Reduced experiments** (top-N, frequency restriction, scaled budget) are not formulation deviations; the result files carry the `reduced_instance` and `budget_is_primary_60` flags.

### 7.4 Changes brought by the new document version

- New at the start of Section 5: relative to the old draft the model changes in only four places (finite line pool, finite trip set, adding $u$, and the corresponding change of objective and conservation); the rest follows the original Overleaf. The code is consistent with this.
- New: when $\mathcal P_{od}=\varnothing$, conservation degenerates to $u=D$. In the code the $x$ of an OD without trips is empty and only $u$ remains, which is consistent.
- Removed: the strengthening remark $\min\{D,C_\ell f\}$ (see the table above).
- Changed: $K$ is no longer fixed to $\{0,10,1000\}$.

### 7.5 The K issue (needs the user's decision)

The new document version requires: the values of $K$ are to be recorded separately before the formal experiments, including at least $K=0$, one pre-selected intermediate value and one value confirmed to be inactive; $\{0,10,1000\}$ is only the record of reduced experiments that were run.

- `configs/randstad_ic_primary.yaml` still lists `[0, 10, 1000]` as the main configuration, with the comment "strict / intermediate (pre-fixed) / non-binding", which does not match the new wording; either the comment or the values need to change.
- In the reduced experiment $K=10$ and $K=1000$ give identical results, so 10 is already inactive on that instance and is not a meaningful "intermediate value".
- In the gradient experiments (top-20/40/80 and the full instance) only $K=10$ was run, so nothing can be said about the effect of $K$.
- The configuration was not changed, because the values are for the user to decide.

### 7.6 What was not checked

- Whether the durations, dwell times and maximum frequencies in the line pool and route pool match the official Java exactly: the documents state that this has been compared byte for byte by tests and Java regeneration; those tests were not re-run.
- Whether the official route generator's transfer-count limit ($\min(2,\text{minimum transfers}+1)$) is appropriate: the official rule is followed, without independent verification.
- The newly added `scripts/greedy_plan.py` is only a heuristic, used only for warm starts, and is not part of the formulation.

## 8. Conclusion

The code implements the formulation of Section 5; no missing variable or constraint was found. The line-level form is equivalent in integer optimal value but has a weaker LP relaxation, which is one possible reason for the large gap on the full instance.

## 9. The equivalent line-level form (used for solving)

This section uses the symbols of the original problem (Section 5 of the handoff document; the same symbols as Sections 3–5 above). Only three symbols are new, and they are marked where they appear: $\mathcal I_{od}$ , $x^t_{od,i}$ and $w^t_{\ell f}$ .

The specification model (Sections 3–5) is the definition. On the full instance it has about 72.5 million trips per period and cannot be built, so the numbers actually solved and reported come from the line-level form below. The two forms have the same optimal value for integer solutions, but their LP relaxations differ (see 9.9 and 9.10).

> **Typesetting note.** In these Markdown files an inline formula needs a space **outside** its dollar signs, between the closing `$` and any adjacent letter, punctuation or CJK character, and **no** space inside them: write ` $x$ ` , not `$ x $` . Otherwise some renderers break the formula.

### 9.1 The original problem in brief

Variables: $z^t_{\ell f}\in\{0,1\}$ ; $x^t_{od,p}\ge0$ with $p\in\mathcal P_{od}$ ; $u^t_{od}\ge0$ ; $\Delta^t_\ell\ge0$ . Actual frequency: $F^t_\ell=\sum_{f\in\mathcal F_\ell}f\,z^t_{\ell f}$ . For a trip $p$ , $S(p)$ is the set of $(\ell,f)$ it requires, and $a_{e,\ell f,p}=1$ if it uses service $(\ell,f)$ on directed connection $e$ .

Trip cost:

$$
c_p=T_p^{\mathrm{ride}}+T_p^{\mathrm{wait}}+\beta N_p^{\mathrm{transfer}},\qquad
T_p^{\mathrm{wait}}=\sum_{(\ell,f)\in S(p)}\frac{60}{2f}
$$

Constraints, for all $t$ :

$$
\begin{aligned}
&\sum_{f\in\mathcal F_\ell}z^t_{\ell f}\le1&&\text{(one frequency per line)}\\
&\sum_{p\in\mathcal P_{od}}x^t_{od,p}+u^t_{od}=D^t_{od}&&\text{(demand conservation)}\\
&x^t_{od,p}\le D^t_{od}\,z^t_{\ell f}\quad\forall(\ell,f)\in S(p)&&\text{(activation)}\\
&\sum_{(o,d)}\sum_{p\in\mathcal P_{od}}a_{e,\ell f,p}\,x^t_{od,p}\le C_\ell f\,z^t_{\ell f}\quad\forall\ell,f,e\in\ell&&\text{(directional capacity)}\\
&\sum_{\ell,f}b_{\ell f}\,z^t_{\ell f}\le B_t&&\text{(budget)}\\
&\Delta^t_\ell\ge\pm\bigl(F^{t+1}_\ell-F^t_\ell\bigr),\quad\sum_\ell\Delta^t_\ell\le K&&\text{(similarity)}
\end{aligned}
$$

### 9.2 Where the size comes from

An **itinerary** $i$ is a physical route without frequency information; it consists of legs, and leg $j$ rides line $\ell_j(i)$ . Expanding $i$ over the frequency of every leg gives the trips:

$$
p=(i,\varphi),\qquad \varphi=(f_1,\dots,f_m),\quad f_j\in\mathcal F_{\ell_j(i)},\qquad
S(p)=\bigl\{(\ell_1(i),f_1),\dots,(\ell_m(i),f_m)\bigr\}
$$

**New symbol 1:** $\mathcal I_{od}$ is the set of itineraries of OD pair $(o,d)$ . The quantities $T_p^{\mathrm{ride}}$ and $N_p^{\mathrm{transfer}}$ depend only on the itinerary, so they may be written $T_i^{\mathrm{ride}}$ and $N_i^{\mathrm{transfer}}$ .

| Per period | $\mathcal P_{od}$ (original) | $\mathcal I_{od}$ |
|---|---|---|
| Objects | 72,536,604 trips | 126,158 itineraries |
| Variables | 145,076,005 | 256,945 |

A 3-leg itinerary whose lines each allow 20 frequencies expands into $20^3=8000$ trips. 72.5 million trips cannot be built, so the expansion has to be avoided.

### 9.3 Two key observations

**Observation 1: waiting cost is separable across legs.**

$$
c_p=\underbrace{T_i^{\mathrm{ride}}+\beta N_i^{\mathrm{transfer}}}_{\text{depends only on the itinerary }i}+\underbrace{\sum_{j}\frac{60}{2f_j}}_{\text{one term per leg}}
$$

The waiting time of leg $j$ depends only on $f_j$ and not on the frequencies chosen for the other legs.

**Observation 2: each line has only one active frequency per period.** By the one-frequency constraint, in an integer solution line $\ell$ is either closed or has exactly one $f$ with $z^t_{\ell f}=1$ , namely $F^t_\ell$ . By the activation constraint, a trip $p$ can carry flow only if every $(\ell,f)\in S(p)$ has $z^t_{\ell f}=1$ , i.e. every leg uses exactly the active frequency of its line.

Hence for each itinerary $i$ **at most one** of its expanded trips can carry flow, namely $\varphi^*(i)=\bigl(F^t_{\ell_1(i)},\dots,F^t_{\ell_m(i)}\bigr)$ ; all the others have flow 0.

### 9.4 New variables: aggregate the bookkeeping by line

**New symbol 2:** $x^t_{od,i}\ge0$ is the flow of itinerary $i\in\mathcal I_{od}$ in period $t$ (no frequency index).

**New symbol 3:** $w^t_{\ell f}\ge0$ is the boarding flow of line $\ell$ in period $t$ while frequency $f$ is active (counted once per leg ridden).

Relation to the original variables:

$$
x^t_{od,i}=\sum_{p\text{ expanded from }i}x^t_{od,p},\qquad
w^t_{\ell f}=\sum_{(o,d)}\sum_{p:\ (\ell,f)\in S(p)}n_\ell(p)\,x^t_{od,p}
$$

where $n_\ell(p)$ is the number of legs of trip $p$ on line $\ell$ ( $n_\ell(i)$ for the itinerary, the same number). Also let $a_{e,\ell,i}=1$ if itinerary $i$ rides line $\ell$ over connection $e$ .

### 9.5 The line-level model: what each original constraint becomes

**Objective.** By Observation 1, $\sum_p c_p x^t_{od,p}$ splits into two terms:

$$
\sum_t\sum_{(o,d)}\sum_{i\in\mathcal I_{od}}\bigl(T_i^{\mathrm{ride}}+\beta N_i^{\mathrm{transfer}}\bigr)\,x^t_{od,i}
\;+\;\sum_t\sum_{\ell,f}\frac{60}{2f}\,w^t_{\ell f}
\;+\;M\sum_t\sum_{(o,d)}u^t_{od}
$$

**Constraints.**

| Original problem | Line-level form |
|---|---|
| One frequency per line $\sum_f z^t_{\ell f}\le1$ | Unchanged |
| Conservation $\sum_{p}x^t_{od,p}+u^t_{od}=D^t_{od}$ | $\sum_{i\in\mathcal I_{od}}x^t_{od,i}+u^t_{od}=D^t_{od}$ |
| Activation $x^t_{od,p}\le D^t_{od}\,z^t_{\ell f}$ | $x^t_{od,i}\le D^t_{od}\sum_f z^t_{\ell f}$ , for the line $\ell$ of every leg of $i$ |
| (implicit in the definition of $x^t_{od,p}$ ) | Boarding balance $\sum_f w^t_{\ell f}=\sum_{(o,d)}\sum_i n_\ell(i)\,x^t_{od,i}$ |
| (none) | Boarding link $w^t_{\ell f}\le W^t_\ell\,z^t_{\ell f}$ , with $W^t_\ell=\sum_{(o,d)}\sum_i n_\ell(i)\,D^t_{od}$ |
| Capacity (one row per $\ell,f,e$ ) | $\sum_{(o,d)}\sum_i a_{e,\ell,i}\,x^t_{od,i}\le C_\ell\sum_f f\,z^t_{\ell f}=C_\ell F^t_\ell$ (one row per $\ell,e$ ) |
| Budget, similarity | Unchanged (they involve only $z$ , $F^t_\ell$ , $\Delta^t_\ell$ ) |

The boarding balance converts itinerary flow into boardings on line $\ell$ ; the boarding link makes sure that a closed $(\ell,f)$ has no boarding flow, so waiting cost can only fall on the frequency that is really active.

### 9.6 Proof: original problem → line-level form

Take an integer feasible solution $(z,x,u,\Delta)$ of the original problem, and define $x^t_{od,i}$ and $w^t_{\ell f}$ as in 9.4. Check each constraint:

1. **Conservation:** $\sum_i x^t_{od,i}=\sum_p x^t_{od,p}$ , so $\sum_i x^t_{od,i}+u^t_{od}=D^t_{od}$ .
2. **Activation:** take a leg of $i$ on line $\ell$ . If $\ell$ is closed ( $\sum_f z^t_{\ell f}=0$ ), the original activation forces every expanded $x^t_{od,p}\le D^t_{od}\,z^t_{\ell f}=0$ , so $x^t_{od,i}=0$ . If $\ell$ is open, conservation gives $x^t_{od,i}\le D^t_{od}$ . In both cases $x^t_{od,i}\le D^t_{od}\sum_f z^t_{\ell f}$ .
3. **Boarding balance:** $w^t_{\ell f}$ is by definition a count per leg, so it holds.
4. **Boarding link:** if $z^t_{\ell f}=0$ , every trip with $(\ell,f)\in S(p)$ has flow 0, so $w^t_{\ell f}=0$ ; if $z^t_{\ell f}=1$ , then $w^t_{\ell f}\le W^t_\ell$ .
5. **Capacity:** sum the original capacity constraints over $f$ . The left side is the total flow of all trips that ride line $\ell$ over connection $e$ , which is $\sum_{(o,d)}\sum_i a_{e,\ell,i}\,x^t_{od,i}$ (this uses "an itinerary rides a given connection at most once", which was checked on the data); the right side is $\sum_f C_\ell f\,z^t_{\ell f}$ .
6. **Objective:** split $\sum_p c_p x^t_{od,p}$ as in 9.5; both sides are equal.

### 9.7 Proof: line-level form → original problem

Take an integer feasible solution of the line-level model. Each open line $\ell$ has a unique active frequency $F^t_\ell$ .

**Construction.** For each itinerary $i$ let $\varphi^*(i)=\bigl(F^t_{\ell_1(i)},\dots,F^t_{\ell_m(i)}\bigr)$ and put all the flow there:

$$
x^t_{od,(i,\varphi^*(i))}=x^t_{od,i},\qquad x^t_{od,(i,\varphi)}=0\ \ (\varphi\ne\varphi^*(i))
$$

If some leg of $i$ is on a closed line, the line-level activation already forces $x^t_{od,i}=0$ , so flow is never placed on a trip that does not exist.

**Check each constraint.**

1. **Conservation:** $\sum_p x^t_{od,p}=\sum_i x^t_{od,i}$ .
2. **Activation:** a trip with flow uses exactly the active frequency on every leg, so $z^t_{\ell f}=1$ for every $(\ell,f)\in S(p)$ , and $x^t_{od,p}=x^t_{od,i}\le D^t_{od}$ . The other trips have flow 0.
3. **Capacity:** for $f=F^t_\ell$ the original left side is $\sum_{(o,d)}\sum_i a_{e,\ell,i}\,x^t_{od,i}$ , the line-level capacity bounds it by $C_\ell F^t_\ell$ , and the original right side is exactly $C_\ell f\cdot1=C_\ell F^t_\ell$ . For $f\ne F^t_\ell$ the left side is 0 and the right side is $C_\ell f\cdot0=0$ .
4. **Objective:** the boarding link forces $w^t_{\ell f}=0$ for $f\ne F^t_\ell$ , and the boarding balance gives $w^t_{\ell F^t_\ell}=\sum_{(o,d)}\sum_i n_\ell(i)\,x^t_{od,i}$ ; so the waiting term of the line-level objective equals $\sum_p T_p^{\mathrm{wait}}x^t_{od,p}$ of the original.
5. Budget and similarity involve only $z$ and $\Delta$ , and are identical.

Both directions hold, so **the integer feasible solutions correspond one to one, with equal objective values and equal optimal values.**

### 9.8 A small numerical example

Lines $A$ and $B$ , both with frequency set $\{1,2\}$ . An OD has a single itinerary $i$ : ride $A$ , then $B$ , with $T_i^{\mathrm{ride}}=30$ , $N_i^{\mathrm{transfer}}=1$ , $\beta=15$ , $D^t_{od}=100$ .

**Original problem:** four trips.

| $(f_A,f_B)$ | $c_p=T_p^{\mathrm{ride}}+T_p^{\mathrm{wait}}+\beta N_p^{\mathrm{transfer}}$ |
|---|---|
| (1,1) | 30 + (30 + 30) + 15 = 105 |
| (1,2) | 30 + (30 + 15) + 15 = 90 |
| (2,1) | 30 + (15 + 30) + 15 = 90 |
| (2,2) | 30 + (15 + 15) + 15 = 75 |

Let $z^t_{A,2}=1$ and $z^t_{B,1}=1$ . Activation leaves only trip (2,1) usable, so $x^t_{od,p}=100$ and the total cost is $90\times100=9000$ .

**Line-level form:** $x^t_{od,i}=100$ , $w^t_{A,2}=100$ , $w^t_{B,1}=100$ , with total cost

$$
(30+15)\times100+\frac{60}{2\cdot2}\times100+\frac{60}{2\cdot1}\times100=4500+1500+3000=9000
$$

The costs are identical, but the line-level form has one flow variable and two boarding variables instead of four trip variables.

### 9.9 When the equivalence does not hold

1. **Only for integer $z$ .** After relaxing to $0\le z^t_{\ell f}\le1$ the two LP relaxations differ. The original activation $x^t_{od,p}\le D^t_{od}\,z^t_{\ell f}$ controls each $(\ell,f)$ separately (and the specification code actually uses the stronger $\min\{D^t_{od},C_\ell f\}\,z^t_{\ell f}$ ); the line-level $x^t_{od,i}\le D^t_{od}\sum_f z^t_{\ell f}$ adds the frequencies together. The line-level LP bound is usually lower.
2. **The same line ridden twice in one itinerary.** In the original problem $S(p)$ is a set of $(\ell,f)$ pairs, while the line-level form counts per leg, so they would differ by a multiplicity. On all 126,158 itineraries there are **0** such itineraries.
3. **The same connection used twice by one itinerary.** The merging of the capacity rows relies on "at most once". Also checked: none.

### 9.10 The strengthened activation (tried, not adopted)

The LP of the line-level form can be tightened by replacing the activation with

$$
x^t_{od,i}\ \le\ \sum_{f\in\mathcal F_\ell}\min\{D^t_{od},\,C_\ell f\}\,z^t_{\ell f}\qquad\text{for the line }\ell\text{ of every leg of }i
$$

**Proposition.** For integer solutions this inequality is valid; it leaves the integer feasible region unchanged and only tightens the LP relaxation.

**Proof.** Take an integer solution satisfying the original line-level constraints, and let $F$ be the active frequency of $\ell$ in period $t$ ( $F=0$ if closed). The right-hand side is then $\min\{D^t_{od},C_\ell F\}$ . First, $x^t_{od,i}\le D^t_{od}$ follows from conservation and $u^t_{od}\ge0$ . Second, every leg of $i$ contains at least one connection $e$ ; the directional capacity row for $(\ell,e)$ contains $x^t_{od,i}$ on its left and all other terms are nonnegative, so $x^t_{od,i}\le C_\ell F^t_\ell=C_\ell F$ . If the line is closed the right-hand side is 0, which agrees with the original constraint. So every original feasible solution satisfies the inequality, and the integer feasible regions coincide. $\blacksquare$

**Status: tried, not adopted.** The switch has been removed from the code. Results (Gurobi, $M=242$ , $K=10$ , scaled budget):

| Instance | Root LP bound (original → strengthened) | MILP |
|---|---|---|
| top-20 | 1,195,502 → 1,200,998 (+0.46%) | Same objective; both optimal in 3 seconds |
| top-40 | 1,789,987 → 1,790,080 (+0.005%) | 900 s: same objective, gap 5.3% → 10.3% (single run, noise not excluded) |
| top-80 | 2,529,730 → 2,529,912 (+0.007%) | MILP not run |

Conclusion: the activation constraint is not the main reason for the weak LP relaxation, so the strengthening is not added to the code. When reporting an LP bound or a gap, state which form is used; the code uses the original line-level activation.

### 9.11 Suggested wording for reports

> We solve the line-level equivalent form of the specification model. Their integer feasible solutions correspond one to one and have the same objective value; the specification model remains the problem definition. The line-level form is used only to avoid the Cartesian expansion over frequency combinations.
