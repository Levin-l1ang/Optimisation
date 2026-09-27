import logging

from typing import Dict

from core.model.mm_change_and_go_network import MMCG
from core.util.statistic import Statistic

from core.solver.solver_parameters import SolverParameters
from core.solver.generic_solver_interface import Solver, Variable, VariableType, ConstraintSense, \
                                                Status, IntAttribute, DoubleAttribute, Model, OptimizationSense

from core.model.od import OD
from core.model.lines import Line, LinePool
from core.model.ridepooling import RidepoolingArea, RidepoolingPool
from core.model.ptn import Link, Stop
from core.model.mm_change_and_go import MMCGEdge, MMCGNodeType, MMCGArcType
from core.model.infrastructure_network import InfraNode, InfraLink
from core.model.graph import Graph

from core.exceptions.config_exceptions import ConfigInvalidValueException

logger = logging.getLogger(__name__)

def lines_on_infra_link(lpool: LinePool, links: list[InfraLink]) -> dict[InfraLink,list[Line]]:
    """
    Helper method to get a dictionary returning for each infrastructure link a list of lines using this link

    :param lpool: a line pool
    :type lpool: LinePool
    :param links: a list of infrastructure links
    :type links: list[InfraLink]
    :return: a dictionary storing for each link a list of lines using this link
    :rtype: dict[InfraLink,list[Line]]
    """

    lines_on_link = {}
    for link in links:
        lines_on_link[link] = []
    for line in lpool.getLines():
        for edge in line.getLinePath().getEdges():
            for link in edge.getUnderlyingInfrastructure().getEdges():
                lines_on_link[link].append(line)
    return lines_on_link

def areas_on_infra_link(rpool: RidepoolingPool, links: list[InfraLink]) -> dict[InfraLink, list[RidepoolingArea]]:
    """
    Helper method to get a dictionary returning for each infrastructure link a list of ridepooling areas using this link

    :param rpool: a ridepooling pool
    :type rpool: RidepoolingPool
    :param links: a list of infrastructure links
    :type links: list[InfraLink]
    :return: a dictionary storing for each link a list of ridepooling areas using this link
    :rtype: dict[InfraLink,list[Line]]
    """

    areas_on_link = {}
    for link in links:
        areas_on_link[link] = []
    for area in rpool.getAreas():
        for edge in area.getEdges():
            for link in edge.getUnderlyingInfrastructure().getEdges():
                areas_on_link[link].append(area)
    return areas_on_link


def cgn_arcs_on_infra_link(nons_cgn_edges: list[MMCGEdge], ptn: Graph[Stop, Link], links: list[InfraLink]) -> dict[InfraLink, list[MMCGEdge]]:
    """
    Helper method to get a dictionary returning for each infrastructure link a list of ptn edges of this modality using it.
    """

    edges_on_link = {}
    for link in links:
        edges_on_link[link] = []
    for edge in nons_cgn_edges:
        ptn_edge = ptn.get_edge_by_node_ids(edge.getLeftNode().getStopId(), edge.getRightNode().getStopId())
        for infra_link in ptn_edge.getUnderlyingInfrastructure().getEdges():
            edges_on_link[infra_link].append(edge)
    return edges_on_link


class mm_travel_time_mip:

    def __init__(self,
                 cgn: MMCG,
                 infra_od: OD,
                 budget: float,
                 period_length: int,
                 vehicle_capacities: Dict[str, int],
                 flow_vars_type: str,
                 parameters: SolverParameters,
                 use_start_solution: bool = False,
                 minimize_cost: bool = False,
                 time_budget: float = -1,
                 fixed_concepts: list[str] = [],
                 respect_infrastructure_capacity: bool = False,
                 isn: Graph[InfraNode, InfraLink] = None,
                 infra_capacity_weight: dict[str,float] = {}):

        logger.debug("Initializing multimodal Change&Go MILP")
        self.cgn = cgn
        self.infra_od = infra_od
        self.budget = budget
        self.period_length = period_length
        self.vehicle_capacities = vehicle_capacities
        self.minimize_cost = minimize_cost
        self.time_budget = time_budget
        self.fixed_concepts = fixed_concepts
        self.respect_infrastructure_capacity = respect_infrastructure_capacity
        self.isn = isn
        self.infra_capacity_weight = infra_capacity_weight

        self.solution_time = None
        self.solution_gap = None

        logger.debug("Initializing solver and model")
        self.solver = Solver.createSolver(parameters.getSolverType())
        self.model = self.solver.createModel()
        parameters.setSolverParameters(self.model)
        self.flow_vars_type = flow_vars_type

        logger.debug("Adding variables")

        # FREQUENCY VARIABLES
        logger.debug("... line frequency variables")
        self.frequencies: Dict[str, Dict[Line, Variable]] = {}
        for modality in self.cgn.get_line_modalities():
            self.frequencies[modality] = {}
            for line in self.cgn.get_line_pools()[modality].getLines():
                if self.minimize_cost:
                    var = self.model.addVariable(0, float("inf"),
                                                var_type=VariableType.INTEGER,
                                                objective=line.getCost(),
                                                name=f"frequency_l{line.getId()}_modality{modality}")
                else:
                    var = self.model.addVariable(0, float("inf"),
                                                var_type=VariableType.INTEGER,
                                                objective=0,
                                                name=f"frequency_l{line.getId()}_modality{modality}")
                self.frequencies[modality][line] = var
                if use_start_solution:
                    if line.getFrequency() is not None:
                        self.model.setStartValue(var, line.getFrequency())
        for modality in self.fixed_concepts:
            if self.cgn.modality_categories[modality].lower() == "line-based":
                for line in self.cgn.get_line_pools()[modality].getLines():
                    self.model.setStartValue(self.frequencies[modality][line], line.getFrequency())
                    self.model.addConstraint(lhs=self.frequencies[modality][line],
                                            sense=ConstraintSense.EQUAL,
                                            rhs=line.getFrequency(),
                                            name=f"fixed_concept_modality_{modality}_line_{line.getId()}")

        # RIDEPOOLING VARIABLES
        logger.debug("... number of ridepooling vehicles per area variables")
        self.vehicles: Dict[str, Dict[RidepoolingArea, Variable]] = {}
        for modality in self.cgn.get_ridepooling_modalities():
            self.vehicles[modality] = {}
            for area in self.cgn.get_ridepooling_pools()[modality].getAreas():
                if self.minimize_cost:
                    var = self.model.addVariable(0, float("inf"),
                                                var_type=VariableType.INTEGER,
                                                objective=self.cgn.rpools[modality].getCost(),
                                                name=f"rp_vehicles_area{area.getId()}_modality{modality}")
                else:
                    var = self.model.addVariable(0, float("inf"),
                                                var_type=VariableType.INTEGER,
                                                objective=0,
                                                name=f"rp_vehicles_area{area.getId()}_modality{modality}")
                self.vehicles[modality][area] = var
                if use_start_solution:
                    if area.getNumberOfVehicles() is not None:
                        self.model.setStartValue(var, area.getNumberOfVehicles())
        for modality in self.fixed_concepts:
            if self.cgn.modality_categories[modality].lower() == "ridepooling":
                for area in self.cgn.get_ridepooling_pools()[modality].getAreas():
                    self.model.setStartValue(self.vehicles[modality][area], area.getNumberOfVehicles())
                    self.model.addConstraint(lhs=self.vehicles[modality][area],
                                             sense=ConstraintSense.EQUAL,
                                             rhs=area.getNumberOfVehicles(),
                                             name=f"fixed_concept_modality_{modality}_area_{area.getId()}")

        logger.debug("... vehicle frequency variables")
        self.vehicle_frequencies: Dict[str, Dict[RidepoolingArea, Dict[Link, Variable]]] = {}
        for modality in self.cgn.get_ridepooling_modalities():
            self.vehicle_frequencies[modality] = {}
            for area in self.cgn.get_ridepooling_pools()[modality].getAreas():
                self.vehicle_frequencies[modality][area] = {}
                for arc in self.cgn.get_ridepooling_arcs()[modality][area]:
                    var = self.model.addVariable(0, float("inf"),
                                                 var_type=VariableType.CONTINUOUS,
                                                 objective=0,
                                                 name=f"rp_vehicle_frequency_area{area.getId()}_edge{arc.getId()}_modality{modality}")
                    self.vehicle_frequencies[modality][area][arc] = var


        # PASSENGER FLOW VARIABLES
        logger.debug("... passenger flow variables")
        self.od_pairs_by_origin = self.infra_od.getODPairsByOriginID()
        self.passenger_flows: Dict[int, Dict[MMCGEdge, Variable]] = {}
        for origin in self.od_pairs_by_origin.keys():
            # o_passengers = sum([self.infra_od.getValue(origin, dest.getDestination()) for dest in self.od_pairs_by_origin[origin]])
            self.passenger_flows[origin] = {}
            for arc in self.cgn.get_graph().getEdges():
                if self.flow_vars_type == "continuous":
                    if self.minimize_cost:
                        var = self.model.addVariable(0, float("inf"),
                                                    var_type=VariableType.CONTINUOUS,
                                                    objective=0,
                                                    name=f"passenger_flow_origin{origin}_arc{arc.getId()}")
                    else:
                        var = self.model.addVariable(0, float("inf"),
                                                    var_type=VariableType.CONTINUOUS,
                                                    objective=arc.getCost(),
                                                    name=f"passenger_flow_origin{origin}_arc{arc.getId()}")
                elif self.flow_vars_type == "integer":
                    if self.minimize_cost:
                        var = self.model.addVariable(0, float("inf"),
                                                    var_type=VariableType.INTEGER,
                                                    objective=0,
                                                    name=f"passenger_flow_origin{origin}_arc{arc.getId()}")
                    else:
                        var = self.model.addVariable(0, float("inf"),
                                                    var_type=VariableType.INTEGER,
                                                    objective=arc.getCost(),
                                                    name=f"passenger_flow_origin{origin}_arc{arc.getId()}")
                else:
                    logger.warning(f"Config parameter mm_lc_rc_cg_fow_vars_type cannot be {self.flow_vars_type}, it must be \"integer\" or \"continuous\"")
                    raise ConfigInvalidValueException("mm_lc_rc_cg_fow_vars_type")
                self.passenger_flows[origin][arc] = var



        logger.debug("Adding constraints")

        # PASSENGER FLOW
        logger.debug("... passenger flow constraints")
        # the passengers form a multi commodity flow between the station nodes of
        # the multimodal Change&Go network. The flow is agregated for each origin.
        for origin in self.od_pairs_by_origin.keys():
            o_passengers = sum([self.infra_od.getValue(origin, dest.getDestination()) for dest in self.od_pairs_by_origin[origin]])
            destinations = [pair.getDestination() for pair in self.od_pairs_by_origin[origin]]
            cg_destinations = [self.cgn.get_destination_at_stop(d) for d in destinations]
            for node in self.cgn.get_graph().getNodes():
                incoming_flow = self.model.createExpression()
                for arc in self.cgn.get_graph().getIncomingEdges(node):
                    incoming_flow.add(self.passenger_flows[origin][arc])
                outgoing_flow = self.model.createExpression()
                for arc in self.cgn.get_graph().getOutgoingEdges(node):
                    outgoing_flow.add(self.passenger_flows[origin][arc])
                excess_flow = outgoing_flow - incoming_flow
                rhs: float = 0.0
                if node == self.cgn.get_origin_at_stop(origin):
                    rhs = o_passengers
                elif node in cg_destinations:
                    rhs = - self.infra_od.getValue(origin, node.getStopId())
                if not rhs.is_integer() and self.flow_vars_type == "integer":
                    logger.warning("Cannot use integer flow variables and non-integer OD-data! Will round OD-data!")
                    rhs = round(rhs)
                self.model.addConstraint(lhs=excess_flow,
                                         sense=ConstraintSense.EQUAL,
                                         rhs=rhs,
                                         name=f"flow_origin{origin}_node{node.getStopId()}")



        # LINE FREQUENCY CONSTRAINTS
        logger.debug("... line frequency constraints")
        # the number of passengers using a line arc in the multimodal Change&Go network
        # is bounded by the frequency times the line vehicle capacity
        for modality in self.cgn.get_line_modalities():
            cap = self.vehicle_capacities[modality]
            for line in self.cgn.get_line_pools()[modality].getLines():
                for arc in self.cgn.get_line_arcs()[modality][line]:
                    passengers_on_arc = self.model.createExpression()
                    for origin in self.od_pairs_by_origin.keys():
                        passengers_on_arc.add(self.passenger_flows[origin][arc])
                    arc_cap = self.model.createExpression()
                    arc_cap.multiAdd(cap, self.frequencies[modality][line])
                    self.model.addConstraint(lhs=passengers_on_arc,
                                             sense=ConstraintSense.LESS_EQUAL,
                                             rhs=arc_cap,
                                             name=f"frequency_modality{modality}_line{line.getId()}_arc{arc.getId()}")

        # RIDEPOOLING VEHICLE FREQUENCY CONSTRAINTS
        logger.debug("... ridepooling vehicle frequency constraints")
        # The number of passengers using a ridepooling arc in the Change&Go-Ridepooling network
        # is bounded by the ridepooling vehicle frequency times the ridepooling vehicle capacity
        for modality in self.cgn.get_ridepooling_modalities():
            cap = self.vehicle_capacities[modality]
            for area in self.cgn.get_ridepooling_pools()[modality].getAreas():
                for arc in self.cgn.get_ridepooling_arcs()[modality][area]:
                    passengers_on_arc = self.model.createExpression()
                    for origin in self.od_pairs_by_origin.keys():
                        passengers_on_arc.add(self.passenger_flows[origin][arc])
                    arc_cap = self.model.createExpression()
                    arc_cap.multiAdd(cap, self.vehicle_frequencies[modality][area][arc])
                    self.model.addConstraint(lhs=passengers_on_arc,
                                             sense=ConstraintSense.LESS_EQUAL,
                                             rhs=arc_cap,
                                             name=f"rp_vehicle_frequency_modality{modality}_area{area.getId()}_arc{arc.getId()}")

        # RIDEPOOLING NUMBER OF VEHICLES CONSTRAINTS
        logger.debug("... ridepooling vehicle constraints")
        # the number of vehicles in a ridepooling area depends on the overall passenger distance/travel time
        # in the ridepooling area as determined by the vehicle frequencies
        for modality in self.cgn.get_ridepooling_modalities():
            for area in self.cgn.get_ridepooling_pools()[modality].getAreas():
                vehicle_time = self.model.createExpression()
                for arc in self.cgn.get_ridepooling_arcs()[modality][area]:
                    vehicle_time.multiAdd(arc.getCost(), self.vehicle_frequencies[modality][area][arc])
                available_time = self.model.createExpression()
                available_time.multiAdd(self.period_length, self.vehicles[modality][area])
                self.model.addConstraint(lhs=vehicle_time,
                                         sense=ConstraintSense.LESS_EQUAL,
                                         rhs=available_time,
                                         name=f"rp_vehicles_modality{modality}_area{area.getId()}")

        # INFRASTRUCTURE CAPACITY CONSTRAINTS
        if self.respect_infrastructure_capacity:
            logger.debug("... infrastructure capacity constraints")

            # prepare dictionnaries
            lines_on_link = {}
            for modality in self.cgn.get_line_modalities():
                lines_on_link[modality] = lines_on_infra_link(self.cgn.get_line_pools()[modality], self.isn.getEdges())
            areas_on_link = {}
            for modality in self.cgn.get_ridepooling_modalities():
                areas_on_link[modality] = areas_on_infra_link(self.cgn.get_ridepooling_pools()[modality], self.isn.getEdges())
            nons_cgn_arcs_on_link = {}
            for modality in self.cgn.get_nonscheduled_modalities():
                nons_cgn_arcs_on_link[modality] = cgn_arcs_on_infra_link(self.cgn.get_nonscheduled_arcs()[modality], self.cgn.ptns[modality], self.isn.getEdges())
            rp_cgn_arcs_on_link = {}
            for modality in self.cgn.get_ridepooling_modalities():
                rp_cgn_arcs_on_link[modality] = {}
                for area in self.cgn.get_ridepooling_pools()[modality].getAreas():
                    rp_cgn_arcs_on_link[modality][area] = cgn_arcs_on_infra_link(self.cgn.get_ridepooling_arcs()[modality][area], self.cgn.ptns[modality], self.isn.getEdges())
            for link in self.isn.getEdges():
                self.model.addConstraint(
                            self.model.createExpression().quicksum(self.infra_capacity_weight[modality]*self.frequencies[modality][line] for modality in self.cgn.get_line_modalities() for line in lines_on_link[modality][link]) +
                            self.model.createExpression().quicksum(self.vehicle_frequencies[modality][area][arc]*self.infra_capacity_weight[modality] for modality in self.cgn.get_ridepooling_modalities() for area in areas_on_link[modality][link] for arc in rp_cgn_arcs_on_link[modality][area][link])+
                            self.model.createExpression().quicksum(self.passenger_flows[origin][arc]*self.infra_capacity_weight[modality] for origin in self.od_pairs_by_origin.keys() for modality in self.cgn.get_nonscheduled_modalities() for arc in nons_cgn_arcs_on_link[modality][link]),
                            ConstraintSense.LESS_EQUAL, link.getCapacity(),f"infra_capacity_{link.getId()}")

        # BUDGET CONSTRAINT
        logger.debug("... budget constraint")
        # the travel time is minimized in the objective function, the overall cost is
        # bounded by a budget (budget=-1 means there is no budget)
        if self.minimize_cost:
            if self.time_budget != -1:
                time = self.model.createExpression()
                for origin in self.od_pairs_by_origin.keys():
                    for arc in self.cgn.get_graph().getEdges():
                        t = arc.getCost()
                        time.multiAdd(t, self.passenger_flows[origin][arc])
                self.model.addConstraint(lhs=time,
                                         sense=ConstraintSense.LESS_EQUAL,
                                         rhs=self.time_budget,
                                         name="time_budget_constraint")
        else:
            if self.budget != -1:
                line_cost = self.model.createExpression()
                for modality in self.cgn.line_modalities:
                    for line in self.cgn.lpools[modality].getLines():
                        line_cost.multiAdd(line.getCost(), self.frequencies[modality][line])
                ridepooling_cost = self.model.createExpression()
                for modality in self.cgn.rpool_modalities:
                    for area in self.cgn.rpools[modality].getAreas():
                        ridepooling_cost.multiAdd(self.cgn.rpools[modality].getCost(), self.vehicles[modality][area])
                overall_cost = line_cost + ridepooling_cost
                self.model.addConstraint(lhs=overall_cost,
                                        sense=ConstraintSense.LESS_EQUAL,
                                        rhs=self.budget,
                                        name="budget_constraint")
        self.model.setSense(OptimizationSense.MINIMIZE)

    def getOriginalModel(self) -> Model:
        return self.model

    def solve(self):
        self.model.solve()
        self.solution_time = self.model.getOriginalModel().Runtime
        self.solution_gap = self.model.getOriginalModel().MIPGap

    def getStatus(self) -> Status:
        return self.model.getStatus()

    def computeIIS(self, filename: str):
        self.model.computeIIS(filename)

    def write(self, filename: str):
        self.model.write(filename)

    def getValue(self, var: Variable):
        return self.model.getValue(var)

    def get_vehicle_distribution(self, modality: str, area: RidepoolingArea, edge: Link) -> float:
        left_node = edge.getLeftNode().getId()
        right_node = edge.getRightNode().getId()
        for arc in self.cgn.get_ridepooling_arcs()[modality][area]:
            left = arc.getLeftNode().getStopId()
            right = arc.getRightNode().getStopId()
            if edge.isDirected():
                if left == left_node and right == right_node:
                    # we have to divide by the number of vehicles to get the real vehicle frequencies ("alpha") as in lprp-alpha model
                    return self.getValue(self.vehicle_frequencies[modality][area][arc])/area.getNumberOfVehicles()
            else:
                if set([left, right]) == set([left_node, right_node]):
                    # we have to divide by the number of vehicles to get the real vehicle frequencies ("alpha") as in lprp-alpha model
                    if area.getNumberOfVehicles() == 0:
                        return 0
                    else:
                        return self.getValue(self.vehicle_frequencies[modality][area][arc])/area.getNumberOfVehicles()

    def compute_ptn_loads(self):
        """
        derives the loads on infra links and all ptn edges from the computed routing.
        """
        # reset all loads to zero
        for modality in self.cgn.modalities:
            for edge in self.cgn.ptns[modality].getEdges():
                edge.setLoad(0)
        if self.respect_infrastructure_capacity:
            for link in self.isn.getEdges():
                link.setLoad(0)

        # Be aware: Load in an undirected graph is the maximum of the load in both directions
        # Thus: first store all loads for directed case in dictionary and then set the load in the undirected case to the maximum of both directions

        load = {}
        for modality in self.cgn.modalities:
            load[modality] = {}
            if self.cgn.ptns[modality].isDirected():
                for edge in self.cgn.ptns[modality].getEdges():
                    load[modality][edge.getLeftNode().getId(), edge.getRightNode().getId()] = 0
            else:
                for edge in self.cgn.ptns[modality].getEdges():
                    load[modality][edge.getLeftNode().getId(), edge.getRightNode().getId()] = 0
                    load[modality][edge.getRightNode().getId(), edge.getLeftNode().getId()] = 0

        if self.respect_infrastructure_capacity:
            infra_load = {}
            for link in self.isn.getEdges():
                infra_load[link.getLeftNode().getId(), link.getRightNode().getId()] = 0
                infra_load[link.getRightNode().getId(), link.getLeftNode().getId()] = 0

        # now compute the loads on the ptn edges and infra links
        for origin in self.od_pairs_by_origin.keys():
            for arc in self.cgn.get_graph().getEdges():
                if arc.getType() == MMCGArcType.LINE or arc.getType() == MMCGArcType.RIDEPOOLING or arc.getType() == MMCGArcType.NONSCHEDULED:
                    # other arc types do not contribute to loads
                    modality = arc.getLeftNode().getModality()
                    load_val = self.model.getValue(self.passenger_flows[origin][arc])
                    if self.flow_vars_type == "integer":
                        load_val = int(round(load_val))

                    load[modality][arc.getLeftNode().getStopId(), arc.getRightNode().getStopId()] += load_val

                    if self.respect_infrastructure_capacity:
                        ptn_edge = self.cgn.ptns[modality].get_edge_by_node_ids(arc.getLeftNode().getStopId(), arc.getRightNode().getStopId())
                        infra_path = ptn_edge.getUnderlyingInfrastructure()
                        node_list = infra_path.getNodes()

                        # check, if path starts at the left node of the cgn arc, if not, reverse the path (might happen in undirected PTN case)
                        if node_list[0].getId() != arc.getLeftNode().getStopId():
                            node_list.reverse()
                        for idx in range(len(node_list)-1):
                            infra_load[node_list[idx].getId(), node_list[idx+1].getId()] += load_val

        # case distinction: directed or undirected PTN to set the actual loads
        for modality in self.cgn.modalities:
            ptn = self.cgn.ptns[modality]
            for edge in ptn.getEdges():
                if ptn.isDirected():
                    edge.setLoad(load[modality][edge.getLeftNode().getId(), edge.getRightNode().getId()])
                else:
                    edge.setLoad(max(load[modality][edge.getLeftNode().getId(), edge.getRightNode().getId()],
                                     load[modality][edge.getRightNode().getId(), edge.getLeftNode().getId()]))
        if self.respect_infrastructure_capacity:
            for link in self.isn.getEdges():
                link.setLoad(max(infra_load[link.getLeftNode().getId(), link.getRightNode().getId()],
                                 infra_load[link.getRightNode().getId(), link.getLeftNode().getId()]))


    def compute_overall_times(self):
        # prepare variables/dicts for saving travel times
        overall_time = 0
        overall_travel_time = 0
        overall_transfer_time = 0
        # compute the travel times
        for edge in self.cgn.get_graph().getEdges():
            # count the passengers on the arc
            passengers_on_arc = 0
            for origin in self.od_pairs_by_origin.keys():
                passengers_on_arc += self.getValue(self.passenger_flows[origin][edge])
            # compute the overall time
            time_on_arc = passengers_on_arc*edge.getCost()
            # add to the overall times
            overall_time += time_on_arc
            # driving
            if edge.getType() == MMCGArcType.LINE or edge.getType() == MMCGArcType.RIDEPOOLING or edge.getType() == MMCGArcType.NONSCHEDULED:
                overall_travel_time += time_on_arc
            # transfers modes
            elif edge.getType() == MMCGArcType.TRANSFER or edge.getType() == MMCGArcType.MODE_TRANSFER:
                overall_transfer_time += time_on_arc
            else:
                logger.warning(f"Unexpected edge type in MCGN: edge {edge.getId()} has type {edge.getType()}")
        return overall_time, overall_travel_time, overall_transfer_time

    def compute_mode_times(self, modality: str):
        """ for the given node compute the overall time, travel time and transfer time in this mode"""
        overall_time = 0
        travel_time = 0
        transfer_time = 0
        for arc in self.cgn.get_modality_arcs(modality):
            passengers = 0
            for origin in self.od_pairs_by_origin.keys():
                passengers += self.getValue(self.passenger_flows[origin][arc])
            t = passengers* arc.getCost()
            overall_time += t
            if arc.getType() in [MMCGArcType.LINE, MMCGArcType.RIDEPOOLING, MMCGArcType.NONSCHEDULED]:
                travel_time += t
            elif arc.getType() == MMCGArcType.TRANSFER:
                transfer_time += t
            else:
                logger.warning(f"Unexpected MCGArcType in MCG network: Edge {arc.getId()} of mode {modality} has type {arc.getType()}")
        return overall_time, travel_time, transfer_time

    def get_travel_time_statistics(self):
        # initialize travel time statistic
        statistic = Statistic()
        # overall travel times
        o1, o2, o3 = self.compute_overall_times()
        statistic.setValue("_overall_passenger_time", o1)
        statistic.setValue("_overall_passenger_travel_time", o2)
        statistic.setValue("_overall_passenger_transfer_time", o3)
        # times for each mode
        for modality in self.cgn.modalities:
            m1, m2, m3 = self.compute_mode_times(modality)
            statistic.setValue(f"{modality}.overall_passenger_time", m1)
            statistic.setValue(f"{modality}.overall_passenger_travel_time", m2)
            statistic.setValue(f"{modality}.overall_passenger_transfer_time", m3)
        return statistic

    def compute_mode_cost(self, modality: str):
        category = self.cgn.modality_categories[modality]
        if category.lower() == "line-based":
            lpool = self.cgn.get_line_pools()[modality]
            C = 0
            for line in lpool.getLines():
                c = line.getCost()
                f = int(round(self.getValue(self.frequencies[modality][line])))
                C += c*f
            return C
        elif category.lower() == "ridepooling":
            rpool = self.cgn.get_ridepooling_pools()[modality]
            C = 0
            c = rpool.getCost()
            for area in rpool.getAreas():
                v = self.getValue(self.vehicles[modality][area])
                C += c*v
            return C
        elif category.lower() == "nonscheduled":
            return 0
        else:
            logger.warning(f"Unexpected: modality {modality} has category {category}")

    def get_cost_statistic(self):
        statistic = Statistic()
        overall_cost = 0
        for modality in self.cgn.modalities:
            modality_cost = self.compute_mode_cost(modality)
            statistic.setValue(f"{modality}.operating_cost", modality_cost)
            overall_cost += modality_cost
        statistic.setValue("_operating_cost", overall_cost)
        statistic.setValue("_cost_budget", self.budget)
        return statistic

    def getDoubleAttribute(self, attr: DoubleAttribute) -> float:
        return self.model.getDoubleAttribute(attr)

    def getIntAttribute(self, attr: IntAttribute) -> int:
        return self.model.getIntAttribute(attr)

    def getNumberOfSolutions(self) -> int:
        return self.model.getIntAttribute(IntAttribute.NUM_SOLUTIONS)

    def getObjectiveValue(self) -> float:
        return self.model.getDoubleAttribute(DoubleAttribute.OBJ_VAL)

    def statusIsOptimal(self) -> bool:
        return (self.getStatus() == Status.OPTIMAL)

    def statusIsInfeasible(self) -> bool:
        return (self.getStatus() == Status.INFEASIBLE)

    def dispose(self):
        self.model.dispose()
        self.solver.dispose()