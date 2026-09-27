from core.model.vehicle_scheduling import VehicleSchedule,VehicleTour,Circulation,Trip,TripType
import networkx as nx
import logging

logger = logging.getLogger(__name__)

def assignment_model(
    trip_list: list[Trip],
    stop_distances: dict[tuple[int,int], dict[str, float]],
    vehicle_cost: float,
    rounding_places: int,
    time_empty_meters_cost: float,
    length_empty_meters_cost: float,
    turn_over_time: float) -> VehicleSchedule:
    """Networkx implementation of the assignment model described in the paper "An overview on vehicle scheduling models" by Stefan Bunte and Natalia Kliewer.
    Uses the VehicleScheduleWriter class to write the vehicle schedule in the vehicle-scheduling folder.

    :param trip_list: List containing all trips the vehicles need to be assigned to
    :type trip_list: list[Trip]
    :param stop_distances: a dict of distances between stops
    :type stop_distances: dict[tuple[int, int], int]
    :param vehicle_cost: the cost of needing and additional vehicle, same unit assumed as in the given distances
    :type vehicle_cost: float
    :param rounding_places: the number of decimal places for rounding the stop distances
    :type rounding_places: int
    :param time_empty_meters_cost: the cost of the time while driving an empty vehicle
    :type: float
    :param length_empty_meters_cost: the cost of the distance while driving an empty vehicle
    :type: float
    :param: turn_over_time: the turn over time of a vehicle until it is ready for the next trip
    :type turn_over_time: float

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
        return (trip1.getEndTime()
                + stop_distances[
                    trip1.getEndStopId(),
                    trip2.getStartStopId()]["time"]
                + turn_over_time  <= trip2.getStartTime())
    #------------------------create graph---------------------------------
    nx_graph = nx.DiGraph()
    for nodeId,trip in enumerate(trip_list):
        nx_graph.add_node(nodeId,data=trip)  # bipartiter Graph
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
                            nodes[id1]["data"].getEndTime())
                        + length_empty_meters_cost * stop_distances[
                            (nodes[id1]["data"].getEndStopId(),
                             nodes[id2]["data"].getStartStopId()
                             )
                        ]["length"],
                        rounding_places) * 10**rounding_places,
                    capacity=1,
                    valid_transfer=True
                ) # no additional vehicle needed
            else:
                nx_graph.add_edge(
                    id1,
                    id2,
                    weight=vehicle_cost*10**rounding_places,
                    capacity=1,
                    valid_transfer=False
                ) # new vehicle needed
    nx_graph.add_node("s")
    nx_graph.add_node("t")
    for id1 in range(len(trip_list)):
        nx_graph.add_edge(
            "s",
            id1,
            weight=0,
            capacity=1,
            valid_transfer=True
        )
    for id2 in range(len(trip_list),2*len(trip_list)):
        nx_graph.add_edge(
            id2,
            "t",
            weight=0,
            capacity=1,
            valid_transfer=True
        )

    logger.debug("Graph created "+str(len(nx_graph.nodes()))+", "+str(len(nx_graph.edges())))
    logger.debug("Length Trip List "+str(len(trip_list)))
    flow_dict=nx.max_flow_min_cost(nx_graph,"s", "t")  # calculate maxflow-mincost
    logger.debug("flow calculated")

    #-------------------- create VehicleSchedule Object---------------------------
    vehicle_tour_list = []
    vehicleId = 1
    for (u,v,d) in nx_graph.edges.data():
        if d["valid_transfer"]==False and flow_dict[u][v]==1: # here a new vehicle starts its journey
            vehicletour = VehicleTour(vehicleId)
            vehicletour_tripId = 1
            same_vehicle = True
            while same_vehicle: # let this loop run until we found all trips for one vehicle tour
                trip_v = nx_graph.nodes[v]["data"]
                trip_u = nx_graph.nodes[u]["data"]
                if nx_graph.edges[u,v]["valid_transfer"]:  # add the empty trip only in between two trips of same vehicletour
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
                    vehicletour.addTrip(vehicletour_tripId,empty_trip)
                    vehicletour_tripId += 1
                vehicletour.addTrip(vehicletour_tripId, trip_v)
                vehicletour_tripId += 1
                for (a,b) in nx_graph.edges(v-len(trip_list)): # search for next trip in the vehicle tour
                    if flow_dict[a][b]:
                        if nx_graph.edges[a, b]["valid_transfer"]:  # the next valid trip for our vehicle is found
                            v=b
                            u=a
                            break
                        else:   # we need a new vehicle/ need to start a new vehicle tour
                            vehicle_tour_list.append(vehicletour)
                            vehicleId +=1
                            same_vehicle = False
                            break

    vehicle_schedule = VehicleSchedule()
    for vehicle_id,vehicle_tour in enumerate(vehicle_tour_list): # fill the vehicle_schedule
        circ = Circulation(vehicle_id+1) # our IDs start at 1 not 0
        circ.addVehicle(vehicle_tour)
        vehicle_schedule.addCirculation(circ)

    return vehicle_schedule