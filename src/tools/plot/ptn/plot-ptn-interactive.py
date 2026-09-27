import logging
import os
import os.path
import sys

import holoviews as hv
import networkx as nx
import numpy as np
from bokeh.models import HoverTool

from core.exceptions.input_exceptions import InputFileException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.ptn import Stop, Link
from core.util.config import Config

hv.extension('bokeh')

logger = logging.getLogger(__name__)


def ptn_to_netx_graph(ptn: Graph[Stop, Link]) -> nx.classes.graph.Graph:
    """
    Convert the ptn into a custom network x graph for further processing
    :param ptn: PTN
    :return: Network X graph representing the PTN
    """
    # Initiate an empty (directed or undirected) graph
    if ptn.isDirected():
        ptn_nx_graph = nx.DiGraph()
    else:
        ptn_nx_graph = nx.Graph()

    # set node-and edge properties
    for edge in ptn.getEdges():
        left_node = edge.getLeftNode()
        right_node = edge.getRightNode()
        u = left_node.getId()
        v = right_node.getId()
        ptn_nx_graph.add_edge(u, v)
        # set node properties
        ptn_nx_graph.nodes[u]["stop_id"] = u
        ptn_nx_graph.nodes[v]["stop_id"] = v
        # set short-and-long names
        ptn_nx_graph.nodes[u]["short_name"] = left_node.getShortName()
        ptn_nx_graph.nodes[u]["long_name"] = left_node.getLongName()
        ptn_nx_graph.nodes[v]["short_name"] = right_node.getShortName()
        ptn_nx_graph.nodes[v]["long_name"] = right_node.getLongName()
        # set position
        ptn_nx_graph.nodes[u]["position"] = (left_node.getXCoordinate(), left_node.getYCoordinate())
        ptn_nx_graph.nodes[v]["position"] = (right_node.getXCoordinate(), right_node.getYCoordinate())
    return ptn_nx_graph


def ptn_nx_to_hv(nx_ptn_graph: nx.classes.graph.Graph) -> hv.element.graphs.Graph:
    """
    Parse the networkx graph representation into a
    :param nx_ptn_graph:
    :return:
    """
    # Extract the node position from the network x graph as it is required parameter for the conversion into a
    # holoviews graph.
    # Afterwards, remove the 'position' attribute from the network x graph. Otherwise an error will arise during the
    # conversion because the function will try to convert the 'position' attribute, but also use the given position
    # in the function parameter which creates an internal conflict in conversion function

    pos = nx.get_node_attributes(nx_ptn_graph, "position")
    for node_id in nx_ptn_graph.nodes:
        del nx_ptn_graph.nodes[node_id]['position']

    for edge in nx_ptn_graph.edges():
        u, v = edge
        # the following code does not work if edge attribute name contains "-", instead use "_"
        nx_ptn_graph[u][v]["left_stop_id"] = u
        nx_ptn_graph[u][v]["right_stop_id"] = v
    hv_loads_graph = hv.Graph.from_networkx(nx_ptn_graph, positions=pos)
    return hv_loads_graph

def remove_background_hook(plot: hv.plotting.bokeh.graphs.GraphPlot, element: hv.element.graphs.Graph)-> None:
    """
    removes background of figure, used as plot hook for the hv graph
    :param plot: plot where the background should be removed
    :param element: unused parameter, but necessary because plot hook convention required two arguments,
                    otherwise errors occur
    :return:
    """
    plot.handles["xaxis"].visible = False
    plot.handles["yaxis"].visible = False
    plot.handles["plot"].border_fill_color = None
    plot.handles["plot"].background_fill_color = None
    plot.handles["plot"].outline_line_color = None
    return

def adjust_hv_graph(hv_loads_graph: hv.element.graphs.Graph, ptn: Graph[Stop, Link], node_label_node_size: int,
                    display_edge_labels: bool) -> hv.core.overlay.Overlay:
    """
    Change the node -and label size of the given holoviews graph and set other layout options, e.g. node labels and
    graph title. Note, that this will change the graph type
    :param hv_loads_graph:
    :param node_label_node_size: interactive parameter
    :return: graph adjusted to the given parameters
    """

    # assign node labels
    pos = dict(zip(hv_loads_graph.nodes.columns()["index"],
                   map(list, zip(hv_loads_graph.nodes.columns()["x"], hv_loads_graph.nodes.columns()["y"]))))
    label_text = hv_loads_graph.nodes.columns()["index"]
    node_labels = hv.Labels({('x', 'y'): np.array(list(pos.values())), 'text': label_text}, ['x', 'y'], 'text')
    node_labels = node_labels.opts(text_font_size='{}pt'.format(node_label_node_size/2.1), text_color='black')

    # create custom hover tool such that the default parameter 'index' is not displayed
    hover = HoverTool(tooltips=[("stop-id", "@stop_id"), ("short name", "@short_name"), (("long name", "@long_name"))])

    # supply the interactive graph with layout information:
    # - enable prepared hover tool and overlay with prepared node labels
    # - set empty title, otherwise the interactive parameters become the title
    # - 'clone=True' makes sure the graph will not be changed by adding options
    # - remove axis labels
    graph = hv_loads_graph.opts(clone=True, tools=[hover], inspection_policy="nodes", title="", xaxis=None, yaxis=None,
                                node_size=node_label_node_size, hooks=[remove_background_hook]) * node_labels

    # add additional edeg labels via an additional overlay
    if display_edge_labels:
        edge_labels = get_edge_labels(ptn=ptn)
        edge_labels = edge_labels.opts(text_font_size='{}pt'.format(node_label_node_size/2.1), text_color='black')
        graph = graph * edge_labels
    return graph


def get_edge_labels(ptn: Graph[Stop, Link]):
    """
    manually define a holoviews label object that represent the edge labels
    :param ptn: public transportation network
    :return:
    """
    # recover the number of edges for iteration purposes
    number_of_edges = len(ptn.getEdges())

    label_for_edge = []     # edge labels to be set
    data = np.zeros((number_of_edges, 2))
    i = 0
    for edge in ptn.getEdges():
        edge_label = edge.getId()
        label_for_edge.append(edge_label)
        left_stop_coordinate = [edge.getLeftNode().getXCoordinate(), edge.getLeftNode().getYCoordinate()]
        right_stop_coordinates = [edge.getRightNode().getXCoordinate(), edge.getRightNode().getYCoordinate()]
        x_left_stop, y_left_stop = left_stop_coordinate
        x_right_stop, y_right_stop = right_stop_coordinates

        # manually set the coordinates for the edge label
        # for convenience define x to be the point with the higher coordinates
        if y_right_stop > y_left_stop:
            h = left_stop_coordinate
            left_stop_coordinate = right_stop_coordinates
            right_stop_coordinates = h
            x_left_stop, y_left_stop = left_stop_coordinate
            x_right_stop, y_right_stop = right_stop_coordinates

        # set the coordinates for the edge labels
        z = [0, 0]
        z[1] = y_right_stop + (y_left_stop - y_right_stop) / 2
        if x_left_stop > x_right_stop:
            z[0] = x_right_stop + (x_left_stop - x_right_stop) / 2
        else:
            z[0] = x_left_stop + (x_right_stop - x_left_stop) / 2
        data[i] = z
        i += 1

    # set labels
    labels = hv.Labels({('x', 'y'): data, 'text': label_for_edge}, ['x', 'y'], 'text')
    return labels


def main():
    # logging.getLogger('matplotlib.font_manager').setLevel(logging.WARNING)
    logging.getLogger('matplotlib').setLevel(logging.WARNING)

    logging.info("Begin reading configuration")
    ConfigReader.read(sys.argv[1])
    config = Config.getDefaultConfig()
    logging.info("Finished reading configuration")

    logging.info("Begin reading config parameters")
    stops_file_name = config.getStringValueStatic("default_stops_file")
    if not os.path.isfile(stops_file_name):
        raise InputFileException(stops_file_name)
    edges_file_name = config.getStringValueStatic("default_edges_file")
    if not os.path.isfile(edges_file_name):
        raise InputFileException(edges_file_name)
    filename_ptn_graph_file = config.getStringValueStatic("filename_ptn_interactive_graph_file")
    display_edge_labels = config.getBooleanValueStatic("ptn_draw_interactive_graph_edge_labels")
    logger.info("Finished reading config parameters")

    # create network x graph
    logging.info("Begin reading PTN")
    ptn = PTNReader.read()
    logging.info("Finished reading PTN")

    logging.info("Begin creating network x graph")
    nx_ptn_graph = ptn_to_netx_graph(ptn)
    logging.info("Finished creating network x graph")

    logging.debug("Begin conversion of the network x graph into an hv graph")
    hv_ptn_graph = ptn_nx_to_hv(nx_ptn_graph=nx_ptn_graph)
    logging.debug("Finished conversion of the network x graph into an hv graph")

    logging.debug("Begin creating dynamic map")

    def g(node_label_node_size):
        return adjust_hv_graph(hv_loads_graph=hv_ptn_graph, ptn=ptn, node_label_node_size=node_label_node_size,
                               display_edge_labels=display_edge_labels)
    dmap = hv.DynamicMap(g, kdims=["node_label_node_size"])
    dmap = dmap.redim.values(node_label_node_size=np.array(range(50, 500, 10))/10)\
        .redim(node_label_node_size="node size")
    dmap.opts(width=600, height=600)
    logging.debug("Finished creating dynamic map")

    logging.info("Begin writing interactive graph to {}".format(filename_ptn_graph_file))
    hv.save(obj=dmap, filename=filename_ptn_graph_file)
    logging.info("Finished writing interactive to {}".format(filename_ptn_graph_file))
    return


if __name__ == '__main__':
    main()
