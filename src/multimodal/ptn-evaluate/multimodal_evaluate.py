"""Generate statistics file and calculate statistics value for multimodal datasets"""
import logging
import sys

# TODO: Add expections and logging

from statistics import mean
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.statistic import StatisticWriter
from core.model.multimodal_transfer import MTNode, MTEdge, MTType
from core.model.ptn import Link, Stop
from core.model.graph import Graph
from core.util.statistic import Statistic
from core.util.mm_utilities import (
    MapOD,
    Dijkstra,
    _get_id_map,
    _remap_od
)
from core.util.multimodal_transfer_method import buildMTGraph

logger = logging.getLogger(__name__)

def simple_statistics(full_graph: Graph[MTNode, MTEdge], all_modalities: list[str]):
    """
    Calculate simple statistics of multimodal PTN
    :param full_graph: the multimodal transfer graph
    :return: tuple of different statistics
    """

    n_stops_modes = {mode: 0 for mode in all_modalities}
    n_edges_modes = {mode: 0 for mode in all_modalities}

    for node in full_graph.getNodes():
        if node.getModality() != "":
            n_stops_modes[node.getModality()] += 1
    for edge in full_graph.getEdges():
        if (edge.getLeftNode().getModality() != "") and (edge.getRightNode().getModality() != ""):
            n_edges_modes[edge.getLeftNode().getModality()] += 1

    return (n_stops_modes, n_edges_modes, len(full_graph.getNodes()), len(full_graph.getEdges()))


# Calculate OD-based statistics
def od_statistics(
   full_graph: Graph[MTNode, MTEdge],
   od: MapOD|None = None,
) -> tuple[float, float, bool]:
    """
    Calculate od-based statistics of multimodal PTN
    :param full_graph: the multimodal transfer graph
    :param od: input od object
    :return: tuple of different statistics
    """
    travel_times = []
    transfer_numbers = []
    is_feasible = True

    if od is None:
        od = ODReader.readInfrastructureOd(od=None, size=None)

    od_id_map = _get_id_map(full_graph)
    remapped_od = _remap_od(od, od_id_map)

    for origin, targets in remapped_od.od.items():
        dijkstra = Dijkstra(
           graph=full_graph,
           start_node=full_graph.getNode(origin),
           distance_function=MTEdge.getLowerBound,
        )

        for target in targets.keys():
            dijkstra.computeShortestPath(dijkstra.graph.getNode(target))
            path = dijkstra.getPath(dijkstra.graph.getNode(target))

            if path is None:
                if origin != target:
                    is_feasible = False
                continue

            this_transfers = 0
            this_travel_time = dijkstra.getDistance(dijkstra.graph.getNode(target))

            effective_path = path.getEdges()  # Remove initial and final transfer edges

            if (effective_path[0].getType() == MTType.TRANSFER):
                first_transfer = effective_path.pop(0).getLowerBound()
            else:
                first_transfer = 0

            if (effective_path[-1].getType() == MTType.TRANSFER):
                last_transfer = effective_path.pop(-1).getLowerBound()
            else:
                last_transfer = 0

            for edge in effective_path:  # There's 2 transfer edges for each transfer
                if (edge.getType() == MTType.TRANSFER):
                    this_transfers += 0.5
                    this_travel_time -= edge.getLowerBound() / 2

            travel_times.append(this_travel_time - first_transfer - last_transfer)
            transfer_numbers.append(this_transfers)

    avg_travel_time = mean(travel_times)
    avg_transfers_all = mean(transfer_numbers)

    return (avg_travel_time, avg_transfers_all, is_feasible)

def main():

    # Import graph from CSV

    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    # For all modes, read their corresponding Stops.giv and Edges.giv -files (all file paths in config)
    scheduled_modalities: list[str] = []
    nonscheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            nonscheduled_modalities.append(mode)
    joint_modality_name = config.getStringValue('modality_joint_name')

    PTN_modes = {}

    for mode in all_modalities:
        PTN_modes[mode] = PTNReader.read(config=config, modality=mode)

    transfer_penalty = config.getIntegerValue('ean_intermodal_change_penalty')
    transfer_penalty_algorithm = config.getStringValue(
        'multimodal_transfer_penalty_algorithm'
    )
    logger.info("Finished reading input data")

    logger.info("Begin building multimodal transfer graph for evaluation")
    # Edit the database PTN to contain transfer penalties
    full_graph = buildMTGraph(
        graphs=PTN_modes.values(),
        penalty_algorithm=transfer_penalty_algorithm,
        transfer_penalty_rate=transfer_penalty
    )
    logger.info("Finished building graph")

    # Initialize statistic file and folder for multimodal networks

    logger.info("Begin calculating statistics")
    statistic = Statistic()

    # Write the statistics to the file

    (
        n_stops_modes,
        n_edges_modes,
        n_stops_all,
        n_edges_all,
    ) = simple_statistics(full_graph=full_graph, all_modalities=all_modalities)

    (
        avg_travel_time,
        avg_transfers_all,
        is_feasible,
    ) = od_statistics(full_graph=full_graph)

    statistic.setValue(f"{joint_modality_name}.time_average", str(avg_travel_time))
    statistic.setValue(f"{joint_modality_name}.transfers_average", str(avg_transfers_all))
    statistic.setValue(f"{joint_modality_name}.feasible_od", str(is_feasible))

    statistic.setValue(f"{joint_modality_name}.obj_stops", str(n_stops_all))
    statistic.setValue(f"{joint_modality_name}.prop_edges", str(n_edges_all))

    for mode in all_modalities:
        statistic.setValue(f"{mode}.obj_stops", str(n_stops_modes[mode]))
        statistic.setValue(f"{mode}.prop_edges", str(n_edges_modes[mode]))

    logger.info("Finished calculating statistics")
    ###
    StatisticWriter.write(statistic)


if __name__ == '__main__':
    main()
