# Line Planning Benchmark

A benchmark library of instances and algorithms for the **Line Planning Problem**, accompanying the paper:

> R.N. van Lieshout and B.T.C. van Rossum. *Line Planning at Scale: Models, Methods, and Insights*. 
## Instance Overview

The library contains **6 networks** based on real-world Dutch and Swiss railway systems:

| Instance          | Network                        | Variant                            |
|-------------------|--------------------------------|------------------------------------|
| Randstad-IC       | Netherlands — Randstad region  | Intercity only                     |
| Randstad-IC-SPR   | Netherlands — Randstad region  | Intercity + Sprinter               |
| Netherlands-IC    | Netherlands — full network     | Intercity only                     |
| Netherlands-IC-SPR| Netherlands — full network     | Intercity + Sprinter               |
| Switzerland-Small | Switzerland — partial network  | —                                  |
| Switzerland-Large | Switzerland — full network     | —                                  |


Each network is combined with multiple parameter settings, yielding **162 problem instances per network** (972 total):

| Dimension         | Levels                | Description                                                        |
|-------------------|-----------------------|--------------------------------------------------------------------|
| OD matrix         | dense / sparse        | Dense: raw demand; Sparse: sparsified via OD splitting (§5.3)      |
| Transfer penalty  | small / medium / large| Cost per passenger transfer: 5 / 15 / 25 minutes                   |
| Budget            | small / medium / large| Total budget for operating lines                                   |
| Line activation cost | low / medium / high| Fixed cost per line activated                                      |
| Line generation   | small / medium / large| Line pool size, controlled by detour ratio: 1.1 / 1.3 / 1.5      

## Repository Structure

```
instances/                     ← see instances/README.md for file format details
    {instance-name}/
        stations.txt
        tracks.txt
        connections.txt
        forbiddenTurns.txt
        allowedTypeChanges.txt
        LineGeneration/        ← small.txt, medium.txt, large.txt
        RouteGeneration/       ← small.txt, medium.txt, large.txt
        ODMatrix/              ← dense.txt, sparse.txt
        transferPenalty/       ← small.txt, medium.txt, large.txt
        budget/                ← small.txt, medium.txt, large.txt  (medium fixed cost)
        budget-fixedCost-low/  ← small.txt, medium.txt, large.txt
        budget-fixedCost-high/ ← small.txt, medium.txt, large.txt
        fixedCost/             ← low.txt, medium.txt, high.txt
        solutions/             ← solver output files
bestSolutions/
    solutionFiles/             ← 972 best solution files (see bestSolutions/README.md)
    overview.csv               ← summary of best known objectives
src/
    linePlanning/
        scripts/               ← Main.java, VerifySolution.java
        solvers/               ← PTNSolver, PTNBendersSolver, DPTNSolver, CGNSolver
        instance/              ← LinePlanningInstance, LinePlanningSolution, LinePlanningParameters
        columnGeneration/      ← master problem, pricing routine, cuts
        lineGeneration/        ← line pool generator
        routeGeneration/       ← passenger route generator
        CGN/                   ← Change-and-Go Network structures
        DPTN/                  ← Dynamic Passenger Transfer Network structures
        PTN/                   ← Passenger Transfer Network structures
    graph/                     ← Dijkstra, DFS, SPPRC
    optimisation/              ← Branch-and-price framework, cutting planes
    util/                      ← Reader, Writer, Logger, Configuration
pom.xml
settings.properties
```

## Building and Running

**Requirements:** Java 21, IBM CPLEX 22.1.0 (must be installed and available on the classpath)

To run the solver, edit the settings at the top of [src/linePlanning/scripts/Main.java](src/linePlanning/scripts/Main.java) and execute that class. The settings control which instance, parameter combination, and algorithm to use.

The CPLEX log level and other low-level options are in [settings.properties](settings.properties).

## Algorithms

Four model families are implemented. The family is selected by `solverIndex`; within-family variants are controlled by separate boolean flags.

| `solverIndex` | Class(es)                              | Family  |
|---------------|----------------------------------------|---------|
| 0             | `PTNSolver(false)`                     | PTN     |
| 1             | `PTNSolver(true)` / `PTNBendersSolver` | PTN-DC  |
| 2             | `DPTNSolver`                           | DPTN    |
| 3             | `CGNSolver`                            | CGN     |

Additional boolean settings in `Main.java`:

| Variable               | Applies to    | Effect                                                                  |
|------------------------|---------------|-------------------------------------------------------------------------|
| `useBenders`           | PTN-DC (1)    | `false` = PTN-BDC (basic direct connection), `true` = PTN-FDC (Benders decomposition) |
| `useColumnGeneration`  | DPTN (2)      | `false` = a priori route enumeration (MIP), `true` = branch-and-price  |
| `useStrongInequalities`| DPTN B&P (2), CGN (3) | `false` = B&P, `true` = branch-price-and-cut (strong disaggregated capacity inequalities) |

`routeGenerationIndex` controls the passenger route set (small = shortest paths only, medium = up to 15 min detour, large = up to 30 min). It applies to PTN (0), PTN-DC (1), and DPTN without column generation (2 with `useColumnGeneration=false`). 
## Verifying Solutions

`VerifySolution.java` verifies feasibility and re-evaluates of a single solution file by computing the optimal routing on the CGN.

## Citation

If you use these instances or algorithms in your research, please cite:

```bibtex
@techreport{vanLieshout2026lineplanning,
  author      = {van Lieshout, R.N. and van Rossum, B.T.C.},
  title       = {Line Planning at Scale: Models, Methods, and Insights},
  institution = {Eindhoven University of Technology},
  year        = {2026},
}
```

## License

This repository is released under the [MIT License](LICENSE).
