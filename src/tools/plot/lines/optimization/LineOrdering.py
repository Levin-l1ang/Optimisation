import logging
import sys
import numpy as np
import math
import os.path

from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader, Config
from core.io.lines import LineReader
from core.io.ptn import PTNReader
from core.io.csv import CsvWriter, CsvReader
from core.model.lines import Line
from core.model.ptn import Stop, Link
from core.model.graph import Graph
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, \
    IntAttribute, DoubleAttribute, LinearExpression, IntParam
from core.solver.solver_parameters import SolverParameters
from core.util.mm_utilities import add_modality_prefix
from typing import List, Dict


logger = logging.getLogger(__name__)

def combine_edges(ptns: dict[str, Graph[Stop, Link]]) -> tuple[dict[Link, Link], dict[Link, list[Link]]] :
    """
    Creates a mapping from PTN edges to modality- and direction-agnostic edges that can be used in 
    the optimization model. Also returns the "inverse mapping", with lists of old edges corresponding to each new edge.
    :param ptns: A dictionary of modality PTNs
    :type ptns: dict[str, Graph[Stop, Link]]
    :return: The mapping and its inverse.
    :rtype: tuple[dict[Link, Link], dict[Link, list[Link]]]
    """
    combined_edges = {}
    combined_edges_inv = {}
    for ptn in ptns.values():
        for edge in ptn.getEdges():
            leftnode = edge.getLeftNode()
            rightnode = edge.getRightNode()
            leftnode.modality = ""
            rightnode.modality = ""
            if leftnode.getId() < rightnode.getId():
                new_edge = Link(0, leftnode, rightnode, 0, 0, 0, False)
            else:
                new_edge = Link(0, rightnode, leftnode, 0, 0, 0, False)
            combined_edges[edge] = new_edge
            combined_edges_inv[new_edge] = combined_edges.get(new_edge, []).append(edge)
    return (combined_edges, combined_edges_inv)

def get_lines_on_edges(line_pool: List[Line], combined_edges: dict[Link, Link], line_id_map: dict[int, int], line_mod_map: dict[int, str]) -> Dict[(Link, List[Line])] :
    """
    Returns a mapping of edges to lists of lines on that edge. Uses the modality- and direction-agnostic 
    edges from the combine_edges -function.
    :param line_pool: The line pool (or line concept) whose lines are considered
    :type line_pool: List[Line]
    :param combined_edges: The mapping of PTN edges to the edges used by the model
    :type combined_edges: dict[Link, Link]
    :return: A mapping of edge => list of lines on that edge
    :rtype: Dict[(Link, List[Line])]
    """
    lines_on_edge: Dict[Link, List[Line]] = {}
    for line in line_pool:
        for edge in line.getLinePath().getEdges():
            edge_simplified = combined_edges[edge]
            if edge_simplified in lines_on_edge:
                cur_lines = lines_on_edge[edge_simplified]
                if line in cur_lines:
                    logger.error(f"Line {line_id_map[line.getId()]} of modality {line_mod_map[line.getId()]} traverses edge {edge.getId()} twice, this is not supported by the model")
                else:
                    cur_lines.append(line)
                    lines_on_edge[edge_simplified] = cur_lines
            else:
                lines_on_edge[edge_simplified] = [line]
    return lines_on_edge

# needed to figure out if a crossing happens when two lines split, return link that needs to have smaller p
def get_direction(common_edge: Link, edge1: Link, edge2: Link) -> Link:
    """
    When lines split, determine the split edge whose corresponding line should have a smaller index on the common edge.
    :param common_edge: The edge that both lines take before the split
    :type common_edge: Link
    :param edge1: The edge that the first line takes after the split
    :type edge1: Link
    :param edge2: The edge that the second line takes after the split
    :type edge2: Link
    :return: Either edge1 or edge2, depending on their order
    :rtype: Link
    """
    common_node: Stop = None
    prev_node: Stop = None
    cor_dir = False

    if common_edge.getLeftNode() == edge1.getRightNode() or common_edge.getLeftNode() == edge1.getLeftNode():
        if common_edge.getLeftNode() == edge2.getLeftNode() or common_edge.getLeftNode() == edge2.getRightNode():
            common_node = common_edge.getLeftNode()
            prev_node = common_edge.getRightNode()

    if common_node == None: 
        common_node = common_edge.getRightNode()
        prev_node = common_edge.getLeftNode()
        cor_dir = True
    node1: Stop = None
    node2: Stop = None

    if edge1.getLeftNode() != common_node: node1 = edge1.getLeftNode()
    else: node1 = edge1.getRightNode()

    if edge2.getLeftNode() != common_node: node2 = edge2.getLeftNode()
    else: node2 = edge2.getRightNode()

    coords_common = (common_node.getXCoordinate(), common_node.getYCoordinate())
    coords_prev = ((prev_node.getXCoordinate(), prev_node.getYCoordinate()))
    coords1 = (node1.getXCoordinate(), node1.getYCoordinate())
    coords2 = (node2.getXCoordinate(), node2.getYCoordinate())


    angle_prev = np.arctan2(coords_prev[1] - coords_common[1], coords_prev[0] - coords_common[0])
    angle_1 = np.arctan2(coords1[1] - coords_common[1], coords1[0] - coords_common[0])
    angle_2 = np.arctan2(coords2[1] - coords_common[1], coords2[0] - coords_common[0])

    angles = [angle_prev, angle_1, angle_2]

    for i in range(0,3):
        if angles[i] < 0: 
            angles[i] = angles[i] + 2*np.pi
            if i == 0: angle_prev += 2*np.pi
            elif i == 1: angle_1 += 2*np.pi
            else: angle_2 += 2*np.pi
    angles.sort()

    if angles[0] == angle_prev:
        if angles[1] == angle_1: 
            if cor_dir: 
                return edge2 
            else:
                return edge1
        
        else:
            if cor_dir: 
                return edge1
            else: 
                return edge2

    elif angles[1] == angle_prev:
        if angles[0] == angle_1:
            if cor_dir: 
                return edge1
            else: 
                return edge2
        elif cor_dir: 
            return edge2
        else: 
            return edge1

    else:
        if angles[0] == angle_1:
            if cor_dir: 
                return edge2
            else: 
                return edge1
        elif cor_dir: 
            return edge1
        else: 
            return edge2



def line_order_optimization(lines_mode: dict[str, list[Line]], parameters: SolverParameters, config: Config, combined_edges: dict[Link, Link], merged_lines_mode: dict[tuple[str, int], str], lc: bool) -> bool:
    """
    Build and optimize the MIP model from Bast et al. (2019) to determine the (visually) optimal ordering of lines on edges.
    Also writes this order to filename_optimized_line_pool_order or filename_optimized_line_concept_order, depending on the parameter lc.
    :param lines_mode: A mapping from modalities to either line pools or line concepts 
    :type lines_mode: dict[str, list[Line]]
    :param parameters: The parameters for the solver
    :type parameters: SolverParameters
    :param config: The configuration used
    :type config: Config
    :param combined_edges: The mapping of PTN edges to the edges used by the model
    :type combined_edges: dict[Link, Link]
    :param merged_lines_mode: If some lines should be merged, the mapping of modality and line id to the name of the merged line
    :type merged_lines_mode: dict[tuple[str, int], str]
    :param lc: True if line concept should be used instead of line pool
    :type lc: bool
    :return: Did the solver find a feasible solution (NOTE: not necessarily optimal, if time limit was reached)
    :rtype: bool
    """
    merged_lines = {}
    logger.info("Begin finding lines on edges")
    lines = []
    line_id_map = {}
    line_mod_map = {}
    running_id = 1
    for mod in lines_mode.keys():
        for line in lines_mode[mod]:
            old_id = line.getId()
            line.line_id = running_id
            lines.append(line)
            line_id_map[line.getId()] = old_id
            line_mod_map[line.getId()] = mod
            if (mod, old_id) in merged_lines_mode:
                merged_lines[running_id] = merged_lines_mode[(mod, old_id)]
            running_id += 1
    lines_on_edges = get_lines_on_edges(lines, combined_edges, line_id_map, line_mod_map)
    logger.info("Finished finding lines on edges")
 
    logger.debug("Adding variables")
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    nodes_used: Dict[Link, Stop] = {}
    line_pairs: Dict[Link, List[tuple[Line, Line]]] = {}

    x_elp = {}          # x_(el <= p)   line positioning
    x_eAB = {}          # x_(eA < B)    line order A vs B
    x_eeAB: Dict[tuple[Link, Link, Line, Line]] = {}         # x_(ee'AB)     do A and B cross between e and e'
    x_eeeAB = {}        # if a crossing happens at a split
    x_eAwB = {}         # if A and B are next to each other
    x_eeAwB: Dict[tuple[Link, Link, Line, Line]] = {}        # if A and B get separated

    sum_elp: Dict[tuple[Link, Line], LinearExpression] = {}
    each_pos_occupied: Dict[tuple[Link, int], LinearExpression] = {}
    sum_pairs: Dict[Link, LinearExpression] = {}

    sum_crossings = model.createExpression()
    sum_separations = model.createExpression()

    max_lines = 0
    # find max lines on an edge (works as big M)
    for edge in lines_on_edges:
        if len(lines_on_edges[edge]) > max_lines:
            max_lines = len(lines_on_edges[edge])

    for edge in lines_on_edges:
        nodes_used[edge] = edge.getLeftNode()
        line_pairs[edge] = []
        for line in lines_on_edges[edge]:
            for p in range(1, len(lines_on_edges[edge]) + 1):          
                x = model.addVariable(0, 1, VariableType.BINARY, 0, f"x_{edge.getId()},{line.getId()}<={p}")
                x_elp[(edge, line, p)] = x
                if (edge, p) not in each_pos_occupied:
                    each_pos_occupied[(edge,p)] = model.createExpression()
                each_pos_occupied[(edge,p)].add(x)
                
                if (edge, line) not in sum_elp:
                    sum_elp[(edge,line)] = model.createExpression()
                sum_elp[(edge,line)].add(x)


            for line2 in lines_on_edges[edge]:
                if line2 != line:
                    if ((line, line2) not in line_pairs[edge]) and ((line2, line) not in line_pairs[edge]):
                        line_pairs[edge].append((line,line2))
                        #lines next to each other variables
                        awb = model.addVariable(0,1,VariableType.BINARY, 0, f"x_{edge.getId()},{line.getId()}||{line2.getId()}")
                        x_eAwB[(edge, line, line2)] = awb
                    #line position variables
                    
                    ab = model.addVariable(0,1,VariableType.BINARY, 0, f"x_{edge.getId()},{line.getId()}<={line2.getId()}")
                    x_eAB[(edge, line, line2)] = ab

                    line1path = line.getLinePath().getEdges()
                    index1 = next(i for i, line_edge in enumerate(line1path) if line_edge.getLeftNode().getId() == edge.getLeftNode().getId() or line_edge.getLeftNode().getId() == edge.getRightNode().getId())
                    # index1 = line1path.index(edge)
                    line2path = line2.getLinePath().getEdges()
                    index2 = next(i for i, line_edge in enumerate(line2path) if line_edge.getLeftNode().getId() == edge.getLeftNode().getId() or line_edge.getLeftNode().getId() == edge.getRightNode().getId())
                    # index2 = line2path.index(edge)

                    if (line, line2) in line_pairs[edge]:
                        
                        if len(line1path) > index1 + 1:
                            
                            next_edge_1: Link = line1path[index1 + 1]
                            next_edge_1 = combined_edges[next_edge_1]
                            # no splitting: a crossing happens if the line positions change
                            if line2 in lines_on_edges[next_edge_1]:
                                # intra-path crossing variables
                                if (next_edge_1, edge, line, line2) not in x_eeAB and (next_edge_1, edge, line2, line) not in x_eeAB and (edge, next_edge_1, line2, line) not in x_eeAB:
                                    x = model.addVariable(0,1, VariableType.BINARY, 0, f"x_{edge.getId()},{next_edge_1.getId()},{line.getId()},{line2.getId()}")
                                    x_eeAB[(edge, next_edge_1, line, line2)] = x
                                # line separation variables
                                if (next_edge_1, edge, line, line2) not in x_eeAwB: 
                                    y = model.addVariable(0,1,VariableType.BINARY, 0, f"x_{edge.getId()},{next_edge_1.getId()},{line.getId()},{line2.getId()}")
                                    x_eeAwB[(edge, next_edge_1, line, line2)] = y
    
                            else:
                            # the lines split, a crossing happens depending on the directions of the splitting edges wrt the common edge
                                next_edge_2 = None
                                next_edge1_nodes = [next_edge_1.getLeftNode(), next_edge_1.getRightNode()]
                                if index2 != len(line2path) - 1:
                                    if line2path[index2 + 1].getRightNode() in next_edge1_nodes or line2path[index2 + 1].getLeftNode() in next_edge1_nodes:
                                        next_edge_2 = line2path[index2 + 1]
                                elif index2 != 0: 
                                    if line2path[index2 - 1].getRightNode() in next_edge1_nodes or line2path[index2 - 1].getLeftNode() in next_edge1_nodes:
                                        next_edge_2 = line2path[index2 - 1]

                                if next_edge_2 != None:
                                    x = model.addVariable(0,1, VariableType.BINARY, 0, f"split crossing from edge {edge.getId()} for lines {line.getId()}, {line2.getId()}")
                                    x_eeeAB[(edge, next_edge_1, next_edge_2, line, line2)] = x

                        if index1 != 0:
                                prev_edge_1: Link = line1path[index1 - 1]

                                prev_edge_1_nodes = [prev_edge_1.getLeftNode(), prev_edge_1.getRightNode()]
                                prev_edge_2 = None
                        
                                if index2 < len(line2path) - 1:
                                    if line2path[index2 + 1].getLeftNode() in prev_edge_1_nodes or line2path[index2 + 1].getRightNode() in prev_edge_1_nodes:
                                        prev_edge_2 = line2path[index2 + 1]
                                if prev_edge_2 == None and index2 != 0: 
                                    if line2path[index2 - 1].getLeftNode() in prev_edge_1_nodes or line2path[index2 - 1].getRightNode() in prev_edge_1_nodes:
                                        prev_edge_2 = line2path[index2 - 1]

                                if prev_edge_2 != None and prev_edge_1 != prev_edge_2:
                                    if (edge, prev_edge_1, prev_edge_2, line, line2) not in x_eeeAB:
                                        x = model.addVariable(0,1, VariableType.BINARY, 0, f"split crossing from edge {edge.getId()} for lines {line.getId()}, {line2.getId()}")
                                        x_eeeAB[(edge, prev_edge_1, prev_edge_2, line, line2)] = x

    logger.debug("Adding constraints")
    for edge in lines_on_edges:
        if len(lines_on_edges[edge]) > 2:
            sum_pairs[edge] = model.createExpression()
            for (line, line2) in line_pairs[edge]:
                sum_pairs[edge].add(x_eAwB[(edge, line, line2)])
            model.addConstraint(sum_pairs[edge], ConstraintSense.LESS_EQUAL, len(line_pairs[edge]) - len(lines_on_edges[edge]) + 1, f"sum pairs on edge {edge.getId()}, lines on edge:{len(lines_on_edges[edge])}")

        for line in lines_on_edges[edge]:
            for p in range(1, len(lines_on_edges[edge]) + 1):
                if p != len(lines_on_edges[edge]):
                    # position constraints, constraint 6 in paper
                    model.addConstraint(x_elp[(edge, line, p)], ConstraintSense.LESS_EQUAL, x_elp[(edge, line, p + 1)],f"x_{edge.getId()},{line.getId()},{p} <= x_{edge.getId()},{line.getId()},{p+1}")
            
            for line2 in lines_on_edges[edge]:
                if line2 != line:
                    # position comparaison constraints (8 and 9 in paper)
                    pos1 = model.createExpression()
                    pos1.add(sum_elp[(edge,line)] - sum_elp[(edge,line2)] + max_lines * x_eAB[(edge, line2, line)])
                    model.addConstraint(pos1, ConstraintSense.GREATER_EQUAL, 0, f"pos comp on edge {edge.getId()} for lines {line.getId()}, {line2.getId()})")
                    
                    pos2 = model.createExpression()
                    pos2.add(x_eAB[(edge, line2, line)] + x_eAB[(edge, line, line2)])
                    model.addConstraint(pos2, ConstraintSense.EQUAL, 1, f"pos comp2 on edge {edge.getId()} for lines {line.getId()}, {line2.getId()}")

                        # line partnering constraints:
                    if (edge, line, line2) in x_eAwB:
                        c1 = model.createExpression()
                        c1.add(sum_elp[(edge,line)] - sum_elp[(edge,line2)] - max_lines * x_eAwB[(edge, line, line2)])
                        model.addConstraint(c1, ConstraintSense.LESS_EQUAL, 1, f"line partner1 {edge.getId()},{line.getId()},{line2.getId()}")
                        
                        c2 = model.createExpression()
                        c2.add(sum_elp[(edge,line2)] - sum_elp[(edge,line)] - max_lines * x_eAwB[(edge, line, line2)])
                        model.addConstraint(c2, ConstraintSense.LESS_EQUAL, 1, f"line partner2 {edge.getId()},{line.getId()},{line2.getId()}")

    for (edge, p) in each_pos_occupied:
            # constraint 7 in paper, makes sure all positions have exactly one line
            model.addConstraint(each_pos_occupied[edge, p], ConstraintSense.EQUAL, p, f"sum x_({edge.getId()},line<={p}) = {p}")

    # crossing constraints (11, 12)
    # find which order of line line2 causes a crossing:
    for (edge, next_edge_1, next_edge_2, line, line2) in x_eeeAB:
        if get_direction(edge, next_edge_1, next_edge_2) == next_edge_1:
            model.addConstraint(x_eAB[((edge, line, line2))], ConstraintSense.LESS_EQUAL, x_eeeAB[(edge, next_edge_1, next_edge_2, line, line2)], f"split crossing const from edge {edge.getId()} for lines {line.getId()}, {line2.getId()}")
        else: 
            model.addConstraint(x_eAB[((edge, line2, line))], ConstraintSense.LESS_EQUAL, x_eeeAB[(edge, next_edge_1, next_edge_2, line, line2)], f"split crossing const from edge {edge.getId()} for lines {line.getId()}, {line2.getId()}")
    
    for (edge,edge2,line,line2) in x_eeAB:
        common_node = None
        edge1_nodes = [edge.getRightNode(), edge.getLeftNode()]
        if edge2.getLeftNode() in edge1_nodes:
            common_node = edge2.getLeftNode()
        else: common_node = edge2.getRightNode()

        if nodes_used[edge] == nodes_used[edge2] or ((nodes_used[edge] != common_node) and (nodes_used[edge2] != common_node)):
            #edges are complicated (positioning is different because of perspective)
            cross1 = model.createExpression()
            cross1.add(x_eAB[(edge, line, line2)] - x_eAB[(edge2, line2, line)] - x_eeAB[(edge, edge2, line, line2)])
            
            cross2 = model.createExpression()
            cross2.add(x_eAB[(edge2, line2, line)] -x_eAB[(edge, line, line2)] - x_eeAB[(edge, edge2, line, line2)])
            
            model.addConstraint(cross1, ConstraintSense.LESS_EQUAL, 0, f"crossing1 on edges {edge.getId()},{edge2.getId()} for lines {line.getId()},{line2.getId()}")
            model.addConstraint(cross2, ConstraintSense.LESS_EQUAL, 0, f"crossing2 on edges {edge.getId()},{edge2.getId()} for lines {line.getId()},{line2.getId()}")

            # add crossing to sum (for objective function, could define some weights for these too)
            sum_crossings.add(x_eeAB[(edge, edge2, line, line2)])
            
            
        else:
            cross1 = model.createExpression()
            cross1.add(x_eAB[(edge, line, line2)] - x_eAB[(edge2, line, line2)] - x_eeAB[(edge, edge2, line, line2)])
            
            cross2 = model.createExpression()
            cross2.add(x_eAB[(edge2, line, line2)] -x_eAB[(edge, line, line2)] - x_eeAB[(edge, edge2, line, line2)])
            
            model.addConstraint(cross1, ConstraintSense.LESS_EQUAL, 0, f"crossing1 on edges {edge.getId()},{edge2.getId()} for lines {line.getId()},{line2.getId()}")
            model.addConstraint(cross2, ConstraintSense.LESS_EQUAL, 0, f"crossing2 on edges {edge.getId()},{edge2.getId()} for lines {line.getId()},{line2.getId()}")

            sum_crossings.add(x_eeAB[(edge, edge2, line, line2)])

        # line separation constraints:
    for (edge, edge2, line, line2) in x_eeAwB:
        if len(lines_on_edges[edge]) > 2 and len(lines_on_edges[edge2]) > 2:
            c1 = model.createExpression()
            c1.add(x_eAwB[(edge, line, line2)] - x_eAwB[(edge2, line, line2)] - x_eeAwB[(edge, edge2, line, line2)])
            model.addConstraint(c1, ConstraintSense.LESS_EQUAL, 0, f"separation constr1 {edge.getId()},{edge2.getId()},{line.getId()},{line2.getId()}")
            
            c2 = model.createExpression()
            c2.add(x_eAwB[(edge2, line, line2)] - x_eAwB[(edge, line, line2)] - x_eeAwB[(edge, edge2, line, line2)])
            model.addConstraint(c2, ConstraintSense.LESS_EQUAL, 0, f"separation constr2 {edge.getId()},{edge2.getId()},{line.getId()},{line2.getId()}")

        elif len(lines_on_edges[edge2]) > 2:
            model.addConstraint(x_eAwB[(edge2, line, line2)] - x_eeAwB[((edge, edge2, line, line2))], ConstraintSense.EQUAL, 0, f"separation {edge.getId()} has only 2 lines")
       
        elif len(lines_on_edges[edge]) > 2:
            model.addConstraint(x_eAwB[(edge, line, line2)] - x_eeAwB[((edge, edge2, line, line2))], ConstraintSense.EQUAL, 0, f"separation {edge.getId()} has only 2 lines")

                
    for (edge, edge1, edge2, line, line2) in x_eeeAB:
        sum_crossings.add(x_eeeAB[(edge, edge1, edge2, line, line2)])

    for (edge, edge2, line, line2) in x_eeAwB:
        sum_separations.add(x_eeAwB[(edge, edge2, line, line2)])

    if merged_lines:
        for edge, line_list in lines_on_edges.items():
            for line1 in line_list:
                if line1.getId() in merged_lines:
                    new_name = merged_lines[line1.getId()]
                    other_lines = [line2 for line2 in line_list if line1.getId() != line2.getId() and line2.getId() in merged_lines and merged_lines[line2.getId()] == new_name]
                    for line2 in other_lines:
                        c1 = model.createExpression()
                        c1.add(sum_elp[(edge,line1)] - sum_elp[(edge,line2)])
                        model.addConstraint(c1, ConstraintSense.LESS_EQUAL, len(other_lines), f"merge con {edge.getId()},{line1.getId()},{line2.getId()}")





    logger.debug("Adding objective")
    model.setObjective(sum_crossings * 2 + sum_separations, OptimizationSense.MINIMIZE)

    logger.debug("Adding parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    model.setIntParam(IntParam.TIMELIMIT, config.getIntegerValue("lpool_opt_visualisation_max_opt_time"))

    logger.debug(f"number of variables: {model.getIntAttribute(IntAttribute.NUM_BIN_VARIABLES)}")
    logger.debug(f"number of constraints: {model.getIntAttribute(IntAttribute.NUM_CONSTRAINTS)}")
    logger.debug(f"max lines: {max_lines}")
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/LineOrderOptimization.lp")
        logger.debug("Finished writing lp file")
    logger.info("Starting optimization")
    model.solve()
    logger.info("Finished optimization")
    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.info("Optimal solution found")
        else:
            gap = model.getDoubleAttribute(DoubleAttribute.MIP_GAP)
            logger.info(f"Feasible solution found")
            if gap > 0.01:
                logger.warning(f"Solution is not optimal. MIP gap: {gap}")
        logger.info("Writing solution")
        positions: Dict[(Link, Line), int] = {}
        for (edge, line, p) in x_elp:
            if (edge, line) in positions:
                positions[(edge, line)] += model.getValue(x_elp[(edge, line, p)])
            else:
                positions[(edge, line)] = model.getValue(x_elp[(edge, line, p)])
        if lc:
            file = config.getStringValue("filename_optimized_line_concept_order")
            header = config.getStringValue("opt_line_concept_order_header")
        else:
            file = config.getStringValue("filename_optimized_line_pool_order")
            header = config.getStringValue("opt_line_pool_order_header")
        order_writer = CsvWriter(file, header)
        for edge in lines_on_edges:
            node1 = edge.getLeftNode()
            node2 = edge.getRightNode()
            for line in lines_on_edges[edge]:
                order_writer.writeLine(
                    [str(node1.getId()), str(node2.getId()), str(line_mod_map[line.getId()]), str(line_id_map[line.getId()]), str(positions[(edge, line)])])
        order_writer.close()
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.info("No feasible solution found")
        model.computeIIS("line-planning/lineOrderOptimization.ilp")
        model.dispose()
        solver.dispose()
        return False


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise ConfigNoFileNameGivenException
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    lc = bool(sys.argv[2] == "true")

    parameters = SolverParameters(config, "lc_")
    logger.info("Begin reading input data")

    lines = {}
    ptns = {}
    if "modalities_all" in config.data:
        all_modalities = config.getStringListValue("modalities_all")
        modalities = []
        for modality in all_modalities:
            if config.getStringValue(add_modality_prefix(modality, "modality_category")) == "line-based":
                modalities.append(modality)
        for modality in modalities:
            ptn = PTNReader.read(read_loads=False, directed=False, modality=modality)
            
            line_pool = LineReader.read(ptn, read_costs=False, read_frequencies=lc, create_directed_lines= not config.getBooleanValue("ptn_is_undirected"), modality=modality)
            if lc:
                lines_mode = line_pool.getLineConcept()
            else:
                lines_mode = line_pool.getLines()
            lines[modality] = lines_mode
            ptns[modality] = ptn
    else:
        ptn = PTNReader.read(read_loads=False, directed=False)
            
        line_pool = LineReader.read(ptn, read_costs=False, read_frequencies=lc, create_directed_lines= not config.getBooleanValue("ptn_is_undirected"))
        if lc:
            lines_mode = line_pool.getLineConcept()
        else:
            lines_mode = line_pool.getLines()
        lines[""] = lines_mode
        ptns[""] = ptn

    logger.info("Finished reading input data")
    logger.info("Start finding line order")
    combined_edges, _ = combine_edges(ptns)

    
    merged_lines_mode = {}
    def process_merged_lines(args: list[str], line_number: int):
        mod = args[0]
        line_id = int(args[1])
        name = args[2]

        merged_lines_mode[(mod, line_id)] = name
    
    if config.getBooleanValue("lpool_opt_visualisation_merge_lines"):
        merged_lines_file = config.getStringValue("filename_merged_lines")
        CsvReader.readCsv(merged_lines_file, process_merged_lines)

    feasible = line_order_optimization(lines, parameters, config, combined_edges, merged_lines_mode, lc)
    if not feasible:
        raise AlgorithmStoppingCriterionException("line order optimization")
    logger.info("Finished finding line order")



  