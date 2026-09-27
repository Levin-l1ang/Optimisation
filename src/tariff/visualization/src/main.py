import logging
import sys

import od_heatmap
import pandas as pd
import numpy as np
import seaborn as sns
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.tariff import PriceMatrixReader
from core.model.impl.mapOD import MapOD
from core.exceptions.config_exceptions import ConfigInvalidValueException

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    old_price_matrix_filename = config.getStringValue("taf_evaluate_old_prices")
    new_price_matrix_filename = config.getStringValue("taf_evaluate_new_prices")
    heatmap_mode = config.getStringValue("taf_heatmap_mode")
    filename_heatmap = config.getStringValue("filename_tariff_heatmap")
    log_scale = config.getBooleanValue("taf_heatmap_log_scale")
    annot = config.getBooleanValue("taf_heatmap_use_annotations")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    od = ODReader.read(MapOD(), -1)
    old_price_matrix = PriceMatrixReader.read(ptn, old_price_matrix_filename)
    new_price_matrix = PriceMatrixReader.read(ptn, new_price_matrix_filename)
    logger.info("Finished reading input data")


    logger.info("Begin computing differences")

    stops = [stop.getId() for stop in ptn.getNodes()]
    n_stops = len(stops)
    len_matrix = len(stops)**2
    
    if heatmap_mode.lower() == "old" or heatmap_mode.lower() == "\"old\"":
        df = pd.DataFrame(columns=["left-stop-id", "right-stop-id", "prices"], index=range(len_matrix))
        df["left-stop-id"] = np.repeat(stops, n_stops)
        df["right-stop-id"] = stops * n_stops
        # the following double loop is used so zero entries don't get lost
        prices = []
        for origin in stops:
            for destination in stops:
                price = old_price_matrix.getValue(origin, destination)
                prices.append(price)
        df["prices"] = prices
    elif heatmap_mode.lower() == "new" or heatmap_mode.lower() == "\"new\"":
        df = pd.DataFrame(columns=["left-stop-id", "right-stop-id", "prices"], index=range(len_matrix))
        df["left-stop-id"] = np.repeat(stops, n_stops)
        df["right-stop-id"] = stops * n_stops
        # the following double loop is used so zero entries don't get lost
        prices = []
        for origin in stops:
            for destination in stops:
                price = new_price_matrix.getValue(origin, destination)
                prices.append(price)
        df["prices"] = prices
    elif heatmap_mode.lower() == "compare" or heatmap_mode.lower() == "\"compare\"":
        df = pd.DataFrame(columns=["left-stop-id", "right-stop-id", "price-difference"], index=range(len_matrix))
        df["left-stop-id"] = np.repeat(stops, n_stops)
        df["right-stop-id"] = stops * n_stops
        # the following double loop is used so zero entries don't get lost
        price_differences = []
        for origin in stops:
            for destination in stops:
                price_diff = new_price_matrix.getValue(origin, destination) - old_price_matrix.getValue(origin, destination)
                price_differences.append(price_diff)
        df["price-differences"] = price_differences
    else:
        raise ConfigInvalidValueException(["taf_heatmap_mode"])

    logger.info("Finished computing differences")

    logger.info("Begin plotting heatmap")
    if heatmap_mode.lower() == "old" or heatmap_mode.lower() == "\"old\"" or heatmap_mode.lower() == "new" or heatmap_mode.lower() == "\"new\"":
        od_heatmap.plot_df_as_heatmap(df, log_scale=log_scale, annot=annot, file_name=filename_heatmap, values="prices", cmap=sns.color_palette("vlag", as_cmap=True), center_at_zero=True)
    else:
        od_heatmap.plot_df_as_heatmap(df, log_scale=log_scale, annot=annot, file_name=filename_heatmap, values="price-differences", cmap=sns.color_palette("vlag", as_cmap=True), center_at_zero=True)
    logger.info("Finished plotting heatmap")
