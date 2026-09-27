import logging
import sys
import numpy as np

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputUnsupportedModalityCategoryException
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
    modality_category = config.getStringValue("modality_category")
    if modality_category.lower() != "ridepooling":
        logger.error("Can only evaluate ridepooling pool, if modality is of modality category ridepooling!")
        raise InputUnsupportedModalityCategoryException(modality_category)

    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    rpool = RidepoolingPoolReader.read(ptn, read_number_vehicles=False, read_vehicle_frequencies=True)


    logger.info("Finished reading input data")

    logger.info("Begin evaluating ridepool")
    statistic = Statistic()

    areas = rpool.getAreas()
    min_edges = min([len(area.getEdges()) for area in areas])
    max_edges = max([len(area.getEdges()) for area in areas])
    average_edges = np.average([[len(area.getEdges()) for area in areas]])
    var_edges = np.var([[len(area.getEdges()) for area in areas]])

    statistic.setValue("rpool_edges_min", min_edges)
    statistic.setValue("rpool_edges_max", max_edges)
    statistic.setValue("rpool_edges_average", average_edges)
    statistic.setValue("rpool_edges_var", var_edges)

    min_veh_freq = min([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
    max_veh_freq = max([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
    average_veh_freq = np.average([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])
    var_veh_freq = np.var([area.getVehicleFrequency(edge.getId()) for area in areas for edge in area.getEdges()])

    statistic.setValue("rpool_vehicle_frequencies_min", min_veh_freq)
    statistic.setValue("rpool_vehicle_frequencies_max", max_veh_freq)
    statistic.setValue("rpool_vehicle_frequencies_average", average_veh_freq)
    statistic.setValue("rpool_vehicle_frequencies_var", var_veh_freq)

    logger.info("Finished evaluating ridepool")

    logger.info("Begin writing output data")
    StatisticWriter.write(statistic)
    logger.info("Finished writing output data")
