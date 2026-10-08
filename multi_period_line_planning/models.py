"""MILP formulations for the multi-period line-planning paper.

The implementation uses :func:`scipy.optimize.milp`, backed by the open-source
HiGHS solver, so running the reproduction does not require Gurobi.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from time import perf_counter
from typing import Dict, List, Mapping, Optional, Sequence, Tuple

import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import coo_matrix

from .instance import LinePlanningInstance


@dataclass(frozen=True)
class SolveResult:
    model: str
    status: str
    objective: float
    runtime_seconds: float
    frequencies: Tuple[Tuple[int, ...], ...]
    dissimilarity: Optional[float] = None
    mip_gap: Optional[float] = None
    message: str = ""
    metadata: Mapping[str, object] = field(default_factory=dict)

    @property
    def optimal(self) -> bool:
        return self.status == "optimal"


class _MilpBuilder:
    def __init__(self) -> None:
        self.costs: List[float] = []
        self.lower: List[float] = []
        self.upper: List[float] = []
        self.integrality: List[int] = []
        self.rows: List[Dict[int, float]] = []
        self.row_lower: List[float] = []
        self.row_upper: List[float] = []

    def variable(
        self,
        cost: float = 0.0,
        lower: float = 0.0,
        upper: float = np.inf,
        integer: bool = False,
    ) -> int:
        index = len(self.costs)
        self.costs.append(float(cost))
        self.lower.append(float(lower))
        self.upper.append(float(upper))
        self.integrality.append(1 if integer else 0)
        return index

    def constraint(
        self,
        coefficients: Mapping[int, float],
        lower: float = -np.inf,
        upper: float = np.inf,
    ) -> None:
        cleaned = {index: value for index, value in coefficients.items() if value}
        self.rows.append(cleaned)
        self.row_lower.append(float(lower))
        self.row_upper.append(float(upper))

    def solve(self, time_limit: Optional[float], mip_gap: float):
        row_indices: List[int] = []
        column_indices: List[int] = []
        values: List[float] = []
        for row, coefficients in enumerate(self.rows):
            for column, value in coefficients.items():
                row_indices.append(row)
                column_indices.append(column)
                values.append(value)
        matrix = coo_matrix(
            (values, (row_indices, column_indices)),
            shape=(len(self.rows), len(self.costs)),
        ).tocsr()
        options = {"disp": False, "mip_rel_gap": float(mip_gap)}
        if time_limit is not None:
            options["time_limit"] = float(time_limit)
        started = perf_counter()
        result = milp(
            c=np.asarray(self.costs),
            integrality=np.asarray(self.integrality),
            bounds=Bounds(np.asarray(self.lower), np.asarray(self.upper)),
            constraints=LinearConstraint(
                matrix, np.asarray(self.row_lower), np.asarray(self.row_upper)
            ),
            options=options,
        )
        return result, perf_counter() - started


_STATUS = {
    0: "optimal",
    1: "limit_reached",
    2: "infeasible",
    3: "unbounded",
    4: "solver_error",
}


def _frequency_variables(
    builder: _MilpBuilder, instance: LinePlanningInstance
) -> List[List[int]]:
    maximum = instance.max_frequency
    return [
        [
            builder.variable(
                cost=instance.line_costs[line], upper=maximum, integer=True
            )
            for line in range(instance.line_count)
        ]
        for _ in range(instance.period_count)
    ]


def _add_edge_bounds(
    builder: _MilpBuilder,
    instance: LinePlanningInstance,
    frequencies: Sequence[Sequence[int]],
) -> None:
    incidence = instance.incidence
    for period in range(instance.period_count):
        for edge in range(instance.edge_count):
            coefficients = {
                frequencies[period][line]: incidence[edge, line]
                for line in range(instance.line_count)
                if incidence[edge, line]
            }
            builder.constraint(
                coefficients,
                lower=instance.lower_bounds[period][edge],
                upper=instance.upper_bounds[period][edge],
            )


def _binary_usage(
    builder: _MilpBuilder,
    instance: LinePlanningInstance,
    frequencies: Sequence[Sequence[int]],
) -> List[List[int]]:
    maximum = instance.max_frequency
    usage = [
        [builder.variable(upper=1.0, integer=True) for _ in range(instance.line_count)]
        for _ in range(instance.period_count)
    ]
    for period in range(instance.period_count):
        for line in range(instance.line_count):
            builder.constraint(
                {frequencies[period][line]: 1.0, usage[period][line]: -maximum},
                upper=0.0,
            )
            builder.constraint(
                {usage[period][line]: 1.0, frequencies[period][line]: -1.0},
                upper=0.0,
            )
    return usage


def _result(
    model: str,
    solver_result,
    runtime: float,
    frequencies: Sequence[Sequence[int]],
    periods: int,
    lines: int,
    dissimilarity: Optional[float] = None,
    metadata: Optional[Mapping[str, object]] = None,
) -> SolveResult:
    status = _STATUS.get(int(solver_result.status), "unknown")
    if solver_result.x is None:
        values: Tuple[Tuple[int, ...], ...] = tuple()
        objective = float("inf")
    else:
        values = tuple(
            tuple(int(round(solver_result.x[frequencies[p][line]])) for line in range(lines))
            for p in range(periods)
        )
        objective = float(solver_result.fun)
    gap_value = getattr(solver_result, "mip_gap", None)
    return SolveResult(
        model=model,
        status=status,
        objective=objective,
        runtime_seconds=runtime,
        frequencies=values,
        dissimilarity=dissimilarity,
        mip_gap=float(gap_value) if gap_value is not None else None,
        message=str(solver_result.message),
        metadata=dict(metadata or {}),
    )


def solve_lower_bound(
    instance: LinePlanningInstance,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve (MP-Lower-Bound), i.e. independent line concepts for all periods."""

    builder = _MilpBuilder()
    frequencies = _frequency_variables(builder, instance)
    _add_edge_bounds(builder, instance, frequencies)
    solved, runtime = builder.solve(time_limit, mip_gap)
    return _result(
        "MP-Lower-Bound",
        solved,
        runtime,
        frequencies,
        instance.period_count,
        instance.line_count,
    )


def solve_upper_bound(
    instance: LinePlanningInstance,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve (MP-Upper-Bound) with one identical concept for every period."""

    builder = _MilpBuilder()
    common = [
        builder.variable(
            cost=instance.period_count * instance.line_costs[line],
            upper=instance.max_frequency,
            integer=True,
        )
        for line in range(instance.line_count)
    ]
    frequencies = [common for _ in range(instance.period_count)]
    _add_edge_bounds(builder, instance, frequencies)
    solved, runtime = builder.solve(time_limit, mip_gap)
    return _result(
        "MP-Upper-Bound",
        solved,
        runtime,
        frequencies,
        instance.period_count,
        instance.line_count,
        dissimilarity=0.0,
    )


def solve_mp_f(
    instance: LinePlanningInstance,
    k: float,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve (MP-F) with Manhattan/L1 frequency similarity, formulation (8)."""

    builder = _MilpBuilder()
    frequencies = _frequency_variables(builder, instance)
    _add_edge_bounds(builder, instance, frequencies)
    usage = _binary_usage(builder, instance, frequencies)
    for period in range(1, instance.period_count):
        for line in range(instance.line_count):
            builder.constraint(
                {usage[period][line]: 1.0, usage[0][line]: -1.0}, lower=0.0, upper=0.0
            )
    for first in range(instance.period_count):
        for second in range(first + 1, instance.period_count):
            absolute_variables = []
            for line in range(instance.line_count):
                absolute = builder.variable(upper=instance.max_frequency)
                absolute_variables.append(absolute)
                builder.constraint(
                    {
                        frequencies[first][line]: 1.0,
                        frequencies[second][line]: -1.0,
                        absolute: -1.0,
                    },
                    upper=0.0,
                )
                builder.constraint(
                    {
                        frequencies[second][line]: 1.0,
                        frequencies[first][line]: -1.0,
                        absolute: -1.0,
                    },
                    upper=0.0,
                )
            builder.constraint({variable: 1.0 for variable in absolute_variables}, upper=k)
    solved, runtime = builder.solve(time_limit, mip_gap)
    result = _result(
        "MP-F",
        solved,
        runtime,
        frequencies,
        instance.period_count,
        instance.line_count,
        metadata={"K": k, "norm": "L1"},
    )
    if result.frequencies:
        dissimilarity = max(
            sum(abs(a - b) for a, b in zip(result.frequencies[first], result.frequencies[second]))
            for first in range(instance.period_count)
            for second in range(first + 1, instance.period_count)
        ) if instance.period_count > 1 else 0.0
        return SolveResult(**{**result.__dict__, "dissimilarity": float(dissimilarity)})
    return result


def solve_mp_b(
    instance: LinePlanningInstance,
    k: int,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve the monotone binary-line model (MP-B), formulation (12)."""

    builder = _MilpBuilder()
    frequencies = _frequency_variables(builder, instance)
    _add_edge_bounds(builder, instance, frequencies)
    usage = _binary_usage(builder, instance, frequencies)
    for period in range(1, instance.period_count):
        for line in range(instance.line_count):
            builder.constraint(
                {frequencies[period - 1][line]: 1.0, frequencies[period][line]: -1.0},
                upper=0.0,
            )
        builder.constraint(
            {
                **{usage[period][line]: 1.0 for line in range(instance.line_count)},
                **{usage[period - 1][line]: -1.0 for line in range(instance.line_count)},
            },
            upper=k,
        )
    solved, runtime = builder.solve(time_limit, mip_gap)
    result = _result(
        "MP-B",
        solved,
        runtime,
        frequencies,
        instance.period_count,
        instance.line_count,
        metadata={"K": k},
    )
    if result.frequencies:
        active = [[value > 0 for value in period] for period in result.frequencies]
        dissimilarity = max(
            sum(now and not before for before, now in zip(active[p - 1], active[p]))
            for p in range(1, instance.period_count)
        ) if instance.period_count > 1 else 0
        return SolveResult(**{**result.__dict__, "dissimilarity": float(dissimilarity)})
    return result


def solve_mp_b_iter(
    instance: LinePlanningInstance,
    k: int,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve the sequential heuristic (MP-B_iter), formulation (13)."""

    first = solve_lower_bound(instance.periods([0]), time_limit, mip_gap)
    if not first.frequencies:
        return SolveResult(
            model="MP-B_iter",
            status=first.status,
            objective=float("inf"),
            runtime_seconds=first.runtime_seconds,
            frequencies=tuple(),
            message=first.message,
            metadata={"K": k},
        )
    all_frequencies: List[Tuple[int, ...]] = [first.frequencies[0]]
    total_objective = first.objective
    total_runtime = first.runtime_seconds
    statuses = [first.status]
    gaps = [first.mip_gap] if first.mip_gap is not None else []
    for period in range(1, instance.period_count):
        one_period = instance.periods([period])
        builder = _MilpBuilder()
        frequencies = _frequency_variables(builder, one_period)
        _add_edge_bounds(builder, one_period, frequencies)
        usage = _binary_usage(builder, one_period, frequencies)
        previous = all_frequencies[-1]
        for line, value in enumerate(previous):
            builder.constraint({frequencies[0][line]: 1.0}, lower=value)
        previous_active = sum(value > 0 for value in previous)
        builder.constraint(
            {usage[0][line]: 1.0 for line in range(instance.line_count)},
            upper=k + previous_active,
        )
        solved, runtime = builder.solve(time_limit, mip_gap)
        partial = _result(
            "MP-B_iter",
            solved,
            runtime,
            frequencies,
            1,
            instance.line_count,
        )
        total_runtime += runtime
        statuses.append(partial.status)
        if not partial.frequencies:
            return SolveResult(
                model="MP-B_iter",
                status=partial.status,
                objective=float("inf"),
                runtime_seconds=total_runtime,
                frequencies=tuple(),
                message=partial.message,
                metadata={"K": k, "failed_period": period + 1},
            )
        all_frequencies.append(partial.frequencies[0])
        total_objective += partial.objective
        if partial.mip_gap is not None:
            gaps.append(partial.mip_gap)
    active = [[value > 0 for value in period] for period in all_frequencies]
    dissimilarity = max(
        sum(now and not before for before, now in zip(active[p - 1], active[p]))
        for p in range(1, instance.period_count)
    ) if instance.period_count > 1 else 0
    return SolveResult(
        model="MP-B_iter",
        status="optimal" if all(status == "optimal" for status in statuses) else "limit_reached",
        objective=float(total_objective),
        runtime_seconds=total_runtime,
        frequencies=tuple(all_frequencies),
        dissimilarity=float(dissimilarity),
        mip_gap=max(gaps) if gaps else None,
        metadata={"K": k},
    )


def solve_mp_b_ext(
    instance: LinePlanningInstance,
    k: int,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve non-monotone binary-line model (MP-B_ext), formulation (15)."""

    builder = _MilpBuilder()
    frequencies = _frequency_variables(builder, instance)
    _add_edge_bounds(builder, instance, frequencies)
    usage = _binary_usage(builder, instance, frequencies)
    for first in range(instance.period_count):
        for second in range(first + 1, instance.period_count):
            differences = []
            for line in range(instance.line_count):
                difference = builder.variable(upper=1.0)
                differences.append(difference)
                builder.constraint(
                    {usage[first][line]: 1.0, usage[second][line]: -1.0, difference: -1.0},
                    upper=0.0,
                )
                builder.constraint(
                    {usage[second][line]: 1.0, usage[first][line]: -1.0, difference: -1.0},
                    upper=0.0,
                )
            builder.constraint({variable: 1.0 for variable in differences}, upper=k)
    solved, runtime = builder.solve(time_limit, mip_gap)
    result = _result(
        "MP-B_ext",
        solved,
        runtime,
        frequencies,
        instance.period_count,
        instance.line_count,
        metadata={"K": k},
    )
    if result.frequencies:
        active = [[value > 0 for value in period] for period in result.frequencies]
        dissimilarity = max(
            sum(a != b for a, b in zip(active[first], active[second]))
            for first in range(instance.period_count)
            for second in range(first + 1, instance.period_count)
        ) if instance.period_count > 1 else 0
        return SolveResult(**{**result.__dict__, "dissimilarity": float(dissimilarity)})
    return result


def solve_mp_w(
    instance: LinePlanningInstance,
    k: float,
    penalty: float = 1.0,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
) -> SolveResult:
    """Solve Wasserstein line-similarity model (MP-W), formulation (17)."""

    if penalty < 0:
        raise ValueError("penalty must be non-negative")
    builder = _MilpBuilder()
    frequencies = _frequency_variables(builder, instance)
    _add_edge_bounds(builder, instance, frequencies)
    distances = instance.line_distance_matrix
    dissimilarity_coefficients: Dict[int, float] = {}
    for period in range(instance.period_count - 1):
        transports = [
            [
                builder.variable(upper=instance.max_frequency, integer=True)
                for _ in range(instance.line_count)
            ]
            for _ in range(instance.line_count)
        ]
        to_sink = [
            builder.variable(upper=instance.max_frequency, integer=True)
            for _ in range(instance.line_count)
        ]
        from_source = [
            builder.variable(upper=instance.max_frequency, integer=True)
            for _ in range(instance.line_count)
        ]
        for source_line in range(instance.line_count):
            coefficients = {
                transports[source_line][target_line]: 1.0
                for target_line in range(instance.line_count)
            }
            coefficients[to_sink[source_line]] = 1.0
            coefficients[frequencies[period][source_line]] = -1.0
            builder.constraint(coefficients, lower=0.0, upper=0.0)
        for target_line in range(instance.line_count):
            coefficients = {
                transports[source_line][target_line]: 1.0
                for source_line in range(instance.line_count)
            }
            coefficients[from_source[target_line]] = 1.0
            coefficients[frequencies[period + 1][target_line]] = -1.0
            builder.constraint(coefficients, lower=0.0, upper=0.0)
        for source_line in range(instance.line_count):
            for target_line in range(instance.line_count):
                dissimilarity_coefficients[transports[source_line][target_line]] = float(
                    distances[source_line, target_line]
                )
            dissimilarity_coefficients[to_sink[source_line]] = penalty
            dissimilarity_coefficients[from_source[source_line]] = penalty
    builder.constraint(dissimilarity_coefficients, upper=k)
    solved, runtime = builder.solve(time_limit, mip_gap)
    dissimilarity = None
    if solved.x is not None:
        dissimilarity = float(
            sum(solved.x[index] * coefficient for index, coefficient in dissimilarity_coefficients.items())
        )
    return _result(
        "MP-W",
        solved,
        runtime,
        frequencies,
        instance.period_count,
        instance.line_count,
        dissimilarity=dissimilarity,
        metadata={"K": k, "penalty": penalty},
    )
