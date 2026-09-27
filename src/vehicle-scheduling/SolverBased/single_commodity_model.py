import logging
import time

from core.model.vehicle_scheduling import VehicleSchedule, VehicleTour, Circulation, Trip, TripType
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, IntAttribute, Status
from core.model.vehicle_scheduling import Depot
from core.solver.solver_parameters import SolverParameters
from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException

logger = logging.getLogger(__name__)

def single_commodity_model(
    depot_list: list[Depot],
    trip_list: list[Trip],
    stop_distances: dict[tuple[int,int],dict[str, float]],
    parameters: SolverParameters,
    stop_number: int,
    vehicle_cost: float,
    time_empty_meter_cost: float,
    length_empty_meter_cost: float,
    turn_over_time: float) -> VehicleSchedule:
    """Implementation of the Single-commodity model with assignment variables described in the paper "An overview on vehicle scheduling models" by Stefan Bunte and Natalia Kliewer.
    We use the Generic-Solver-Interface provided by Lintim. If our IP is feasible, then the solution is returned as a VehicleSchedule.

    :param depot_list: List of depots.
    :type depot_list: list[Depot]
    :param trip_list: List containing all trips the vehicles need to be assigned to
    :type trip_list: list[Trip]
    :param parameters: the parameters specifying the solver which is used for solving the IP formulated in this algorithm
    :type parameters: SolverParameters
    :param stop_distances: a dict of distances between stops
    :type stop_distances: dict[tuple[int, int], dict[str, float]]
    :param vehicle_cost: the cost of using an additional vehicle
    :type vehicle_cost: float
    :param stop_number: the total amount of stops
    :type stop_number: int
    :param time_empty_meter_cost: the cost per time unit while driving an empty vehicle
    :type time_empty_meter_cost: float
    :param length_empty_meter_cost: the cost per distance while driving an empty vehicle
    :type length_empty_meter_cost: float
    :param turn_over_time: the time in seconds which a vehicle needs after arrival to ready itself for the next trip
    :type turn_over_time: float

    ...
    :raises SolverFoundNoFeasibleSolutionException: If we have less vehicles than needed this exception is raised.
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
                + turn_over_time
                + stop_distances[
                    trip1.getEndStopId(),
                    trip2.getStartStopId()]["time"] <= trip2.getStartTime())

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    logger.debug("Add parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")
    #---------------------------------------create all variables----------------------------------------------
    x={}
    y={}
    for i,trip_i in enumerate(trip_list):
        for j,trip_j in enumerate(trip_list):
            if compatible(trip_i,trip_j):  # we create only variables if trip j can actually be served after trip i by the same vehicle
                x[(i,j)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective=(
                        time_empty_meter_cost * stop_distances[(trip_i.getEndStopId(),trip_j.getStartStopId())]["time"]
                        + length_empty_meter_cost * stop_distances[(trip_i.getEndStopId(),trip_j.getStartStopId())]["length"]
                    ),
                    name = "x"+str((i,j)) # the trip j after trip i variables
                )
    for i,trip_i in enumerate(trip_list):
        for g in range(len(trip_list),len(trip_list)+len(depot_list)):
            x[i,g] = model.addVariable(
                lower_bound=0,
                upper_bound=1,
                var_type=VariableType.BINARY,
                objective= (
                    time_empty_meter_cost * stop_distances[(trip_i.getEndStopId(),g-len(trip_list)+stop_number+1)]["time"]
                    + length_empty_meter_cost * stop_distances[(trip_i.getEndStopId(),g-len(trip_list)+stop_number+1)]["length"]
                ), # transform g from the numbers directly successing the number of trips to the numbers directly successing the number of stops
                name="x"+str((i, g))
            )
            x[g,i] = model.addVariable(
                lower_bound=0,
                upper_bound=1,
                var_type=VariableType.BINARY,
                objective= (
                    vehicle_cost
                    + time_empty_meter_cost * stop_distances[(g-len(trip_list)+stop_number+1,trip_i.getStartStopId())]["time"]
                    + length_empty_meter_cost * stop_distances[(g-len(trip_list)+stop_number+1,trip_i.getStartStopId())]["length"]
                ),
                name="x"+str((g, i))
            )
            y[i,g] = model.addVariable(
                lower_bound=0,
                upper_bound=1,
                var_type=VariableType.BINARY,
                objective=0,
                name="y" + str((i, g))
            )

    logger.debug("Add constraints")
    # ----------------------------------add constraints-----------------------------------------
    for i, trip_i in enumerate(trip_list):  # implement first two sum constraints
        sum_constraint_1 = model.createExpression()
        sum_constraint_2 = model.createExpression()
        for j, trip_j in enumerate(trip_list):
            if compatible(trip_i,trip_j):
                sum_constraint_1.add(x[(i,j)])
            if compatible(trip_j,trip_i):
                sum_constraint_2.add(x[(j,i)])
        for g in range(len(trip_list),len(trip_list)+len(depot_list)):
            sum_constraint_1.add(x[(i,g)])
            sum_constraint_2.add(x[(g,i)])
        model.addConstraint(sum_constraint_1, ConstraintSense.EQUAL, 1,name = "sum_constraint_1"+str(i))
        model.addConstraint(sum_constraint_2, ConstraintSense.EQUAL, 1,name = "sum_constraint_2"+str(i))

    for g in range(len(trip_list),len(trip_list)+len(depot_list)): # implement third sum constraints
        sum_constraint_3 = model.createExpression()
        for i,trip_i in enumerate(trip_list):
            sum_constraint_3.add(x[(g,i)])
        model.addConstraint(sum_constraint_3, ConstraintSense.LESS_EQUAL, depot_list[g-len(trip_list)].getNumberOfVehicles(),name = "sum_constraint_3"+str(g))

    for g in range(len(trip_list),len(trip_list)+len(depot_list)):  # implement the constraints responsible for the mapping of every trip to one depot
        for i,trip_i in enumerate(trip_list):
            constraint_4 = model.createExpression()
            constraint_4.add(x[(g,i)])
            constraint_4.multiAdd(-1,y[(i,g)])
            model.addConstraint(constraint_4, ConstraintSense.LESS_EQUAL, 0,name=f"pred_of_{i}_is_{g}")
            constraint_5 = model.createExpression()
            constraint_5.add(x[(i,g)])
            constraint_5.multiAdd(-1,y[(i,g)])
            model.addConstraint(constraint_5, ConstraintSense.LESS_EQUAL, 0,name=f"succ_of_{i}_is_{g}")
            for j,trip_j in enumerate(trip_list):
                if compatible(trip_i,trip_j):
                    constraint_7 = model.createExpression()
                    constraint_7.add(x[(i,j)])
                    constraint_7.add(y[(i,g)])
                    constraint_7.multiAdd(-1,y[(j,g)])
                    model.addConstraint(constraint_7, ConstraintSense.LESS_EQUAL, 1, name=f"pred_of_{j}_belongs_to_{g}")

    for i,trip_i in enumerate(trip_list):
        constraint_9 = model.createExpression()
        for g in range(len(trip_list),len(trip_list)+len(depot_list)):
            constraint_9.add(y[(i,g)])
        model.addConstraint(constraint_9,ConstraintSense.EQUAL,1,name=f"only_one_depot_{g}_per_trip_{i}")

    #--------------------------------------------------solve Model---------------------------------
    model.setSense(OptimizationSense.MINIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("vehicle-scheduling/single-commodity.lp")
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

        # -------------------- create VehicleSchedule Object----------------------------------
        vehicle_tour_list = []
        vehicleId = 1
        for g in range(len(trip_list),len(trip_list)+len(depot_list)):
            for i,trip_i in enumerate(trip_list):
                if model.getValue(x[(g,i)])==1:  # start one circulation/vehicle tour
                    vehicletour = VehicleTour(vehicleId)
                    vehicletour_tripId = 1

                    depot_trip = Trip(
                        -1,
                        -1,
                        -depot_list[g-len(trip_list)].depotId,
                        int(trip_i.getStartTime()-stop_distances[(g-len(trip_list)+stop_number+1,trip_i.getStartStopId())]["time"]), # every vehicletour starts with the departure from one depot
                        trip_i.getStartAperiodicEventId(),
                        trip_i.getStartPeriodicEventId(),
                        trip_i.getStartStopId(),
                        trip_i.getStartTime(),
                        -1,
                        TripType.EMPTY)  # empty trip starts at depot with aperiodic-start-id=-1, periodic-start-id=-1, the negative depotId as stopId and ends at beginning of i
                    vehicletour.addTrip(vehicletour_tripId, depot_trip)
                    vehicletour_tripId += 1

                    same_vehicle = True
                    while same_vehicle: # calculate next trips in the circulation/vehicle tour
                        for j,trip_j in enumerate(trip_list):
                            if compatible(trip_i,trip_j) and model.getValue(x[(i,j)])==1:  # check that x[i,j] exists as variable and check if trip j successes trip i
                                vehicletour.addTrip(vehicletour_tripId, trip_i)
                                vehicletour_tripId += 1
                                empty_trip = Trip(trip_i.getEndAperiodicEventId(),
                                                  trip_i.getEndPeriodicEventId(),
                                                  trip_i.getEndStopId(),
                                                  trip_i.getEndTime(),
                                                  trip_j.getStartAperiodicEventId(),
                                                  trip_j.getStartPeriodicEventId(),
                                                  trip_j.getStartStopId(),
                                                  trip_j.getStartTime(),
                                                  -1,
                                                  TripType.EMPTY)  # empty trip starts at end of i and ends at beginning of j
                                vehicletour.addTrip(vehicletour_tripId, empty_trip)
                                vehicletour_tripId += 1
                                i,trip_i = j,trip_j
                                break
                        if model.getValue(x[i,g])==1: # end of circulation/vehicle tour
                            vehicletour.addTrip(vehicletour_tripId, trip_i)
                            vehicletour_tripId += 1
                            depot_trip = Trip(
                                trip_i.getEndAperiodicEventId(),
                                trip_i.getEndPeriodicEventId(),
                                trip_i.getEndStopId(),
                                trip_i.getEndTime(), # every vehicletour ends with the arrival at one depot
                                -1,
                                -1,
                                -depot_list[g - len(trip_list)].depotId,
                                int(trip_i.getEndTime() + stop_distances[(trip_i.getEndStopId(),g - len(trip_list) + stop_number+1)]["time"]),
                                -1,
                                TripType.EMPTY) # empty trip ends at depot with aperiodic-end-id=-1, periodic-end-id=-1, the negative depotId as stopId and starts at the end of i
                            vehicletour.addTrip(vehicletour_tripId, depot_trip)
                            vehicle_tour_list.append(vehicletour)
                            vehicleId += 1
                            same_vehicle = False

        vehicle_schedule = VehicleSchedule()
        for vehicle_id, vehicle_tour in enumerate(vehicle_tour_list): # fill the vehicle_schedule
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
            model.computeIIS("vehicle-scheduling/IIS_SingleCommodityModel.ilp")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()
