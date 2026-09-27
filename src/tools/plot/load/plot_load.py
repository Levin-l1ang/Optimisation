import logging
import os
import os.path
import os.path
import sys

import matplotlib
import matplotlib.pyplot as plt
import networkx as nx
import numpy as np
from core.exceptions.input_exceptions import InputFileException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.ptn import Stop, Link
from core.util.config import Config
from matplotlib.lines import Line2D


def load_data_to_netx_graph(ptn: Graph[Stop, Link], loads_graph_lower_bound: float, loads_graph_upper_bound: float,
                            loads_graph_use_log_scale: bool, loads_graph_use_edge_color: bool,
                            min_edge_width: int, max_edge_width: int) -> nx.classes.graph.Graph:

    # determine the interval, where edges with load within that interval get displayed
    loads_list = [edge.getLoad() for edge in ptn.getEdges()]
    max_load = max(loads_list)
    min_load = min(loads_list)
    low_max = loads_graph_lower_bound * max_load
    up_max = loads_graph_upper_bound * max_load

    # Initiate an empty (directed or undirected) graph
    if ptn.isDirected():
        ptn_nx_graph = nx.DiGraph()
    else:
        ptn_nx_graph = nx.Graph()

    # add all notes (this is required beforehand because nodes might get lost if only those nodes
    # get added whose adjacent edges have the appropriate load, which is later the case)
    for node in ptn.getNodes():
        node_id = node.getId()
        ptn_nx_graph.add_node(node_id)
    # adjust max load for scaling when log of load is displayed
    if loads_graph_use_log_scale:
        max_load = np.log(max_load)
        min_load = np.log(min_load)
        # set global graph attribute so loads_graph_use_log_scale does not need
        # to be given as a function parameter
        ptn_nx_graph.graph["log_scale"] = True
    else:
        ptn_nx_graph.graph["log_scale"] = False

    remove_zero_edge_log = []  # collect edges with zero load which should be removed under the logarithmic scale
    for edge in ptn.getEdges():
        left_node = edge.getLeftNode()
        right_node = edge.getRightNode()
        u = left_node.getId()
        v = right_node.getId()
        load = edge.getLoad()
        if low_max <= load <= up_max:
            ptn_nx_graph.add_edge(u, v)
            # set node properties
            ptn_nx_graph.nodes[u]["stop_id"] = u
            ptn_nx_graph.nodes[v]["stop_id"] = v
            # set position
            ptn_nx_graph.nodes[u]["position"] = (left_node.getXCoordinate(), left_node.getYCoordinate())
            ptn_nx_graph.nodes[v]["position"] = (right_node.getXCoordinate(), right_node.getYCoordinate())
            # set load
            ptn_nx_graph[u][v]["load"] = load
            processed_load = load
            if loads_graph_use_log_scale:
                if load == 0:
                    remove_zero_edge_log.append((u, v))
                    continue
                processed_load = np.log(load)
            if loads_graph_use_edge_color:
                ptn_nx_graph[u][v]["edge color"] = processed_load
                # remove zero load edges so graph can still be drawn
            else:  # edge width is used as a medium to display the traffic loads
                # scale these loads in order to fit them within the given interval for the edge width
                ptn_nx_graph[u][v]["edge width"] = scale_singular_load(load=processed_load,
                                                                       min_edge_width=min_edge_width,
                                                                       max_edge_width=max_edge_width,
                                                                       max_load=max_load,
                                                                       min_load=min_load)
    # remove zero load edges from graph during logarithmic visualization
    if loads_graph_use_log_scale:
        if remove_zero_edge_log:
            logging.warning("Some edges have load 0. "
                            "These will be removed to permit drawing the graph with a logarithmic scale.")
            logging.debug("Removed edges with zero load (due to the logarithmic scale): "
                          "{}".format(remove_zero_edge_log))
            # remove the edges with load 0
            for u, v in remove_zero_edge_log:
                ptn_nx_graph.remove_edge(u, v)
    return ptn_nx_graph


def scale_singular_load(load: float, min_edge_width: int, max_edge_width: int, max_load: int, min_load: int = 0):
    """
    scales the traffic loads of the edges to be somewhere between min_edge_width and max_edge_width
    :param load: value to be scaled
    :param min_edge_width: minimal value for scaling
    :param max_edge_width: maximal value for scaling
    :param max_edge_width: maximal value for scaling
    :param max_load: maximum over all loads (needed as a denominator in the scaling)
    :param min_load: minimum over all loads
    :return: scaled load within [in_edge_width, max_edge_width]
    """
    if max_load == min_load:
        return (max_edge_width+min_edge_width) / 2
    scaled_load = (max_edge_width-min_edge_width) * (load-min_load) / (max_load-min_load) + min_edge_width
    return scaled_load


def draw_nx_loads_graph(nx_loads_graph, loads_draw_conversion_factor: float,
                        loads_graph_use_edge_color: bool, filename_loads_graph_file: str,
                        min_edge_width: float, max_edge_width: float) -> None:
    """
    create and save a graph with the x-and y-coordinates of the PTN, where the load of an edge is represented in the
    line thickness or line cover in line thickness
    :param nx_loads_graph: graph to be drawn
    :param loads_draw_conversion_factor: scales both the nodes and the edges of the graph
    :param loads_graph_use_edge_color: true if graph color (instead of edge width) should be used to represent the loads
    :param filename_loads_graph_file: file in which to save the result
    :param min_edge_width: the minimum edge width when plotting
    :param max_edge_width: the maximum edge width when plotting
    :return:
    """
    # set node attributes to use for drawing
    pos = nx.get_node_attributes(nx_loads_graph, "position")
    labels = dict(zip(nx_loads_graph.nodes, nx_loads_graph))

    # draw the network x graph
    logging.info("Begin making graph figure")
    plt.figure()
    ax = plt.gca()
    if loads_graph_use_edge_color:
        # set up attributes for graph
        cmap = plt.cm.Blues
        edge_colors = list(nx.get_edge_attributes(nx_loads_graph, "edge color").values())
        loads = nx.get_edge_attributes(nx_loads_graph, "load").values()
        # draw graph
        nx.draw_networkx_nodes(nx_loads_graph, pos=pos, node_color="w", node_shape='o',
                               node_size=300 * loads_draw_conversion_factor)
        nx.draw_networkx_labels(nx_loads_graph, pos=pos, labels=labels, font_size=12 * loads_draw_conversion_factor)
        # Below, edge_vmin=0 forces the lower limit of the colorbar to be set to zero. This is helpful because
        # the chosen cmap (plt.cm.Blues) gives edges with lower load a lighter color, which causes edges to seemingly
        # vanish if the lower end of the colorbar is automatically set to the lowest load and not forced to zero
        nx.draw_networkx_edges(nx_loads_graph, pos=pos, width=2 * loads_draw_conversion_factor,
                               edge_color=edge_colors, edge_cmap=cmap, edge_vmin=0)

        # set legend
        vmax = max(loads)
        if nx_loads_graph.graph["log_scale"]:
            # create logarithmic colorbar with its lower end set to 1, can't be set to zero because the
            # log can't be taken
            sm = plt.cm.ScalarMappable(cmap=cmap, norm=matplotlib.colors.LogNorm(vmin=1, vmax=vmax))  # vmin=vmin
        else:
            sm = plt.cm.ScalarMappable(cmap=cmap, norm=plt.Normalize(vmin=0, vmax=vmax))   # vmin=vmin
        sm._A = []
        ax = plt.gca()
        cbar = plt.colorbar(sm, ax=ax)
        cbar.set_label("load")
    else:
        edge_weights = list(nx.get_edge_attributes(nx_loads_graph, "edge width").values())
        edge_weights = [loads_draw_conversion_factor * w for w in edge_weights]
        # draw graph
        nx.draw_networkx_nodes(nx_loads_graph, pos=pos, node_color="w", node_shape='o',
                               node_size=300 * loads_draw_conversion_factor)
        nx.draw_networkx_labels(nx_loads_graph, pos=pos, labels=labels, font_size=12 * loads_draw_conversion_factor)
        nx.draw_networkx_edges(nx_loads_graph, pos=pos, width=edge_weights)
        # set legend
        num_intervals = 5  # number of lines to include in legend
        loads_values = list(nx.get_edge_attributes(nx_loads_graph, "load").values())
        if nx_loads_graph.graph["log_scale"]:
            loads_values = np.log(loads_values)
            label_lines = np.logspace(min(loads_values), max(loads_values), num_intervals, base=np.e)
            lines = [scale_singular_load(load=n_customers,
                                             min_edge_width=min_edge_width,
                                             max_edge_width=max_edge_width,
                                             max_load=max(np.log(label_lines)),
                                             min_load=min(np.log(label_lines)),
                                             ) for n_customers in np.log(label_lines)]
        else:
            label_lines = np.linspace(min(loads_values), max(loads_values), num_intervals, dtype=int)
            lines = np.linspace(min(edge_weights), max(edge_weights), num_intervals)
        line2ds = [Line2D([], [], linewidth=width, color='black') for width in lines]
        plt.legend(line2ds, np.round(label_lines, decimals=2), title="load")

    # make outline around nodes
    ax.collections[0].set_edgecolor("#000000")
    # force aspect ratio to equal
    ax.set_aspect('equal')
    # remove the frame around the figure
    ax.axis('off')
    logging.info("Finished making graph figure")

    # saving the figure
    logging.info("Begin saving loads graph")
    plt.savefig(filename_loads_graph_file, dpi=600, facecolor="white")
    logging.info("Finished saving loads graph")
    return


def main():
    logging.getLogger('matplotlib').setLevel(logging.WARNING)

    logging.info("Begin reading configuration")
    ConfigReader.read(sys.argv[1])
    config = Config.getDefaultConfig()
    logging.info("Finished reading configuration")

    logging.info("Begin reading config parameters")
    loads_file_name = config.getStringValueStatic("default_loads_file")
    if not os.path.isfile(loads_file_name):
        raise InputFileException(loads_file_name)
    stops_file_name = config.getStringValueStatic("default_stops_file")
    if not os.path.isfile(stops_file_name):
        raise InputFileException(stops_file_name)
    edges_file_name = config.getStringValueStatic("default_edges_file")
    if not os.path.isfile(edges_file_name):
        raise InputFileException(edges_file_name)
    filename_loads_graph_file = config.getStringValueStatic("filename_loads_graph_file")
    loads_graph_use_log_scale = config.getBooleanValueStatic("loads_graph_use_log_scale")
    loads_graph_use_edge_color = config.getBooleanValueStatic("loads_graph_use_edge_color")
    loads_graph_max_edge_width = config.getIntegerValueStatic("loads_graph_max_edge_width")
    loads_draw_conversion_factor = config.getDoubleValueStatic("loads_draw_conversion_factor")
    loads_graph_lower_bound = config.getDoubleValueStatic("loads_graph_lower_bound")
    loads_graph_upper_bound = config.getDoubleValueStatic("loads_graph_upper_bound")
    if loads_graph_lower_bound < 0 or loads_graph_lower_bound > 1:
        logging.fatal("The Config parameter \'od_visualization_lower_bound\' needs to be between 0 and 1")
    if loads_graph_upper_bound < 0 or loads_graph_upper_bound > 1:
        logging.fatal("The Config parameter \'od_visualization_upper_bound\' needs to be between 0 and 1")
    logging.info("Finished reading config parameters")
    logging.info("Finished reading config parameters")

    logging.info("Begin reading PTN")
    ptn = PTNReader.read(read_loads=True)
    logging.info("Finished reading PTN")

    logging.info("Begin creating network x graph")
    nx_loads_graph = load_data_to_netx_graph(ptn=ptn, loads_graph_lower_bound=loads_graph_lower_bound,
                                             loads_graph_upper_bound=loads_graph_upper_bound,
                                             loads_graph_use_log_scale=loads_graph_use_log_scale,
                                             loads_graph_use_edge_color=loads_graph_use_edge_color,
                                             min_edge_width=1,
                                             max_edge_width=loads_graph_max_edge_width)
    logging.info("Finished creating network x graph")

    logging.info("Begin creating and saving the graph")
    draw_nx_loads_graph(nx_loads_graph=nx_loads_graph,
                        loads_draw_conversion_factor=loads_draw_conversion_factor,
                        loads_graph_use_edge_color=loads_graph_use_edge_color,
                        filename_loads_graph_file=filename_loads_graph_file,
                        min_edge_width=1, max_edge_width=loads_graph_max_edge_width)
    logging.info("Finished creating and saving the graph")
    return


if __name__ == '__main__':
    main()
