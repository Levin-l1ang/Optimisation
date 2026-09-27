import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.exceptions import LinTimException
from core.io.config import ConfigReader
from core.io.od import ODReader, ODWriter
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.model.impl.mapOD import MapOD

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    default_value = config.getDoubleValue("od_default_value")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read()
    od = ODReader.readInfrastructureOd(MapOD(), -1)
    logger.info("Finished reading input data")


    logger.info("Begin completing OD matrix")
    if default_value < 0:
        LinTimException("default od value can not be negative!")
    for stop1 in isn.getNodes():
        for stop2 in isn.getNodes():
            try:
                value = od.od[stop1.getId()][stop2.getId()]
            except KeyError:
                od.setValue(stop1.getId(), stop2.getId(), default_value)
    logger.info("Finished completing OD matrix")


    logger.info("Begin writing output data")
    ODWriter.writeInfrastructureOd(isn, od)
    logger.info("Finished writing output data")
