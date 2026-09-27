import logging
import math
import sys

from functools import partial
from core.exceptions.config_exceptions import ConfigTypeMismatchException, ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineReader
from core.io.ridepooling import RidepoolingPoolReader
from core.io.od import ODReader, ODWriter
from core.io.ptn import PTNReader, PTNWriter
from core.algorithm.dijkstra import Dijkstra
from core.util.mm_ean_helpers import makeDirected
from core.util.mm_utilities import MapOD
from core.util.multimodal_transfer_method import buildMTGraph, get_transfer_penalty
from core.util.config import Config
from core.model.mm_change_and_go_network import MMCG
from core.model.graph import Graph
from core.model.lines import LinePool
from core.model.mm_change_and_go import MMCGEdge, MMCGArcType
from core.model.multimodal_transfer import MTEdge, MTType
from core.model.ptn import Stop, Link
from core.model.ridepooling import RidepoolingPool

logger = logging.getLogger(__name__)

def get_MTEdge_weight(edge: MTEdge, model_weight_drive: str, model_weight_wait: str, min_wait_time: int, max_wait_time: int, transfer_penalty: float, penalty_algorithm: str) -> float:
    """
    Get weight of MTEdge based on configuration
    :param edge: MTEdge to get weight for
    :param config: configuration to use
    :return: weight of the edge
    """
    if edge.getType() == MTType.LINE:
        drive_time = 0
        wait_time = 0
        if model_weight_drive == "AVERAGE_DRIVING_TIME":
            drive_time = (edge.getLowerBound() + edge.getUpperBound()) / 2
        elif model_weight_drive == "MINIMAL_DRIVING_TIME":
            drive_time = edge.getLowerBound()
        elif model_weight_drive == "MAXIMAL_DRIVING_TIME":
            drive_time = edge.getUpperBound()
        elif model_weight_drive == "EDGE_LENGTH":
            drive_time = edge.getLength()
        else:
            raise ConfigTypeMismatchException("ean_model_weight_drive", "String", model_weight_drive)

        if model_weight_wait == "MINIMAL_WAITING_TIME":
            wait_time = min_wait_time
        elif model_weight_wait == "AVERAGE_WAITING_TIME":
            wait_time = (min_wait_time + max_wait_time) / 2
        elif model_weight_wait == "MAXIMAL_WAITING_TIME":
            wait_time =  max_wait_time
        elif model_weight_wait == "ZERO_COST":
            wait_time = 0
        else:
            raise ConfigTypeMismatchException("ean_model_weight_wait", "String", model_weight_wait)
        return drive_time + wait_time
    elif edge.getType() == MTType.TRANSFER:
        return get_transfer_penalty(transfer_penalty, penalty_algorithm)


def build_joint_graph_cg(config: Config, modes: list[str], graphs: dict[str, Graph[Stop, Link]]):
    """
    Build joint change-and-go graph with nonscheduled transfers
    :param config: configuration to use
    :param modes: list of all modes
    :param graphs: dictionary of PTNs for each mode
    :return: joint graph
    """

    line_concepts: dict[str, LinePool] = {}
    ridepooling_concepts: dict[str, RidepoolingPool] = {}

    modality_categories: dict[str, str] = {}
    for mode in modes:
        modality_categories[mode] = config.getStringValue("modality_category", modality=mode)

    for mode in modes:
        if modality_categories[mode] == "line-based":
            line_concepts[mode] = LineReader.read(ptn=graphs[mode], read_frequencies=True, read_costs=False, modality=mode)
            # we want only the line concept, but as LinePool object is required for MCGN building, delete zero lines from pool.
            for line in line_concepts[mode].getLines():
                if line.getFrequency() == 0:
                    line_concepts[mode].removeLine(line.getId())
        elif modality_categories[mode] == "ridepooling":
            ridepooling_concepts[mode] = RidepoolingPoolReader.read(ptn=graphs[mode], read_number_vehicles=True, modality=mode)
            for area in ridepooling_concepts[mode].getAreas():
                if area.getNumberOfVehicles() == 0:
                    ridepooling_concepts[mode].removeArea(area.getId())

    # extract the settings for transfer arc construction based on selected waiting and transfer models
    settings = MMCG.deduce_transfer_settings(config, modes)

    # build graph
    joint_graph_object = MMCG(
                    modalities = modes,
                    modality_categories = modality_categories,
                    ptns = graphs,
                    line_pools = line_concepts,
                    ridepooling_pools = ridepooling_concepts,
                    station_times = {mode: config.getDoubleValue("mm_cg_intermodal_transfer_time", mode) for mode in modes},
                    platform_times = {mode: config.getDoubleValue("mm_cg_intramodal_transfer_time", mode) for mode in modes},
                    detour_factors = {mode: config.getDoubleValue("mm_cg_rp_detour_factor", mode) for mode in modes if modality_categories[mode] == "ridepooling"},
                    create_intermodal_transfer_arcs=settings["create_intermodal_transfer_arcs"],
                    create_intramodal_transfer_arcs=settings["create_intramodal_transfer_arcs"],
                    create_all_transfer_arcs=settings["create_all_transfer_arcs"],
                    model_drive = config.getStringValue("mm_cg_model_drive")
        )

    # set waiting times
    for modality in modes:
        model_wait = config.getStringValue("mm_cg_model_wait", modality=modality)
        if model_wait.upper() != "ZERO_COST":
            joint_graph_object.set_waiting_times(
                                    modality,
                                    model_wait,
                                    config.getIntegerValue("mm_cg_minimal_waiting_time", modality=modality),
                                    config.getIntegerValue("mm_cg_maximal_waiting_time", modality=modality)
                                    )

    # set transfer times and penalties
    period = config.getIntegerValue("period_length")
    for modality in joint_graph_object.get_line_modalities():
        joint_graph_object.set_intramodal_transfer_times(modality, config.getStringValue("mm_cg_model_intramodal_change", modality=modality), period)
        penalty =  config.getIntegerValue("mm_cg_intramodal_transfer_penalty", modality=modality)
        if penalty > 0:
            joint_graph_object.set_intramodal_transfer_penalty(modality, penalty)

    joint_graph_object.set_intermodal_transfer_times(config.getStringValue("mm_cg_model_intermodal_change"), period=period)
    penalty =  config.getIntegerValue("mm_cg_intermodal_transfer_penalty")
    if penalty > 0:
        joint_graph_object.set_intermodal_transfer_penalty(penalty)

    joint_graph = joint_graph_object.get_graph()

    # Initialize loads to zero
    for edge in joint_graph.getEdges():
        edge.setLoad(0)

    return joint_graph_object

def distribute_passengers_sp(od_nodes, joint_graph, stopids_to_directed_link, modes, useCg, distance_function):
    """
    Distribute passengers using shortest paths in the joint graph
    :param od_nodes: overall OD matrix
    :param joint_graph: joint graph to use
    :param stopids_to_directed_link: mapping from stop ids to directed links
    :param modes: list of all modes
    :param useCg: whether to use change-and-go graph
    :param distance_function: function to use for distance calculation
    :return: dictionary of OD matrices for each mode
    """
    demands = {mode: {} for mode in modes}
    iteration_counter= 0
    n_origins = len(od_nodes.od.items())
    skipped_passengers = 0
    all_passengers = od_nodes.computeNumberOfPassengers()

    if useCg:
        joint_graph_object = joint_graph
        joint_graph = joint_graph_object.get_graph()

    for origin, targets in od_nodes.od.items():
        iteration_counter+=1
        if iteration_counter%100==0:
            logger.debug(f"Handling origin {iteration_counter} of {n_origins}.")
        if useCg:
            start_nodes = [joint_graph_object.get_origin_at_stop(origin)]
        else:
            start_nodes = [node for node in joint_graph.getNodes() if node.getStopId() == origin and node.modality != "" and getattr(node, "line_id", 0) == 0]

        dijkstra = Dijkstra(
            graph=joint_graph,
            start_node=start_nodes,
            distance_function=distance_function
        )
        dijkstra.computeShortestPaths()

        for target, demand in targets.items():
            if origin == target:
                continue
            if demand == 0:
                continue
            if useCg:
                target_nodes = [joint_graph_object.get_destination_at_stop(target)]
            else:
                target_nodes = [node for node in joint_graph.getNodes() if node.getStopId() == target and node.modality != "" and getattr(node, "line_id", 0) == 0]
            path = dijkstra.getPath(target_nodes)

            if path is None:
                skipped_passengers += od_nodes.getValue(origin, target)
                logger.debug(
                    f"No path from node {origin} to node {target}, {od_nodes.getValue(origin, target)} passengers are skipped.")
                continue

            edges = path.getEdges()
            nodes = path.getNodes()

            # skip leading origin/global nodes without modality
            start_index = 0
            while start_index < len(nodes) and nodes[start_index].getModality() is None:
                start_index += 1
            if start_index >= len(nodes):
                continue

            start_node = nodes[start_index]
            start_modality = start_node.getModality()
            current_node = start_node

            for edge in edges:
                if edge.getLeftNode().getModality() is None or edge.getRightNode().getModality() is None:
                    continue
                this_edge = joint_graph.getEdge(edge.getId())
                this_edge.setLoad(this_edge.getLoad() + demand)

                if this_edge.getLeftNode().getId() == current_node.getId(): # Forward edge
                    new_node = this_edge.getRightNode()
                else: # Backward edge
                    new_node = this_edge.getLeftNode()

                if new_node.getModality() == start_modality:
                    current_node = new_node
                elif new_node.getModality() != start_modality:
                    if start_modality != '' and start_modality is not None:
                        demands[start_modality][(start_node.getStopId(), current_node.getStopId())] = demands[start_modality].get((start_node.getStopId(), current_node.getStopId()),0) + demand
                    start_node = new_node
                    start_modality = new_node.getModality()
                    current_node = start_node
            demands[start_modality][(start_node.getStopId(), current_node.getStopId())] = demands[start_modality].get((start_node.getStopId(), current_node.getStopId()),0) + demand

    for edge in joint_graph.getEdges():
        if useCg:
            if edge.getType() not in [MMCGArcType.LINE, MMCGArcType.RIDEPOOLING, MMCGArcType.NONSCHEDULED]:
                continue
        else:
            if edge.getType() != MTType.LINE:
                continue
        mode = edge.getLeftNode().getModality()
        if mode != edge.getRightNode().getModality():
            raise RuntimeError('Edge is not a transfer edge but is intermodal')
        directed_link = stopids_to_directed_link[(edge.getLeftNode().getStopId(), edge.getRightNode().getStopId(), mode)]
        directed_link.setLoad(edge.getLoad() + directed_link.getLoad())

    if skipped_passengers > 0:
        logger.warning(f"{skipped_passengers} passengers ({round(skipped_passengers/all_passengers*100,2)}% of all passengers) cannot be routed and are ignored, as the corresponding infrastructure nodes are not connected.")

    od_splits = {}
    for mode in modes:
        od = MapOD()
        for (left_id, right_id), demand in demands[mode].items():
            od.setValue(left_id, right_id, demand)
        od_splits[mode] = od
    return od_splits

def main():
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()

    config = ConfigReader.read(sys.argv[-1])
    useCg = config.getBooleanValue("load_generator_use_cg")

    transfer_penalty = config.getDoubleValue(
        'ean_intermodal_change_penalty',
    )
    penalty_algorithm = config.getStringValue(
        'multimodal_transfer_penalty_algorithm',
    )

    scheduled_modalities: list[str] = []
    nonscheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            nonscheduled_modalities.append(mode)

    capacity_dict = {mode: config.getIntegerValue(f'{mode}.gen_passengers_per_vehicle') for mode in all_modalities}

    upper_freq_dict = {mode: config.getIntegerValue(f'{mode}.load_generator_fixed_upper_frequency') for mode in all_modalities}

    directed = {mode: not config.getBooleanValue("ptn_is_undirected", mode) for mode in all_modalities}

    write_od = config.getBooleanValue('load_generator_write_multimodal_od_split')

    model_weight_drive = config.getStringValue("ean_model_weight_drive").upper()
    model_weight_wait = config.getStringValue("ean_model_weight_wait").upper()
    min_wait_time = config.getIntegerValue("ean_default_minimal_waiting_time")
    max_wait_time = config.getIntegerValue("ean_default_maximal_waiting_time")

    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")

    od_nodes: MapOD = ODReader.readInfrastructureOd(od=None, size=None, config=config)

    graphs = {mode: PTNReader.read(config=config, modality=mode) for mode in all_modalities}

    # Remove all OD-pairs if no corresponding PTN node and print message if value is greater zero
    ptn_nodes = set([node.getId() for modality in all_modalities for node in graphs[modality].getNodes()])
    removed_passengers = 0
    all_passengers = od_nodes.computeNumberOfPassengers()
    for origin in list(od_nodes.od.keys()):
        for destination in list(od_nodes.od[origin].keys()):
            if origin not in ptn_nodes or destination not in ptn_nodes:
                removed_passengers += od_nodes.od[origin][destination]
                del od_nodes.od[origin][destination]

    for key in list(od_nodes.od.keys()):
        if od_nodes.od[key] == {}:
            del od_nodes.od[key]
    if removed_passengers > 0:
        logger.warning(f"{removed_passengers} passengers ({round(removed_passengers/all_passengers*100,2)}% of all passengers) cannot be routed and are ignored, as the corresponding infrastructure nodes are not included in any PTN.")


    directed_graphs = {mode: makeDirected(graph) for mode, graph in graphs.items() if not directed[mode]}
    directed_graphs.update({mode: graph for mode, graph in graphs.items() if directed[mode]})
    stopids_to_directed_link: dict[tuple[int, int, str], Link] = {}

    # Initialize loads and lower frequency bounds to zero
    # Also create mapping from stop ids to directed links
    for mode in all_modalities:
        graph = directed_graphs[mode]
        for edge in graph.getEdges():
            edge.setLoad(0)
            edge.setLowerFrequencyBound(0)
            left_stopid = edge.getLeftNode().getId()
            right_stopid = edge.getRightNode().getId()
            stopids_to_directed_link[(left_stopid, right_stopid, mode)] = edge

    if useCg:
        joint_graph = build_joint_graph_cg(config, all_modalities, graphs)

        logger.info("Finished reading input data")

        logger.info("Begin computing loads")

        od_splits = distribute_passengers_sp(od_nodes, joint_graph, stopids_to_directed_link, all_modalities, useCg, distance_function=MMCGEdge.getCost)

    else:
        joint_graph = buildMTGraph(
            graphs=list(directed_graphs.values()),
            penalty_algorithm=penalty_algorithm,
            transfer_penalty_rate=transfer_penalty,
        )

        logger.info("Finished reading input data")

        logger.info("Begin computing loads")

        distance_function = partial(get_MTEdge_weight, model_weight_drive=model_weight_drive, model_weight_wait=model_weight_wait, min_wait_time=min_wait_time, max_wait_time=max_wait_time, transfer_penalty=transfer_penalty, penalty_algorithm=penalty_algorithm)

        od_splits = distribute_passengers_sp(od_nodes, joint_graph, stopids_to_directed_link, all_modalities, useCg, distance_function=distance_function)

    # Set loads and frequency bounds on undirected links
    for mode in all_modalities:
        capacity = capacity_dict[mode]
        original_graph = graphs[mode]
        upper_frequency = upper_freq_dict[mode]
        for edge in original_graph.getEdges():
            left_stopid = edge.getLeftNode().getId()
            right_stopid = edge.getRightNode().getId()
            forward = stopids_to_directed_link[(left_stopid, right_stopid, mode)].getLoad()
            if not directed[mode]:
                backward = stopids_to_directed_link[(right_stopid, left_stopid, mode)].getLoad()
                load = max(forward, backward)
            else:
                load = forward
            edge.setLoad(load)
            edge.setLowerFrequencyBound(int(math.ceil(load / capacity)))
            edge.setUpperFrequencyBound(upper_frequency)

    logger.info("Finished computing loads")

    logger.info("Begin writing output")

    for mode in all_modalities:
        PTNWriter.write(ptn=graphs[mode], config=config, write_stops=False,
                        write_links=False, write_loads=True, modality=mode)
        if write_od:
            ODWriter.write(ptn=graphs[mode], config=config, od=od_splits[mode], modality=mode)

    logger.info("Finished writing output")

if __name__ == "__main__":
    main()

