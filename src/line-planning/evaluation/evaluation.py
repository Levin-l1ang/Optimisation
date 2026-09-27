import logging
import sys
from typing import Dict, List

import numpy as np

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.statistic import StatisticWriter
from core.model.graph import Graph
from core.model.impl.mapOD import MapOD
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.lines import Line, LinePool
from core.model.od import OD
from core.model.ptn import Stop, Link
from core.util.change_and_go_method import build_cg_graph
from core.util.statistic import Statistic
from cg_evaluation import change_and_go_evaluation
from extended_evaluation import extended_ptn_evaluation
from helper import transform_ptn_into_networkx, transform_cg_into_networkx
from parameters import Parameters
from single_source_shortest_paths import single_source_shortest_path_length


def shortest_paths_in_ptn(ptn: Graph[Stop, Link], link_frequencies: Dict[Link, int], lines: List[Line],
                          od: OD, parameters: Parameters, statistic: Statistic):
    ptn_nx = transform_ptn_into_networkx(ptn, parameters.drive_weight_function, parameters.wait_time, link_frequencies)

    ptn_nodes = ptn.getNodes()

    total_travel_time = 0
    direct_travellers_without_capacities = 0
    number_of_passengers = 0

    # Idea: Create a cgn. If the travel time of the shortest path in the ptn is
    # equal to the travel time of the shortest path in the cgn, there is a
    # shortest path without transferring, i.e., the passenger is a direct
    # traveler (without respecting capacities)
    cgn = build_cg_graph(
        line_concept=lines,
        model_drive=parameters.ean_model_weight_drive,
        model_wait=parameters.ean_model_weight_wait,
        model_change="MINIMAL_CHANGING_TIME",
        change_penalty=parameters.ean_change_penalty,
        min_change_time=parameters.period_length,
        min_wait_time=parameters.min_wait_time,
        max_wait_time=parameters.max_wait_time,
        period_length=parameters.period_length,
        create_transfer_arcs=False,
        directed=ptn.isDirected()
    )

    cgn_nx = transform_cg_into_networkx(cgn)
    od_nodes = {}
    for cgnode in cgn.getNodes():
        # Since create_transfer_arcs is False, origin and destination nodes
        # are the same.
        if cgnode.isOrigin():
            od_nodes[cgnode.getStopId()] = cgnode

    logger.debug("precompute shortest paths in the reduced PTN")
    sp_dist_ptn = {
        ptn_node: single_source_shortest_path_length(G=ptn_nx, source=ptn_node,
                                                     weight="weight")
        for ptn_node in ptn_nx
    }
    # Since the waiting time is added to every driving time, it is counted one
    # time too many.
    ptn_path_difference = parameters.wait_time

    logger.debug("precompute shortest paths in the CGN")
    sp_dist_cgn = {
        cgn_node: single_source_shortest_path_length(G=cgn_nx, source=cgn_node,
                                                     weight="weight")
        for cgn_node in od_nodes.values()
    }
    # In order to simulate transfers, half the transfer time (including
    # the penalty) is added to every boarding and alighting arc. Therefore, the
    # transfer time is counted one time too many.
    cgn_path_difference = parameters.period_length + parameters.ean_change_penalty

    logger.debug("iterate od-pairs")
    for ptn_origin in ptn_nodes:
        for ptn_destination in ptn_nodes:
            passengers = od.getValue(ptn_origin.getId(), ptn_destination.getId())
            if ptn_origin == ptn_destination or passengers == 0:
                continue
            l_ptn = sp_dist_ptn[ptn_origin].get(ptn_destination, float("inf")) - ptn_path_difference

            od_origin = od_nodes[ptn_origin.getId()]
            od_destination = od_nodes[ptn_destination.getId()]
            l_cgn = sp_dist_cgn[od_origin].get(od_destination, float("inf")) - cgn_path_difference
            direct = (l_ptn == l_cgn)
            if direct:
                direct_travellers_without_capacities += passengers
            total_travel_time += passengers * l_ptn
            number_of_passengers += passengers
    statistic.setValue("lc_uncapacitated_direct_travelers", direct_travellers_without_capacities)
    statistic.setValue("lc_time_average_without_transfers",
                       total_travel_time / number_of_passengers)


def basic_evaluation(ptn: Graph[Stop, Link], line_concept: List[Line],
                     statistic: Statistic) -> Dict[Link, int]:
    line_frequencies = []
    line_lengths = []
    line_distances = []
    line_no_edges = []
    cost = 0
    lc_prop_directed_lines = 0
    link_frequencies = {edge: 0 for edge in ptn.getEdges()}

    for line in line_concept:
        line_path_edges = line.getLinePath().getEdges()
        line_start_stop = line.getLinePath().getNodes()[0]
        line_end_stop = line.getLinePath().getNodes()[-1]

        cost += line.getCost() * line.getFrequency()

        for link in line_path_edges:
            link_frequencies[link] += line.getFrequency()

        if line.directed:
            lc_prop_directed_lines += 1
        else:
            lc_prop_directed_lines += 2

        dist = np.sqrt((line_start_stop.getXCoordinate() - line_end_stop.getXCoordinate()) ** 2 \
                       + (line_start_stop.getYCoordinate() - line_end_stop.getYCoordinate()) ** 2)

        line_no_edges.append(len(line_path_edges))
        line_frequencies.append(line.getFrequency())
        line_lengths.append(line.getLength())
        line_distances.append(dist)

    # lc_cost
    statistic.setValue("lc_cost", cost)
    # lc_prop_directed_lines
    statistic.setValue("lc_prop_directed_lines", lc_prop_directed_lines)
    # lc_prop_freq_max
    statistic.setValue("lc_prop_freq_max", max(line_frequencies))
    # lc_average_distance/edges/length
    statistic.setValue("lc_average_distance", np.average(line_distances))
    statistic.setValue("lc_average_edges", np.average(line_no_edges))
    statistic.setValue("lc_average_length", np.average(line_lengths))
    # lc_min_distance/edges/length
    statistic.setValue("lc_min_distance", min(line_distances))
    statistic.setValue("lc_min_edges", min(line_no_edges))
    statistic.setValue("lc_min_length", min(line_lengths))
    # lc_var_distance/edges/length
    statistic.setValue("lc_var_distance", np.var(line_distances))
    statistic.setValue("lc_var_edges", np.var(line_no_edges))
    statistic.setValue("lc_var_length", np.var(line_lengths))
    # lc_feasible/lc_obj_game
    lc_feasible = True
    lc_obj_game = 0
    for link in ptn.getEdges():
        lc_obj_game += link_frequencies.get(link) ** 2
        if link_frequencies.get(link) > link.getUpperFrequencyBound() or link_frequencies.get(
            link) < link.getLowerFrequencyBound():
            lc_feasible = False
    statistic.setValue("lc_feasible", lc_feasible)
    statistic.setValue("lc_obj_game", lc_obj_game)
    return link_frequencies


logger = logging.getLogger(__name__)

if __name__ == '__main__':

    logger.info("Start reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    parameters = Parameters(config)
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(ptn=SimpleDictGraph(), read_loads=True)
    lines = LineReader.read(ptn, read_frequencies=True)
    line_concept = lines.getLineConcept()
    od = ODReader.read(od=MapOD())
    logger.info("Finished reading input data")

    # set all basic parameters
    statistic = Statistic()
    link_frequencies = basic_evaluation(ptn, line_concept, statistic)

    # compute shortest paths in reduced networkx ptn
    shortest_paths_in_ptn(ptn, link_frequencies, line_concept, od, parameters,
                          statistic)

    if parameters.lc_eval_extended:
        change_and_go_evaluation(ptn, lines, od, parameters, statistic)
        extended_ptn_evaluation(ptn, od, lines, link_frequencies, statistic, parameters)

    # write statistic
    StatisticWriter.write(statistic)
