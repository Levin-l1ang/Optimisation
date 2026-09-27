import logging
import numpy as np
from typing import List, Tuple
from core.model.ptn import Stop
from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, IntAttribute

from InstanceClass import Instance


logger = logging.getLogger(__name__)

def p_best_centers(NodeList: List[Stop], interactions: List[int], p: float) -> List[Stop]:
    """ given a sorted list of nodes, p_best_centers returns the ceil(n*p)-first centers """
    logger.debug("Entered p_best_centers")
    mask = np.zeros((len(interactions)))
    mask[[int(node.getId()-1) for node in NodeList]] = 1
    mask = mask>0
    w_curr = interactions[mask]
    perm = np.argsort((-1)*w_curr)
    Nodes_sorted = []
    for j in perm:
        Nodes_sorted.append(NodeList[j])
    n = len(Nodes_sorted)
    numb_centers = int(np.ceil(n*p))
    Centers = Nodes_sorted[0:numb_centers]
    Centers = Centers + [node for node in NodeList if interactions[int(node.getId()-1)]==interactions[int(Nodes_sorted[int(numb_centers-1)].getId()-1)] and node not in Centers]
    logger.debug("Exiting p_best_centers")
    return Centers

def find_gaps(NodeList: List[Stop], instance: Instance) -> List[Stop]:
    logger.debug("Entered find_gaps")
    mask = np.zeros((len(instance.interactions)))
    mask[[int(node.getId()-1) for node in NodeList]] = 1
    mask = mask>0
    interactions_curr = np.where(mask, instance.interactions, np.ones((instance.interactions.shape[0])) * -np.inf)
    perm = np.argsort((-1)*interactions_curr)
    interactions_curr = interactions_curr[perm]
    interactions_curr = interactions_curr[interactions_curr>-np.inf]
    Nodes_sorted = []
    for j in perm:
        Nodes_sorted.append(instance.ptn.getNode(j+1))
    n = len(Nodes_sorted)
    numb_p_centers = int(np.ceil(n*instance.p_centers))
    diff = interactions_curr[:-1] - interactions_curr[1:]
    cut_idx = np.argmax(diff[max(0, int(np.floor(numb_p_centers - n*0.2))):min(len(diff), int(np.ceil(numb_p_centers + n*0.2)))]) + max(0,int(np.floor(numb_p_centers - n*0.2)))
    Centers_pot = Nodes_sorted[0:cut_idx+1]
    logger.debug("Exiting find_gaps")
    return Centers_pot

def preprocess_centers(instance: Instance) -> List[Stop]:
    """ preprocess_centers returns a list of possible centers (chosen by p_best_centers) and a list of endstations """
    logger.debug("Entered preprocess_centers")
    Endstations = [node for node in instance.Nodes if (instance.Degrees[node]==1)]
    Centers_curr = [node for node in instance.Nodes if (instance.Degrees[node]>=instance.minimal_node_degree_for_center)]
    Centers_pot = find_gaps(Centers_curr, instance)
    instance.Endstations = Endstations
    logger.debug("Exiting preprocess_centers")
    return Centers_pot

def circle_centers(Centers_pot: List[Stop], instance: Instance, cummuliert: bool= False) -> None:
    """ chooses centers from a list of potential centers """
    logger.debug("Entered circle_centers")
    weights = np.zeros((instance.numb_nodes,))                    # List: for each node the weight of the circle around it with radius r
    dist = np.zeros((instance.numb_nodes,instance.numb_nodes))    # Dictionary: For each node a list of the ids of the nodes, lying inside the circle with radius r around it
    dist[instance.distances<=instance.center_radius] = 1
    for curr_center in Centers_pot:
        weights[curr_center.getId()-1] = cummuliert * np.sum(instance.interactions[dist[curr_center.getId()-1,:]>0]) + (1-cummuliert) * instance.interactions[curr_center.getId()-1]

    solver = Solver.createSolver(instance.solver_parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add parameters")
    instance.solver_parameters.setSolverParameters(model)

    logger.debug("Add variables")
    x_var = {}
    k_var = {}
    for i in range(instance.numb_nodes):
        x_var[i] = model.addVariable(var_type=VariableType.BINARY, objective=weights[i], name=f"x_{i}")
        k_var[i] = {}
        for j in range(instance.numb_nodes):
            k_var[i][j] = model.addVariable(var_type=VariableType.BINARY, name=f"k_{i}_{j}")

    logger.debug("Add constraints")

    for i in [node.getId()-1 for node in instance.Nodes if node not in Centers_pot]:
        model.addConstraint(x_var[i], ConstraintSense.EQUAL, 0, name = "NotCenter_"+str(i))
    for v in range(instance.numb_nodes):
        model.addConstraint(model.createExpression().quicksum(k_var[i][v] for i in range(instance.numb_nodes)), ConstraintSense.LESS_EQUAL, 1, name = "Sum_k_"+str(v))
        for u in range(instance.numb_nodes):
            model.addConstraint(k_var[u][v] - dist[u,v]*x_var[u], ConstraintSense.EQUAL, 0, name = "k_equals_z_"+str(v)+str(u))

    model.setSense(OptimizationSense.MAXIMIZE)

    if instance.solver_parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/lpool_center_periphery_circles.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    model.solve()
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")

        for i in range(instance.numb_nodes):
            if int(round(model.getValue(x_var[i]))) == 1:
                instance.Centers = instance.Centers + [instance.Nodes[i]]
        model.dispose()
        solver.dispose()
        logger.debug("Exiting circle_centers")
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("line-planning/lpool_center_periphery_circles_IIS")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()


def sortOD(nodes: List[Stop], instance: Instance) -> List[Tuple[int, Tuple[Stop, Stop]]]:
    """ sorts pairs of nodes by their od-data """
    logger.debug("Entered sortOD")
    w = []
    for i in nodes:
        for j in nodes:
            if j.getId() < i.getId() or instance.directed:
                w.append((instance.od.getValue(i.getId(),j.getId()) + instance.od.getValue(j.getId(),i.getId()), (i, j)))
    def firstEntry(e):
        return e[0]
    w.sort(reverse = True, key = firstEntry)
    logger.debug("Exiting sortOD")
    return w

def choosingPeriphery(instance: Instance) -> None:
    """ returns a list of all nodes classified as Periphery """
    logger.debug("Entered choosingPeriphery")
    mean_dist = 0
    numb_centers = len(instance.Centers)
    for center in instance.Centers:
        mean_dist += np.sum(instance.distances[center.stop_id-1,:])/(instance.numb_nodes - 1)
    mean_dist = mean_dist/numb_centers

    instance.weighted_mean_dist = mean_dist*instance.p_periphery
    # instance.weighted_mean_dist = min(mean_dist*p_periphery, center_radius)                   # for experimental use

    instance.Periphery = list(set(instance.Endstations + [node for node in instance.Nodes if instance.distances[instance.closest_Centers[node][0].getId()-1,node.getId()-1] > instance.weighted_mean_dist]) - set(instance.Centers))
    def distnextcenter(node: Stop) -> float:
        return instance.distances[instance.closest_Centers[node][0].getId()-1,node.getId()-1]
    instance.Periphery.sort(reverse = True, key = distnextcenter)
    if not instance.Periphery:
        logger.warning("No peripheries chosen.")
    logger.debug("Exiting choosingPeriphery")
