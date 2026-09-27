from core.model.vehicle_scheduling import VehicleSchedule,VehicleTour,Circulation,Trip,TripType
import networkx as nx
import math
import logging

logger = logging.getLogger(__name__)

def transportation_model(
    trip_list: list[Trip],
    number_of_vehicles: int,
    stop_distances: dict[tuple[int,int],dict[str, float]],
    vehicle_cost: float,
    stop_number: int,
    missing_trip_penalty: float,
    rounding_places: int,
    time_empty_meters_cost: float,
    length_empty_meters_cost: float,
    turn_over_time: float,
    depot_index: int) -> VehicleSchedule:
    """Networkx implementation of the transportation model described in the paper "An overview on vehicle scheduling models" by Stefan Bunte and Natalia Kliewer.
    We have one depot specified in "basis/Depot.giv". If there are several depots listed in the file, we only consider the first one and ignore the rest.
    Prints out all trips left out if we have less available vehicles than would be needed to serve all trips.
    All trips that can be served are returned as a vehicle schedule

    :param trip_list: List containing all trips the vehicles need to be assigned to
    :type trip_list: list[Trip]
    :param stop_distances: a dict of distances between stops
    :type stop_distances: dict[tuple[int, int], int]
    :param vehicle_cost: the cost of needing and additional vehicle
    :type vehicle_cost: int
    :param number_of_vehicles: upper bound on the number of vehicles which can be used in the schedule
    :type number_of_vehicles: int
    :param missing_trip_penalty: the penalty of a trip to which no vehicle can be assigned
    :type missing_trip_penalty: int
    :param stop_number: the total number of stops
    :type stop_number: int
    :param rounding_places: the number of decimal places for rounding the stop distances
    :type rounding_places: int
    :param time_empty_meters_cost: the cost of the time while driving an empty vehicle
    :type: float
    :param length_empty_meters_cost: the cost of the distance while driving an empty vehicle
    :type: float
    :param turn_over_time: the time a vehicle needs after arrival to ready itself for the next trip
    :type turn_over_time: int
    :param depot_index: the index of the depot
    :type depot_index: int
    ...
    :return: vehicle schedule which assigns vehicles to trips
    :rtype: VehicleSchedule
    """
    def compatible(trip1: Trip, trip2: Trip) -> bool:
        """Short check whether trip2 can be reached by a vehicle after finishing trip1

        :param trip1: first trip
        :type trip1: Trip
        :param trip2: second trip
        :type trip2: Trip
        ...
        :return: True or False
        """
        return (
            trip1.getEndTime()
            + turn_over_time
            + stop_distances[
                trip1.getEndStopId(),
                trip2.getStartStopId()]["time"] <= trip2.getStartTime()
        )
    #-------------------------create graph-----------------------------------
    nx_graph = nx.DiGraph()
    for nodeId,trip in enumerate(trip_list):
        nx_graph.add_node(nodeId,data=trip)  # bipartite Graph
        nx_graph.add_node(nodeId+len(trip_list),data=trip)
    for id1 in range(len(trip_list)):
        for id2 in range(len(trip_list),2*len(trip_list)):
            nodes = nx_graph.nodes()  # maybe saving some computing if we do not call nx.graph.nodes several times
            if compatible(nodes[id1]["data"],nodes[id2]["data"]):
                nx_graph.add_edge(
                    id1,
                    id2,
                    weight=round(
                        time_empty_meters_cost * (
                            nodes[id2]["data"].getStartTime() -
                            nodes[id1]["data"].getEndTime()
                            )
                        + length_empty_meters_cost * stop_distances[
                            (nodes[id1]["data"].getEndStopId(),
                             nodes[id2]["data"].getStartStopId()
                             )
                        ]["length"],
                        rounding_places
                    ) *10**rounding_places,
                    capacity=1)
    nx_graph.add_node("depot_outgoing")
    nx_graph.add_node("depot_incoming")
    nx_graph.add_edge(
        "depot_outgoing",
        "depot_incoming",
        weight=0,
        capacity=number_of_vehicles
    ) # create opportunity for vehicles to stay in the depot
    for id1,trip_i in enumerate(trip_list):
        nx_graph.add_edge(
            id1,
            "depot_incoming",
            weight=(math.ceil(vehicle_cost/2) + round(
                time_empty_meters_cost * stop_distances[(
                    stop_number,
                    trip_i.getEndStopId()
                )]["time"]
                + length_empty_meters_cost * stop_distances[(
                    stop_number,
                    trip_i.getEndStopId()
                )]["length"],
                rounding_places)) *10**rounding_places,
            capacity=1) # create edges for a vehicle returning back to the depot after finishing its trips
    for id2,trip_j in enumerate(trip_list):
        nx_graph.add_edge(
            "depot_outgoing",
            id2+len(trip_list),
            weight=(math.floor(vehicle_cost / 2) + round(
                time_empty_meters_cost * stop_distances[(
                    stop_number,
                    trip_j.getStartStopId()
                )]["time"]
                + length_empty_meters_cost * stop_distances[(
                    stop_number,
                    trip_j.getStartStopId()
                )]["length"],
                rounding_places)) *10**rounding_places,
            capacity=1) # create edges for a vehicle coming out of the depot to start its trips, as the tripId shall be a node of the other half of the bipartit graph len(trip_list) must be added
    for id1 in range(len(trip_list)):
        nx_graph.add_edge(
            id1,
            id1+len(trip_list),
            weight=missing_trip_penalty*10**rounding_places,
            capacity=1)

    nx_graph.add_node("s") # add source and sink nodes for max flow min cost
    nx_graph.add_node("t")
    for id1 in range(len(trip_list)):
        nx_graph.add_edge(
            "s",
            id1,
            weight=0,
            capacity=1
        )
    for id2 in range(len(trip_list),2*len(trip_list)):
        nx_graph.add_edge(
            id2,
            "t",
            weight=0,
            capacity=1
        )
    nx_graph.add_edge(
        "s",
        "depot_outgoing",
        weight=0,
        capacity=number_of_vehicles
    )
    nx_graph.add_edge(
        "depot_incoming",
        "t",
        weight=0,
        capacity=number_of_vehicles
    )

    logger.debug("Graph created: "+ str(len(nx_graph.nodes()))+" nodes, "+str(len(nx_graph.edges()))+" edges")
    flow_dict=nx.max_flow_min_cost(nx_graph,"s", "t")  # calculate maxflow-mincost
    logger.debug("flow calculated")
    #-------------------- create VehicleSchedule Object---------------------------
    vehicle_tour_list = []
    vehicleId = 1
    for (u,v) in nx_graph.edges("depot_outgoing"):
        if flow_dict[u][v]==1 and v!="depot_incoming": # let a new vehicle starts its vehicle tour
            vehicletour = VehicleTour(vehicleId)
            vehicletour_tripId = 1
            same_vehicle = True

            trip_v = nx_graph.nodes[v]["data"]  # we add one empty trip from the depot to the first trip
            empty_depot_outgoing_trip = Trip(
                -1,
                -1,
                -depot_index,
                int(trip_v.getStartTime()-turn_over_time-stop_distances[(stop_number,trip_v.getStartStopId())]["time"]),
                trip_v.getStartAperiodicEventId(),
                trip_v.getStartPeriodicEventId(),
                trip_v.getStartStopId(),
                trip_v.getStartTime(),
                -1,
                TripType.EMPTY)
            vehicletour.addTrip(vehicletour_tripId, empty_depot_outgoing_trip)
            vehicletour_tripId += 1

            while same_vehicle:  # let this loop run until we found all trips for one vehicle tour
                trip_v = nx_graph.nodes[v]["data"]
                if u!="depot_outgoing":  # add the empty trip only in between two trips of same vehicle tour
                    trip_u = nx_graph.nodes[u]["data"]
                    empty_trip = Trip(
                        trip_u.getEndAperiodicEventId(),
                        trip_u.getEndPeriodicEventId(),
                        trip_u.getEndStopId(),
                        trip_u.getEndTime(),
                        trip_v.getStartAperiodicEventId(),
                        trip_v.getStartPeriodicEventId(),
                        trip_v.getStartStopId(),
                        trip_v.getStartTime(),
                        -1,
                        TripType.EMPTY)  # empty trip starts at end of u and ends at beginning of v
                    vehicletour.addTrip(vehicletour_tripId, empty_trip)
                    vehicletour_tripId += 1
                vehicletour.addTrip(vehicletour_tripId, trip_v)
                vehicletour_tripId += 1
                for (a,b) in nx_graph.edges(v-len(trip_list)): # search for the next trip in the vehicle tour
                    if flow_dict[a][b]:
                        if b=="depot_incoming":  # vehicle tour over, we need a new vehicle
                            trip_a = nx_graph.nodes[a]["data"]  # we add one empty trip from the last trip to the depot
                            empty_depot_incoming_trip = Trip(
                                trip_a.getEndAperiodicEventId(),
                                trip_a.getEndPeriodicEventId(),
                                trip_a.getEndStopId(),
                                trip_a.getEndTime(),
                                -1,
                                -1,
                                -depot_index,
                                int(trip_a.getEndTime()
                                    + turn_over_time
                                    + stop_distances[(stop_number, trip_a.getEndStopId())]["time"]
                                    ),
                                -1,
                                TripType.EMPTY)
                            vehicletour.addTrip(vehicletour_tripId, empty_depot_incoming_trip)
                            vehicletour_tripId += 1

                            vehicle_tour_list.append(vehicletour)
                            vehicleId += 1
                            same_vehicle = False
                            break
                        else: # b is the next trip for our vehicle
                            v=b
                            u=a
                            break

    for id1 in range(len(trip_list)):  # printing out the not served trips if logging level is debug
        if flow_dict[id1][id1+len(trip_list)]:
            logger.debug("left out trip:"+ str(nx_graph.nodes[id1]["data"]))

    vehicle_schedule = VehicleSchedule()
    for vehicle_id,vehicle_tour in enumerate(vehicle_tour_list): # fill the vehicle_schedule
        circ = Circulation(vehicle_id+1) # our IDs start at 1 not 0
        circ.addVehicle(vehicle_tour)
        vehicle_schedule.addCirculation(circ)

    return(vehicle_schedule)