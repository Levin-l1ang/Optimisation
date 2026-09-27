import logging
import sys
import numpy as np

import linegenerator, evaluate, lengths_and_costs, preprocess, residual, visualization
from InstanceClass import Instance
from typing import Callable, Tuple
from core.util.config import Config

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineWriter
from core.model.ptn import Link

from core.solver.solver_parameters import SolverParameters
import cost





logger = logging.getLogger(__name__)

def init(logger) -> Tuple[Config, Instance]:
    """
    Read config and initialise instance
    """
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()

    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    logger.info("Finished reading configuration")

    instance = Instance(config)

    logger.info("Start solving APSPP")
    instance.computeAPSPP()
    logger.info("APSPP successfully solved")

    return config, instance

def main(
    logger,
    config: Config, instance: Instance,
    dist: Callable[[Link], float] = lengths_and_costs.dist
    ) -> None:


    logger.debug(f"percentage of nodes usabel as centers: {instance.p_centers}")
    logger.debug(f"percentage of mean distance to determine peripheries: {instance.p_periphery}")
    logger.debug(f"percentage of OD-data used to create periphery-periphery-lines: {instance.p_od_periphery}")
    logger.debug(f"percentage of mean distance as minimum center-center-distance: {instance.p_center_radius}")
    logger.debug(f"percentage of OD-Data used in residual network for OD-lines: {instance.p_od_lines}")
    logger.debug(f"minimal node degree for center nodes: {instance.minimal_node_degree_for_center}")


#################################### Linepool #################################################################################################

    logger.info("Begin computing linepool")

#################################### Centers and Periphery ####################################################################################
    logger.info("Choosing Centers")

    instance.calculate_interactions()

    Centers_pot = preprocess.preprocess_centers(instance)

    preprocess.circle_centers(
        Centers_pot,
        instance,
        True
    )

    if len(instance.Centers) == 0:
        raise AttributeError("No centers were chosen! Probably there is a problem with Gurobi. See Gruobi-Log for more details.")

    logger.info(f"{len(instance.Centers)} Centers successfully chosen")

    logger.info("Choosing periphery")

    instance.setClosestCenters()
    preprocess.choosingPeriphery(instance)

    logger.info("Periphery successfully chosen")
###############################################################################################################################################

    logger.debug(f"Endstations: {[station.getId() for station in instance.Endstations]}")
    logger.debug(f"Centers: {[center.getId() for center in instance.Centers]}")
    logger.debug(f"Periphery: {list(np.sort([peripherie.getId() for peripherie in instance.Periphery]))}")
    logger.debug(f"node weights: {instance.interactions}")
    logger.debug(f"weighted_mean_dist: {instance.weighted_mean_dist}")

    # If shortest paths w.r.t. costs should be used, switch here to cost model
    if instance.opt_cost:
        instance.distances = instance.distances_cost
        instance.Dijkstras = instance.Dijkstras_cost

#################################### Create Lines #############################################################################################
    logger.info("Creating lines")

### Center - Periphery ###
    linegenerator.createCenterPeripheryLines(instance, False)

### Center - Center ###
    linegenerator.createCenterCenterLines(instance, False)

### Periphery - Periphery ###
    linegenerator.createPeripheryPeripheryLines(instance, False)

### Concatenate Lines
    linegenerator.createLinesByOD(instance)


    logger.info(f"{instance.idx} lines created")

### Detours

    idx_before_detours = instance.idx
    criteria = linegenerator.defCriteriaDetoursCovered(instance.edge_line_dict)
    incidence_edges = set()
    for node in instance.Periphery + instance.Centers:
        incidence_edges.update(instance.ptn.getIncidentEdges(node))
    incidence_edges = list(incidence_edges)

    linegenerator.createLinesContainingSpecificEdges(criteria, instance, False)

    logger.info(f"{instance.idx - idx_before_detours} new lines created after detours")

### UncoveredNodes - Center ###
    idx_before_uncovered = instance.idx
    linegenerator.createUncoveredCenterLines(instance)

    logger.info(f"{instance.idx - idx_before_uncovered} new lines created for uncovered nodes")

    logger.info(f"{instance.idx} lines successfully created in total")
###############################################################################################################################################





#################################### Postprocessing ###########################################################################################

    logger.info("Starting postprocessing")

    if False:
        logger.debug("Check if solution is feasible")
        solver_parameters = SolverParameters(config, "lc_")
        feasible = cost.solve_lineplanning_problem(instance.ptn, instance.linepool, solver_parameters, False)
        logger.debug(f"Solution is feasible: {feasible}")
        instance.feasibility_before = feasible

#################################### Iterated Postprocessing ##################################################################################

    ########## Frequency based ###########################

    logger.info("Postprocessing: Frequency based")

    iter = 1
    covered = False

    while iter <= instance.max_iter and (instance.min_times_edge_covered >= residual.computeCoveredTimesFrequency(instance)):

        idx_before = instance.idx

        ### update residual network ###
        residual.updateResidualNetwork(
            instance,
            covered,
            dist
        )

        ### choose new lines from residual network (same principle as first step) ###

        instance.covered_nodes = set()

        linegenerator.createCenterPeripheryLines(instance, True)
        linegenerator.createCenterCenterLines(instance, True)
        linegenerator.createPeripheryPeripheryLines(instance, True)

        if instance.idx - idx_before == 0:
            logger.info(f"no new lines along shortest paths created in iteration {iter}, trying detours")
            criteriaDetoursCovered = linegenerator.defCriteriaDetoursCovered(instance.edge_line_dict)
            linegenerator.createLinesContainingSpecificEdges(criteriaDetoursCovered, instance, True)
            if instance.idx - idx_before == 0:
                logger.info(f"no new lines created in iteration {iter}")
                break

        logger.info(f"Created {instance.idx-idx_before} additional lines in iteration {iter}")
        iter += 1

    ########## Covered based ###########################

    iter = 1
    covered = True

    logger.info("Postprocessing: Covered based")

    while iter <= instance.max_iter and (instance.min_times_edge_covered >= residual.computeCoveredTimesFrequency(instance)):

        idx_before = instance.idx

        ### update residual network ###
        residual.updateResidualNetwork(
            instance,
            covered,
            dist
        )

        ### choose new lines from residual network (same principle as first step) ###

        instance.covered_nodes = set()

        linegenerator.createCenterPeripheryLines(instance, True)
        linegenerator.createCenterCenterLines(instance, True)
        linegenerator.createPeripheryPeripheryLines(instance, True)

        if instance.idx - idx_before == 0:
            logger.info(f"no new lines along shortest paths created in iteration {iter}, trying detours")
            criteriaDetoursCovered = linegenerator.defCriteriaDetoursCovered(instance.edge_line_dict)
            linegenerator.createLinesContainingSpecificEdges(criteriaDetoursCovered, instance, True)
            if instance.idx - idx_before == 0:
                logger.info(f"no new lines created in iteration {iter}")
                break

        logger.info(f"Created {instance.idx-idx_before} additional lines in iteration {iter}")
        iter += 1

    logger.info("Postprocessing finished")

    logger.info(f"Linepool consists of {instance.idx} lines in total")

    logger.info("Finished computing linepool")


###############################################################################################################################################



#################################### Check feasibility ########################################################################################

    logger.info("Check if solution is feasible")
    solver_parameters = SolverParameters(config, "lc_")
    feasible = cost.solve_lineplanning_problem(instance.ptn, instance.linepool, solver_parameters, False)
    logger.info(f"Solution is feasible: {feasible}")
    instance.feasibility_after = feasible

###############################################################################################################################################


#################################### Plotting #################################################################################################

    if instance.plot_centers:
        logger.info("Plotting PTN")
        path = f"./graphics/pc_ptn_{instance.p_center_radius}km_minNodeDegree_{instance.minimal_node_degree_for_center}.png"
        visualization.draw_ptn(instance,path = path)
        logger.info(f"Finished plotting, file saved to {path}")

        for center in instance.Centers:
            logger.info(f"Plotting single center {center.getId()}")

            path = f"./graphics/pc_ptn_{instance.p_center_radius}km_minNodeDegree_{instance.minimal_node_degree_for_center}_center{str(center.getId())}.png"

            visualization.draw_circle(instance, center, path = path)

            logger.info(f"Finished plotting, file saved to {path}")


###############################################################################################################################################


    lengths_and_costs.set_cost_for_pool(instance.linepool, instance.cost_fixed, instance.cost_length, instance.cost_edges)

#################################### Evaluation ###############################################################################################

    evaluate_linepool = False
    evaluate_path = ""
    if evaluate_linepool:
        logger.info("Start Evaluation")
        evaluate.evaluateLinepool(
            evaluate_path,
            instance
        )
        logger.info("End Evaluation")

###############################################################################################################################################


#################################### Write Output #############################################################################################

    logger.info("Begin writing output data")
    LineWriter.write(instance.linepool, write_line_concept=False)
    logger.info("Finished writing output data")


###############################################################################################################################################
###############################################################################################################################################
###############################################################################################################################################


if __name__ == '__main__':
    config, instance = init(logger)

    if instance.opt_cost:
        func = instance.cost
    else:
        func = lengths_and_costs.dist

    main(
        logger,
        config, instance,
        func
    )

###############################################################################################################################################
###############################################################################################################################################
###############################################################################################################################################

