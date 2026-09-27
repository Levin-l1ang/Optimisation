import logging
import os
import os.path
from pathlib import Path

import matplotlib.pyplot as plt
import seaborn as sns
from core.exceptions.input_exceptions import InputFileException
from matplotlib.colors import LogNorm, Normalize


def draw_od_heatmap(config, od_df) -> None:
    """
    reads in relevant config parameters and calls plotting method
    :param config: configuration
    :param od_df: dataframe representing the OD data with columns "left-stop-id", "right-stop" and "customers"
    :return:
    """
    logging.info("Begin reading heatmap-specific config parameters")
    od_file_name = config.getStringValueStatic("default_od_file")
    if not os.path.isfile(od_file_name):
        raise InputFileException(od_file_name)
    file_name_heatmap = config.getStringValueStatic("filename_od_visualization_file")
    log_scale = config.getBooleanValueStatic("od_visualization_use_log_scale")
    annot = config.getBooleanValueStatic("od_visualization_use_annotations")
    logging.info("Finished reading heatmap-specific config parameters")

    # form dataframe into matrix form
    logging.info("Begin plotting heatmap")
    plot_df_as_heatmap(od_df, log_scale=log_scale, annot=annot, file_name=file_name_heatmap)
    logging.info("Finished plotting heatmap")
    return


def plot_df_as_heatmap(od_df, log_scale=False, annot=False, file_name=None, values="customers", cmap=plt.cm.Blues, center_at_zero=False) -> None:
    """
    produces a heatmap for the od data (given in a dataframe)
    :param od_df: pandas dataframe in matrix form s.t. the entry df_heatmap.values[i,j]
           represents the number of passengers that with to travel from stop i to stop j
    :param log_scale: if True, then the color bar uses log scale values
    :param annot: if True, then the OD-values are written in the heatmap rectangles
    :param file_name: file to save the heatmap in
    :return:
    """
    #  make a dataframe in matrix form s.t. the entry df_heatmap.values[i,j] represents
    #  the number of passengers that with to travel from stop i to stop j
    df_heatmap = od_df.pivot(index="right-stop-id", columns="left-stop-id", values=values)
    df2 = df_heatmap[::-1]  # makes it so that in the y-direction the stop id increases (as opposed to decreases)

    # set up the heatmap
    vmax = max(od_df[values])
    vmin = min(od_df[values])
    if log_scale:
        norm = LogNorm(vmin=vmin, vmax=vmax)
    else:
        norm = Normalize(vmin=vmin, vmax=vmax)
    plt.xlabel("left-stop-id")
    plt.ylabel("right-stop-id")

    # create the heatmap
    if center_at_zero:
        sns.heatmap(df2, cmap=cmap, norm=norm, center=0, annot=annot, fmt='g', cbar_kws={'label': values})
    else:
        sns.heatmap(df2, cmap=cmap, norm=norm, annot=annot, fmt='g', cbar_kws={'label': values})

    if file_name is not None:
        logging.info("Begin saving heatmap")
        Path(file_name).parent.mkdir(parents=True, exist_ok=True)
        plt.savefig(file_name)
        logging.info("Finished saving heatmap")
    return
