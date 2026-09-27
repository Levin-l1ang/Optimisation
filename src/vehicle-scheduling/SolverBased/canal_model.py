import logging
import time

from core.model.vehicle_scheduling import VehicleSchedule, VehicleTour, Circulation, Trip, TripType
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, IntAttribute, Status
from core.model.vehicle_scheduling import Depot
from core.solver.solver_parameters import SolverParameters
from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException
from core.model.ptn import Stop
from collections import defaultdict

logger = logging.getLogger(__name__)

def canal(
    trip_list: list[Trip],
    parameters: SolverParameters,
    stop_list: list[Stop],
    stop_distances: dict[tuple[int, int], dict[str, float]],
    turn_over_time: float,
    vehicle_cost: float,
    time_empty_meter_cost: float,
    length_empty_meter_cost: float) -> VehicleSchedule:
    """Implementation of the Canal model described in the diploma "Das Kanalmodell zur Effizienzsteigerung
    in der Fahrzeugumlaufplanung" by Anke Uffmann.
    We implemented the simple case where every stop can park some currently not used vehicles. However the IP from the diploma was altered for ensuring turn over times for vehicles.
    The canal model consists of one main problem managing the vehicle flow between stops and several subproblems managing the vehicle flow at every stop(canal).
    There are several optimal assignment solutions for the subproblems. Here the vehicles are assigned at stops following more or less the FIFO principle.

    :param trip_list: the list of trips which need to be served with vehicles
    :type trip_list: list[Trip]
    :param parameters: the parameters which are specifying the solver which is used for solving the IP formulated in this algorithm
    :type parameters: SolverParameters
    :param stop_list: the list of all stops of our ptn
    :type stop_list: list[Stop]
    :param stop_distances: a dict of distances between stops
    :type stop_distances: dict[tuple[int, int], dict[str, float]]
    :param turn_over_time: the time in seconds it takes for one vehicle to be ready for the next trip after it arrived at a stop
    :type turn_over_time: float
    :param vehicle_cost: the cost of needing and additional vehicle
    :type vehicle_cost: float
    :param time_empty_meter_cost: the cost of the time while driving an empty vehicle
    :type: float
    :param length_empty_meter_cost: the cost of the distance while driving an empty vehicle
    :type: float

    ...
    :raises SolverFoundNoFeasibleSolutionException
    ...
    :return: vehicle schedule which contains the vehicle assignments
    :rtype: VehicleSchedule
    """
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    logger.debug("Add parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")
    # ---------------------------------------create all variables----------------------------------------------

    x = {}
    N = {}
    for stop in stop_list:
        N[-stop.getId()] = {}

    #------------------------------------------defining the variables------------------------
    # in the canal graph that we are creating, stops are modelled with negative stop ids and trip nodes are modelled with positive trip ids
    for i, trip_i in enumerate(trip_list):
        x[(i, -trip_i.getEndStopId(),trip_i.getEndTime(),trip_i.getEndTime())] = model.addVariable(
            lower_bound=0,
            upper_bound=1,
            var_type=VariableType.BINARY,
            objective=0, # add edges between trips and their corresponding stops, the stops get negative id to differ from the trip Ids. We index with (start_stop/trip_id, end_stop/trip_id, start_time, end_time)
            name="x" + str((i, -trip_i.getEndStopId())))
        N[-trip_i.getEndStopId()].setdefault(trip_i.getEndTime(), []).append({ # N is indexed by the canal/stop and the time points a vehicle could enter or leave the canal. As several vehicles can enter and leave at the same time we use lists for the information about the incoming/edges
            "incoming": True,
            "edge_info": (i, -trip_i.getEndStopId(), trip_i.getEndTime(), trip_i.getEndTime()),
            "variable": model.addVariable(
                lower_bound=0,
                upper_bound=float("inf"),
                var_type=VariableType.INTEGER,
                objective=0,
                name="N" + str((-trip_i.getEndStopId(), trip_i.getEndTime())))
        })

        x[(-trip_i.getStartStopId(), i,trip_i.getStartTime(),trip_i.getStartTime())] = model.addVariable(
            lower_bound=0,
            upper_bound=1,
            var_type=VariableType.BINARY,
            objective=0,
            name="x" + str((-trip_i.getStartStopId(), i)))
        N[-trip_i.getStartStopId()].setdefault(trip_i.getStartTime(),[]).append({
            "incoming": False,
            "edge_info": (-trip_i.getStartStopId(), i,trip_i.getStartTime(),trip_i.getStartTime()),
            "variable": model.addVariable(
                lower_bound=0,
                upper_bound=float("inf"),
                var_type=VariableType.INTEGER,
                objective=0,
                name="N" + str((-trip_i.getStartStopId(), trip_i.getStartTime())))
        })


        for stop in stop_list: # add the edges between stops and stops representing empty trips
            if stop.getId() != trip_i.getStartStopId(): # add edge from stop to the stop where the trip starts.
                starting_time_empty_trip = (
                    trip_i.getStartTime()
                    - stop_distances[(stop.getId(),trip_i.getStartStopId())]["time"]
                    - turn_over_time
                )
                x[(-stop.getId(), -trip_i.getStartStopId(), starting_time_empty_trip, trip_i.getStartTime()-turn_over_time)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective= (
                        time_empty_meter_cost * stop_distances[(stop.getId(),trip_i.getStartStopId())]["time"]
                        + length_empty_meter_cost * stop_distances[(stop.getId(),trip_i.getStartStopId())]["length"]
                    ),
                    name="x" + str((-stop.getId(),-trip_i.getStartStopId())))
                N[-stop.getId()].setdefault(starting_time_empty_trip,[]).append({
                    "incoming": False,
                    "edge_info": (-stop.getId(), -trip_i.getStartStopId(), starting_time_empty_trip, trip_i.getStartTime()-turn_over_time),
                    "variable": model.addVariable(
                        lower_bound=0,
                        upper_bound=float("inf"),
                        var_type=VariableType.INTEGER,
                        objective=0,
                        name="N" + str((-stop.getId(), starting_time_empty_trip)))
                })
                N[-trip_i.getStartStopId()].setdefault(trip_i.getStartTime()-turn_over_time,[]).append({
                    "incoming": True,
                    "edge_info": (-stop.getId(), -trip_i.getStartStopId(), starting_time_empty_trip, trip_i.getStartTime()-turn_over_time),
                    "variable": model.addVariable(
                        lower_bound=0,
                        upper_bound=float("inf"),
                        var_type=VariableType.INTEGER,
                        objective=0,
                        name="N" + str((-trip_i.getStartStopId(), trip_i.getStartTime()-turn_over_time)))
                })


            if trip_i.getEndStopId() != stop.getId(): # add edge from the stop where trip ends to stop
                ending_time_empty_trip = (
                    trip_i.getEndTime()
                    + stop_distances[(trip_i.getEndStopId(),stop.getId())]["time"]
                    + turn_over_time
                )
                x[(-trip_i.getEndStopId(),-stop.getId(),trip_i.getEndTime()+turn_over_time,ending_time_empty_trip)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective=(
                        time_empty_meter_cost * stop_distances[(trip_i.getEndStopId(),stop.getId())]["time"]
                        + length_empty_meter_cost * stop_distances[(trip_i.getEndStopId(),stop.getId())]["length"]
                    ),
                    name="x" + str((-trip_i.getEndStopId(),-stop.getId())))
                N[-stop.getId()].setdefault(ending_time_empty_trip,[]).append({
                    "incoming": True,
                    "edge_info": (-trip_i.getEndStopId(),-stop.getId(),trip_i.getEndTime()+turn_over_time,ending_time_empty_trip),
                    "variable": model.addVariable(
                        lower_bound=0,
                        upper_bound=float("inf"),
                        var_type=VariableType.INTEGER,
                        objective=0,
                        name="N" + str((-stop.getId(), ending_time_empty_trip)))
                })
                N[-trip_i.getEndStopId()].setdefault(trip_i.getEndTime() +turn_over_time,[]).append({
                    "incoming": False,
                    "edge_info": (-trip_i.getEndStopId(),-stop.getId(),trip_i.getEndTime()+turn_over_time,ending_time_empty_trip),
                    "variable": model.addVariable(
                        lower_bound=0,
                        upper_bound=float("inf"),
                        var_type=VariableType.INTEGER,
                        objective=0,
                        name="N" + str((-trip_i.getEndStopId(), trip_i.getEndTime() +turn_over_time)))
                })

    number_of_vehicles_dict_at_period_jump = {}
    for stop in stop_list:
        N[-stop.getId()] = dict(sorted(N[-stop.getId()].items())) # sort the N regarding the time in each canal/stop
        for event_list in N[-stop.getId()].values():  # sort the incoming edges first for every event list for one time point
            event_list.sort(key=lambda y: y["incoming"], reverse=True)
        number_of_vehicles_dict_at_period_jump[-stop.getId()] = model.addVariable(
                        lower_bound=0,
                        upper_bound=float("inf"),
                        var_type=VariableType.INTEGER,
                        objective=vehicle_cost,
                        name="N" + str((-stop.getId()))+"number_vehicles_at_period_jump")

    logger.debug("Add constraints")
    # ----------------------------------add constraints-----------------------------------------
    for i, trip_i in enumerate(trip_list):  # implement first sum constraints
        model.addConstraint(
            x[(i, -trip_i.getEndStopId(),trip_i.getEndTime(),trip_i.getEndTime())],
            ConstraintSense.EQUAL,
            1,
            name="sum_constraint_ending_trips"
        )
        model.addConstraint(
            x[(-trip_i.getStartStopId(), i, trip_i.getStartTime(),trip_i.getStartTime())],
            ConstraintSense.EQUAL,
            1,
            name="sum_constraint_beginning_trips"
        )

    for stop_id in N:
        pred = number_of_vehicles_dict_at_period_jump[stop_id] # the predecessor of the first event is the one before the period jump
        event_vehicle_memory = defaultdict(list)  # dict contains an empty list for every new key
        N_tuple_list = list(N[stop_id].items())
        for i,(event_time, event_list) in enumerate(N_tuple_list):
            for j, event in enumerate(event_list):
                expression = model.createExpression()
                if j ==0 and event_time in event_vehicle_memory.keys(): # add the vehicles from memory which now completed the turn over time
                    for event2 in event_vehicle_memory[event_time]:
                        expression.add(x[event2["edge_info"]])
                    event_vehicle_memory[event_time] = []
                if event["incoming"]: # save the incoming vehicle in the memory for use after the turnover time
                    for event_time_succ,event_list_succ in N_tuple_list[i:]:  # find event after event + turn over time to ensure the turn over time in the IP
                        if event_time_succ >= event_time + turn_over_time:
                            event_vehicle_memory[event_time_succ].append(event)
                            break
                else:
                    expression.multiAdd(-1,x[event["edge_info"]])
                expression.add(pred)
                model.addConstraint(
                    expression,
                    ConstraintSense.EQUAL,
                    event["variable"],
                    name="N" + str((stop_id, event_time, i)))
                pred = event["variable"]

        vehicle_at_jump_expression = model.createExpression()
        for event_time,event_list in event_vehicle_memory.items():
            for event in event_list:
                vehicle_at_jump_expression.add(x[event["edge_info"]])
        model.addConstraint(
            pred + vehicle_at_jump_expression,
            ConstraintSense.EQUAL,
            number_of_vehicles_dict_at_period_jump[stop_id],
            name="N[" + str(stop_id)+"]_vehicle_at_jump_constraint"
        )

    # --------------------------------------------------solve Model---------------------------------
    model.setSense(OptimizationSense.MINIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("vehicle-scheduling/canal.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")

        assignments_per_canal_dict = {}
        vehicle_tour_starting_events = {}
        for stop_id in N:  # implemented easier variant of Alg2
            A_ü = []
            E_g = []
            assignments_per_canal_dict[stop_id] = {}
            for event_time, event_list in N[stop_id].items():
                for event in event_list:
                    if model.getValue(x[event["edge_info"]]):  # only assign vehicles to actually happening events
                        if event["incoming"]:
                            E_g.append((event_time,event["edge_info"]))
                        else:
                            if len(E_g) !=0 and E_g[0][0]+turn_over_time <= event_time:
                                assignments_per_canal_dict[stop_id][E_g.pop(0)] = (event_time, event["edge_info"])
                            else:
                                A_ü.append((event_time, event["edge_info"]))
            vehicle_tour_starting_events[stop_id]=A_ü
        # -------------------- create VehicleSchedule Object----------------------------------
        vehicle_tour_list = []
        vehicleId = 1
        for stop_id,starting_event_list in vehicle_tour_starting_events.items():
            for event_time,edge_info in starting_event_list:
                vehicletour = VehicleTour(vehicleId)
                vehicletour_tripId = 1
                trip_ongoing = True
                pred = None
                while trip_ongoing:
                    next_stop_or_trip_id = edge_info[1]
                    if next_stop_or_trip_id >=0: # check if the vehicle is driving towards a trip or if it is an empty trip between stops
                        trip = trip_list[next_stop_or_trip_id]
                        if pred != None and pred.getTripType() == TripType.TRIP:  # add an empty trip if previous trip was not empty
                            empty_trip = Trip(
                                pred.getEndAperiodicEventId(),
                                pred.getEndPeriodicEventId(),
                                pred.getEndStopId(),
                                pred.getEndTime(),
                                trip.getStartAperiodicEventId(),
                                trip.getStartPeriodicEventId(),
                                trip.getStartStopId(),
                                trip.getStartTime(),
                                -1,
                                TripType.EMPTY)
                            vehicletour.addTrip(vehicletour_tripId, empty_trip)
                            vehicletour_tripId += 1
                        vehicletour.addTrip(vehicletour_tripId, trip)
                        vehicletour_tripId += 1
                        assignmentKey_in_tripsEndStopCanal = (trip.getEndTime(),(next_stop_or_trip_id,-trip.getEndStopId(),trip.getEndTime(),trip.getEndTime()))
                        if assignmentKey_in_tripsEndStopCanal in assignments_per_canal_dict[-trip.getEndStopId()].keys(): #check whether vehicle tour ends
                            event_time,edge_info = assignments_per_canal_dict[-trip.getEndStopId()][assignmentKey_in_tripsEndStopCanal]
                        else:
                            trip_ongoing = False
                        pred = trip
                    else:
                        startAperiodicEventId = (pred.getEndAperiodicEventId() if pred != None else -1)
                        startPeriodicEventId = (pred.getEndPeriodicEventId() if pred != None else -1)
                        if (edge_info[3], edge_info) in assignments_per_canal_dict[edge_info[1]].keys(): #check whether vehicle tour ends
                            src,dest,starting_time_edge,_ = edge_info
                            starting_time = (pred.getEndTime() if pred != None else starting_time_edge)  # the edge may start later than the ending of the predecessor trip
                            event_time, edge_info = assignments_per_canal_dict[edge_info[1]][(edge_info[3], edge_info)]
                            endAperiodicEventId = (trip_list[edge_info[1]].getStartAperiodicEventId() if edge_info[1]>=0 else -1) # if next trip is not empty take its Aperiodic and Periodic Start Ids, if not -1
                            endPeriodicEventId = (trip_list[edge_info[1]].getStartPeriodicEventId() if edge_info[1]>=0 else -1)
                            empty_trip = Trip(
                                startAperiodicEventId,
                                startPeriodicEventId,
                                -src,
                                int(starting_time),
                                endAperiodicEventId,
                                endPeriodicEventId,
                                -dest,
                                int(edge_info[2]),
                                -1,
                                TripType.EMPTY)
                        else:
                            empty_trip = Trip(
                                startAperiodicEventId,
                                startPeriodicEventId,
                                -edge_info[0],
                                int(edge_info[2]),
                                -1,
                                -1,
                                -edge_info[1],
                                int(edge_info[3]+turn_over_time),
                                -1,
                                TripType.EMPTY)
                            trip_ongoing = False
                        vehicletour.addTrip(vehicletour_tripId, empty_trip)
                        vehicletour_tripId += 1
                        pred = empty_trip
                vehicle_tour_list.append(vehicletour)
                vehicleId += 1

        vehicle_schedule = VehicleSchedule()
        for vehicle_id, vehicle_tour in enumerate(vehicle_tour_list):  # fill the vehicle_schedule
            circ = Circulation(vehicle_id + 1)  # our IDs start at 1 not 0
            circ.addVehicle(vehicle_tour)
            vehicle_schedule.addCirculation(circ)

        # ----------- return vehicle schedule and dispose model------------------
        model.dispose()
        solver.dispose()
        return(vehicle_schedule)

    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("vehicle-scheduling/IIS_canalModel.ilp")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()
