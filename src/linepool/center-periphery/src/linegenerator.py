import logging
import numpy as np
from core.model.lines import Line
from core.model.impl.list_path import ListPath
from core.model.ptn import Stop, Link
from core.algorithm.dijkstra import Dijkstra
from typing import Callable, List, Dict

from preprocess import sortOD
from InstanceClass import Instance


logger = logging.getLogger(__name__)


def lineIsNew(line: Line, linepool_rep: Dict[Stop, Dict[Stop,List[Line]]], directed: bool=False) -> bool:
    logger.debug("Entered lineIsNew")
    is_new = True
    start = line.getLinePath().getNodes()[0]
    end = line.getLinePath().getNodes()[-1]
    for old_line in linepool_rep[start][end]:
        if line.compareEdges(old_line):
            is_new = False
            break
    logger.debug(f"Exiting lineIsNew with {is_new}")
    return is_new

def addLineToLinepool(line: Line, instance: Instance) -> None:
    """
    Adds a given line to the linepool if it is a new line. Also updating linepool_rep, covered_nodes and edge_line_dict
    """
    logger.debug("Entered addLineToLinepool")
    if line.getLinePath().getEdges() and lineIsNew(line, instance.linepool_rep, instance.directed):
        instance.addToLinepoolRep(line)
        instance.covered_nodes.update(line.line_path.getNodes())
        instance.linepool.addLine(line)
        length = 0
        for edge in line.getLinePath().getEdges():
            instance.edge_line_dict[edge].append(line)
            length += edge.getLength()
        line.setLength(length)
        instance.idx += 1
        if len(line.getLinePath().getEdges())==1:
            logger.debug("line with only 1 edge added")
            instance.oneEdgeLines += 1
    logger.debug("Exiting addLineToLinepool")


def createLine(idx: int, directed: bool, Path: ListPath) -> Line:
    """
    creates a line with Index idx but with a new path object. directed decides whether the line should be directed or not
    """
    logger.debug("Entered createLine")
    line = Line(idx, directed, line_path = ListPath(directed))
    for e in Path.getEdges():
        line.addLink(e, compute_cost_and_length = True)
    logger.debug("Exiting createLine")
    return line

def concatenateLines(line1: Line, line2: Line, instance: Instance) -> Line:
    """
    Concatenate line1 and line2 if exactly one of its endnodes matches, and return this new line.
    Doesn't check for line direction.
    """
    logger.debug("Entered concatenate lines")
    if len(set(line1.getLinePath().getNodes()).intersection(set(line2.getLinePath().getNodes()))) == 1:
        left1 = line1.getLinePath().getNodes()[0]
        right1 = line1.getLinePath().getNodes()[-1]
        left2 = line2.getLinePath().getNodes()[0]
        right2 = line2.getLinePath().getNodes()[-1]
        if left1 == left2:
            line = createLine(instance.idx, instance.directed, ListPath(instance.directed))
            for e in reversed(line1.getLinePath().getEdges()):
                line.addLink(e)
            for e in line2.getLinePath().getEdges():
                line.addLink(e)
        elif left1 == right2:
            line = createLine(instance.idx, instance.directed, ListPath(instance.directed))
            for e in line2.getLinePath().getEdges() + line1.getLinePath().getEdges():
                line.addLink(e)
        elif right1 == left2:
            line = createLine(instance.idx, instance.directed, ListPath(instance.directed))
            for e in line1.getLinePath().getEdges() + line2.getLinePath().getEdges():
                line.addLink(e)
        elif right1 == right2:
            line = createLine(instance.idx, instance.directed, ListPath(instance.directed))
            for e in line1.getLinePath().getEdges():
                line.addLink(e)
            for e in reversed(line2.getLinePath().getEdges()):
                line.addLink(e)

        logger.debug("Exiting concatenate lines")
        return line
    else:
        logger.debug("Exiting concatenate lines")
        return Line(instance.idx, instance.directed, line_path = ListPath(instance.directed))

### creating-line-functions ###

def createCenterPeripheryLines(instance: Instance, is_postprocessing: bool) -> None:
    """
    Create lines from Periphery-nodes to Center-nodes along all shortest paths between those nodes, but only if the Periphery-node is not yet covered with a line.
    If is_postprocessing, we use shortest paths in the residual ptn and we don't care if a node is already covered or not.
    """
    logger.debug("Entered createCenterPeripheryLines")
    if is_postprocessing:
        Dijkstras = instance.Dijkstras_res
    else:
        Dijkstras = instance.Dijkstras
    for node in instance.Periphery:
        if is_postprocessing or node not in instance.covered_nodes:
            for center in instance.closest_Centers[node]:
                logger.debug(f"Center: {center.getId()}, Peripherie: {node.getId()}")
                for Path in Dijkstras[node].getPaths(center):
                    line = createLine(instance.idx, instance.directed, Path)
                    logger.debug(f"Line-Nodes: {[node.getId() for node in line.getLinePath().getNodes()]}")
                    addLineToLinepool(line, instance)
                # Falls der Gaph gerichtet ist, ergänze für jede Linie die Rückrichtungen.
                if instance.directed:
                    for Path in Dijkstras[center].getPaths(node):
                        line = createLine(instance.idx, instance.directed, Path)
                        addLineToLinepool(line, instance)
    logger.debug("Exiting createCenterPeripheryLines")



def createCenterCenterLines(instance: Instance, is_postprocessing: bool) -> None:
    """
    Create lines between all Center-nodes along all shortest paths between those nodes.
    If is_postprocessing, we use shortest paths in the residual ptn.
    """
    logger.debug("Entered createCenterCenterLines")
    if is_postprocessing:
        Dijkstras = instance.Dijkstras_res
    else:
        Dijkstras = instance.Dijkstras
    for i in range(len(instance.Centers)):
        # Falls der Graph gerichtet ist, sollen auch in die entgegengesetzte Richtung orientierte Linien eingefügt werden.
        if instance.directed:
            for j in range(i-1):
                center1 = instance.Centers[i]
                center2 = instance.Centers[j]
                if center1 != center2:
                    for Path in Dijkstras[center1].getPaths(center2):
                        line = createLine(instance.idx, instance.directed, Path)
                        addLineToLinepool(line, instance)
        for j in range(i+1, len(instance.Centers)):
            center1 = instance.Centers[i]
            center2 = instance.Centers[j]
            if center1 != center2:
                for Path in Dijkstras[center1].getPaths(center2):
                    line = createLine(instance.idx, instance.directed, Path)
                    addLineToLinepool(line, instance)
    logger.debug("Exiting createCenterCenterLines")


def createUncoveredCenterLines(instance: Instance) -> None:
    """
    Create lines from all nodes that are not yet covered by a line to its closest Center along all shortest paths between those nodes.
    """
    logger.debug("Entered createUncoveredCenterLines")
    uncovered_nodes = list(set(instance.Nodes).difference(instance.covered_nodes))
    def distnextcenter(node):
        return instance.distances[instance.closest_Centers[node][0].getId()-1,node.getId()-1]
    uncovered_nodes.sort(reverse =True, key = distnextcenter)
    while uncovered_nodes:
        node = uncovered_nodes.pop(0)
        for center in instance.closest_Centers[node]:
            for Path in instance.Dijkstras[node].getPaths(center):
                line = createLine(instance.idx, instance.directed, Path)
                addLineToLinepool(line, instance)
                uncovered_nodes = list(set(instance.Nodes).difference(instance.covered_nodes)).sort(reverse=True, key = distnextcenter)
            # Falls der Gaph gerichtet ist, ergänze für jede Linie die Rückrichtungen.
            if instance.directed:
                for Path in instance.Dijkstras[center].getPaths(node):
                    line = createLine(instance.idx, instance.directed, Path)
                    addLineToLinepool(line, instance)
                    uncovered_nodes = list(set(instance.Nodes).difference(instance.covered_nodes)).sort(reverse=True, key = distnextcenter)
    logger.debug("Exiting createUncoveredCenterLines")


def createPeripheryPeripheryLines(instance: Instance, is_postprocessing: bool) -> None:
    """
    Create lines between Periphery-nodes along all shortest paths between those nodes if at least mean_od * p_od_periphery people want to travel between those nodes.
    If is_postprocessing, we use shortest paths in the residual ptn.
    """
    logger.debug("Entered createPeripheriePeripherieLines")
    if is_postprocessing:
        Dijkstras = instance.Dijkstras_res
    else:
        Dijkstras = instance.Dijkstras
    for i in range(len(instance.Periphery)):
        # Falls der Graph gerichtet ist, sollen auch Linien für die umgekehrte Richtung ergänzt werden.
        if instance.directed:
            for j in range(i-1):
                # TODO: Gehen eure Sachen eigentlich kaputt, wenn die Stop-IDs nicht konsekutiv sind?
                node1 = instance.Periphery[i]
                node2 = instance.Periphery[j]
                if instance.od.getValue(node1.getId(),node2.getId()) > instance.mean_od*instance.p_od_periphery:
                    for Path in Dijkstras[node1].getPaths(node2):
                        line = createLine(instance.idx, instance.directed, Path)
                        addLineToLinepool(line, instance)
        for j in range(i+1, len(instance.Periphery)):
            node1 = instance.Periphery[i]
            node2 = instance.Periphery[j]
            if instance.od.getValue(node1.getId(),node2.getId()) > instance.mean_od*instance.p_od_periphery:
                for Path in Dijkstras[node1].getPaths(node2):
                    line = createLine(instance.idx, instance.directed, Path)
                    addLineToLinepool(line, instance)
    logger.debug("Exiting createPeripheriePeripherieLines")


def createLinesByOD(instance: Instance) -> None:
    """
    Create Lines by OD by connecting existing lines.
    """
    logger.debug("Entered createLinesByOD")
    candidates = sortOD(instance.Centers + instance.Periphery, instance)
    chosen_tupels = [candidate[1] for candidate in candidates if candidate[0] > instance.mean_od * instance.p_od_lines]
    for (node1, node2) in chosen_tupels:
        for center1 in instance.closest_Centers[node1]:
            for center2 in instance.closest_Centers[node2]:
                if node1 == node2:
                    break
                elif (node1 in instance.Periphery) and (node2 in instance.Periphery):
                    if center1 != center2:
                        for line1 in instance.linepool_rep[node1][center1]:
                            for linec in instance.linepool_rep[center1][center2]:
                                for line2 in instance.linepool_rep[center2][node2]:
                                    line = concatenateLines(line1, concatenateLines(linec, line2, instance), instance)
                                    addLineToLinepool(line, instance)
                    else:
                        for line1 in instance.linepool_rep[node1][center1]:
                            for line2 in instance.linepool_rep[center2][node2]:
                                line = concatenateLines(line1, line2, instance)
                                addLineToLinepool(line, instance)
                elif (node1 in instance.Periphery) and (node2 in instance.Centers):
                    if center1 != node2:
                        for line1 in instance.linepool_rep[node1][center1]:
                            for linec in instance.linepool_rep[center1][node2]:
                                line = concatenateLines(line1, linec, instance)
                                addLineToLinepool(line, instance)
                    # If center1 == node2, then createCenterPeripheryLines has already created lines between those nodes
                elif (node1 in instance.Centers) and (node2 in instance.Periphery):
                    if node1 != center2:
                        for linec in instance.linepool_rep[node1][center2]:
                            for line2 in instance.linepool_rep[center2][node2]:
                                line = concatenateLines(linec, line2, instance)
                                addLineToLinepool(line, instance)
                    # If center1 == node2, then createCenterPeripheryLines has already created lines between those nodes
                # If node1 and node2 are in instance.Centers, then createCenterCenterLines has already created lines between those nodes
    logger.debug("Exiting createLinesByOD")


def findClosestNodes(node: Stop, candidates: List[Stop], distances: np.ndarray) -> List[Stop]:
    """
    Find to node all closest nodes from candidates, distances ebtween nodes given in distances
    """
    logger.debug("Entered findClosestNodes")
    if not candidates:
        logger.debug("No candidates given to find closest nodes. Returning empty list.")
        return []
    mask = np.zeros((distances.shape[0]))
    mask[[candidate.getId()-1 for candidate in candidates]] = 1
    distances_candidates = np.where(mask, distances[node.getId()-1], np.ones((distances.shape[0])) * np.inf)
    indices = np.where(distances_candidates==distances_candidates.min())
    closestNodes = []
    for candidate in candidates:
        if candidate.getId()-1 in indices[0]:
            closestNodes.append(candidate)
    logger.debug("Exiting findClosestNodes")
    return closestNodes

def defCriteriaDetoursCovered(edge_line_dict: Dict[Link,List[Line]]) -> Callable[[list],list]:
    """
    defines the function criteriaDetoursCovered, which returns all specific edges along which detours should be tried
    """
    logger.debug("Entered defCriteriaDetoursCovered")
    def criteriaDetoursCovered(IncidentEdges: list) -> list:
        """
        Returns all sepcific edges contained in IncidentEdges, along which detours should be tried
        """
        logger.debug("Entered criteriaDetoursCovered")
        min_covered = min([len(edge_line_dict[edge]) for edge in IncidentEdges])
        logger.debug(f"min_covered = {min_covered}")
        logger.debug("Exiting criteriaDetoursCovered")
        return [edge for edge in IncidentEdges if len(edge_line_dict[edge])==min_covered]
    logger.debug("Exiting defCriteriaDetoursCovered")
    return criteriaDetoursCovered

def createLinesContainingSpecificEdges(criteria: Callable[[list],list], instance: Instance, is_postprocessing: bool) -> None:
    """
    Create lines containig edges fulfilling criteria given by a function
    """
    logger.debug("Entered createLinesContainingSpecificEdges")
    if is_postprocessing:
        Dijkstras = instance.Dijkstras_res
        distances = instance.distances_res
    else:
        Dijkstras = instance.Dijkstras
        distances = instance.distances
    specific_edges = criteria(instance.Edges)
    logger.info(f"number of specific edges: {len(specific_edges)}")
    for edge in specific_edges:
        logger.debug(f"Cover edge {edge}")
        leftNode = edge.getLeftNode()
        rightNode = edge.getRightNode()
        starts = findClosestNodes(leftNode, instance.Centers, distances) + findClosestNodes(leftNode, instance.Periphery, distances)
        ends = findClosestNodes(rightNode, instance.Centers, distances) + findClosestNodes(rightNode, instance.Periphery, distances)
        for start in starts:
            for end in ends:
                start_leftNode_paths = Dijkstras[start].getPaths(leftNode)
                if not start_leftNode_paths:
                    start_leftNode_paths = [ListPath(instance.directed)]
                for start_leftNode_path in start_leftNode_paths:
                    if not start_leftNode_path.containsNode(rightNode): # If rightNode is not in the shortest path from start to leftNode
                        rightNode_end_paths = Dijkstras[rightNode].getPaths(end)
                        if not rightNode_end_paths:
                            rightNode_end_paths = [ListPath(instance.directed)]
                        for rightNode_end_path in rightNode_end_paths:
                            line = createLine(instance.idx, instance.directed, start_leftNode_path)
                            line.addLink(edge, compute_cost_and_length=True)
                            for right_end_edge in rightNode_end_path.getEdges():
                                if not ((line.line_path.containsNode(right_end_edge.getLeftNode())) and (line.line_path.containsNode(right_end_edge.getRightNode()))):
                                    line.addLink(right_end_edge, compute_cost_and_length=True)
                                else:
                                    addLineToLinepool(line, instance)
                                    line2 = createLine(instance.idx, instance.directed, ListPath(instance.directed))
                                    if rightNode_end_path.getEdges():
                                        for e in reversed(rightNode_end_path.getEdges()):
                                            line2.addLink(e, compute_cost_and_length=True)
                                    if not line2.getLinePath().containsNode(leftNode):
                                        line2.addLink(edge, compute_cost_and_length=True)
                                    else:
                                        addLineToLinepool(line2, instance)
                                        break
                                    if start_leftNode_path.getEdges():
                                        for e in reversed(start_leftNode_path.getEdges()):
                                            if not ((line2.line_path.containsNode(e.getLeftNode())) and (line2.line_path.containsNode(e.getRightNode()))):
                                                line2.addLink(e, compute_cost_and_length=True)
                                            else:
                                                break
                                    addLineToLinepool(line2, instance)
                                    break
                            addLineToLinepool(line, instance)
                    elif start_leftNode_path.containsEdge(edge): # If the edge (rightNode, leftNode) is part of the shortest path from start to leftNode
                        line = createLine(instance.idx, instance.directed, start_leftNode_path)
                        addLineToLinepool(line, instance)
                    elif start != rightNode: # If start != rightNode and the edge (rightNode, leftNode) is not in the shortest path from start to leftNode, but rightNode is.
                        line = createLine(instance.idx, instance.directed, ListPath(instance.directed))
                        for e in start_leftNode_path.getEdges():
                            line.addLink(e, compute_cost_and_length=True)
                            if (e.getLeftNode() == rightNode) or (e.getRightNode() == rightNode):
                                break
                        line.addLink(edge, compute_cost_and_length=True)
                        addLineToLinepool(line, instance)
        if len(instance.edge_line_dict[edge]) == 0:
            line = createLine(instance.idx, instance.directed, ListPath(instance.directed))
            line.addLink(edge, compute_cost_and_length=True)
            addLineToLinepool(line, instance)
    if instance.oneEdgeLines != 0:
        logger.info(f"{instance.oneEdgeLines} edges as lines added")
    logger.debug("Exiting createLinesContainingSpecificEdges")
