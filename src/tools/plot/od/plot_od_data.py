import logging
import os
import os.path
import sys
from typing import List

import numpy as np
import pandas as pd
from core.exceptions.input_exceptions import InputFileException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.model.od import OD
from core.util.config import Config

from od_graph import graph_main
from od_heatmap import draw_od_heatmap


def transform_od_to_df(od: OD, stops: List[int]) -> pd.DataFrame:
    """
    transforms the LinTim OD object into a dataframe
    This preprocessing is used to easily draw the heatmap with sns.heatmap() later on
    :param od: OD object
    :param stops: list of stop ids (used for setting the index)
    :return: pandas dataframe with columns "left-stop-id", "right-stop-id" and "customers"
    """
    len_od = len(stops)**2
    df = pd.DataFrame(columns=["left-stop-id", "right-stop-id", "customers"], index=range(len_od))
    n_stops = len(stops)
    df["left-stop-id"] = np.repeat(stops, n_stops)
    df["right-stop-id"] = stops * n_stops
    # the following double loop is used so zero entries don't get lost
    customers = []
    for origin in stops:
        for destination in stops:
            c_od = od.getValue(origin, destination)
            customers.append(c_od)
    df["customers"] = customers
    return df


def main():
    logging.getLogger('matplotlib').setLevel(logging.WARNING)
    pd.options.mode.chained_assignment = None  # default='warn'

    logging.info("Begin reading configuration")
    ConfigReader.read(sys.argv[1])
    config = Config.getDefaultConfig()
    logging.info("Finished reading configuration")

    logging.info("Begin reading config parameters")
    od_file_name = config.getStringValueStatic("default_od_file")
    if not os.path.isfile(od_file_name):
        raise InputFileException(od_file_name)
    od_draw_use_heatmap = config.getBooleanValueStatic("od_visualization_use_heatmap")
    od_draw_conversion_factor = config.getDoubleValueStatic("od_draw_conversion_factor")
    logging.info("Finished reading config parameters")

    logging.info("Begin reading stops")
    ptn = PTNReader.read(read_stops=True, read_links=False)
    stops = [stop.getId() for stop in ptn.getNodes()]
    logging.info("Finished reading stops")
    logging.info("Begin reading OD data")
    od = ODReader.read(od=None)
    logging.info("Finished reading OD data")

    if od_draw_use_heatmap:
        logging.debug("Begin transforming OD data to dataframe")
        od_df = transform_od_to_df(od, stops)
        logging.debug("Finished transforming OD data to dataframe")
        draw_od_heatmap(config=config, od_df=od_df)
    else:
        graph_main(config=config, od=od, ptn=ptn, od_draw_conversion_factor=od_draw_conversion_factor)
    return


if __name__ == '__main__':
    main()
