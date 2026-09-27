#! python

import matplotlib as mpl
mpl.use('Agg')
import matplotlib.colors as mcolors
from matplotlib.pyplot import figure
import matplotlib.patches as mpatches
from matplotlib import transforms

import sys
import matplotlib.pyplot as plt
import os.path
import networkx as nx
import logging

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader, Config
from core.io.csv import CsvReader
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.ptn import Stop, Link


logger = logging.getLogger(__name__)

def plot(config: Config):
    """
    Reads modality-specific PTNs and draws a multimodal PTN.
    :param config: The configuration used
    :type config: Config
    """
    # read input
    ptns: dict[str: Graph[Stop, Link]] = {}
    if "modalities_all" in config.data:
        all_modalities = config.getStringListValue("modalities_all")
        for modality in all_modalities:
            ptns[modality] = PTNReader.read(modality=modality)
    else:
        logger.error("No modalities found in config")

    G = nx.MultiGraph()
    legend_keys = []

    colors = mpl.color_sequences['tab10']
    mod_colors = {}
    i = 0

    mod_color_file = config.getStringValue("filename_modality_colors")
    if os.path.isfile(mod_color_file):
        def process_line_colors(args, index):
            if len(args) != 2:
                raise InputFormatException(mod_color_file, len(args), 2)
            color_read = str(args[1])
            if "(" in color_read:
                color_read = color_read.replace("(", "")
                color_read = color_read.replace(")", "")
                colors_array_read = color_read.split(",")
                color_read = [float(color_value) for color_value in colors_array_read]
            mod_colors[str(args[0])] = color_read
        CsvReader.readCsv(mod_color_file, process_line_colors)

    node_labels = {}
    added_nodes = []
    for modality, ptn in ptns.items():
        new_stops = ptn.getNodes()
        for node in new_stops:
            if not (node.getId() in added_nodes):
             G.add_node(f"{node.getId()}", name=node.getShortName(), pos=(node.getXCoordinate(), node.getYCoordinate()))
             added_nodes.append(node.getId())
             node_labels[str(node.getId())] = node.getId()
        new_edges = ptn.getEdges()
        if modality not in mod_colors:
            mod_colors[modality] = colors[i]
            i += 1

        legend_keys.append(mpatches.Patch(color = mod_colors[modality], label = modality.capitalize()))
        edges_added = []
        for edge in new_edges:
            if ((edge.getRightNode().getId(), edge.getLeftNode().getId()) not in edges_added) and ((edge.getLeftNode().getId(), edge.getRightNode().getId()) not in edges_added):
                edges_added.append((edge.getLeftNode().getId(), edge.getRightNode().getId()))
                G.add_edge(f"{edge.getLeftNode().getId()}",f"{edge.getRightNode().getId()}", modality = modality, color = mod_colors[modality])

    # get node positions
    pos = nx.get_node_attributes(G,'pos')
    col = nx.get_edge_attributes(G, 'color').values()

    #figure out scale of figure
    xs = [coor[0] for coor in pos.values()]
    ys = [coor[1] for coor in pos.values()]

    x_range = max(xs) - min(xs)
    y_range = max(ys) - min(ys)
    xy = x_range / y_range

    background = config.getBooleanValue("lpool_opt_visualisation_background")
    scale = config.getDoubleValue("ptn_draw_conversion_factor")
    scale = scale * min(50, G.number_of_nodes()/5)

    fig_width = scale * xy
    fig_height = scale
    margin = config.getDoubleValue("lpool_opt_visualisation_plot_margins")

    fig = figure(figsize=(fig_width, fig_height), dpi=160, frameon=False)
    ax = plt.axes(frame_on = background)
    ax.margins(margin, margin)
    seen = {}
    line_width = config.getDoubleValue("lpool_opt_visualisation_line_width")
    line_distance = config.getDoubleValue("lpool_opt_visualisation_line_distance")
    pixel_offset =  (line_distance + line_width) /72.

    tot_width = {}
    width_so_far = {}

    for (u,v,k) in G.edges(keys=True):
        so_far = tot_width.get((u,v), 0)
        tot_width[(u,v)] = so_far + line_width

    # draw the lines:
    for (u,v,k) in G.edges(keys=True):
        n_parallel = G.number_of_edges(u,v)
        total = (tot_width[(u,v)] + (n_parallel - 1) * line_distance)
        linewidth = line_width
        start = - 0.5* (total) /72. - 0.5*line_distance/72.

        index = seen.get((u,v),0)
        prev = width_so_far.get((u,v), start)

        pixel_offset =  (linewidth) /72. # how far from each other are the lines
        pixel_offset +=  line_distance/72.
        dx = pos[v][1] - pos[u][1]
        dy = pos[v][0] - pos[u][0]
        norm = (dx**2 + dy**2)**0.5

        if norm == 0:
            norm = 1

        offset_x = 0
        offset_y = 0

        if n_parallel > 1:
            offset_x = dx / norm  * (  prev+ 0.5*pixel_offset)
            offset_y = - dy / norm  * (  prev + 0.5*pixel_offset)

        width_so_far[(u,v)] = prev + pixel_offset

        offset_trans = transforms.ScaledTranslation(offset_x, offset_y, fig.dpi_scale_trans)
        new_line_trans = ax.transData + offset_trans

        edge_x = (pos[u][0], pos[v][0])
        edge_y = (pos[u][1], pos[v][1])

        line, = ax.plot(edge_x, edge_y, color=G[u][v][k]['color'], linewidth = linewidth, transform = new_line_trans)

        seen[(u,v)] = index + 1

    node_size = config.getIntegerValue("lpool_opt_visualisation_def_node_size")
    nx.draw_networkx_nodes(G, pos, ax=ax, node_color='white', edgecolors="black", node_size = node_size, node_shape='s')
    # draw node_labels separately, so arbitrary node_labels could be drawn
    node_label_size = config.getIntegerValue("lpool_opt_visualisation_node_label_size")
    nx.draw_networkx_labels(G,pos, node_labels, font_size = node_label_size)

    # draw legend:
    if config.getBooleanValue("lpool_opt_visualisation_draw_legend"):
        legend_loc = config.getStringValue("lpool_opt_visualisation_legend_location")
        legend_scale = config.getDoubleValue("lpool_opt_visualisation_legend_scaling")

        ax.legend(handles=legend_keys, loc = legend_loc, prop={'size': max(7, legend_scale)})

    # save picture. note that any dot file type is replaced by png
    plt.savefig(config.getStringValue('filename_multimodal_ptn_graph_file').replace("dot","png"), format = "PNG")

    logger.info(f"Success! You can find the graphics file under {config.getStringValue('filename_multimodal_ptn_graph_file').replace('dot','png')}.")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    plot(config)



