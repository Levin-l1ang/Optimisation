import logging
from typing import List

import networkx as nx
from networkx import single_source_dijkstra

from core.model.change_and_go import CGNode, CGEdge, CGArcType
from core.model.graph import Graph
from core.model.lines import Line, LinePool
from core.model.od import OD
from core.model.ptn import Link, Stop
from core.util.change_and_go_method import build_cg_graph
from core.util.statistic import Statistic
from helper import transform_cg_into_networkx
from multi_commodity_flow import capacitated_multi_commodity_flow
from parameters import Parameters

logger = logging.getLogger(__name__)


def change_and_go_evaluation(ptn: Graph[Stop, Link], lines: LinePool, od: OD,
                             parameters: Parameters, statistic: Statistic):

    if parameters.ean_model_weight_change == "MINIMAL_CHANGING_TIME":
        create_transfer_arcs = False
        cgn_path_difference = parameters.ean_change_penalty
        if not parameters.include_departure_adaption_time:
            cgn_path_difference += parameters.min_transfer_time / 2
        if not parameters.include_arrival_adaption_time:
            cgn_path_difference += parameters.min_transfer_time / 2
    else:
        create_transfer_arcs = not parameters.include_departure_adaption_time
        cgn_path_difference = 0

    change_and_go = build_cg_graph(
        line_concept=lines.getLineConcept(),
        directed=ptn.isDirected(),
        model_drive=parameters.ean_model_weight_drive,
        model_wait=parameters.ean_model_weight_wait,
        min_wait_time=parameters.min_wait_time,
        max_wait_time=parameters.max_wait_time,
        create_transfer_arcs=create_transfer_arcs,
        model_change=parameters.ean_model_weight_change,
        change_penalty=parameters.ean_change_penalty,
        min_change_time=parameters.min_transfer_time,
        period_length=parameters.period_length)

    logger.debug("Compute uncapacitated travel time")
    time_average, number_of_changes = uncapacitated_evaluation(
        change_and_go, ptn, od, cgn_path_difference)
    statistic.setValue("lc_perceived_time_average", time_average)
    statistic.setValue("lc_prop_changes", number_of_changes)

    logger.debug("Compute capacitated travel time")
    cap_travel_time, cap_transfers = capacitated_multi_commodity_flow(
        change_and_go, ptn, od, lines, parameters, cgn_path_difference)
    statistic.setValue("lc_capacitated_perceived_time_average", cap_travel_time)
    statistic.setValue("lc_capacitated_prop_changes", cap_transfers)

    logger.debug("Compute travel time model travel time")
    # Now build to model travel time model behavior
    change_and_go = build_cg_graph(
        line_concept=lines.getLineConcept(),
        directed=ptn.isDirected(),
        model_drive=parameters.ean_model_weight_drive,
        model_wait=parameters.ean_model_weight_wait,
        min_wait_time=parameters.min_wait_time,
        max_wait_time=parameters.max_wait_time,
        create_transfer_arcs=False,
        model_change="MINIMAL_CHANGING_TIME",
        change_penalty=parameters.ean_change_penalty,
        min_change_time=parameters.min_transfer_time,
        period_length=parameters.period_length)

    cgn_path_difference = parameters.ean_change_penalty
    if not parameters.include_departure_adaption_time:
        cgn_path_difference += parameters.min_transfer_time / 2
    if not parameters.include_arrival_adaption_time:
        cgn_path_difference += parameters.min_transfer_time / 2
    cap_travel_time, cap_transfers = capacitated_multi_commodity_flow(
        change_and_go, ptn, od, lines, parameters, cgn_path_difference)
    statistic.setValue("lc_obj_travel_time", cap_travel_time)


def uncapacitated_evaluation(cgn: Graph[CGNode, CGEdge], ptn: Graph[Stop, Link],
                             od: OD, cgn_path_difference: float):
    cgn_nx = transform_cg_into_networkx(cgn)

    total_travel_time_cg = 0
    number_of_changes = 0
    number_of_passengers = 0

    origin_nodes = {}
    destination_nodes = {}
    for cgnode in cgn.getNodes():
        if cgnode.isOrigin():
            origin_nodes[cgnode.getStopId()] = cgnode
        if cgnode.isDestination():
            destination_nodes[cgnode.getStopId()] = cgnode


    logger.debug("start computing shortest paths in (reduced) Networkx Change & Go Network")

    sp_cgn = {
        cgn_node: single_source_dijkstra(G=cgn_nx, source=cgn_node, weight="weight")
        for cgn_node in origin_nodes.values()
    }

    logger.debug("iterate od-pairs")
    for ptn_origin in ptn.getNodes():
        for ptn_destination in ptn.getNodes():
            passengers = od.getValue(ptn_origin.getId(), ptn_destination.getId())
            number_of_passengers += passengers
            if ptn_origin == ptn_destination or passengers == 0:
                continue
            origin = origin_nodes[ptn_origin.getId()]
            destination = destination_nodes[ptn_destination.getId()]
            l_cgn = sp_cgn[origin][0].get(destination, float("inf"))
            # We counted both boarding and alighting as an od edge with total
            # length cgn_path_difference, need to subtract this
            l_cgn -= cgn_path_difference
            path_cgn = sp_cgn[origin][1][destination]
            path = nx.path_graph(path_cgn)
            for edge in path.edges():
                if cgn_nx.edges[edge[0], edge[1]]['object'].getType() in {CGArcType.TRANSFER, CGArcType.BOARD}:
                    number_of_changes += passengers
            number_of_changes -= passengers
            total_travel_time_cg += passengers * l_cgn

    logger.info("shortest paths in reduced Networkx Change & Go Network done")
    return total_travel_time_cg / number_of_passengers, number_of_changes