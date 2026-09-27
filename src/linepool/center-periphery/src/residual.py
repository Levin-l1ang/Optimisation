import logging
import numpy as np
from typing import List, Callable, Dict
from InstanceClass import Instance
from core.model.graph import Graph
from core.model.ptn import Link
from core.model.lines import Line, LinePool

from lengths_and_costs import dist, solveAPSPP


logger = logging.getLogger(__name__)

def new_res_lengths(instance: Instance, dist: Callable[[Link], float] = dist, covered: bool = False) -> None:
    """ given residual edges, edges and a dict for reference, the function sets new weighted lengths """
    if not covered:
        for edge in instance.Edges_res:                                                      
            edge.length = np.exp(instance.ptn.getEdge(edge.getId()).getLowerFrequencyBound() - edge.getLowerFrequencyBound()) * dist(instance.ptn.getEdge(edge.getId()))
    else:
        for edge in instance.Edges_res:
            edge.length = len(instance.edge_line_dict[instance.ptn.getEdge(edge.getId())]) * dist(instance.ptn.getEdge(edge.getId()))

def def_dist_res(ptn_res: Graph, dist: Callable[[Link],float] = dist) -> Callable[[Link], float]:
    """ given ptn to res references, dist_res returns the dist function in res """
    def dist_res(edge: Link) -> float:
        return dist(ptn_res.getEdge(edge.getId()))
    return dist_res

def updateResidualNetwork(instance: Instance, covered: bool, dist: Callable[[Link], float] = dist) -> None:
    logger.debug("Entered updateResidualNetwork")
    f_min_lines = {}

    for line in instance.linepool.getLines():
        try:
            f_min_lines[line] = min([edge.getLowerFrequencyBound() for edge in line.getLinePath().getEdges()])
        except ValueError:
            f_min_lines[line] = 0

    for edge in instance.Edges:
        if instance.edge_line_dict[edge]:
            instance.ptn_res.getEdge(edge.getId()).setLowerFrequencyBound(max(0, edge.getLowerFrequencyBound() - len(instance.edge_line_dict[edge]) * min([f_min_lines[line] for line in instance.edge_line_dict[edge]])))
            # instance.ptn_res.getEdge(edge.getId().setLowerFrequencyBound(max(0, edge.getLowerFrequencyBound() - sum([f_min_lines[line] for line in instance.edge_line_dict[edge]])))

    new_res_lengths(instance, dist, covered)
    dist_res = def_dist_res(instance.ptn_res, dist)
    instance.distances_res, instance.Dijkstras_res = solveAPSPP(instance.ptn, instance.Nodes, instance.numb_nodes, dist_res)
    logger.debug("Exiting updateResidualNetwork")


def getCoveredTimes(Edges: List[Link], edge_line_dict: Dict[Link, List[Line]]) -> None:
    for edge in Edges:
        print(f"Edge {edge.getId()} covered {len(edge_line_dict[edge])} times")

def getFrequencyMin(Edges: List[Link], edge_line_dict: Dict[Link, List[Line]], linepool: LinePool) -> None:
    f_min_lines = {}

    for line in linepool.getLines():
        try:
            f_min_lines[line] = min([edge.getLowerFrequencyBound() for edge in line.getLinePath().getEdges()])
        except ValueError:
            f_min_lines[line] = 0

    for edge in Edges:
        if edge_line_dict[edge]:
            print(f"{edge.getId()}: {min([f_min_lines[line] for line in edge_line_dict[edge]])}")

def compute_covered_times(instance: Instance) -> int:
    return np.min(np.array([len(instance.edge_line_dict[edge]) for edge in instance.Edges]))

def computeCoveredTimesFrequency(instance: Instance) -> float:
    return min([len(instance.edge_line_dict[edge])/edge.getLowerFrequencyBound() for edge in instance.Edges if edge.getLowerFrequencyBound() > 0])