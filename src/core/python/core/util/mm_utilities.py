"""Utilities for working with multimodal datasets."""
import os
from typing import Iterable

from core.algorithm.dijkstra import Dijkstra
from core.model.graph import Graph
from core.model.impl.mapOD import MapOD
from core.model.multimodal_transfer import MTEdge, MTNode
from core.model.mm_change_and_go import MMCGNode, MMCGEdge


# Type alias for a stop id pair.
_Key = tuple[int, int]


def add_modality_prefix(modality: str, filepath: str) -> str:
    """Add modality prefix to a filename in a path.

    :param modality: Modality prefix that will be added, separated by a dot.
    :param filepath: File path that will be modified to include the modality
        prefix for the file name.
    :return: Modified file path.
    """
    base_path, filename = os.path.split(filepath)
    # remove possible pre-existing prefix
    if filename.endswith(".giv.geo"):
        filename_parts = filename.split('.')[-3:]
    else:
        filename_parts = filename.split('.')[-2:]
    filename = '.'.join(filename_parts)
    return os.path.join(base_path, f'{modality}.{filename}')


def _get_id_from_node_list(nodes: list[MTNode]) -> int:
    """
    Get node id for OD from the list of nodes with the same stop id.
    If there is only one node, return its id. If there are multiple nodes,
    return the id of the transfer node (modality == ''). If there are multiple
    nodes but no transfer node, raise an error.
    :param nodes: List of nodes with the same stop id
    :return: The id of the node to use for OD
    """
    if len(nodes) == 1:
        # no other modalities at this stop
        return nodes[0].node_id
    for node in nodes:
        # multiple modalities, filter for the transfer node
        if node.modality == '':
            return node.node_id
    # multiple nodes for the same stop id but no transfer node -> error
    raise RuntimeError('No valid node found for OD node id mapping!')


def _get_id_map(graph: Graph[MTNode, MTEdge]) -> dict[int, int]:
    """
    Get stop id to node id map for OD.
    If there are multiple nodes for the same stop id, the transfer node is used
    (modality == ''). If there are multiple nodes but no transfer node, an error
    is raised.
    :param graph: The multimodal graph
    :return: The stop id to node id map
    """
    id_to_nodes: dict[int, list[MTNode]] = {}
    for node in graph.getNodes():
        id_to_nodes.setdefault(node.stop_id, []).append(node)
    return {
        stop_id: _get_id_from_node_list(nodes)
        for (stop_id, nodes) in id_to_nodes.items()
    }


def _remap_od(od: MapOD, id_map: dict[int, int]) -> MapOD:
    """
    Map OD ids with a given ID map.
    :param od: The OD to remap
    :param id_map: The map from old ids to new ids
    :return: The remapped OD
    """
    pairs = od.getODPairs()
    new_od = MapOD()
    for pair in pairs:
        new_od.setValue(
            id_map[pair.getOrigin()],
            id_map[pair.getDestination()],
            pair.getValue(),
        )
    return new_od


def _get_ordered_nodes(
    origin_id: int,
    edges: Iterable[MTEdge],
) -> Iterable[MTNode]:
    """
    Get nodes in order starting from origin_id.
    :param origin_id: The id of the starting node
    :param edges: The edges to traverse
    :return: The nodes in order
    """
    prev_id = origin_id
    for edge in edges:
        left = edge.getLeftNode()
        right = edge.getRightNode()
        left_id = left.getId()
        right_id = right.getId()
        if left_id == prev_id:
            prev_id = right_id
            yield right
        else:
            prev_id = left_id
            yield left


def _get_same_modality_edges(
        origin: MTNode,
        edges: list[MTEdge],
) -> Iterable[tuple[str, _Key]]:
    """Get same modality OD pairs along the provided path.

    Assumes, that the iterable has edges in the proper order with next edge's
    left node being equal to the previous edge's right node. Returns keys in
    terms of stop ids instead of node ids.

    :param origin: The starting node of the path
    :param edges: Iterable of MTEdges.
    :return: Generator of tuples (modality, key), where key is a tuple of
    stop ids.
    """
    left = origin.getStopId()
    right = origin.getStopId()
    modality = origin.modality
    for node in _get_ordered_nodes(origin.node_id, edges):
        if node.modality == modality:
            right = node.getStopId()
        else:
            if modality != '' and left != right:
                yield modality, (left, right)
            modality = node.modality
            left = node.getStopId()
            right = node.getStopId()
    if modality != '' and right != left:
        yield modality, (left, right)


def _dict_to_OD(demands: dict[_Key, float]) -> MapOD:
    """
    Get MapOD from a dict of demands between node id pairs.
    :param demands: The dict of demands between node id pairs
    :return: OD matrix
    """
    od = MapOD()
    for (left_id, right_id), demand in demands.items():
        od.setValue(left_id, right_id, demand)
    return od


def split_od(od: MapOD, graph: Graph[MTNode, MTEdge]) -> dict[str, MapOD]:
    """
    Split Infrastructure-OD demands by modality.

    Calculates the shortest path between all OD pairs and then assigns the
    demand to new node pairs along the path that belong to the same modality.

    :param od: Infrastructure-OD demand.
    :param graph: Joined multimodal graph.
    :return: Dict of ODs keyed by the corresponding modality.
    """
    od_id_map = _get_id_map(graph)
    remapped_od = _remap_od(od, od_id_map)
    demands: dict[str, dict[_Key, float]] = {}
    for origin, targets in remapped_od.od.items():
        start_node = graph.getNode(origin)
        dijkstra = Dijkstra(
            graph=graph,
            start_node=start_node,
            distance_function=MTEdge.getLowerBound,
        )
        for target, demand in targets.items():
            if origin == target:
                continue
            target_node = graph.getNode(target)
            dijkstra.computeShortestPath(target_node)
            path = dijkstra.getPath(target_node)
            if path is None:
                raise RuntimeError('Graph is invalid: no path found!')
            # start_node = dijkstra.start_node
            edges = path.getEdges()
            for modality, key in _get_same_modality_edges(start_node, edges):
                modality_demand = demands.setdefault(modality, {})
                modality_demand[key] = modality_demand.get(key, 0) + demand
    return {
        modality: _dict_to_OD(modality_demand)
        for modality, modality_demand in demands.items()
    }

def split_od_cg(od: MapOD, graph: Graph[MMCGNode, MMCGEdge]) -> dict[str, MapOD]:
    """
    Split Infrastructure-OD demands by modality for Change-and-Go graph.
    Calculates the shortest path between all OD pairs and then assigns the
    demand to new node pairs along the path that belong to the same modality.
    :param od: Infrastructure-OD demand.
    :param graph: Joined multimodal Change-and-Go graph.
    :return: Dict of ODs keyed by the corresponding modality.
    """
    demands: dict[str, dict[_Key, float]] = {}

    for origin, targets in od.od.items():
        start_nodes = [node for node in graph.getNodes() if node.getStopId() == origin]

        dijkstra = Dijkstra(
            graph=graph,
            start_node=start_nodes,
            distance_function=MMCGEdge.getWeight,
        )
        for target, demand in targets.items():
            if origin == target:
                continue
            target_nodes = [node for node in graph.getNodes() if node.getStopId() == target]
            dijkstra.computeShortestPath(target_nodes)
            path = dijkstra.getPath(target_nodes)
            if path is None:
                raise RuntimeError('Graph is invalid: no path found!')

            edges = path.getEdges()
            start_node = path.getNodes()[0]
            for modality, key in _get_same_modality_edges_cg(start_node, edges):
                modality_demand = demands.setdefault(modality, {})
                modality_demand[key] = modality_demand.get(key, 0) + demand
    return {
        modality: _dict_to_OD(modality_demand)
        for modality, modality_demand in demands.items()
    }

def _get_same_modality_edges_cg(
        origin: MMCGNode,
        edges: list[MMCGEdge],
) -> Iterable[tuple[str, _Key]]:
    """
    Get same modality OD pairs along the provided path.

    Assumes, that the iterable has edges in the proper order with next edge's
    left node being equal to the previous edge's right node. Returns keys in
    terms of stop ids instead of node ids.

    :param origin: The starting node of the path
    :param edges: Iterable of MMCGEdges.
    :return: Generator of tuples (modality, key), where key is a tuple of
    stop ids.
    """
    left = origin.getStopId()
    right = origin.getStopId()
    modality = origin.modality
    for node in _get_ordered_nodes(origin.id, edges):
        if node.modality == modality:
            right = node.getStopId()
        else:
            if modality != '' and left != right:
                yield modality, (left, right)
            modality = node.modality
            left = node.getStopId()
            right = node.getStopId()
    if modality != '' and right != left:
        yield modality, (left, right)

