import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.io.config import ConfigReader
from core.solver.solver_parameters import SolverParameters
from core.io.od import ODReader
from core.io.ptn import PTNReader, PTNWriter
from core.io.lines import LineReader, LineWriter
from core.io.statistic import StatisticWriter
from core.io.ridepooling import RidepoolingPoolReader, RidepoolingPoolWriter
from core.io.infrastructure_network import InfrastructureNetworkReader, InfrastructureNetworkWriter
from core.model.ridepooling import RidepoolingPool
from core.model.lines import LinePool
from core.model.ptn import Link, Stop
from core.model.graph import Graph
from core.model.mm_change_and_go_network import MMCG

from mm_travel_time_mip import mm_travel_time_mip

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException

    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "mm_lc_rc_")

    infrastructure_od_filename = config.getStringValue("filename_infrastructure_od_file")
    budget = config.getDoubleValue("mm_lc_rc_cg_budget")
    period_length = config.getIntegerValue("period_length")
    flow_vars_type = config.getStringValue("mm_lc_rc_cg_flow_vars_type")
    write_statistic = config.getBooleanValue("mm_lc_rc_cg_write_statistic")
    statistic_file = config.getStringValue("filename_mm_lc_rc_cg_statistic")
    use_start_solution = config.getBooleanValue("mm_lc_rc_cg_use_start_solution")
    minimize_cost = config.getBooleanValue("mm_lc_rc_cg_minimize_cost")
    time_budget = config.getDoubleValue("mm_lc_rc_cg_time_budget")
    model_drive = config.getStringValue("mm_cg_model_drive")
    write_loads = config.getBooleanValue("mm_lc_rc_cg_write_loads")
    respect_infrastructure_capacity = config.getBooleanValue("mm_lc_rc_cg_respect_infrastructure_capacity")

    modalities = config.getStringListValue("modalities_all")
    fixed_concepts = config.getStringListValue("fixed_concept_modalities")
    modality_category: dict[str, str] = {}
    vehicle_capacity: dict[str, int] = {}
    station_times: dict[str, float] = {}
    platform_times: dict[str, float] = {}
    infra_capacity_weight: dict[str, float] = {}
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

    logger.debug("Reading infrastructure OD")
    od = ODReader.readInfrastructureOd(None, file_name=infrastructure_od_filename, config=config)

    if respect_infrastructure_capacity:
        logger.debug("Reading infrastructure network")
        isn = InfrastructureNetworkReader.read()
    else:
        isn = None

    logger.debug("Reading PTNs, LinePools, RidepoolingPools")

    ptns: dict[str, Graph[Stop, Link]] = {}
    lpools: dict[str, LinePool] = {}
    rpools: dict[str, RidepoolingPool] = {}
    for modality in line_modalities:
        try:
            ptns[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=respect_infrastructure_capacity, infrastructure_network=isn, read_loads=write_loads)
        except:
            # if loads should be written, we try to read the loads file to write back the original lower and upper frequencues. If loads are not present, lower/upper frequencies will be zero.
            ptns[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=respect_infrastructure_capacity, infrastructure_network=isn, read_loads=False)
        if use_start_solution or modality in fixed_concepts:
            try:
                lpools[modality] = LineReader.read(ptns[modality], read_frequencies=True, modality=modality)
            except:
                logger.info(f"Tried reading Line Concept for modality {modality}, did not find it, will ignore")
                lpools[modality] = LineReader.read(ptns[modality], read_frequencies=False, modality=modality)
        else:
            lpools[modality] = LineReader.read(ptns[modality], read_frequencies=False, modality=modality)
    for modality in rpool_modalities:
        try:
            ptns[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=respect_infrastructure_capacity, infrastructure_network=isn, read_loads=write_loads)
        except:
            # if loads should be written, we try to read the loads file to write back the original lower and upper frequencues. If loads are not present, lower/upper frequencies will be zero.
            ptns[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=respect_infrastructure_capacity, infrastructure_network=isn, read_loads=False)

        if use_start_solution or modality in fixed_concepts:
            try:
                rpools[modality] = RidepoolingPoolReader.read(ptns[modality], read_number_vehicles=True, modality=modality)
            except:
                logger.info(f"Tried reading Ridepooling Concept for modality {modality}, did not find it, will ignore")
                rpools[modality] = RidepoolingPoolReader.read(ptns[modality], read_number_vehicles=False, modality=modality)
        else:
            rpools[modality] = RidepoolingPoolReader.read(ptns[modality], read_number_vehicles=False, modality=modality)
    for modality in nons_modalities:
        try:
            ptns[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=respect_infrastructure_capacity, infrastructure_network=isn, read_loads=write_loads)
        except:
            # if loads should be written, we try to read the loads file to write back the original lower and upper frequencues. If loads are not present, lower/upper frequencies will be zero.
            ptns[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=respect_infrastructure_capacity, infrastructure_network=isn, read_loads=False)

    logger.info("Finished reading input data")


    logger.info("Begin computing line and ridepooling concepts")

    logger.debug("Begin building multimodal Change&Go network")

    mcgn = MMCG(modalities=modalities,
                modality_categories=modality_category,
                ptns=ptns,
                line_pools=lpools,
                ridepooling_pools=rpools,
                station_times=station_times,
                platform_times=platform_times,
                detour_factors=detour_factors,
                model_drive=model_drive)

    logger.debug("Finished building multimodal Change&Go network")

    logger.debug("Begin building mm-travel-time-mip")

    model = mm_travel_time_mip(cgn=mcgn,
                               infra_od=od,
                               budget=budget,
                               period_length=period_length,
                               vehicle_capacities=vehicle_capacity,
                               flow_vars_type=flow_vars_type,
                               parameters=parameters,
                               use_start_solution=use_start_solution,
                               minimize_cost=minimize_cost,
                               time_budget=time_budget,
                               fixed_concepts=fixed_concepts,
                               respect_infrastructure_capacity=respect_infrastructure_capacity,
                               isn=isn,
                               infra_capacity_weight=infra_capacity_weight)

    logger.debug("Finished building mm-travel-time-mip")

    logger.debug("Start optimization")
    if parameters.writeLpFile():
        model.write("line-planning/mm-traveltime-cg.lp")
    model.solve()
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getNumberOfSolutions()> 0:
        if model.statusIsOptimal():
            logger.debug("Optimal solution found")
            logger.info(f"Optimal MIP objective: {model.getObjectiveValue()}")
        else:
            logger.debug("Feasible solution found")

        # get line modalities solutions
        for modality in model.cgn.get_line_modalities():
            for line in model.cgn.get_line_pools()[modality].getLines():
                f = int(round(model.getValue(model.frequencies[modality][line])))
                line.setFrequency(f)

        # get ridepooling modalities solutions
        for modality in model.cgn.get_ridepooling_modalities():
            for area in model.cgn.get_ridepooling_pools()[modality].getAreas():
                v = int(round(model.getValue(model.vehicles[modality][area])))
                area.setNumberOfVehicles(v)
                for edge in area.getEdges():
                    dist = float(model.get_vehicle_distribution(modality, area, edge))
                    area.setDistribution(edge_id=edge.getId(), distribution=dist)

        if write_statistic:
            time_statistic = model.get_travel_time_statistics()
            StatisticWriter.write(statistic=time_statistic, file_name=statistic_file, append=False)
            cost_statistic = model.get_cost_statistic()
            cost_statistic.setValue("_solver_runtime", model.solution_time)
            cost_statistic.setValue("_solver_gap", model.solution_gap)
            StatisticWriter.write(statistic=cost_statistic, file_name=statistic_file, append=True)

        if write_loads:
            model.compute_ptn_loads()

        model.dispose()
    else:
        logger.debug("No feasible solution found")
        if model.statusIsInfeasible():
            model.computeIIS("line-planning/mm-traveltime-cg.ilp")
        model.dispose()
        raise AlgorithmStoppingCriterionException(
            "multimodal traveling time model for line and ridepooling planning")

    logger.info("Finished computing line concepts and ridepooling concepts")

    logger.info("Begin writing output data")
    for modality in rpool_modalities:
        RidepoolingPoolWriter.write(rpools[modality], write_pool=False, write_ride_concept=True, write_distribution=True, modality=modality)
    for modality in line_modalities:
        LineWriter.write(lpools[modality], write_pool=False, write_costs=False, write_line_concept=True, modality=modality)

    if write_loads:
        if respect_infrastructure_capacity:
            InfrastructureNetworkWriter.write(isn, write_load=True, write_infrastructure_nodes=False, write_infrastructure_links=False)
        for modality in modalities:
            PTNWriter.write(ptns[modality], write_loads=True, modality=modality, write_stops= False, write_links=False)
    logger.info("Finished writing output data")

