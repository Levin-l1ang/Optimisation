import logging
import sys

import networkx as nx

from typing import Dict
from collections import defaultdict

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException

from core.util.networkx import convert_graph_to_networkx, convert_nxpath_to_listpath
from core.util.statistic import Statistic
from core.util.config import Config

from core.model.impl.list_path import ListPath

from core.solver.solver_parameters import SolverParameters

from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.io.lines import LineReader
from core.io.ridepooling import RidepoolingPoolReader
from core.io.statistic import StatisticWriter

from core.model.graph import Graph
from core.model.ptn import Stop, Link
from core.model.lines import Line, LinePool
from core.model.ridepooling import RidepoolingArea, RidepoolingPool
from core.model.infrastructure_network import InfraNode, InfraLink
from core.model.od import OD
from core.model.mm_change_and_go_network import MMCG
from core.model.mm_change_and_go import MMCGNode, MMCGEdge, MMCGNodeType, MMCGArcType

from mm_travel_time_mip import mm_travel_time_mip


logger = logging.getLogger(__name__)


def basic_multimodal_concept_evaluation(ptns: dict[str, Graph[Stop, Link]],
                                        lpools: dict[str, LinePool],
                                        rpools: dict[str, RidepoolingPool],
                                        isn_capacity_weights: dict[str, float],
                                        vehicle_capacities: dict[str, int],
                                        isn: Graph[InfraNode, InfraLink],
                                        statistic: Statistic,
                                        joint_modality_name: str,
                                        check_isn_feasibility: bool):
    """
    Performs basic evaluation of a multimodal line concept.
    :param ptns: Dictionary of PTNs for all modes.
    :param lpools: Dictionary of line concepts for line-based modalities.
    :param rpools: Dictionary of ridepooling concepts for ridepooling modalities.
    :param statistic: Statistic object to store evaluation results.
    :param joint_modality_name: Name for the joint modality (e.g., "joint").
    :param check_isn_feasibilty: whether to check feasibility w.r.t ISN loads. Only if this file is present
    :return:
    """
    modalities = ptns.keys()

    # compute stuff
    cost = 0
    line_cost = 0
    ridepooling_cost = 0

    lc_number_of_directed_lines = 0
    rc_number_of_areas = 0

    for modality in lpools.keys():
        mode_cost = 0
        mode_lc_number_of_directed_lines = 0
        for line in lpools[modality].getLines():
            c = line.getFrequency()*line.getCost()
            cost += c
            line_cost += c
            mode_cost += c
            if line.directed:
                lc_number_of_directed_lines += 1
                mode_lc_number_of_directed_lines += 1
            else:
                lc_number_of_directed_lines += 2
                mode_lc_number_of_directed_lines += 2
        statistic.setValue(f"{modality}.lc_cost", mode_cost)
        statistic.setValue(f"{modality}.lc_number_of_directed_lines", mode_lc_number_of_directed_lines)

    for modality in rpools.keys():
        mode_cost = 0
        mode_rc_number_of_areas = 0
        for area in rpools[modality].getAreas():
            c = rpools[modality].getCost()*area.getNumberOfVehicles()
            cost += c
            ridepooling_cost += c
            mode_cost += c
            rc_number_of_areas += 1
            mode_rc_number_of_areas += 1
        statistic.setValue(f"{modality}.rc_cost", mode_cost)
        statistic.setValue(f"{modality}.rc_number_of_areas", mode_rc_number_of_areas)

    # cost
    statistic.setValue(f"{joint_modality_name}.lc_cost", line_cost)
    statistic.setValue(f"{joint_modality_name}.rc_cost", ridepooling_cost)
    statistic.setValue(f"{joint_modality_name}.lc_rc_cost", cost)
    # number of lines/areas
    statistic.setValue(f"{joint_modality_name}.lc_number_of_directed_lines", lc_number_of_directed_lines)
    statistic.setValue(f"{joint_modality_name}.rc_number_of_areas", rc_number_of_areas)

    # check feasibility according to ISN loads and capacities
    if check_isn_feasibility:
        lines_on_infra_link: Dict[InfraLink, list[Line]] = defaultdict(list)
        freq_sum = {link: 0 for link in isn.getEdges()}
        line_cap = {link: 0 for link in isn.getEdges()}
        for modality in lpools.keys():
            for line in lpools[modality].getLines():
                for edge in line.getLinePath().getEdges():
                    for link in edge.getUnderlyingInfrastructure().getEdges():
                        lines_on_infra_link[link].append(line)
                        freq_sum[link] += line.getFrequency()*isn_capacity_weights[modality]
                        line_cap[link] += line.getFrequency()*vehicle_capacities[modality]

        areas_on_infra_link: Dict[InfraLink, list[RidepoolingArea]] = defaultdict(list)
        veh_sum = {link: 0 for link in isn.getEdges()}
        rp_cap = {link: 0 for link in isn.getEdges()}
        for modality in rpools.keys():
            for area in rpools[modality].getAreas():
                for edge in area.getEdges():
                    for link in edge.getUnderlyingInfrastructure().getEdges():
                        areas_on_infra_link[link].append(area)
                        veh_sum[link] += area.getNumberOfVehicles()*area.getVehicleFrequency(edge.getId())*isn_capacity_weights[modality]
                        rp_cap[link] += area.getNumberOfVehicles()*area.getVehicleFrequency(edge.getId())*vehicle_capacities[modality]

        feasible = True
        for link in isn.getEdges():
            if freq_sum[link] + veh_sum[link] > link.getCapacity() or line_cap[link] + rp_cap[link] < link.getLoad():
                feasible = False

        statistic.setValue(f"{joint_modality_name}.lc_rc_feasible", feasible)


def uncapacitated_average_travel_time(config: Config,
                                      ptns: dict[str, Graph[Stop, Link]],
                                      lpools: dict[str, LinePool],
                                      rpools: dict[str, RidepoolingPool],
                                      od: OD,
                                      joint_modality_name: str,
                                      statistic: Statistic):

    # build MMCG network
    mcgn = MMCG.build_MMCG_from_config_settings(config=config,
                                                modalities=ptns.keys(),
                                                ptns=ptns,
                                                line_pools=lpools,
                                                ridepooling_pools=rpools,
                                                use_concepts=True)

    # translate to networkx
    nx_mcgn = convert_graph_to_networkx(graph=mcgn.get_graph(),
                                        multi_graph=False,
                                        weight_function=lambda e: e.getCost())

    # for each od pair compute a shortest path and its travel time
    overall_travel_time = 0
    overall_number_passengers = 0
    transfers = {m: 0 for m in ptns.keys()} # number of transfers in modality
    mode_times = {m: 0 for m in ptns.keys()} # travel time in modality
    overall_transfers = 0 # all transfers
    overall_mode_transfers = 0 # transfers between modalities

    for od_pair in od.getODPairs():
        o_stop = od_pair.getOrigin()
        d_stop = od_pair.getDestination()

        o_node = mcgn.get_origin_at_stop(o_stop)
        d_node = mcgn.get_destination_at_stop(d_stop)

        nxPath = nx.dijkstra_path(nx_mcgn, o_node.getId(), d_node.getId())
        length = nx.path_weight(nx_mcgn, nxPath, "weight")

        path: ListPath[MMCGNode, MMCGEdge] = convert_nxpath_to_listpath([mcgn.get_graph().getNode(n) for n in nxPath], mcgn.get_graph())
        for arc in path.getEdges():
            # travel time by modalities
            if arc.getType() in [MMCGArcType.LINE, MMCGArcType.RIDEPOOLING, MMCGArcType.NONSCHEDULED, MMCGArcType.TRANSFER]:
                # all time
                mode_times[arc.getLeftNode().getModality()] += arc.getCost()*od_pair.getValue()
                # transfers within a modality
                if arc.getType() == MMCGArcType.TRANSFER:
                    overall_transfers += od_pair.getValue()
                    transfers[arc.getLeftNode().getModality()] += od_pair.getValue()
            # intermodal transfers
            if arc.getType() == MMCGArcType.MODE_TRANSFER:
                overall_transfers += od_pair.getValue()
                overall_mode_transfers += od_pair.getValue()

        overall_number_passengers += od_pair.getValue()
        overall_travel_time += od_pair.getValue()*length

    statistic.setValue(f"{joint_modality_name}.lc_rc_uncapacitated_time_average", overall_travel_time/overall_number_passengers)
    statistic.setValue(f"{joint_modality_name}.lc_rc_uncapacitated_changes", overall_transfers)
    statistic.setValue(f"{joint_modality_name}.lc_rc_uncapacitated_intermodal_changes", overall_mode_transfers)
    statistic.setValue(f"{joint_modality_name}.lc_rc_uncapacitated_total_travel_time", overall_travel_time)
    for modality in ptns.keys():
        statistic.setValue(f"{modality}.lc_rc_uncapacitated_total_travel_time", mode_times[modality])
        statistic.setValue(f"{modality}.lc_rc_uncapacitated_changes", transfers[modality])


def capacitated_evaluation(config: Config,
                           ptns: dict[str, Graph[Stop, Link]],
                           lpools: dict[str, LinePool],
                           rpools: dict[str, RidepoolingPool],
                           vehicle_capacities: dict[str, int],
                           period: float,
                           flow_vars_type: str,
                           parameters: SolverParameters,
                           od: OD,
                           joint_modality_name: str,
                           statistic: Statistic):

    # build MMCG network
    mcgn = MMCG.build_MMCG_from_config_settings(config=config,
                                                modalities=ptns.keys(),
                                                ptns=ptns,
                                                line_pools=lpools,
                                                ridepooling_pools=rpools,
                                                use_concepts=True)

    # set all modalities as fixed modalities
    fixed_concepts = [m for m in ptns.keys()]

    # od matrix things
    total_passengers = od.computeNumberOfPassengers()
    od_pairs_by_origin = od.getODPairsByOriginID()

    # EVALUATION FOR PLATFORM AND STATION TIMES

    # solve routing problem in MMCG network
    model = mm_travel_time_mip(cgn=mcgn,
                                infra_od=od,
                                budget=-1,
                                period_length=period,
                                vehicle_capacities=vehicle_capacities,
                                flow_vars_type=flow_vars_type,
                                parameters=parameters,
                                use_start_solution=True,
                                minimize_cost=False,
                                time_budget=-1,
                                fixed_concepts=fixed_concepts)
    model.solve()

    # evaluate stuff
    total_travel_time = model.getObjectiveValue()
    statistic.setValue(f"{joint_modality_name}.lc_rc_capacitated_travel_time", total_travel_time)
    statistic.setValue(f"{joint_modality_name}.lc_rc_capacitated_average_travel_time", total_travel_time/total_passengers)

    mode_times = {m: 0 for m in ptns.keys()} # travel time in modality
    transfers = {m: 0 for m in ptns.keys()} # number of transfers in modality
    overall_transfers = 0 # number of all transfers
    overall_mode_transfers = 0 # number of intermodal transfers

    for arc in mcgn.get_graph().getEdges():
        passengers_on_arc = 0
        for origin in od_pairs_by_origin.keys():
            passengers_on_arc += model.getValue(model.passenger_flows[origin][arc])
        if arc.getType() in [MMCGArcType.LINE, MMCGArcType.RIDEPOOLING, MMCGArcType.NONSCHEDULED, MMCGArcType.TRANSFER]:
            # all time
            mode_times[arc.getLeftNode().getModality()] += arc.getCost()*passengers_on_arc
            # transfers within a modality
            if arc.getType() == MMCGArcType.TRANSFER:
                overall_transfers += passengers_on_arc
                transfers[arc.getLeftNode().getModality()] += passengers_on_arc
        # intermodal transfers
        if arc.getType() == MMCGArcType.MODE_TRANSFER:
            overall_transfers += passengers_on_arc
            overall_mode_transfers += passengers_on_arc

    statistic.setValue(f"{joint_modality_name}.lc_rc_capacitated_changes", overall_transfers)
    statistic.setValue(f"{joint_modality_name}.lc_rc_capacitated_intermodal_changes", overall_mode_transfers)
    for modality in ptns.keys():
        statistic.setValue(f"{modality}.lc_rc_capacitated_travel_time", mode_times[modality])
        statistic.setValue(f"{modality}.lc_rc_capacitated_changes", transfers[modality])

    model.dispose()



if __name__ == '__main__':

    logger.info("Start reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "mm_lc_rc_")

    infrastructure_od_filename = config.getStringValue("filename_infrastructure_od_file")
    joint_modality_name = config.getStringValue("modality_joint_name")
    period = config.getIntegerValue("period_length")
    extended_evaluation = config.getBooleanValue("mm_lc_rc_evaluation_extended")
    flow_vars_type = config.getStringValue("mm_lc_rc_cg_flow_vars_type")

    modalities = config.getStringListValue("modalities_all")
    modality_category: dict[str, str] = {}
    vehicle_capacity: dict[str, int] = {}
    station_times: dict[str, float] = {}
    platform_times: dict[str, float] = {}
    infra_capacity_weight = {}
    for modality in modalities:
        modality_category[modality] = config.getStringValue("modality_category", modality=modality)
        vehicle_capacity[modality] = config.getIntegerValue("gen_passengers_per_vehicle", modality=modality)
        station_times[modality] = config.getDoubleValue("mm_cg_intermodal_transfer_time", modality=modality)
        platform_times[modality] = config.getDoubleValue("mm_cg_intramodal_transfer_time", modality=modality)
        infra_capacity_weight[modality] = config.getDoubleValue("gen_infrastructure_capacity_weight", modality=modality)


    rpool_modalities: list[str] = []
    line_modalities: list[str] = []
    nons_modalities: list[str] = []
    for modality in modalities:
        if modality_category[modality].lower() == "line-based":
            line_modalities.append(modality)
        elif modality_category[modality].lower() == "ridepooling":
            rpool_modalities.append(modality)
        elif modality_category[modality].lower() =="nonscheduled":
            nons_modalities.append(modality)
        else:
            logger.warning(f"Modality {modality} does not have a modality category!")

    detour_factors: dict[str, float] = {}
    for modality in rpool_modalities:
        detour_factors[modality] = config.getDoubleValue("mm_cg_rp_detour_factor", modality=modality)

    logger.info("Finished reading configuration")
    logger.info(f"Found {len(line_modalities)} line-based modalities, {len(rpool_modalities)} ridepooling modalities and {len(nons_modalities)} nonscheduled modalities")

    logger.info("Begin reading input data")

    logger.debug("Reading infrastructure network")
    try:
        isn = InfrastructureNetworkReader.read(read_loads=True)
        check_isn_feasibility = True
    except InputFileException: 
        logger.info("ISN Load file could not be found. Will not check ISN feasibility.")
        isn = None
        check_isn_feasibility = False

    logger.debug("Reading infrastructure OD")
    od = ODReader.readInfrastructureOd(None, file_name=infrastructure_od_filename, config=config)

    logger.debug("Reading PTNs, LinePools, RidepoolingPools")

    ptns: dict[str, Graph[Stop, Link]] = {}
    lpools: dict[str, LinePool] = {}
    rpools: dict[str, RidepoolingPool] = {}
    for modality in line_modalities:
        ptns[modality] = PTNReader.read(modality=modality)
        lpools[modality] = LineReader.read(ptns[modality], read_frequencies=True, modality=modality)
        for line in lpools[modality].getLines():
            if line.getFrequency() == 0:
                lpools[modality].removeLine(line.getId())
    for modality in rpool_modalities:
        ptns[modality] = PTNReader.read(modality=modality)
        rpools[modality] = RidepoolingPoolReader.read(ptns[modality], read_number_vehicles=True, read_vehicle_frequencies=True, modality=modality)
        for area in rpools[modality].getAreas():
            if area.getNumberOfVehicles() == 0:
                rpools[modality].removeArea(area.getId())
    for modality in nons_modalities:
        ptns[modality] = PTNReader.read(modality=modality)

    logger.info("Finished reading input data")

    logger.info("Begin multimodal line and ridepooling concept evaluation")

    # initialize statistic
    statistic = Statistic()

    # basic evaluation
    basic_multimodal_concept_evaluation(ptns=ptns,
                                        lpools=lpools,
                                        rpools=rpools,
                                        isn_capacity_weights=infra_capacity_weight,
                                        vehicle_capacities=vehicle_capacity,
                                        isn=isn,
                                        statistic=statistic,
                                        joint_modality_name=joint_modality_name,
                                        check_isn_feasibility=check_isn_feasibility)

    uncapacitated_average_travel_time(config=config,
                                      ptns=ptns,
                                      lpools=lpools,
                                      rpools=rpools,
                                      od=od,
                                      joint_modality_name=joint_modality_name,
                                      statistic=statistic)

    logger.info("Finished multimodal line and ridepooling concept evaluation")


    if extended_evaluation:
        logger.info("Begin extended multimodal line and ridepooling concept evaluation")
        capacitated_evaluation(config=config,
                                ptns=ptns,
                                lpools=lpools,
                                rpools=rpools,
                                vehicle_capacities=vehicle_capacity,
                                period=period,
                                flow_vars_type=flow_vars_type,
                                parameters=parameters,
                                od=od,
                                joint_modality_name=joint_modality_name,
                                statistic=statistic)


        logger.info("Finished extended line and ridepooling concept evaluation")



    logger.info("Begin writing output data")

    # write statistic
    StatisticWriter.write(statistic=statistic)

    logger.info("Finished writing output data")
