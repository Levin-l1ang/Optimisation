"""Reader/validator for the official 2026 benchmark text files (Randstad-IC)."""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Dict, List, Optional

from .domain import (
    BenchmarkInstance, Connection, InstanceParameters, ODPair, Station, Track,
)


class BenchmarkFormatError(ValueError):
    pass


def _lines(path: Path) -> List[str]:
    return [line for line in path.read_text().splitlines() if line.strip()]


def _read_int(path: Path) -> int:
    # Mirrors Reader.readInt: the last non-empty line holds the value.
    return int(_lines(path)[-1])


def _read_bool(path: Path) -> bool:
    return _lines(path)[-1].strip().lower() == "true"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def read_stations(path: Path) -> Dict[str, Station]:
    """Columns follow Reader.readStation (the README header is misleading):
    id short full terminalIC terminalSPR type inRandstad lat lon."""
    stations: Dict[str, Station] = {}
    for line in _lines(path):
        f = line.split(" ")
        if len(f) != 9:
            raise BenchmarkFormatError(f"{path}: expected 9 columns, got {len(f)}: {line!r}")
        name = f[1]
        if name in stations:
            raise BenchmarkFormatError(f"duplicate station {name}")
        stations[name] = Station(int(f[0]), name, f[2], f[3] == "true", f[4] == "true",
                                 int(f[5]), f[6] == "true")
    return stations


def read_tracks(path: Path) -> Dict[int, Track]:
    tracks: Dict[int, Track] = {}
    for line in _lines(path):
        f = line.split(" ")
        track = Track(int(f[0]), f[1], f[2], float(f[3]), int(f[4]), float(f[5]), int(f[6]))
        if track.track_id in tracks:
            raise BenchmarkFormatError(f"duplicate track {track.track_id}")
        tracks[track.track_id] = track
    return tracks


def read_connections(path: Path) -> Dict[int, Connection]:
    connections: Dict[int, Connection] = {}
    for line in _lines(path):
        f = line.split(" ")
        conn = Connection(int(f[0]), f[1], f[2], float(f[3]), float(f[4]), int(f[5]),
                          int(f[6]), int(f[7]), tuple(int(x) for x in f[8:]))
        if conn.connection_id in connections:
            raise BenchmarkFormatError(f"duplicate connection {conn.connection_id}")
        connections[conn.connection_id] = conn
    return connections


def read_od(path: Path) -> List[ODPair]:
    pairs = []
    for line in _lines(path):
        o, d, v = line.split(" ")
        pairs.append(ODPair(o, d, float(v)))
    return pairs


def load_instance(
    root: Path,
    od_matrix: str = "dense",
    line_generation: str = "small",
    route_generation: str = "medium",
    transfer_penalty: str = "medium",
    budget: str = "medium",
    fixed_cost: str = "medium",
) -> BenchmarkInstance:
    """Load and validate one parameter combination of one benchmark network."""
    root = Path(root)
    stations = read_stations(root / "stations.txt")
    tracks = read_tracks(root / "tracks.txt")
    connections = read_connections(root / "connections.txt")
    od_pairs = read_od(root / "ODMatrix" / f"{od_matrix}.txt")

    forbidden = frozenset(
        frozenset(int(x) for x in line.split(" ")) for line in _lines(root / "forbiddenTurns.txt")
    )
    # Randstad-IC is IC-only; its allowedTypeChanges.txt holds two stray station-like
    # rows that the reference code never uses (no IC<->SPR change can occur).  Keep
    # only the first three tokens, as Reader.readAllowedTypeChanges does.
    type_changes = frozenset(
        tuple(line.split(" ")[:3]) for line in _lines(root / "allowedTypeChanges.txt")
    )

    lg = _lines(root / "LineGeneration" / f"{line_generation}.txt")
    params = InstanceParameters(
        line_detour_ratio=float(lg[0]),
        line_max_duration=float(lg[1]),
        route_max_detour=_read_int(root / "RouteGeneration" / f"{route_generation}.txt"),
        transfer_penalty=_read_int(root / "transferPenalty" / f"{transfer_penalty}.txt"),
        fixed_cost=float(_read_int(root / "fixedCost" / f"{fixed_cost}.txt")),
        budget=float(_read_int(root / "budget" / f"{budget}.txt")),
        has_ics=_read_bool(root / "hasICs.txt"),
        has_sprs=_read_bool(root / "hasSPRs.txt"),
    )
    active = tuple(sorted({c.from_station for c in connections.values()}
                          | {c.to_station for c in connections.values()},
                          key=lambda name: stations[name].index))
    hashes = {
        str(p.relative_to(root)): sha256_file(p)
        for p in sorted(root.rglob("*.txt"))
        if p.relative_to(root).parts[0] not in {"budget-fixedCost-low", "budget-fixedCost-high"}
    }
    instance = BenchmarkInstance(
        root=str(root), stations=stations, active_stations=active, tracks=tracks,
        connections=connections, forbidden_turns=forbidden, allowed_type_changes=type_changes,
        od_pairs=tuple(od_pairs), parameters=params, file_hashes=hashes,
    )
    validate_instance(instance)
    return instance


def validate_instance(inst: BenchmarkInstance) -> None:
    """Check every cross-reference; raise BenchmarkFormatError on the first problem."""
    problems: List[str] = []
    for c in inst.connections.values():
        for s in (c.from_station, c.to_station):
            if s not in inst.stations:
                problems.append(f"connection {c.connection_id}: unknown station {s}")
        for t in c.track_ids:
            if t not in inst.tracks:
                problems.append(f"connection {c.connection_id}: unknown track {t}")
        rev = inst.connections.get(c.reverse_id)
        if rev is None:
            problems.append(f"connection {c.connection_id}: reverse {c.reverse_id} missing")
        else:
            if rev.reverse_id != c.connection_id:
                problems.append(f"connection {c.connection_id}: reverse not symmetric")
            if (rev.from_station, rev.to_station) != (c.to_station, c.from_station):
                problems.append(f"connection {c.connection_id}: reverse endpoints differ")
            if rev.train_type != c.train_type:
                problems.append(f"connection {c.connection_id}: reverse type differs")
        if c.track_ids:
            first, last = inst.tracks.get(c.track_ids[0]), inst.tracks.get(c.track_ids[-1])
            if first and first.from_station != c.from_station:
                problems.append(f"connection {c.connection_id}: first track start mismatch")
            if last and last.to_station != c.to_station:
                problems.append(f"connection {c.connection_id}: last track end mismatch")
    for pair in inst.forbidden_turns:
        for t in pair:
            if t not in inst.tracks:
                problems.append(f"forbidden turn references unknown track {t}")
    for t in inst.tracks.values():
        for s in (t.from_station, t.to_station):
            if s not in inst.stations:
                problems.append(f"track {t.track_id}: unknown station {s}")
    seen = set()
    for od in inst.od_pairs:
        for s in (od.origin, od.destination):
            if s not in inst.stations:
                problems.append(f"OD {od.key}: unknown station {s}")
            elif s not in inst.active_stations:
                problems.append(f"OD {od.key}: station {s} has no connection")
        if od.key in seen:
            problems.append(f"duplicate OD pair {od.key}")
        seen.add(od.key)
        if od.origin == od.destination or od.volume <= 0:
            problems.append(f"OD {od.key}: degenerate")
    if problems:
        raise BenchmarkFormatError("; ".join(problems[:10]) + (f" (+{len(problems)-10} more)" if len(problems) > 10 else ""))
