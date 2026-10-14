# 2026 Line Planning Benchmark Migration Handoff

> English translation of `LINE_PLANNING_BENCHMARK_2026_HANDOFF_ZH.md` (source version last modified 2026-10-14 00:47). The Chinese file remains the authoritative original; if the two differ, the Chinese file wins. Mathematical content and identifiers are unchanged.

## 1. Project decisions

This project no longer uses Bahn-01, the reconstructed Germany-rail data, or subsets of either as paper experiment data.

Future experiments use the 2026 Line Planning Benchmark published by van Lieshout and van Rossum, and implement the multi-period passenger-oriented line planning model according to the modified problem formulation given in Section 5 of this document.

This document is the single entry point for subsequent coding agents. An agent does not need to read the old Bahn handoff documents or `docs/changes.md` to understand the task; if an old document conflicts with this one, this one wins.

### 1.1 Coding agent tasks

Continue and verify the following work in the current repository:

1. First read the complete formulation in Section 5 and the implementation errata in Section 14 of this document;
2. Run the tests, and re-check the pinned benchmark, the 93-line pool, the route pool, the two-period OD data, and $M=242$;
3. Keep the specification model and the compact equivalent model consistent;
4. Keep improving the solution of the full Randstad-IC instance without changing the mathematical model of Section 5;
5. Clearly separate reduced experiments from the full instance; do not treat reduced results as main-experiment conclusions;
6. Update the README, tests and result notes, and remove the active workflow's dependency on Bahn data.

Unless the user explicitly authorizes it, do not run all 972 benchmark combinations, do not delete the old code listed in Sections 9.2 or 9.3, and do not overwrite current uncommitted changes.

### 1.2 Current implementation status

- The official 2026 benchmark is pinned in `data/external/line-planning-benchmark/`;
- The 93-line pool, DPTN route export, OD lower bounds and two-period OD data are frozen in `data/derived/randstad_ic/`;
- `benchmark_2026/model.py` implements the specification model of Section 5, and `benchmark_2026/model_compact.py` implements and verifies the equivalent compact formulation;
- All 416 OD pairs have retained routes, $M=242$, and $\max_p c_p=179.09<242$ has been verified;
- Small instances, Randstad-IC subsets and sensitivity experiments are done;
- In a 900-second HiGHS run, the full Randstad-IC found no solution better than the all-unserved initial solution, so no feasible improving solution or optimality conclusion for the full instance can yet be reported;
- The repository still contains legacy Bahn/Germany-rail content and uncommitted changes; cleanup must follow Sections 9–11;
- For details on completed work, verification results and limitations, see Section 14 and `docs/MIGRATION_STATUS_2026.md`.

## 2. Fixed sources of the new benchmark

- Repository: <https://github.com/rn-van-lieshout/line-planning-benchmark>
- Paper: <https://arxiv.org/abs/2606.24386>
- Multi-period reference: <https://doi.org/10.1007/s43069-023-00268-7>
- Verification date: 2026-10-13
- Verified commit: `5d0aae68588701285c42ba47e593fbeb600cbc5c`
- License: MIT

Future experiments must record the benchmark commit. Do not write only "latest version", otherwise results cannot be reproduced after the repository changes.

The benchmark contains six networks and 972 parameter combinations. The first phase uses only the smallest complete network, `Randstad-IC`, and does not remove stations, lines or OD pairs on its own.

The roles of the three sources must be stated separately:

- The 2026 benchmark provides the network, the single-period OD matrix, the generation parameters and the reference algorithms;
- Schiewe, Schöbel and Sieber (2023) provide the basis for the multi-period structure, including a PTN and line pool shared by all periods, monotone demand periods, and similarity between periods;
- This project converts the single-period OD matrix into multi-period OD matrices, and must publish the concrete random distribution, seed and generated files.

This project combines and extends the two works; it is not an item-by-item reproduction of the computational experiments of either paper.

## 3. Fixed main-experiment configuration

| Item                            | Choice        | Source or explanation                                                                                  |
| ------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------ |
| Network                         | `Randstad-IC` | Smallest public complete network                                                                       |
| OD matrix                       | `dense.txt`   | 416 OD pairs                                                                                           |
| Line generation                 | `small.txt`   | Detour ratio 1.1; the 60 in the file only limits Sprinter duration, and Randstad-IC is IC-only          |
| Passenger-route generation      | `medium.txt`  | Allows a 15-minute detour over the shortest realizable route                                           |
| Transfer penalty                | `medium.txt`  | 15 minutes                                                                                             |
| Fixed line cost                 | `medium.txt`  | 1 cost unit per activated line                                                                         |
| Budget                          | `medium.txt`  | 60 cost units                                                                                          |
| Planning period                 | 60 minutes    | Constant in the reference implementation                                                               |
| Vehicle capacity                | 1500          | Per train, per direction                                                                               |
| Uniform unserved-demand penalty | $M=242$       | Converted from the official OD-specific penalty rule, see Section 5.3                                  |

The instance name in the paper should be written in full:

> Randstad-IC, dense OD, small line-generation setting, medium route-generation setting, medium transfer penalty, medium fixed cost, and medium budget.

Do not write just "Randstad dataset", because the benchmark contains 162 different Randstad-IC parameter combinations.

### 3.1 Verified sizes

- Number of stations used for this network in the paper: 29;
- Directed connections: 80;
- Dense OD pairs: 416;
- Small / medium / large line pool: 93 / 140 / 162.

`stations.txt` actually contains 110 station metadata records, but `connections.txt` references only 29 active stations. The reader must not interpret the 110 metadata records directly as 110 network nodes of this instance.

### 3.2 Two files that are easy to misread

`LineGeneration/small.txt` is not a candidate line list but the line-generation parameters:

```text
1.1
60
```

The benchmark uses these parameters and the infrastructure constraints to generate a finite line pool. The expected number of lines for the small setting is 93.

The second line, `60`, is used in the official generator only to limit the duration of Sprinter lines. Randstad-IC is IC-only, so it must not be written as a 60-minute cap on all lines of this instance; the longest line in the frozen 93-line pool is 101.49 minutes.

`RouteGeneration/medium.txt` is likewise not a passenger-trip list. The `15` in the file means that a route may exceed the shortest realizable route by at most 15 minutes of detour.

Therefore the new code must first generate and freeze the candidate line pool, and only then generate the passenger-trip sets. The lines in `bestSolutions/` are only the lines operated in some solved solution, and must not be treated as the complete candidate line pool.

Unlike the line pool, the OD pairs and base demand volumes are ready-made data. Every row of `ODMatrix/dense.txt` already gives `origin destination volume`. Dense and sparse are two single-period OD versions, not two demand periods; the main experiment uses only dense, and does not stitch dense and sparse together into multi-period data.

## 4. Mapping between benchmark files and model parameters

| Modified formulation              | Benchmark source                                  | Implementation requirement                                                         |
| --------------------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Network $G$                       | `stations.txt`, `tracks.txt`, `connections.txt`   | Use directed connections; also read reverse connections                            |
| Candidate line set $\mathcal L$   | Infrastructure files + `LineGeneration/small.txt` | Generate with the official rules, then persist; record generator version and hash  |
| Base OD demand $D_{od}$           | `ODMatrix/dense.txt`                              | Ready-made single-period data; keep all 416 OD pairs, then generate $D_{od}^{t}$ as in Section 5.5 |
| Passenger trips $\mathcal P_{od}$ | Generated $\mathcal L$ + `RouteGeneration/medium.txt` | Apply the +15-minute filter before frequency expansion                         |
| Transfer penalty                  | `transferPenalty/medium.txt`                      | 15 minutes                                                                         |
| Frequency upper bound             | `maxFrequency` in `connections.txt`               | A line's upper bound is the minimum upper bound over the connections it traverses  |
| $C_\ell$                          | Reference constant `CAPACITY=1500`                | Capacity per train, per direction                                                  |
| $B_t$                             | `budget/medium.txt`                               | The public single-period value is 60; the main experiment fixes $B_1=B_2=60$       |
| Fixed activation cost             | `fixedCost/medium.txt`                            | 1                                                                                  |
| Variable line cost                | Line duration and the reference formula           | See the formula below                                                              |

The reference implementation uses, for a line:

$$
c_\ell^{V}
=
\frac{2\,\mathrm{duration}_\ell+2\times30}{60},
$$

so the binary frequency-selection model can be written as:

$$
b_{\ell f}=1+c_\ell^{V}f.
$$

Here the `2` stands for the two operating directions, 30 minutes is the turnaround time at each end, and 60 minutes is the planning period.

## 5. The modified problem formulation to implement

This section gives the complete mathematical model, not just the changes relative to the old draft. The implementer does not need to piece together missing variables and constraints from the old Overleaf, `docs/changes.md` or other handoff documents. If the old draft conflicts with this section, this section wins; MILP structure of the old draft that was not changed is retained here.

The model structure undergoes only the following four modifications: $\mathcal L$ becomes a finite candidate line pool; $\mathcal P_{od}$ becomes a finite retained trip set; $u_{od}^{t}$ is added; and the objective and the demand equality are changed accordingly. All other variables and constraints follow the original Overleaf. The concrete values for capacity, budget, transfer penalty, $M$ and multi-period demand are benchmark mappings or explicitly recorded experimental settings; they are not additional decision variables or constraints.

Use a directed graph $G=(V,E)$ to represent the public transport network, where $V$ is the set of stations, $E$ is the set of directed connections, and $w_e$ is the travel time of connection $e$. $\mathcal T$ is the set of demand periods, and $D_{od}^{t}$ is the demand of OD pair $(o,d)$ in period $t$.

### 5.1 Finite candidate line pool

Define the finite candidate line set:

$$
\mathcal L
\subseteq
\{\ell\mid \ell\text{ is an operationally feasible simple path in }G\}.
$$

Generate and freeze $\mathcal L$ according to the small line-generation setting. The MILP only selects line frequencies within this set, and does not generate new lines arbitrarily during solving.

For each line $\ell$, let $\mathcal F_\ell$ be the set of allowed positive frequencies:

$$
\mathcal F_\ell=\{1,\ldots,\overline f_\ell\},
\qquad
\overline f_\ell
=
\min_{e\in\ell}\{\text{maxFrequency}_e\}.
$$

Not selecting a line means frequency 0; 0 need not be put into $\mathcal F_\ell$. The line capacity is fixed at:

$$
C_\ell=1500,
$$

meaning the passenger capacity per train and per operating direction. For frequency option $f$, the operating cost is:

$$
b_{\ell f}=1+c_\ell^{V}f,
\qquad
c_\ell^{V}
=
\frac{2\,\mathrm{duration}_\ell+2\times30}{60}.
$$

Here $\mathrm{duration}_\ell$ must be taken directly from the single-direction line duration that the official line generator computes on the DPTN arc path, including driving time and intermediate dwell time. The old Overleaf simply wrote $T_\ell=\sum_{e\in\ell}w_e$; if $w_e$ contains only the connection driving time, that old formula misses the dwell time and cannot be used directly in the new implementation.

All demand periods share the same candidate line set $\mathcal L$. What changes per period is which candidate lines satisfy $F_\ell^t>0$ and what their specific frequencies are; no period-specific line pool is defined.

Demand volume plays no role in the physical feasibility of a line, so changing only $D_{od}^{t}$ does not require regenerating the line pool. Period-specific pools would be needed only if the infrastructure, forbidden turns, train types or other operational feasibility rules changed between periods; this project does not consider that case.

If the official line-generation logic is rewritten in Python, at least the following must be verified:

- The small pool generates exactly 93 lines;
- Each line complies with the forbidden-turn, terminal, train-type and duration rules;
- The meaning of forward and reverse service matches the reference implementation;
- The maximum frequency, duration and cost of each line match the Java reference.

Until these checks pass, the generated result can only be called "our reimplementation of the benchmark line generator", and must not be called the official precomputed line pool.

### 5.2 Finite passenger-trip set

For each OD pair, generate a finite passenger-trip set using the frozen $\mathcal L$:

$$
\mathcal P_{od}
=
\left\{
p\mid p\text{ is a retained feasible trip from }o\text{ to }d
\text{ using lines in }\mathcal L
\right\}.
$$

Use the medium route-generation policy:

$$
\text{route duration}
\leq
\text{shortest realizable route duration}+15.
$$

The filtering happens before the frequency combinations are expanded. Afterwards each itinerary is expanded into the frequency-specific trips required by the modified formulation.

The route duration used for filtering here includes in-vehicle time, intermediate dwell time and the 15-minute transfer penalty per transfer, but not frequency-dependent waiting time. The implementation must not, after the route duration already includes the transfer penalty, add the same penalty again in $c_p$.

For an expanded trip $p$:

- $S(p)$ is the set of line-frequency combinations $(\ell,f)$ that the trip requires to be active;
- $a_{e,\ell f,p}=1$ means trip $p$ uses service $(\ell,f)$ on directed connection $e$, and 0 otherwise.

Because all periods share the network, the candidate line pool and the route-generation policy, the base passenger-trip pool is also generated only once and reused across periods. What changes per period is line activation, frequencies and passenger flow, not a re-enumeration of a different set of physical routes.

The original Overleaf penalizes transfers only through $\beta N_p^{\mathrm{transfer}}$ in $c_p$; it specifies no maximum number of transfers and no corresponding MILP constraint. When constructing the finite $\mathcal P_{od}$, the 2026 benchmark reference route generator separately filters the number of transfers: for each OD it uses $\min\{2,\text{transfers of the shortest path}+1\}$ as the limit. This is a passenger-route preprocessing rule of the benchmark, not an added model constraint. The implementation must also obey the other infrastructure-feasibility rules; Yen shortest paths run on a plain PTN must not directly replace the official route-generation logic unless verified item by item.

### 5.3 Decision variables, unserved demand and objective

The decision variables of the model are:

$$
z_{\ell f}^{t}
=
\begin{cases}
1,&\text{line $\ell$ operates with frequency $f$ in period $t$},\\
0,&\text{otherwise},
\end{cases}
$$

$$
x_{od,p}^{t}\geq0,
$$

where $x_{od,p}^{t}$ is the passenger flow assigned to trip $p\in\mathcal P_{od}$ in period $t$. Passenger flow may be split among several trips.

The actual frequency of line $\ell$ in period $t$ is:

$$
F_\ell^t
=
\sum_{f\in\mathcal F_\ell}fz_{\ell f}^{t}.
$$

The frequency difference between adjacent periods uses the auxiliary variable $\Delta_\ell^t\geq0$. For each period and OD pair, the unserved-demand variable is:

$$
u_{od}^{t}\geq0,
$$

and the following is used:

$$
\sum_{p\in\mathcal P_{od}}x_{od,p}^{t}+u_{od}^{t}=D_{od}^{t}.
$$

If $\mathcal P_{od}=\varnothing$, this constraint degenerates to $u_{od}^{t}=D_{od}^{t}$, so the whole MILP does not become structurally infeasible because the finite trip set fails to cover some OD pair.

The old `passenger_line_planning/model.py` does not implement this variable and rejects OD pairs without trips, so it cannot serve as the final model. The current active implementation is `benchmark_2026/model.py`, which supports $u_{od}^{t}$, together with the equivalence-verified `benchmark_2026/model_compact.py`.

The objective function is:

$$
\min
\sum_{t\in\mathcal T}
\sum_{(o,d)}
\sum_{p\in\mathcal P_{od}}
c_p x_{od,p}^{t}
+
M\sum_{t\in\mathcal T}\sum_{(o,d)}u_{od}^{t}.
$$

The 2026 paper uses an OD-specific penalty $\pi_{od}$ in its formulation, but the paper body gives no uniform $M$ and does not explicitly write the numerical factor 3. The official reference code accompanying the paper uses:

$$
\pi_{od}=3\underline c_{od},
$$

where $\underline c_{od}$ is the shortest travel time of OD pair $(o,d)$ on the DPTN. The factor 3 comes from `MISSED_DEMAND_PENALTY_FACTOR = 3.0` in the official code, and `getMissedDemandPenalty` multiplies it by `lowerBoundPerOD`.

To keep the uniform $M$ of this document's formulation, the main experiment converts the official OD-specific rule into:

$$
M
=
\left\lceil
3\max_{(o,d)}\underline c_{od}
\right\rceil.
$$

Under the fixed Randstad-IC, dense OD and medium transfer-penalty configuration, the largest DPTN shortest travel time among the 416 OD pairs is:

$$
\max_{(o,d)}\underline c_{od}
=80.4284615\text{ minutes},
$$

for the OD between `Lls` (Lelystad Centrum) and `Ddr` (Dordrecht). Therefore:

$$
M=\lceil3\times80.4284615\rceil=242.
$$

The main experiment fixes:

$$
\boxed{M=242\text{ minutes per unserved passenger}}.
$$

Both periods use the same $M$; it must not be multiplied by the number of periods or by the demand. If the implementation changes the unit of $c_p$ to hours, use $M=242/60$ accordingly. The implementation must make $M$ an explicit configuration value and write it into the result metadata; no hidden default.

The basis must be stated accurately: the unserved-demand variable and the OD-specific penalty $\pi_{od}$ come from the 2026 paper; the factor 3 comes from the paper's official reference code; the uniform value $M=242$ is this project's conversion of the official rule for the fixed Randstad-IC instance, and is not a number reported directly by the paper.

At least run the following sensitivity check:

$$
M\in\{161,242,403\},
$$

corresponding to factors 2, 3 and 5. Report whether service rate, objective and the selected line frequencies change under each value. For the frozen main-experiment trip pool, $\max_p c_p=179.09<242$ has been verified; after regenerating the trip pool or changing the frequency set, this value must still be recomputed and recorded. If the new $\max_p c_p$ is not smaller than 242, $M$ must not be silently increased; first report it and re-confirm the penalty interpretation.

Here $M$ is the unserved-demand penalty coefficient in the objective; it is not a classic Big-M used to linearize constraints, and it does not constitute a strict lexicographic service guarantee. If the research goal requires minimizing unserved demand unconditionally first, two-stage optimization should be used instead of continuing to increase $M$.

Verification locations:

- Paper formulation: <https://arxiv.org/html/2606.24386#S3>;
- Official parameters: <https://github.com/rn-van-lieshout/line-planning-benchmark/blob/5d0aae68588701285c42ba47e593fbeb600cbc5c/src/linePlanning/instance/LinePlanningParameters.java#L12>;
- Official penalty computation: <https://github.com/rn-van-lieshout/line-planning-benchmark/blob/5d0aae68588701285c42ba47e593fbeb600cbc5c/src/linePlanning/instance/LinePlanningInstance.java#L476-L479>.

### 5.4 Generalized travel time and frequency-dependent waiting

The benchmark reference objective is based mainly on route duration and transfer penalty, and does not directly use the $60/(2f)$ random-arrival waiting time of the current code.

This project keeps the frequency-specific waiting time. It is therefore "a model extension on 2026 benchmark data", not a faithful reproduction of the benchmark reference model. Documents, code and result tables must all state this.

The generalized travel time of each frequency-specific trip keeps the definition of the original model:

$$
\begin{aligned}
c_p={}&T_p^{\mathrm{ride}}+T_p^{\mathrm{wait}}
+\alpha T_p^{\mathrm{walk}}
+\beta N_p^{\mathrm{transfer}}.
\end{aligned}
$$

The main experiment uses:

$$
T_p^{\mathrm{wait}}
=
\sum_{(\ell,f)\in S(p)}\frac{60}{2f},
\qquad
T_p^{\mathrm{walk}}=0,
\qquad
\beta=15.
$$

The 2026 benchmark provides no walking component, so the main experiment sets $T_p^{\mathrm{walk}}=0$; in this case $\alpha$ in the original formula does not affect the objective, and no new value needs to be specified artificially.

$T_p^{\mathrm{ride}}$ contains only riding and intermediate dwell time; $\beta N_p^{\mathrm{transfer}}$ adds the transfer penalty separately. If the duration output by the official route generator, which already contains the transfer penalty, is read directly, the transfer component must first be split off, or that duration must be used directly as the pre-wait route cost; $\beta N_p^{\mathrm{transfer}}$ must not be added again.

The old `passenger_line_planning/trip_expansion.py` defaults to `beta=5.0`, which does not match the fixed medium transfer-penalty configuration and must not continue to be used in the active workflow. The current `benchmark_2026/trip_expansion.py` takes 15 explicitly from configuration; the related tests must keep checking that the transfer penalty is not counted twice.

### 5.5 Multi-period OD

The 2026 benchmark provides only a single-period OD matrix, and no multi-period OD data labelled by time such as morning peak or off-peak. What is generated here is the demand volume $D_{od}^{t}$ of each period, not new OD pairs; all periods use the same set of 416 OD pairs.

The model of Schiewe, Schöbel and Sieber (2023) allows an arbitrary number of periods and uses random monotone demand data. Its main cost-similarity experiments and illustrative example use two periods; runtime is additionally tested with 3, 5, 10 and 20 periods. Two periods are therefore sufficient as this paper's main experiment, but cannot by themselves support conclusions about large-scale period scalability.

The main experiment fixes two periods:

$$
D_{od}^{2}=D_{od},
$$

$$
D_{od}^{1}=r_{od}D_{od},
\qquad
r_{od}\sim U[0.5,0.9].
$$

Here $U[0.5,0.9]$ is an experimental distribution specified by this project to implement random monotone OD demand; it is not data provided by the 2026 benchmark, nor an exact generation formula published in the 2023 paper. In the paper it may only be described as inspired by, or adapted from, the monotone multi-period setting of Schiewe, Schöbel and Sieber (2023).

When generating, one must:

- Fix the random seed;
- Save $r_{od}$ for each OD;
- Keep all 416 OD pairs;
- Guarantee $0<D_{od}^{1}\leq D_{od}^{2}$;
- Call the data synthetic monotone demand periods, not actual morning/evening peak data.

If computing time remains after the main experiment, add a 3-period robustness experiment. For each OD, independently draw two $U[0.5,0.9]$ random numbers and sort them:

$$
(r_{od}^{1},r_{od}^{2})
=
\operatorname{sort}(q_{od}^{1},q_{od}^{2}),
\qquad
q_{od}^{1},q_{od}^{2}\sim U[0.5,0.9],
$$

then define:

$$
D_{od}^{1}=r_{od}^{1}D_{od},
\qquad
D_{od}^{2}=r_{od}^{2}D_{od},
\qquad
D_{od}^{3}=D_{od}.
$$

This robustness experiment serves only to confirm that the model does not depend on there being exactly two periods; it need not replicate the 10-period or 20-period runtime experiments of the 2023 paper.

The deterministic monotone-period generation idea in `multi_period_line_planning/instance.py` can be migrated, but the old code generates edge load bounds, not OD matrices, and cannot be called directly and then claimed to have produced multi-period OD.

### 5.6 Full constraints, budget and similarity

In addition to the demand equality already given in Section 5.3, the MILP must contain all of the following constraints.

Each line selects at most one positive frequency in each period:

$$
\sum_{f\in\mathcal F_\ell}z_{\ell f}^{t}\leq1
\qquad
\forall\ell,t.
$$

Passengers may use only the frequency-specific services that have been activated:

$$
x_{od,p}^{t}
\leq
D_{od}^{t}z_{\ell f}^{t}
\qquad
\forall(o,d),\ p\in\mathcal P_{od},\ (\ell,f)\in S(p),\ t.
$$

The capacity constraint is imposed separately on each directed line segment:

$$
\sum_{(o,d)}
\sum_{p\in\mathcal P_{od}}
a_{e,\ell f,p}x_{od,p}^{t}
\leq
C_\ell fz_{\ell f}^{t}
\qquad
\forall t,\ell,\ f\in\mathcal F_\ell,\ e\in\ell.
$$

The directions must not be merged here; passenger flow in the forward and reverse directions must be accumulated separately according to $a_{e,\ell f,p}$, each with the per-direction capacity 1500.

The public medium budget of 60 is a single-period parameter. The main experiment fixes:

$$
B_1=B_2=60,
$$

so as to observe the effects of demand and the similarity constraint in isolation. If the budget is scaled by total demand, this must be a separate, explicitly labelled experiment.

The budget constraint is:

$$
\sum_{\ell\in\mathcal L}
\sum_{f\in\mathcal F_\ell}
b_{\ell f}z_{\ell f}^{t}
\leq B_t
\qquad\forall t.
$$

The optional 3-period robustness experiment likewise uses:

$$
B_1=B_2=B_3=60.
$$

$K$ follows the original Overleaf and denotes the upper limit on the total frequency change allowed between adjacent periods. It is not benchmark data, and the 2023 paper will not automatically provide a unique value for the current Randstad-IC instance. A formal experiment should record the chosen $K$ values separately before running, including at least $K=0$, one pre-selected intermediate value, and one value verified to make the constraint effectively inactive.

The current reduced implementation experiments used $K\in\{0,10,1000\}$, but this is only a record of what has been run; 10 and 1000 are not to be written as values prescribed by the original formulation, the 2023 paper or the 2026 benchmark. Detailed results are kept in Section 14 and `docs/MIGRATION_STATUS_2026.md`.

When $|\mathcal T|>2$, the existing similarity constraint is imposed only on adjacent periods $(t,t+1)$, and does not additionally compare all period combinations.

The complete similarity linearization is:

$$
\Delta_\ell^t
\geq
F_\ell^{t+1}-F_\ell^t
\qquad
\forall\ell,\ t=1,\ldots,|\mathcal T|-1,
$$

$$
\Delta_\ell^t
\geq
F_\ell^t-F_\ell^{t+1}
\qquad
\forall\ell,\ t=1,\ldots,|\mathcal T|-1,
$$

$$
\sum_{\ell\in\mathcal L}\Delta_\ell^t
\leq K
\qquad
\forall t=1,\ldots,|\mathcal T|-1.
$$

The variable domains are:

$$
z_{\ell f}^{t}\in\{0,1\},
\qquad
x_{od,p}^{t}\geq0,
\qquad
u_{od}^{t}\geq0,
\qquad
\Delta_\ell^t\geq0.
$$

Since $z_{\ell f}^{t}$ is binary, the model remains a MILP.

### 5.7 Solution scope, optimality claim and result reporting

Before solving, only a finite candidate line pool $\mathcal L$ and finite passenger-trip sets $\mathcal P_{od}$ are generated. Do not perform, or claim in the paper, "enumerate all feasible simple paths" or "enumerate all theoretically possible passenger trips".

SciPy/HiGHS uses branch-and-bound on the finite MILP above. The LP relaxation replaces:

$$
z_{\ell f}^{t}\in\{0,1\}
$$

with:

$$
0\leq z_{\ell f}^{t}\leq1.
$$

For a minimization problem, the LP bound is a lower bound and the current feasible integer solution is an upper bound. If the solver reaches zero optimality gap, one may only write:

$$
\mathrm{Gap}
=
\frac{UB-LB}{|UB|}
\qquad(UB\neq0).
$$

> The solution is globally optimal for the restricted MILP defined by the fixed candidate line pool, the retained passenger-trip sets, and the stated experimental parameters.

One must not claim that it is an unrestricted global optimum over all theoretically feasible lines and passenger paths.

Each solve records at least solver status, runtime, objective, best bound, gap, $M$, $K$, the selected frequencies of each period, unserved demand, and:

$$
\mathrm{ServiceRate}
=
1-
\frac{\sum_{t,(o,d)}u_{od}^{t}}
{\sum_{t,(o,d)}D_{od}^{t}}.
$$

Service rate is only a reporting metric computed from the solution; it is not an added decision variable, constraint or objective term.

## 6. Assessment of reuse of existing code

This section records the assessment of the old code made at the start of the migration. The migration has since produced the active package `benchmark_2026/`; subsequent agents should not start over from `passenger_line_planning/` according to the table below. The current implementation paths and completion status are as per Sections 1.2, 7, 8 and 14; the table below is mainly for deciding which old files can be cleaned up after verification.

### 6.1 Keep and refactor

| Path | Assessment | Reason and change |
|---|---|---|
| `docs/changes.md` | Keep | Record of the modification process; later agents use Section 5 of this document as the implementation baseline |
| `passenger_line_planning/domain.py` | Keep and extend | The object layer is reusable; benchmark connection, reverse, type, max frequency, line duration/cost must be added |
| `passenger_line_planning/model.py` | Keep and modify | The sparse SciPy/HiGHS builder is reusable; $u_{od}^{t}$, the penalty, service rate and support for missing trips must be added |
| `passenger_line_planning/trip_expansion.py` | Keep and adapt | This document decided to keep frequency-dependent waiting, so the frequency-specific Cartesian expansion is still needed |
| `passenger_line_planning/cli.py` | Keep the framework and rewrite the arguments | The subcommand structure is reusable; the LinTim/Bahn arguments must be replaced |
| `passenger_line_planning/__init__.py` | Keep and update exports | The package entry point is still usable |
| `passenger_line_planning/__main__.py` | Keep | The CLI entry point is dataset-independent |
| `requirements.txt` | Keep | NumPy/SciPy are still used by the new model |

### 6.2 Delete after replacement

| Path | Handling | Reason |
|---|---|---|
| `passenger_line_planning/lintim_io.py` | Delete or fully rewrite after the new benchmark loader is verified | Reads only LinTim `*.giv`, cannot read the benchmark `*.txt` schema |
| `passenger_line_planning/passenger_paths.py` | Delete or rewrite after the official route-generation adapter is verified | The current implementation is based on plain PTN/Yen paths and does not fully express forbidden turns or DPTN dwell/type rules |
| `passenger_line_planning/benchmark.py` | Rewrite | The current reporting logic hardcodes reconstructed Bahn-01 semantics |
| `tests/test_passenger_line_planning.py` | Replace the fixture, keep the test goals | The current tests depend on `datasets/passenger-toy` and the LinTim reader |
| `docs/PASSENGER_MODEL_IMPLEMENTATION.md` | Rewrite and rename | The core model description is useful, but the commands, data and conclusions are tied to Bahn |

### 6.3 Migrate only one function idea

The "fixed seed, monotone growth" idea of `_generate_monotone_periods` in `multi_period_line_planning/instance.py` can be migrated to the new OD generation module.

Do not reuse the edge lower/upper bounds it generates, and do not keep using the MP-F/MP-W models in `multi_period_line_planning/models.py`, which center on operating cost and edge frequency bounds, as the current problem formulation; they are not the same model.

## 7. Current active code structure

```text
benchmark_2026/
  domain.py              # station, track, connection, candidate line, OD, trip
  io.py                  # reads the official benchmark txt files
  line_pool.py           # reading and validation of the frozen line pool
  passenger_routes.py    # official routes -> shortest realizable +15 minute filter
  multiperiod.py         # single-period OD -> reproducible multi-period OD
  trip_expansion.py      # frequency-specific trips and 60/(2f) waiting
  model.py               # the specification MILP of Section 5 of this document
  model_compact.py       # line-level solving form equivalent to the specification model
  pipeline.py            # active data-to-model pipeline
  prepare.py             # preparation/validation of frozen data and M
  solvers.py             # HiGHS/Gurobi backend adapter
  cli.py                 # prepare, verify, estimate, solve

configs/
  randstad_ic_primary.yaml

data/external/
  line-planning-benchmark/  # pinned commit or submodule

tests/
  test_benchmark_2026_io.py
  test_line_pool_2026.py
  test_passenger_routes_2026.py
  test_multiperiod_model_2026.py
  test_compact_model_2026.py
  test_gurobi_backend_2026.py
```

`benchmark_2026/` is the only active model package. `passenger_line_planning/` is the pre-migration implementation and can serve only as cleanup or historical reference; it must not be used together with `benchmark_2026/` as a paper-experiment entry point.

## 8. Verification and reproduction order

Phases A–E below have been implemented, but a new coding agent should re-check them in the same order, and should not skip the frozen-data and generator-consistency checks and run the solver directly. The reduced experiments of Phase F are done; the full Randstad-IC has still not been solved successfully.

### Phase A: pin the external data

1. Make the official repository a pinned submodule, or provide a read-only download script;
2. Pin commit `5d0aae68588701285c42ba47e593fbeb600cbc5c`;
3. Keep the MIT license;
4. Output file hashes and the instance configuration.

Acceptance: any member can obtain the same `Randstad-IC` input from an empty directory.

### Phase B: implement the loader

Read and validate:

- stations;
- tracks;
- connections and reverse IDs;
- forbidden turns;
- allowed type changes;
- dense OD;
- line/route generation parameters;
- transfer penalty, budget, fixed cost.

Acceptance: 80 connections, 416 dense OD pairs, and all referenced IDs valid.

### Phase C: generate and freeze the line pool

Prefer calling or minimally adapting the official Java generator. If a Python port is used, it must be compared with the reference generator by count and line by line.

Acceptance: the small setting yields 93 reproducible candidate lines, persisted and shared by all periods; a different candidate pool must not be regenerated for each synthetic demand period.

### Phase D: generate the passenger-trip pool

Generate realizable routes for all OD pairs with positive demand, using the medium +15-minute detour, then expand frequencies. This pool is generated once and shared by all periods.

Acceptance:

- No OD is silently dropped;
- ODs without a route are recorded;
- All trips use only the frozen candidate lines;
- The capacity incidence includes the service direction;
- The same input generates the same trip IDs.

### Phase E: implement the modified MILP

Add $z$, $x$, $u$ and $\Delta$; build the activation, directional capacity, budget, demand conservation and inter-period similarity constraints.

Acceptance:

- ODs without a route automatically enter $u$, and the model does not become structurally infeasible because of a missing candidate set;
- $u$, service rate and the penalty cost appear in the result file;
- Item-by-item constraint tests on tiny hand-made instances pass;
- Before building the model for Randstad-IC, output the estimates of variables, constraints and nonzeros first.

### Phase F: experiments

First run the single-period consistency test, then the two synthetic periods. After the two-period main experiment is complete and time permits, run one 3-period robustness experiment. Do not run all 972 combinations directly, and replicating the 10-period or 20-period runtime tests of the 2023 paper is not required.

The main results save at least: instance configuration, benchmark commit, OD seed, $M$, $K$, budget, solver status, runtime, objective, bound, gap, service rate and selected frequencies.

## 9. List of old content that can be deleted

Apart from files explicitly marked "deleted", the rest of this section is only a later cleanup list and does not authorize deleting anything right now.

### 9.1 Can be deleted now: Bahn-only and not tracked by Git

The following files are artifacts of the previous round of Bahn-01 investigation or reconstruction validation, and have no continuing value for the new benchmark:

- `docs/BAHN01_CODE_GUIDE_ZH.md`

The first two items of this list were deleted on 2026-10-13:

- `/Users/yulema/Downloads/codex_lintim_bahn01_handoff.md`
- `docs/BAHN01_USAGE_TUTORIAL_ZH.md`

On the same day it was confirmed that all 11 files in `reports/` belonged to the previous round of Bahn work, and the whole directory was deleted:

- `dataset_audit.md` and `dataset_audit.json`
- `external_source_audit.md` and `external_source_audit.json`
- `full_bahn01_feasibility.md` and `full_bahn01_feasibility.json`
- `full_reconstructed_feasibility.json`
- `implementation_validation.md`
- `medium_benchmark.md`
- `model_questions.md`
- `routing_reuse_audit.md`

The still-useful conclusions on $K$, budget, capacity, data sources and path generation have been written into this document, and the old reports need not be relied on.

### 9.2 Delete after the new benchmark tests pass

The following still provide a smoke test or code skeleton for the passenger MILP, and deleting them first is not recommended:

- `datasets/passenger-toy/`
- `results/passenger_toy/`
- `passenger_line_planning/lintim_io.py`
- The current version of `passenger_line_planning/passenger_paths.py`
- The current version of `passenger_line_planning/benchmark.py`
- The current version of `tests/test_passenger_line_planning.py`

Deletion threshold: the new loader, the 93-line pool verification, the route-generation tests and the minimal MILP test all pass.

### 9.3 Can be deleted or archived, but involves tracked old project content

The following is already tracked by Git and belongs to the original Germany-rail/2023 reproduction. If the paper definitely no longer keeps the old reproduction experiments, it is recommended to first create a tag or a `legacy-bahn` branch, and then delete from the active branch:

- `datasets/germany-rail/`
- `results/germany_rail/`
- `docs/IMPLEMENTATION_GUIDE.md`
- `docs/REPRODUCTION_REPORT.md`
- `multi_period_line_planning/germany_rail.py`
- `multi_period_line_planning/reproduce.py`
- `test_germany_rail_reconstruction_is_deterministic` in `tests/test_multi_period_line_planning.py`
- `prepare-germany-rail` and the Germany-rail default paths in `multi_period_line_planning/cli.py`
- The Germany-rail exports in `multi_period_line_planning/__init__.py`

If the old MP-F, MP-W and MP-B reproductions also leave the paper entirely, the following can additionally be archived or deleted:

- `multi_period_line_planning/models.py`
- `multi_period_line_planning/instance.py`, after the monotone-demand idea has been migrated
- `multi_period_line_planning/reproduce.py`
- `multi_period_line_planning/cli.py`
- `multi_period_line_planning/__init__.py`
- `multi_period_line_planning/__main__.py`
- `results/multi_period/`

Do not delete only `germany_rail.py` and leave a dangling import; the CLI, `__init__.py` and the tests must be modified in step.

### 9.4 Do not delete just because the benchmark changed

- `docs/changes.md`
- The generic sparse MILP builder in `passenger_line_planning/model.py`
- The generic object ideas of `passenger_line_planning/domain.py`
- `passenger_line_planning/trip_expansion.py`, keep and adapt to the frequency-dependent waiting this document has decided to use
- `requirements.txt`
- The repository-root `LICENSE`
- `src/`, which lives together with the upstream OpenLinTim code, unless the team separately decides to move the whole vendor codebase out of the project

In particular, `src/simulation/data/bahn-01/` is part of the upstream OpenLinTim tree and is not data generated by the previous round on its own. It may no longer be referenced by new experiments, but it should not be confused with `datasets/germany-rail/` and then deleted on its own casually.

## 10. What needs handling in the README and Git status

The current `README.md` has uncommitted Bahn links and descriptions. During migration:

- Remove the Bahn tutorial links;
- Change the default dataset to the pinned Randstad-IC configuration of the 2026 benchmark;
- State clearly the difference between the reference benchmark and this project's multi-period extension.

There are also uncommitted modifications to several tracked files:

- `README.md`
- `datasets/germany-rail/basis/Generation-Metadata.json`
- `docs/REPRODUCTION_REPORT.md`
- `multi_period_line_planning/germany_rail.py`
- `tests/test_multi_period_line_planning.py`

Do not simply discard these changes during cleanup. First decide whether to save them to a `legacy-bahn` branch, or restore them item by item to the state before the benchmark switch.

## 11. Recommended safe cleanup order

1. Create a recoverable Git commit, tag or archive branch of the current state;
2. Introduce and pin the 2026 benchmark commit;
3. Complete the new loader and line-pool verification;
4. Complete the tests of the passenger routes and the modified MILP;
5. Rewrite the README and usage documents;
6. Delete the Bahn-only untracked artifacts of Section 9.1;
7. Delete or replace the old fixtures/adapters of Section 9.2;
8. After the team confirms the old paper reproductions are no longer needed, handle the tracked content of Section 9.3;
9. Run the full tests, and use `rg -i 'bahn|germany-rail|timpass'` to check whether active documents and commands still contain stale references.

## 12. Fixed implementation decisions

To keep subsequent coding agents from stalling because a choice is unclear, this handoff adopts the following decisions:

1. The main experiment uses the uniform $M=242$ defined in Section 5.3 of this document, makes it a required configuration with no hidden default, and runs the sensitivity check $M\in\{161,242,403\}$;
2. Keep the $60/(2f)$ frequency-dependent waiting time, use $\beta=15$ and $T_p^{\mathrm{walk}}=0$ (so $\alpha$ is not fixed separately), prevent the transfer penalty from being counted twice, and call this project a model extension on benchmark data;
3. The main experiment uses $B_1=B_2=60$;
4. $K$ remains an experimental parameter of the original formulation; $\{0,10,1000\}$ is recorded only as a reduced experiment that was run, and is not claimed to be prescribed by the paper or the benchmark;
5. Prefer calling or minimally adapting the official Java generator; a Python port may replace it only after count and object-by-object verification against the reference output;
6. The main experiment uses two synthetic monotone demand periods; 3 periods is only a robustness experiment if time permits;
7. All periods share the same candidate line pool and the same base passenger-trip pool;
8. $U[0.5,0.9]$ is a generation setting publicly declared by this project; it is not claimed to be provided by the 2026 benchmark or to be the exact formula of the 2023 paper.

If the team later changes any of these decisions, the document and the paper's formulation should be modified first, then the code; it must not be changed silently in the implementation alone.

## 13. What the coding agent must deliver when the migration is complete

- A reproducible, commit-pinned data entry point for the benchmark;
- The Randstad-IC loader, line-pool generator/adapter, route generator and multi-period OD generator;
- The MILP consistent with Section 5 of this document, and configuration files;
- Automated tests and key consistency checks such as the 93-line pool;
- One small-scale end-to-end solve result;
- A two-period synthetic OD file with a fixed seed and saved scaling factors;
- A model-size report for the full Randstad-IC;
- A 3-period robustness result, if time permits;
- If the full MILP is run, saved solver status, runtime, objective, bound, gap and service rate;
- An updated README and user-facing run commands;
- A final handoff record stating unfinished items, deviations and known limitations.

Code that exists without generator consistency checks, tests and a reproducible configuration does not count as a completed migration.

## 14. Implementation record and errata (2026-10-13, appended by the implementing agent)

This section does not change the decisions of Sections 1–13; it only records problems found during implementation. The errata directly related to the formulation and parameters have already been merged back into the earlier text. For the detailed record see
`docs/MIGRATION_STATUS_2026.md`, and for how to run see `docs/BENCHMARK_2026_IMPLEMENTATION.md`.

1. Merged into Section 3: `60` should be understood as the maximum duration of Sprinter lines, not as a uniform line-duration cap for Randstad-IC.
   `LinePoolGenerator.satisfiesSprinterDuration` times only Sprinter segments; Randstad-IC has only Intercity,
   so the 93 lines of the small line pool have a longest duration of 101.49 minutes.
2. Merged into Section 5.2: the "shortest realizable route" is computed relative to the frozen line pool, not the infrastructure shortest route. For 50 ODs the shortest realizable route
   is longer than the infrastructure shortest route (by up to 17.63 minutes), so the official +15-minute window is not enough to export all needed routes; the implementation exports a
   +45-minute window, then filters "shortest realizable + 15" in Python and checks that the window is sufficient.
3. The implementation removes 5,244 strictly dominated itineraries (where two adjacent segments on the same line could have been merged but were split into a transfer). This does not change the optimal value.
4. At full scale there are about 72.5 million frequency-specific trips per period, which cannot be built directly. For solving, a line-level form **equivalent** to Section 5 is used
   (itinerary flow $x^t_{od,i}$ plus boarding flow $w^t_{\ell f}$ aggregated by line and frequency; symbols defined in `PROBLEM_FORMULATION_DETAILED_EN.md`, Section 9); the formulation of Section 5
   is unchanged and remains the definition. The equivalence has been verified numerically on tiny instances (including exhaustive enumeration) and on Randstad-IC subsets.
5. The MILP of the full Randstad-IC found no feasible improving solution within 15 minutes (gap 99.9%) and is still unsolved.
