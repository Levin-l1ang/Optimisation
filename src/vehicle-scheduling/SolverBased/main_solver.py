import logging
import sys

from single_commodity_model import single_commodity_model
from mdm import mdm1,mdm2
from canal_model import canal
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.trip import TripReader
from core.io.vehicleSchedule import VehicleScheduleWriter
from core.model.vehicle_scheduling import VehicleSchedule, VehicleTour, Circulation, Trip, TripType
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.solver.solver_parameters import SolverParameters
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, IntAttribute
from core.algorithm.dijkstra import Dijkstra
from core.io.depot import DepotReader, DepotWriter
from core.model.vehicle_scheduling import Depot
from core.util.config import Config

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    vehicle_cost = config.getDoubleValue("vs_vehicle_costs")
    time_empty_meters_cost = config.getDoubleValue("vs_eval_cost_factor_empty_trips_duration")/3600 # the costs are given per empty driven hour, but we need them to be per second
    length_empty_meters_cost = config.getDoubleValue("vs_eval_cost_factor_empty_trips_length")
    vehicle_speed_level = config.getStringValue("vs_vehicle_speed_level")
    vehicle_speed = config.getDoubleValue("gen_vehicle_speed")/3600 # we consider the speed to be in "length unit/h", but need seconds in the divisor
    missing_trip_penalty = config.getDoubleValue("vs_penalty_costs")
    turn_over_time = config.getDoubleValue("vs_turn_over_time")
    time_units_per_minute = config.getDoubleValue("time_units_per_minute")
    model = config.getStringValue("vs_model")
    parameters = SolverParameters(config, "vs_")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(config=config)
    trip_list = TripReader.read(config=config)
    stop_number = len(ptn.getNodes())
    stop_list = ptn.getNodes()
    if model == "SINGLE_COMMODITY_MODEL":  # only read depots if necessary for the model
        depots = DepotReader.read(config=config)
    logger.info("Finished reading input data")

    logger.info("Begin calculating the stop distances")
    # decide for each ptn edge how much time should be assigned to it
    if (vehicle_speed_level[0] in ["f","F"]
        and vehicle_speed_level[1] in ["a","A"]
        and vehicle_speed_level[2] in ["s","S"]
        and vehicle_speed_level[3] in ["t","T"]):
        edge_time = lambda link: link.getLowerBound() / time_units_per_minute * 60  # we assume the edge lengths given in time units per minute and need to convert them to seconds
    elif (vehicle_speed_level[0] in ["s","S"]
        and vehicle_speed_level[1] in ["l","L"]
        and vehicle_speed_level[2] in ["o","O"]
        and vehicle_speed_level[3] in ["w","W"]):
        edge_time = lambda link: link.getUpperBound() / time_units_per_minute * 60
    else:
        edge_time = lambda link: (link.getLowerBound() + link.getUpperBound() / 2) /time_units_per_minute * 60

    stop_distances = {}
    for startNode in ptn.getNodes():  # calculate the distances between stops/depots
        time_dijkstra = Dijkstra(ptn, startNode, edge_time)
        for endNode in ptn.getNodes():
            time_dijkstra.computeShortestPath(endNode)
            time_distance = time_dijkstra.getDistance(endNode)
            path = time_dijkstra.getPath(endNode)
            length_distance = (sum([edge.getLength() for edge in path.getEdges()]) if path!=None else 0)
            stop_distances[(startNode.getId(), endNode.getId())] = {
                "time": time_distance,
                "length": length_distance
            }
    if model == "SINGLE_COMMODITY_MODEL":  # only add distances to the depots if the model actually needs depots
        for g, depot in enumerate(depots):
            nearestStop = depot.getNearestStop(ptn)
            for stop in ptn.getNodes():  # we assign to a depot the distances from its nearest stop
                time_depot_travel_dist = stop_distances[(stop.getId(), nearestStop.getId())]["time"] + depot.getDistance(nearestStop)/vehicle_speed  # we calculate the euclidean distance from the depot to its nearest stop, from this stop we use distances in the ptn.
                length_depot_travel_dist = stop_distances[(stop.getId(), nearestStop.getId())]["length"] + depot.getDistance(nearestStop)
                stop_distances[(stop.getId(), g + 1 + len(ptn.getNodes()))] = {
                    "time": time_depot_travel_dist,
                    "length": length_depot_travel_dist
                }
                stop_distances[(g + 1 + len(ptn.getNodes()), stop.getId())] = {
                    "time": time_depot_travel_dist,
                    "length": length_depot_travel_dist
                }
    logger.info("Finished calculating the stop distances")

    logger.info("Begin computing: "+model)
    if model == "SINGLE_COMMODITY_MODEL":
        vehicle_schedule = single_commodity_model(
            depots,
            trip_list,
            stop_distances,
            parameters,
            stop_number,
            vehicle_cost,
            time_empty_meters_cost,
            length_empty_meters_cost,
            turn_over_time)
    elif model == "MDM1":
        vehicle_schedule = mdm1(
            trip_list,
            parameters,
            turn_over_time)
    elif model == "MDM2":
        vehicle_schedule = mdm2(
            trip_list,
            parameters,
            turn_over_time)
    elif model == "CANAL_MODEL":
        vehicle_schedule = canal(
            trip_list,
            parameters,
            stop_list,
            stop_distances,
            turn_over_time,
            vehicle_cost,
            time_empty_meters_cost,
            length_empty_meters_cost)
    logger.info("Finished computing: "+model)

    logger.info("Begin writing output data")
    VehicleScheduleWriter.write(vehicle_schedule)
    logger.info("Finished writing output data")
