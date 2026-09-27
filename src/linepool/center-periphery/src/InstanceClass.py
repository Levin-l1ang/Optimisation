from typing import List

import numpy as np

from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.model.impl.mapOD import MapOD
from core.model.lines import LinePool, Line
from core.model.ptn import Stop
from core.util.config import Config
from core.solver.solver_parameters import SolverParameters
from lengths_and_costs import dist, solveAPSPP, def_cost

class Instance:
    """
    class that handles commonly used variables
    """

    def __init__(self, config: Config) -> None:

        self.ptn = PTNReader.read(read_loads=True)
        self.Nodes = self.ptn.getNodes()
        self.Edges = self.ptn.getEdges()
        self.od = ODReader.read(od=MapOD(), size=-1)
        self.numb_nodes = len(self.Nodes)
        self.numb_Edges = len(self.Edges)
        self.Degrees = {node: 0 for node in self.Nodes}
        for edge in self.Edges:
            self.Degrees[edge.getLeftNode()] += 1
            self.Degrees[edge.getRightNode()] += 1
        self.directed = not config.getBooleanValue("ptn_is_undirected")

        self.ptn_res = PTNReader.read(read_loads=True)
        self.Nodes_res = self.ptn_res.getNodes()
        self.Edges_res = self.ptn_res.getEdges()

        self.solver_parameters = SolverParameters(config, "lc_")

        self.cost_fixed = config.getDoubleValue("lpool_costs_fixed")
        self.cost_length = config.getDoubleValue("lpool_costs_length")
        self.cost_edges = config.getDoubleValue("lpool_costs_edges")
        self.p_centers = config.getDoubleValue("lpool_centers_fraction")                      # Percentage of nodes, that can become centers
        self.p_periphery = config.getDoubleValue("lpool_periphery_radius_factor")               # Factor for mean_dist to choose periphery nodes
        self.p_od_periphery = config.getDoubleValue("lpool_direct_periphery_lines_factor")      # Anteil an Peripherieknotenpaaren, zwischen denen Linien gelegt werden sollen, wobei diese die größt mögliche OD-Data zwsichen den Knoten haben
        self.p_center_radius = config.getDoubleValue("lpool_center_radius_factor")              # Percentage of mean_dist determining the radius of the centers
        self.p_od_lines = config.getDoubleValue("lpool_concatenate_lines_factor")               # Factor for mean_od to choose node pairs, for which direct lines are created by concatenating existing lines.
        self.minimal_node_degree_for_center = config.getDoubleValue("lpool_min_degree_center")  # minimal node degree that a center node must have
        self.min_times_edge_covered = config.getDoubleValue("lpool_min_times_edge_covered")     # Each edge should be covered at least min_times_covered*f_min(edge) many times
        self.max_iter = config.getDoubleValue("lpool_max_iter_postprocessing")                  # maximal number of iterations for each postprocessing step
        self.opt_cost = config.getBooleanValue("lpool_opt_cost")                                # Determines, if shortest paths w.r.t. edge costs are used to create lines.
                                                                                                # For the choice of centers and peripheries always shortest paths w.r.t. edge lengths are used.

        try:
            self.plot_centers = config.getBooleanValue("lpool_plot_centers")                    # Create plots of PTN with centers indicated in red and periphery nodes in blue
        except:
            self.plot_centers = False

        self.Centers = []
        self.Periphery = []
        self.Endstations = []                                                   # List of all nodes with degree 1
        self.closest_Centers = {}                                               # Dict{Node: [Centers]}: For every node store a list of its closest centers
        self.covered_nodes = set()                                              # covered_nodes: empty set used to keep track of nodes that aren't covered by a line already
        self.interactions = []                                                  # interactions = number of interactions for each node (sum of all OD values starting from and ending in that node), measure for its importance

        self.weighted_mean_dist = 0
        mean_od = 0
        for i in range(self.numb_nodes):
            for j in range(self.numb_nodes):
                mean_od += self.od.getValue(i+1,j+1)
        mean_od = mean_od/(self.numb_nodes*(self.numb_nodes-1))
        self.mean_od = mean_od

        self.linepool = LinePool()
        self.linepool_rep = {start: {end: [] for end in self.Nodes} for start in self.Nodes}   # dictionary used to represent linepool to check whether a line is already contained in linepool or not
        self.edge_line_dict = {}                                                # edge_line_dict: dictionary with each edge as a key and an empty list as their value used to keep track of lines covering the edge which is the key
        for edge in self.Edges:
            self.edge_line_dict[edge] = []
        self.idx = 1                                                            # idx: integer with value 1 used as index of a new line added to the linepool. Updated when new line is added

        self.distances = np.zeros((self.numb_nodes, self.numb_nodes))
        self.Dijkstras = None

        self.distances_res = np.zeros((self.numb_nodes, self.numb_nodes))
        self.Dijkstras_res = None

        self.cost = def_cost(self.cost_length, self.cost_edges)

        self.distances_cost = np.zeros((self.numb_nodes, self.numb_nodes))
        self.Dijkstras_cost = None

        self.max_dist = 0
        self.mean_dist = 0
        self.mean_edge_length = np.mean([edge.getLength() for edge in self.Edges])
        self.center_radius = 0


        self.oneEdgeLines = 0

        self.feasibility_before = False
        self.feasibility_after = False


    def computeAPSPP(self) -> None:
        self.distances, self.Dijkstras = solveAPSPP(
                self.ptn,
                self.Nodes,
                self.numb_nodes,
                dist
            )
        if self.opt_cost:
            self.distances_cost, self.Dijkstras_cost = solveAPSPP(
                self.ptn,
                self.Nodes,
                self.numb_nodes,
                self.cost
            )
        self.max_dist = np.max(self.distances)
        self.mean_dist = np.mean(self.distances)
        self.center_radius = self.p_center_radius * self.mean_dist


    def findClosestNodes(self, node: Stop, candidates: List[Stop]) -> List[Stop]:
        """
        Find to node all closest nodes from candidates, distances between nodes are given in self.distances
        """
        if not candidates:
            print("No candidates given to find closest nodes.")
            return []
        mask = np.zeros((self.distances.shape[0]))
        mask[[candidate.getId()-1 for candidate in candidates]] = 1
        distances_candidates = np.where(mask, self.distances[node.getId()-1], np.ones((self.distances.shape[0])) * np.inf)
        indices = np.where(distances_candidates==distances_candidates.min())
        closestNodes = []
        for candidate in candidates:
            if candidate.getId()-1 in indices[0]:
                closestNodes.append(candidate)
        return closestNodes

    def setClosestCenters(self) -> None:
        for node in self.Nodes:
            self.closest_Centers[node] = self.findClosestNodes(node, self.Centers)


    def calculate_interactions(self) -> None:
        self.interactions = np.zeros((self.numb_nodes,))                          # interactions = number of interactions for each node (sum of all OD values starting from and ending in that node), measure for its importance
        for i in range(len(self.interactions)):
            for j in range(len(self.interactions)):
                self.interactions[i] += self.od.getValue(j+1,i+1) + self.od.getValue(i+1,j+1)


    def addToLinepoolRep(self, line: Line) -> None:
        """
        Add line to linepool_rep
        """
        start = line.getLinePath().getNodes()[0]
        end = line.getLinePath().getNodes()[-1]
        self.linepool_rep[start][end].append(line)
        if not self.directed:
            self.linepool_rep[end][start].append(line)



