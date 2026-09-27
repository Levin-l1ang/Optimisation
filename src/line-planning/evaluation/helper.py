from collections.abc import Callable
import logging
from itertools import chain
from typing import Dict, List

import networkx as nx

from core.model.change_and_go import CGNode, CGEdge
from core.model.graph import Graph, Node
from core.model.ptn import Stop, Link

logger = logging.getLogger(__name__)


def transform_ptn_into_networkx(
        ptn: Graph[Stop, Link], weight_function: Callable[[Link], float],
        wait_time: float, link_frequencies: Dict[Link, int]) -> nx.Graph:
    logger.info("start constructing networkx ptn graph")
    if ptn.isDirected():
        ptn_networkx = nx.DiGraph()
    else:
        ptn_networkx = nx.Graph()
    ptn_networkx.add_nodes_from(ptn.getNodes())
    ptn_networkx.add_edges_from(
        [(edge.getLeftNode(), edge.getRightNode(),
          {"weight": weight_function(edge) + wait_time, "object": edge})
         for edge in ptn.getEdges() if link_frequencies.get(edge, 0) > 0])
    logger.info("finished constructing networkx ptn graph")
    return ptn_networkx


def transform_cg_into_networkx(change_and_go: Graph[CGNode, CGEdge]) \
        -> nx.Graph:
    logger.info("start constructing networkx change and go graph")
    assert change_and_go.isDirected()
    change_and_go_networkx = nx.DiGraph()
    change_and_go_networkx.add_nodes_from(change_and_go.getNodes())
    change_and_go_edges = change_and_go.getEdges()
    undirected_change_and_go_edges = [edge for edge in change_and_go_edges
                                      if not edge.isDirected()]
    change_and_go_networkx.add_edges_from(
        [(change_and_go_edges[i].getLeftNode(),
          change_and_go_edges[i].getRightNode(),
          {'weight': change_and_go_edges[i].getCost(),
           'object': change_and_go_edges[i]})
         for i in range(len(change_and_go_edges))])
    change_and_go_networkx.add_edges_from(
        [(undirected_change_and_go_edges[i].getRightNode(),
          undirected_change_and_go_edges[i].getLeftNode(),
          {'weight': undirected_change_and_go_edges[i].getCost(),
           'object': undirected_change_and_go_edges[i]})
         for i in range(len(undirected_change_and_go_edges))])
    logger.info("finished constructing networkx change and go graph")
    return change_and_go_networkx


def sublist(complete_lst: List[Node], sub_lst: List[Node], allow_reverse=True):
    sub_lst_set = set(sub_lst)
    lst = [node for node in complete_lst if node in sub_lst_set]

    if lst == sub_lst:
        start = complete_lst.index(sub_lst[0])
        end = complete_lst.index(sub_lst[-1])
        if abs(start - end) + 1 == len(sub_lst):
            return start, end

    if allow_reverse:
        if lst == [node for node in reversed(sub_lst)]:
            start = complete_lst.index(sub_lst[0])
            end = complete_lst.index(sub_lst[-1])
            if abs(start - end) + 1 == len(sub_lst):
                return end, start
            else:
                return None, None
        else:
            return None, None


def sublist_iter(complete_lst: List, sub_lst: List, allow_reverse=True):
    sub_iters = {}

    start_indices = {i for i, node in enumerate(complete_lst) if node == sub_lst[0]}

    for i, node in enumerate(chain(complete_lst, [None])):
        if i in start_indices:
            sub_iters[i] = iter(sub_lst)
        remove = []
        for j, sub_lst_iter in sub_iters.items():
            try:
                if node != next(sub_lst_iter):
                    remove.append(j)
            except StopIteration:
                return j, i - 1
        for j in remove:
            sub_iters.pop(j)

    if allow_reverse:
        start, stop = sublist_iter(list(reversed(complete_lst)), sub_lst, allow_reverse=False)
        if start is None:
            return None, None
        else:
            return len(complete_lst) - 1 - stop, len(complete_lst) - 1 - start

    return None, None


def set_drive_weight_function(ean_model_weight_drive: str) -> Callable[[Link], float]:
    def average_driving_time(link: Link):
        return (link.getUpperBound() + link.getLowerBound()) / 2

    if ean_model_weight_drive == "AVERAGE_DRIVING_TIME":
        return average_driving_time
    elif ean_model_weight_drive == "MINIMAL_DRIVING_TIME":
        return Link.getLowerBound
    elif ean_model_weight_drive == "MAXIMAL_DRIVING_TIME":
        return Link.getUpperBound
    elif ean_model_weight_drive == "EDGE_LENGTH":
        return Link.getLength
    else:
        logger.warning("Parameter ean_model_weight_drive not correct. Use EDGE_LENGTH instead.")
        return Link.getLength