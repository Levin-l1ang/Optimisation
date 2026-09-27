import logging
import numpy as np
from core.algorithm.dijkstra import Dijkstra
from core.model.ptn import Link, Stop
from core.model.graph import Graph
from core.model.lines import LinePool, Line
from typing import Callable, List, Tuple, Dict


logger = logging.getLogger(__name__)

def dist(edge: Link) -> float:
    """ given an edge, dist returns its length """
    return edge.getLength()

def def_cost(cost_length: float, cost_edges: float) -> Callable[[Link], float]:
    def cost(edge: Link) -> float:
        return cost_length * edge.getLength() + cost_edges
    return cost

def solveAPSPP(ptn: Graph, Nodes: List[Stop], numb_nodes: int, dist: Callable[[Link], float]) -> Tuple[np.ndarray, Dict[Stop, Dijkstra]]:
    """ solves the all pairs shortest path problem """
    logger.debug("Entered solveAPSPP")

    Dijkstras = {}
    distances = np.zeros((numb_nodes,numb_nodes))       # numb_nodes x numb_nodes-Matrix, distances[i,j]=shortest path length from i to j
    for start in Nodes:
        M = Dijkstra(ptn, start, dist)
        M.computeShortestPaths()
        Dijkstras[start] = M
        for end in Nodes:
            distances[start.getId()-1,end.getId()-1] = M.getDistance(end)

    logger.debug("Exiting solveAPSPP")
    return distances, Dijkstras

def set_costs_for_line(line: Line, cost_fixed: float, cost_length: float, cost_edges: float) -> None:
    """
    sets the costs for line to cost_fixed + sum([cost_length * edge.length + cost_edges for edge in line.getLinePath().getEdges()]))]
    """
    logger.debug("Entered set_costs_for_line")
    line.setCost(cost_fixed + sum([cost_length * edge.length + cost_edges for edge in line.getLinePath().getEdges()]))
    logger.debug("Exiting set_costs_for line")

def set_cost_for_pool(linepool: LinePool, cost_fixed: float, cost_length: float, cost_edges: float) -> None:
    """
    sets the costs for every line in linepool by the function set_costs_for_line
    """
    logger.debug("Entered set_costs_for_pool")
    for line in linepool.getLines():
        set_costs_for_line(line, cost_fixed, cost_length, cost_edges)
    logger.debug("Exiting set_costs_for_pool")

