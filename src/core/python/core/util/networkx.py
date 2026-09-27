from typing import Union, TypeVar, Callable, List

import networkx as nx

from core.model.graph import Graph, Node, Edge
from core.model.impl.list_path import ListPath

N = TypeVar('N', bound=Node)
E = TypeVar('E', bound=Edge[N])
G = TypeVar('G', bound=Graph[N, E])


def convert_graph_to_networkx(graph: G, multi_graph: bool, weight_function: Callable[[E], float]) \
    -> Union[nx.Graph, nx.DiGraph, nx.MultiGraph, nx.MultiDiGraph]:
    """
    Convert the given graph to networkx
    :param graph: the graph to convert
    :param multi_graph: whether the given graph is a multigraph or simple
    :param weight_function: the weight function to use. The computed weight for each edge will be stored in the
    networkx edge attribute `weight`
    :return: the networkx graph
    """
    directed = graph.isDirected()
    if directed and multi_graph:
        nx_graph = nx.MultiDiGraph()
    elif directed:
        nx_graph = nx.DiGraph()
    elif multi_graph:
        nx_graph = nx.MultiGraph()
    else:
        nx_graph = nx.Graph()
    for node in graph.getNodes():
        nx_graph.add_node(node.getId(), id=node.getId())
    for edge in graph.getEdges():
        nx_graph.add_edge(edge.getLeftNode().getId(), edge.getRightNode().getId(), weight=weight_function(edge))
    return nx_graph

def convert_nxpath_to_listpath(nodes: List[Node], graph: Graph[Node, Edge]) -> ListPath:
    """
    convert a list of nodes which is returned as path in networkx to ListPath.
    :param nodes: list of node ids in consecutive order
    :param graph: the underlying graph
    :return: path as ListPath
    """
    list_path = ListPath(directed=graph.isDirected())
    for i in range(len(nodes)-1):
        list_path.addLastEdge(graph.get_edge_from_nodes(nodes[i], nodes[i+1]))
    return list_path
