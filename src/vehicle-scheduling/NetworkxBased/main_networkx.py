import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.depot_exceptions import OnlyOneDepotException
from assignment_model import assignment_model
from transportation_model import transportation_model
from network_flow_model import network_flow_model
from core.io.trip import TripReader
from core.io.vehicleSchedule import VehicleScheduleWriter
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.algorithm.dijkstra import Dijkstra
from core.io.depot import DepotReader

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    vehicle_cost = config.getDoubleValue("vs_vehicle_costs")
    time_empty_meters_cost = config.getDoubleValue("vs_eval_cost_factor_empty_trips_duration")/3600 # the costs are given per empty driving hour, but we need seconds
    length_empty_meters_cost = config.getDoubleValue("vs_eval_cost_factor_empty_trips_length")
    vehicle_speed_level = config.getStringValue("vs_vehicle_speed_level")
    missing_trip_penalty = config.getDoubleValue("vs_penalty_costs")
    model = config.getStringValue("vs_model")
    depot_file = config.getStringValue("filename_depot_file")
    vehicle_speed = config.getDoubleValue("gen_vehicle_speed")/3600 # we consider the speed to be in "length unit/h", but need seconds in the divisor
    rounding_places = config.getIntegerValue("vs_distances_rounding_places")
    turn_over_time = config.getDoubleValue("vs_turn_over_time")
    time_units_per_minute = config.getDoubleValue("time_units_per_minute")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn = PTNReader.read(config=config)
    trip_list = TripReader.read(config=config)
    stop_number = len(ptn.getNodes())
    if model == "TRANSPORTATION_MODEL" or model == "NETWORK_FLOW_MODEL":  # only read depots if necessary for the model
        depots = DepotReader.read(config=config)
        stop_number +=1
        if len(depots)>1:
            raise OnlyOneDepotException(depot_file)
        number_of_vehicles = depots[0].getNumberOfVehicles()
        depot_index = depots[0].getId()
    logger.info("Finished reading input data")

    logger.info("Begin calculating the stop distances")
    # decide for each ptn edge how much time should be assigned to it
    if (vehicle_speed_level[0] in ["f", "F"]
        and vehicle_speed_level[1] in ["a", "A"]
        and vehicle_speed_level[2] in ["s", "S"]
        and vehicle_speed_level[3] in ["t", "T"]):
        edge_time = lambda \
            link: link.getLowerBound() / time_units_per_minute * 60  # we assume the edge lengths given in time units per minute and need to convert them to seconds
    elif (vehicle_speed_level[0] in ["s", "S"]
          and vehicle_speed_level[1] in ["l", "L"]
          and vehicle_speed_level[2] in ["o", "O"]
          and vehicle_speed_level[3] in ["w", "W"]):
        edge_time = lambda link: link.getUpperBound() / time_units_per_minute * 60
    else:
        edge_time = lambda link: (link.getLowerBound() + link.getUpperBound() / 2) / time_units_per_minute * 60

    stop_distances = {}
    for startNode in ptn.getNodes():  # calculate the distances between stops/depots
        dijkstra = Dijkstra(ptn, startNode, edge_time)
        for endNode in ptn.getNodes():
            dijkstra.computeShortestPath(endNode)
            time_distance = dijkstra.getDistance(endNode)
            path = dijkstra.getPath(endNode)
            length_distance = (sum([edge.getLength() for edge in path.getEdges()]) if path != None else 0)
            stop_distances[(startNode.getId(), endNode.getId())] = {
                "time": time_distance,
                 "length": length_distance
            }
    if model == "TRANSPORTATION_MODEL" or model == "NETWORK_FLOW_MODEL":  # only add distances to the depots if the model actually needs depots
        depot = depots[0]
        nearestStop = depot.getNearestStop(ptn)
        for stop in ptn.getNodes():  # we assign to a depot the distances from its nearest stop
            time_depot_travel_dist = stop_distances[(stop.getId(), nearestStop.getId())]["time"] + depot.getDistance(nearestStop) / vehicle_speed  # we calculate the euclidean distance from the depot to its nearest stop, from this stop we use distances in the ptn.
            length_depot_travel_dist = stop_distances[(stop.getId(), nearestStop.getId())]["length"] + depot.getDistance(nearestStop)
            stop_distances[(stop.getId(), stop_number)] = {
                "time": time_depot_travel_dist,
                "length": length_depot_travel_dist
            }
            stop_distances[(stop_number, stop.getId())] = {
                "time": time_depot_travel_dist,
                "length": length_depot_travel_dist
            }
    logger.info("Finished calculating the stop distances")

    logger.info("Begin the " + model + " computations")
    if model == "ASSIGNMENT_MODEL":
        vehicle_schedule = assignment_model(
            trip_list,
            stop_distances,
            vehicle_cost,
            rounding_places,
            time_empty_meters_cost,
            length_empty_meters_cost,
            turn_over_time)
    elif model == "TRANSPORTATION_MODEL":
        vehicle_schedule = transportation_model(
            trip_list,
            number_of_vehicles,
            stop_distances,
            vehicle_cost,
            stop_number,
            missing_trip_penalty,
            rounding_places,
            time_empty_meters_cost,
            length_empty_meters_cost,
            turn_over_time,
            depot_index)
    elif model == "NETWORK_FLOW_MODEL":
        vehicle_schedule = network_flow_model(
            trip_list,
            number_of_vehicles,
            stop_distances,
            vehicle_cost,
            stop_number,
            rounding_places,
            depot_file,
            time_empty_meters_cost,
            length_empty_meters_cost,
            turn_over_time,
            depot_index)
    logger.info("Finished the model computations")

    logger.info("Begin writing output data")
    VehicleScheduleWriter.write(vehicle_schedule)
    logger.info("Finished writing output data")
