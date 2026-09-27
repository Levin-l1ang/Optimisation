import logging
import os
import os.path
from pathlib import Path

import matplotlib
import matplotlib.pyplot as plt
import networkx as nx
import numpy as np
from core.exceptions.input_exceptions import InputFileException
from core.model.graph import Graph
from core.model.od import OD
from core.model.ptn import Stop, Link
from core.util.config import Config
from matplotlib.lines import Line2D


def graph_main(config: Config, od: OD, ptn: Graph[Stop, Link], od_draw_conversion_factor: float) -> None:
    """
    create and save a graph with the x-and y-coordinates of the PTN, where two stops A and B are connected with a line
    if there are passengers that wish to travel from A to B. The corresponding number of passengers is represented by
    in line thickness
    :param config: configuration
    :param od: OD data
    :param ptn: PTN
    :param od_draw_conversion_factor: scaling factor for nodes and node labels
    :return:
    """
    logging.info("Begin reading graph-specific config parameters")
    stops_file_name = config.getStringValueStatic("default_stops_file")
    if not os.path.isfile(stops_file_name):
        raise InputFileException(stops_file_name)
    filename_od_graph_file = config.getStringValueStatic("filename_od_visualization_file")
    od_graph_use_log_scale = config.getBooleanValueStatic("od_visualization_use_log_scale")
    od_graph_use_edge_color = config.getBooleanValueStatic("od_visualization_use_edge_color")
    od_graph_max_edge_width = config.getIntegerValueStatic("od_visualization_max_edge_width")
    od_draw_lower_bound = config.getDoubleValueStatic("od_visualization_lower_bound")
    od_draw_upper_bound = config.getDoubleValueStatic("od_visualization_upper_bound")
    if od_draw_lower_bound < 0 or od_draw_lower_bound > 1:
        logging.fatal("The Config parameter \'od_visualization_lower_bound\' needs to be between 0 and 1")
    if od_draw_upper_bound < 0 or od_draw_upper_bound > 1:
        logging.fatal("The Config parameter \'od_visualization_upper_bound\' needs to be between 0 and 1")
    logging.info("Finished reading graph-specific config parameters")
    od_graph_min_edge_width = 1

    # create network x graph from dataframe
    graph_od_data = make_nx_od_graph(od=od, ptn=ptn, od_draw_lower_bound=od_draw_lower_bound,
                                     od_draw_upper_bound=od_draw_upper_bound, min_edge_width=od_graph_min_edge_width,
                                     max_edge_width=od_graph_max_edge_width,
                                     od_graph_use_log_scale=od_graph_use_log_scale)
    stops = [stop.getId() for stop in ptn.getNodes()]

    # set node attributes to use for drawing
    pos = nx.get_node_attributes(graph_od_data, "position")
    labels = dict(zip(stops, stops))

    # check if OD matrix is symmetric and change to undirected graph if it is
    is_sym = od_data_is_symmetric(od=od)
    if is_sym:
        connection_style = "arc3"  # None
        graph_od_data = graph_od_data.to_undirected()
    else:
        connection_style = "arc3, rad = 0.1"  # use curved edges so edges (u,v) and (v,u) don't overlap
    logging.debug("The OD data matrix is symmetric: {}".format(is_sym))

    # draw the network x graph
    logging.info("Begin making graph figure")
    plt.figure()
    if od_graph_use_edge_color:
        # set up attributes for graph
        cmap = plt.cm.Blues
        # Hack the colormap to be the bluest blue, with the alpha value instead ranging from 0 to 1.
        # Get the colormap colors
        my_cmap = cmap(np.arange(cmap.N))
        # Get the blue value and set the whole colormap to it
        blue = my_cmap[-1,:]
        for i in np.arange(cmap.N):
            my_cmap[i,:] = blue
        # Set alpha
        my_cmap[:,-1] = np.linspace(0, 1, cmap.N)
        # Create new colormap
        cmap = matplotlib.colors.ListedColormap(my_cmap)

        # draw graph
        nx.draw_networkx_nodes(graph_od_data, pos=pos, node_color="w", node_shape='o',
                               node_size=300 * od_draw_conversion_factor)
        nx.draw_networkx_labels(graph_od_data, pos=pos, labels=labels, font_size=12 * od_draw_conversion_factor)
        edge_list, edge_colors = zip(*nx.get_edge_attributes(graph_od_data, 'edge color').items())
        nx.draw_networkx_edges(graph_od_data, pos=pos, edgelist=edge_list, connectionstyle=connection_style,
                               width=2*od_draw_conversion_factor, edge_color=blue, edge_cmap=cmap, edge_vmin=0, alpha=[val/max(edge_colors) for val in edge_colors])

        # set legend
        vmax = max(edge_colors)
        ax = plt.gca()
        ax.set_aspect('equal')
        if od_graph_use_log_scale:
            sm = plt.cm.ScalarMappable(cmap=cmap, norm=matplotlib.colors.LogNorm(vmin=1, vmax=vmax))
        else:
            sm = plt.cm.ScalarMappable(cmap=cmap, norm=plt.Normalize(vmin=0, vmax=vmax))
        sm._A = []
        cbar = plt.colorbar(sm, ax=ax) #, fraction=0.046, pad=0.04)
        cbar.set_label("customers")  # rotation = 270
    else:
        # draw graph
        nx.draw_networkx_nodes(graph_od_data, pos=pos, node_color="w", node_shape='o',
                               node_size=300 * od_draw_conversion_factor)
        nx.draw_networkx_labels(graph_od_data, pos=pos, labels=labels, font_size=12 * od_draw_conversion_factor)
        edge_list, edge_weights = zip(*nx.get_edge_attributes(graph_od_data, 'edge width').items())
        edge_weights = [od_draw_conversion_factor * w for w in edge_weights]
        nx.draw_networkx_edges(graph_od_data, pos=pos, edgelist=edge_list, connectionstyle=connection_style,
                               width=edge_weights)
        # set legend
        num_intervals = 5  # number of lines to include in legend
        customers = list(nx.get_edge_attributes(graph_od_data, "customers").values())
        if od_graph_use_log_scale:
            customers = np.log(customers)
            label_lines = np.logspace(min(customers), max(customers), num_intervals, base=np.e)
            lines = [scale_singular_od_value(od_value=n_customers,
                                             min_edge_width=od_graph_min_edge_width,
                                             max_edge_width=od_graph_max_edge_width,
                                             max_od_value=max(np.log(label_lines)),
                                             min_od_value=min(np.log(label_lines)),
                                             ) for n_customers in np.log(label_lines)]
        else:
            label_lines = np.linspace(min(customers), max(customers), num_intervals)
            lines = np.linspace(min(edge_weights), max(edge_weights), num_intervals)
        line2ds = [Line2D([], [], linewidth=width, color='black') for width in lines]
        plt.legend(line2ds, np.round(label_lines, decimals=2), title="customers")  # bbox_to_anchor=(0, 1)
        ax = plt.gca()
        ax.set_aspect('equal')

    # make outline around nodes

    ax.collections[0].set_edgecolor("#000000")
    # remove the frame around the figure
    ax.axis('off')
    logging.info("Finished making graph figure")

    # saving the figure
    logging.info("Begin saving od graph")
    Path(filename_od_graph_file).parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(filename_od_graph_file, dpi=600, facecolor="none")
    logging.info("Finished saving od graph")
    return


def make_nx_od_graph(od: OD, ptn: Graph[Stop, Link], od_draw_lower_bound: float, od_draw_upper_bound: float,
                     min_edge_width: int, max_edge_width: int, od_graph_use_log_scale: bool) \
                     -> nx.classes.digraph.DiGraph:

    # start with an empty directed graph
    # if the OD data is symmetric, this graph will simply be turned undirected later on
    nx_od_graph = nx.DiGraph()

    # prepare the nodes
    # simply adding all stops which occur in OD pairs is insufficient as some stops could get lost
    # so add all PTN stops to the graph
    for stop in ptn.getNodes():
        stop_id = stop.getId()
        nx_od_graph.add_node(stop_id)
        nx_od_graph.nodes[stop_id]["position"] = [stop.getXCoordinate(), stop.getYCoordinate()]

    max_od = max([od_pair.getValue() for od_pair in od.getODPairs()])  # used for determining the edge width
    max_od_val = max([od_pair.getValue() for od_pair in od.getODPairs() if od_draw_lower_bound * max_od <= od_pair.getValue() <= od_draw_upper_bound * max_od])  # used for determining the edge width
    min_od_val = min([od_pair.getValue() for od_pair in od.getODPairs() if od_draw_lower_bound * max_od <= od_pair.getValue() <= od_draw_upper_bound * max_od])  # used for determining the edge width
    for od_pair in od.getODPairs():
        u = od_pair.getOrigin()
        v = od_pair.getDestination()
        # nx_od_graph.add_edge(u, v)

        # add od pair value as attribute
        c = od_pair.getValue()

        # process od value if logarithm should be applied
        # and continue with the original value otherwise
        if od_graph_use_log_scale:
            if c <= 0: # can't do logarithms, skip this edge
                continue 
            processed_od_val = np.log(c)
        else:
            processed_od_val = c
        # print(f"{u}-{v}:{od_draw_lower_bound * max_od}<={c}<={od_draw_upper_bound * max_od}")
        if od_draw_lower_bound * max_od <= c <= od_draw_upper_bound * max_od:
            nx_od_graph.add_edge(u, v)
            nx_od_graph[u][v]["customers"] = c
            # set the edge width within the given interval [min_edge_width, max_edge_width]
            if od_graph_use_log_scale:
                nx_od_graph[u][v]["edge width"] = scale_singular_od_value(od_value=processed_od_val,
                                                                      min_edge_width=min_edge_width,
                                                                      max_edge_width=max_edge_width,
                                                                      max_od_value=np.log(max_od_val),
                                                                      min_od_value=np.log(min_od_val) 
                                                                      )
            else:
                nx_od_graph[u][v]["edge width"] = scale_singular_od_value(od_value=processed_od_val,
                                                                      min_edge_width=min_edge_width,
                                                                      max_edge_width=max_edge_width,
                                                                      max_od_value=max_od_val,
                                                                      min_od_value=min_od_val)
            nx_od_graph[u][v]["edge color"] = processed_od_val
    return nx_od_graph


def scale_singular_od_value(od_value: float, min_edge_width: int, max_edge_width: int, max_od_value: int, min_od_value: int = 0):
    """
    scales the traffic loads of the edges to be somewhere between min_edge_width and max_edge_width
    :param od_value: value to be scaled
    :param min_edge_width: minimal value for scaling
    :param max_edge_width: maximal value for scaling
    :param max_edge_width: maximal value for scaling
    :param max_od_value: maximum over all OD values (needed as a denominator in the scaling)
    :param min_od_value: minimum over all OD values
    :return: scaled load within [in_edge_width, max_edge_width]
    """
    if max_od_value == min_od_value:
        return (max_edge_width+min_edge_width) / 2
    scaled_load = (max_edge_width-min_edge_width) * (od_value-min_od_value) / (max_od_value-min_od_value) + min_edge_width
    return scaled_load


def od_data_is_symmetric(od: OD):
    is_symmetric = True
    for od_pair in od.getODPairs():
        u = od_pair.getOrigin()
        v = od_pair.getDestination()
        if od.getValue(u, v) != od.getValue(v, u):
            is_symmetric = False
    return is_symmetric
