import logging
import sys
import numpy as np


from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.statistic import StatisticWriter
from core.io.ridepooling import RidepoolingPoolReader
from core.util.statistic import Statistic

logger = logging.getLogger(__name__)


if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    capacity_ridepooling = config.getIntegerValue("gen_passengers_per_vehicle")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    rconcept = RidepoolingPoolReader.read(ptn, read_number_vehicles=True, read_vehicle_frequencies=True)

    logger.info("Finished reading input data")


    logger.info("Begin evaluating rideconcept")
    statistic = Statistic()

    areas = rconcept.getAreas()
    min_edges = min([len(area.getEdges()) for area in areas])
    max_edges = max([len(area.getEdges()) for area in areas])
    average_edges = np.average([[len(area.getEdges()) for area in areas]])
    var_edges = np.var([[len(area.getEdges()) for area in areas]])

    statistic.setValue("rc_edges_min", min_edges)
    statistic.setValue("rc_edges_max", max_edges)
    statistic.setValue("rc_edges_average", average_edges)
    statistic.setValue("rc_edges_var", var_edges)

    min_vehicles = min([area.getNumberOfVehicles() for area in areas])
    max_vehicles = max([area.getNumberOfVehicles() for area in areas])
    average_vehicles = np.average([[area.getNumberOfVehicles() for area in areas]])
    var_vehicles = np.var([[area.getNumberOfVehicles() for area in areas]])

    statistic.setValue("rc_vehicles_min", min_vehicles)
    statistic.setValue("rc_vehicles_max", max_vehicles)
    statistic.setValue("rc_vehicles_average", average_vehicles)
    statistic.setValue("rc_vehicles_var", var_vehicles)

    overall_costs = 0
    for area in rconcept.getAreas():
        overall_costs += area.getNumberOfVehicles() * rconcept.getCost()

    statistic.setValue("rc_obj_cost", overall_costs)

    min_veh_freq = min([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
    max_veh_freq = max([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
    average_veh_freq = np.average([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
    var_veh_freq = np.var([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])

    statistic.setValue("rc_vehicle_frequencies_min", min_veh_freq)
    statistic.setValue("rc_vehicle_frequencies_max", max_veh_freq)
    statistic.setValue("rc_vehicle_frequencies_average", average_veh_freq)
    statistic.setValue("rc_vehicle_frequencies_var", var_veh_freq)

    logger.info("Finished evaluating rideconcept")


    logger.info("Begin writing output data")
    StatisticWriter.write(statistic)
    logger.info("Finished writing output data")



