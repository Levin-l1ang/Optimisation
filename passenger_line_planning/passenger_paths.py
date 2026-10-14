"""Passenger-itinerary generation following LinTim's min-changes structure.

LinTim's ``MinChangesIP`` first generates k-shortest paths in the PTN and then
enumerates line combinations on each path.  The implementation below exposes
that preprocessing step without invoking the Xpress-dependent optimization
code.  It is a small, independently testable adapter, not a new routing model.
"""

from __future__ import annotations

import heapq
import math
from collections import defaultdict
from dataclasses import dataclass
from typing import Dict, Iterable, Iterator, List, Mapping, Optional, Sequence, Set, Tuple

from .domain import Itinerary, Leg, LinTimDataset


@dataclass(frozen=True)
class RoutingConfig:
    k_ptn_paths: int = 1
    k_itineraries: int = 3
    max_transfers: int = 2
    transfer_penalty: float = 5.0

    def __post_init__(self) -> None:
        if self.k_ptn_paths <= 0 or self.k_itineraries <= 0:
            raise ValueError("Path counts must be positive")
        if self.max_transfers < 0 or self.transfer_penalty < 0:
            raise ValueError("Transfer settings must be nonnegative")


@dataclass(frozen=True)
class _PTNPath:
    nodes: Tuple[int, ...]
    edges: Tuple[int, ...]
    cost: float


def _adjacency(dataset: LinTimDataset):
    graph: Dict[int, List[Tuple[int, float, int]]] = defaultdict(list)
    for edge in dataset.edges.values():
        graph[edge.left].append((edge.right, edge.travel_time, edge.edge_id))
        if not dataset.directed:
            graph[edge.right].append((edge.left, edge.travel_time, edge.edge_id))
    for neighbours in graph.values():
        neighbours.sort(key=lambda item: (item[0], item[2]))
    return graph


def _shortest_path(
    graph: Mapping[int, Sequence[Tuple[int, float, int]]],
    origin: int,
    destination: int,
    banned_nodes: Set[int] = frozenset(),
    banned_arcs: Set[Tuple[int, int, int]] = frozenset(),
) -> Optional[_PTNPath]:
    if origin in banned_nodes or destination in banned_nodes:
        return None
    distances = {origin: 0.0}
    predecessor: Dict[int, Tuple[int, int]] = {}
    queue: List[Tuple[float, int]] = [(0.0, origin)]
    while queue:
        distance, node = heapq.heappop(queue)
        if distance != distances.get(node):
            continue
        if node == destination:
            break
        for neighbour, weight, edge_id in graph.get(node, ()):  # pragma: no branch
            if neighbour in banned_nodes or (node, neighbour, edge_id) in banned_arcs:
                continue
            candidate = distance + weight
            old = distances.get(neighbour, math.inf)
            tie = predecessor.get(neighbour, (math.inf, math.inf))
            if candidate < old or (candidate == old and (node, edge_id) < tie):
                distances[neighbour] = candidate
                predecessor[neighbour] = (node, edge_id)
                heapq.heappush(queue, (candidate, neighbour))
    if destination not in distances:
        return None
    nodes = [destination]
    edges: List[int] = []
    current = destination
    while current != origin:
        previous, edge_id = predecessor[current]
        nodes.append(previous)
        edges.append(edge_id)
        current = previous
    nodes.reverse()
    edges.reverse()
    return _PTNPath(tuple(nodes), tuple(edges), distances[destination])


def _k_shortest_paths(
    dataset: LinTimDataset, origin: int, destination: int, k: int
) -> Tuple[_PTNPath, ...]:
    """Yen's deterministic k-shortest loopless path algorithm."""

    graph = _adjacency(dataset)
    first = _shortest_path(graph, origin, destination)
    if first is None:
        return tuple()
    accepted = [first]
    accepted_keys = {(first.nodes, first.edges)}
    candidates: List[Tuple[float, Tuple[int, ...], Tuple[int, ...], _PTNPath]] = []
    candidate_keys = set()
    edge_weight = {edge.edge_id: edge.travel_time for edge in dataset.edges.values()}

    while len(accepted) < k:
        previous = accepted[-1]
        root_cost = 0.0
        for spur_index in range(len(previous.nodes) - 1):
            root_nodes = previous.nodes[: spur_index + 1]
            root_edges = previous.edges[:spur_index]
            banned_arcs = set()
            for path in accepted:
                if path.nodes[: spur_index + 1] == root_nodes and len(path.edges) > spur_index:
                    banned_arcs.add(
                        (path.nodes[spur_index], path.nodes[spur_index + 1], path.edges[spur_index])
                    )
            spur = _shortest_path(
                graph,
                root_nodes[-1],
                destination,
                banned_nodes=set(root_nodes[:-1]),
                banned_arcs=banned_arcs,
            )
            if spur is not None:
                nodes = root_nodes[:-1] + spur.nodes
                edges = root_edges + spur.edges
                key = (nodes, edges)
                if key not in accepted_keys and key not in candidate_keys:
                    candidate = _PTNPath(nodes, edges, root_cost + spur.cost)
                    heapq.heappush(candidates, (candidate.cost, nodes, edges, candidate))
                    candidate_keys.add(key)
            root_cost += edge_weight[previous.edges[spur_index]]
        if not candidates:
            break
        _, _, _, chosen = heapq.heappop(candidates)
        candidate_keys.remove((chosen.nodes, chosen.edges))
        accepted.append(chosen)
        accepted_keys.add((chosen.nodes, chosen.edges))
    return tuple(accepted)


def _line_orientations(dataset: LinTimDataset):
    orientations: List[Tuple[int, Tuple[int, ...], Tuple[int, ...]]] = []
    for line in dataset.lines.values():
        orientations.append((line.line_id, line.stop_ids, line.edge_ids))
        if not dataset.directed:
            orientations.append(
                (line.line_id, tuple(reversed(line.stop_ids)), tuple(reversed(line.edge_ids)))
            )
    return orientations


def _orientation_index(dataset: LinTimDataset):
    index: Dict[
        Tuple[int, int, int],
        List[Tuple[int, Tuple[int, ...], Tuple[int, ...], int]],
    ] = defaultdict(list)
    for line_id, stops, edge_ids in _line_orientations(dataset):
        for offset, edge_id in enumerate(edge_ids):
            index[(edge_id, stops[offset], stops[offset + 1])].append(
                (line_id, stops, edge_ids, offset)
            )
    for values in index.values():
        values.sort(key=lambda item: (item[0], item[3]))
    return index


def _leg_options(dataset: LinTimDataset, path: _PTNPath, orientation_index=None):
    if orientation_index is None:
        orientation_index = _orientation_index(dataset)
    options: Dict[int, List[Tuple[int, Leg]]] = defaultdict(list)
    for start in range(len(path.edges)):
        key = (path.edges[start], path.nodes[start], path.nodes[start + 1])
        for line_id, stops, edge_ids, offset in orientation_index.get(key, ()):
            length = 0
            while (
                start + length < len(path.edges)
                and offset + length < len(edge_ids)
                and edge_ids[offset + length] == path.edges[start + length]
                and stops[offset + length + 1] == path.nodes[start + length + 1]
            ):
                length += 1
                arcs = tuple(
                    (
                        path.edges[position],
                        path.nodes[position],
                        path.nodes[position + 1],
                    )
                    for position in range(start, start + length)
                )
                options[start].append(
                    (
                        start + length,
                        Leg(
                            line_id,
                            path.nodes[start],
                            path.nodes[start + length],
                            arcs,
                        ),
                    )
                )
    for values in options.values():
        values.sort(
            key=lambda item: (
                -len(item[1].arcs),
                item[1].line_id,
                item[1].alight_stop,
            )
        )
    return options


def _itineraries_on_path(
    dataset: LinTimDataset,
    path: _PTNPath,
    maximum: int,
    max_transfers: int,
    orientation_index=None,
) -> Tuple[Itinerary, ...]:
    options = _leg_options(dataset, path, orientation_index)
    queue: List[
        Tuple[int, Tuple[Tuple[int, int, int], ...], int, Tuple[int, ...], Tuple[Leg, ...]]
    ] = [(0, tuple(), 0, tuple(), tuple())]
    found: List[Itinerary] = []
    signatures = set()
    while queue and len(found) < maximum:
        _, signature, position, used_lines, legs = heapq.heappop(queue)
        if max(0, len(legs) - 1) > max_transfers:
            continue
        if position == len(path.edges):
            if signature not in signatures:
                signatures.add(signature)
                found.append(
                    Itinerary(path.nodes[0], path.nodes[-1], legs, path.cost)
                )
            continue
        for end, leg in options.get(position, ()):  # no route if an edge is unserved
            if leg.line_id in used_lines:
                continue
            next_legs = legs + (leg,)
            # A trip with ``max_transfers`` transfers has at most
            # ``max_transfers + 1`` legs.  Prune before queue insertion:
            # generating every over-limit continuation can otherwise create a
            # very large heap even though all such states are discarded when
            # popped.
            if len(next_legs) > max_transfers + 1:
                continue
            next_signature = signature + (
                (leg.line_id, leg.board_stop, leg.alight_stop),
            )
            heapq.heappush(
                queue,
                (
                    len(next_legs),
                    next_signature,
                    end,
                    used_lines + (leg.line_id,),
                    next_legs,
                ),
            )
    return tuple(found)


def route_demands(
    dataset: LinTimDataset,
    config: RoutingConfig,
    od_pairs: Optional[Iterable[Tuple[int, int]]] = None,
) -> Iterator[Tuple[Tuple[int, int], float, Tuple[Itinerary, ...]]]:
    """Yield routes for every requested positive-demand OD pair.

    No OD is silently removed: missing itineraries are yielded as an empty
    tuple and must be handled by the caller/reporting layer.
    """

    pairs = sorted(dataset.demand if od_pairs is None else od_pairs)
    for pair in pairs:
        if pair not in dataset.demand:
            raise KeyError(f"Requested OD pair {pair} has no positive demand")

    orientation_index = _orientation_index(dataset)
    if config.k_ptn_paths != 1:
        for pair in pairs:
            candidates: List[Itinerary] = []
            seen = set()
            for path in _k_shortest_paths(
                dataset, pair[0], pair[1], config.k_ptn_paths
            ):
                for itinerary in _itineraries_on_path(
                    dataset,
                    path,
                    config.k_itineraries,
                    config.max_transfers,
                    orientation_index,
                ):
                    if itinerary.signature not in seen:
                        seen.add(itinerary.signature)
                        candidates.append(itinerary)
            candidates.sort(
                key=lambda item: (
                    item.ride_time + config.transfer_penalty * item.transfers,
                    item.transfers,
                    item.signature,
                )
            )
            yield pair, dataset.demand[pair], tuple(
                candidates[: config.k_itineraries]
            )
        return

    # The full audit normally asks for k=1.  One Dijkstra tree per origin is
    # materially smaller than one Dijkstra call per OD pair and is exact for
    # the first shortest PTN path.
    graph = _adjacency(dataset)
    by_origin: Dict[int, List[Tuple[int, int]]] = defaultdict(list)
    for pair in pairs:
        by_origin[pair[0]].append(pair)
    for origin in sorted(by_origin):
        distances = {origin: 0.0}
        predecessor: Dict[int, Tuple[int, int]] = {}
        queue: List[Tuple[float, int]] = [(0.0, origin)]
        while queue:
            distance, node = heapq.heappop(queue)
            if distance != distances.get(node):
                continue
            for neighbour, weight, edge_id in graph.get(node, ()):
                candidate = distance + weight
                old = distances.get(neighbour, math.inf)
                tie = predecessor.get(neighbour, (math.inf, math.inf))
                if candidate < old or (
                    candidate == old and (node, edge_id) < tie
                ):
                    distances[neighbour] = candidate
                    predecessor[neighbour] = (node, edge_id)
                    heapq.heappush(queue, (candidate, neighbour))
        for pair in by_origin[origin]:
            destination = pair[1]
            if destination not in distances:
                itineraries = tuple()
            else:
                nodes = [destination]
                edge_ids: List[int] = []
                current = destination
                while current != origin:
                    previous, edge_id = predecessor[current]
                    nodes.append(previous)
                    edge_ids.append(edge_id)
                    current = previous
                nodes.reverse()
                edge_ids.reverse()
                path = _PTNPath(tuple(nodes), tuple(edge_ids), distances[destination])
                itineraries = _itineraries_on_path(
                    dataset,
                    path,
                    config.k_itineraries,
                    config.max_transfers,
                    orientation_index,
                )
            yield pair, dataset.demand[pair], itineraries

