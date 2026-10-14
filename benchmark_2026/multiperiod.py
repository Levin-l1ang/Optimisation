"""Synthetic monotone multi-period OD demand from the single-period benchmark OD matrix.

The benchmark only provides one OD matrix.  Following the *spirit* of the random
monotone demand periods of Schiewe, Schoebel & Sieber (2023), this project
defines (it is NOT provided by either paper):

    two periods:    D^2 = D,   D^1 = r * D,         r ~ U[0.5, 0.9]  per OD
    three periods:  D^3 = D,   D^1 = r1 * D, D^2 = r2 * D with (r1, r2) = sort(q1, q2),
                    q1, q2 ~ U[0.5, 0.9] per OD (robustness experiment)

All 416 OD pairs are kept in every period; the data are *synthetic monotone demand
periods*, not measured peak/off-peak data.
"""

from __future__ import annotations

import csv
import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np

from .domain import ODPair

LOW, HIGH = 0.5, 0.9
DEFAULT_SEED = 20261013


@dataclass(frozen=True)
class MultiPeriodDemand:
    od_pairs: Tuple[ODPair, ...]
    ratios: Tuple[Tuple[float, ...], ...]  # ratios[p][od] for periods p = 0..T-1 (last = 1.0)
    seed: int
    distribution: str

    @property
    def periods(self) -> int:
        return len(self.ratios)

    def demands(self) -> Tuple[dict, ...]:
        return tuple(
            {od.key: od.volume * self.ratios[p][i] for i, od in enumerate(self.od_pairs)}
            for p in range(self.periods)
        )

    def sha256(self) -> str:
        payload = repr((self.seed, self.distribution,
                        [(o.key, o.volume) for o in self.od_pairs],
                        [[round(r, 12) for r in row] for row in self.ratios]))
        return hashlib.sha256(payload.encode()).hexdigest()


def generate(od_pairs: Sequence[ODPair], periods: int = 2, seed: int = DEFAULT_SEED) -> MultiPeriodDemand:
    if periods not in (2, 3):
        raise ValueError("only the 2-period main experiment and the 3-period robustness experiment are defined")
    rng = np.random.default_rng(seed)
    n = len(od_pairs)
    if periods == 2:
        r = rng.uniform(LOW, HIGH, size=n)
        rows = (tuple(map(float, r)), tuple([1.0] * n))
        dist = f"D1 = r*D, r~U[{LOW},{HIGH}] i.i.d. per OD; D2 = D"
    else:
        q = np.sort(rng.uniform(LOW, HIGH, size=(n, 2)), axis=1)
        rows = (tuple(map(float, q[:, 0])), tuple(map(float, q[:, 1])), tuple([1.0] * n))
        dist = f"(r1,r2)=sort(q1,q2), q~U[{LOW},{HIGH}] i.i.d. per OD; D3 = D"
    demand = MultiPeriodDemand(tuple(od_pairs), rows, seed, dist)
    for i, od in enumerate(od_pairs):  # monotonicity and positivity guards
        values = [rows[p][i] * od.volume for p in range(periods)]
        assert 0 < values[0] <= values[-1] and all(a <= b + 1e-12 for a, b in zip(values, values[1:]))
    return demand


def write_csv(demand: MultiPeriodDemand, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["origin", "destination", "base_demand"]
                        + [f"ratio_p{p + 1}" for p in range(demand.periods)]
                        + [f"demand_p{p + 1}" for p in range(demand.periods)])
        for i, od in enumerate(demand.od_pairs):
            ratios = [demand.ratios[p][i] for p in range(demand.periods)]
            writer.writerow([od.origin, od.destination, repr(od.volume)]
                            + [repr(r) for r in ratios] + [repr(od.volume * r) for r in ratios])


def read_csv(path: Path, od_pairs: Sequence[ODPair], seed: int, distribution: str = "") -> MultiPeriodDemand:
    rows = list(csv.DictReader(path.open()))
    periods = sum(1 for name in rows[0] if name.startswith("ratio_p"))
    if [(r["origin"], r["destination"]) for r in rows] != [od.key for od in od_pairs]:
        raise ValueError("OD order in the demand file differs from the benchmark OD matrix")
    ratios = tuple(tuple(float(r[f"ratio_p{p + 1}"]) for r in rows) for p in range(periods))
    return MultiPeriodDemand(tuple(od_pairs), ratios, seed, distribution)


def identical(od_pairs: Sequence[ODPair], periods: int = 2) -> MultiPeriodDemand:
    """Every period equals the base matrix (consistency checks only; not an experiment)."""
    n = len(od_pairs)
    return MultiPeriodDemand(tuple(od_pairs), tuple(tuple([1.0] * n) for _ in range(periods)), 0,
                             "D^t = D for all t (consistency check)")
