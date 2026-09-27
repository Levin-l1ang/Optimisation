import logging
import sys

from evaluation_functions import compute_revenue
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.statistic import StatisticWriter
from core.io.tariff import PriceMatrixReader
from core.model.impl.mapOD import MapOD
from core.util.statistic import Statistic

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    old_price_matrix_filename = config.getStringValue("taf_evaluate_old_prices")
    new_price_matrix_filename = config.getStringValue("taf_evaluate_new_prices")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    od = ODReader.read(MapOD(), -1)

    old_price_matrix = PriceMatrixReader.read(ptn, old_price_matrix_filename)
    new_price_matrix = PriceMatrixReader.read(ptn, new_price_matrix_filename)

    logger.info("Finished reading input data")

    logger.info("Begin evaluating tariff zones")
    statistic = Statistic()

    statistic_dict = compute_revenue(old_price_matrix, new_price_matrix, ptn, od)
    for (k,v) in statistic_dict.items():
        statistic.setValue(k,v)
    logger.info("Finished evaluating tariff zones")

    logger.info("Begin writing output data")
    StatisticWriter.write(statistic)
    logger.info("Finished writing output data")
