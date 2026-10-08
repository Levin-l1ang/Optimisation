"""Run a compact qualitative reproduction of the paper's experiments."""

from __future__ import annotations

import csv
import json
import os
import tempfile
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

_matplotlib_cache = Path(tempfile.gettempdir()) / "multi-period-line-planning-matplotlib"
_matplotlib_cache.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(_matplotlib_cache))
os.environ.setdefault("XDG_CACHE_HOME", str(_matplotlib_cache))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from .instance import LinePlanningInstance, load_lintim_instance
from .models import (
    SolveResult,
    solve_lower_bound,
    solve_mp_b,
    solve_mp_b_ext,
    solve_mp_b_iter,
    solve_mp_f,
    solve_mp_w,
    solve_upper_bound,
)


def _write_csv(path: Path, rows: Iterable[dict], columns: Sequence[str]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def _plot_tradeoffs(results: Sequence[SolveResult], path: Path) -> None:
    models = ("MP-F", "MP-B", "MP-B_ext", "MP-W")
    figure, axes = plt.subplots(2, 2, figsize=(10, 7), constrained_layout=True)
    for model, axis in zip(models, axes.flat):
        values = [result for result in results if result.model == model and result.frequencies]
        axis.plot(
            [float(result.metadata["K"]) for result in values],
            [result.objective for result in values],
            marker="o",
            linewidth=1.8,
        )
        axis.set_title(model)
        axis.set_xlabel("Similarity bound K")
        axis.set_ylabel("Optimal operating cost")
        axis.grid(alpha=0.25)
    figure.suptitle("Cost-similarity trade-off on reconstructed Germany-rail data")
    figure.savefig(path, dpi=180)
    plt.close(figure)


def _select_representative(
    results: Sequence[SolveResult], lower_objective: float
) -> Dict[str, SolveResult]:
    del lower_objective  # retained in the signature for backward compatibility
    selected: Dict[str, SolveResult] = {}
    for model in ("MP-F", "MP-B", "MP-W"):
        candidates = [result for result in results if result.model == model and result.frequencies]
        target = (max(result.objective for result in candidates) + min(
            result.objective for result in candidates
        )) / 2.0
        selected[model] = min(candidates, key=lambda result: abs(result.objective - target))
    return selected


def _plot_line_concepts(
    instance: LinePlanningInstance,
    lower: SolveResult,
    selected: Dict[str, SolveResult],
    path: Path,
) -> None:
    rows: List[Tuple[str, SolveResult]] = [("Lower bound", lower)] + list(selected.items())
    figure, axes = plt.subplots(
        len(rows), instance.period_count, figsize=(14, 8), constrained_layout=True
    )
    if instance.period_count == 1:
        axes = np.asarray(axes).reshape(len(rows), 1)
    maximum = max(
        value
        for _, result in rows
        for period in result.frequencies
        for value in period
    )
    image = None
    for row, (label, result) in enumerate(rows):
        for period in range(instance.period_count):
            data = np.asarray(result.frequencies[period], dtype=float).reshape(1, -1)
            image = axes[row, period].imshow(data, vmin=0, vmax=maximum, cmap="Blues", aspect="auto")
            if instance.line_count <= 32:
                axes[row, period].set_xticks(range(instance.line_count), instance.line_ids)
            else:
                axes[row, period].set_xticks([])
            axes[row, period].set_yticks([])
            if instance.line_count <= 32:
                for line, value in enumerate(result.frequencies[period]):
                    axes[row, period].text(
                        line,
                        0,
                        str(value),
                        ha="center",
                        va="center",
                        color="white" if value > maximum / 2 else "black",
                        fontsize=8,
                    )
            suffix = "" if label == "Lower bound" else f", K={result.metadata['K']}"
            axes[row, period].set_title(f"{label}{suffix} - period {period + 1}", fontsize=9)
            axes[row, period].set_xlabel("Line id")
    if image is not None:
        figure.colorbar(image, ax=axes, label="Frequency", shrink=0.7)
    figure.suptitle("Representative Germany-rail line-frequency concepts")
    figure.savefig(path, dpi=180)
    plt.close(figure)


def reproduce(
    dataset: Path,
    output: Path,
    periods: int = 2,
    seed: int = 2023,
    penalty: float = 1.0,
    time_limit: float = 60.0,
) -> List[SolveResult]:
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    instance = load_lintim_instance(dataset, periods=periods, seed=seed)

    results: List[SolveResult] = [
        solve_lower_bound(instance, time_limit=time_limit),
        solve_upper_bound(instance, time_limit=time_limit),
    ]
    if instance.line_count > 32:
        # Compact grid for the 132-line Germany-rail experiment. It captures the
        # transition and plateau without turning a qualitative reproduction into
        # the paper's much heavier runtime-scaling study.
        f_values = (0, 2, 4, 8, 12, 16, 20, 24, 32)
        binary_values = (0, 1, 2, 4, 6, 8, 12, 16)
        w_values = (0, 4, 8, 12, 16, 20, 24, 32, 40)
    else:
        f_values = tuple(range(0, 33))
        binary_values = tuple(range(0, instance.line_count + 1))
        w_values = tuple(range(0, 33))
    for k in f_values:
        results.append(solve_mp_f(instance, k, time_limit=time_limit))
    for k in binary_values:
        results.append(solve_mp_b(instance, k, time_limit=time_limit))
        results.append(solve_mp_b_iter(instance, k, time_limit=time_limit))
        results.append(solve_mp_b_ext(instance, k, time_limit=time_limit))
    for k in w_values:
        results.append(solve_mp_w(instance, k, penalty=penalty, time_limit=time_limit))

    summary_rows = []
    frequency_rows = []
    for result in results:
        summary_rows.append(
            {
                "model": result.model,
                "K": result.metadata.get("K", ""),
                "status": result.status,
                "objective": result.objective,
                "dissimilarity": "" if result.dissimilarity is None else result.dissimilarity,
                "runtime_seconds": result.runtime_seconds,
                "mip_gap": "" if result.mip_gap is None else result.mip_gap,
            }
        )
        for period, frequencies in enumerate(result.frequencies, 1):
            for line_id, frequency in zip(instance.line_ids, frequencies):
                frequency_rows.append(
                    {
                        "model": result.model,
                        "K": result.metadata.get("K", ""),
                        "period": period,
                        "line_id": line_id,
                        "frequency": frequency,
                    }
                )
    _write_csv(
        output / "benchmark.csv",
        summary_rows,
        ("model", "K", "status", "objective", "dissimilarity", "runtime_seconds", "mip_gap"),
    )
    _write_csv(
        output / "frequencies.csv",
        frequency_rows,
        ("model", "K", "period", "line_id", "frequency"),
    )
    _plot_tradeoffs(results, output / "cost_similarity_tradeoff.png")
    lower = results[0]
    selected = _select_representative(results, lower.objective)
    _plot_line_concepts(instance, lower, selected, output / "line_concepts.png")

    plateau = {}
    for model in ("MP-F", "MP-B", "MP-B_ext", "MP-W"):
        candidates = [result for result in results if result.model == model and result.optimal]
        best = min(candidates, key=lambda result: result.objective)
        plateau[model] = {
            "best_objective": best.objective,
            "first_best_K": min(
                float(result.metadata["K"])
                for result in candidates
                if abs(result.objective - best.objective) <= 1e-7
            ),
        }
    generation_metadata_path = Path(dataset) / "basis" / "Generation-Metadata.json"
    generation_metadata = (
        json.loads(generation_metadata_path.read_text(encoding="utf-8"))
        if generation_metadata_path.is_file()
        else None
    )
    metadata = {
        "dataset": str(Path(dataset)),
        "periods": periods,
        "seed": seed,
        "penalty": penalty,
        "edges": instance.edge_count,
        "lines": instance.line_count,
        "demand": instance.demand_description,
        "generation": generation_metadata,
        "parameter_grid": {
            "MP-F": list(f_values),
            "MP-B_family": list(binary_values),
            "MP-W": list(w_values),
        },
        "solve_count": len(results),
        "optimal_solve_count": sum(result.optimal for result in results),
        "lower_bound_objective": lower.objective,
        "upper_bound_objective": results[1].objective,
        "plateau": plateau,
        "paper_reproduction_limit": (
            "The original implementation, exact candidate pools, random demand vectors, and seed "
            "are unavailable. This reconstruction combines the repository's Bahn-01/R1L1 OD matrix "
            "with the public TimPassLib long-distance topology and tests qualitative rather than "
            "point-for-point numerical agreement."
        ),
    }
    (output / "summary.json").write_text(
        json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return results
