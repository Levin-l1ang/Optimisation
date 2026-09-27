import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.util.statistic import Statistic
from core.io.statistic import StatisticWriter

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read()
    od = None
    try:
        od = ODReader.readInfrastructureOd(None)
    except InputFileException:
        logger.debug("No Infrastructure OD found")
    logger.info("Finished reading input data")


    logger.info("Begin evaluating infrastructure network")
    statistic = Statistic()
    number_nodes = len(isn.getNodes())
    statistic.setValue("isn_prop_nodes", number_nodes)
    number_links = len(isn.getEdges())
    statistic.setValue("isn_prop_edges", number_links)

    modalities = set()
    for e in isn.getEdges():
        for mode in e.getModalities():
            modalities.add(mode)
    modalities = list(modalities)
    modalities.sort()
    statistic.setValue("isn_prop_modalities", '[' + ', '.join(modalities) + ']')

    min_modalities = min([len(e.getModalities()) for e in isn.getEdges()])
    max_modalities = max([len(e.getModalities()) for e in isn.getEdges()])
    average_modalities = sum([len(e.getModalities()) for e in isn.getEdges()])/len(isn.getEdges())

    statistic.setValue("isn_min_modalities_per_link", min_modalities)
    statistic.setValue("isn_max_modalities_per_link", max_modalities)
    statistic.setValue("isn_average_modalities_per_link", average_modalities)

    if od:
        statistic.setValue("isn_prop_od_entries_greater_zero", len(od.getODPairs()))
        statistic.setValue("isn_prop_od_overall_sum", od.computeNumberOfPassengers())

    logger.info("Finished evaluating infrastructure network")


    logger.info("Begin writing output data")
    StatisticWriter.write(statistic)
    logger.info("Finished writing output data")
