import logging
import sys
import numpy as np


from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.statistic import StatisticWriter
from core.io.ridepooling import RidepoolingPoolReader
from core.io.lines import LineReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.model.lines import LinePool
from core.model.ridepooling import RidepoolingPool
from core.model.graph import Graph
from core.model.ptn import Stop, Link
from core.util.statistic import Statistic

logger = logging.getLogger(__name__)


if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    joint_prefix = config.getStringValue("modality_joint_name")
    modalities = config.getStringListValue("modalities_all")
    modality_category = {}
    vehicle_capacity = {}
    infra_capacity_weight = {}
    for modality in modalities:
        modality_category[modality] = config.getStringValue("modality_category", modality=modality)
        vehicle_capacity[modality] = config.getIntegerValue("gen_passengers_per_vehicle", modality=modality)
        infra_capacity_weight[modality] = config.getDoubleValue("gen_infrastructure_capacity_weight", modality=modality)

    rpool_modalities = []
    line_modalities = []
    for modality in modalities:
        if modality_category[modality].lower() == "line-based":
            line_modalities.append(modality)
        elif modality_category[modality].lower() == "ridepooling":
            rpool_modalities.append(modality)

    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read()

    ptn: dict[str, Graph[Stop, Link]] = {}
    rconcept: dict[str, RidepoolingPool] = {}
    lconcept: dict[str, LinePool] = {}
    for modality in rpool_modalities:
        ptn[modality] = PTNReader.read(read_ptn_infrastructure_map=True, infrastructure_network=isn, modality=modality)
        rconcept[modality] = RidepoolingPoolReader.read(ptn[modality], read_number_vehicles=True, read_vehicle_frequencies=True, modality=modality)
    for modality in line_modalities:
        ptn[modality] = PTNReader.read(read_ptn_infrastructure_map=True, infrastructure_network=isn, modality=modality)
        lconcept[modality] = LineReader.read(ptn[modality], read_frequencies=True, modality=modality)

    logger.info("Finished reading input data")


    logger.info("Begin evaluating rideconcepts and line concepts")
    statistic = Statistic()

    for modality in rpool_modalities:
        areas = rconcept[modality].getAreas()
        min_edges = min([len(area.getEdges()) for area in areas])
        max_edges = max([len(area.getEdges()) for area in areas])
        average_edges = np.average([[len(area.getEdges()) for area in areas]])
        var_edges = np.var([[len(area.getEdges()) for area in areas]])

        statistic.setValue(f"{modality}.rc_edges_min", min_edges)
        statistic.setValue(f"{modality}.rc_edges_max", max_edges)
        statistic.setValue(f"{modality}.rc_edges_average", average_edges)
        statistic.setValue(f"{modality}.rc_edges_var", var_edges)

        min_vehicles = min([area.getNumberOfVehicles() for area in areas])
        max_vehicles = max([area.getNumberOfVehicles() for area in areas])
        average_vehicles = np.average([[area.getNumberOfVehicles() for area in areas]])
        var_vehicles = np.var([[area.getNumberOfVehicles() for area in areas]])

        statistic.setValue(f"{modality}.rc_vehicles_min", min_vehicles)
        statistic.setValue(f"{modality}.rc_vehicles_max", max_vehicles)
        statistic.setValue(f"{modality}.rc_vehicles_average", average_vehicles)
        statistic.setValue(f"{modality}.rc_vehicles_var", var_vehicles)

        overall_costs = 0
        for area in areas:
            overall_costs += area.getNumberOfVehicles() * rconcept[modality].getCost()

        statistic.setValue(f"{modality}.rc_cost", overall_costs)

        min_veh_freq = min([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
        max_veh_freq = max([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
        average_veh_freq = np.average([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
        var_veh_freq = np.var([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])

        statistic.setValue(f"{modality}.rc_vehicle_frequencies_min", min_veh_freq)
        statistic.setValue(f"{modality}.rc_vehicle_frequencies_max", max_veh_freq)
        statistic.setValue(f"{modality}.rc_vehicle_frequencies_average", average_veh_freq)
        statistic.setValue(f"{modality}.rc_vehicle_frequencies_var", var_veh_freq)

    overall_costs = 0
    for modality in rpool_modalities:
        for area in rconcept[modality].getAreas():
            overall_costs += area.getNumberOfVehicles() * rconcept[modality].getCost()
    for modality in line_modalities:
        for line in lconcept[modality].getLineConcept():
            overall_costs += line.getFrequency() * line.getCost()

    statistic.setValue(f"{joint_prefix}.lc_rc_cost", overall_costs)


    rp_cap = {}
    line_cap = {}
    for link in isn.getEdges():
        rp_cap[link] = 0
        line_cap[link] = 0

    for modality in line_modalities:
        for line in lconcept[modality].getLineConcept():
            for edge in line.getLinePath().getEdges():
                for link in edge.getUnderlyingInfrastructure().getEdges():
                    line_cap[link] += line.getFrequency() * vehicle_capacity[modality]
    for modality in rpool_modalities:
        for area in rconcept[modality].getAreas():
            for edge in area.getEdges():
                for link in edge.getUnderlyingInfrastructure().getEdges():
                    rp_cap[link] += area.getVehicleFrequency(edge.getId()) * area.getNumberOfVehicles() * vehicle_capacity[modality]

    percentage_ridepooling = {}
    for link in isn.getEdges():
        if rp_cap[link] + line_cap[link] == 0:
            percentage_ridepooling[link] = 0
        else:
            percentage_ridepooling[link] = rp_cap[link] / (rp_cap[link] + line_cap[link])

    overall_rp_cap = 0
    overall_line_cap = 0
    for edge in isn.getEdges():
        overall_rp_cap += rp_cap[edge]
        overall_line_cap += line_cap[edge]
    overall_percentage_ridepooling = overall_rp_cap / (overall_rp_cap + overall_line_cap)

    statistic.setValue(f"{joint_prefix}.rc_min_rp_percentage", min(percentage_ridepooling.values()))
    statistic.setValue(f"{joint_prefix}.rc_max_rp_percentage", max(percentage_ridepooling.values()))
    statistic.setValue(f"{joint_prefix}.rc_average_rp_percentage", np.average(list(percentage_ridepooling.values())))
    statistic.setValue(f"{joint_prefix}.rc_var_rp_percentage", np.var(list(percentage_ridepooling.values())))
    statistic.setValue(f"{joint_prefix}.rc_ridepooling_percentage", overall_percentage_ridepooling)

    logger.info("Finished evaluating rideconcepts and line concepts")


    logger.info("Begin writing output data")
    StatisticWriter.write(statistic)
    logger.info("Finished writing output data")



